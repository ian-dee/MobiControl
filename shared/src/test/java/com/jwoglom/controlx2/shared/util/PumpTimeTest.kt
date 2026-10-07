package com.jwoglom.controlx2.shared.util

import com.jwoglom.pumpx2.pump.messages.helpers.Dates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PumpTimeTest {
    @Test
    fun `Pacific daylight time is encoded as local wall clock`() {
        assertEncodedWallClock(
            instant = Instant.parse("2026-08-18T20:27:22Z"),
            zoneId = ZoneId.of("America/Los_Angeles"),
            expectedWallClock = Instant.parse("2026-08-18T13:27:22Z"),
        )
    }

    @Test
    fun `Pacific standard time uses the winter UTC offset`() {
        assertEncodedWallClock(
            instant = Instant.parse("2026-01-18T20:27:22Z"),
            zoneId = ZoneId.of("America/Los_Angeles"),
            expectedWallClock = Instant.parse("2026-01-18T12:27:22Z"),
        )
    }

    @Test
    fun `fractional positive UTC offset is encoded correctly`() {
        assertEncodedWallClock(
            instant = Instant.parse("2026-08-18T20:27:22Z"),
            zoneId = ZoneId.of("Asia/Kolkata"),
            expectedWallClock = Instant.parse("2026-08-19T01:57:22Z"),
        )
    }

    @Test
    fun `request builder uses the local wall clock payload`() {
        val request = phoneLocalTimeChangeRequest(
            instant = Instant.parse("2026-08-18T20:27:22Z"),
            zoneId = ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(
            Instant.parse("2026-08-18T13:27:22Z"),
            request.instant,
        )
    }

    private fun assertEncodedWallClock(
        instant: Instant,
        zoneId: ZoneId,
        expectedWallClock: Instant,
    ) {
        val encoded = phoneLocalTimeAsTandemEpochSeconds(instant, zoneId)

        assertEquals(
            expectedWallClock,
            Dates.fromJan12008EpochSecondsToDate(encoded),
        )
    }
}
