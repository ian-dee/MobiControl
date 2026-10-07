package com.jwoglom.controlx2.sync.xdrip.models

import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class XdripTreatmentPayload(
    val eventType: String,
    val createdAt: String,
    val mills: Long,
    val insulin: Double,
    val notes: String,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("eventType", eventType)
        put("created_at", createdAt)
        put("mills", mills)
        put("insulin", insulin)
        put("notes", notes)
    }

    fun toJsonArrayString(): String = JSONArray().put(toJsonObject()).toString()

    companion object {
        const val EXTRA_KEY = "treatments"

        /** Build a treatment only from pump-authored terminal delivery history. */
        fun fromBolusDeliveryHistory(
            log: BolusDeliveryHistoryLog,
            timestamp: Instant,
        ): XdripTreatmentPayload = XdripTreatmentPayload(
            eventType = "Bolus",
            createdAt = timestamp.toString(),
            mills = timestamp.toEpochMilli(),
            insulin = InsulinUnit.from1000To1(log.deliveredTotal.toLong()),
            notes = buildString {
                append("MobiControl pump-confirmed bolus")
                append(" bolusId=${log.bolusID}")
                append(" sequence=${log.sequenceNum}")
                append(" status=${log.bolusDeliveryStatusId}")
                log.bolusSource?.let { append(" source=${it.name}") }
            },
        )
    }
}
