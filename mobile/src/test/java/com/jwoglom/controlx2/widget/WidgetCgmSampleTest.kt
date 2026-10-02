package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.WidgetCgmSessionCache
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCgmSampleTest {
    private val activeSession = WidgetCgmSessionCache(
        phase = WidgetCgmSessionPhase.ACTIVE,
        sensorStartedTimestamp = 1234L,
        sessionTimeRemainingSeconds = 0L,
        observedAtEpochMillis = 5000L,
    )

    @Test
    fun `valid egv captures glucose trend time and sensor identity together`() {
        val sample = currentEgvWidgetSample(
            CurrentEGVGuiDataResponse(1000L, 159, 1, 6),
            activeSession,
            observedAtEpochMillis = 5678L,
        )

        assertEquals(159, sample?.glucoseMgdl)
        assertEquals(6, sample?.trendRateRaw)
        assertEquals(1234L, sample?.sensorStartedTimestamp)
        assertEquals(5678L, sample?.observedAtEpochMillis)
    }

    @Test
    fun `special or unavailable egv never invents an arrow`() {
        val low = currentEgvWidgetSample(
            CurrentEGVGuiDataResponse(1000L, 0, 2, -30),
            activeSession,
            observedAtEpochMillis = 5678L,
        )
        val unavailable = currentEgvWidgetSample(
            CurrentEGVGuiDataResponse(1000L, 0, 4, 30),
            activeSession,
            observedAtEpochMillis = 5678L,
        )

        assertEquals(39, low?.glucoseMgdl)
        assertNull(low?.trendRateRaw)
        assertNull(unavailable?.glucoseMgdl)
        assertNull(unavailable?.trendRateRaw)
    }

    @Test
    fun `non-active session cannot create a current widget sample`() {
        assertNull(
            currentEgvWidgetSample(
                CurrentEGVGuiDataResponse(1000L, 159, 1, 6),
                activeSession.copy(phase = WidgetCgmSessionPhase.STARTUP),
                observedAtEpochMillis = 5678L,
            ),
        )
    }
}
