package com.jwoglom.controlx2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetCgmSampleCacheTest {
    @Test
    fun `valid glucose and trend round trip atomically`() {
        val sample = WidgetCgmSampleCache(
            glucoseMgdl = 159,
            trendRateRaw = 6,
            sensorStartedTimestamp = 1234L,
            observedAtEpochMillis = 5678L,
        )

        assertEquals(sample, WidgetCgmSampleCache.decode(sample.encode()))
    }

    @Test
    fun `unavailable sample round trips without a trend`() {
        val sample = WidgetCgmSampleCache(
            glucoseMgdl = null,
            trendRateRaw = null,
            sensorStartedTimestamp = 1234L,
            observedAtEpochMillis = 5678L,
        )

        assertEquals(sample, WidgetCgmSampleCache.decode(sample.encode()))
    }

    @Test
    fun `malformed or internally mismatched samples are rejected`() {
        assertNull(WidgetCgmSampleCache.decode("1|159|6|0|5678"))
        assertNull(WidgetCgmSampleCache.decode("1||6|1234|5678"))
        assertNull(WidgetCgmSampleCache.decode("not-a-sample"))
    }
}
