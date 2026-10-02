package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.CgmHighLowAlertRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CgmOutOfRangeAlertRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CgmRiseFallAlertRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMGlucoseAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMOORAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMRateAlertSettingsResponse

internal val CGM_ALERT_REPEAT_OPTIONS_MINUTES = listOf(0, 15, 30, 60, 120, 180, 240, 300)

internal data class CgmAlertSettingsSnapshot(
    val highEnabled: Boolean,
    val highThreshold: Int,
    val highRepeatMinutes: Int,
    val lowEnabled: Boolean,
    val lowThreshold: Int,
    val lowRepeatMinutes: Int,
    val riseEnabled: Boolean,
    val riseRate: Int,
    val fallEnabled: Boolean,
    val fallRate: Int,
    val outOfRangeEnabled: Boolean,
    val outOfRangeDelayMinutes: Int,
) {
    fun validationError(): String? = when {
        highThreshold !in 120..400 -> "High alert must be between 120 and 400 mg/dL."
        lowThreshold !in 60..100 -> "Low alert must be between 60 and 100 mg/dL."
        highRepeatMinutes !in CGM_ALERT_REPEAT_OPTIONS_MINUTES ->
            "Choose a supported High alert repeat interval."
        lowRepeatMinutes !in CGM_ALERT_REPEAT_OPTIONS_MINUTES ->
            "Choose a supported Low alert repeat interval."
        riseRate !in 2..3 -> "Rise rate must be 2 or 3 mg/dL/min."
        fallRate !in 2..3 -> "Fall rate must be 2 or 3 mg/dL/min."
        outOfRangeDelayMinutes !in 20..200 ->
            "Out-of-range delay must be between 20 and 200 minutes."
        else -> null
    }
}

internal fun cgmAlertSettingsSnapshot(
    glucose: CGMGlucoseAlertSettingsResponse?,
    rate: CGMRateAlertSettingsResponse?,
    outOfRange: CGMOORAlertSettingsResponse?,
): CgmAlertSettingsSnapshot? {
    if (glucose == null || rate == null || outOfRange == null) return null
    return CgmAlertSettingsSnapshot(
        highEnabled = glucose.highGlucoseAlertEnabled != 0,
        highThreshold = glucose.highGlucoseAlertThreshold,
        highRepeatMinutes = glucose.highGlucoseRepeatDuration,
        lowEnabled = glucose.lowGlucoseAlertEnabled != 0,
        lowThreshold = glucose.lowGlucoseAlertThreshold,
        lowRepeatMinutes = glucose.lowGlucoseRepeatDuration,
        riseEnabled = rate.riseRateEnabled != 0,
        riseRate = rate.riseRateThreshold,
        fallEnabled = rate.fallRateEnabled != 0,
        fallRate = rate.fallRateThreshold,
        outOfRangeEnabled = outOfRange.sensorTimeoutAlertEnabled != 0,
        outOfRangeDelayMinutes = outOfRange.sensorTimeoutAlertThreshold,
    )
}

internal enum class CgmAlertWriteResponseKind {
    HIGH_LOW,
    RISE_FALL,
    OUT_OF_RANGE,
}

internal data class CgmAlertWriteOperation(
    val label: String,
    val responseKind: CgmAlertWriteResponseKind,
    val message: Message,
)

internal fun buildCgmAlertWritePlan(
    confirmed: CgmAlertSettingsSnapshot,
    requested: CgmAlertSettingsSnapshot,
): List<CgmAlertWriteOperation> {
    require(requested.validationError() == null) { requested.validationError() ?: "Invalid settings" }
    return buildList {
        val highBitmask = changeBitmask(
            enabledChanged = confirmed.highEnabled != requested.highEnabled,
            primaryValueChanged = confirmed.highThreshold != requested.highThreshold,
            secondaryValueChanged = confirmed.highRepeatMinutes != requested.highRepeatMinutes,
        )
        if (highBitmask != 0) {
            add(
                CgmAlertWriteOperation(
                    label = "High alert",
                    responseKind = CgmAlertWriteResponseKind.HIGH_LOW,
                    message = CgmHighLowAlertRequest(
                        CgmHighLowAlertRequest.ALERT_TYPE_HIGH,
                        requested.highThreshold,
                        requested.highRepeatMinutes,
                        requested.highEnabled,
                        highBitmask,
                    ),
                )
            )
        }

        val lowBitmask = changeBitmask(
            enabledChanged = confirmed.lowEnabled != requested.lowEnabled,
            primaryValueChanged = confirmed.lowThreshold != requested.lowThreshold,
            secondaryValueChanged = confirmed.lowRepeatMinutes != requested.lowRepeatMinutes,
        )
        if (lowBitmask != 0) {
            add(
                CgmAlertWriteOperation(
                    label = "Low alert",
                    responseKind = CgmAlertWriteResponseKind.HIGH_LOW,
                    message = CgmHighLowAlertRequest(
                        CgmHighLowAlertRequest.ALERT_TYPE_LOW,
                        requested.lowThreshold,
                        requested.lowRepeatMinutes,
                        requested.lowEnabled,
                        lowBitmask,
                    ),
                )
            )
        }

        val riseBitmask = changeBitmask(
            enabledChanged = confirmed.riseEnabled != requested.riseEnabled,
            primaryValueChanged = confirmed.riseRate != requested.riseRate,
        )
        if (riseBitmask != 0) {
            add(
                CgmAlertWriteOperation(
                    label = "Rise alert",
                    responseKind = CgmAlertWriteResponseKind.RISE_FALL,
                    message = CgmRiseFallAlertRequest(
                        CgmRiseFallAlertRequest.ALERT_TYPE_RISE,
                        requested.riseEnabled,
                        requested.riseRate,
                        riseBitmask,
                    ),
                )
            )
        }

        val fallBitmask = changeBitmask(
            enabledChanged = confirmed.fallEnabled != requested.fallEnabled,
            primaryValueChanged = confirmed.fallRate != requested.fallRate,
        )
        if (fallBitmask != 0) {
            add(
                CgmAlertWriteOperation(
                    label = "Fall alert",
                    responseKind = CgmAlertWriteResponseKind.RISE_FALL,
                    message = CgmRiseFallAlertRequest(
                        CgmRiseFallAlertRequest.ALERT_TYPE_FALL,
                        requested.fallEnabled,
                        requested.fallRate,
                        fallBitmask,
                    ),
                )
            )
        }

        val outOfRangeBitmask = changeBitmask(
            enabledChanged = confirmed.outOfRangeEnabled != requested.outOfRangeEnabled,
            primaryValueChanged = confirmed.outOfRangeDelayMinutes != requested.outOfRangeDelayMinutes,
        )
        if (outOfRangeBitmask != 0) {
            add(
                CgmAlertWriteOperation(
                    label = "Out-of-range alert",
                    responseKind = CgmAlertWriteResponseKind.OUT_OF_RANGE,
                    message = CgmOutOfRangeAlertRequest(
                        requested.outOfRangeEnabled,
                        requested.outOfRangeDelayMinutes,
                        outOfRangeBitmask,
                    ),
                )
            )
        }
    }
}

private fun changeBitmask(
    enabledChanged: Boolean,
    primaryValueChanged: Boolean,
    secondaryValueChanged: Boolean = false,
): Int =
    (if (enabledChanged) 0b001 else 0) or
        (if (primaryValueChanged) 0b010 else 0) or
        (if (secondaryValueChanged) 0b100 else 0)

internal fun cgmRepeatLabel(minutes: Int): String = when (minutes) {
    0 -> "Never"
    15, 30 -> "$minutes min"
    60 -> "1 hr"
    else -> "${minutes / 60} hr"
}
