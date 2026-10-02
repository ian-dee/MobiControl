package com.jwoglom.controlx2.shared

/**
 * Durable lifecycle for one app-initiated manual bolus.
 *
 * A Bluetooth write completing does not prove that the pump received a command, and a missing
 * response does not prove that it did not.  The ambiguous phases deliberately require fresh,
 * pump-authoritative status before the app offers another insulin-affecting command.
 */
enum class ManualBolusOperationPhase {
    INITIATION_SENDING,
    INITIATION_OUTCOME_UNKNOWN,
    ACTIVE,
    CANCELLATION_SENDING,
    CANCELLATION_OUTCOME_UNKNOWN,
    CANCELLATION_NOT_COMPLETED,
    CANCELLATION_CONFIRMED,
    TERMINAL,
    INITIATION_REJECTED,
    RECONCILIATION_BLOCKED,
}

enum class ManualBolusTerminalCause {
    UNKNOWN,
    ALARM_STOPPED,
    NO_MATCHING_DELIVERY_RECORD,
    FINAL_AMOUNT_UNAVAILABLE,
}

data class ManualBolusOperation(
    val identity: ManualBolusIdentity,
    val requestedMilliunits: Long,
    val phase: ManualBolusOperationPhase,
    val deliveredMilliunits: Long? = null,
    val terminalStatusId: Int? = null,
    val terminalEndReasonId: Int? = null,
    val terminalCompletionStatusId: Int? = null,
    val terminalCause: ManualBolusTerminalCause = ManualBolusTerminalCause.UNKNOWN,
    val terminalAlarmId: Long? = null,
    val terminalAlarmAcknowledged: Boolean = false,
    val terminalResultAcknowledged: Boolean = false,
    val cancellationOutcome: ManualBolusCancelOutcome? = null,
    val historySequenceAtOrBeforeInitiation: Long? = null,
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
        require(historySequenceAtOrBeforeInitiation == null ||
            historySequenceAtOrBeforeInitiation in 0L..0xFFFF_FFFFL
        ) {
            "historySequenceAtOrBeforeInitiation must fit the pump sequence range"
        }
    }

    val isUnresolved: Boolean
        get() = phase !in setOf(
            ManualBolusOperationPhase.TERMINAL,
            ManualBolusOperationPhase.INITIATION_REJECTED,
        )

    val shouldDisplayPanel: Boolean
        get() = phase !in setOf(
            ManualBolusOperationPhase.TERMINAL,
            ManualBolusOperationPhase.INITIATION_REJECTED,
        )

    val canRequestCancellation: Boolean
        get() = phase == ManualBolusOperationPhase.ACTIVE

    val canRetryCancellation: Boolean
        get() = phase == ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED

    fun encode(): ByteArray = listOf(
        "3",
        identity.pumpSid,
        identity.pumpSessionId,
        identity.bolusId,
        requestedMilliunits,
        phase.name,
        deliveredMilliunits ?: -1L,
        terminalStatusId ?: -1,
        terminalEndReasonId ?: -1,
        terminalCompletionStatusId ?: -1,
        terminalCause.name,
        terminalAlarmId ?: -1L,
        terminalAlarmAcknowledged,
        terminalResultAcknowledged,
        cancellationOutcome?.name ?: "NONE",
        historySequenceAtOrBeforeInitiation ?: -1L,
        createdAtEpochMillis,
        updatedAtEpochMillis,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): ManualBolusOperation? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts[0] == "1") return decodeVersion1(parts)
            if (parts[0] == "2") return decodeVersion2(parts)
            if (parts.size != 18 || parts[0] != "3") return null
            return try {
                val delivered = parts[6].toLong()
                val terminalStatus = parts[7].toInt()
                val terminalEndReason = parts[8].toInt()
                val terminalCompletionStatus = parts[9].toInt()
                val terminalAlarmId = parts[11].toLong()
                val historySequenceAtOrBeforeInitiation = parts[15].toLong()
                ManualBolusOperation(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    requestedMilliunits = parts[4].toLong(),
                    phase = ManualBolusOperationPhase.valueOf(parts[5]),
                    deliveredMilliunits = delivered.takeIf { it >= 0L },
                    terminalStatusId = terminalStatus.takeIf { it >= 0 },
                    terminalEndReasonId = terminalEndReason.takeIf { it >= 0 },
                    terminalCompletionStatusId = terminalCompletionStatus.takeIf { it >= 0 },
                    terminalCause = ManualBolusTerminalCause.valueOf(parts[10]),
                    terminalAlarmId = terminalAlarmId.takeIf { it >= 0L },
                    terminalAlarmAcknowledged = parts[12].toBooleanStrict(),
                    terminalResultAcknowledged = parts[13].toBooleanStrict(),
                    cancellationOutcome = parts[14]
                        .takeUnless { it == "NONE" }
                        ?.let(ManualBolusCancelOutcome::valueOf),
                    historySequenceAtOrBeforeInitiation =
                        historySequenceAtOrBeforeInitiation.takeIf { it >= 0L },
                    createdAtEpochMillis = parts[16].toLong(),
                    updatedAtEpochMillis = parts[17].toLong(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun decodeVersion2(parts: List<String>): ManualBolusOperation? {
            if (parts.size != 16) return null
            return try {
                val delivered = parts[6].toLong()
                val terminalStatus = parts[7].toInt()
                val terminalEndReason = parts[8].toInt()
                val terminalCompletionStatus = parts[9].toInt()
                val terminalAlarmId = parts[11].toLong()
                ManualBolusOperation(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    requestedMilliunits = parts[4].toLong(),
                    phase = ManualBolusOperationPhase.valueOf(parts[5]),
                    deliveredMilliunits = delivered.takeIf { it >= 0L },
                    terminalStatusId = terminalStatus.takeIf { it >= 0 },
                    terminalEndReasonId = terminalEndReason.takeIf { it >= 0 },
                    terminalCompletionStatusId = terminalCompletionStatus.takeIf { it >= 0 },
                    terminalCause = ManualBolusTerminalCause.valueOf(parts[10]),
                    terminalAlarmId = terminalAlarmId.takeIf { it >= 0L },
                    terminalAlarmAcknowledged = parts[12].toBooleanStrict(),
                    terminalResultAcknowledged = parts[13].toBooleanStrict(),
                    createdAtEpochMillis = parts[14].toLong(),
                    updatedAtEpochMillis = parts[15].toLong(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun decodeVersion1(parts: List<String>): ManualBolusOperation? {
            if (parts.size != 10) return null
            return try {
                val delivered = parts[6].toLong()
                val terminalStatus = parts[7].toInt()
                ManualBolusOperation(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    requestedMilliunits = parts[4].toLong(),
                    phase = ManualBolusOperationPhase.valueOf(parts[5]),
                    deliveredMilliunits = delivered.takeIf { it >= 0L },
                    terminalStatusId = terminalStatus.takeIf { it >= 0 },
                    createdAtEpochMillis = parts[8].toLong(),
                    updatedAtEpochMillis = parts[9].toLong(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Small transport-independent state machine. The send callbacks make the important ordering
 * explicit: state is durably persisted before either insulin-affecting command is handed to BLE.
 */
class ManualBolusOperationCoordinator(
    initial: ManualBolusOperation? = null,
    private val persist: (ManualBolusOperation) -> Unit,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    var operation: ManualBolusOperation? = initial
        private set

    init {
        initial?.let { restored ->
            val restoredPhase = when (restored.phase) {
                ManualBolusOperationPhase.INITIATION_SENDING ->
                    ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN
                ManualBolusOperationPhase.CANCELLATION_SENDING ->
                    ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN
                else -> null
            }
            if (restoredPhase != null) {
                update(
                    restored.copy(
                        phase = restoredPhase,
                        updatedAtEpochMillis = nowEpochMillis()
                            .coerceAtLeast(restored.updatedAtEpochMillis),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun beginInitiation(
        identity: ManualBolusIdentity,
        requestedMilliunits: Long,
        historySequenceAtOrBeforeInitiation: Long? = null,
        send: () -> Unit,
    ) {
        val existing = operation
        require(existing?.isUnresolved != true) {
            "An unresolved manual bolus operation already exists"
        }
        val now = nowEpochMillis()
        update(
            ManualBolusOperation(
                identity = identity,
                requestedMilliunits = requestedMilliunits,
                phase = ManualBolusOperationPhase.INITIATION_SENDING,
                historySequenceAtOrBeforeInitiation = historySequenceAtOrBeforeInitiation,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        send()
    }

    @Synchronized
    fun markInitiationNotSent(identity: ManualBolusIdentity) {
        transition(identity, ManualBolusOperationPhase.INITIATION_REJECTED)
    }

    @Synchronized
    fun markInitiationOutcomeUnknown(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase == ManualBolusOperationPhase.INITIATION_SENDING
        ) {
            transition(identity, ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN)
        }
    }

    @Synchronized
    fun onInitiationResponse(identity: ManualBolusIdentity, accepted: Boolean) {
        val current = operation ?: return
        if (current.identity != identity || current.phase !in setOf(
                ManualBolusOperationPhase.INITIATION_SENDING,
                ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN,
            )
        ) return
        transition(
            identity,
            if (accepted) ManualBolusOperationPhase.ACTIVE
            else ManualBolusOperationPhase.INITIATION_REJECTED,
        )
    }

    /** Returns false without sending unless fresh evidence has made cancellation safe. */
    @Synchronized
    fun beginCancellation(identity: ManualBolusIdentity, send: () -> Unit): Boolean {
        val current = operation ?: return false
        if (current.identity != identity ||
            (!current.canRequestCancellation && !current.canRetryCancellation)
        ) return false
        transition(identity, ManualBolusOperationPhase.CANCELLATION_SENDING)
        send()
        return true
    }

    @Synchronized
    fun markCancellationOutcomeUnknown(
        identity: ManualBolusIdentity,
        outcome: ManualBolusCancelOutcome? = null,
    ) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase in setOf(
                ManualBolusOperationPhase.CANCELLATION_SENDING,
                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            )
        ) {
            update(
                current.copy(
                    phase = ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                    cancellationOutcome = outcome ?: current.cancellationOutcome,
                    updatedAtEpochMillis = nextTimestamp(current),
                ),
            )
        }
    }

    /** Restores the cancellable active state when a local safety gate proves no send occurred. */
    @Synchronized
    fun markCancellationNotSent(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase == ManualBolusOperationPhase.CANCELLATION_SENDING
        ) {
            transition(identity, ManualBolusOperationPhase.ACTIVE)
        }
    }

    /** Fresh current status showed that delivery had ended before a cancel write was needed. */
    @Synchronized
    fun onCancellationAlreadyEnded(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity != identity || current.phase !in setOf(
                ManualBolusOperationPhase.ACTIVE,
                ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
            )
        ) return
        update(
            current.copy(
                phase = ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                cancellationOutcome = ManualBolusCancelOutcome.ALREADY_ENDED,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    @Synchronized
    fun onCancellationResponse(
        identity: ManualBolusIdentity,
        outcome: ManualBolusCancelOutcome,
    ) {
        val current = operation ?: return
        if (current.identity != identity || current.phase !in setOf(
                ManualBolusOperationPhase.CANCELLATION_SENDING,
                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
            )
        ) return
        update(
            current.copy(
                phase = if (outcome == ManualBolusCancelOutcome.CONFIRMED) {
                    ManualBolusOperationPhase.CANCELLATION_CONFIRMED
                } else {
                    ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN
                },
                cancellationOutcome = outcome,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    /** Records a disconnect without guessing whether the pump acted on the last write. */
    @Synchronized
    fun onDisconnected() {
        val current = operation ?: return
        when (current.phase) {
            ManualBolusOperationPhase.INITIATION_SENDING ->
                transition(current.identity, ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN)
            ManualBolusOperationPhase.CANCELLATION_SENDING ->
                transition(current.identity, ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN)
            else -> Unit
        }
    }

    /** Returns true when reconnect reconciliation should query current, last, and history. */
    @Synchronized
    fun onPumpSession(pumpSid: Int, pumpSessionId: Long): Boolean {
        val current = operation ?: return false
        if (!current.isUnresolved) return false
        if (current.identity.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.identity.pumpSessionId, pumpSessionId)
        ) {
            transition(current.identity, ManualBolusOperationPhase.RECONCILIATION_BLOCKED)
            return false
        }
        return true
    }

    /** Fresh current-status evidence for the exact pump session, bolus ID, and source. */
    @Synchronized
    fun onActiveEvidence(identity: ManualBolusIdentity, requestedMilliunits: Long) {
        val current = operation ?: return
        if (current.identity != identity) return
        val nextPhase = when (current.phase) {
            ManualBolusOperationPhase.INITIATION_SENDING,
            ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN ->
                ManualBolusOperationPhase.ACTIVE
            ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN ->
                ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED
            ManualBolusOperationPhase.CANCELLATION_CONFIRMED ->
                if (nowEpochMillis() - current.updatedAtEpochMillis >= 8_000L) {
                    ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED
                } else {
                    current.phase
            }
            else -> current.phase
        }
        val nextRequestedMilliunits = requestedMilliunits.takeIf { it > 0L }
            ?: current.requestedMilliunits
        if (nextPhase == current.phase &&
            nextRequestedMilliunits == current.requestedMilliunits
        ) return
        update(
            current.copy(
                requestedMilliunits = nextRequestedMilliunits,
                phase = nextPhase,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    /** Exact-ID terminal evidence from LastBolusStatus or a bolus history record. */
    @Synchronized
    fun onTerminalEvidence(
        identity: ManualBolusIdentity,
        deliveredMilliunits: Long,
        terminalStatusId: Int,
        terminalEndReasonId: Int? = null,
        terminalCompletionStatusId: Int? = null,
    ) {
        val current = operation ?: return
        if (current.identity != identity) return
        val replacesProvisionalTerminal = current.terminalCause in setOf(
            ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD,
            ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE,
        )
        update(
            current.copy(
                phase = ManualBolusOperationPhase.TERMINAL,
                deliveredMilliunits = deliveredMilliunits.coerceAtLeast(0L),
                terminalStatusId = terminalStatusId,
                terminalEndReasonId = terminalEndReasonId ?: current.terminalEndReasonId,
                terminalCompletionStatusId =
                    terminalCompletionStatusId ?: current.terminalCompletionStatusId,
                terminalCause = when {
                    terminalStatusId == ALARM_STOPPED_STATUS_ID ||
                        terminalEndReasonId == ALARM_STOPPED_END_REASON_ID ||
                        terminalCompletionStatusId == ALARM_STOPPED_COMPLETION_STATUS_ID ->
                        ManualBolusTerminalCause.ALARM_STOPPED
                    current.terminalCause in setOf(
                        ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD,
                        ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE,
                    ) ->
                        ManualBolusTerminalCause.UNKNOWN
                    else -> current.terminalCause
                },
                terminalResultAcknowledged = if (replacesProvisionalTerminal) {
                    false
                } else {
                    current.terminalResultAcknowledged
                },
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    /**
     * Finishes an ambiguous operation only after the caller has established all three pieces of
     * pump-authoritative evidence: the same pump session is idle, Last Bolus does not identify this
     * operation, and a complete recent history window spanning its start contains no matching ID.
     * No delivered amount is invented for this outcome.
     */
    @Synchronized
    fun onHistoryCoverageCompleteWithoutMatch(pumpSid: Int, pumpSessionId: Long) {
        val current = operation ?: return
        if (!current.isUnresolved || current.identity.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.identity.pumpSessionId, pumpSessionId)
        ) return
        if (current.phase !in setOf(
                ManualBolusOperationPhase.INITIATION_OUTCOME_UNKNOWN,
                ManualBolusOperationPhase.CANCELLATION_OUTCOME_UNKNOWN,
                ManualBolusOperationPhase.CANCELLATION_CONFIRMED,
            )
        ) return
        update(
            current.copy(
                phase = ManualBolusOperationPhase.TERMINAL,
                deliveredMilliunits = null,
                terminalStatusId = null,
                terminalEndReasonId = null,
                terminalCompletionStatusId = null,
                terminalCause = ManualBolusTerminalCause.NO_MATCHING_DELIVERY_RECORD,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    /**
     * Migrates the one legacy state which proves a cancel reply was received but did not retain
     * whether it meant cancelled or already ended. The caller must first obtain two independent,
     * complete negative pump snapshots. We deliberately do not invent a delivered amount.
     */
    @Synchronized
    fun onLegacyCancellationEndedWithoutAmount(pumpSid: Int, pumpSessionId: Long) {
        val current = operation ?: return
        if (current.phase != ManualBolusOperationPhase.CANCELLATION_CONFIRMED ||
            current.historySequenceAtOrBeforeInitiation != null ||
            current.identity.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.identity.pumpSessionId, pumpSessionId)
        ) return
        update(
            current.copy(
                phase = ManualBolusOperationPhase.TERMINAL,
                deliveredMilliunits = null,
                terminalStatusId = null,
                terminalEndReasonId = null,
                terminalCompletionStatusId = null,
                terminalCause = ManualBolusTerminalCause.FINAL_AMOUNT_UNAVAILABLE,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    @Synchronized
    fun onTerminalAlarmEvidence(alarmId: Long) {
        val current = operation ?: return
        if (current.phase != ManualBolusOperationPhase.TERMINAL ||
            current.terminalCause != ManualBolusTerminalCause.ALARM_STOPPED
        ) return
        if (current.terminalAlarmId == alarmId && !current.terminalAlarmAcknowledged) return
        update(
            current.copy(
                terminalAlarmId = alarmId,
                terminalAlarmAcknowledged = false,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    @Synchronized
    fun acknowledgeTerminalAlarm(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity != identity ||
            current.phase != ManualBolusOperationPhase.TERMINAL ||
            current.terminalCause != ManualBolusTerminalCause.ALARM_STOPPED ||
            current.terminalAlarmAcknowledged
        ) return
        update(
            current.copy(
                terminalAlarmAcknowledged = true,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    @Synchronized
    fun acknowledgeTerminalResult(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity != identity ||
            current.phase != ManualBolusOperationPhase.TERMINAL ||
            current.terminalResultAcknowledged
        ) return
        update(
            current.copy(
                terminalResultAcknowledged = true,
                updatedAtEpochMillis = nextTimestamp(current),
            ),
        )
    }

    private fun transition(identity: ManualBolusIdentity, phase: ManualBolusOperationPhase) {
        val current = operation ?: return
        if (current.identity != identity || current.phase == phase) return
        update(current.copy(phase = phase, updatedAtEpochMillis = nextTimestamp(current)))
    }

    private fun nextTimestamp(current: ManualBolusOperation): Long =
        nowEpochMillis().coerceAtLeast(current.updatedAtEpochMillis)

    private fun update(next: ManualBolusOperation) {
        operation = next
        persist(next)
    }
}

private const val ALARM_STOPPED_END_REASON_ID = 1
private const val ALARM_STOPPED_COMPLETION_STATUS_ID = 1
private const val ALARM_STOPPED_STATUS_ID = 1
