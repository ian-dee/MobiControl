package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsAppSupportUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Prefs(context).prefs().edit().clear().commit()
    }

    @Test
    fun appSupportShowsRestartAndAcknowledgements() {
        render()

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Restart MobiControl Connection"))
        composeRule.onNodeWithText("Restart MobiControl Connection")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Cold-restart MobiControl, its background service, and pump connection.",
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Acknowledgements"))
        composeRule.onNodeWithText("Acknowledgements").assertIsDisplayed()
        composeRule.onNodeWithText(
            "ControlX2, PumpX2, and open-source license information.",
        ).assertIsDisplayed()
    }

    @Test
    fun acknowledgementsUsesDedicatedNavigationCallback() {
        var navigationCount = 0
        render(navigateToAcknowledgements = { navigationCount++ })

        composeRule.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Acknowledgements"))
        composeRule.onNodeWithText("Acknowledgements").performClick()

        composeRule.runOnIdle {
            assertEquals(1, navigationCount)
        }
    }

    private fun render(
        navigateToAcknowledgements: () -> Unit = {},
    ) {
        composeRule.setContent {
            ControlX2Theme {
                Settings(
                    sendMessage = { _, _ -> },
                    sendPumpCommands = { _, _ -> },
                    navigateToAcknowledgements = navigateToAcknowledgements,
                )
            }
        }
    }
}
