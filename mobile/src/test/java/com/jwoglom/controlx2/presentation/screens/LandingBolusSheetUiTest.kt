package com.jwoglom.controlx2.presentation.screens

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.BottomSheetValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.BolusTimeoutReminder
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h840dp")
class LandingBolusSheetUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var prefs: Prefs
    private lateinit var reminder: BolusTimeoutReminder
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
        prefs.setDeveloperPreviewEnabled(true)
        reminder = BolusTimeoutReminder(context)
        reminder.cancelSession()
    }

    @After
    fun tearDown() {
        reminder.cancelSession()
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun bolusSheetHidesNavigationResistsDragAndOnlyCancelClearsReminder() {
        showBolusSheet()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bolus").assertIsDisplayed()
        composeRule.onNodeWithText("Dashboard").assertDoesNotExist()
        composeRule.onNodeWithText("Notifications").assertDoesNotExist()
        composeRule.onNodeWithText("Actions").assertDoesNotExist()
        composeRule.onNodeWithText("Settings").assertDoesNotExist()
        composeRule.onNodeWithText("Carb Capture", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle { assertNotNull(reminder.activeSession()) }

        composeRule.onNodeWithText("Bolus").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bolus").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bolus").assertDoesNotExist()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(reminder.activeSession()) }
    }

    @Test
    fun androidBackDismissesBolusSheetAndClearsReminder() {
        showBolusSheet()

        composeRule.waitForIdle()
        composeRule.runOnIdle { assertNotNull(reminder.activeSession()) }

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bolus").assertDoesNotExist()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(reminder.activeSession()) }
    }

    @Test
    fun coldLandingDoesNotRestoreAnUnfinishedBolusScreen() {
        val originalSession = reminder.startSession(
            nowEpochMillis = System.currentTimeMillis(),
            sessionId = "restore-session",
        )
        showLanding()

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Carbs (g)").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(originalSession, reminder.activeSession())
        }
    }

    @Test
    fun successfulDeveloperAuthenticationBrieflyDisplaysThenReturnsToDashboard() {
        showLanding(
            bottomScaffoldDisplayState = BottomSheetValue.Expanded,
            bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW,
            requestBolusAuthentication = { _, onResult ->
                onResult(BolusAuthenticationResult.Succeeded)
            },
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Carbs (g)").performTextInput("30")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Confirm Bolus").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Press and hold to deliver")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bolus Confirmed").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bolus Confirmed").assertDoesNotExist()
        composeRule.onNodeWithText("Carbs (g)").assertDoesNotExist()
        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Manual Bolus").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(reminder.activeSession()) }
    }

    @Test
    fun keyboardDoneOnlyFinishesEditingAndExplicitDeliverStartsAuthentication() {
        var authenticationRequests = 0
        showLanding(
            bottomScaffoldDisplayState = BottomSheetValue.Expanded,
            bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW,
            requestBolusAuthentication = { _, _ -> authenticationRequests++ },
        )

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Carbs (g)").performTextInput("30")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Carbs (g)").performImeAction()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Delivery Calculation:").assertExists()
        composeRule.onNodeWithText("Next").assertExists()
        composeRule.onNodeWithText("Confirm Bolus").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, authenticationRequests) }

        composeRule.onNodeWithText("Next")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Confirm Bolus").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, authenticationRequests) }

        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Press and hold to deliver").performClick()
        composeRule.runOnIdle { assertEquals(0, authenticationRequests) }
        composeRule.onNodeWithText("Press and hold to deliver")
            .performTouchInput {
                down(center)
                advanceEventTime(1_000L)
                up()
            }
        composeRule.runOnIdle { assertEquals(1, authenticationRequests) }
    }

    @Test
    fun dashboardBolusPromptsFromFreshSnapshotAndNoTurnsCorrectionOff() {
        showLanding()

        composeRule.onNodeWithText("Carb Capture", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Bolus", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Carb Capture", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("BG is above target").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Current BG is 150 mg/dL; target is 110 mg/dL; IOB is 1.50 u. " +
                "Your BG is higher than your target. Add correction?",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("No").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Correction bolus").assertIsOff()
        composeRule.onAllNodesWithText("Off").assertCountEquals(0)
        composeRule.onAllNodesWithText("Not applied").assertCountEquals(0)
    }

    @Test
    fun manualOverrideDisablesCorrectionAndClearingItRestoresTheRetainedChoice() {
        showBolusSheet()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Correction bolus")
            .assertIsOn()
            .assertIsEnabled()

        composeRule.onNodeWithText("Units").performTextReplacement("2.75")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Correction bolus")
            .assertIsOff()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Manual override active")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Not applied while a manual override is active",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("2.75")
            .performScrollTo()
            .performTextClearance()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Correction bolus")
            .assertIsOn()
            .assertIsEnabled()
        composeRule.onNodeWithText("Manual override active").assertDoesNotExist()
    }

    @Test
    fun leavingUntouchedUnitsFieldRecalculatesCarbBolusImmediately() {
        val dataStore = DataStore().also(::applyDeveloperPreviewProfile)
        showLanding(
            bottomScaffoldDisplayState = BottomSheetValue.Expanded,
            bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW,
            dataStore = dataStore,
        )
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals("0.00", dataStore.bolusUnitsRawValue.value) }
        composeRule.onNodeWithText("Units").performClick()
        composeRule.onNodeWithText("Carbs (g)").performTextInput("30")
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Correction bolus")
            .assertIsOn()
            .assertIsEnabled()
        composeRule.onNodeWithText("Manual override active").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("3.00", dataStore.bolusUnitsRawValue.value) }
    }

    private fun showBolusSheet() {
        showLanding(
            bottomScaffoldDisplayState = BottomSheetValue.Expanded,
            bottomScaffoldState = BottomScaffoldState.BOLUS_WINDOW,
        )
    }

    private fun showLanding(
        bottomScaffoldDisplayState: BottomSheetValue = BottomSheetValue.Collapsed,
        bottomScaffoldState: BottomScaffoldState = BottomScaffoldState.NONE,
        requestBolusAuthentication: BolusAuthenticationRequest = { _, onResult ->
            onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
        },
        dataStore: DataStore = DataStore().also(::applyDeveloperPreviewProfile),
    ) {
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    Landing(
                        sendMessage = { _, _ -> },
                        sendPumpCommands = { _, _ -> },
                        sendServiceBolusRequest = { _, _, _, _, _, _, _, _, _ -> },
                        requestBolusAuthentication = requestBolusAuthentication,
                        bottomScaffoldDisplayState = bottomScaffoldDisplayState,
                        _bottomScaffoldState = bottomScaffoldState,
                    )
                }
            }
        }
    }
}
