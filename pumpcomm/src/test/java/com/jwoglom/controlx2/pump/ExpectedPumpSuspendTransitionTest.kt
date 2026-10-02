package com.jwoglom.controlx2.pump

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectedPumpSuspendTransitionTest {
    @Test
    fun acceptedSuspendKeepsTransitionActiveUntilReconnectClearsIt() {
        var now = 0L
        val transition = ExpectedPumpSuspendTransition(nowNanos = { now }, timeoutMs = 5_000)

        transition.begin()
        transition.onResponse(0)
        assertTrue(transition.isActive())

        transition.clear()
        assertFalse(transition.isActive())
    }

    @Test
    fun rejectedSuspendImmediatelyClearsTransition() {
        val transition = ExpectedPumpSuspendTransition(nowNanos = { 0L })

        transition.begin()
        transition.onResponse(4)

        assertFalse(transition.isActive())
    }

    @Test
    fun transitionExpiresIfThePumpDoesNotDisconnect() {
        var now = 0L
        val transition = ExpectedPumpSuspendTransition(nowNanos = { now }, timeoutMs = 5_000)

        transition.begin()
        now = 5_000L * 1_000_000L + 1L

        assertFalse(transition.isActive())
    }
}
