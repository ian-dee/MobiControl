package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.enums.BasalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TempRateWindowUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun developerPreviewReviewsAndStartsWithoutSendingPumpCommand() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        val dataStore = DataStore().apply {
            basalRate.value = "0.50u"
            basalStatus.value = BasalStatus.ON
            tempRateActive.value = false
            tempRatePercentRawValue.value = "150"
            tempRateHoursRawValue.value = "1"
            tempRateMinutesRawValue.value = "30"
            controlIQEnabled.value = true
        }
        var pumpCommandSent = false
        var closed = false
        var authenticationResult: ((BolusAuthenticationResult) -> Unit)? = null
        val submissionStates = mutableListOf<Boolean>()

        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    LazyColumn {
                        item {
                            TempRateWindow(
                                sendPumpCommands = { _, _ -> pumpCommandSent = true },
                                requestActionAuthentication = { actionName, onResult ->
                                    assertEquals("Temp Rate", actionName)
                                    authenticationResult = onResult
                                },
                                onSubmissionStateChange = { submissionStates += it },
                                closeWindow = { closed = true },
                                developerPreviewEnabledOverride = true,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Start Temp Rate").assertIsDisplayed()
        composeRule.onNodeWithText("Delivery Preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Review Simulated Temp Rate")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Start temporary basal?").assertIsDisplayed()
        composeRule.onNodeWithText("Start and verify").performClick()
        composeRule.runOnIdle {
            assertEquals(null, prefs.developerPreviewTempRate())
            assertFalse(closed)
            assertFalse(pumpCommandSent)
            assertTrue(submissionStates.isEmpty())
            authenticationResult?.invoke(BolusAuthenticationResult.Succeeded)
        }

        composeRule.waitUntil(2_000) { prefs.developerPreviewTempRate() != null && closed }
        val persisted = prefs.developerPreviewTempRate()
        assertEquals(150, persisted?.percent)
        assertEquals(90, persisted?.durationMinutes)
        assertEquals(true, dataStore.tempRateActive.value)
        assertFalse(pumpCommandSent)
        assertTrue(closed)
        assertEquals(listOf(true, false), submissionStates)
        prefs.prefs().edit().clear().commit()
    }
}
