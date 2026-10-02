package com.jwoglom.controlx2.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutProfileTest {
    @Test
    fun `flip 8 cover window selects compact cover layout`() {
        assertEquals(
            WindowLayoutProfile.COVER_COMPACT,
            windowLayoutProfile(widthDp = 399, heightDp = 441),
        )
    }

    @Test
    fun `flip 8 size compatibility cover window selects compact cover layout`() {
        assertEquals(
            WindowLayoutProfile.COVER_COMPACT,
            windowLayoutProfile(widthDp = 474, heightDp = 523),
        )
    }

    @Test
    fun `unfolded portrait window keeps standard layout`() {
        assertEquals(
            WindowLayoutProfile.STANDARD,
            windowLayoutProfile(widthDp = 360, heightDp = 840),
        )
    }

    @Test
    fun `short but wide window keeps standard layout`() {
        assertEquals(
            WindowLayoutProfile.STANDARD,
            windowLayoutProfile(widthDp = 840, heightDp = 399),
        )
    }
}
