package com.jwoglom.controlx2.presentation.screens.sections.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpStatusGaugePolicyTest {
    @Test
    fun `battery fill preserves exact reported percentage and clamps invalid values`() {
        assertEquals(0f, batteryFillFraction(null), 0f)
        assertEquals(0f, batteryFillFraction(-5), 0f)
        assertEquals(0.37f, batteryFillFraction(37), 0.0001f)
        assertEquals(1f, batteryFillFraction(140), 0f)
    }

    @Test
    fun `low battery state starts at twenty percent inclusively`() {
        assertFalse(isBatteryLow(null))
        assertFalse(isBatteryLow(21))
        assertTrue(isBatteryLow(20))
        assertTrue(isBatteryLow(1))
        assertTrue(isBatteryLow(0))
    }

    @Test
    fun `low battery renders one twenty percent red bar while retaining exact percentage`() {
        assertEquals(0.47f, batteryGaugeFillFraction(47, low = false), 0.0001f)
        assertEquals(0.20f, batteryGaugeFillFraction(20, low = true), 0.0001f)
        assertEquals(0.20f, batteryGaugeFillFraction(4, low = true), 0.0001f)
        assertEquals(0.20f, batteryGaugeFillFraction(0, low = true), 0.0001f)
    }

    @Test
    fun `cartridge fill preserves exact unit resolution and clamps to capacity`() {
        assertEquals(0f, cartridgeFillFraction(null), 0f)
        assertEquals(0f, cartridgeFillFraction(-1), 0f)
        assertEquals(0.005f, cartridgeFillFraction(1), 0.0001f)
        assertEquals(0.555f, cartridgeFillFraction(111), 0.0001f)
        assertEquals(1f, cartridgeFillFraction(240), 0f)
        assertEquals(0f, cartridgeFillFraction(20, cartridgeMax = 0), 0f)
    }

    @Test
    fun `low insulin state honors the pump reported threshold inclusively`() {
        assertFalse(isCartridgeLow(null))
        assertFalse(isCartridgeLow(21))
        assertTrue(isCartridgeLow(20))
        assertFalse(isCartridgeLow(16, lowAlertUnits = 15))
        assertTrue(isCartridgeLow(15, lowAlertUnits = 15))
    }

    @Test
    fun `official Mobi text remains numeric until one unit then displays low`() {
        assertEquals("-- U", cartridgeLevelText(null))
        assertEquals("42 U", cartridgeLevelText(42))
        assertEquals("42+ U", cartridgeLevelText(42, cartridgeAmountEstimate = true))
        assertEquals("2 U", cartridgeLevelText(2))
        assertEquals("LOW", cartridgeLevelText(1))
        assertEquals("LOW", cartridgeLevelText(0))
    }

    @Test
    fun `low alert renders one twenty percent red bar without falsifying numeric units`() {
        assertEquals(0.50f, cartridgeGaugeFillFraction(100, low = false), 0.0001f)
        assertEquals(0.20f, cartridgeGaugeFillFraction(20, low = true), 0.0001f)
        assertEquals(0.20f, cartridgeGaugeFillFraction(1, low = true), 0.0001f)
    }
}
