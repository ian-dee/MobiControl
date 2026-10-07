package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import com.jwoglom.controlx2.AutomaticBolusNotifier
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * UI-only automatic-bolus sequence for Developer Preview. It writes synthetic
 * responses directly to DataStore and the local notifier; it has no message-bus
 * or pump-command dependency.
 */
class DeveloperAutomaticBolusSimulator(
    context: Context,
    private val dataStore: DataStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val notifier = AutomaticBolusNotifier(appContext)
    private var sequenceJob: Job? = null
    private var activeIdentity: AutomaticBolusIdentity? = null

    fun start() {
        if (!Prefs(appContext).developerPreviewEnabled()) {
            Timber.e("Blocked automatic bolus simulation outside Developer Preview")
            return
        }

        activeIdentity?.let { finish(it, deliveredUnits = 0.0) }
        sequenceJob?.cancel()

        val identity = AutomaticBolusIdentity(
            pumpSid = DEMO_PUMP_SID,
            pumpSessionId = DEMO_PUMP_SESSION_ID,
            bolusId = nextBolusId(),
        )
        activeIdentity = identity
        dataStore.automaticBolusCancelResult.value = null
        dataStore.pumpSid.value = identity.pumpSid
        dataStore.timeSinceResetResponse.value = TimeSinceResetResponse(
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            DEMO_SECONDS_SINCE_RESET,
        )

        sequenceJob = scope.launch {
            publish(identity, CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING)
            delay(PREPARING_DURATION_MS)
            if (activeIdentity == identity) {
                publish(identity, CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING)
            }
            delay(DELIVERING_DURATION_MS)
            if (activeIdentity == identity) finish(identity, deliveredUnits = DEMO_REQUESTED_UNITS)
        }
    }

    fun cancel() {
        if (!Prefs(appContext).developerPreviewEnabled()) {
            Timber.e("Blocked automatic bolus simulation cancellation outside Developer Preview")
            return
        }
        val identity = activeIdentity ?: return
        sequenceJob?.cancel()
        dataStore.automaticBolusCancelResult.value = AutomaticBolusCancelResult(
            identity,
            AutomaticBolusCancelOutcome.CONFIRMED,
        )
        sequenceJob = scope.launch {
            delay(CANCELLATION_CONFIRM_DURATION_MS)
            if (activeIdentity == identity) finish(identity, deliveredUnits = DEMO_CANCELLED_UNITS)
        }
    }

    private fun publish(
        identity: AutomaticBolusIdentity,
        status: CurrentBolusStatusResponse.CurrentBolusStatus,
    ) {
        val response = CurrentBolusStatusResponse(
            status.id,
            identity.bolusId,
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            DEMO_REQUESTED_MILLIUNITS,
            BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS.id(),
            CORRECTION_BOLUS_TYPE,
        )
        dataStore.bolusCurrentResponse.value = response
        notifier.onStatus(
            response = response,
            pumpSid = identity.pumpSid,
            pumpSession = dataStore.timeSinceResetResponse.value,
            cancellationEnabled = false,
        )
    }

    private fun finish(identity: AutomaticBolusIdentity, deliveredUnits: Double) {
        if (activeIdentity != identity) return
        activeIdentity = null
        val terminal = CurrentBolusStatusResponse(
            CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID.id,
            0,
            0L,
            0L,
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS.id(),
            0,
        )
        dataStore.bolusCurrentResponse.value = terminal
        dataStore.lastBolusSource.value =
            BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
        dataStore.lastBolusStatus.value = String.format(
            java.util.Locale.US,
            "%.2fu at %s (demo)",
            deliveredUnits,
            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
        )
        notifier.onStatus(
            response = terminal,
            pumpSid = identity.pumpSid,
            pumpSession = dataStore.timeSinceResetResponse.value,
            cancellationEnabled = false,
        )
    }

    companion object {
        private const val DEMO_PUMP_SID = 900001
        private const val DEMO_PUMP_SESSION_ID = 500_000_000L
        private const val DEMO_SECONDS_SINCE_RESET = 10_000L
        private const val DEMO_REQUESTED_UNITS = 1.2
        private const val DEMO_REQUESTED_MILLIUNITS = 1_200L
        private const val DEMO_CANCELLED_UNITS = 0.35
        private const val CORRECTION_BOLUS_TYPE = 2
        private const val PREPARING_DURATION_MS = 1_500L
        // Long enough to inspect the Dashboard, notification shade, and Cancel action.
        private const val DELIVERING_DURATION_MS = 30_000L
        private const val CANCELLATION_CONFIRM_DURATION_MS = 1_000L
        private val nextId = AtomicInteger(20_000)

        private fun nextBolusId(): Int = nextId.updateAndGet { if (it >= 60_000) 20_000 else it + 1 }
    }
}
