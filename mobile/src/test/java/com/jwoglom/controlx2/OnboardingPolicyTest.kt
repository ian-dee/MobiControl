package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.screens.finalOnboardingPumpDataSyncCommands
import com.jwoglom.controlx2.presentation.screens.pumpOnboardingScreenTitle
import com.jwoglom.controlx2.presentation.screens.onboardingPumpTimeComparison
import com.jwoglom.controlx2.shared.util.phoneLocalTimeAsTandemEpochSeconds
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.controlx2.presentation.components.PumpPairingStepCount
import com.jwoglom.controlx2.presentation.components.pumpPairingProgressPresentation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OnboardingPolicyTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun pairedPhoneUsesAuthenticatedPumpActionsWithoutLegacyToggle() {
        prefs.setInsulinDeliveryActions(false)
        assertFalse(prefs.insulinDeliveryActions())

        prefs.setPumpSetupComplete(true)
        assertTrue(prefs.insulinDeliveryActions())
    }

    @Test
    fun developerPreviewNeverInheritsPairedPumpActions() {
        prefs.setPumpSetupComplete(true)
        prefs.setInsulinDeliveryActions(true)
        prefs.setDeveloperPreviewEnabled(true)

        assertFalse(prefs.insulinDeliveryActions())
    }

    @Test
    fun legacyUpdatePreferenceCannotReenableUpdateChecks() {
        prefs.prefs().edit().putBoolean("check-for-updates", true).commit()

        assertFalse(prefs.checkForUpdates())
    }

    @Test
    fun successfulPairingAdvancesToOptionalTimeSync() {
        assertEquals(
            PumpSetupStage.PUMPX2_SET_PUMP_TIME,
            PumpSetupStage.PUMPX2_PUMP_CONNECTED.nextStage(
                PumpSetupStage.PUMPX2_SET_PUMP_TIME,
            ),
        )
    }

    @Test
    fun MobiInstructionStagesAreOrderedChargerGestureSelectionCode() {
        assertEquals(1, PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD.step)
        assertEquals(2, PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP.step)
        assertEquals(3, PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP.step)
        assertEquals(4, PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE.step)

        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
            PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP.previousStage(),
        )
        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP,
            PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE.previousStage(),
        )
    }

    @Test
    fun postPairOnboardingUsesDistinctScreensInOrder() {
        assertEquals(
            "Pump Pairing",
            pumpOnboardingScreenTitle(PumpSetupStage.PUMPX2_PUMP_CONNECTED),
        )
        assertEquals(
            "Set Pump Time",
            pumpOnboardingScreenTitle(PumpSetupStage.PUMPX2_SET_PUMP_TIME),
        )
        assertEquals(
            "Sync Pump Data",
            pumpOnboardingScreenTitle(PumpSetupStage.PUMPX2_SYNC_PUMP_DATA),
        )
        assertEquals(
            PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
            PumpSetupStage.PUMPX2_SET_PUMP_TIME.nextStage(
                PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
            ),
        )
        assertEquals(
            "Background Connection",
            pumpOnboardingScreenTitle(PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION),
        )
        assertEquals(
            PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
            PumpSetupStage.PUMPX2_SYNC_PUMP_DATA.nextStage(
                PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
            ),
        )
    }

    @Test
    fun finalSyncUsesTheObservedOfficialMobiLoadStatusRequest() {
        val commands = finalOnboardingPumpDataSyncCommands()

        assertEquals(1, commands.size)
        assertTrue(commands.single() is LoadStatusRequest)
    }

    @Test
    fun timeDecisionCanSurviveUiRecreationUntilOnboardingFinishes() {
        assertFalse(prefs.onboardingTimeDecisionComplete())

        prefs.setOnboardingTimeDecisionComplete(true)
        assertTrue(Prefs(context).onboardingTimeDecisionComplete())

        prefs.setOnboardingTimeDecisionComplete(false)
        assertFalse(Prefs(context).onboardingTimeDecisionComplete())
    }

    @Test
    fun eightStepIndicatorEndsWhenPumpPairingCompletes() {
        val readingProfiles = pumpPairingProgressPresentation(
            PumpSetupStage.PUMPX2_READING_PUMP_PROFILES,
            initialSetup = true,
        )

        assertFalse(readingProfiles.visible)
        assertEquals(PumpPairingStepCount, PumpSetupStage.PUMPX2_PUMP_CONNECTED.step)
    }

    @Test
    fun optionalTimeSyncIsNotPartOfPairingProgress() {
        val timeSync = pumpPairingProgressPresentation(
            PumpSetupStage.PUMPX2_SET_PUMP_TIME,
            initialSetup = true,
        )

        assertFalse(timeSync.visible)
    }

    @Test
    fun backgroundConnectionSetupIsNotPartOfPairingProgress() {
        val backgroundSetup = pumpPairingProgressPresentation(
            PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
            initialSetup = true,
        )

        assertFalse(backgroundSetup.visible)
    }

    @Test
    fun onboardingDoesNotOfferSyncWhenPumpIsWithinOneSecond() {
        val zone = ZoneId.of("America/Los_Angeles")
        val observedAt = Instant.parse("2026-08-19T19:00:00Z")
        val pumpTime = phoneLocalTimeAsTandemEpochSeconds(observedAt, zone) - 1L

        val comparison = onboardingPumpTimeComparison(
            reportedPumpLocalTandemEpochSeconds = pumpTime,
            observedAt = observedAt,
            now = observedAt.plusSeconds(30),
            zoneId = zone,
        )

        assertEquals(1L, comparison.differenceSeconds)
        assertTrue(comparison.withinTolerance)
        assertFalse(comparison.shouldOfferSync)
    }

    @Test
    fun onboardingOffersSyncWhenPumpDiffersByMoreThanOneSecond() {
        val zone = ZoneId.of("America/Los_Angeles")
        val observedAt = Instant.parse("2026-08-19T19:00:00Z")
        val pumpTime = phoneLocalTimeAsTandemEpochSeconds(observedAt, zone) - 2L

        val comparison = onboardingPumpTimeComparison(
            reportedPumpLocalTandemEpochSeconds = pumpTime,
            observedAt = observedAt,
            now = observedAt.plusSeconds(30),
            zoneId = zone,
        )

        assertEquals(2L, comparison.differenceSeconds)
        assertFalse(comparison.withinTolerance)
        assertTrue(comparison.shouldOfferSync)
    }
}
