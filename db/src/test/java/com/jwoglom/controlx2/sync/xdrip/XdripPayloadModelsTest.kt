package com.jwoglom.controlx2.sync.xdrip

import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripDeviceStatusSnapshot
import com.jwoglom.controlx2.sync.xdrip.models.XdripSgvPayload
import com.jwoglom.controlx2.sync.xdrip.models.XdripTreatmentPayload
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XdripPayloadModelsTest {
    @Test
    fun sgvPayload_usesReadingTimestampAndDirection() {
        val readingAt = Instant.parse("2026-08-24T16:55:00Z")
        val payload = XdripSgvPayload.fromValue(123, 0f, readingAt)
        val obj = JSONArray(payload.toJsonArrayString()).getJSONObject(0)

        assertEquals("sgvs", XdripSgvPayload.EXTRA_KEY)
        assertEquals(123, obj.getInt("mgdl"))
        assertEquals(readingAt.toEpochMilli(), obj.getLong("mills"))
        assertEquals("Flat", obj.getString("direction"))
    }

    @Test
    fun directionFromTrendRate_mapsXdripNames() {
        assertEquals("DoubleDown", XdripSgvPayload.directionFromTrendRate(-4f))
        assertEquals("SingleDown", XdripSgvPayload.directionFromTrendRate(-2f))
        assertEquals("FortyFiveDown", XdripSgvPayload.directionFromTrendRate(-1f))
        assertEquals("Flat", XdripSgvPayload.directionFromTrendRate(0f))
        assertEquals("FortyFiveUp", XdripSgvPayload.directionFromTrendRate(1f))
        assertEquals("SingleUp", XdripSgvPayload.directionFromTrendRate(2f))
        assertEquals("DoubleUp", XdripSgvPayload.directionFromTrendRate(3f))
    }

    @Test
    fun deviceStatus_usesPhoneUploaderBatteryAndPumpBatterySeparately() {
        val snapshot = XdripDeviceStatusSnapshot()
        val receivedAt = Instant.parse("2026-08-24T17:00:00Z")
        snapshot.applyBatteryResponse(
            CurrentBatteryV2Response(0, 66, 0, 0, 0, 0, 0),
            receivedAt,
        )
        snapshot.applyIobResponse(ControlIQIOBResponse(1450, 0, 0, 0, 0), receivedAt)
        snapshot.applyReservoirResponse(InsulinStatusResponse(120, 0, 20), receivedAt)
        snapshot.applyBasalResponse(CurrentBasalStatusResponse(0, 850, 0), receivedAt)

        val json = JSONObject(
            snapshot.toPayload(
                createdAt = receivedAt,
                uploaderBatteryPercent = 91,
            ).toJsonString(),
        )

        assertEquals("devicestatus", XdripDeviceStatusPayload.EXTRA_KEY)
        assertEquals(91, json.getInt("uploaderBattery"))
        assertEquals(66, json.getJSONObject("pump").getJSONObject("battery").getInt("percent"))
        assertEquals(
            InsulinUnit.from1000To1(1450),
            json.getJSONObject("pump").getJSONObject("iob").getDouble("bolusiob"),
            0.0001,
        )
        assertEquals(120, json.getJSONObject("pump").getInt("reservoir"))
        assertEquals(0.85, json.getJSONObject("pump").getDouble("basal"), 0.0001)
        assertFalse(json.has("cgm"))
        assertFalse(json.has("controlx2ReceivedAt"))
    }

    @Test
    fun terminalTreatment_usesDeliveredNotRequestedAmount() {
        val deliveredAt = Instant.parse("2026-08-24T16:58:00Z")
        val log = BolusDeliveryHistoryLog(
            1234,
            9001,
            77,
            3,
            setOf(BolusDeliveryHistoryLog.BolusType.FOOD1),
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            0,
            5_000,
            0,
            0,
            0,
            1_250,
        )

        val obj = JSONArray(
            XdripTreatmentPayload.fromBolusDeliveryHistory(log, deliveredAt).toJsonArrayString(),
        ).getJSONObject(0)

        assertEquals("treatments", XdripTreatmentPayload.EXTRA_KEY)
        assertEquals("Bolus", obj.getString("eventType"))
        assertEquals(1.25, obj.getDouble("insulin"), 0.0001)
        assertEquals(deliveredAt.toEpochMilli(), obj.getLong("mills"))
        assertEquals(deliveredAt.toString(), obj.getString("created_at"))
        assertTrue(obj.getString("notes").contains("bolusId=77"))
    }
}
