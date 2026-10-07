package com.jwoglom.controlx2.presentation.pairing

import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpPairingDiscoveryStateTest {
    private val now = 1_000_000L

    @Test
    fun `empty and malformed payloads do not create candidates`() {
        val state = PumpPairingDiscoveryState()

        state.observeFoundPumpsPayload("", now)
        assertNull(state.observeDiscoveredPayload("", now))
        assertNull(state.observeDiscoveredPayload("Tandem Mobi 101=bad;;PICKED_UP_WITH_TAP", now))
        assertNull(state.observeDiscoveredPayload("Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;NOT_A_STATE", now))

        assertTrue(state.candidates().isEmpty())
    }

    @Test
    fun `unnamed pump is nonselectable then upgrades in place on same normalized address`() {
        val state = PumpPairingDiscoveryState()

        val unnamed = state.observeDiscoveredPayload(
            "NO NAME=aa:bb:cc:dd:ee:ff;;CHARGING_OR_PICKED_UP",
            now,
        )!!
        assertNull(unnamed.name)
        assertTrue(unnamed.indicatesMobiOnCharger())
        assertFalse(unnamed.isSelectable(now))
        assertFalse(state.hasSelectableCandidate(now))

        val named = state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now + 1_000L,
        )!!
        assertEquals(1, state.candidates().size)
        assertEquals("Tandem Mobi 101", named.name)
        assertEquals("AA:BB:CC:DD:EE:FF", named.address)
        assertTrue(named.isSelectable(now + 1_000L))
        assertTrue(state.hasSelectableCandidate(now + 1_000L))
    }

    @Test
    fun `charger instruction signal rejects a named unsupported device`() {
        val state = PumpPairingDiscoveryState()

        val unsupported = state.observeDiscoveredPayload(
            "Another Pump=AA:BB:CC:DD:EE:FF;;CHARGING_OR_PICKED_UP",
            now,
        )!!

        assertFalse(unsupported.indicatesMobiOnCharger())
        assertFalse(unsupported.isSelectable(now))
    }

    @Test
    fun `named Mobi from summary remains blocked until same address reports fresh tap`() {
        val state = PumpPairingDiscoveryState()
        state.observeFoundPumpsPayload("Tandem Mobi 101=AA:BB:CC:DD:EE:FF", now)

        assertNull(state.select("AA:BB:CC:DD:EE:FF", now))
        state.observeDiscoveredPayload(
            "Tandem Mobi 102=AA:BB:CC:DD:EE:01;;PICKED_UP_WITH_TAP",
            now + 1L,
        )
        assertNull(state.select("AA:BB:CC:DD:EE:FF", now + 1L))

        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now + 2L,
        )
        assertEquals("AA:BB:CC:DD:EE:FF", state.select("aa:bb:cc:dd:ee:ff", now + 2L)?.address)
    }

    @Test
    fun `repeated tap advertisements do not extend the readiness window`() {
        val state = PumpPairingDiscoveryState()
        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now,
        )
        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now + 100_000L,
        )

        assertTrue(state.candidate("AA:BB:CC:DD:EE:FF")!!.isSelectable(now + 120_000L))
        assertFalse(state.candidate("AA:BB:CC:DD:EE:FF")!!.isSelectable(now + 120_001L))
    }

    @Test
    fun `a return to charging clears readiness and a later tap starts a new window`() {
        val state = PumpPairingDiscoveryState()
        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now,
        )
        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;CHARGING_OR_PICKED_UP",
            now + 1_000L,
        )
        assertFalse(state.candidate("AA:BB:CC:DD:EE:FF")!!.isSelectable(now + 1_000L))

        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:FF;;PICKED_UP_WITH_TAP",
            now + 2_000L,
        )
        assertEquals(
            now + 2_000L,
            state.candidate("AA:BB:CC:DD:EE:FF")!!.readyObservedAtElapsedRealtimeMillis,
        )
    }

    @Test
    fun `multiple pumps stay distinct and reset removes all attempt state`() {
        val state = PumpPairingDiscoveryState()
        state.observeDiscoveredPayload(
            "Tandem Mobi 102=AA:BB:CC:DD:EE:01;;PICKED_UP_WITH_TAP",
            now,
        )
        state.observeDiscoveredPayload(
            "Tandem Mobi 101=AA:BB:CC:DD:EE:02;;PICKED_UP_WITH_TAP",
            now,
        )
        assertEquals(2, state.candidates().size)

        state.reset()

        assertTrue(state.candidates().isEmpty())
        assertNull(state.select("AA:BB:CC:DD:EE:02", now))
    }

    @Test
    fun `recognized tslim is selectable without applying Mobi readiness semantics`() {
        val state = PumpPairingDiscoveryState()
        state.observeFoundPumpsPayload("tslim X2 ***123=AA:BB:CC:DD:EE:FF", now)

        assertEquals(PumpReadyState.UNKNOWN, state.candidates().single().readyState)
        assertTrue(state.candidates().single().isSelectable(now))
    }
}
