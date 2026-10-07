package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.sections.components.cartridge.ChangeCartridgeWorkflowScreen
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChangeCartridgeWorkflowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closeAfterPumpModeStartsIsNavigationOnly() {
        var dismisses = 0
        var enterRequests = 0
        var exitRequests = 0
        var completions = 0
        composeRule.setContent {
            ControlX2Theme {
                ChangeCartridgeWorkflowScreen(
                    innerPadding = PaddingValues(),
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    inChangeCartridgeMode = true,
                    enterChangeCartridgeState = null,
                    detectingCartridgeState = null,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    onDismiss = { dismisses += 1 },
                    onEnter = { enterRequests += 1 },
                    onExit = { exitRequests += 1 },
                    onDone = { completions += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Close cartridge change?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Close")[1].performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismisses)
            assertEquals(0, enterRequests)
            assertEquals(0, exitRequests)
            assertEquals(0, completions)
        }
    }

    @Test
    fun cancelBeforePumpModeStartsDismissesWithoutAConfirmationOrCommand() {
        var dismisses = 0
        var enterRequests = 0
        composeRule.setContent {
            ControlX2Theme {
                ChangeCartridgeWorkflowScreen(
                    basalStatus = BasalStatus.PUMP_SUSPENDED,
                    inChangeCartridgeMode = false,
                    enterChangeCartridgeState = null,
                    detectingCartridgeState = null,
                    activeNotifications = emptyList(),
                    notificationsRefreshing = false,
                    sendPumpCommands = { _, _ -> },
                    refreshNotifications = { Job() },
                    onDismiss = { dismisses += 1 },
                    onEnter = { enterRequests += 1 },
                    onExit = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismisses)
            assertEquals(0, enterRequests)
        }
    }
}
