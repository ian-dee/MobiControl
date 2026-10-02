package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillTubingWorkflowScreen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FillTubingWorkflowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newAttemptRequiresExplicitBodyDisconnectConfirmationBeforeEnterCommand() {
        var enterRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                FillTubingWorkflowScreen(
                    innerPadding = PaddingValues(),
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    inFillTubingMode = false,
                    fillTubingButtonDown = null,
                    exitFillTubingState = null,
                    enterFillTubingModeResponseEvent = null,
                    exitFillTubingModeResponseEvent = null,
                    exitRequested = false,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    requestAuthentication = { _, result ->
                        result(BolusAuthenticationResult.Succeeded)
                    },
                    onDismissPumpPauseAlert = {},
                    onDismiss = {},
                    onBeginFillTubing = { enterRequests += 1 },
                    onEnterRequestFailed = {},
                    onFinishFillTubing = {},
                    onExitRequestFailed = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Disconnect from your body").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, enterRequests) }
        composeRule
            .onNodeWithText("I Confirm Tubing Is Disconnected")
            .performClick()
        composeRule.runOnIdle { assertEquals(1, enterRequests) }
    }

    @Test
    fun automatedZeroBasalDoesNotCountAsManualSuspension() {
        composeRule.setContent {
            ControlX2Theme {
                FillTubingWorkflowScreen(
                    innerPadding = PaddingValues(),
                    basalStatus = BasalStatus.BASALIQ_SUSPENDED,
                    inFillTubingMode = false,
                    fillTubingButtonDown = null,
                    exitFillTubingState = null,
                    enterFillTubingModeResponseEvent = null,
                    exitFillTubingModeResponseEvent = null,
                    exitRequested = false,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    requestAuthentication = { _, result ->
                        result(BolusAuthenticationResult.Succeeded)
                    },
                    onDismissPumpPauseAlert = {},
                    onDismiss = {},
                    onBeginFillTubing = {},
                    onEnterRequestFailed = {},
                    onFinishFillTubing = {},
                    onExitRequestFailed = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Insulin delivery is not manually suspended").assertIsDisplayed()
    }

    @Test
    fun noLoopRequiresFreshAuthenticationAndFreshAcknowledgedEnterResponse() {
        val buttonDown = mutableStateOf<Boolean?>(true)
        val enterResponse = mutableStateOf(
            PumpMessageEvent(1, Instant.EPOCH, EnterFillTubingModeResponse(0)),
        )
        var authenticationRequests = 0
        var enterRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                FillTubingWorkflowScreen(
                    innerPadding = PaddingValues(),
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    inFillTubingMode = true,
                    fillTubingButtonDown = buttonDown.value,
                    exitFillTubingState = null,
                    enterFillTubingModeResponseEvent = enterResponse.value,
                    exitFillTubingModeResponseEvent = null,
                    exitRequested = false,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    requestAuthentication = { _, result ->
                        authenticationRequests += 1
                        result(BolusAuthenticationResult.Succeeded)
                    },
                    onDismissPumpPauseAlert = {},
                    onDismiss = {},
                    onBeginFillTubing = {
                        enterRequests += 1
                        enterResponse.value = PumpMessageEvent(
                            2,
                            Instant.ofEpochSecond(1),
                            EnterFillTubingModeResponse(0),
                        )
                    },
                    onEnterRequestFailed = {},
                    onFinishFillTubing = {},
                    onExitRequestFailed = {},
                    onDone = {},
                )
            }
        }

        composeRule.runOnIdle { buttonDown.value = false }
        composeRule.onNodeWithText("Release the Pump Button").assertIsDisplayed()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Do you see drops?").assertIsDisplayed()
        composeRule.onNodeWithText("No").performClick()
        composeRule.onNodeWithText("I Confirm Tubing Is Disconnected").performClick()

        composeRule.runOnIdle {
            assertEquals(1, authenticationRequests)
            assertEquals(1, enterRequests)
        }
        composeRule.onNodeWithText("Use the Pump Button").assertIsDisplayed()
    }

    @Test
    fun closeDuringActiveFillIsNavigationOnlyAndDoesNotConfirmCompletion() {
        var dismisses = 0
        var finishRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                FillTubingWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    inFillTubingMode = true,
                    fillTubingButtonDown = null,
                    exitFillTubingState = null,
                    enterFillTubingModeResponseEvent = null,
                    exitFillTubingModeResponseEvent = null,
                    exitRequested = false,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    requestAuthentication = { _, result ->
                        result(BolusAuthenticationResult.Succeeded)
                    },
                    onDismissPumpPauseAlert = {},
                    onDismiss = { dismisses += 1 },
                    onBeginFillTubing = {},
                    onEnterRequestFailed = {},
                    onFinishFillTubing = { finishRequests += 1 },
                    onExitRequestFailed = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Close tubing fill?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close")[1].performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismisses)
            assertEquals(0, finishRequests)
        }
    }
}
