package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CgmAlertPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun twentyFourHourExpirationAlertUsesApprovedCopy() {
        assertEquals(
            CgmAlertPresentation(
                headline = "Sensor Expiring Soon",
                supportingText = "Approximately 24 hours remain before the sensor session expires.",
            ),
            cgmAlertPresentation(CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRING_CGM_ALERT),
        )
    }

    @Test
    fun twoHourExpirationAlertUsesApprovedCopy() {
        assertEquals(
            CgmAlertPresentation(
                headline = "Sensor Expiring Soon",
                supportingText = "Approximately 2 hours remain before the sensor session expires.",
            ),
            cgmAlertPresentation(CGMAlertStatusResponse.CGMAlert.SENSOR_EXPIRING_CGM_ALERT2),
        )
    }

    @Test
    fun sensorExpiredAlertUsesApprovedGracePeriodCopy() {
        assertEquals(
            CgmAlertPresentation(
                headline = "Sensor Expired",
                supportingText = "The 12-hour grace period has started. CGM readings and Control-IQ+ remain available during the grace period.",
            ),
            cgmAlertPresentation(CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_31),
        )
    }

    @Test
    fun unapprovedAlertRetainsDiagnosticFallback() {
        val presentation = cgmAlertPresentation(
            CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_30,
        )

        assertEquals("CGM Alert: DEFAULT_CGM_ALERT_30", presentation.headline)
        assertNull(presentation.supportingText)
    }

    @Test
    fun notificationRowDoesNotExposeRawSensorExpiredEnum() {
        composeRule.setContent {
            MaterialTheme {
                NotificationItem(
                    notification = CGMAlertStatusResponse.CGMAlert.DEFAULT_CGM_ALERT_31,
                    dismissalPending = false,
                    onDismiss = { false },
                )
            }
        }

        composeRule.onNodeWithText("Sensor Expired").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The 12-hour grace period has started. CGM readings and Control-IQ+ remain available during the grace period.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("CGM Alert: DEFAULT_CGM_ALERT_31").assertDoesNotExist()
    }
}
