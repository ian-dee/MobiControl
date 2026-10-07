package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusSnapshotRefreshGateTest {
    @Test
    fun duplicateRefreshIsCoalescedUntilTheFirstRefreshFinishes() {
        val gate = BolusSnapshotRefreshGate()

        assertTrue(gate.tryBegin())
        assertFalse(gate.tryBegin())

        gate.finish()
        assertTrue(gate.tryBegin())
    }
}
