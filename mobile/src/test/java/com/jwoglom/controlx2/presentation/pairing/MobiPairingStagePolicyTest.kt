package com.jwoglom.controlx2.presentation.pairing

import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobiPairingStagePolicyTest {
    private val now = 1_000_000L

    @Test
    fun `current-attempt charger signal reveals button instructions`() {
        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
            nextMobiInstructionStage(
                currentStage = PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS,
                candidate = candidate(name = null, readyState = PumpReadyState.CHARGING_OR_PICKED_UP),
                nowElapsedRealtimeMillis = now,
            ),
        )
    }

    @Test
    fun `fresh named tap reveals Mobi selection after gesture`() {
        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP,
            nextMobiInstructionStage(
                currentStage = PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
                candidate = candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = now,
                ),
                nowElapsedRealtimeMillis = now,
            ),
        )
    }

    @Test
    fun `stale tap and remembered name cannot reveal Mobi selection`() {
        val stale = candidate(
            name = "Tandem Mobi 101",
            readyState = PumpReadyState.PICKED_UP_WITH_TAP,
            readyObservedAt = now - MOBI_PAIRING_READINESS_WINDOW_MILLIS - 1L,
        )
        val remembered = candidate(
            name = "Tandem Mobi 101",
            readyState = PumpReadyState.UNKNOWN,
        )

        assertNull(
            nextMobiInstructionStage(
                PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
                stale,
                now,
            ),
        )
        assertNull(pairingSelectionStage(listOf(stale, remembered), now))
    }

    @Test
    fun `mixed fresh pumps use the shared picker while Mobi alone uses its serialized picker`() {
        val mobi = candidate(
            name = "Tandem Mobi 101",
            readyState = PumpReadyState.PICKED_UP_WITH_TAP,
            readyObservedAt = now,
        )
        val tslim = candidate(
            address = "AA:BB:CC:DD:EE:01",
            name = "tslim X2 ***123",
            readyState = PumpReadyState.UNKNOWN,
        )

        assertEquals(
            PumpSetupStage.PUMP_FINDER_SELECT_PUMP,
            pairingSelectionStage(listOf(tslim), now),
        )
        assertEquals(
            PumpSetupStage.PUMP_FINDER_SELECT_PUMP,
            pairingSelectionStage(listOf(tslim, mobi), now),
        )
        assertEquals(
            PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP,
            pairingSelectionStage(listOf(mobi), now),
        )
        assertEquals(
            PumpSetupStage.PUMP_FINDER_SELECT_PUMP,
            nextMobiInstructionStage(
                currentStage = PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
                candidate = mobi,
                nowElapsedRealtimeMillis = now,
                allCandidates = listOf(tslim, mobi),
            ),
        )
    }

    @Test
    fun `charger and tap signals cannot replace an active code entry`() {
        assertNull(
            nextMobiInstructionStage(
                PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
                candidate(name = null, readyState = PumpReadyState.CHARGING_OR_PICKED_UP),
                now,
            ),
        )
        assertNull(
            nextMobiInstructionStage(
                PumpSetupStage.PUMP_FINDER_MOBI_ENTER_PAIRING_CODE,
                candidate(
                    name = "Tandem Mobi 101",
                    readyState = PumpReadyState.PICKED_UP_WITH_TAP,
                    readyObservedAt = now,
                ),
                now,
            ),
        )
    }

    private fun candidate(
        address: String = "AA:BB:CC:DD:EE:FF",
        name: String?,
        readyState: PumpReadyState,
        readyObservedAt: Long? = null,
    ) = PumpPairingCandidate(
        address = address,
        name = name,
        readyState = readyState,
        readyObservedAtElapsedRealtimeMillis = readyObservedAt,
        lastSeenAtElapsedRealtimeMillis = now,
    )
}
