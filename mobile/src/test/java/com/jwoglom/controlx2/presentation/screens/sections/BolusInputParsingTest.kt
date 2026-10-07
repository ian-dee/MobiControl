package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BolusInputParsingTest {
    @Test
    fun blankIntegerInputIsAbsentRatherThanZero() {
        assertNull(rawToInt(null))
        assertNull(rawToInt(""))
        assertNull(rawToInt("   "))
    }

    @Test
    fun explicitZeroRemainsZero() {
        assertEquals(0, rawToInt("0"))
    }

    @Test
    fun validIntegerIsParsed() {
        assertEquals(120, rawToInt("120"))
    }

    @Test
    fun carbohydratesAreIgnoredWhenDisabledInTheActiveProfile() {
        assertEquals(45, bolusCarbsForProfile("45", true))
        assertNull(bolusCarbsForProfile("45", false))
    }

    @Test
    fun blankGlucoseIsAbsentInBothDisplayUnits() {
        listOf(null, "", "   ").forEach { raw ->
            assertNull(parseBolusGlucoseMgdl(raw, GlucoseUnit.MGDL))
            assertNull(parseBolusGlucoseMgdl(raw, GlucoseUnit.MMOL))
        }
    }

    @Test
    fun validMgdlGlucoseIsParsedWithoutChangingItsValue() {
        assertEquals(155, parseBolusGlucoseMgdl("155", GlucoseUnit.MGDL))
        assertEquals(0, parseBolusGlucoseMgdl("0", GlucoseUnit.MGDL))
        assertNull(parseBolusGlucoseMgdl("155.5", GlucoseUnit.MGDL))
    }

    @Test
    fun decimalMmolGlucoseIsConvertedToMgdl() {
        assertEquals(155, parseBolusGlucoseMgdl("8.6", GlucoseUnit.MMOL))
        assertEquals(155, parseBolusGlucoseMgdl("8,6", GlucoseUnit.MMOL))
    }

    @Test
    fun incompleteMmolGlucoseIsAbsent() {
        assertNull(parseBolusGlucoseMgdl(".", GlucoseUnit.MMOL))
        assertNull(parseBolusGlucoseMgdl(",", GlucoseUnit.MMOL))
        assertNull(parseBolusGlucoseMgdl("8.", GlucoseUnit.MMOL))
        assertNull(parseBolusGlucoseMgdl("8,", GlucoseUnit.MMOL))
    }
}
