package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PumpReminderPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun siteReminderRowUsesReadableCopyAndNotTheRawEnum() {
        composeRule.setContent {
            MaterialTheme {
                NotificationItem(
                    notification = ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER,
                    dismissalPending = false,
                    onDismiss = { false },
                )
            }
        }

        composeRule.onNodeWithText("Site Reminder").assertIsDisplayed()
        composeRule.onNodeWithText("It's time to change your infusion set.").assertIsDisplayed()
        composeRule.onNodeWithText("Reminder: SITE_CHANGE_REMINDER").assertDoesNotExist()
    }
}
