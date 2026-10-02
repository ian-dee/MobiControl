package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmAlertThresholdLinesTest {
    @Test
    fun fixedLowLineIsAlwaysPresent() {
        val lines = buildCgmAlertThresholdLines(
            highEnabled = false,
            highThresholdMgdl = 200,
            lowEnabled = false,
            lowThresholdMgdl = 80,
        )

        assertEquals(
            listOf(CgmAlertThresholdLine(CgmAlertThresholdLineKind.FIXED_LOW, 55)),
            lines,
        )
    }

    @Test
    fun enabledHighAndLowAlertsCreateIndependentLines() {
        val highOnly = buildCgmAlertThresholdLines(true, 190, false, 75)
        assertEquals(
            listOf(CgmAlertThresholdLineKind.FIXED_LOW, CgmAlertThresholdLineKind.HIGH_ALERT),
            highOnly.map { it.kind },
        )

        val lowOnly = buildCgmAlertThresholdLines(false, 190, true, 75)
        assertEquals(
            listOf(CgmAlertThresholdLineKind.FIXED_LOW, CgmAlertThresholdLineKind.LOW_ALERT),
            lowOnly.map { it.kind },
        )

        val both = buildCgmAlertThresholdLines(true, 190, true, 75)
        assertEquals(3, both.size)
        assertEquals(75, both.first { it.kind == CgmAlertThresholdLineKind.LOW_ALERT }.glucoseMgdl)
        assertEquals(190, both.first { it.kind == CgmAlertThresholdLineKind.HIGH_ALERT }.glucoseMgdl)
    }

    @Test
    fun graphDisplayLinesAreIndependentAndAlwaysKeepFixedLow() {
        val hidden = buildGraphDisplayThresholdLines(
            showUpperLimitLine = false,
            upperThresholdMgdl = 180,
            showLowerLimitLine = false,
            lowerThresholdMgdl = 70,
        )
        assertEquals(
            listOf(CgmAlertThresholdLineKind.FIXED_LOW),
            hidden.map { it.kind },
        )

        val visible = buildGraphDisplayThresholdLines(
            showUpperLimitLine = true,
            upperThresholdMgdl = 180,
            showLowerLimitLine = true,
            lowerThresholdMgdl = 70,
        )
        assertEquals(
            listOf(
                CgmAlertThresholdLineKind.FIXED_LOW,
                CgmAlertThresholdLineKind.LOWER_LIMIT,
                CgmAlertThresholdLineKind.UPPER_LIMIT,
            ),
            visible.map { it.kind },
        )
        assertEquals("Lower Control Limit 70", visible[1].displayLabel(GlucoseUnit.MGDL))
        assertEquals("Upper Control Limit 180", visible[2].displayLabel(GlucoseUnit.MGDL))
    }

    @Test
    fun pumpAlertsAndGraphLimitsCoexistWithoutDuplicatingFixedLow() {
        val combined = combineCgmAndGraphDisplayThresholdLines(
            cgmAlertLines = buildCgmAlertThresholdLines(true, 200, true, 80),
            graphDisplayLines = buildGraphDisplayThresholdLines(true, 180, true, 70),
        )

        assertEquals(
            listOf(
                CgmAlertThresholdLineKind.FIXED_LOW,
                CgmAlertThresholdLineKind.LOW_ALERT,
                CgmAlertThresholdLineKind.HIGH_ALERT,
                CgmAlertThresholdLineKind.LOWER_LIMIT,
                CgmAlertThresholdLineKind.UPPER_LIMIT,
            ),
            combined.map { it.kind },
        )
        assertEquals(1, combined.count { it.kind == CgmAlertThresholdLineKind.FIXED_LOW })
    }

    @Test
    fun onlyControlLimitsAreUnlabeledOnTheChart() {
        assertTrue(!CgmAlertThresholdLineKind.FIXED_LOW.isLabeledOnChart())
        assertTrue(CgmAlertThresholdLineKind.LOW_ALERT.isLabeledOnChart())
        assertTrue(CgmAlertThresholdLineKind.HIGH_ALERT.isLabeledOnChart())
        assertTrue(!CgmAlertThresholdLineKind.LOWER_LIMIT.isLabeledOnChart())
        assertTrue(!CgmAlertThresholdLineKind.UPPER_LIMIT.isLabeledOnChart())
    }

    @Test
    fun glucoseValueMapsToFixedChartRange() {
        assertEquals(0f, cgmAlertThresholdYFraction(410, 30f, 410f), 0.0001f)
        assertEquals(1f, cgmAlertThresholdYFraction(30, 30f, 410f), 0.0001f)
        assertEquals(0.5f, cgmAlertThresholdYFraction(220, 30f, 410f), 0.0001f)
        assertTrue(cgmAlertThresholdYFraction(55, 30f, 410f) > 0.9f)
    }

    @Test
    fun labelsRespectDisplayedGlucoseUnits() {
        val fixedLow = CgmAlertThresholdLine(CgmAlertThresholdLineKind.FIXED_LOW, 55)
        val low = CgmAlertThresholdLine(CgmAlertThresholdLineKind.LOW_ALERT, 80)
        val high = CgmAlertThresholdLine(CgmAlertThresholdLineKind.HIGH_ALERT, 200)
        assertEquals("Fixed Low Alert 55", fixedLow.displayLabel(GlucoseUnit.MGDL))
        assertEquals("Fixed Low Alert 3.1", fixedLow.displayLabel(GlucoseUnit.MMOL))
        assertEquals("CGM Low Alert 80", low.displayLabel(GlucoseUnit.MGDL))
        assertEquals("CGM High Alert 200", high.displayLabel(GlucoseUnit.MGDL))
    }

    @Test
    fun labelsNearEachOtherAreSeparatedWithoutLeavingChart() {
        val lines = listOf(
            CgmAlertThresholdLine(CgmAlertThresholdLineKind.FIXED_LOW, 55),
            CgmAlertThresholdLine(CgmAlertThresholdLineKind.LOWER_LIMIT, 70),
            CgmAlertThresholdLine(CgmAlertThresholdLineKind.UPPER_LIMIT, 180),
        )

        val offsets = resolveThresholdLabelTopOffsets(
            lines = lines,
            containerHeightDp = 240f,
            minimumGlucoseMgdl = 40f,
            maximumGlucoseMgdl = 400f,
        )

        assertTrue(offsets.all { it in 0f..228f })
        val sortedOffsets = offsets.sorted()
        assertTrue(sortedOffsets.zipWithNext().all { (first, second) -> second - first >= 14f })
    }
}
