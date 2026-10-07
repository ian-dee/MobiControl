package com.jwoglom.controlx2.sync.gdh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GdhBroadcastSenderTest {
    private val payload = GdhPayload(
        mgdl = 145,
        readingAt = Instant.parse("2026-08-24T17:00:00Z"),
        trendRateMgdlPerMinute = 2f,
        sensorId = "MobiControl-68",
        sensorStartedAt = Instant.parse("2026-08-20T17:00:00Z"),
        iobUnits = 1.25f,
        iobObservedAt = Instant.parse("2026-08-24T17:00:05Z"),
        sourcePackage = "com.jwoglom.controlx2",
    )

    @Test
    fun constantsMatchGdhMinuteBroadcastContract() {
        assertEquals("glucodata.Minute", GdhBroadcastSender.ACTION_GLUCODATA_MINUTE)
        assertEquals("glucodata.Minute.mgdl", GdhBroadcastSender.EXTRA_MGDL)
        assertEquals("glucodata.Minute.Time", GdhBroadcastSender.EXTRA_TIME)
        assertEquals("glucodata.Minute.Rate", GdhBroadcastSender.EXTRA_RATE)
        assertEquals("glucodata.Minute.SerialNumber", GdhBroadcastSender.EXTRA_SERIAL)
        assertEquals("glucodata.Minute.IOB", GdhBroadcastSender.EXTRA_IOB)
        assertEquals("gdh.IOB_COB_time", GdhBroadcastSender.EXTRA_IOB_COB_TIME)
        assertEquals("gdh.sensor_start_time", GdhBroadcastSender.EXTRA_SENSOR_START_TIME)
        assertEquals("gdh.source_package", GdhBroadcastSender.EXTRA_SOURCE_PACKAGE)
    }

    @Test
    fun sendsToEveryInstalledGdhPackage() {
        val sent = mutableListOf<Pair<String, GdhPayload>>()
        val sender = GdhBroadcastSender(
            installedPackagesProvider = {
                listOf(GdhBroadcastSender.GDH_PACKAGE, GdhBroadcastSender.GDH_SECOND_PACKAGE)
            },
            sendBroadcastFn = { packageName, emittedPayload ->
                sent += packageName to emittedPayload
                true
            },
        )

        assertTrue(sender.send(payload))
        assertEquals(
            listOf(GdhBroadcastSender.GDH_PACKAGE, GdhBroadcastSender.GDH_SECOND_PACKAGE),
            sent.map { it.first },
        )
        assertTrue(sent.all { it.second == payload })
    }

    @Test
    fun unavailableGdhReturnsFalseWithoutBroadcasting() {
        var calls = 0
        val sender = GdhBroadcastSender(
            installedPackagesProvider = { emptyList() },
            sendBroadcastFn = { _, _ -> calls += 1; true },
        )

        assertFalse(sender.send(payload))
        assertEquals(0, calls)
    }

    @Test
    fun oneBrokenInstallationDoesNotBlockTheOther() {
        val attempted = mutableListOf<String>()
        val sender = GdhBroadcastSender(
            installedPackagesProvider = {
                listOf(GdhBroadcastSender.GDH_PACKAGE, GdhBroadcastSender.GDH_SECOND_PACKAGE)
            },
            sendBroadcastFn = { packageName, _ ->
                attempted += packageName
                if (packageName == GdhBroadcastSender.GDH_PACKAGE) error("stopped")
                true
            },
        )

        assertTrue(sender.send(payload))
        assertEquals(2, attempted.size)
    }
}
