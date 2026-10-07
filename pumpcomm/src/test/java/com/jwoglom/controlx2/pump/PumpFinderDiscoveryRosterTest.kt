package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PumpFinderDiscoveryRosterTest {
    @Test
    fun `same address upgrades from unnamed to named without duplicating`() {
        val roster = PumpFinderDiscoveryRoster()
        val generation = roster.startGeneration()

        roster.observe(
            generation,
            null,
            "aa:bb:cc:dd:ee:ff",
            PumpReadyState.CHARGING_OR_PICKED_UP,
        )
        roster.observe(
            generation,
            "Tandem Mobi 101",
            "AA:BB:CC:DD:EE:FF",
            PumpReadyState.PICKED_UP_WITH_TAP,
        )

        assertEquals(1, roster.entries().size)
        assertEquals("Tandem Mobi 101=AA:BB:CC:DD:EE:FF", roster.namedPumpPayload())
        assertEquals(PumpReadyState.PICKED_UP_WITH_TAP, roster.entries().single().readyState)
    }

    @Test
    fun `invalid address is rejected and unnamed entry is excluded from selectable payload`() {
        val roster = PumpFinderDiscoveryRoster()
        val generation = roster.startGeneration()

        assertNull(roster.observe(generation, "Tandem Mobi 101", "", PumpReadyState.PICKED_UP_WITH_TAP))
        assertNull(roster.observe(generation, "Tandem Mobi 101", "not-a-mac", PumpReadyState.PICKED_UP_WITH_TAP))
        roster.observe(generation, "NO NAME", "AA:BB:CC:DD:EE:FF", PumpReadyState.CHARGING_OR_PICKED_UP)

        assertEquals(1, roster.entries().size)
        assertEquals("", roster.namedPumpPayload())
    }

    @Test
    fun `callbacks from a prior generation are ignored`() {
        val roster = PumpFinderDiscoveryRoster()
        val staleGeneration = roster.startGeneration()
        val currentGeneration = roster.startGeneration()

        assertNull(
            roster.observe(
                staleGeneration,
                "Tandem Mobi 102",
                "AA:BB:CC:DD:EE:01",
                PumpReadyState.PICKED_UP_WITH_TAP,
            ),
        )
        roster.observe(
            currentGeneration,
            "Tandem Mobi 101",
            "AA:BB:CC:DD:EE:02",
            PumpReadyState.PICKED_UP_WITH_TAP,
        )

        assertEquals(listOf("AA:BB:CC:DD:EE:02"), roster.entries().map { it.address })
    }

    @Test
    fun `multiple addresses remain distinct`() {
        val roster = PumpFinderDiscoveryRoster()
        val generation = roster.startGeneration()

        roster.observe(generation, "Tandem Mobi 102", "AA:BB:CC:DD:EE:01", PumpReadyState.PICKED_UP_WITH_TAP)
        roster.observe(generation, "Tandem Mobi 101", "AA:BB:CC:DD:EE:02", PumpReadyState.PICKED_UP_WITH_TAP)

        assertEquals(2, roster.entries().size)
    }
}
