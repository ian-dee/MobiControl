package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.QuickBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/** Pump-command-free Quick Bolus fixture for visual and lifecycle testing in Developer Preview. */
class DeveloperQuickBolusSimulator(
    context: Context,
    private val dataStore: DataStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var job: Job? = null
    private var activeIdentity: QuickBolusIdentity? = null

    fun start(requestedMilliunits: Long = 2_000L) {
        if (!Prefs(appContext).developerPreviewEnabled()) {
            Timber.e("Blocked Quick Bolus simulation outside Developer Preview")
            return
        }
        job?.cancel()
        val identity = QuickBolusIdentity(DEMO_PUMP_SID, DEMO_PUMP_SESSION_ID, nextId())
        val now = System.currentTimeMillis()
        activeIdentity = identity
        dataStore.quickBolusCancelResult.value = null
        dataStore.pumpSid.value = identity.pumpSid
        dataStore.timeSinceResetResponse.value = TimeSinceResetResponse(
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            DEMO_SECONDS_SINCE_RESET,
        )
        dataStore.quickBolusOperation.value = QuickBolusOperation(
            identity = identity,
            requestedMilliunits = requestedMilliunits,
            phase = QuickBolusOperationPhase.ACTIVE,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        publish(identity, requestedMilliunits, CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING)
        job = scope.launch {
            delay(1_500L)
            if (activeIdentity == identity) {
                publish(
                    identity,
                    requestedMilliunits,
                    CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
                )
            }
            delay(30_000L)
            finish(identity, requestedMilliunits)
        }
    }

    fun cancel(identity: QuickBolusIdentity) {
        if (!Prefs(appContext).developerPreviewEnabled() || activeIdentity != identity) return
        val current = dataStore.quickBolusOperation.value ?: return
        if (current.identity != identity || !current.canRequestCancellation) return
        job?.cancel()
        update(current.copy(
            phase = QuickBolusOperationPhase.CANCELLATION_SENDING,
            cancellationRequested = true,
        ))
        job = scope.launch {
            delay(800L)
            if (activeIdentity != identity) return@launch
            dataStore.quickBolusCancelResult.value = QuickBolusCancelResult(
                identity,
                QuickBolusCancelOutcome.CONFIRMED,
            )
            update(checkNotNull(dataStore.quickBolusOperation.value).copy(
                phase = QuickBolusOperationPhase.CANCELLATION_CONFIRMED,
                cancellationAccepted = true,
            ))
            delay(2_000L)
            finish(identity, deliveredMilliunits = 380L)
        }
    }

    fun acknowledge(identity: QuickBolusIdentity) {
        val current = dataStore.quickBolusOperation.value ?: return
        if (current.identity == identity && current.phase == QuickBolusOperationPhase.TERMINAL) {
            update(current.copy(terminalResultAcknowledged = true))
        }
    }

    private fun publish(
        identity: QuickBolusIdentity,
        requestedMilliunits: Long,
        status: CurrentBolusStatusResponse.CurrentBolusStatus,
    ) {
        dataStore.bolusCurrentResponse.value = CurrentBolusStatusResponse(
            status.id,
            identity.bolusId,
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            requestedMilliunits,
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS.id(),
            FOOD_BOLUS_TYPE,
        )
    }

    private fun finish(identity: QuickBolusIdentity, deliveredMilliunits: Long) {
        if (activeIdentity != identity) return
        val current = dataStore.quickBolusOperation.value ?: return
        activeIdentity = null
        update(current.copy(
            phase = QuickBolusOperationPhase.TERMINAL,
            deliveredMilliunits = deliveredMilliunits,
            terminalStatusId = TERMINAL_STATUS_ID,
        ))
        dataStore.bolusCurrentResponse.value = CurrentBolusStatusResponse(
            CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID.id,
            0,
            0L,
            0L,
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS.id(),
            FOOD_BOLUS_TYPE,
        )
        dataStore.lastBolusSource.value = BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS
        dataStore.lastBolusStatus.value = String.format(
            Locale.US,
            "%.2fu at %s (Quick Bolus demo)",
            deliveredMilliunits / 1000.0,
            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
        )
    }

    private fun update(next: QuickBolusOperation) {
        dataStore.quickBolusOperation.value = next.copy(
            updatedAtEpochMillis = System.currentTimeMillis().coerceAtLeast(next.updatedAtEpochMillis),
        )
    }

    companion object {
        private const val DEMO_PUMP_SID = 900002
        private const val DEMO_PUMP_SESSION_ID = 600_000_000L
        private const val DEMO_SECONDS_SINCE_RESET = 20_000L
        private const val FOOD_BOLUS_TYPE = 1
        private const val TERMINAL_STATUS_ID = 0
        private val ids = AtomicInteger(40_000)
        private fun nextId(): Int = ids.incrementAndGet()
    }
}
