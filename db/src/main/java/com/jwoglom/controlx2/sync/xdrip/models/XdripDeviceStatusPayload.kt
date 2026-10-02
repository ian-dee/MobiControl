package com.jwoglom.controlx2.sync.xdrip.models

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import org.json.JSONObject
import java.time.Instant

data class XdripDeviceStatusPayload(
    val createdAt: String,
    val uploaderBattery: Int?,
    val pump: Pump
) {
    data class Pump(
        val batteryPercent: Int?,
        val bolusIobUnits: Double?,
        val reservoirUnits: Int?,
        val basalUnitsPerHour: Double?
    )

    fun toJsonString(): String {
        return JSONObject().apply {
            put("created_at", createdAt)
            uploaderBattery?.let { put("uploaderBattery", it) }
            put("pump", JSONObject().apply {
                put("battery", JSONObject().apply { put("percent", pump.batteryPercent) })
                put("iob", JSONObject().apply { put("bolusiob", pump.bolusIobUnits) })
                put("reservoir", pump.reservoirUnits)
                put("basal", pump.basalUnitsPerHour)
            })
        }.toString()
    }

    companion object {
        const val EXTRA_KEY = "devicestatus"
    }
}

data class XdripTimedValue<T>(
    var value: T,
    var receivedAt: Instant
)

internal data class XdripDeviceStatusSnapshot(
    var batteryPercent: XdripTimedValue<Int>? = null,
    var iobUnits: XdripTimedValue<Double>? = null,
    var cartridgeUnits: XdripTimedValue<Int>? = null,
    var basalUnitsPerHour: XdripTimedValue<Double>? = null
) {
    fun applyBatteryResponse(response: CurrentBatteryAbstractResponse, receivedAt: Instant) {
        batteryPercent = XdripTimedValue(response.batteryPercent, receivedAt)
    }

    fun applyIobResponse(response: ControlIQIOBResponse, receivedAt: Instant) {
        iobUnits = XdripTimedValue(InsulinUnit.from1000To1(response.pumpDisplayedIOB), receivedAt)
    }

    fun applyReservoirResponse(response: InsulinStatusResponse, receivedAt: Instant) {
        cartridgeUnits = XdripTimedValue(response.currentInsulinAmount, receivedAt)
    }

    fun applyBasalResponse(response: CurrentBasalStatusResponse, receivedAt: Instant) {
        basalUnitsPerHour = XdripTimedValue(InsulinUnit.from1000To1(response.currentBasalRate), receivedAt)
    }

    fun toPayload(createdAt: Instant, uploaderBatteryPercent: Int?): XdripDeviceStatusPayload {
        return XdripDeviceStatusPayload(
            createdAt = createdAt.toString(),
            uploaderBattery = uploaderBatteryPercent,
            pump = XdripDeviceStatusPayload.Pump(
                batteryPercent = batteryPercent?.value,
                bolusIobUnits = iobUnits?.value,
                reservoirUnits = cartridgeUnits?.value,
                basalUnitsPerHour = basalUnitsPerHour?.value
            )
        )
    }
}
