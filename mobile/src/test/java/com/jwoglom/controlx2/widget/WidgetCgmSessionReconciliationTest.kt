package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.WidgetCgmSessionCache
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetCgmSessionReconciliationTest {
    @Test
    fun `legacy active repairs stale full-lifetime startup cache`() {
        val stale = cache(
            phase = WidgetCgmSessionPhase.STARTUP,
            sensorStartedTimestamp = 0L,
            remaining = 1_296_000L,
        )

        val active = reconcileWidgetCgmSession(
            current = stale,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.LEGACY,
                phase = WidgetCgmPumpPhase.ACTIVE,
                sensorStartedTimestamp = 590_003_157L,
            ),
            observedAtEpochMillis = 2_000L,
        )

        assertEquals(WidgetCgmSessionPhase.ACTIVE, active.phase)
        assertEquals(590_003_157L, active.sensorStartedTimestamp)
        assertEquals(0L, active.sessionTimeRemainingSeconds)
    }

    @Test
    fun `stale v2 start pending cannot regress newer active widget state`() {
        val active = cache(
            phase = WidgetCgmSessionPhase.ACTIVE,
            sensorStartedTimestamp = 590_003_157L,
        )

        val reconciled = reconcileWidgetCgmSession(
            current = active,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.V2,
                phase = WidgetCgmPumpPhase.START_PENDING,
                sensorStartedTimestamp = 0L,
                sessionTimeRemainingSeconds = 1_296_000L,
                sessionDurationSeconds = 1_296_000L,
            ),
            observedAtEpochMillis = 2_000L,
        )

        assertEquals(WidgetCgmSessionPhase.ACTIVE, reconciled.phase)
        assertEquals(590_003_157L, reconciled.sensorStartedTimestamp)
    }

    @Test
    fun `one second active timestamp wobble retains canonical sensor identity`() {
        val active = cache(
            phase = WidgetCgmSessionPhase.ACTIVE,
            sensorStartedTimestamp = 590_003_156L,
        )

        val reconciled = reconcileWidgetCgmSession(
            current = active,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.LEGACY,
                phase = WidgetCgmPumpPhase.ACTIVE,
                sensorStartedTimestamp = 590_003_157L,
            ),
            observedAtEpochMillis = 2_000L,
        )

        assertEquals(590_003_156L, reconciled.sensorStartedTimestamp)
    }

    @Test
    fun `materially different active timestamp establishes replacement identity`() {
        val active = cache(
            phase = WidgetCgmSessionPhase.ACTIVE,
            sensorStartedTimestamp = 590_003_156L,
        )

        val reconciled = reconcileWidgetCgmSession(
            current = active,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.V2,
                phase = WidgetCgmPumpPhase.ACTIVE,
                sensorStartedTimestamp = 590_003_162L,
            ),
            observedAtEpochMillis = 2_000L,
        )

        assertEquals(590_003_162L, reconciled.sensorStartedTimestamp)
    }

    @Test
    fun `full-lifetime v2 pending state is pairing not startup`() {
        val reconciled = reconcileWidgetCgmSession(
            current = null,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.V2,
                phase = WidgetCgmPumpPhase.START_PENDING,
                sessionTimeRemainingSeconds = 1_296_000L,
                sessionDurationSeconds = 1_296_000L,
            ),
            observedAtEpochMillis = 2_000L,
        )

        assertEquals(WidgetCgmSessionPhase.PAIRING, reconciled.phase)
        assertEquals(0L, reconciled.sessionTimeRemainingSeconds)
    }

    @Test
    fun `credible v2 startup survives matching legacy pending status`() {
        val startup = reconcileWidgetCgmSession(
            current = null,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.V2,
                phase = WidgetCgmPumpPhase.START_PENDING,
                sessionTimeRemainingSeconds = 3_600L,
                sessionDurationSeconds = 1_296_000L,
            ),
            observedAtEpochMillis = 2_000L,
        )
        val reconciled = reconcileWidgetCgmSession(
            current = startup,
            observation = WidgetCgmSessionObservation(
                source = WidgetCgmSessionSource.LEGACY,
                phase = WidgetCgmPumpPhase.START_PENDING,
            ),
            observedAtEpochMillis = 3_000L,
        )

        assertEquals(WidgetCgmSessionPhase.STARTUP, reconciled.phase)
        assertEquals(3_600L, reconciled.sessionTimeRemainingSeconds)
    }

    private fun cache(
        phase: WidgetCgmSessionPhase,
        sensorStartedTimestamp: Long,
        remaining: Long = 0L,
    ) = WidgetCgmSessionCache(
        phase = phase,
        sensorStartedTimestamp = sensorStartedTimestamp,
        sessionTimeRemainingSeconds = remaining,
        observedAtEpochMillis = 1_000L,
    )
}
