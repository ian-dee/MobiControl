package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.GraphDisplayPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphDisplayDotColorTest {
    private val settings = GraphDisplayPreferences(
        lowerThresholdMgdl = 70,
        upperThresholdMgdl = 180,
    )

    @Test
    fun valuesExactlyOnEitherLimitStayInRange() {
        assertEquals(CgmDotRange.IN_RANGE, cgmDotRange(70.0, settings))
        assertEquals(CgmDotRange.IN_RANGE, cgmDotRange(180.0, settings))
    }

    @Test
    fun valuesOutsideLimitsUseTheirCorrespondingColorRange() {
        assertEquals(CgmDotRange.BELOW_LOWER_LIMIT, cgmDotRange(69.0, settings))
        assertEquals(CgmDotRange.ABOVE_UPPER_LIMIT, cgmDotRange(181.0, settings))
    }

    @Test
    fun eachColorToggleCanBeDisabledWithoutChangingTheOther() {
        val noHighColor = settings.copy(colorDotsAboveUpperLimit = false)
        assertEquals(CgmDotRange.IN_RANGE, cgmDotRange(250.0, noHighColor))
        assertEquals(CgmDotRange.BELOW_LOWER_LIMIT, cgmDotRange(60.0, noHighColor))

        val noLowColor = settings.copy(colorDotsBelowLowerLimit = false)
        assertEquals(CgmDotRange.ABOVE_UPPER_LIMIT, cgmDotRange(250.0, noLowColor))
        assertEquals(CgmDotRange.IN_RANGE, cgmDotRange(60.0, noLowColor))
    }

    @Test
    fun invalidAndOutOfChartValuesAreExcludedBeforeRendering() {
        assertEquals(true, isRenderableCgmValue(155.0, 30f, 410f))
        assertEquals(false, isRenderableCgmValue(30.0, 30f, 410f))
        assertEquals(false, isRenderableCgmValue(410.0, 30f, 410f))
        assertEquals(true, isRenderableCgmValue(30.1, 30f, 410f))
        assertEquals(true, isRenderableCgmValue(409.9, 30f, 410f))
        assertEquals(false, isRenderableCgmValue(0.0, 30f, 410f))
        assertEquals(false, isRenderableCgmValue(411.0, 30f, 410f))
        assertEquals(false, isRenderableCgmValue(Double.NaN, 30f, 410f))
    }
}
