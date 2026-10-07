package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.CgmHighLowAlertRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CgmOutOfRangeAlertRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CgmRiseFallAlertRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMGlucoseAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMOORAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMRateAlertSettingsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmAlertSettingsModelsTest {
    private val defaults = CgmAlertSettingsSnapshot(
        highEnabled = true,
        highThreshold = 200,
        highRepeatMinutes = 0,
        lowEnabled = true,
        lowThreshold = 80,
        lowRepeatMinutes = 0,
        riseEnabled = false,
        riseRate = 3,
        fallEnabled = false,
        fallRate = 3,
        outOfRangeEnabled = true,
        outOfRangeDelayMinutes = 20,
    )

    @Test
    fun responsePayloadsBecomeOnePumpSnapshot() {
        val snapshot = cgmAlertSettingsSnapshot(
            CGMGlucoseAlertSettingsResponse(200, 1, 60, 0, 80, 1, 30, 0),
            CGMRateAlertSettingsResponse(3, 1, 0, 2, 0, 0),
            CGMOORAlertSettingsResponse(20, 1, 0),
        )

        assertEquals(200, snapshot?.highThreshold)
        assertEquals(60, snapshot?.highRepeatMinutes)
        assertEquals(80, snapshot?.lowThreshold)
        assertEquals(30, snapshot?.lowRepeatMinutes)
        assertEquals(true, snapshot?.riseEnabled)
        assertEquals(3, snapshot?.riseRate)
        assertEquals(false, snapshot?.fallEnabled)
        assertEquals(2, snapshot?.fallRate)
        assertEquals(true, snapshot?.outOfRangeEnabled)
        assertEquals(20, snapshot?.outOfRangeDelayMinutes)
    }

    @Test
    fun snapshotRequiresEveryPumpSettingsResponse() {
        assertNull(cgmAlertSettingsSnapshot(null, null, null))
    }

    @Test
    fun unchangedSettingsProduceNoPumpWrites() {
        assertTrue(buildCgmAlertWritePlan(defaults, defaults).isEmpty())
    }

    @Test
    fun highAlertWritesOnlyChangedFields() {
        val requested = defaults.copy(highThreshold = 180, highRepeatMinutes = 60)
        val plan = buildCgmAlertWritePlan(defaults, requested)

        assertEquals(1, plan.size)
        val request = plan.single().message as CgmHighLowAlertRequest
        assertEquals(CgmHighLowAlertRequest.ALERT_TYPE_HIGH, request.alertType)
        assertEquals(180, request.threshold)
        assertEquals(60, request.repeatDurationMinutes)
        assertTrue(request.isEnableAlert)
        assertEquals(0b110, request.bitmask)
    }

    @Test
    fun eachAlertTypeGetsAnIndependentSignedControlRequest() {
        val requested = defaults.copy(
            highEnabled = false,
            lowThreshold = 75,
            riseEnabled = true,
            riseRate = 2,
            fallEnabled = true,
            outOfRangeEnabled = false,
            outOfRangeDelayMinutes = 45,
        )
        val plan = buildCgmAlertWritePlan(defaults, requested)

        assertEquals(5, plan.size)
        val high = plan[0].message as CgmHighLowAlertRequest
        val low = plan[1].message as CgmHighLowAlertRequest
        val rise = plan[2].message as CgmRiseFallAlertRequest
        val fall = plan[3].message as CgmRiseFallAlertRequest
        val outOfRange = plan[4].message as CgmOutOfRangeAlertRequest

        assertEquals(0b001, high.bitmask)
        assertEquals(0b010, low.bitmask)
        assertEquals(0b011, rise.bitmask)
        assertEquals(0b001, fall.bitmask)
        assertEquals(0b011, outOfRange.bitmask)
    }

    @Test
    fun validationMatchesDocumentedMobiRanges() {
        assertNull(defaults.validationError())
        assertTrue(defaults.copy(highThreshold = 119).validationError()!!.contains("High"))
        assertTrue(defaults.copy(lowThreshold = 101).validationError()!!.contains("Low"))
        assertTrue(defaults.copy(riseRate = 4).validationError()!!.contains("Rise"))
        assertTrue(
            defaults.copy(outOfRangeDelayMinutes = 201)
                .validationError()!!
                .contains("Out-of-range")
        )
    }
}
