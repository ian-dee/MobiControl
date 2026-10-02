package com.jwoglom.controlx2.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PumpHydrationStateTest {
    @Test
    fun `hydration state codec is strict`() {
        val state = PumpHydrationState(PumpHydrationPhase.LIVE_STATE_READY, 123_456L)

        assertEquals(state, PumpHydrationState.decode(state.encode()))
        assertNull(PumpHydrationState.decode("1|UNKNOWN|123".toByteArray()))
        assertNull(PumpHydrationState.decode("bad".toByteArray()))
    }
}
