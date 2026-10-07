package com.jwoglom.controlx2

import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNotificationBackgroundRefreshTest {
    @Test
    fun `existing background refresh includes status displays and pump notification bundle`() {
        val apiVersion = KnownApiVersion.API_V2_5.get()
        val requests = periodicPumpStatusRequests(apiVersion)
        val requestTypes = requests.map { it::class }

        assertTrue(requestTypes.contains(CurrentBatteryRequestBuilder.create(apiVersion)::class))
        assertTrue(requestTypes.contains(ControlIQIOBRequest::class))
        assertTrue(requestTypes.contains(InsulinStatusRequest::class))
        pumpNotificationStatusRequests(apiVersion).forEach { expected ->
            assertTrue(
                "Missing periodic pump notification request ${expected::class.simpleName}",
                requestTypes.contains(expected::class),
            )
        }
    }
}
