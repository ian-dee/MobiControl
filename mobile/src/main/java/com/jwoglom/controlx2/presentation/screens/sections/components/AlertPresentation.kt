package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse

internal data class AlertPresentation(
    val headline: String,
    val supportingText: String?,
)

internal fun alertPresentation(
    alert: AlertStatusResponse.AlertResponseType,
): AlertPresentation = when (alert) {
    AlertStatusResponse.AlertResponseType.INCOMPLETE_CALIBRATION -> AlertPresentation(
        headline = "Calibration Incomplete",
        supportingText = alert.description,
    )
    AlertStatusResponse.AlertResponseType.CALIBRATION_TIMEOUT -> AlertPresentation(
        headline = "Calibration Timed Out",
        supportingText = alert.description,
    )
    AlertStatusResponse.AlertResponseType.QUICK_BOLUS_ALERT -> AlertPresentation(
        headline = "Quick Bolus Alert",
        supportingText =
            "A quick bolus has been requested three times without delivery. " +
                "Please check the pump button to see if it is stuck.",
    )
    else -> AlertPresentation(
        headline = "Alert: ${alert.name}",
        supportingText = alert.description,
    )
}
