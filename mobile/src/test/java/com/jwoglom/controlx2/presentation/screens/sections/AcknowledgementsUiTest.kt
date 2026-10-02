package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AcknowledgementsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageCreditsControlX2PumpX2AndIncludesPumpX2License() {
        render()

        composeRule.onNodeWithText("Acknowledgements").assertIsDisplayed()
        composeRule.onNodeWithTag("acknowledgements-page")
            .performScrollToNode(hasText("ControlX2"))
        composeRule.onNodeWithText("ControlX2").assertIsDisplayed()
        composeRule.onNodeWithText(CONTROLX2_REPOSITORY).assertIsDisplayed()

        composeRule.onNodeWithTag("acknowledgements-page")
            .performScrollToNode(hasText("PumpX2 license"))
        composeRule.onNodeWithText("PumpX2 license").assertIsDisplayed()
        composeRule.onNodeWithTag("acknowledgements-page")
            .performScrollToNode(hasText("Copyright (c) 2022 James Woglom", substring = true))
        composeRule.onNodeWithText(
            "Copyright (c) 2022 James Woglom",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun backUsesNavigationCallback() {
        var backCount = 0
        render(navigateBack = { backCount++ })

        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
    }

    private fun render(navigateBack: () -> Unit = {}) {
        composeRule.setContent {
            ControlX2Theme {
                Acknowledgements(navigateBack = navigateBack)
            }
        }
    }
}
