package com.jwoglom.controlx2.shared.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TandemCgmTrendTest {
    @Test
    fun `raw rate is signed tenths of mgdl per minute`() {
        assertEquals(0.6f, tandemCgmTrendRateMgdlPerMinute(6))
        assertEquals(-0.8f, tandemCgmTrendRateMgdlPerMinute(-8))
        assertEquals(2.0f, tandemCgmTrendRateMgdlPerMinute(20))
        assertEquals(-3.0f, tandemCgmTrendRateMgdlPerMinute(-30))
    }

    @Test
    fun `raw boundaries map to the seven tandem arrows`() {
        assertEquals("▼▼", tandemCgmTrendDirection(-30).tandemArrow)
        assertEquals("▼", tandemCgmTrendDirection(-20).tandemArrow)
        assertEquals("⬊", tandemCgmTrendDirection(-10).tandemArrow)
        assertEquals("▶", tandemCgmTrendDirection(-9).tandemArrow)
        assertEquals("▶", tandemCgmTrendDirection(6).tandemArrow)
        assertEquals("⬈", tandemCgmTrendDirection(10).tandemArrow)
        assertEquals("▲", tandemCgmTrendDirection(20).tandemArrow)
        assertEquals("▲▲", tandemCgmTrendDirection(30).tandemArrow)
    }
}
