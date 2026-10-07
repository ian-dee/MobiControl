package com.jwoglom.controlx2.presentation.simulation

import android.content.Context
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Pump-disabled manual-bolus lifecycle for Developer Preview. Synthetic states
 * are written directly to DataStore and never enter the message bus or BLE path.
 */
class DeveloperManualBolusSimulator(
    context: Context,
    private val dataStore: DataStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private var sequenceJob: Job? = null
    private var activeIdentity: ManualBolusIdentity? = null
    private var startingIobUnits: Double = 0.0

    fun start(requestedUnits: Double) {
        if (!Prefs(appContext).developerPreviewEnabled()) {
            Timber.e("Blocked manual bolus simulation outside Developer Preview")
            return
        }
        if (!requestedUnits.isFinite() || requestedUnits < MINIMUM_BOLUS_UNITS) {
            Timber.e("Blocked invalid manual bolus simulation amount: $requestedUnits")
            return
        }

        sequenceJob?.cancel()
        val requestedMilliunits = requestedUnits.toExactMilliunits()
        val identity = ManualBolusIdentity(
            pumpSid = DEMO_PUMP_SID,
            pumpSessionId = DEMO_PUMP_SESSION_ID,
            bolusId = nextBolusId(),
        )
        val now = System.currentTimeMillis()
        activeIdentity = identity
        startingIobUnits = dataStore.iobUnits.value ?: 0.0
        dataStore.manualBolusCancelResult.value = null
        dataStore.pumpSid.value = identity.pumpSid
        dataStore.timeSinceResetResponse.value = TimeSinceResetResponse(
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            DEMO_SECONDS_SINCE_RESET,
        )
        dataStore.manualBolusOperation.value = ManualBolusOperation(
            identity = identity,
            requestedMilliunits = requestedMilliunits,
            phase = ManualBolusOperationPhase.INITIATION_SENDING,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        publish(identity, requestedMilliunits, CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING)

        sequenceJob = scope.launch {
            delay(INITIATION_DURATION_MS)
            if (activeIdentity == identity) {
                updatePhase(identity, ManualBolusOperationPhase.ACTIVE)
                publish(identity, requestedMilliunits, CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING)
            }
            delay(PREPARING_DURATION_MS)
            if (activeIdentity == identity) {
                publish(identity, requestedMilliunits, CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING)
            }
            delay(DELIVERING_DURATION_MS)
            if (activeIdentity == identity) {
                finish(identity, requestedMilliunits)
            }
        }
    }

    fun cancel(identity: ManualBolusIdentity) {
        if (!Prefs(appContext).developerPreviewEnabled()) {
            Timber.e("Blocked manual bolus simulation cancellation outside Developer Preview")
            return
        }
        if (activeIdentity != identity) return
        val operation = dataStore.manualBolusOperation.value ?: return
        if (operation.identity != identity || operation.phase != ManualBolusOperationPhase.ACTIVE) return

        sequenceJob?.cancel()
        updatePhase(identity, ManualBolusOperationPhase.CANCELLATION_SENDING)
        sequenceJob = scope.launch {
            delay(CANCELLATION_RESPONSE_DURATION_MS)
            if (activeIdentity != identity) return@launch
            dataStore.manualBolusCancelResult.value = ManualBolusCancelResult(
                identity,
                ManualBolusCancelOutcome.CONFIRMED,
            )
            updatePhase(identity, ManualBolusOperationPhase.CANCELLATION_CONFIRMED)
            delay(CANCELLATION_TERMINAL_DURATION_MS)
            if (activeIdentity == identity) {
                val partialMilliunits = (operation.requestedMilliunits * CANCELLED_DELIVERY_FRACTION)
                    .toLong()
                    .coerceAtLeast(0L)
                finish(identity, partialMilliunits, cancelled = true)
            }
        }
    }

    private fun publish(
        identity: ManualBolusIdentity,
        requestedMilliunits: Long,
        status: CurrentBolusStatusResponse.CurrentBolusStatus,
    ) {
        dataStore.bolusCurrentResponse.value = CurrentBolusStatusResponse(
            status.id,
            identity.bolusId,
            DEMO_PUMP_SESSION_ID + DEMO_SECONDS_SINCE_RESET,
            requestedMilliunits,
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS.id(),
            FOOD_BOLUS_TYPE,
        )
    }

    private fun updatePhase(identity: ManualBolusIdentity, phase: ManualBolusOperationPhase) {
        val current = dataStore.manualBolusOperation.value ?: return
        if (current.identity != identity) return
        dataStore.manualBolusOperation.value = current.copy(
            phase = phase,
            updatedAtEpochMillis = System.currentTimeMillis().coerceAtLeast(current.updatedAtEpochMillis),
        )
    }

    private fun finish(
        identity: ManualBolusIdentity,
        deliveredMilliunits: Long,
        cancelled: Boolean = false,
    ) {
        if (activeIdentity != identity) return
        val current = dataStore.manualBolusOperation.value ?: return
        activeIdentity = null
        dataStore.manualBolusOperation.value = current.copy(
            phase = ManualBolusOperationPhase.TERMINAL,
            deliveredMilliunits = deliveredMilliunits,
            terminalStatusId = TERMINAL_STATUS_ID,
            updatedAtEpochMillis = System.currentTimeMillis().coerceAtLeast(current.updatedAtEpochMillis),
        )
        dataStore.bolusCurrentResponse.value = CurrentBolusStatusResponse(
            CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID.id,
            0,
            0L,
            0L,
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS.id(),
            FOOD_BOLUS_TYPE,
        )
        val deliveredUnits = deliveredMilliunits / 1000.0
        dataStore.iobUnits.value = startingIobUnits + deliveredUnits
        dataStore.lastBolusSource.value =
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
        dataStore.lastBolusStatus.value = String.format(
            Locale.US,
            "%.2fu at %s%s",
            deliveredUnits,
            LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a")),
            if (cancelled) " (cancelled demo)" else " (demo)",
        )
    }

    private fun Double.toExactMilliunits(): Long = BigDecimal.valueOf(this)
        .movePointRight(3)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

    companion object {
        private const val DEMO_PUMP_SID = 900002
        private const val DEMO_PUMP_SESSION_ID = 600_000_000L
        private const val DEMO_SECONDS_SINCE_RESET = 20_000L
        private const val MINIMUM_BOLUS_UNITS = 0.05
        private const val FOOD_BOLUS_TYPE = 1
        private const val TERMINAL_STATUS_ID = 0
        private const val CANCELLED_DELIVERY_FRACTION = 0.25
        private const val INITIATION_DURATION_MS = 750L
        private const val PREPARING_DURATION_MS = 1_500L
        private const val DELIVERING_DURATION_MS = 30_000L
        private const val CANCELLATION_RESPONSE_DURATION_MS = 800L
        // Keep the accepted state visible long enough to be read in Developer Preview.
        private const val CANCELLATION_TERMINAL_DURATION_MS = 3_000L
        private val nextId = AtomicInteger(30_000)

        private fun nextBolusId(): Int = nextId.updateAndGet { if (it >= 60_000) 30_000 else it + 1 }
    }
}
