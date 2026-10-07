package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.plannedCorrectionVolumeMilliunits
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewLastBg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusCorrectionPolicyTest {
    @Test
    fun preflightUsesTheSameConfirmedBgTargetAndIobAsTheCalculator() {
        val profile = DeveloperPreviewProfile.DEFAULT
        val preflight = bolusCorrectionPreflight(
            createDeveloperPreviewBolusSnapshot(profile),
            createDeveloperPreviewLastBg(),
        )

        assertEquals(150, preflight.bgMgdl)
        assertEquals(110, preflight.targetMgdl)
        assertEquals(1.5, preflight.iobUnits!!, 0.001)
        assertEquals(BolusBgTargetRelation.ABOVE, preflight.relation)
        assertTrue(preflight.requiresChoice)
    }

    @Test
    fun aboveAndBelowTargetUseDifferentRelations() {
        val above = bolusCorrectionPreflight(
            createDeveloperPreviewBolusSnapshot(
                DeveloperPreviewProfile.DEFAULT.copy(cgmValueMgdl = 155),
            ),
            createDeveloperPreviewLastBg(),
        )
        val below = bolusCorrectionPreflight(
            createDeveloperPreviewBolusSnapshot(
                DeveloperPreviewProfile.DEFAULT.copy(cgmValueMgdl = 80),
            ),
            createDeveloperPreviewLastBg(),
        )

        assertEquals(BolusBgTargetRelation.ABOVE, above.relation)
        assertEquals(BolusBgTargetRelation.BELOW, below.relation)
    }

    @Test
    fun belowSeventyUsesAutomaticReverseCorrectionWhileSeventyStillOffersAChoice() {
        val atSeventy = bolusCorrectionPreflight(
            createDeveloperPreviewBolusSnapshot(
                DeveloperPreviewProfile.DEFAULT.copy(cgmValueMgdl = 70),
            ),
            createDeveloperPreviewLastBg(),
        )
        val belowSeventy = bolusCorrectionPreflight(
            createDeveloperPreviewBolusSnapshot(
                DeveloperPreviewProfile.DEFAULT.copy(cgmValueMgdl = 69),
            ),
            createDeveloperPreviewLastBg(),
        )

        assertTrue(atSeventy.requiresChoice)
        assertFalse(atSeventy.correctionRequired)
        assertFalse(belowSeventy.requiresChoice)
        assertTrue(belowSeventy.correctionRequired)
    }

    @Test
    fun correctionSwitchChangesTheIndependentFinalCalculation() {
        val profile = DeveloperPreviewProfile.DEFAULT.copy(startingIobUnits = 0.0)
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(profile),
            createDeveloperPreviewLastBg(),
            null,
            30,
            null,
        )

        val correctionOn = bolusCalcDecision(
            calculator,
            correctionExclusions(null, correctionEnabled = true),
        )!!.units
        val correctionOff = bolusCalcDecision(
            calculator,
            correctionExclusions(null, correctionEnabled = false),
        )!!.units

        assertEquals(4.0, correctionOn.total, 0.001)
        assertEquals(3.0, correctionOff.total, 0.001)
        assertEquals(1_000L, plannedCorrectionVolumeMilliunits(correctionOn, true))
        assertEquals(0L, plannedCorrectionVolumeMilliunits(correctionOff, false))
        assertFalse(
            correctionExclusions(null, true)
                .contains(com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition.POSITIVE_BG_CORRECTION),
        )
    }

    @Test
    fun twentyFiveGramPreviewFloorsExcessIobWithoutReducingTheFoodBolus() {
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(
                DeveloperPreviewProfile.DEFAULT.copy(startingIobUnits = 3.0),
            ),
            createDeveloperPreviewLastBg(),
            null,
            25,
            null,
        )

        val correctionOn = bolusCalcDecision(
            calculator,
            correctionExclusions(null, correctionEnabled = true),
        )!!.units
        val correctionOff = bolusCalcDecision(
            calculator,
            correctionExclusions(null, correctionEnabled = false),
        )!!.units

        assertEquals(2.5, correctionOn.fromCarbs, 0.001)
        assertEquals(1.0, correctionOn.fromBG, 0.001)
        assertEquals(-3.0, correctionOn.fromIOB, 0.001)
        assertEquals(2.5, correctionOn.total, 0.001)
        assertEquals(2.5, correctionOff.total, 0.001)
        assertEquals(0L, plannedCorrectionVolumeMilliunits(correctionOn, true))
    }
}
