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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

/** Full dispatcher → sender → Android-broadcast-boundary contract tests. */
class XdripBroadcastIntegrationTest {
    private data class CapturedBroadcast(val action: String, val extraKey: String, val payload: String)

    private val broadcasts = mutableListOf<CapturedBroadcast>()
    private val now = Instant.parse("2026-08-24T17:00:00Z")
    private val pumpEventTime = now.minusSeconds(300)
    private lateinit var dispatcher: XdripMessageDispatcher
    private var config = XdripSyncConfig(enabled = true)

    @Before
    fun setUp() {
        broadcasts.clear()
        config = XdripSyncConfig(enabled = true)
        val sender = XdripBroadcastSender(
            sendBroadcastFn = { action, extra, payload ->
                broadcasts += CapturedBroadcast(action, extra, payload)
                true
            },
            nowMillisFn = { now.toEpochMilli() },
        )
        dispatcher = XdripMessageDispatcher(
            broadcaster = sender,
            configProvider = { config },
            nowProvider = { now },
            pumpSidProvider = { 68 },
            uploaderBatteryProvider = { 91 },
            pumpTimestampToInstant = { pumpEventTime },
        )
    }

    @Test
    fun sgvPayloadMatchesReceiverSchemaAndPumpTime() {
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 145, 1, 2))

        val broadcast = broadcasts.single { it.action == XdripBroadcastSender.ACTION_NEW_SGV }
        assertEquals("sgvs", broadcast.extraKey)
        val json = JSONArray(broadcast.payload).getJSONObject(0)
        assertEquals(145, json.getInt("mgdl"))
        assertEquals(pumpEventTime.toEpochMilli(), json.getLong("mills"))
        assertTrue(json.getString("direction") in XDRIP_DIRECTIONS)
    }

    @Test
    fun pumpStatusMatchesReceiverSchemaWithoutFalseBasalTreatment() {
        config = config.copy(sendCgmSgv = false, sendStatusLine = false)
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(ControlIQIOBResponse(1234, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(InsulinStatusResponse(80, 0, 20))
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val status = JSONObject(
            broadcasts.last { it.action == XdripBroadcastSender.ACTION_NEW_DEVICE_STATUS }.payload,
        )
        val pump = status.getJSONObject("pump")
        assertEquals(91, status.getInt("uploaderBattery"))
        assertEquals(55, pump.getJSONObject("battery").getInt("percent"))
        assertEquals(80, pump.getInt("reservoir"))
        assertEquals(
            InsulinUnit.from1000To1(1234),
            pump.getJSONObject("iob").getDouble("bolusiob"),
            0.0001,
        )
        assertEquals(0.9, pump.getDouble("basal"), 0.0001)
        assertFalse(broadcasts.any { it.action == XdripBroadcastSender.ACTION_NEW_TREATMENT })
    }

    @Test
    fun onlyTerminalHistoryCreatesOneDeliveredTreatment() {
        config = config.copy(
            sendCgmSgv = false,
            sendPumpDeviceStatus = false,
            sendStatusLine = false,
        )
        dispatcher.onReceiveMessage(InitiateBolusResponse(0, 77, 0))
        dispatcher.onReceiveMessage(CurrentBolusStatusResponse(0, 77, 1234, 9_000, 0, 0))
        dispatcher.onHistoryLogItem(terminalBolusItem())
        dispatcher.onHistoryLogItem(terminalBolusItem())

        val treatments = broadcasts.filter {
            it.action == XdripBroadcastSender.ACTION_NEW_TREATMENT
        }
        assertEquals(1, treatments.size)
        assertEquals("treatments", treatments.single().extraKey)
        val json = JSONArray(treatments.single().payload).getJSONObject(0)
        assertEquals(1.5, json.getDouble("insulin"), 0.0001)
        assertEquals(pumpEventTime.toEpochMilli(), json.getLong("mills"))
        assertFalse(broadcasts.any { it.action == "info.nightscout.client.NEW_FOOD" })
    }

    @Test
    fun globalEnableTakesEffectOnSameDispatcherInstance() {
        config = config.copy(enabled = false)
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1234, 145, 1, 2))
        assertTrue(broadcasts.isEmpty())

        config = config.copy(enabled = true)
        dispatcher.onReceiveMessage(CurrentEGVGuiDataResponse(1235, 146, 1, 2))
        assertEquals(1, broadcasts.count { it.action == XdripBroadcastSender.ACTION_NEW_SGV })
    }

    @Test
    fun statuslineKeepsBasalPatternWithoutBatteryPercentPattern() {
        config = config.copy(sendCgmSgv = false, sendPumpDeviceStatus = false, sendTreatments = false)
        dispatcher.onReceiveMessage(CurrentBatteryV2Response(0, 55, 0, 0, 0, 0, 0))
        dispatcher.onReceiveMessage(CurrentBasalStatusResponse(0, 900, 0))

        val statusline = broadcasts.last {
            it.action == XdripBroadcastSender.ACTION_EXTERNAL_STATUSLINE
        }.payload
        assertTrue(Regex("""([0-9.,]+U/h)""").containsMatchIn(statusline))
        assertFalse(Regex("""([0-9]+%)""").containsMatchIn(statusline))
    }

    private fun terminalBolusItem(): HistoryLogItem {
        val log = BolusDeliveryHistoryLog(
            1234,
            9001,
            77,
            3,
            setOf(BolusDeliveryHistoryLog.BolusType.FOOD1),
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            0,
            9_000,
            0,
            0,
            0,
            1_500,
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

    companion object {
        private val XDRIP_DIRECTIONS = setOf(
            "DoubleDown",
            "SingleDown",
            "FortyFiveDown",
            "Flat",
            "FortyFiveUp",
            "SingleUp",
            "DoubleUp",
        )
    }
}
