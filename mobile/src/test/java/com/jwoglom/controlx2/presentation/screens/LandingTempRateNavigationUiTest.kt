package com.jwoglom.controlx2.presentation.screens

import androidx.compose.material.BottomSheetValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h840dp")
class LandingTempRateNavigationUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        prefs.setDeveloperPreviewEnabled(true)
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test fun dashboardDismissesTempRateWorkflow() = verifyDestinationDismisses("Dashboard")

    @Test fun notificationsDismissesTempRateWorkflow() = verifyDestinationDismisses("Notifications")

    @Test fun actionsDismissesTempRateWorkflow() = verifyDestinationDismisses("Actions")

    @Test fun settingsDismissesTempRateWorkflow() = verifyDestinationDismisses("Settings")

    private fun verifyDestinationDismisses(destination: String) {
        val dataStore = DataStore().also {
            setUpPreviewState(it)
            applyDeveloperPreviewProfile(it)
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    Landing(
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                        bottomScaffoldDisplayState = BottomSheetValue.Expanded,
                        _bottomScaffoldState = BottomScaffoldState.TEMP_RATE_WINDOW,
                        sectionState = LandingSection.ACTIONS,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("temp-rate-window").assertIsDisplayed()
        composeRule.onNodeWithTag("primary-nav-${destination.lowercase()}").performClick()
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithTag("temp-rate-window").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("temp-rate-window").assertDoesNotExist()
    }
}
