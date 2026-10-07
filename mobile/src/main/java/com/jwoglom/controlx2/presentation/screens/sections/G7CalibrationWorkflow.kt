package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingType
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CgmStatusV2Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmBleCalibrationEvtG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationG7HistoryLog
import kotlin.math.abs

internal const val MIN_G7_CALIBRATION_MGDL = 20
internal const val MAX_G7_CALIBRATION_MGDL = 600

internal enum class G7CalibrationPhase {
    SENDING,
    ACKNOWLEDGED,
    OUTCOME_UNKNOWN,
    REJECTED,
}

/**
 * Durable record for the one calibration write which may have reached the pump.
 *
 * ACKNOWLEDGED deliberately means only that the request response was successful. It is not a
 * claim that the sensor accepted the calibration; that requires a newer calibration timestamp or
 * matching pump history.
 */
internal data class G7CalibrationOperation(
    val pumpSid: Int,
    val sensorStartedTimestamp: Long,
    val bgMgdl: Int,
    val pumpCurrentTime: Long,
    val pumpTimeSecondsSinceReset: Long,
    val priorLastCalibrationTimestamp: Long,
    val phase: G7CalibrationPhase,
    val createdAtEpochMillis: Long,
    val responseStatus: Int? = null,
) {
    init {
        require(pumpSid >= 0)
        require(sensorStartedTimestamp > 0L)
        require(bgMgdl in MIN_G7_CALIBRATION_MGDL..MAX_G7_CALIBRATION_MGDL)
        require(pumpCurrentTime > 0L)
        require(pumpTimeSecondsSinceReset > 0L)
        require(priorLastCalibrationTimestamp >= 0L)
        require(createdAtEpochMillis > 0L)
    }

    val preventsResend: Boolean
        get() = phase in setOf(
            G7CalibrationPhase.SENDING,
            G7CalibrationPhase.ACKNOWLEDGED,
            G7CalibrationPhase.OUTCOME_UNKNOWN,
        )

    fun encode(): String = listOf(
        "1",
        pumpSid,
        sensorStartedTimestamp,
        bgMgdl,
        pumpCurrentTime,
        pumpTimeSecondsSinceReset,
        priorLastCalibrationTimestamp,
        phase.name,
        createdAtEpochMillis,
        responseStatus ?: -1,
    ).joinToString("|")

    companion object {
        fun decode(value: String?): G7CalibrationOperation? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 10 || parts[0] != "1") return null
            return try {
                G7CalibrationOperation(
                    pumpSid = parts[1].toInt(),
                    sensorStartedTimestamp = parts[2].toLong(),
                    bgMgdl = parts[3].toInt(),
                    pumpCurrentTime = parts[4].toLong(),
                    pumpTimeSecondsSinceReset = parts[5].toLong(),
                    priorLastCalibrationTimestamp = parts[6].toLong(),
                    phase = G7CalibrationPhase.valueOf(parts[7]),
                    createdAtEpochMillis = parts[8].toLong(),
                    responseStatus = parts[9].toInt().takeIf { it >= 0 },
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal enum class G7CalibrationAvailability {
    AVAILABLE,
    PUMP_DISCONNECTED,
    SESSION_NOT_ACTIVE,
    NOT_G7,
    SESSION_IDENTITY_UNAVAILABLE,
    NO_CURRENT_READING,
    BOLUS_UNRESOLVED,
    CALIBRATION_UNRESOLVED,
}

internal data class G7CalibrationReadinessCheck(
    val acceptEventsAfterSequence: Long,
    val sensorStartedTimestamp: Long,
) {
    init {
        require(acceptEventsAfterSequence >= 0L)
        require(sensorStartedTimestamp > 0L)
    }
}

internal fun isG7CalibrationActionEnabled(
    availability: G7CalibrationAvailability,
    checkingCurrentReading: Boolean,
): Boolean = !checkingCurrentReading && availability in setOf(
    G7CalibrationAvailability.AVAILABLE,
    G7CalibrationAvailability.NO_CURRENT_READING,
)

internal fun g7CalibrationActionSupportingText(
    availability: G7CalibrationAvailability,
    checkingCurrentReading: Boolean,
    readinessError: String?,
): String? = when {
    checkingCurrentReading -> "Checking current sensor reading…"
    readinessError != null -> readinessError
    availability == G7CalibrationAvailability.NO_CURRENT_READING ->
        "Tap to check for a current reading from this G7 sensor."
    else -> g7CalibrationAvailabilityText(availability)
}

internal fun isG7CalibrationReadinessSatisfied(
    check: G7CalibrationReadinessCheck,
    currentEgvEventSequence: Long?,
    currentSensorStartedTimestamp: Long,
    availability: G7CalibrationAvailability,
): Boolean = isFreshG7Event(
    currentEgvEventSequence,
    check.acceptEventsAfterSequence,
) && currentSensorStartedTimestamp == check.sensorStartedTimestamp &&
    availability == G7CalibrationAvailability.AVAILABLE

internal fun g7CalibrationReadinessCommands(): List<Message> = listOf(
    CgmStatusV2Request(),
    CurrentEGVGuiDataRequest(),
)

internal fun g7CalibrationAvailability(
    pumpConnected: Boolean,
    sessionState: CGMSessionState?,
    sensorType: CgmStatusV2Response.CgmSensorType?,
    sensorStartedTimestamp: Long,
    currentReadingMgdl: Int?,
    readingSensorStartedTimestamp: Long?,
    manualBolusOperation: ManualBolusOperation?,
    bolusPermissionOperation: BolusPermissionOperation?,
    calibrationOperation: G7CalibrationOperation?,
): G7CalibrationAvailability = when {
    !pumpConnected -> G7CalibrationAvailability.PUMP_DISCONNECTED
    sessionState != CGMSessionState.ACTIVE -> G7CalibrationAvailability.SESSION_NOT_ACTIVE
    sensorType != CgmStatusV2Response.CgmSensorType.DEXCOM_G7 ->
        G7CalibrationAvailability.NOT_G7
    sensorStartedTimestamp <= 0L ->
        G7CalibrationAvailability.SESSION_IDENTITY_UNAVAILABLE
    currentReadingMgdl == null || currentReadingMgdl <= 0 ||
        readingSensorStartedTimestamp != sensorStartedTimestamp ->
        G7CalibrationAvailability.NO_CURRENT_READING
    manualBolusOperation?.isUnresolved == true ||
        bolusPermissionOperation.isUnresolvedForCgmCalibration() ->
        G7CalibrationAvailability.BOLUS_UNRESOLVED
    calibrationOperation?.takeIf {
        it.pumpSid >= 0 && it.sensorStartedTimestamp == sensorStartedTimestamp
    }?.preventsResend == true -> G7CalibrationAvailability.CALIBRATION_UNRESOLVED
    else -> G7CalibrationAvailability.AVAILABLE
}

private fun BolusPermissionOperation?.isUnresolvedForCgmCalibration(): Boolean {
    val phase = this?.phase ?: return false
    return phase !in setOf(
        BolusPermissionOperationPhase.RELEASED,
        BolusPermissionOperationPhase.DENIED,
        BolusPermissionOperationPhase.INITIATION_CONSUMED,
        BolusPermissionOperationPhase.INITIATION_REJECTED,
        BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
        BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH,
    )
}

internal fun g7CalibrationAvailabilityText(
    availability: G7CalibrationAvailability,
): String? = when (availability) {
    G7CalibrationAvailability.AVAILABLE -> null
    G7CalibrationAvailability.PUMP_DISCONNECTED -> "Reconnect the pump to calibrate the CGM."
    G7CalibrationAvailability.SESSION_NOT_ACTIVE ->
        "Calibration is available after the G7 sensor session becomes active."
    G7CalibrationAvailability.NOT_G7 -> "Calibration requires an active Dexcom G7 session."
    G7CalibrationAvailability.SESSION_IDENTITY_UNAVAILABLE ->
        "Refresh the active G7 session before calibrating."
    G7CalibrationAvailability.NO_CURRENT_READING ->
        "Wait for a current reading from this G7 sensor before calibrating."
    G7CalibrationAvailability.BOLUS_UNRESOLVED ->
        "Finish or reconcile the current bolus workflow before calibrating."
    G7CalibrationAvailability.CALIBRATION_UNRESOLVED ->
        "The prior calibration result is still being reconciled. It will not be sent again."
}

internal fun isValidG7CalibrationBg(value: String?): Boolean =
    value?.toIntOrNull() in MIN_G7_CALIBRATION_MGDL..MAX_G7_CALIBRATION_MGDL

internal fun createG7CalibrationRequest(
    bgMgdl: Int,
    pumpTimeSecondsSinceReset: Long,
): RemoteBgEntryRequest {
    require(bgMgdl in MIN_G7_CALIBRATION_MGDL..MAX_G7_CALIBRATION_MGDL)
    require(pumpTimeSecondsSinceReset > 0L)
    return RemoteBgEntryRequest(
        bgMgdl,
        true,
        BloodGlucoseReadingType.MANUAL,
        BloodGlucoseReadingSource.REMOTE,
        pumpTimeSecondsSinceReset,
        0,
    )
}

internal fun newerCalibrationTimestampConfirmsOperation(
    operation: G7CalibrationOperation,
    lastCalibrationTimestamp: Long,
): Boolean = lastCalibrationTimestamp > operation.priorLastCalibrationTimestamp &&
    lastCalibrationTimestamp >= operation.pumpCurrentTime - 5L * 60L

internal data class G7CalibrationHistoryEvidence(
    val accepted: Boolean,
    val bgMgdl: Int,
)

internal fun findG7CalibrationHistoryEvidence(
    operation: G7CalibrationOperation,
    items: List<HistoryLogItem>,
): G7CalibrationHistoryEvidence? {
    val parsed = items.mapNotNull { item ->
        runCatching { item to item.parse() }.getOrNull()
    }
    val matchingValue = parsed
        .filter { (_, log) ->
            log is CgmCalibrationG7HistoryLog &&
                log.bg.toInt() == operation.bgMgdl
        }
        .minByOrNull { (item, _) -> abs(item.pumpTimeSec - operation.pumpCurrentTime) }
        ?.takeIf { (item, _) ->
            abs(item.pumpTimeSec - operation.pumpCurrentTime) <=
                CALIBRATION_HISTORY_CORRELATION_SECONDS
        }
        ?: return null
    val result = parsed
        .filter { (_, log) -> log is CgmBleCalibrationEvtG7HistoryLog }
        .minByOrNull { (item, _) -> abs(item.pumpTimeSec - matchingValue.first.pumpTimeSec) }
        ?.takeIf { (item, _) ->
            abs(item.pumpTimeSec - matchingValue.first.pumpTimeSec) <=
                CALIBRATION_HISTORY_CORRELATION_SECONDS
        }
        ?: return null
    return G7CalibrationHistoryEvidence(
        accepted = (result.second as CgmBleCalibrationEvtG7HistoryLog).isCalibrationAccepted,
        bgMgdl = operation.bgMgdl,
    )
}

private const val CALIBRATION_HISTORY_CORRELATION_SECONDS = 5L * 60L
