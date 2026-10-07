package com.jwoglom.controlx2.shared

/**
 * Durable ownership state for the pump's pre-initiation bolus permission.
 *
 * Permission release is deliberately separate from active-bolus cancellation. Once initiation
 * may have reached the pump, this state machine never sends a permission release for that ID.
 */
enum class BolusPermissionOperationPhase {
    REQUESTING,
    GRANTED,
    AUTOMATIC_BG_PENDING,
    AUTOMATIC_BG_SENDING,
    AUTOMATIC_BG_OUTCOME_UNKNOWN,
    RELEASE_PENDING,
    RELEASE_RECONCILING,
    RELEASE_SENDING,
    RELEASE_OUTCOME_UNKNOWN,
    RELEASED,
    DENIED,
    INITIATION_SUBMITTED,
    INITIATION_CONSUMED,
    INITIATION_REJECTED,
    // Legacy value written by builds which incorrectly treated an explicit pump rejection as
    // an ambiguous outcome. Decode migrates it to INITIATION_REJECTED.
    INITIATION_REJECTED_OUTCOME_UNKNOWN,
    BLOCKED_SESSION_MISMATCH,
}

data class BolusPermissionOperation(
    val pumpSid: Int,
    val pumpSessionId: Long,
    val bolusId: Int?,
    val phase: BolusPermissionOperationPhase,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val automaticBgMgdl: Int? = null,
    val automaticBgPumpTimeSeconds: Long? = null,
    val bgRecordType: BolusBgRecordType = if (automaticBgMgdl == null) {
        BolusBgRecordType.NONE
    } else {
        BolusBgRecordType.AUTOMATIC
    },
) {
    init {
        require(pumpSid >= 0) { "pumpSid must be non-negative" }
        require(pumpSessionId > 0L) { "pumpSessionId must be positive" }
        require(bolusId == null || bolusId > 0) { "bolusId must be positive when present" }
        require(createdAtEpochMillis > 0L) { "createdAtEpochMillis must be positive" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede creation"
        }
        require((automaticBgMgdl == null) == (automaticBgPumpTimeSeconds == null)) {
            "automatic BG and pump time must either both be present or both be absent"
        }
        require(automaticBgMgdl == null || automaticBgMgdl in 1..0xFFFF) {
            "automatic BG must fit the pump protocol's unsigned 16-bit field"
        }
        require(automaticBgPumpTimeSeconds == null || automaticBgPumpTimeSeconds > 0L) {
            "automatic BG pump time must be positive"
        }
        require((automaticBgMgdl == null) == (bgRecordType == BolusBgRecordType.NONE)) {
            "BG value and record type must either both be present or both be absent"
        }
    }

    val identity: ManualBolusIdentity?
        get() = bolusId?.let { ManualBolusIdentity(pumpSid, pumpSessionId, it) }

    val ownsUnusedPermission: Boolean
        get() = phase == BolusPermissionOperationPhase.GRANTED

    val needsReleaseRecovery: Boolean
        get() = phase in setOf(
            BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING,
            BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING,
            BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
            BolusPermissionOperationPhase.RELEASE_PENDING,
            BolusPermissionOperationPhase.RELEASE_RECONCILING,
            BolusPermissionOperationPhase.RELEASE_SENDING,
            BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
        )

    fun encode(): ByteArray = listOf(
        "3",
        pumpSid,
        pumpSessionId,
        bolusId ?: -1,
        phase.name,
        createdAtEpochMillis,
        updatedAtEpochMillis,
        automaticBgMgdl ?: -1,
        automaticBgPumpTimeSeconds ?: -1L,
        bgRecordType.name,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): BolusPermissionOperation? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if ((parts[0] == "1" && parts.size != 7) ||
                (parts[0] == "2" && parts.size != 9) ||
                (parts[0] == "3" && parts.size != 10) ||
                parts[0] !in setOf("1", "2", "3")
            ) return null
            return try {
                val encodedBolusId = parts[3].toInt()
                val encodedAutomaticBg = parts.getOrNull(7)?.toInt() ?: -1
                val encodedAutomaticBgPumpTime = parts.getOrNull(8)?.toLong() ?: -1L
                BolusPermissionOperation(
                    pumpSid = parts[1].toInt(),
                    pumpSessionId = parts[2].toLong(),
                    bolusId = encodedBolusId.takeIf { it > 0 },
                    phase = when (parts[4]) {
                        "INITIATION_REJECTED_OUTCOME_UNKNOWN" ->
                            BolusPermissionOperationPhase.INITIATION_REJECTED
                        else -> BolusPermissionOperationPhase.valueOf(parts[4])
                    },
                    createdAtEpochMillis = parts[5].toLong(),
                    updatedAtEpochMillis = parts[6].toLong(),
                    automaticBgMgdl = encodedAutomaticBg.takeIf { it > 0 },
                    automaticBgPumpTimeSeconds = encodedAutomaticBgPumpTime.takeIf { it > 0L },
                    bgRecordType = when {
                        encodedAutomaticBg <= 0 -> BolusBgRecordType.NONE
                        parts[0] == "3" -> BolusBgRecordType.valueOf(parts[9])
                        else -> BolusBgRecordType.AUTOMATIC
                    },
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * UI-to-pump-host request for an abandoned, not-yet-initiated bolus workflow.
 *
 * The identity prevents a delayed UI message from attaching a glucose value to a later bolus
 * permission. The record type preserves whether the final confirmed value was automatically
 * populated from CGM or manually entered from a meter.
 */
data class AbandonedBolusCleanupRequest(
    val identity: ManualBolusIdentity,
    val bgMgdl: Int?,
    val bgRecordType: BolusBgRecordType,
) {
    init {
        require(bgMgdl == null || bgMgdl in 1..0xFFFF) {
            "BG must fit the pump protocol's unsigned 16-bit field"
        }
        require((bgMgdl == null) == (bgRecordType == BolusBgRecordType.NONE)) {
            "BG value and record type must either both be present or both be absent"
        }
    }

    fun encode(): ByteArray = listOf(
        "2",
        identity.pumpSid,
        identity.pumpSessionId,
        identity.bolusId,
        bgMgdl ?: -1,
        bgRecordType.name,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): AbandonedBolusCleanupRequest? {
            if (bytes.isEmpty()) return null
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if ((parts[0] == "1" && parts.size != 5) ||
                (parts[0] == "2" && parts.size != 6) ||
                parts[0] !in setOf("1", "2")
            ) return null
            return try {
                AbandonedBolusCleanupRequest(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    bgMgdl = parts[4].toInt().takeIf { it > 0 },
                    bgRecordType = when {
                        parts[4].toInt() <= 0 -> BolusBgRecordType.NONE
                        parts[0] == "2" -> BolusBgRecordType.valueOf(parts[5])
                        else -> BolusBgRecordType.AUTOMATIC
                    },
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

sealed interface BolusPermissionAction {
    data object None : BolusPermissionAction
    data object RequestPermission : BolusPermissionAction
    data class RecordBg(
        val bolusId: Int,
        val bgMgdl: Int,
        val pumpTimeSeconds: Long,
        val recordType: BolusBgRecordType,
    ) : BolusPermissionAction
    data class ReleasePermission(val bolusId: Int) : BolusPermissionAction
}

/** Transport-independent permission lifecycle with persistence-before-send ordering. */
class BolusPermissionOperationCoordinator(
    initial: BolusPermissionOperation? = null,
    private val persist: (BolusPermissionOperation) -> Unit,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    var operation: BolusPermissionOperation? = initial
        private set

    /** Returns false when an existing unresolved lifecycle makes another request unsafe. */
    @Synchronized
    fun beginAcquisition(
        pumpSid: Int,
        pumpSessionId: Long,
        send: () -> Unit,
    ): Boolean = beginAcquisition(
        pumpSid = pumpSid,
        pumpSessionId = pumpSessionId,
        reconcileExistingGrant = false,
        send = send,
    )

    /**
     * Starts the permission transaction for a newly opened bolus workflow.
     *
     * The official Mobi app asks the pump on every fresh workflow, including after process death
     * or an exit which could not release an outstanding permission. The request is idempotent: the
     * pump may return the same still-reserved ID. This is deliberately different from resuming the
     * same foreground workflow, which does not call this method and keeps its current grant.
     */
    @Synchronized
    fun beginFreshWorkflowAcquisition(
        pumpSid: Int,
        pumpSessionId: Long,
        send: () -> Unit,
    ): Boolean = beginAcquisition(
        pumpSid = pumpSid,
        pumpSessionId = pumpSessionId,
        reconcileExistingGrant = true,
        send = send,
    )

    @Synchronized
    private fun beginAcquisition(
        pumpSid: Int,
        pumpSessionId: Long,
        reconcileExistingGrant: Boolean,
        send: () -> Unit,
    ): Boolean {
        val current = operation
        if (current != null && current.pumpSid == pumpSid &&
            pumpSessionIdsMatch(current.pumpSessionId, pumpSessionId)
        ) {
            when (current.phase) {
                BolusPermissionOperationPhase.REQUESTING,
                BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING,
                BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING,
                BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
                BolusPermissionOperationPhase.INITIATION_SUBMITTED,
                BolusPermissionOperationPhase.RELEASE_PENDING,
                BolusPermissionOperationPhase.RELEASE_RECONCILING,
                BolusPermissionOperationPhase.RELEASE_SENDING,
                BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
                -> return false

                BolusPermissionOperationPhase.GRANTED ->
                    if (!reconcileExistingGrant) return false

                else -> Unit
            }
        } else if (current != null && current.phase !in setOf(
                BolusPermissionOperationPhase.RELEASED,
                BolusPermissionOperationPhase.DENIED,
                BolusPermissionOperationPhase.INITIATION_CONSUMED,
                BolusPermissionOperationPhase.INITIATION_REJECTED,
                BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
            )
        ) {
            update(current.copy(
                phase = BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH,
                updatedAtEpochMillis = nextTimestamp(current),
            ))
            return false
        }

        val now = nowEpochMillis()
        update(BolusPermissionOperation(
            pumpSid = pumpSid,
            pumpSessionId = pumpSessionId,
            bolusId = null,
            phase = BolusPermissionOperationPhase.REQUESTING,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        ))
        send()
        return true
    }

    /**
     * Records a pump response. If cleanup was already queued, the returned action releases the
     * recovered ID rather than exposing it as a new bolus permission.
     */
    @Synchronized
    fun onPermissionResponse(
        pumpSid: Int,
        pumpSessionId: Long,
        granted: Boolean,
        bolusId: Int,
    ): BolusPermissionAction {
        val current = operation
        if (!granted || bolusId <= 0) {
            val now = nowEpochMillis()
            update(BolusPermissionOperation(
                pumpSid = pumpSid,
                pumpSessionId = pumpSessionId,
                bolusId = bolusId.takeIf { it > 0 },
                phase = BolusPermissionOperationPhase.DENIED,
                createdAtEpochMillis = current?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = maxOf(now, current?.updatedAtEpochMillis ?: now),
            ))
            return BolusPermissionAction.None
        }

        if (current != null &&
            (current.pumpSid != pumpSid ||
                !pumpSessionIdsMatch(current.pumpSessionId, pumpSessionId)) &&
            current.phase !in setOf(
                BolusPermissionOperationPhase.RELEASED,
                BolusPermissionOperationPhase.DENIED,
                BolusPermissionOperationPhase.INITIATION_CONSUMED,
            )
        ) {
            update(current.copy(
                phase = BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH,
                updatedAtEpochMillis = nextTimestamp(current),
            ))
            return BolusPermissionAction.None
        }

        val shouldRecordBg =
            current?.phase == BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING &&
                current.automaticBgMgdl != null &&
                current.automaticBgPumpTimeSeconds != null
        val wasRecoveringRelease = current?.needsReleaseRecovery == true
        val now = nowEpochMillis()
        val next = BolusPermissionOperation(
            pumpSid = pumpSid,
            // Preserve the first derived boot epoch as the stable identity when a later response
            // differs only because its two clock fields crossed a one-second boundary.
            pumpSessionId = current
                ?.takeIf {
                    it.pumpSid == pumpSid &&
                        pumpSessionIdsMatch(it.pumpSessionId, pumpSessionId)
                }
                ?.pumpSessionId
                ?: pumpSessionId,
            bolusId = bolusId,
            phase = when {
                shouldRecordBg -> BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING
                wasRecoveringRelease -> BolusPermissionOperationPhase.RELEASE_PENDING
                else -> BolusPermissionOperationPhase.GRANTED
            },
            createdAtEpochMillis = current?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = maxOf(now, current?.updatedAtEpochMillis ?: now),
            automaticBgMgdl = current?.automaticBgMgdl,
            automaticBgPumpTimeSeconds = current?.automaticBgPumpTimeSeconds,
            bgRecordType = current?.bgRecordType ?: BolusBgRecordType.NONE,
        )
        update(next)
        return when {
            shouldRecordBg -> BolusPermissionAction.RecordBg(
                bolusId = bolusId,
                bgMgdl = requireNotNull(next.automaticBgMgdl),
                pumpTimeSeconds = requireNotNull(next.automaticBgPumpTimeSeconds),
                recordType = next.bgRecordType,
            )
            wasRecoveringRelease -> BolusPermissionAction.ReleasePermission(bolusId)
            else -> BolusPermissionAction.None
        }
    }

    /**
     * Queues user-requested cleanup even when Bluetooth is currently unavailable. When the UI
     * supplies a same-identity automatic CGM value, the state machine persists that history write
     * ahead of release and will not permit the release write until a BG response arrives.
     */
    @Synchronized
    fun queueRelease(
        request: AbandonedBolusCleanupRequest? = null,
        automaticBgPumpTimeSeconds: Long? = null,
    ): BolusPermissionAction {
        val current = operation ?: return BolusPermissionAction.None
        if (request != null && current.identity != request.identity) {
            return BolusPermissionAction.None
        }
        if (current.phase in setOf(
                BolusPermissionOperationPhase.INITIATION_SUBMITTED,
                BolusPermissionOperationPhase.INITIATION_CONSUMED,
                BolusPermissionOperationPhase.INITIATION_REJECTED,
                BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
                BolusPermissionOperationPhase.RELEASED,
                BolusPermissionOperationPhase.DENIED,
                BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH,
            )
        ) return BolusPermissionAction.None

        when (current.phase) {
            BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING,
            BolusPermissionOperationPhase.RELEASE_SENDING,
            BolusPermissionOperationPhase.RELEASE_RECONCILING,
            -> return BolusPermissionAction.None

            BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
            BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN -> {
                transition(BolusPermissionOperationPhase.RELEASE_RECONCILING)
                return BolusPermissionAction.RequestPermission
            }

            else -> Unit
        }

        val cleanupBg = request?.bgMgdl
        val hasCleanupBg = cleanupBg != null && request.bgRecordType != BolusBgRecordType.NONE &&
            automaticBgPumpTimeSeconds != null &&
            automaticBgPumpTimeSeconds > 0L
        if (current.phase !in setOf(
                BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING,
                BolusPermissionOperationPhase.RELEASE_PENDING,
                BolusPermissionOperationPhase.RELEASE_RECONCILING,
                BolusPermissionOperationPhase.RELEASE_SENDING,
                BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
            )
        ) {
            update(current.copy(
                phase = if (hasCleanupBg) {
                    BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING
                } else {
                    BolusPermissionOperationPhase.RELEASE_PENDING
                },
                updatedAtEpochMillis = nextTimestamp(current),
                automaticBgMgdl = cleanupBg.takeIf { hasCleanupBg },
                automaticBgPumpTimeSeconds = automaticBgPumpTimeSeconds.takeIf { hasCleanupBg },
                bgRecordType = request?.bgRecordType?.takeIf { hasCleanupBg }
                    ?: BolusBgRecordType.NONE,
            ))
        }
        val queued = operation ?: return BolusPermissionAction.None
        val queuedBolusId = queued.bolusId ?: return BolusPermissionAction.None
        return if (queued.phase == BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING) {
            BolusPermissionAction.RecordBg(
                bolusId = queuedBolusId,
                bgMgdl = requireNotNull(queued.automaticBgMgdl),
                pumpTimeSeconds = requireNotNull(queued.automaticBgPumpTimeSeconds),
                recordType = queued.bgRecordType,
            )
        } else {
            BolusPermissionAction.ReleasePermission(queuedBolusId)
        }
    }

    /** Persists the automatic BG write before it is sent to the pump. */
    @Synchronized
    fun beginBgEntry(
        action: BolusPermissionAction.RecordBg,
        send: () -> Unit,
    ): Boolean {
        val current = operation ?: return false
        if (current.phase != BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING ||
            current.bolusId != action.bolusId ||
            current.automaticBgMgdl != action.bgMgdl ||
            current.automaticBgPumpTimeSeconds != action.pumpTimeSeconds ||
            current.bgRecordType != action.recordType
        ) return false
        transition(BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING)
        send()
        return true
    }

    /**
     * A response closes the BG-write stage before release is allowed. A non-success response is
     * retained in transport logs, but permission is still released so a history-write failure
     * cannot strand the user's next bolus workflow.
     */
    @Synchronized
    fun onAutomaticBgEntryResponse(): BolusPermissionAction {
        val current = operation ?: return BolusPermissionAction.None
        val bolusId = current.bolusId ?: return BolusPermissionAction.None
        if (current.phase != BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING) {
            return BolusPermissionAction.None
        }
        transition(BolusPermissionOperationPhase.RELEASE_PENDING)
        return BolusPermissionAction.ReleasePermission(bolusId)
    }

    @Synchronized
    fun markAutomaticBgEntryOutcomeUnknown() {
        if (operation?.phase == BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING) {
            transition(BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN)
        }
    }

    /** Persists RELEASE_SENDING before the Bluetooth write is attempted. */
    @Synchronized
    fun beginRelease(bolusId: Int, send: () -> Unit): Boolean {
        val current = operation ?: return false
        if (current.bolusId != bolusId || current.phase !in setOf(
                BolusPermissionOperationPhase.RELEASE_PENDING,
                BolusPermissionOperationPhase.RELEASE_RECONCILING,
                BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
            )
        ) return false
        transition(BolusPermissionOperationPhase.RELEASE_SENDING)
        send()
        return true
    }

    @Synchronized
    fun markInitiationSubmitted(identity: ManualBolusIdentity): Boolean {
        val current = operation ?: return false
        if (current.identity != identity || current.phase != BolusPermissionOperationPhase.GRANTED) {
            return false
        }
        transition(BolusPermissionOperationPhase.INITIATION_SUBMITTED)
        return true
    }

    /**
     * Returns the persisted permission identity for a freshly observed pump/session/bolus tuple.
     * The caller must use this canonical identity for the subsequent durable initiation lifecycle.
     */
    @Synchronized
    fun grantedIdentity(
        pumpSid: Int,
        observedPumpSessionId: Long,
        bolusId: Int,
    ): ManualBolusIdentity? {
        val current = operation ?: return null
        if (current.phase != BolusPermissionOperationPhase.GRANTED ||
            current.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.pumpSessionId, observedPumpSessionId) ||
            current.bolusId != bolusId
        ) return null
        return current.identity
    }

    /** Used only when a local gate proves the initiation never reached the transport. */
    @Synchronized
    fun markInitiationNotSent(identity: ManualBolusIdentity) {
        val current = operation ?: return
        if (current.identity == identity &&
            current.phase == BolusPermissionOperationPhase.INITIATION_SUBMITTED
        ) {
            transition(BolusPermissionOperationPhase.GRANTED)
        }
    }

    @Synchronized
    fun onInitiationResponse(identity: ManualBolusIdentity, accepted: Boolean) {
        val current = operation ?: return
        if (current.identity != identity ||
            current.phase != BolusPermissionOperationPhase.INITIATION_SUBMITTED
        ) return
        transition(
            if (accepted) BolusPermissionOperationPhase.INITIATION_CONSUMED
            else BolusPermissionOperationPhase.INITIATION_REJECTED,
        )
    }

    @Synchronized
    fun onReleaseResponse(success: Boolean) {
        val current = operation ?: return
        if (current.phase !in setOf(
                BolusPermissionOperationPhase.RELEASE_SENDING,
                BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
                BolusPermissionOperationPhase.RELEASE_RECONCILING,
            )
        ) return
        // A reconciliation request may already be in flight. Even if a late release success now
        // arrives, its permission response can allocate/return an ID which must also be released.
        if (current.phase == BolusPermissionOperationPhase.RELEASE_RECONCILING && success) return
        transition(
            if (success) BolusPermissionOperationPhase.RELEASED
            else BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
        )
    }

    @Synchronized
    fun markReleaseOutcomeUnknown() {
        if (operation?.phase == BolusPermissionOperationPhase.RELEASE_SENDING) {
            transition(BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN)
        }
    }

    @Synchronized
    fun onDisconnected() {
        when (operation?.phase) {
            BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING ->
                transition(BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN)

            BolusPermissionOperationPhase.RELEASE_SENDING,
            BolusPermissionOperationPhase.RELEASE_RECONCILING,
            -> transition(BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN)

            else -> Unit
        }
    }

    /**
     * On reconnect, an idempotent permission request determines the currently reserved ID. The
     * response is then released once; this avoids blindly repeating an unacknowledged release.
     */
    @Synchronized
    fun onPumpSession(
        pumpSid: Int,
        pumpSessionId: Long,
        permissionRequestInFlight: Boolean = false,
    ): BolusPermissionAction {
        val current = operation ?: return BolusPermissionAction.None
        if (current.pumpSid != pumpSid ||
            !pumpSessionIdsMatch(current.pumpSessionId, pumpSessionId)
        ) {
            if (current.phase !in setOf(
                    BolusPermissionOperationPhase.RELEASED,
                    BolusPermissionOperationPhase.DENIED,
                    BolusPermissionOperationPhase.INITIATION_CONSUMED,
                    BolusPermissionOperationPhase.INITIATION_REJECTED,
                    BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
                )
            ) {
                update(current.copy(
                    phase = BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH,
                    updatedAtEpochMillis = nextTimestamp(current),
                ))
            }
            return BolusPermissionAction.None
        }
        if (current.phase == BolusPermissionOperationPhase.REQUESTING) {
            // A routine TimeSinceReset refresh can arrive while the original request is still
            // awaiting its response. Only resend after a reconnect, where the transport-level
            // in-flight flag has been cleared and the original outcome is genuinely unknown.
            return if (permissionRequestInFlight) {
                BolusPermissionAction.None
            } else {
                BolusPermissionAction.RequestPermission
            }
        }
        if (current.phase == BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING) {
            val bolusId = current.bolusId ?: return BolusPermissionAction.None
            return BolusPermissionAction.RecordBg(
                bolusId = bolusId,
                bgMgdl = current.automaticBgMgdl ?: return BolusPermissionAction.None,
                pumpTimeSeconds = current.automaticBgPumpTimeSeconds
                    ?: return BolusPermissionAction.None,
                recordType = current.bgRecordType,
            )
        }
        if (!current.needsReleaseRecovery) return BolusPermissionAction.None
        if (current.phase != BolusPermissionOperationPhase.RELEASE_RECONCILING) {
            transition(BolusPermissionOperationPhase.RELEASE_RECONCILING)
        }
        return BolusPermissionAction.RequestPermission
    }

    private fun transition(phase: BolusPermissionOperationPhase) {
        val current = operation ?: return
        update(current.copy(phase = phase, updatedAtEpochMillis = nextTimestamp(current)))
    }

    private fun nextTimestamp(current: BolusPermissionOperation): Long =
        maxOf(nowEpochMillis(), current.updatedAtEpochMillis)

    private fun update(next: BolusPermissionOperation) {
        operation = next
        persist(next)
    }
}
