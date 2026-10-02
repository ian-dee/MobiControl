package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardSuspendedBannerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bannerWarnsThatBasalIsStoppedAndOffersResume() {
        var resumeRequested = false
        composeRule.setContent {
            ControlX2Theme {
                SuspendedDeliveryBanner(
                    onResumeInsulin = { resumeRequested = true },
                )
            }
        }

        composeRule.onNodeWithText("Insulin Delivery Suspended").assertIsDisplayed()
        composeRule.onNodeWithText("Basal insulin is not currently being delivered.").assertIsDisplayed()
        composeRule.onNodeWithText("Resume Insulin").assertIsDisplayed().performClick()

        assertTrue(resumeRequested)
    }
}
