package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.plannedCorrectionVolumeMilliunits
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewLastBg
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Release-gate checks for the values that ultimately cross the pump protocol boundary. */
class BolusSafetyMatrixTest {
    @Test
    fun userFacingUnitsConvertToExactProtocolMilliunits() {
        val cases = listOf(
            0.05 to 50L,
            0.10 to 100L,
            0.30 to 300L,
            1.50 to 1_500L,
            5.37 to 5_370L,
            7.52 to 7_520L,
            10.00 to 10_000L,
            15.00 to 15_000L,
        )

        cases.forEach { (units, expectedMilliunits) ->
            val actualMilliunits = InsulinUnit.from1To1000(units)
            assertEquals("$units U protocol value", expectedMilliunits, actualMilliunits)
            assertEquals(
                "$units U protocol round trip",
                units,
                InsulinUnit.from1000To1(actualMilliunits),
                0.000_001,
            )
        }
    }

    @Test
    fun foodCorrectionAndIobMatrixMatchesTheDisplayedAndTransmittedValues() {
        val cases = listOf(
            ExpectedCalculation(bgMgdl = 150, iobUnits = 0.0, food = 2.5, bg = 1.0, iob = 0.0, total = 3.5, transmittedCorrectionMilliunits = 1_000L),
            ExpectedCalculation(bgMgdl = 150, iobUnits = 1.5, food = 2.5, bg = 1.0, iob = -1.5, total = 2.5, transmittedCorrectionMilliunits = 0L),
            ExpectedCalculation(bgMgdl = 110, iobUnits = 1.5, food = 2.5, bg = 0.0, iob = -1.5, total = 2.5, transmittedCorrectionMilliunits = 0L),
            ExpectedCalculation(bgMgdl = 90, iobUnits = 0.0, food = 2.5, bg = -0.5, iob = 0.0, total = 2.0, transmittedCorrectionMilliunits = 0L),
            ExpectedCalculation(bgMgdl = 60, iobUnits = 0.0, food = 2.5, bg = -1.25, iob = 0.0, total = 1.25, transmittedCorrectionMilliunits = 0L),
        )

        cases.forEach { expected ->
            val profile = DeveloperPreviewProfile.DEFAULT.copy(
                startingIobUnits = expected.iobUnits,
                cgmValueMgdl = expected.bgMgdl,
            )
            val calculator = buildBolusCalculator(
                createDeveloperPreviewBolusSnapshot(profile),
                createDeveloperPreviewLastBg(),
                null,
                25,
                null,
            )
            val actual = requireNotNull(
                bolusCalcDecision(calculator, correctionExclusions(null, correctionEnabled = true)),
            ).units

            assertEquals("${expected.bgMgdl} mg/dL food", expected.food, actual.fromCarbs, 0.001)
            assertEquals("${expected.bgMgdl} mg/dL correction", expected.bg, actual.fromBG, 0.001)
            assertEquals("${expected.bgMgdl} mg/dL IOB", expected.iob, actual.fromIOB, 0.001)
            assertEquals("${expected.bgMgdl} mg/dL total", expected.total, actual.total, 0.001)
            assertEquals(
                "${expected.bgMgdl} mg/dL transmitted correction",
                expected.transmittedCorrectionMilliunits,
                plannedCorrectionVolumeMilliunits(actual, correctionEnabled = true),
            )
            assertEquals(
                "${expected.bgMgdl} mg/dL transmitted total",
                InsulinUnit.from1To1000(expected.total),
                InsulinUnit.from1To1000(actual.total),
            )
        }
    }

    @Test
    fun decliningOptionalCorrectionSendsNoCorrectionComponent() {
        val profile = DeveloperPreviewProfile.DEFAULT.copy(
            startingIobUnits = 0.0,
            cgmValueMgdl = 155,
        )
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(profile),
            createDeveloperPreviewLastBg(),
            null,
            25,
            null,
        )
        val calculation = requireNotNull(
            bolusCalcDecision(calculator, correctionExclusions(null, correctionEnabled = false)),
        ).units

        assertEquals(2.5, calculation.total, 0.001)
        assertEquals(0L, plannedCorrectionVolumeMilliunits(calculation, correctionEnabled = false))
        assertEquals(2_500L, InsulinUnit.from1To1000(calculation.total))
    }

    @Test
    fun manualOverrideIsAuthoritativeRegardlessOfRetainedCorrectionChoice() {
        val profile = DeveloperPreviewProfile.DEFAULT.copy(
            startingIobUnits = 0.0,
            cgmValueMgdl = 155,
        )
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(profile),
            createDeveloperPreviewLastBg(),
            2.75,
            25,
            155,
        )

        listOf(true, false).forEach { retainedCorrectionChoice ->
            val calculation = requireNotNull(
                bolusCalcDecision(
                    calculator,
                    correctionExclusions(null, correctionEnabled = retainedCorrectionChoice),
                ),
            ).units

            assertEquals(2.75, calculation.total, 0.001)
            assertEquals(0.0, calculation.fromCarbs, 0.001)
            assertEquals(0.0, calculation.fromBG, 0.001)
            assertEquals(0.0, calculation.fromIOB, 0.001)
            assertEquals(2_750L, InsulinUnit.from1To1000(calculation.total))
            assertEquals(
                0L,
                plannedCorrectionVolumeMilliunits(
                    calculation,
                    correctionEnabled = retainedCorrectionChoice,
                ),
            )
        }
    }

    private data class ExpectedCalculation(
        val bgMgdl: Int,
        val iobUnits: Double,
        val food: Double,
        val bg: Double,
        val iob: Double,
        val total: Double,
        val transmittedCorrectionMilliunits: Long,
    )
}
