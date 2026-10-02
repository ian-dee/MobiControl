package com.jwoglom.controlx2.sync.gdh

import java.time.Instant

/**
 * One live GlucoDataHandler update using GDH's public `glucodata.Minute` contract.
 *
 * Glucose time remains pump-authored. IOB time is the time MobiControl observed the pump's
 * displayed IOB, so GDH can age the two values independently.
 */
data class GdhPayload(
    val mgdl: Int,
    val readingAt: Instant,
    val trendRateMgdlPerMinute: Float,
    val sensorId: String,
    val sensorStartedAt: Instant? = null,
    val iobUnits: Float? = null,
    val iobObservedAt: Instant? = null,
    val sourcePackage: String,
) {
    init {
        require(mgdl in GDH_MIN_MGDL..GDH_MAX_MGDL) { "GDH glucose must be displayable" }
        require(sensorId.isNotBlank()) { "GDH sensor identity must not be blank" }
        require(sourcePackage.isNotBlank()) { "GDH source package must not be blank" }
        require((iobUnits == null) == (iobObservedAt == null)) {
            "GDH IOB value and observation time must be supplied together"
        }
        require(iobUnits == null || (iobUnits.isFinite() && iobUnits >= 0f)) {
            "GDH IOB must be a finite non-negative value"
        }
    }

    companion object {
        const val GDH_MIN_MGDL = 36
        const val GDH_MAX_MGDL = 600
    }
}
