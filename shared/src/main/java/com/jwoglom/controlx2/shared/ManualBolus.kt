package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog

/** Stable identity for one app-initiated manual bolus within one pump boot session. */
data class ManualBolusIdentity(
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
        fun decode(bytes: ByteArray): ManualBolusIdentity? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 4 || parts[0] != "1") return null
            return try {
                ManualBolusIdentity(
                    pumpSid = parts[1].toInt(),
                    pumpSessionId = parts[2].toLong(),
                    bolusId = parts[3].toInt(),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class ManualBolusCancelOutcome {
    CONFIRMED,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
    ACTIONS_DISABLED,
    DISCONNECTED,
    TIMEOUT,
    PUMP_REJECTED,
}

data class ManualBolusCancelResult(
    val identity: ManualBolusIdentity,
    val outcome: ManualBolusCancelOutcome,
) {
    fun encode(): ByteArray =
        "1|${identity.pumpSid}|${identity.pumpSessionId}|${identity.bolusId}|${outcome.name}"
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): ManualBolusCancelResult? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                ManualBolusCancelResult(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    outcome = ManualBolusCancelOutcome.valueOf(parts[4]),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class ManualBolusTerminalAcknowledgementStage { ALARM, RESULT }

data class ManualBolusTerminalAcknowledgement(
    val identity: ManualBolusIdentity,
    val stage: ManualBolusTerminalAcknowledgementStage,
) {
    fun encode(): ByteArray =
        "1|${identity.pumpSid}|${identity.pumpSessionId}|${identity.bolusId}|${stage.name}"
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): ManualBolusTerminalAcknowledgement? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                ManualBolusTerminalAcknowledgement(
                    identity = ManualBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    stage = ManualBolusTerminalAcknowledgementStage.valueOf(parts[4]),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class ManualBolusValidation {
    MATCHED_ACTIVE_MANUAL_BOLUS,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
}

fun CurrentBolusStatusResponse.isActiveManualBolus(): Boolean =
    isActiveBolus() && bolusSource == BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS

/** Revalidates pump-authoritative identity and source immediately before cancellation. */
fun validateManualBolusForCancellation(
    identity: ManualBolusIdentity,
    currentPumpSid: Int,
    currentPumpSessionId: Long,
    response: CurrentBolusStatusResponse,
): ManualBolusValidation {
    if (identity.pumpSid != currentPumpSid ||
        !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId)
    ) {
        return ManualBolusValidation.IDENTITY_MISMATCH
    }
    if (!response.isActiveBolus()) {
        return ManualBolusValidation.ALREADY_ENDED
    }
    if (response.bolusId != identity.bolusId ||
        response.bolusSource != BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
    ) {
        return ManualBolusValidation.IDENTITY_MISMATCH
    }
    return ManualBolusValidation.MATCHED_ACTIVE_MANUAL_BOLUS
}
