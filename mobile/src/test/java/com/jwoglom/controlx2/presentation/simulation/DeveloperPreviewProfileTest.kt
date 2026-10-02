package com.jwoglom.controlx2.presentation.simulation

import com.jwoglom.controlx2.DeveloperPreviewTempRate
import com.jwoglom.controlx2.presentation.screens.sections.bolusCalcDecision
import com.jwoglom.controlx2.presentation.screens.sections.buildBolusCalculator
import com.jwoglom.controlx2.presentation.screens.sections.SYNTHETIC_DEVELOPER_PROFILE
import com.jwoglom.controlx2.presentation.screens.sections.profileManagerFromSnapshots
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DeveloperPreviewProfileTest {
    private val profile = DeveloperPreviewProfile.DEFAULT

    @Test
    fun syntheticProfileValuesArePreserved() {
        assertEquals(0.6, profile.basalRateUnitsPerHour, 0.0)
        assertEquals(10.0, profile.carbRatioGramsPerUnit, 0.0)
        assertEquals(40, profile.correctionFactorMgdlPerUnit)
        assertEquals(110, profile.targetGlucoseMgdl)
        assertEquals(5, profile.insulinDurationHours)
        assertEquals(15.0, profile.maximumBolusUnits, 0.0)
        assertEquals(GlucoseUnit.MGDL, profile.glucoseUnit)
        assertEquals(1.5, profile.startingIobUnits, 0.0)
        assertEquals(150, profile.cgmValueMgdl)
    }

    @Test
    fun profileRestoresPersistedSuspensionIntoSharedPreviewState() {
        val dataStore = DataStore()

        applyDeveloperPreviewProfile(dataStore, deliverySuspended = true)
        assertEquals(BasalStatus.PUMP_SUSPENDED, dataStore.basalStatus.value)

        applyDeveloperPreviewProfile(dataStore, deliverySuspended = false)
        assertEquals(BasalStatus.ON, dataStore.basalStatus.value)
    }

    @Test
    fun profileRestoresPersistedControlIqModeIntoSharedPreviewState() {
        val dataStore = DataStore()

        applyDeveloperPreviewProfile(dataStore, controlIqMode = UserMode.SLEEP)
        assertEquals(UserMode.SLEEP, dataStore.controlIQMode.value)

        applyDeveloperPreviewProfile(dataStore, controlIqMode = UserMode.EXERCISE)
        assertEquals(UserMode.EXERCISE, dataStore.controlIQMode.value)
    }

    @Test
    fun profileRestoresActiveTempRateIntoSharedPreviewState() {
        val dataStore = DataStore()
        val tempRate = DeveloperPreviewTempRate(
            percent = 150,
            durationMinutes = 30,
            startedAtEpochMillis = System.currentTimeMillis(),
        )

        applyDeveloperPreviewProfile(dataStore, tempRate = tempRate)

        assertEquals("0.90u", dataStore.basalRate.value)
        assertEquals(BasalStatus.TEMP_RATE, dataStore.basalStatus.value)
        assertEquals(true, dataStore.tempRateActive.value)
        assertEquals(150, dataStore.tempRateDetails.value?.percentage)
        assertEquals(30L * 60L, dataStore.tempRateDetails.value?.duration)
    }

    @Test
    fun profilePopulatesSensorInfoPanelFields() {
        val dataStore = DataStore()

        applyDeveloperPreviewProfile(dataStore)

        assertEquals("▶", dataStore.cgmDeltaArrow.value)
        assertEquals(CGMSessionState.ACTIVE, dataStore.cgmSessionState.value)
        assertEquals("8 days", dataStore.cgmSessionExpireRelative.value)
        assertEquals("August 25, 2026 9:00 AM", dataStore.cgmSessionExpireExact.value)
        assertEquals("Good", dataStore.cgmTransmitterStatus.value)
    }

    @Test
    fun profileAppliesCurrentDeliveryLimitsAndDocumentedDefaults() {
        val dataStore = DataStore()

        applyDeveloperPreviewProfile(
            dataStore,
            maxBolusMilliunits = 12_000,
            basalLimitMilliunits = 2_500L,
        )

        assertEquals(12_000, dataStore.globalMaxBolusSettingsResponse.value?.maxBolus)
        assertEquals(10_000, dataStore.globalMaxBolusSettingsResponse.value?.maxBolusDefault)
        assertEquals(2_500L, dataStore.basalLimitSettingsResponse.value?.basalLimit)
        assertEquals(3_000L, dataStore.basalLimitSettingsResponse.value?.basalLimitDefault)
        assertEquals(12_000, dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount)
    }

    @Test
    fun snapshotMapsProfileIntoPumpCalculatorUnits() {
        val snapshot = createDeveloperPreviewBolusSnapshot(profile)

        assertEquals(150, snapshot.correctionFactor)
        assertEquals(1500L, snapshot.iob)
        assertEquals(110, snapshot.targetBg)
        assertEquals(40, snapshot.isf)
        assertTrue(snapshot.carbEntryEnabled)
        assertEquals(10000L, snapshot.carbRatio)
        assertEquals(15000, snapshot.maxBolusAmount)
        assertTrue(snapshot.isAutopopAllowed)
    }

    @Test
    fun bolusPreviewUsesTheActiveSimulatedProfilesCarbohydratesSetting() {
        val dataStore = DataStore().apply {
            idpManager.value = profileManagerFromSnapshots(
                listOf(SYNTHETIC_DEVELOPER_PROFILE.copy(carbEntryEnabled = false)),
                minuteOfDay = 0,
            )
        }

        applyDeveloperPreviewBolusInputs(dataStore)

        assertFalse(dataStore.bolusCalcDataSnapshot.value!!.carbEntryEnabled)
    }

    @Test
    fun calculatorUsesCarbsCgmAndStartingIob() {
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(profile),
            createDeveloperPreviewLastBg(),
            null,
            30,
            null,
        )

        val units = bolusCalcDecision(calculator, null)!!.units
        assertEquals(3.0, units.fromCarbs, 0.001)
        assertEquals(1.0, units.fromBG, 0.001)
        assertEquals(-1.5, units.fromIOB, 0.001)
        assertEquals(3.0, units.total, 0.001)
    }
}
