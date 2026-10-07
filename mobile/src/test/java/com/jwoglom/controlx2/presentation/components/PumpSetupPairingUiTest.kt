package com.jwoglom.controlx2.presentation.components

import androidx.compose.runtime.CompositionLocalProvider
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.pairing.PumpPairingCandidate
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PumpSetupPairingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unnamedAdvertisementIsProgressOnly() {
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS
            pumpFinderPumps.value = listOf(
                candidate(name = null, readyState = PumpReadyState.CHARGING_OR_PICKED_UP),
            )
        }
        render(dataStore)

        composeRule.onNodeWithText("Waiting for a Mobi on the charger…")
            .assertIsDisplayed().assertHasNoClickAction()
        composeRule.onNodeWithText("Select a pump to connect to:").assertDoesNotExist()
    }

    @Test
    fun chargerInstructionsDoNotRevealTheLaterGestureStep() {
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS
            pumpFinderPumps.value = listOf(
                candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.CHARGING_OR_PICKED_UP,
                ),
            )
        }
        render(dataStore)

        composeRule.onNodeWithText("Place your Mobi on the wireless charger.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "Remove the Mobi from the charger. Within 120 seconds, quickly press the pump button twice.",
        ).assertDoesNotExist()
    }

    @Test
    fun freshNamedMobiCanBeSelected() {
        val observedAt = SystemClock.elapsedRealtime()
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP
            pumpFinderPumps.value = listOf(
                candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = observedAt,
                ),
            )
        }
        render(dataStore)

        composeRule.onNodeWithText("Tandem Mobi 101 (MAC: AA:BB:CC:DD:EE:FF)")
            .assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun selectingFreshMobiBindsAddressAndAdvancesDirectlyToCodeEntry() {
        val observedAt = SystemClock.elapsedRealtime()
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP
            pumpFinderPumps.value = listOf(
                candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = observedAt,
                ),
            )
        }
        render(dataStore)

        composeRule.onNodeWithText("Tandem Mobi 101 (MAC: AA:BB:CC:DD:EE:FF)")
            .performClick()
        composeRule.waitForIdle()

        assertEquals("AA:BB:CC:DD:EE:FF", dataStore.selectedPumpFinderMac.value)
        assertEquals("Tandem Mobi 101", dataStore.setupDeviceName.value)
        assertEquals(PairingCodeType.SHORT_6CHAR, dataStore.setupPairingCodeType.value)
        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
            dataStore.pumpSetupStage.value,
        )
    }

    @Test
    fun multipleFreshMobisRemainSeparateExplicitChoices() {
        val observedAt = SystemClock.elapsedRealtime()
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP
            pumpFinderPumps.value = listOf(
                candidate(
                    address = "AA:BB:CC:DD:EE:01",
                    name = "Tandem Mobi 102",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = observedAt,
                ),
                candidate(
                    address = "AA:BB:CC:DD:EE:02",
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = observedAt,
                ),
            )
        }
        render(dataStore)

        composeRule.onNodeWithText("Tandem Mobi 102 (MAC: AA:BB:CC:DD:EE:01)")
            .assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("Tandem Mobi 101 (MAC: AA:BB:CC:DD:EE:02)")
            .assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun gestureInstructionsAreShownOnlyAfterChargerDetection() {
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP
        }
        render(dataStore)

        composeRule.onNodeWithText(
            "Remove the Mobi from the charger. Within 120 seconds, quickly press the pump button twice.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Select your Mobi:").assertDoesNotExist()
    }

    @Test
    fun expiredMobiReadinessRemovesPairingCodeEntry() {
        val dataStore = DataStore().apply {
            pumpSetupStage.value = PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE
            selectedPumpFinderMac.value = "AA:BB:CC:DD:EE:FF"
            pumpFinderPumps.value = listOf(
                candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = SystemClock.elapsedRealtime() - 120_001L,
                ),
            )
        }
        render(dataStore)

        composeRule.waitForIdle()

        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD,
            dataStore.pumpSetupStage.value,
        )
    }

    private fun render(dataStore: DataStore) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme {
                    PumpSetupStageDescription(initialSetup = true)
                }
            }
        }
    }

    private fun candidate(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String?,
        readyState: PumpReadyState,
        readyObservedAt: Long? = null,
    ) = PumpPairingCandidate(
        address = address,
        name = name,
        readyState = readyState,
        readyObservedAtElapsedRealtimeMillis = readyObservedAt,
        lastSeenAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
    )
}
