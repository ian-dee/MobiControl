package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse

internal data class CgmAlertPresentation(
    val headline: String,
    val supportingText: String?,
)

/**
 * User-readable copy for Mobi/Dexcom G7 alert IDs confirmed against pump status/history captures.
 * Unmapped alerts deliberately retain their existing diagnostic enum name rather than guessing.
 */
internal fun cgmAlertPresentation(
    alert: CGMAlertStatusResponse.CGMAlert,
): CgmAlertPresentation = when (alert) {
    CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRING_CGM_ALERT -> CgmAlertPresentation(
        headline = "Sensor Expiring Soon",
        supportingText = "Approximately 24 hours remain before the sensor session expires.",
    )

    CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRING_CGM_ALERT2 -> CgmAlertPresentation(
        headline = "Sensor Expiring Soon",
        supportingText = "Approximately 2 hours remain before the sensor session expires.",
    )

    CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_31 -> CgmAlertPresentation(
        headline = "Sensor Expired",
        supportingText = "The 12-hour grace period has started. CGM readings and Control-IQ+ remain available during the grace period.",
    )

    else -> CgmAlertPresentation(
        headline = "CGM Alert: ${alert.name}",
        supportingText = null,
    )
}
