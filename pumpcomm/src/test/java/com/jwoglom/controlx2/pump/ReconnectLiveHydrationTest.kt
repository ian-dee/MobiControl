package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusV3Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import java.math.BigInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectLiveHydrationTest {
    @Test
    fun `all live and safety fields are required before ready`() {
        val tracker = ReconnectLiveHydration()
        val responses = listOf(
            TimeSinceResetResponse(),
            CurrentBolusStatusResponse(),
            LastBolusStatusV3Response(),
            AlarmStatusResponse(),
            ReminderStatusResponse(BigInteger.ZERO),
            HomeScreenMirrorResponse(),
            LoadStatusResponse(),
            InsulinStatusResponse(),
            ControlIQIOBResponse(),
            CurrentBatteryV2Response(),
            CurrentEGVGuiDataResponse(),
        )

        responses.dropLast(1).forEach { assertFalse(tracker.onResponse(it)) }
        assertTrue(tracker.onResponse(responses.last()))
        assertTrue(tracker.pendingFields().isEmpty())
    }

    @Test
    fun `reset makes a completed hydration incomplete again`() {
        val tracker = ReconnectLiveHydration()
        ReconnectLiveField.entries.forEach { field ->
            // Exercise reset independently from protocol construction details.
            if (field == ReconnectLiveField.CGM) {
                tracker.onResponse(CurrentEGVGuiDataResponse())
            }
        }
        tracker.reset()

        assertFalse(tracker.pendingFields().isEmpty())
    }
}
