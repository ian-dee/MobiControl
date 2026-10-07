package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutomaticBolusCardUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val identity = AutomaticBolusIdentity(12345, 700_000L, 42)
    private val response = CurrentBolusStatusResponse(
        CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING.id,
        identity.bolusId,
        100L,
        1_250L,
        BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS.id(),
        2,
    )

    @Test
    fun activeAutomaticBolusShowsProgressAndRequestsGuardedCancellation() {
        var requested: AutomaticBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                AutomaticBolusCard(
                    response = response,
                    identity = identity,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = null,
                    onCancel = { requested = it },
                )
            }
        }

        composeRule.onNodeWithText("Control-IQ Automatic Bolus").assertIsDisplayed()
        composeRule.onNodeWithText("Delivering 1.25u").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel Automatic Bolus")
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Stop Automatic Bolus?").assertIsDisplayed()
        assertEquals(null, requested)
        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithText("Verifying with pump…").assertIsDisplayed()
        assertEquals(identity, requested)
    }

    @Test
    fun decliningAutomaticBolusCancellationSendsNothing() {
        var requested: AutomaticBolusIdentity? = null
        composeRule.setContent {
            ControlX2Theme {
                AutomaticBolusCard(
                    response = response,
                    identity = identity,
                    pumpConnected = true,
                    cancellationEnabled = true,
                    cancelResult = null,
                    onCancel = { requested = it },
                )
            }
        }

        composeRule.onNodeWithText("Cancel Automatic Bolus").performClick()
        composeRule.onNodeWithText("Stop Automatic Bolus?").assertIsDisplayed()
        composeRule.onNodeWithText("No").performClick()
        composeRule.onNodeWithText("Stop Automatic Bolus?").assertDoesNotExist()
        assertEquals(null, requested)
    }

    @Test
    fun cancellationButtonIsDisabledWhenInsulinActionsAreOff() {
        composeRule.setContent {
            ControlX2Theme {
                AutomaticBolusCard(
                    response = response,
                    identity = identity,
                    pumpConnected = true,
                    cancellationEnabled = false,
                    cancelResult = null,
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("Cancel Automatic Bolus").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancellation is disabled in insulin-delivery settings.")
            .assertIsDisplayed()
    }
}
