package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlin.math.abs

/**
 * Stable identity for one bolus within one pump boot session.
 *
 * Bolus IDs can be reused after a pump restart, so [pumpSessionId] is the pump's
 * calculated boot epoch rather than an Android BLE connection identifier. This
 * keeps notification deduplication stable across ordinary Bluetooth reconnects.
 */
data class AutomaticBolusIdentity(
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
        fun decode(bytes: ByteArray): AutomaticBolusIdentity? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 4 || parts[0] != "1") return null
            return try {
                AutomaticBolusIdentity(
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

enum class AutomaticBolusCancelOutcome {
    CONFIRMED,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
    ACTIONS_DISABLED,
    DISCONNECTED,
    TIMEOUT,
    PUMP_REJECTED,
}

data class AutomaticBolusCancelResult(
    val identity: AutomaticBolusIdentity,
    val outcome: AutomaticBolusCancelOutcome,
) {
    fun encode(): ByteArray =
        "1|${identity.pumpSid}|${identity.pumpSessionId}|${identity.bolusId}|${outcome.name}"
            .toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): AutomaticBolusCancelResult? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 5 || parts[0] != "1") return null
            return try {
                AutomaticBolusCancelResult(
                    identity = AutomaticBolusIdentity(
                        pumpSid = parts[1].toInt(),
                        pumpSessionId = parts[2].toLong(),
                        bolusId = parts[3].toInt(),
                    ),
                    outcome = AutomaticBolusCancelOutcome.valueOf(parts[4]),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class AutomaticBolusValidation {
    MATCHED_ACTIVE_AUTOMATIC_BOLUS,
    ALREADY_ENDED,
    IDENTITY_MISMATCH,
}

/** Pump boot epoch, stable across BLE reconnects and different from a wall-clock setting change. */
fun TimeSinceResetResponse.pumpSessionId(): Long = currentTime - pumpTimeSecondsSinceReset

/**
 * The pump reports the two clock values used to derive [pumpSessionId] independently. A response
 * arriving on a second boundary can therefore move the derived boot epoch by a second even though
 * the pump has not restarted. Keep the first captured value canonical and tolerate only this small
 * measurement skew; a real restart changes the boot epoch by much more than this window.
 */
const val PUMP_SESSION_ID_MAX_SKEW_SECONDS = 2L

fun pumpSessionIdsMatch(expected: Long, observed: Long): Boolean =
    expected > 0L && observed > 0L && abs(expected - observed) <= PUMP_SESSION_ID_MAX_SKEW_SECONDS

fun CurrentBolusStatusResponse.isActiveBolus(): Boolean =
    isValid && (status == CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING ||
        status == CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING)

fun CurrentBolusStatusResponse.isActiveAutomaticBolus(): Boolean =
    isActiveBolus() && bolusSource == BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS

/**
 * Revalidates the pump-authoritative status immediately before cancellation.
 * The caller separately verifies the pump SID/session and BLE session token.
 */
fun validateAutomaticBolusForCancellation(
    identity: AutomaticBolusIdentity,
    currentPumpSid: Int,
    currentPumpSessionId: Long,
    response: CurrentBolusStatusResponse,
): AutomaticBolusValidation {
    if (identity.pumpSid != currentPumpSid ||
        !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId)
    ) {
        return AutomaticBolusValidation.IDENTITY_MISMATCH
    }
    if (!response.isActiveBolus()) {
        return AutomaticBolusValidation.ALREADY_ENDED
    }
    if (response.bolusId != identity.bolusId ||
        response.bolusSource != BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
    ) {
        return AutomaticBolusValidation.IDENTITY_MISMATCH
    }
    return AutomaticBolusValidation.MATCHED_ACTIVE_AUTOMATIC_BOLUS
}
