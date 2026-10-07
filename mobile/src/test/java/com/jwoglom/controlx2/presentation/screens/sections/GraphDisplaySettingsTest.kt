package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphDisplaySettingsTest {
    @Test
    fun acceptsValidWholeNumberLimits() {
        val result = validateGraphDisplayThresholds(lowerRaw = "70", upperRaw = "180")

        assertTrue(result.isValid)
        assertEquals(70, result.lowerThresholdMgdl)
        assertEquals(180, result.upperThresholdMgdl)
    }

    @Test
    fun rejectsMissingOutOfRangeAndReversedLimits() {
        assertFalse(validateGraphDisplayThresholds("", "180").isValid)
        assertFalse(validateGraphDisplayThresholds("39", "180").isValid)
        assertFalse(validateGraphDisplayThresholds("70", "401").isValid)
        assertFalse(validateGraphDisplayThresholds("180", "180").isValid)
        assertFalse(validateGraphDisplayThresholds("190", "180").isValid)
    }
}
