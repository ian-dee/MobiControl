package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SafetyLimitsActionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        prefs.setDeveloperPreviewEnabled(true)
        prefs.setDeveloperPreviewMaxBolusMilliunits(15_000)
        prefs.setDeveloperPreviewBasalLimitMilliunits(3_000)
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun developerPreviewShowsTandemRangesAndAppliesMaxBolusToCalculator() {
        val dataStore = DataStore()
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    SafetyLimitsActions(
                        innerPadding = PaddingValues(),
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        navigateBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Max Bolus").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Max Bolus (U)").performTextClearance()
        composeRule.onNodeWithText("Max Bolus (U)").performTextInput("16")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("16 U").assertIsDisplayed()
        composeRule.onNodeWithText("Allowed: 1–25 U in whole-unit increments · Default: 10 U")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Basal Rate Limit").assertIsDisplayed()
        composeRule.onNodeWithText("Allowed: 0.2–15 U/hr · Default: 3 U/hr")
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(16_000, prefs.developerPreviewMaxBolusMilliunits())
            assertEquals(16_000, dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount)
        }
    }
}
