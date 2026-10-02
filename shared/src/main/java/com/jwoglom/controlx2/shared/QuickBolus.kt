package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog

/** Stable pump-issued identity for one physical-button Quick Bolus. */
data class QuickBolusIdentity(
    val pumpSid: Int,
    val pumpSessionId: Long,
    val bolusId: Int,
) {
    init {
        require(pumpSid >= 0) { "pumpSid must be non-negative" }
        require(pumpSessionId > 0L) { "pumpSessionId must be positive" }
        require(bolusId > 0) { "bolusId must be positive" }
    }

    fun encode(): ByteArray = "1|$pumpSid|$pumpSessionId|$bolusId".toByteArray(Charsets.UTF_8)

    fun stableKey(): String = "$pumpSid:$pumpSessionId:$bolusId"

    companion object {
        fun decode(bytes: ByteArray): QuickBolusIdentity? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 4 || parts[0] != "1") return null
            return try {
                QuickBolusIdentity(parts[1].toInt(), parts[2].toLong(), parts[3].toInt())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class QuickBolusOperationPhase {
    ACTIVE,
    CANCELLATION_SENDING,
    CANCELLATION_OUTCOME_UNKNOWN,
    CANCELLATION_NOT_COMPLETED,
    CANCELLATION_CONFIRMED,
    RECONCILING_TERMINAL,
    TERMINAL,
    RECONCILIATION_BLOCKED,
}

data class QuickBolusOperation(
    val identity: QuickBolusIdentity,
    val requestedMilliunits: Long,
    val phase: QuickBolusOperationPhase,
    val deliveredMilliunits: Long? = null,
    val terminalStatusId: Int? = null,
    val terminalEndReasonId: Int? = null,
    val terminalCompletionStatusId: Int? = null,
    val cancellationRequested: Boolean = false,
    val cancellationAccepted: Boolean = false,
    val terminalResultAcknowledged: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(requestedMilliunits > 0L) { "requestedMilliunits must be positive" }
        require(deliveredMilliunits == null || deliveredMilliunits >= 0L) {
            "deliveredMilliunits must be non-negative"
        }
        require(createdAtEpochMillis > 0L) { "createdAtEpochMillis must be positive" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede creation"
        }
    }

    val source: BolusDeliveryHistoryLog.BolusSource
        get() = BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS

    val isUnresolved: Boolean
        get() = phase !in setOf(
            QuickBolusOperationPhase.TERMINAL,
            QuickBolusOperationPhase.RECONCILIATION_BLOCKED,
        )

    val shouldDisplayPanel: Boolean
        get() = phase !in setOf(
            QuickBolusOperationPhase.TERMINAL,
            QuickBolusOperationPhase.RECONCILIATION_BLOCKED,
        )

    val canRequestCancellation: Boolean
        get() = phase in setOf(
            QuickBolusOperationPhase.ACTIVE,
            QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
        )

    val shouldDisplayStoppedResult: Boolean
        get() = phase == QuickBolusOperationPhase.TERMINAL &&
            cancellationRequested &&
            (cancellationAccepted || terminalEndReasonId == 0 ||
                terminalCompletionStatusId == 0) &&
            !terminalResultAcknowledged

    val shouldDisplayReconciliationFailure: Boolean
        get() = phase == QuickBolusOperationPhase.RECONCILIATION_BLOCKED &&
            !terminalResultAcknowledged

    fun encode(): ByteArray = listOf(
        "1",
        identity.pumpSid,
        identity.pumpSessionId,
        identity.bolusId,
        requestedMilliunits,
        phase.name,
        deliveredMilliunits ?: -1L,
        terminalStatusId ?: -1,
        terminalEndReasonId ?: -1,
        terminalCompletionStatusId ?: -1,
        cancellationRequested,
        cancellationAccepted,
        terminalResultAcknowledged,
        createdAtEpochMillis,
        updatedAtEpochMillis,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): QuickBolusOperation? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 15 || parts[0] != "1") return null
            return try {
                val delivered = parts[6].toLong()
                val status = parts[7].toInt()
                val endReason = parts[8].toInt()
                val completionStatus = parts[9].toInt()
                QuickBolusOperation(
                    identity = QuickBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    requestedMilliunits = parts[4].toLong(),
                    phase = QuickBolusOperationPhase.valueOf(parts[5]),
                    deliveredMilliunits = delivered.takeIf { it >= 0L },
                    terminalStatusId = status.takeIf { it >= 0 },
                    terminalEndReasonId = endReason.takeIf { it >= 0 },
                    terminalCompletionStatusId = completionStatus.takeIf { it >= 0 },
                    cancellationRequested = parts[10].toBooleanStrict(),
                    cancellationAccepted = parts[11].toBooleanStrict(),
                    terminalResultAcknowledged = parts[12].toBooleanStrict(),
                    createdAtEpochMillis = parts[13].toLong(),
                    updatedAtEpochMillis = parts[14].toLong(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class QuickBolusCancelOutcome {
    CONFIRMED,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
    ACTIONS_DISABLED,
    DISCONNECTED,
    TIMEOUT,
    PUMP_REJECTED,
}

data class QuickBolusCancelResult(
    val identity: QuickBolusIdentity,
    val outcome: QuickBolusCancelOutcome,
) {
    fun encode(): ByteArray =
        "1|${identity.pumpSid}|${identity.pumpSessionId}|${identity.bolusId}|${outcome.name}"
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): QuickBolusCancelResult? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                QuickBolusCancelResult(
                    QuickBolusIdentity(parts[1].toInt(), parts[2].toLong(), parts[3].toInt()),
                    QuickBolusCancelOutcome.valueOf(parts[4]),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class QuickBolusValidation {
    MATCHED_ACTIVE_QUICK_BOLUS,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
}

fun CurrentBolusStatusResponse.isActiveQuickBolus(): Boolean =
    isActiveBolus() && bolusSource == BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS

fun validateQuickBolusForCancellation(
    identity: QuickBolusIdentity,
    currentPumpSid: Int,
    currentPumpSessionId: Long,
    response: CurrentBolusStatusResponse,
): QuickBolusValidation {
    if (identity.pumpSid != currentPumpSid ||
        !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId)
    ) return QuickBolusValidation.IDENTITY_MISMATCH
    if (!response.isActiveBolus()) return QuickBolusValidation.ALREADY_ENDED
    if (response.bolusId != identity.bolusId ||
        response.bolusSource != BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS
    ) return QuickBolusValidation.IDENTITY_MISMATCH
    return QuickBolusValidation.MATCHED_ACTIVE_QUICK_BOLUS
}

/** Durable pump-originated lifecycle; every transition is persisted before a modifying send. */
class QuickBolusOperationCoordinator(
    initial: QuickBolusOperation? = null,
    private val persist: (QuickBolusOperation) -> Unit,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    var operation: QuickBolusOperation? = initial
        private set

    @Synchronized
    fun observeActive(identity: QuickBolusIdentity, requestedMilliunits: Long) {
        require(requestedMilliunits > 0L)
        val current = operation
        if (current?.identity != identity) {
            val now = nowEpochMillis()
            update(
                QuickBolusOperation(
                    identity = identity,
                    requestedMilliunits = requestedMilliunits,
                    phase = QuickBolusOperationPhase.ACTIVE,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            return
        }
        if (current.phase in setOf(
                QuickBolusOperationPhase.TERMINAL,
                QuickBolusOperationPhase.RECONCILIATION_BLOCKED,
            )
        ) return

        val nextPhase = when (current.phase) {
            QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            QuickBolusOperationPhase.RECONCILING_TERMINAL ->
                if (current.cancellationRequested) {
                    QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED
                } else {
                    QuickBolusOperationPhase.ACTIVE
                }
            else -> current.phase
        }
        if (current.requestedMilliunits != requestedMilliunits || current.phase != nextPhase) {
            update(
                current.copy(
                    requestedMilliunits = requestedMilliunits,
                    phase = nextPhase,
                    updatedAtEpochMillis = nowEpochMillis(),
                ),
            )
        }
    }

    @Synchronized
    fun onCurrentCleared(pumpSid: Int, pumpSessionId: Long) {
        val current = operation ?: return
        if (!current.isUnresolved || current.identity.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.identity.pumpSessionId, pumpSessionId)
        ) return
        transition(current.identity, QuickBolusOperationPhase.RECONCILING_TERMINAL)
    }

    @Synchronized
    fun beginCancellation(identity: QuickBolusIdentity, send: () -> Unit): Boolean {
        val current = operation ?: return false
        if (current.identity != identity || !current.canRequestCancellation) return false
        update(
            current.copy(
                phase = QuickBolusOperationPhase.CANCELLATION_SENDING,
                cancellationRequested = true,
                updatedAtEpochMillis = nowEpochMillis(),
            ),
        )
        send()
        return true
    }

    @Synchronized
    fun markCancellationNotSent(identity: QuickBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase == QuickBolusOperationPhase.CANCELLATION_SENDING
        ) transition(identity, QuickBolusOperationPhase.CANCELLATION_NOT_COMPLETED)
    }

    @Synchronized
    fun markCancellationOutcomeUnknown(identity: QuickBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase == QuickBolusOperationPhase.CANCELLATION_SENDING
        ) transition(identity, QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN)
    }

    @Synchronized
    fun onCancellationResponse(identity: QuickBolusIdentity, accepted: Boolean) {
        val current = operation ?: return
        if (current.identity != identity || current.phase !in setOf(
                QuickBolusOperationPhase.CANCELLATION_SENDING,
                QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            )
        ) return
        update(
            current.copy(
                phase = if (accepted) QuickBolusOperationPhase.CANCELLATION_CONFIRMED
                else QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                cancellationAccepted = accepted,
                updatedAtEpochMillis = nowEpochMillis(),
            ),
        )
    }

    @Synchronized
    fun onDisconnected() {
        val current = operation ?: return
        if (current.phase == QuickBolusOperationPhase.CANCELLATION_SENDING) {
            transition(current.identity, QuickBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN)
        }
    }

    @Synchronized
    fun onPumpSession(pumpSid: Int, pumpSessionId: Long) {
        val current = operation ?: return
        if (!current.isUnresolved) return
        if (current.identity.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.identity.pumpSessionId, pumpSessionId)
        ) transition(current.identity, QuickBolusOperationPhase.RECONCILIATION_BLOCKED)
    }

    @Synchronized
    fun onTerminalEvidence(
        identity: QuickBolusIdentity,
        deliveredMilliunits: Long,
        terminalStatusId: Int? = null,
        terminalEndReasonId: Int? = null,
        terminalCompletionStatusId: Int? = null,
    ) {
        val current = operation ?: return
        if (current.identity != identity || !current.isUnresolved) return
        update(
            current.copy(
                phase = QuickBolusOperationPhase.TERMINAL,
                deliveredMilliunits = deliveredMilliunits.coerceAtLeast(0L),
                terminalStatusId = terminalStatusId ?: current.terminalStatusId,
                terminalEndReasonId = terminalEndReasonId ?: current.terminalEndReasonId,
                terminalCompletionStatusId =
                    terminalCompletionStatusId ?: current.terminalCompletionStatusId,
                updatedAtEpochMillis = nowEpochMillis(),
            ),
        )
    }

    @Synchronized
    fun acknowledgeTerminalResult(identity: QuickBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity && current.phase in setOf(
                QuickBolusOperationPhase.TERMINAL,
                QuickBolusOperationPhase.RECONCILIATION_BLOCKED,
            )
        ) {
            update(
                current.copy(
                    terminalResultAcknowledged = true,
                    updatedAtEpochMillis = nowEpochMillis(),
                ),
            )
        }
    }

    private fun transition(identity: QuickBolusIdentity, phase: QuickBolusOperationPhase) {
        val current = operation ?: return
        if (current.identity != identity || current.phase == phase) return
        update(current.copy(phase = phase, updatedAtEpochMillis = nowEpochMillis()))
    }

    private fun update(next: QuickBolusOperation) {
        persist(next)
        operation = next
    }
}
