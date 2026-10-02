package com.jwoglom.controlx2.sync.xdrip.models

import com.jwoglom.controlx2.shared.util.CgmTrendDirection
import com.jwoglom.controlx2.shared.util.cgmTrendDirectionMgdlPerMinute
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class XdripSgvPayload(
    val mgdl: Int,
    val mills: Long,
    val direction: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("mgdl", mgdl)
            put("mills", mills)
            put("direction", direction)
        }
    }

    fun toJsonArrayString(): String = JSONArray().put(toJsonObject()).toString()

    companion object {
        const val EXTRA_KEY = "sgvs"

        /**
         * Map a numeric trend rate (mg/dL per minute) to an xDrip-compatible direction string.
         * Thresholds follow the standard Dexcom CGM trend arrow ranges.
         */
        fun directionFromTrendRate(trendRateMgdlPerMinute: Float): String {
            return when (cgmTrendDirectionMgdlPerMinute(trendRateMgdlPerMinute)) {
                CgmTrendDirection.DOUBLE_DOWN -> "DoubleDown"
                CgmTrendDirection.DOWN -> "SingleDown"
                CgmTrendDirection.DOWN_RIGHT -> "FortyFiveDown"
                CgmTrendDirection.FLAT -> "Flat"
                CgmTrendDirection.UP_RIGHT -> "FortyFiveUp"
                CgmTrendDirection.UP -> "SingleUp"
                CgmTrendDirection.DOUBLE_UP -> "DoubleUp"
            }
        }

        fun fromValue(
            mgdl: Int,
            trendRateMgdlPerMinute: Float,
            readingAt: Instant,
        ): XdripSgvPayload {
            return XdripSgvPayload(
                mgdl = mgdl,
                mills = readingAt.toEpochMilli(),
                direction = directionFromTrendRate(trendRateMgdlPerMinute)
            )
        }
    }
}
