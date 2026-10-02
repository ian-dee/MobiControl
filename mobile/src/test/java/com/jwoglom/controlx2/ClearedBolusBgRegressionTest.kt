package com.jwoglom.controlx2

import com.jwoglom.controlx2.presentation.screens.sections.bolusCalcDecision
import com.jwoglom.controlx2.presentation.screens.sections.bolusCalcParameters
import com.jwoglom.controlx2.presentation.screens.sections.buildBolusCalculator
import com.jwoglom.controlx2.presentation.screens.sections.parseBolusGlucoseMgdl
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewLastBg
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearedBolusBgRegressionTest {
    private val noCgmProfile = DeveloperPreviewProfile.DEFAULT.copy(cgmValueMgdl = 0)
    private val snapshot = createDeveloperPreviewBolusSnapshot(noCgmProfile)
    private val lastBg = createDeveloperPreviewLastBg()

    @Test
    fun typeCalculateThenClearMatchesNeverEnteredCalculation() {
        val enteredCalculator = calculatorFor("155")
        val enteredUnits = bolusCalcDecision(enteredCalculator, null)!!.units
        assertEquals(155, enteredCalculator.glucoseMgdl.orElse(null))
        assertTrue(enteredUnits.fromBG > 0.0)

        val clearedCalculator = calculatorFor("")
        val clearedParameters = bolusCalcParameters(clearedCalculator, null)
        val neverEnteredParameters = bolusCalcParameters(calculatorFor(null), null)

        assertFalse(clearedCalculator.glucoseMgdl.isPresent)
        assertNull(clearedParameters.first.glucoseMgdl)
        assertEquals(0.0, clearedParameters.second.fromBG, 0.001)
        assertEquals(neverEnteredParameters.second.fromBG, clearedParameters.second.fromBG, 0.001)
        assertEquals(neverEnteredParameters.second.fromIOB, clearedParameters.second.fromIOB, 0.001)
        assertEquals(neverEnteredParameters.second.fromCarbs, clearedParameters.second.fromCarbs, 0.001)
        assertEquals(neverEnteredParameters.second.total, clearedParameters.second.total, 0.001)
    }

    @Test
    fun clearedBgDoesNotCreateRemoteBgEntry() {
        val clearedParameters = bolusCalcParameters(calculatorFor(""), null).first

        val request = createRemoteBgEntryRequestOrNull(
            clearedParameters.glucoseMgdl,
            42,
            snapshot,
            TimeSinceResetResponse(1000L, 500L),
        )

        assertNull(clearedParameters.glucoseMgdl)
        assertNull(request)
    }

    @Test
    fun decimalMmolTypeThenClearAlsoRemovesBgCorrection() {
        val enteredCalculator = buildBolusCalculator(
            snapshot,
            lastBg,
            null,
            30,
            parseBolusGlucoseMgdl("8.6", GlucoseUnit.MMOL),
        )
        val clearedCalculator = buildBolusCalculator(
            snapshot,
            lastBg,
            null,
            30,
            parseBolusGlucoseMgdl("", GlucoseUnit.MMOL),
        )

        assertEquals(155, enteredCalculator.glucoseMgdl.orElse(null))
        assertTrue(bolusCalcDecision(enteredCalculator, null)!!.units.fromBG > 0.0)
        assertFalse(clearedCalculator.glucoseMgdl.isPresent)
        assertNull(bolusCalcParameters(clearedCalculator, null).first.glucoseMgdl)
        assertEquals(0.0, bolusCalcDecision(clearedCalculator, null)!!.units.fromBG, 0.001)
    }

    @Test
    fun clearingManualBgFallsBackToAvailableCgmRatherThanZero() {
        val cgmSnapshot = createDeveloperPreviewBolusSnapshot(DeveloperPreviewProfile.DEFAULT)
        val calculator = buildBolusCalculator(
            cgmSnapshot,
            lastBg,
            null,
            30,
            parseBolusGlucoseMgdl("", GlucoseUnit.MGDL),
        )

        assertEquals(150, calculator.glucoseMgdl.orElse(null))
        assertTrue(bolusCalcDecision(calculator, null)!!.units.fromBG > 0.0)
    }

    @Test
    fun freshPumpSnapshotSuppliesAutomaticBgWithoutSeparateLastBgResponse() {
        val cgmSnapshot = createDeveloperPreviewBolusSnapshot(DeveloperPreviewProfile.DEFAULT)
        val calculator = buildBolusCalculator(
            cgmSnapshot,
            null,
            null,
            30,
            null,
        )

        assertEquals(150, calculator.glucoseMgdl.orElse(null))
        assertTrue(bolusCalcDecision(calculator, null)!!.units.fromBG > 0.0)
    }

    @Test
    fun snapshotWithoutAutomaticBgRemainsUnavailableWithoutLastBgResponse() {
        val calculator = buildBolusCalculator(
            snapshot,
            null,
            null,
            30,
            null,
        )

        assertFalse(calculator.glucoseMgdl.isPresent)
        assertNull(bolusCalcParameters(calculator, null).first.glucoseMgdl)
        assertEquals(0.0, bolusCalcDecision(calculator, null)!!.units.fromBG, 0.001)
    }

    @Test
    fun editedFingerstickBgOverridesTheAutomaticSnapshotValue() {
        val cgmSnapshot = createDeveloperPreviewBolusSnapshot(
            DeveloperPreviewProfile.DEFAULT.copy(startingIobUnits = 0.0),
        )
        val calculator = buildBolusCalculator(
            cgmSnapshot,
            null,
            null,
            30,
            140,
            allowAutomaticBg = false,
        )

        assertEquals(140, calculator.glucoseMgdl.orElse(null))
        assertEquals(0.75, bolusCalcDecision(calculator, null)!!.units.fromBG, 0.001)
    }

    @Test
    fun explicitlyClearedEditedFieldDoesNotFallBackToAutomaticSnapshot() {
        val cgmSnapshot = createDeveloperPreviewBolusSnapshot(DeveloperPreviewProfile.DEFAULT)
        val calculator = buildBolusCalculator(
            cgmSnapshot,
            null,
            null,
            30,
            null,
            allowAutomaticBg = false,
        )

        assertFalse(calculator.glucoseMgdl.isPresent)
        assertNull(bolusCalcParameters(calculator, null).first.glucoseMgdl)
        assertEquals(0.0, bolusCalcDecision(calculator, null)!!.units.fromBG, 0.001)
    }

    @Test
    fun validBgStillCreatesRemoteBgEntry() {
        val enteredParameters = bolusCalcParameters(calculatorFor("155"), null).first

        val request = createRemoteBgEntryRequestOrNull(
            enteredParameters.glucoseMgdl,
            42,
            snapshot,
            TimeSinceResetResponse(1000L, 500L),
        )

        assertEquals(155, request?.getBG())
        assertEquals(42, request?.bolusId)
        assertEquals(500L, request?.pumpTime)
        assertEquals(BloodGlucoseReadingSource.PUMP, request!!.source)
    }

    @Test
    fun abandonmentOnlyCarriesPumpAutopopulatedBgForTheGrantedBolusId() {
        val cgmSnapshot = createDeveloperPreviewBolusSnapshot(DeveloperPreviewProfile.DEFAULT)
        val automaticBg = automaticBgForAbandonmentOrNull(
            calculatedBgMgdl = 150,
            glucoseWasEnteredManually = false,
            dataSnapshot = cgmSnapshot,
        )
        assertEquals(150, automaticBg)
        assertNull(
            automaticBgForAbandonmentOrNull(
                calculatedBgMgdl = 150,
                glucoseWasEnteredManually = true,
                dataSnapshot = cgmSnapshot,
            ),
        )

        val permission = BolusPermissionOperation(
            pumpSid = 12345,
            pumpSessionId = 700_000L,
            bolusId = 42,
            phase = BolusPermissionOperationPhase.GRANTED,
            createdAtEpochMillis = 1_000L,
            updatedAtEpochMillis = 1_000L,
        )
        val cleanup = createAbandonedBolusCleanupRequestOrNull(permission, automaticBg)
        assertEquals(42, cleanup?.identity?.bolusId)
        assertEquals(150, cleanup?.bgMgdl)
        assertEquals(BolusBgRecordType.AUTOMATIC, cleanup?.bgRecordType)
    }

    private fun calculatorFor(rawBg: String?) = buildBolusCalculator(
        snapshot,
        lastBg,
        null,
        30,
        parseBolusGlucoseMgdl(rawBg, GlucoseUnit.MGDL),
    )
}
