package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertPresentationTest {
    @Test
    fun calibrationAlertsUseReadableCopyFromPumpDescriptions() {
        assertEquals(
            AlertPresentation(
                "Calibration Incomplete",
                "The CGM calibration was incomplete.",
            ),
            alertPresentation(AlertStatusResponse.AlertResponseType.INCOMPLETE_CALIBRATION),
        )
        assertEquals(
            AlertPresentation(
                "Calibration Timed Out",
                "The timeout was reached for CGM calibration.",
            ),
            alertPresentation(AlertStatusResponse.AlertResponseType.CALIBRATION_TIMEOUT),
        )
    }

    @Test
    fun unrelatedAlertRetainsExistingDiagnosticPresentation() {
        assertEquals(
            "Alert: LOW_INSULIN_ALERT2",
            alertPresentation(AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2).headline,
        )
    }

    @Test
    fun quickBolusAlertUsesCapturedOfficialMobiCopy() {
        assertEquals(
            AlertPresentation(
                "Quick Bolus Alert",
                "A quick bolus has been requested three times without delivery. " +
                    "Please check the pump button to see if it is stuck.",
            ),
            alertPresentation(AlertStatusResponse.AlertResponseType.QUICK_BOLUS_ALERT),
        )
    }
}
