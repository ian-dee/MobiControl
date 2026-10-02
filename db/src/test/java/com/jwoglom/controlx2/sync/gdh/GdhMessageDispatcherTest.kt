package com.jwoglom.controlx2.sync.gdh

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GdhMessageDispatcherTest {
    private class FakeBroadcaster : GdhBroadcaster {
        val payloads = mutableListOf<GdhPayload>()
        var succeeds = true

        override fun send(payload: GdhPayload): Boolean {
            if (succeeds) payloads += payload
            return succeeds
        }
    }

    private val now = Instant.parse("2026-08-24T17:00:00Z")
    private val readingAt = now.minusSeconds(300)

    private fun dispatcher(
        broadcaster: FakeBroadcaster,
        config: () -> GdhSyncConfig = { GdhSyncConfig(enabled = true) },
        currentNow: () -> Instant = { now },
    ) = GdhMessageDispatcher(
        broadcaster = broadcaster,
        configProvider = config,
        nowProvider = currentNow,
        pumpSidProvider = { 68 },
        sourcePackageProvider = { "com.jwoglom.controlx2" },
        pumpTimestampToInstant = { readingAt },
    )

    private fun cgm(identity: String = "cgm-1", at: Instant = readingAt) =
        GdhDispatchEvent.CgmReading(145, 2, at, identity)

    @Test
    fun disabledExportProducesNoPayload() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(
            broadcaster,
            config = { GdhSyncConfig(enabled = false) },
        )

        dispatcher.onEvent(cgm())

        assertTrue(broadcaster.payloads.isEmpty())
    }

    @Test
    fun liveCgmCarriesPumpTimeTrendSensorAndSource() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)
        val sensorStart = now.minusSeconds(86_400)

        dispatcher.onEvent(GdhDispatchEvent.SensorSession(true, sensorStart))
        dispatcher.onEvent(cgm())

        val payload = broadcaster.payloads.single()
        assertEquals(145, payload.mgdl)
        assertEquals(readingAt, payload.readingAt)
        assertEquals(0.2f, payload.trendRateMgdlPerMinute)
        assertEquals("MobiControl-68", payload.sensorId)
        assertEquals(sensorStart, payload.sensorStartedAt)
        assertEquals("com.jwoglom.controlx2", payload.sourcePackage)
        assertNull(payload.iobUnits)
    }

    @Test
    fun retainedTraceRateIsScaledBeforeGdhEmission() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onEvent(GdhDispatchEvent.CgmReading(113, 6, readingAt, "trace-raw-6"))

        assertEquals(0.6f, broadcaster.payloads.single().trendRateMgdlPerMinute)
    }

    @Test
    fun duplicateCgmIsSuppressedButFailedEmissionCanRetry() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        broadcaster.succeeds = false
        dispatcher.onEvent(cgm())
        broadcaster.succeeds = true
        dispatcher.onEvent(cgm())
        dispatcher.onEvent(cgm())

        assertEquals(1, broadcaster.payloads.size)
    }

    @Test
    fun staleOrFarFutureCgmIsNotSent() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onEvent(cgm("old", now.minusSeconds(601)))
        dispatcher.onEvent(cgm("future", now.plusSeconds(121)))

        assertTrue(broadcaster.payloads.isEmpty())
    }

    @Test
    fun freshIobIsAttachedAndCanUpdateAgainstSameGlucoseTime() {
        val broadcaster = FakeBroadcaster()
        var currentNow = now
        val dispatcher = dispatcher(broadcaster, currentNow = { currentNow })

        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.25f, currentNow))
        dispatcher.onEvent(cgm())
        currentNow = now.plusSeconds(10)
        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.0f, currentNow))

        assertEquals(2, broadcaster.payloads.size)
        assertEquals(1.25f, broadcaster.payloads[0].iobUnits)
        assertEquals(1.0f, broadcaster.payloads[1].iobUnits)
        assertEquals(readingAt, broadcaster.payloads[1].readingAt)
        assertEquals(currentNow, broadcaster.payloads[1].iobObservedAt)
    }

    @Test
    fun unchangedIobIsRateLimitedToGdhAcceptanceWindow() {
        val broadcaster = FakeBroadcaster()
        var currentNow = now
        val dispatcher = dispatcher(broadcaster, currentNow = { currentNow })
        dispatcher.onEvent(cgm())

        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.25f, currentNow))
        currentNow = now.plusSeconds(20)
        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.25f, currentNow))
        currentNow = now.plusSeconds(31)
        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.25f, currentNow))

        assertEquals(3, broadcaster.payloads.size)
        assertNull(broadcaster.payloads[0].iobUnits)
        assertEquals(1.25f, broadcaster.payloads[1].iobUnits)
        assertEquals(1.25f, broadcaster.payloads[2].iobUnits)
    }

    @Test
    fun iobExportCanBeDisabledWithoutSuppressingCgm() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(
            broadcaster,
            config = { GdhSyncConfig(enabled = true, sendPumpIob = false) },
        )

        dispatcher.onEvent(GdhDispatchEvent.PumpIob(1.25f, now))
        dispatcher.onEvent(cgm())

        assertEquals(1, broadcaster.payloads.size)
        assertNull(broadcaster.payloads.single().iobUnits)
    }

    @Test
    fun stoppedSensorSessionRemovesSensorStartMetadata() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onEvent(GdhDispatchEvent.SensorSession(true, now.minusSeconds(500)))
        dispatcher.onEvent(GdhDispatchEvent.SensorSession(false, null))
        dispatcher.onEvent(cgm())

        assertNull(broadcaster.payloads.single().sensorStartedAt)
    }

    @Test
    fun currentEgvNormalizesLowHighAndRejectsUnavailable() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1000, 0, 0, 0))
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1001, 55, 2, -1))
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1002, 350, 3, 1))

        assertEquals(listOf(39, 401), broadcaster.payloads.map { it.mgdl })
    }
}
