package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ControlIqSettingsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun developerPreviewShowsTwoSchedulesAndOpensReadableEditor() {
        composeRule.setContent {
            ControlX2Theme {
                ControlIQSettingsActions(
                    innerPadding = PaddingValues(),
                    sendMessage = { _, _ -> },
                    sendPumpCommands = { _, _ -> },
                    navigateBack = {},
                    developerPreviewEnabledOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("Sleep Schedule 2")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText("Sleep Schedule 1")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("Start Time").assertIsDisplayed()
        composeRule.onNodeWithText("End Time").assertIsDisplayed()
        composeRule.onNodeWithText("Repeat").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()

        // Monday is selected in the default weekday schedule. Changing one day produces a
        // valid, savable draft without relying on a pump connection.
        composeRule.onAllNodesWithText("M")[0].performClick()
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }
}
