package com.jwoglom.controlx2.sync.xdrip

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class XdripMessageDispatcherTest {
    private data class SentValue<T>(val payload: T, val minimumIntervalSeconds: Int?)

    private class FakeBroadcaster : XdripBroadcaster {
        val sgvPayloads = mutableListOf<SentValue<String>>()
        val deviceStatusPayloads = mutableListOf<SentValue<String>>()
        val treatmentPayloads = mutableListOf<SentValue<String>>()
        val statuslinePayloads = mutableListOf<SentValue<String>>()

        override fun sendSgv(sgvsJsonArrayString: String, minimumIntervalSeconds: Int?): Boolean {
            sgvPayloads += SentValue(sgvsJsonArrayString, minimumIntervalSeconds)
            return true
        }

        override fun sendDeviceStatus(
            deviceStatusJsonString: String,
            minimumIntervalSeconds: Int?,
        ): Boolean {
            deviceStatusPayloads += SentValue(deviceStatusJsonString, minimumIntervalSeconds)
            return true
        }

        override fun sendTreatments(
            treatmentsJsonString: String,
            minimumIntervalSeconds: Int?,
        ): Boolean {
            treatmentPayloads += SentValue(treatmentsJsonString, minimumIntervalSeconds)
            return true
        }

        override fun sendExternalStatusline(
            statusline: String,
            minimumIntervalSeconds: Int?,
        ): Boolean {
            statuslinePayloads += SentValue(statusline, minimumIntervalSeconds)
            return true
        }
    }

    private val now = Instant.parse("2026-08-24T17:00:00Z")
    private val readingAt = now.minusSeconds(300)

    private fun dispatcher(
        broadcaster: FakeBroadcaster,
        configProvider: () -> XdripSyncConfig = { XdripSyncConfig(enabled = true) },
        ledger: XdripExportLedger = InMemoryXdripExportLedger(),
    ) = XdripMessageDispatcher(
        broadcaster = broadcaster,
        configProvider = configProvider,
        nowProvider = { now },
        pumpSidProvider = { 68 },
        uploaderBatteryProvider = { 87 },
        pumpTimestampToInstant = { readingAt },
        ledger = ledger,
    )

    @Test
    fun currentSgv_usesPumpTimestampAndValidDirection() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster, configProvider = {
            XdripSyncConfig(
                enabled = true,
                sendPumpDeviceStatus = false,
                sendTreatments = false,
                sendStatusLine = false,
                cgmSgvMinimumIntervalSeconds = 42,
            )
        })

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 145, 1, 6))

        val sent = broadcaster.sgvPayloads.single()
        val json = JSONArray(sent.payload).getJSONObject(0)
        assertEquals(42, sent.minimumIntervalSeconds)
        assertEquals(145, json.getInt("mgdl"))
        assertEquals(readingAt.toEpochMilli(), json.getLong("mills"))
        assertEquals("Flat", json.getString("direction"))
    }

    @Test
    fun currentSgv_filtersInvalidAndUnavailable_andNormalizesLowHigh() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster, configProvider = {
            XdripSyncConfig(
                enabled = true,
                sendPumpDeviceStatus = false,
                sendTreatments = false,
                sendStatusLine = false,
            )
        })

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1000, 0, 0, 0))
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1001, 0, 4, 0))
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1002, 55, 2, -1))
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1003, 350, 3, 1))

        assertEquals(2, broadcaster.sgvPayloads.size)
        assertEquals(39, JSONArray(broadcaster.sgvPayloads[0].payload).getJSONObject(0).getInt("mgdl"))
        assertEquals(401, JSONArray(broadcaster.sgvPayloads[1].payload).getJSONObject(0).getInt("mgdl"))
    }

    @Test
    fun pumpStatus_usesPhoneUploaderBattery_andCurrentBasalIsNotTreatment() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster, configProvider = {
            XdripSyncConfig(enabled = true, sendCgmSgv = false, sendStatusLine = false)
        })

        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(ControlIQIOBResponse(1234, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(InsulinStatusResponse(80, 0, 20))
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val status = JSONObject(broadcaster.deviceStatusPayloads.last().payload)
        assertEquals(87, status.getInt("uploaderBattery"))
        assertEquals(55, status.getJSONObject("pump").getJSONObject("battery").getInt("percent"))
        assertEquals(
            InsulinUnit.from1000To1(1234),
            status.getJSONObject("pump").getJSONObject("iob").getDouble("bolusiob"),
            0.0001,
        )
        assertEquals(80, status.getJSONObject("pump").getInt("reservoir"))
        assertEquals(0.9, status.getJSONObject("pump").getDouble("basal"), 0.0001)
        assertTrue(broadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun initiationAndCurrentBolusStatus_neverProduceTreatment() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 77, 0))
        dispatcher.onReceiveMessage(CurrentBolusStatusResponse(0, 77, 1234, 2300, 0, 0))

        assertTrue(broadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun terminalBolusHistory_exportsDeliveredAmountOnce_withoutThrottle() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)
        val item = terminalBolusItem(deliveredMilliunits = 1_500)

        dispatcher.onHistoryLogItem(item)
        dispatcher.onHistoryLogItem(item)

        val sent = broadcaster.treatmentPayloads.single()
        val json = JSONArray(sent.payload).getJSONObject(0)
        assertEquals(null, sent.minimumIntervalSeconds)
        assertEquals("Bolus", json.getString("eventType"))
        assertEquals(1.5, json.getDouble("insulin"), 0.0001)
        assertEquals(readingAt.toEpochMilli(), json.getLong("mills"))
        assertTrue(json.getString("notes").contains("bolusId=77"))
        assertTrue(json.getString("notes").contains("sequence=9001"))
    }

    @Test
    fun zeroDeliveredBolusHistory_isNotTreatment() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster)

        dispatcher.onHistoryLogItem(terminalBolusItem(deliveredMilliunits = 0))

        assertTrue(broadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun persistedLedger_suppressesReplayAfterDispatcherRecreation() {
        var storedLedger: String? = null
        val firstBroadcaster = FakeBroadcaster()
        val firstLedger = PersistedXdripExportLedger(
            readValue = { storedLedger },
            writeValue = { storedLedger = it; true },
        )
        dispatcher(firstBroadcaster, ledger = firstLedger)
            .onHistoryLogItem(terminalBolusItem(deliveredMilliunits = 1_250))

        val recreatedBroadcaster = FakeBroadcaster()
        val recreatedLedger = PersistedXdripExportLedger(
            readValue = { storedLedger },
            writeValue = { storedLedger = it; true },
        )
        dispatcher(recreatedBroadcaster, ledger = recreatedLedger)
            .onHistoryLogItem(terminalBolusItem(deliveredMilliunits = 1_250))

        assertEquals(1, firstBroadcaster.treatmentPayloads.size)
        assertTrue(recreatedBroadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun reconnectHistory_backfillsRecentG7ReadingOnce() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(broadcaster, configProvider = {
            XdripSyncConfig(
                enabled = true,
                sendPumpDeviceStatus = false,
                sendTreatments = false,
                sendStatusLine = false,
            )
        })
        val log = DexcomG7CGMHistoryLog(
            1234L,
            9002L,
            0,
            1,
            1,
            32,
            -50,
            155,
            1234,
            0,
            5,
        )
        val item = HistoryLogItem(
            seqId = log.sequenceNum,
            pumpSid = 68,
            typeId = log.typeId(),
            cargo = log.cargo,
            pumpTimeSec = log.pumpTimeSec,
            pumpTime = LocalDateTime.of(2026, 8, 24, 9, 55),
        )

        dispatcher.onHistoryLogItem(item)
        dispatcher.onHistoryLogItem(item)

        val json = JSONArray(broadcaster.sgvPayloads.single().payload).getJSONObject(0)
        assertEquals(155, json.getInt("mgdl"))
        assertEquals(readingAt.toEpochMilli(), json.getLong("mills"))
        assertEquals("Flat", json.getString("direction"))
    }

    @Test
    fun dispatcherReadsChangedConfigWithoutRestart() {
        val broadcaster = FakeBroadcaster()
        var config = XdripSyncConfig(enabled = false)
        val dispatcher = dispatcher(broadcaster, configProvider = { config })

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 120, 1, 0))
        assertTrue(broadcaster.sgvPayloads.isEmpty())

        config = config.copy(enabled = true)
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1235, 121, 1, 1))
        assertEquals(1, broadcaster.sgvPayloads.size)
    }

    @Test
    fun eventsOlderThan24Hours_areNotBackfilled() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = XdripMessageDispatcher(
            broadcaster = broadcaster,
            configProvider = { XdripSyncConfig(enabled = true) },
            nowProvider = { now },
            pumpTimestampToInstant = { now.minusSeconds(24 * 60 * 60 + 1L) },
        )

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 120, 1, 0))
        dispatcher.onHistoryLogItem(terminalBolusItem(deliveredMilliunits = 1_000))

        assertTrue(broadcaster.sgvPayloads.isEmpty())
        assertTrue(broadcaster.treatmentPayloads.isEmpty())
    }

    @Test
    fun disabledConfigSkipsAllBroadcasts() {
        val broadcaster = FakeBroadcaster()
        val dispatcher = dispatcher(
            broadcaster,
            configProvider = { XdripSyncConfig(enabled = false) },
        )

        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 120, 1, 0))
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        dispatcher.onHistoryLogItem(terminalBolusItem(deliveredMilliunits = 1_000))

        assertFalse(
            broadcaster.sgvPayloads.isNotEmpty() ||
                broadcaster.deviceStatusPayloads.isNotEmpty() ||
                broadcaster.statuslinePayloads.isNotEmpty() ||
                broadcaster.treatmentPayloads.isNotEmpty(),
        )
    }

    private fun terminalBolusItem(deliveredMilliunits: Int): HistoryLogItem {
        val log = BolusDeliveryHistoryLog(
            1234,
            9001,
            77,
            3,
            setOf(BolusDeliveryHistoryLog.BolusType.FOOD1),
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            0,
            2_000,
            0,
            0,
            0,
            deliveredMilliunits,
        )
        return HistoryLogItem(
            seqId = log.sequenceNum,
            pumpSid = 68,
            typeId = log.typeId(),
            cargo = log.cargo,
            pumpTimeSec = log.pumpTimeSec,
            pumpTime = LocalDateTime.of(2026, 8, 24, 9, 55),
        )
    }
}
