package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.CartridgeActionsMenuScreen
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.FillCannulaWorkflowScreen
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CannulaFillWorkflowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cartridgeMenuDisplaysInfusionSetSummaryWithoutSuspendingDelivery() {
        composeRule.setContent {
            ControlX2Theme {
                CartridgeActionsMenuScreen(
                    innerPadding = PaddingValues(),
                    deviceName = "Mobi",
                    basalStatus = BasalStatus.ON,
                    lastInfusionSetChangeText = "Aug 16, 2026, 2:30 PM",
                    infusionSetAgeText = "1 day, 18 hours",
                    nextInfusionSetChangeText = "Aug 19, 2026, 9:00 AM",
                    onChangeCartridge = {},
                    onFillTubing = {},
                    onFillCannula = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Suspend Insulin Delivery").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Fill Cannula"))
        composeRule.onNodeWithText("Fill Cannula").assertIsDisplayed().assertIsNotEnabled()
        composeRule
            .onNodeWithText("Last changed: Aug 16, 2026, 2:30 PM", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Site age: 1 day, 18 hours", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Next scheduled: Aug 19, 2026, 9:00 AM", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun developerPreviewMenuShowsThreeDistinctDemoProcesses() {
        composeRule.setContent {
            ControlX2Theme {
                CartridgeActionsMenuScreen(
                    innerPadding = PaddingValues(),
                    deviceName = "Mobi (simulated)",
                    developerPreview = true,
                    onChangeCartridge = {},
                    onFillTubing = {},
                    onFillCannula = {},
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Emulator simulation only — no pump commands are sent.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("1. Change Cartridge & Fill Tubing")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Replace the cartridge, then complete the required tubing fill")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("2. Fill Tubing"))
        composeRule.onNodeWithText("2. Fill Tubing").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("3. Fill Cannula Only"))
        composeRule.onNodeWithText("3. Fill Cannula Only").assertIsDisplayed()
    }

    @Test
    fun cartridgeActionsAreDisabledUntilDeliveryIsSuspended() {
        composeRule.setContent {
            ControlX2Theme {
                CartridgeActionsMenuScreen(
                    innerPadding = PaddingValues(),
                    deviceName = "Mobi (simulated)",
                    developerPreview = true,
                    basalStatus = BasalStatus.ON,
                    onChangeCartridge = {},
                    onFillTubing = {},
                    onFillCannula = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Suspend Insulin Delivery").assertIsDisplayed()
        composeRule.onNodeWithText("1. Change Cartridge & Fill Tubing").assertIsNotEnabled()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("2. Fill Tubing"))
        composeRule.onNodeWithText("2. Fill Tubing").assertIsNotEnabled()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("3. Fill Cannula Only"))
        composeRule.onNodeWithText("3. Fill Cannula Only").assertIsNotEnabled()
    }

    @Test
    fun incompleteWorkflowCardRoutesOnlyAfterExplicitContinue() {
        var continueRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                CartridgeActionsMenuScreen(
                    innerPadding = PaddingValues(),
                    deviceName = "Mobi",
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    incompleteWorkflow = IncompleteCartridgePhase.FILL_TUBING,
                    onContinueIncompleteWorkflow = { continueRequests += 1 },
                    onChangeCartridge = {},
                    onFillTubing = {},
                    onFillCannula = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Tubing fill incomplete").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.runOnIdle { assertEquals(1, continueRequests) }
    }

    @Test
    fun completedWorkflowLabelsTheTimestampAndVerifiedReminder() {
        composeRule.setContent {
            ControlX2Theme {
                FillCannulaWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    workflowState = CannulaFillAttemptState.begin(1, 10).copy(
                        phase = CannulaFillPhase.COMPLETE,
                        reminderPlan = SiteChangeReminderPlan(3, 9 * 60L),
                        completedAtEpochMillis = 1_000,
                    ),
                    readiness = CannulaFillReadiness(
                        CannulaFillReadinessKind.READY,
                        "Pump confirmed PRIME_NUDGE.",
                    ),
                    primeAmount = 0.7,
                    reminderDays = 3,
                    reminderTimeMinutes = 9 * 60,
                    lastCompletedText = "Aug 16, 2026, 2:30 PM",
                    onPrimeAmountChange = {},
                    onReminderDaysChange = {},
                    onReminderTimeChange = {},
                    onSaveReminderPreference = {},
                    onDismiss = {},
                    onDone = {},
                    requestAuthentication = { _, result -> result(BolusAuthenticationResult.Succeeded) },
                    onSendFillRequest = {},
                    onRetryFill = {},
                    onRetryReminder = {},
                )
            }
        }

        composeRule.onNodeWithText("Cannula fill complete!").assertIsDisplayed()
        composeRule.onNodeWithText("Pump-confirmed: Aug 16, 2026, 2:30 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Site-change reminder verified: 3 days at 9:00 AM").assertIsDisplayed()
    }


    @Test
    fun readyCannulaFillShowsEditableReminderDefaults() {
        composeRule.setContent {
            ControlX2Theme {
                FillCannulaWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    workflowState = CannulaFillAttemptState.begin(1, 10).copy(
                        phase = CannulaFillPhase.READY,
                    ),
                    readiness = CannulaFillReadiness(
                        CannulaFillReadinessKind.READY,
                        "Pump confirmed PRIME_NUDGE.",
                    ),
                    primeAmount = 0.7,
                    reminderDays = 3,
                    reminderTimeMinutes = 9 * 60,
                    lastCompletedText = null,
                    onPrimeAmountChange = {},
                    onReminderDaysChange = {},
                    onReminderTimeChange = {},
                    onSaveReminderPreference = {},
                    onDismiss = {},
                    onDone = {},
                    requestAuthentication = { _, result -> result(BolusAuthenticationResult.Succeeded) },
                    onSendFillRequest = {},
                    onRetryFill = {},
                    onRetryReminder = {},
                )
            }
        }

        composeRule.onNodeWithText("Prime amount: 0.7U").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Days after fill").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reminder time: 9:00 AM").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Save Reminder Preferences").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cannulaCommandIsSentOnlyAfterSuccessfulDeviceAuthentication() {
        var authenticationRequests = 0
        var fillRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                FillCannulaWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    workflowState = CannulaFillAttemptState.begin(1, 10).copy(
                        phase = CannulaFillPhase.READY,
                    ),
                    readiness = CannulaFillReadiness(
                        CannulaFillReadinessKind.READY,
                        "Pump confirmed PRIME_NUDGE.",
                    ),
                    primeAmount = 0.7,
                    reminderDays = 3,
                    reminderTimeMinutes = 9 * 60,
                    lastCompletedText = null,
                    onPrimeAmountChange = {},
                    onReminderDaysChange = {},
                    onReminderTimeChange = {},
                    onSaveReminderPreference = {},
                    onDismiss = {},
                    onDone = {},
                    requestAuthentication = { _, result ->
                        authenticationRequests += 1
                        result(BolusAuthenticationResult.Succeeded)
                    },
                    onSendFillRequest = { fillRequests += 1 },
                    onRetryFill = {},
                    onRetryReminder = {},
                )
            }
        }

        composeRule.onNode(hasText("Fill Cannula") and hasClickAction()).performClick()

        composeRule.runOnIdle {
            assertEquals(1, authenticationRequests)
            assertEquals(1, fillRequests)
        }
    }

    @Test
    fun cancelledCannulaAuthenticationNeverSendsThePumpCommand() {
        var fillRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                FillCannulaWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    workflowState = CannulaFillAttemptState.begin(1, 10).copy(
                        phase = CannulaFillPhase.READY,
                    ),
                    readiness = CannulaFillReadiness(
                        CannulaFillReadinessKind.READY,
                        "Pump confirmed PRIME_NUDGE.",
                    ),
                    primeAmount = 0.7,
                    reminderDays = 3,
                    reminderTimeMinutes = 9 * 60,
                    lastCompletedText = null,
                    onPrimeAmountChange = {},
                    onReminderDaysChange = {},
                    onReminderTimeChange = {},
                    onSaveReminderPreference = {},
                    onDismiss = {},
                    onDone = {},
                    requestAuthentication = { _, result ->
                        result(BolusAuthenticationResult.Cancelled("Authentication cancelled."))
                    },
                    onSendFillRequest = { fillRequests += 1 },
                    onRetryFill = {},
                    onRetryReminder = {},
                )
            }
        }

        composeRule.onNode(hasText("Fill Cannula") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals(0, fillRequests) }
        composeRule.onNodeWithText("Authentication cancelled.").performScrollTo().assertIsDisplayed()
    }
}
