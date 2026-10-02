package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusV3Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse

internal enum class ReconnectLiveField {
    PUMP_TIME,
    CURRENT_BOLUS,
    LAST_BOLUS,
    ALARMS,
    REMINDERS,
    DELIVERY,
    LOAD,
    INSULIN,
    IOB,
    BATTERY,
    CGM,
}

/** Pure response tracker; it never changes command priority or transport behavior. */
internal class ReconnectLiveHydration {
    private var pending = ReconnectLiveField.entries.toMutableSet()

    fun reset() {
        pending = ReconnectLiveField.entries.toMutableSet()
    }

    fun onResponse(message: Message): Boolean {
        when (message) {
            is TimeSinceResetResponse -> pending.remove(ReconnectLiveField.PUMP_TIME)
            is CurrentBolusStatusResponse -> pending.remove(ReconnectLiveField.CURRENT_BOLUS)
            is LastBolusStatusAbstractResponse,
            is LastBolusStatusV3Response,
            -> pending.remove(ReconnectLiveField.LAST_BOLUS)
            is AlarmStatusResponse -> pending.remove(ReconnectLiveField.ALARMS)
            is ReminderStatusResponse -> pending.remove(ReconnectLiveField.REMINDERS)
            is HomeScreenMirrorResponse -> pending.remove(ReconnectLiveField.DELIVERY)
            is LoadStatusResponse -> pending.remove(ReconnectLiveField.LOAD)
            is InsulinStatusResponse -> pending.remove(ReconnectLiveField.INSULIN)
            is ControlIQIOBResponse -> pending.remove(ReconnectLiveField.IOB)
            is CurrentBatteryAbstractResponse -> pending.remove(ReconnectLiveField.BATTERY)
            is CurrentEGVGuiDataResponse -> pending.remove(ReconnectLiveField.CGM)
        }
        return pending.isEmpty()
    }

    fun pendingFields(): Set<ReconnectLiveField> = pending.toSet()
}
