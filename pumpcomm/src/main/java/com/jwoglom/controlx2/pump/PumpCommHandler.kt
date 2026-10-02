package com.jwoglom.controlx2.pump

import androidx.annotation.VisibleForTesting
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanResult
import android.content.Context
import java.util.concurrent.CountDownLatch
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.widget.Toast
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.controlx2.shared.AutomaticBolusValidation
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperationCoordinator
import com.jwoglom.controlx2.shared.ManualBolusOperationPhase
import com.jwoglom.controlx2.shared.ManualBolusTerminalAcknowledgement
import com.jwoglom.controlx2.shared.ManualBolusTerminalAcknowledgementStage
import com.jwoglom.controlx2.shared.ManualBolusValidation
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusOperationCoordinator
import com.jwoglom.controlx2.shared.QuickBolusValidation
import com.jwoglom.controlx2.shared.BolusPermissionAction
import com.jwoglom.controlx2.shared.BolusPermissionOperationCoordinator
import com.jwoglom.controlx2.shared.AbandonedBolusCleanupRequest
import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.controlx2.shared.PostBolusRecordCommand
import com.jwoglom.controlx2.shared.PostBolusRecordPhase
import com.jwoglom.controlx2.shared.PostBolusRecordPlan
import com.jwoglom.controlx2.shared.PostBolusRecordTransition
import com.jwoglom.controlx2.shared.PumpHydrationPhase
import com.jwoglom.controlx2.shared.PumpHydrationState
import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.messaging.StateKeys
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.util.extractPumpSid
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.pumpSessionIdsMatch
import com.jwoglom.controlx2.shared.isActiveManualBolus
import com.jwoglom.controlx2.shared.isActiveQuickBolus
import com.jwoglom.controlx2.shared.validateAutomaticBolusForCancellation
import com.jwoglom.controlx2.shared.validateManualBolusForCancellation
import com.jwoglom.controlx2.shared.validateQuickBolusForCancellation
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemConfig
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.messages.Packetize
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.bluetooth.CharacteristicUUID
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.bluetooth.ServiceUUID
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingType
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.CancelBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionRequest
import com.jwoglom.pumpx2.pump.messages.request.control.BolusPermissionReleaseRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.BolusCalcDataSnapshotRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBolusStatusV3Request
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.TimeSinceResetRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentEGVGuiDataRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractCentralChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.authentication.AbstractPumpChallengeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionReleaseResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteBgEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusV3Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusCompletedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.qualifyingEvent.QualifyingEvent
import com.jwoglom.pumpx2.shared.Hex
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import com.welie.blessed.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import timber.log.Timber
import java.security.Security
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.function.Supplier
import kotlin.math.roundToLong

const val CacheSeconds = 30
internal const val InitialProfileReadResponseTimeoutMs = 5_000L
internal const val InitialProfileReadMaxAttempts = 3
internal const val BolusCancellationOperationTimeoutMs = 15_000L
internal const val QuickBolusTerminalReconcileIntervalMs = 3_000L
internal const val QuickBolusTerminalHistoryBatchSize = 30
internal const val ManualBolusReconciliationRetryIntervalMs = 4_000L
internal const val ManualBolusReconciliationMaxAttempts = 3
internal const val ManualBolusNegativeEvidenceSettleMs = 4_000L
internal const val PumpNotConnectedSignalDedupMs = 1_000L

/**
 * Handler for pump communication. Manages the TandemPump connection lifecycle,
 * message routing, response caching, and bolus command handling.
 *
 * Extracted from CommService's inner class to enable independent testing.
 */
class PumpCommHandler(
    looper: Looper,
    private val callbacks: CommServiceCallbacks
) : Handler(looper) {
    private lateinit var pump: Pump
    private lateinit var tandemBTHandler: TandemBluetoothHandler
    var currentSession: PumpSession? = null
        internal set

    private var historyLogFetcher: PumpHistoryLogFetcher? = null
    private var historyLogSyncWorker: PumpHistoryLogSyncWorker? = null
    private val initialPumpProfileRead = InitialPumpProfileRead()
    private var postProfileConnectionInitializationComplete = false
    private val reconnectLiveHydration = ReconnectLiveHydration()
    private var hydrationNonce = 0L
    private var hydrationState = PumpHydrationState(
        PumpHydrationPhase.DISCONNECTED,
        System.currentTimeMillis(),
    )
    private var initialProfileReadRequestNonce = 0L
    private var initialProfileReadRequestAttempts = 0

    private enum class AutomaticBolusCancelStage { VERIFYING, CANCELLING }

    private data class PendingAutomaticBolusCancel(
        val identity: AutomaticBolusIdentity,
        val connectionToken: com.jwoglom.controlx2.shared.PumpSessionToken,
        val nonce: Long,
        val stage: AutomaticBolusCancelStage,
        val matchingVerificationReads: Int = 0,
    )

    private var pendingAutomaticBolusCancel: PendingAutomaticBolusCancel? = null
    private var automaticBolusCancelNonce = 0L
    private var awaitingAutomaticBolusTerminal = false

    private enum class ManualBolusCancelStage { VERIFYING, CANCELLING }

    private data class PendingManualBolusCancel(
        val identity: ManualBolusIdentity,
        val connectionToken: com.jwoglom.controlx2.shared.PumpSessionToken,
        val nonce: Long,
        val stage: ManualBolusCancelStage,
        val matchingVerificationReads: Int = 0,
    )

    private var pendingManualBolusCancel: PendingManualBolusCancel? = null
    private var manualBolusCancelNonce = 0L

    private enum class QuickBolusCancelStage { VERIFYING, CANCELLING }

    private data class PendingQuickBolusCancel(
        val identity: QuickBolusIdentity,
        val connectionToken: com.jwoglom.controlx2.shared.PumpSessionToken,
        val nonce: Long,
        val stage: QuickBolusCancelStage,
        val matchingVerificationReads: Int = 0,
    )

    private var pendingQuickBolusCancel: PendingQuickBolusCancel? = null
    private var quickBolusCancelNonce = 0L
    private var lastQuickBolusTerminalReadIdentity: QuickBolusIdentity? = null
    private var lastQuickBolusTerminalReadElapsedMillis = 0L
    private var manualBolusInitiationNonce = 0L
    private var abandonedBolusBgNonce = 0L
    private var bolusPermissionReleaseNonce = 0L
    private var bolusPermissionReconcileInFlight = false
    private var bolusPreparationRequested = false
    private var emptyCartridgeAlarmActive = false
    private val manualBolusOperations = ManualBolusOperationCoordinator(
        initial = callbacks.loadManualBolusOperation(),
        persist = callbacks::persistManualBolusOperation,
    )
    private var manualBolusReconciliationTracker: ManualBolusReconciliationTracker? = null
    private var manualBolusReconciliationAttempt: ManualBolusReconciliationAttempt? = null
    private var manualBolusReconciliationNonce = 0L
    private enum class ManualBolusNegativeEvidenceKind {
        GROUNDED_ABSENCE,
        LEGACY_CONFIRMED_ENDED,
    }
    private data class PendingManualBolusNegativeEvidence(
        val identity: ManualBolusIdentity,
        val kind: ManualBolusNegativeEvidenceKind,
        val notBeforeElapsedRealtime: Long,
    )
    private var pendingManualBolusNegativeEvidence: PendingManualBolusNegativeEvidence? = null
    private data class PumpHistorySequenceBoundary(
        val pumpSid: Int,
        val pumpSessionId: Long,
        val newestSequence: Long,
    )
    private var latestPumpHistorySequenceBoundary: PumpHistorySequenceBoundary? = null
    private val quickBolusOperations = QuickBolusOperationCoordinator(
        initial = callbacks.loadQuickBolusOperation(),
        persist = callbacks::persistQuickBolusOperation,
    )
    private val bolusPermissionOperations = BolusPermissionOperationCoordinator(
        initial = callbacks.loadBolusPermissionOperation(),
        persist = callbacks::persistBolusPermissionOperation,
    )
    private val expectedPumpSuspendTransition = ExpectedPumpSuspendTransition()
    private var lastPumpNotConnectedReason: String? = null
    private var lastPumpNotConnectedSignalAtMs: Long = Long.MIN_VALUE

    fun getPumpSid(): Int? {
        return if (this::pump.isInitialized) pump.pumpSid else null
    }

    fun isPumpReadyForHistoryFetch(): Boolean {
        return currentSession?.isActive == true
    }

    /** Low-frequency fallback; qualifying events and active-bolus polling remain primary. */
    fun requestBolusStatusWatchdog() {
        post {
            if (this::pump.isInitialized && currentSession?.isActive == true) {
                pump.command(
                    CurrentBolusStatusRequest(),
                    if (currentSession?.isBolusTrafficActive() == true) {
                        PumpCommandPriority.BOLUS
                    } else {
                        PumpCommandPriority.STATUS
                    },
                )
            }
        }
    }

    private fun ensurePumpConnection(foregroundRequest: Boolean) {
        if (!this::tandemBTHandler.isInitialized) {
            Timber.w("ensurePumpConnection ignored because Bluetooth handler is not initialized")
            return
        }
        if (this::pump.isInitialized && pump.isConnected && currentSession?.isActive == true) {
            Timber.d("ensurePumpConnection: pump session is already active")
            return
        }
        Timber.i("ensurePumpConnection: asking transport to re-arm (foreground=$foregroundRequest)")
        tandemBTHandler.ensureConnectionActive(foregroundRequest)
    }

    private fun reportPumpNotConnected(reason: String) {
        val now = SystemClock.elapsedRealtime()
        val duplicate = lastPumpNotConnectedReason == reason &&
            lastPumpNotConnectedSignalAtMs != Long.MIN_VALUE &&
            now - lastPumpNotConnectedSignalAtMs in 0 until PumpNotConnectedSignalDedupMs
        if (duplicate) {
            Timber.d("Coalescing duplicate pump-not-connected signal: $reason")
            return
        }
        lastPumpNotConnectedReason = reason
        lastPumpNotConnectedSignalAtMs = now
        callbacks.sendWearCommMessage(
            MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED,
            reason.toByteArray(),
        )
    }

    /**
     * Re-publishes the service-owned connection and hydration state after an Activity/UI
     * recreation. The caller opts into restarting the read-only profile snapshot only when its
     * process-local profile cache is absent or incomplete.
     */
    fun refreshConnectedPumpSnapshotForUi(includeProfileSnapshot: Boolean = false): Boolean {
        val sessionIsActive = this::pump.isInitialized && currentSession?.isActive == true
        if (!sessionIsActive) {
            // This is a direct reply to a UI request, so it must not be coalesced with an earlier
            // not-connected event that may have been emitted while that UI had no listener.
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED,
                "service_snapshot_disconnected".toByteArray(),
            )
            callbacks.sendWearCommMessage(
                MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE,
                hydrationState.encode(),
            )
            return false
        }

        callbacks.sendWearCommMessage(
            MessagePaths.FROM_PUMP_PUMP_CONNECTED,
            pump.lastPeripheral?.name.orEmpty().toByteArray(),
        )
        callbacks.sendWearCommMessage(
            MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE,
            hydrationState.encode(),
        )
        if (includeProfileSnapshot) {
            retryInitialPumpProfileRead()
        }
        return true
    }

    /** Restarts only the idempotent profile read; pairing credentials and the BLE bond are kept. */
    fun retryInitialPumpProfileRead() {
        post {
            if (!this::pump.isInitialized || currentSession?.isActive != true) {
                callbacks.sendWearCommMessage(
                    MessagePaths.FROM_PUMP_INITIAL_PROFILE_READ_FAILED,
                    "Pump disconnected before profiles could be loaded.".toByteArray(),
                )
                return@post
            }
            if (currentSession?.isBolusTrafficActive() == true) {
                Timber.i("Deferring initial profile read while the bolus fast lane is active")
                initialPumpProfileRead.reset()
                initialProfileReadRequestNonce += 1
                initialProfileReadRequestAttempts = 0
                return@post
            }
            initialProfileReadRequestNonce += 1
            initialProfileReadRequestAttempts = 0
            val firstRequest = initialPumpProfileRead.start()
            Timber.i("Starting pump-owned profile snapshot read")
            dispatchInitialProfileReadRequest(firstRequest, resetAttempts = true)
        }
    }

    private fun dispatchInitialProfileReadRequest(
        request: com.jwoglom.pumpx2.pump.messages.Message,
        resetAttempts: Boolean,
    ) {
        if (resetAttempts) initialProfileReadRequestAttempts = 0
        initialProfileReadRequestAttempts += 1
        initialProfileReadRequestNonce += 1
        val nonce = initialProfileReadRequestNonce
        val attempt = initialProfileReadRequestAttempts

        try {
            pump.command(request)
        } catch (t: Throwable) {
            Timber.w(t, "Initial profile read request could not be sent")
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_INITIAL_PROFILE_READ_FAILED,
                "The profile request could not be sent. Check the pump connection and retry.".toByteArray(),
            )
            return
        }

        postDelayed({
            if (nonce != initialProfileReadRequestNonce || currentSession?.isActive != true) {
                return@postDelayed
            }
            val outstanding = initialPumpProfileRead.outstandingRequest() ?: return@postDelayed
            if (attempt < InitialProfileReadMaxAttempts) {
                Timber.w(
                    "Initial profile read timed out for ${outstanding.messageName()}; " +
                        "retrying attempt ${attempt + 1} of $InitialProfileReadMaxAttempts",
                )
                dispatchInitialProfileReadRequest(outstanding, resetAttempts = false)
            } else {
                Timber.e(
                    "Initial profile read stalled after $InitialProfileReadMaxAttempts attempts " +
                        "for ${outstanding.messageName()}",
                )
                callbacks.sendWearCommMessage(
                    MessagePaths.FROM_PUMP_INITIAL_PROFILE_READ_FAILED,
                    "The pump stopped responding while profiles were loading. Pairing is still complete; retry the profile read.".toByteArray(),
                )
            }
        }, InitialProfileReadResponseTimeoutMs)
    }

    /**
     * Tear down any live BLE pump connection, blocking up to [timeoutMs] for
     * the GATT-level disconnect callback to fire.
     *
     * Used by the graceful path in [com.jwoglom.controlx2.shared.util.AppReload]
     * so that callers which kill the process (toggling the device role,
     * Nightscout config, etc.) don't leave the pump in a stale "peer vanished"
     * state — the next reconnect would otherwise renegotiate to a high-latency
     * link and silently drop the JPAKE3 message, manifesting as a spurious
     * "pairing code was invalid" error after the very next pair.
     *
     * Returns `true` if a disconnect callback fired in time, `false` if there
     * was nothing to disconnect or the timeout elapsed first.
     */
    fun shutdownAndAwaitDisconnect(timeoutMs: Long): Boolean {
        if (!this::pump.isInitialized) return false
        val peripheral = pump.lastPeripheral ?: return false
        Timber.i("PumpCommHandler.shutdownAndAwaitDisconnect: cancelling ${peripheral.name}")
        val latch = CountDownLatch(1)
        pump.shutdownLatch = latch
        try {
            peripheral.cancelConnection()
        } catch (t: Throwable) {
            Timber.w(t, "shutdownAndAwaitDisconnect: cancelConnection threw")
            pump.shutdownLatch = null
            return false
        }
        return try {
            val ok = latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!ok) Timber.w("shutdownAndAwaitDisconnect: timed out after ${timeoutMs}ms")
            ok
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } finally {
            pump.shutdownLatch = null
        }
    }

    @VisibleForTesting
    fun simulateConnectedPump(peripheral: BluetoothPeripheral) {
        pump.initializeConnection(peripheral)
    }

    fun stopHistoryLogSyncWorker() {
        historyLogSyncWorker?.stop()
        historyLogSyncWorker = null
    }

    fun refreshHistoryLogSyncWorker(triggerImmediateSync: Boolean = false) {
        val worker = historyLogSyncWorker
        if (worker == null) {
            Timber.i("refreshHistoryLogSyncWorker skipped: worker not initialized")
            return
        }

        if (!callbacks.prefAutoFetchHistoryLogs()) {
            Timber.i("refreshHistoryLogSyncWorker stopping worker")
            worker.stop()
            return
        }

        Timber.i("refreshHistoryLogSyncWorker starting worker")
        worker.start()
        if (triggerImmediateSync) {
            worker.triggerImmediateSync()
        }
    }

    private fun requestHistoryLogStatusUpdate() {
        if (!callbacks.prefAutoFetchHistoryLogs()) {
            return
        }
        callbacks.sendPumpCommMessages(
            PumpMessageSerializer.toBulkBytes(
                listOf(com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest())
            )
        )
    }

    private fun beginBolusFastLane(reason: String) {
        Timber.i("Beginning bolus fast lane: $reason")
        if (!postProfileConnectionInitializationComplete &&
            initialPumpProfileRead.outstandingRequest() != null
        ) {
            Timber.i("Pausing initial profile read until the bolus lifecycle completes")
            initialPumpProfileRead.reset()
            initialProfileReadRequestNonce += 1
            initialProfileReadRequestAttempts = 0
        }
        currentSession?.beginBolusTraffic()
        historyLogSyncWorker?.stop()
    }

    private fun endBolusFastLane(reason: String) {
        Timber.i("Ending bolus fast lane: $reason")
        currentSession?.endBolusTraffic()
        if (!postProfileConnectionInitializationComplete &&
            currentSession?.isActive == true &&
            initialPumpProfileRead.outstandingRequest() == null
        ) {
            startReconnectHydrationAndProfile()
        }
        if (callbacks.prefAutoFetchHistoryLogs()) {
            refreshHistoryLogSyncWorker(triggerImmediateSync = false)
        }
    }

    private fun publishHydrationState(phase: PumpHydrationPhase) {
        hydrationState = PumpHydrationState(phase, System.currentTimeMillis())
        callbacks.sendWearCommMessage(
            MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE,
            hydrationState.encode(),
        )
    }

    private fun startReconnectHydrationAndProfile() {
        if (!this::pump.isInitialized || currentSession?.isActive != true) return
        reconnectLiveHydration.reset()
        hydrationNonce += 1L
        val nonce = hydrationNonce
        publishHydrationState(PumpHydrationPhase.LIVE_STATE_LOADING)

        val apiVersion = PumpState.getPumpAPIVersion()
            ?: com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.API_V2_5.get()
        val requests = listOf(
            TimeSinceResetRequest(),
            CurrentBolusStatusRequest(),
            LastBolusStatusRequestBuilder.create(apiVersion),
            *pumpNotificationStatusRequests(apiVersion).toTypedArray(),
            HomeScreenMirrorRequest(),
            LoadStatusRequest(),
            InsulinStatusRequest(),
            ControlIQIOBRequest(),
            CurrentBatteryRequestBuilder.create(apiVersion),
            CurrentEGVGuiDataRequest(),
        ).filter { !it.props().minApi.get().greaterThan(apiVersion) }

        Timber.i("Queueing reconnect live-state hydration before profile/history (${requests.size} reads)")
        requests.forEach { pump.command(it, PumpCommandPriority.STATUS) }
        retryInitialPumpProfileRead()

        postDelayed({
            if (nonce == hydrationNonce &&
                hydrationState.phase == PumpHydrationPhase.LIVE_STATE_LOADING
            ) {
                Timber.w(
                    "Reconnect live-state hydration incomplete: ${reconnectLiveHydration.pendingFields()}",
                )
                publishHydrationState(PumpHydrationPhase.LIVE_STATE_PARTIAL)
            }
        }, 45_000L)
    }

    private fun needsBolusRecoveryOnConnect(): Boolean {
        if (manualBolusOperations.operation?.isUnresolved == true) return true
        if (quickBolusOperations.operation?.isUnresolved == true) return true
        return when (bolusPermissionOperations.operation?.phase) {
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.REQUESTING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.RELEASE_PENDING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.RELEASE_RECONCILING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.RELEASE_SENDING,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.INITIATION_SUBMITTED,
            -> true
            else -> false
        }
    }

    private fun prepareBolusWorkflow() {
        if (bolusPreparationRequested) {
            Timber.i("Coalescing duplicate request to prepare the current bolus workflow")
            return
        }
        if (manualBolusOperations.operation?.isUnresolved == true) {
            Timber.w("Blocking bolus preparation while a prior manual bolus is unresolved")
            requestManualBolusReconciliation()
            callbacks.sendWearCommMessage(
                MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                "unresolved_bolus_operation".toByteArray(),
            )
            return
        }
        if (!pumpConnectedPrecondition()) {
            bolusPreparationRequested = false
            return
        }
        beginBolusFastLane("user opened bolus workflow")
        bolusPreparationRequested = true
        continueBolusPreparationWithCurrentIdentity()
    }

    private fun continueBolusPreparationWithCurrentIdentity() {
        if (!bolusPreparationRequested || !pumpConnectedPrecondition()) return
        val pumpSid = getPumpSid()
        val pumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (pumpSid == null || pumpSessionId == null || pumpSessionId <= 0L) {
            Timber.i("Bolus preparation needs a stable pump session; requesting pump time")
            pump.command(TimeSinceResetRequest(), PumpCommandPriority.BOLUS)
            return
        }

        val started = bolusPermissionOperations.beginFreshWorkflowAcquisition(
            pumpSid = pumpSid,
            pumpSessionId = pumpSessionId,
        ) {
            bolusPermissionReconcileInFlight = true
            Timber.i("Fresh bolus workflow is asking the pump to grant or reconcile permission")
            pump.command(BolusPermissionRequest(), PumpCommandPriority.BOLUS)
        }
        if (!started) {
            Timber.i(
                "Bolus preparation is waiting for the existing permission lifecycle: " +
                    "${bolusPermissionOperations.operation?.phase}",
            )
        }
    }

    private fun ensurePumpUnbondedForFreshInit(filterToBluetoothMac: String?): Boolean {
        val ctx = callbacks.getApplicationContext()
        val targetMac = (filterToBluetoothMac ?: callbacks.prefPumpFinderPumpMac().orEmpty())
            .trim()
            .uppercase()
        val scheduledUnbondMac = callbacks.prefUnbondOnNextCommInitMac().orEmpty().trim().uppercase()

        if (scheduledUnbondMac.isEmpty()) {
            Timber.i("init_pump_comm: skipping unbond (not scheduled for this init)")
            return true
        }

        if (targetMac.isEmpty()) {
            Timber.i("init_pump_comm: skipping unbond (no target MAC)")
            return true
        }

        if (scheduledUnbondMac != targetMac) {
            Timber.i("init_pump_comm: skipping unbond for $targetMac (scheduled for $scheduledUnbondMac)")
            return true
        }

        callbacks.prefSetUnbondOnNextCommInitMac(null)

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Timber.w("init_pump_comm: cannot unbond $targetMac because BluetoothAdapter is null")
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Timber.w("init_pump_comm: cannot unbond $targetMac without BLUETOOTH_CONNECT permission")
            return true
        }

        val bondedDevice = adapter.bondedDevices
            ?.firstOrNull { it.address.equals(targetMac, ignoreCase = true) }
        if (bondedDevice == null) {
            Timber.i("init_pump_comm: target $targetMac not currently bonded")
            return true
        }

        try {
            val removeBondMethod = bondedDevice.javaClass.getMethod("removeBond")
            val didRequestUnbond = removeBondMethod.invoke(bondedDevice) as? Boolean ?: false
            Timber.i("init_pump_comm: requested unbond for ${bondedDevice.name} ($targetMac), result=$didRequestUnbond")
        } catch (e: SecurityException) {
            Timber.w(e, "init_pump_comm: missing permission while requesting unbond for $targetMac")
        } catch (e: Exception) {
            Timber.w(e, "init_pump_comm: failed unbond request for $targetMac")
        }

        Thread.sleep(500)
        val stillBonded = adapter.bondedDevices
            ?.any { it.address.equals(targetMac, ignoreCase = true) } == true
        if (!stillBonded) {
            Timber.i("init_pump_comm: target $targetMac unbonded successfully")
            return true
        }

        val details = "${bondedDevice.name ?: "Unknown Tandem device"}=$targetMac"
        Timber.w("init_pump_comm: target is still bonded after unbond attempt: $details")
        callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_BONDED_NEEDS_MANUAL_UNBOND, details.toByteArray())
        callbacks.showToast(
            "Pump is still bonded in Android Bluetooth settings. Unpair it, then retry.",
            Toast.LENGTH_LONG
        )
        return false
    }

    private inner class Pump(var tandemConfig: TandemConfig) : TandemPump(callbacks.getApplicationContext(), tandemConfig) {
        private val scope = CoroutineScope(SupervisorJob(parent = callbacks.supervisorJob) + Dispatchers.IO)
        var lastPeripheral: BluetoothPeripheral? = null
        var isConnected = false
        var pumpSid: Int? = null
        // Set by [shutdownAndAwaitDisconnect] so that the next disconnect
        // callback can wake the waiter on the calling thread (typically the
        // main thread during triggerAppReload's shutdown hook).
        @Volatile var shutdownLatch: CountDownLatch? = null

        init {
            if (callbacks.prefConnectionSharingEnabled()) {
                enableTconnectAppConnectionSharing()
                enableSendSharedConnectionResponseMessages()
            }
            if (callbacks.prefOnlySnoopBluetoothEnabled()) {
                Timber.i("ONLY SNOOP BLUETOOTH ENABLED")
                onlySnoopBluetoothAndBlockAllPumpX2Functionality()
            }

            if (callbacks.prefInsulinDeliveryActions()) {
                Timber.i("ACTIONS AFFECTING INSULIN DELIVERY ENABLED")
                enableActionsAffectingInsulinDelivery()
            } else {
                Timber.i("Actions affecting insulin delivery are disabled")
            }
            Timber.i("Pump init")
        }

        override fun onReceiveMessage(
            peripheral: BluetoothPeripheral?,
            message: com.jwoglom.pumpx2.pump.messages.Message?
        ) {
            val responseSession = currentSession
            val heldResponseDispatch = message?.let {
                responseSession?.beginResponseHandling(it)
            } == true
            message?.let {
                callbacks.pumpCommState.lastResponseMessage.put(Pair(it.characteristic, it.opCode()), Pair(it, Instant.now()))
                val source = if (callbacks.pumpCommState.consumeDebugPromptResponse(it.characteristic, it.opCode())) {
                    com.jwoglom.controlx2.shared.util.SendType.DEBUG_PROMPT
                } else {
                    com.jwoglom.controlx2.shared.util.SendType.STANDARD
                }
                callbacks.onPumpMessageReceived(it, source = source)
            }
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_RECEIVE_MESSAGE, PumpMessageSerializer.toBytes(message))

            message?.let { response ->
                if (hydrationState.phase == PumpHydrationPhase.LIVE_STATE_LOADING &&
                    reconnectLiveHydration.onResponse(response)
                ) {
                    hydrationNonce += 1L
                    publishHydrationState(PumpHydrationPhase.LIVE_STATE_READY)
                }
                val profileAdvance = initialPumpProfileRead.onResponse(response)
                if (profileAdvance.acceptedResponse) {
                    initialProfileReadRequestNonce += 1
                    initialProfileReadRequestAttempts = 0
                    if (profileAdvance.completed) {
                        Timber.i("Initial pump profile snapshot read complete")
                        finishPostProfileConnectionInitialization()
                    } else {
                        profileAdvance.nextRequest?.let { next ->
                            Timber.i("Initial pump profile snapshot requesting ${next.messageName()}")
                            dispatchInitialProfileReadRequest(next, resetAttempts = true)
                        }
                    }
                }
            }

            message?.let {
                if (it is CurrentBatteryAbstractResponse ||
                    it is ControlIQIOBResponse ||
                    it is CurrentEGVGuiDataResponse
                ) {
                    callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE, PumpMessageSerializer.toBytes(message))
                }
            }

            when (message) {
                is TimeSinceResetResponse -> onReceiveTimeSinceResetResponse(message)
                is SuspendPumpingResponse -> expectedPumpSuspendTransition.onResponse(message.status)
                is InitiateBolusResponse -> onReceiveInitiateBolusResponse(message)
                is BolusCalcDataSnapshotResponse -> {
                    if (bolusPreparationRequested) {
                        Timber.i("Bolus preparation snapshot received")
                        bolusPreparationRequested = false
                    }
                }
                is CurrentBolusStatusResponse -> onReceiveCurrentBolusStatusResponse(message)
                is LastBolusStatusAbstractResponse -> onReceiveLastBolusStatusResponse(message)
                is LastBolusStatusV3Response -> onReceiveLastBolusStatusV3Response(message)
                is AlarmStatusResponse -> onReceiveAlarmStatusResponse(message)
                is CancelBolusResponse -> onReceiveCancelBolusResponse(message)
                is BolusPermissionResponse -> {
                    bolusPermissionReconcileInFlight = false
                    PumpStateSupplier.inProgressBolusId = Supplier {
                        message.bolusId.takeIf { message.isPermissionGranted && it > 0 }
                    }
                    val sid = getPumpSid()
                    val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
                    if (sid != null && sessionId != null && sessionId > 0L) {
                        val followUp = bolusPermissionOperations.onPermissionResponse(
                                pumpSid = sid,
                                pumpSessionId = sessionId,
                                granted = message.isPermissionGranted,
                                bolusId = message.bolusId,
                            )
                        executeBolusPermissionAction(followUp)
                        if (message.isPermissionGranted && message.bolusId > 0 &&
                            bolusPreparationRequested &&
                            followUp == BolusPermissionAction.None
                        ) {
                            Timber.i(
                                "Bolus permission granted; requesting one fresh calculation snapshot",
                            )
                            pump.command(
                                BolusCalcDataSnapshotRequest(),
                                PumpCommandPriority.BOLUS,
                            )
                        } else if (!message.isPermissionGranted) {
                            bolusPreparationRequested = false
                            endBolusFastLane("permission denied")
                        }
                    } else {
                        Timber.e("Cannot persist bolus permission response without pump identity")
                    }
                }
                is RemoteBgEntryResponse -> {
                    val operation = bolusPermissionOperations.operation
                    if (operation?.phase ==
                        com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING
                    ) {
                        abandonedBolusBgNonce += 1
                        Timber.i(
                            "Abandoned-bolus BG response status=${message.status}; " +
                                "continuing permission release bolusId=${operation.bolusId}",
                        )
                        executeBolusPermissionAction(
                            bolusPermissionOperations.onAutomaticBgEntryResponse(),
                        )
                    } else {
                        onReceivePostBolusBgEntryResponse(message)
                    }
                }
                is RemoteCarbEntryResponse -> onReceivePostBolusCarbEntryResponse(message)
                is BolusPermissionReleaseResponse -> {
                    bolusPermissionReleaseNonce += 1
                    val released =
                        message.releaseStatus == BolusPermissionReleaseResponse.ReleaseStatus.SUCCESS
                    bolusPermissionOperations.onReleaseResponse(released)
                    if (released) {
                        PumpStateSupplier.inProgressBolusId = Supplier { null }
                        if (bolusPreparationRequested) {
                            Timber.i(
                                "Previous permission cleanup completed; preparing the fresh workflow",
                            )
                            continueBolusPreparationWithCurrentIdentity()
                        } else {
                            endBolusFastLane("unused permission released")
                        }
                    }
                }
                is CurrentBatteryAbstractResponse -> callbacks.updateComplicationData("pumpBattery", "${message.batteryPercent}", Instant.now())
                is ControlIQIOBResponse -> callbacks.updateComplicationData("pumpIOB", "${InsulinUnit.from1000To1(message.pumpDisplayedIOB)}", Instant.now())
                is ControlIQInfoAbstractResponse -> callbacks.updateComplicationData(
                    StateKeys.PUMP_CONTROL_IQ_MODE,
                    when (message.currentUserModeType) {
                        ControlIQInfoAbstractResponse.UserModeType.STANDARD -> "STANDARD"
                        ControlIQInfoAbstractResponse.UserModeType.SLEEP -> "SLEEP"
                        ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> "EXERCISE"
                        else -> "UNKNOWN"
                    },
                    Instant.now(),
                )
                is InsulinStatusResponse -> callbacks.updateComplicationData("pumpCartridgeUnits", "${message.currentInsulinAmount}", Instant.now())
                is CurrentBasalStatusResponse -> callbacks.updateComplicationData("pumpCurrentBasal", "${InsulinUnit.from1000To1(message.currentBasalRate)}", Instant.now())
                is CurrentEGVGuiDataResponse -> callbacks.updateComplicationData("cgmReading", "${message.cgmReading}", Instant.now())
                is HistoryLogStatusResponse -> {
                    Timber.i("HistoryLogStatusResponse: $message")
                    rememberPumpHistorySequenceBoundary(message)
                    if (!requestManualBolusTerminalHistory(message) &&
                        !requestQuickBolusTerminalHistory(message)
                    ) {
                        scope.launch {
                            Timber.i("HistoryLogStatusResponse: launching in scope $message")
                            historyLogFetcher?.onStatusResponse(message, scope)
                        }
                    }
                }
                is HistoryLogStreamResponse -> {
                    message.historyLogs.forEach {
                        Timber.i("HISTORY-LOG-MESSAGE(${it.sequenceNum}): $it")
                        callbacks.pumpCommState.historyLogCache[it.sequenceNum] = it
                        observeManualBolusReconciliationHistory(it)
                        if (it is BolusDeliveryHistoryLog) {
                            onReceiveBolusDeliveryHistoryLog(it)
                        }
                        if (it is BolusCompletedHistoryLog) {
                            onReceiveBolusCompletedHistoryLog(it)
                        }
                        scope.launch {
                            historyLogFetcher?.onStreamResponse(it)
                        }
                    }
                    evaluateManualBolusReconciliation()
                    requestNextManualBolusHistoryChunk()
                }
            }

            if (heldResponseDispatch) {
                responseSession?.endResponseHandling()
            }

            message?.let { callbacks.dispatchExternalMessage(it) }
            message?.let { callbacks.updateNotificationWithPumpData(it) }
        }

        override fun onReceiveQualifyingEvent(
            peripheral: BluetoothPeripheral?,
            events: MutableSet<QualifyingEvent>?
        ) {
            Timber.i("onReceiveQualifyingEvent: $events")
            if (callbacks.prefQualifyingEventToastsEnabled()) {
                callbacks.showToast("Events: $events", Toast.LENGTH_SHORT)
            }
            if (events != null && QualifyingEvent.PUMP_COMMUNICATIONS_SUSPENDED in events) {
                Timber.w("onReceiveQualifyingEvent: PUMP_COMMUNICATIONS_SUSPENDED — pausing sends")
                currentSession?.pauseSends(currentSession?.rateLimitConfig?.commSuspendedPauseMs ?: 5_000)
            }
            if (expectedPumpSuspendTransition.isActive()) {
                Timber.i(
                    "Deferring qualifying-event status reads during expected pump suspend transition: $events",
                )
            } else {
                events?.forEach { event ->
                    event.suggestedHandlers.forEach { handler ->
                        Timber.i("onReceiveQualifyingEvent: running handler for $event message: ${handler.get()}")
                        handler.get()?.let { command(it) }
                    }
                }
            }
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_RECEIVE_QUALIFYING_EVENT, com.jwoglom.controlx2.shared.PumpQualifyingEventsSerializer.toBytes(events))
        }

        var pairingCodeCentralChallenge: AbstractCentralChallengeResponse? = null
        override fun onWaitingForPairingCode(
            peripheral: BluetoothPeripheral?,
            centralChallengeResponse: AbstractCentralChallengeResponse?
        ) {
            pairingCodeCentralChallenge = centralChallengeResponse
            val hasSavedPairingCode = !PumpState.getPairingCode(context).isNullOrBlank()
            if (!hasSavedPairingCode) {
                val challengeBytes = if (centralChallengeResponse != null) {
                    PumpMessageSerializer.toBytes(centralChallengeResponse)
                } else {
                    byteArrayOf()
                }
                Timber.i("onWaitingForPairingCode: no saved pairing code, waiting for user input")
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_MISSING_PAIRING_CODE, challengeBytes)
                return
            }
            Timber.i("onWaitingForPairingCode: saved pairing code found, auto-pairing")
            performPairing(peripheral, centralChallengeResponse, false)
        }

        override fun onInvalidPairingCode(
            peripheral: BluetoothPeripheral?,
            resp: AbstractPumpChallengeResponse?
        ) {
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_INVALID_PAIRING_CODE, "".toByteArray())
            super.onInvalidPairingCode(peripheral, resp)
        }

        fun performPairing(
            peripheral: BluetoothPeripheral?,
            centralChallengeResponse: AbstractCentralChallengeResponse?,
            manuallyTriggered: Boolean
        ) {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            }
            Security.addProvider(BouncyCastleProvider())

            Timber.i("performPairing manuallyTriggered=$manuallyTriggered")
            var challengeBytes = byteArrayOf()
            if (centralChallengeResponse != null) {
                challengeBytes = PumpMessageSerializer.toBytes(centralChallengeResponse)
            }
            PumpState.getPairingCode(context)?.let {
                Timber.i("Pairing with saved code: <redacted>; legacyCentralChallengePresent=${centralChallengeResponse != null}")
                pair(peripheral, centralChallengeResponse, it)
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_ENTERED_PAIRING_CODE, challengeBytes)
            } ?: run {
                Timber.i("Pairing without saved code: centralChallenge: $centralChallengeResponse")
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_MISSING_PAIRING_CODE, challengeBytes)
            }
        }

        override fun onPumpDiscovered(
            peripheral: BluetoothPeripheral?,
            scanResult: ScanResult?,
            readyState: PumpReadyState
        ): Boolean {
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_DISCOVERED,
                "${peripheral?.name.orEmpty()};;${readyState.name}".toByteArray()
            )
            return super.onPumpDiscovered(peripheral, scanResult, readyState)
        }

        override fun onInitialPumpConnection(peripheral: BluetoothPeripheral?) {
            lastPeripheral = peripheral
            val wait = (500..1000).random()
            Timber.i("Waiting to pair onInitialPumpConnection to avoid race condition with tconnect app for ${wait}ms")
            Thread.sleep(wait.toLong())

            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_INITIAL_PUMP_CONNECTION, "${peripheral?.name}".toByteArray())

            if (Packetize.txId.get() > 0) {
                Timber.w("Not pairing in onInitialPumpConnection because it looks like the tconnect app has already paired with txId=${Packetize.txId.get()}")
                return
            }
            super.onInitialPumpConnection(peripheral)
        }

        @VisibleForTesting
        fun initializeConnection(peripheral: BluetoothPeripheral) {
            val reconnectingAfterSuspend = expectedPumpSuspendTransition.isActive()
            lastPeripheral = peripheral

            extractPumpSid(peripheral.name ?: "")?.let {
                pumpSid = it
                callbacks.prefSetCurrentPumpSid(it)
            }

            val session = PumpSession.open(this, peripheral)
            this@PumpCommHandler.currentSession = session

            isConnected = true
            Timber.i("service initializeConnection: $this")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED,
                peripheral.name!!.toByteArray()
            )
            val glucoseUnit = callbacks.prefGlucoseUnit()
            if (glucoseUnit != null) {
                callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_GLUCOSE_UNIT, glucoseUnit.name.toByteArray())
            }
            callbacks.markConnectionTime()
            if (reconnectingAfterSuspend) {
                expectedPumpSuspendTransition.clear()
                Timber.i("Expected pump suspend reconnect complete; scheduling fresh pump-state read")
                post {
                    command(HomeScreenMirrorRequest())
                    command(LoadStatusRequest())
                }
            }
        }

        private fun initializeHistoryAndSync() {
            historyLogFetcher = callbacks.createHistoryLogFetcher(
                pumpSid = pumpSid!!,
                pumpSession = this@PumpCommHandler.currentSession!!,
                autoFetchEnabled = { callbacks.prefAutoFetchHistoryLogs() },
            )
            Timber.i("HistoryLogFetcher initialized")

            historyLogSyncWorker = callbacks.createHistoryLogSyncWorker(requestSync = { requestHistoryLogStatusUpdate() })
            refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            callbacks.onPumpConnectedSync(pumpSid!!)
        }

        private fun finishPostProfileConnectionInitialization() {
            if (postProfileConnectionInitializationComplete) return
            val peripheral = lastPeripheral ?: return
            postProfileConnectionInitializationComplete = true
            initializeHistoryAndSync()
            waitForResponseStabilization()
            sendBaseCommandsIfNeeded(peripheral)
            callbacks.updateNotification("Connected to pump")
        }

        private fun waitForResponseStabilization() {
            var numResponses = -99999
            while (PumpState.processedResponseMessages != numResponses) {
                numResponses = PumpState.processedResponseMessages
                val wait = (250..500).random()
                Timber.i("service onPumpConnected -- waiting for ${wait}ms to avoid race conditions: (processedResponseMessages: ${PumpState.processedResponseMessages})")
                Thread.sleep(wait.toLong())
            }
        }

        private fun sendBaseCommandsIfNeeded(peripheral: BluetoothPeripheral) {
            Timber.i("service onPumpConnected -- checking for base messages (processedResponseMessages: ${PumpState.processedResponseMessages})")
            if (!callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, ApiVersionRequest().opCode()))) {
                Timber.i("service onPumpConnected -- sending ApiVersionRequest")
                command(ApiVersionRequest())
            }

            if (!callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(Characteristic.CURRENT_STATUS, TimeSinceResetResponse().opCode()))) {
                Timber.i("service onPumpConnected -- sending TimeSinceResetResponse")
                command(TimeSinceResetRequest())
            }
            Thread.sleep(250)
        }

        override fun onPumpConnected(peripheral: BluetoothPeripheral?) {
            initializeConnection(peripheral!!)
            lastPumpNotConnectedReason = null
            lastPumpNotConnectedSignalAtMs = Long.MIN_VALUE
            postProfileConnectionInitializationComplete = false
            if (needsBolusRecoveryOnConnect()) {
                beginBolusFastLane("reconnect with unresolved bolus lifecycle")
                pump.command(TimeSinceResetRequest(), PumpCommandPriority.BOLUS)
            } else {
                startReconnectHydrationAndProfile()
            }
        }

        override fun onPumpModel(peripheral: BluetoothPeripheral?, model: KnownDeviceModel?) {
            super.onPumpModel(peripheral, model)
            Timber.i("service onPumpModel")
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_MODEL,
                model!!.name.toByteArray()
            )

            val modelName = when (model) {
                KnownDeviceModel.TSLIM_X2 -> "t:slim X2"
                KnownDeviceModel.MOBI -> "Tandem Mobi"
                else -> "Tandem Pump"
            }
            callbacks.prefSetPumpModelName(modelName)
        }

        override fun onPumpDisconnected(
            peripheral: BluetoothPeripheral?,
            status: HciStatus?
        ): Boolean {
            val expectedSuspendDisconnect = expectedPumpSuspendTransition.isActive() &&
                status == HciStatus.REMOTE_USER_TERMINATED_CONNECTION
            Timber.i("service onPumpDisconnected: isConnected=false")
            currentSession?.close()
            currentSession = null
            initialPumpProfileRead.reset()
            initialProfileReadRequestNonce += 1
            initialProfileReadRequestAttempts = 0
            postProfileConnectionInitializationComplete = false
            lastPeripheral = null
            callbacks.pumpCommState.lastResponseMessage.clear()
            historyLogFetcher?.cancel()
            historyLogFetcher = null
            historyLogSyncWorker?.stop()
            historyLogSyncWorker = null
            callbacks.pumpCommState.lastTimeSinceReset = null
            emptyCartridgeAlarmActive = false
            callbacks.pumpCommState.debugPromptResponseCounts.clear()
            isConnected = false
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_DISCONNECTED,
                peripheral?.name!!.toByteArray()
            )
            hydrationNonce += 1L
            reconnectLiveHydration.reset()
            publishHydrationState(PumpHydrationPhase.DISCONNECTED)
            callbacks.markConnectionTime()
            callbacks.updateNotification(
                if (expectedSuspendDisconnect) {
                    "Reconnecting after pump suspension"
                } else {
                    "Disconnected from pump"
                },
            )
            manualBolusOperations.onDisconnected()
            if (manualBolusReconciliationTracker != null ||
                pendingManualBolusNegativeEvidence != null
            ) {
                finishManualBolusReconciliation(
                    "pump disconnected during manual bolus reconciliation",
                )
            }
            quickBolusOperations.onDisconnected()
            bolusPermissionOperations.onDisconnected()
            bolusPermissionReconcileInFlight = false
            finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.DISCONNECTED)
            finishPendingManualBolusCancel(ManualBolusCancelOutcome.DISCONNECTED)
            finishPendingQuickBolusCancel(QuickBolusCancelOutcome.DISCONNECTED)
            if (expectedSuspendDisconnect) {
                Timber.i("Suppressing disconnect toast for expected pump suspend transition")
            } else {
                callbacks.showToast("Pump disconnected: $status", Toast.LENGTH_SHORT)
            }
            shutdownLatch?.countDown()
            return super.onPumpDisconnected(peripheral, status)
        }

        override fun onPumpCriticalError(peripheral: BluetoothPeripheral?, reason: TandemError?) {
            reason?.initiatingMessage?.let { currentSession?.onRequestFailed(it) }
            if (
                expectedPumpSuspendTransition.isActive() &&
                reason == TandemError.ERROR_RESPONSE &&
                reason.initiatingMessage?.characteristic == Characteristic.CURRENT_STATUS
            ) {
                Timber.i(
                    "Suppressing read-only pump error during expected suspend transition: " +
                        reason.initiatingMessage,
                )
                return
            }
            super.onPumpCriticalError(peripheral, reason)
            Timber.w("onPumpCriticalError $reason")
            val source = reason?.initiatingMessage?.let {
                if (callbacks.pumpCommState.consumeDebugPromptResponse(it.characteristic, it.responseOpCode)) {
                    com.jwoglom.controlx2.shared.util.SendType.DEBUG_PROMPT
                } else {
                    com.jwoglom.controlx2.shared.util.SendType.STANDARD
                }
            } ?: com.jwoglom.controlx2.shared.util.SendType.STANDARD
            reason?.let { callbacks.onPumpCriticalError(it, source = source) }
            callbacks.showToast("${reason?.name}: ${reason?.message}", Toast.LENGTH_LONG)
            callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CRITICAL_ERROR,
                reason?.message!!.toByteArray()
            )
        }

        @Synchronized
        fun command(
            message: com.jwoglom.pumpx2.pump.messages.Message?,
            priority: PumpCommandPriority = message
                ?.let(PumpSession::defaultPriority)
                ?: PumpCommandPriority.STATUS,
        ) {
            if (message == null) {
                Timber.w("Not sending null message")
                return
            }

            if (lastPeripheral == null) {
                Timber.w("Not sending message because no saved peripheral yet: $message")
                return
            }

            if (!isConnected) {
                Timber.w("Not sending message because no onConnected event yet: $message")
                return
            }

            val session = currentSession
            if (session == null) {
                Timber.w("Not sending message because no active session: $message")
                return
            }

            Timber.i("Pump send command: $message")
            val beginsSuspendTransition = message is SuspendPumpingRequest
            if (beginsSuspendTransition) {
                expectedPumpSuspendTransition.begin()
            }
            val sent = runBlocking { session.sendCommand(message, priority) }
            if (beginsSuspendTransition) {
                if (sent) {
                    // PumpSession applies the pause only after this command reaches the radio.
                    // Applying it here would pause the newly queued suspend request itself.
                } else {
                    expectedPumpSuspendTransition.clear()
                }
            }
        }

        override fun toString(): String {
            return "Pump(isConnected=$isConnected, lastPeripheral=$lastPeripheral)"
        }
    }

    private fun onReceiveInitiateBolusResponse(response: InitiateBolusResponse?) {
        response?.let {
            manualBolusOperations.operation
                ?.identity
                ?.takeIf { identity -> identity.bolusId == it.bolusId }
                ?.let { identity ->
                    manualBolusOperations.onInitiationResponse(
                        identity = identity,
                        accepted = it.wasBolusInitiated(),
                    )
                    bolusPermissionOperations.onInitiationResponse(
                        identity = identity,
                        accepted = it.wasBolusInitiated(),
                    )
                    if (it.wasBolusInitiated()) {
                        val postRecordDispatched = dispatchAcceptedBolusHistoryRecords(it.bolusId)
                        if (!postRecordDispatched) {
                            pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
                        }
                    } else {
                        bolusPreparationRequested = false
                        callbacks.clearPostBolusRecordPlan()
                        endBolusFastLane("initiation rejected")
                    }
                }
        }
        callbacks.broadcastBolusInitiateResponse(PumpMessageSerializer.toBytes(response))
    }

    private fun dispatchAcceptedBolusHistoryRecords(bolusId: Int): Boolean {
        val plan = callbacks.loadPostBolusRecordPlan() ?: return false
        if (plan.bolusId != bolusId) {
            Timber.e(
                "Discarding post-bolus record plan for bolusId=${plan.bolusId}; " +
                    "pump accepted bolusId=$bolusId",
            )
            callbacks.clearPostBolusRecordPlan()
            return false
        }
        if (plan.phase != PostBolusRecordPhase.STAGED) {
            Timber.w(
                "Ignoring duplicate post-bolus dispatch for bolusId=$bolusId phase=${plan.phase}",
            )
            return plan.phase == PostBolusRecordPhase.BG_RESPONSE_PENDING ||
                plan.phase == PostBolusRecordPhase.CARB_RESPONSE_PENDING
        }
        val pumpTime = currentPumpTimeSecondsSinceReset()
        if (pumpTime == null) {
            Timber.w(
                "Post-bolus records retained without dispatch because pump time is unavailable",
            )
            return false
        }

        return applyPostBolusRecordTransition(plan.onInitiationAccepted(pumpTime))
    }

    private fun onReceivePostBolusBgEntryResponse(response: RemoteBgEntryResponse) {
        val plan = callbacks.loadPostBolusRecordPlan() ?: return
        val transition = plan.onBgResponse(response.isSuccess)
        if (!transition.handled) return

        Timber.i(
            "Post-bolus BG response status=${response.status} bolusId=${plan.bolusId}",
        )
        if (!applyPostBolusRecordTransition(transition)) {
            pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
        }
    }

    private fun onReceivePostBolusCarbEntryResponse(response: RemoteCarbEntryResponse) {
        val plan = callbacks.loadPostBolusRecordPlan() ?: return
        val transition = plan.onCarbResponse(response.status == 0)
        if (!transition.handled) return

        Timber.i(
            "Post-bolus carb response status=${response.status} bolusId=${plan.bolusId}",
        )
        applyPostBolusRecordTransition(transition)
        pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
    }

    /**
     * Persists the next lifecycle phase before placing its command on the BLE queue. A pending
     * phase is deliberately not auto-replayed after process/radio loss because the command outcome
     * would be ambiguous and a replay could duplicate a pump history record.
     */
    private fun applyPostBolusRecordTransition(transition: PostBolusRecordTransition): Boolean {
        if (!transition.handled) return false
        val plan = transition.persistedPlan
        if (plan == null) {
            if (!callbacks.clearPostBolusRecordPlan()) {
                Timber.w("Unable to clear completed post-bolus record plan")
            }
        } else if (!callbacks.persistPostBolusRecordPlan(plan)) {
            Timber.e(
                "Refusing post-bolus record command because phase persistence failed: " +
                    "bolusId=${plan.bolusId} phase=${plan.phase}",
            )
            return false
        }

        return when (transition.nextCommand) {
            PostBolusRecordCommand.SEND_BG -> {
                val next = requireNotNull(plan)
                val bg = requireNotNull(next.bgMgdl)
                val pumpTime = requireNotNull(next.pumpTimeSecondsSinceReset)
                val automatic = next.bgRecordType == BolusBgRecordType.AUTOMATIC
                pump.command(
                    RemoteBgEntryRequest(
                        bg,
                        false,
                        if (automatic) {
                            BloodGlucoseReadingType.AUTO
                        } else {
                            BloodGlucoseReadingType.MANUAL
                        },
                        if (automatic) {
                            BloodGlucoseReadingSource.REMOTE
                        } else {
                            BloodGlucoseReadingSource.PUMP
                        },
                        pumpTime,
                        next.bolusId,
                    ),
                    PumpCommandPriority.BOLUS,
                )
                true
            }
            PostBolusRecordCommand.SEND_CARBS -> {
                val next = requireNotNull(plan)
                pump.command(
                    RemoteCarbEntryRequest(
                        next.carbsGrams,
                        requireNotNull(next.pumpTimeSecondsSinceReset),
                        next.bolusId,
                    ),
                    PumpCommandPriority.BOLUS,
                )
                true
            }
            PostBolusRecordCommand.NONE -> false
        }
    }

    private var lastBolusStatusId: Int? = null

    private fun onReceiveCurrentBolusStatusResponse(response: CurrentBolusStatusResponse?) {
        if (response != null) {
            val reconciliationAttempt = manualBolusReconciliationAttempt
            if (reconciliationAttempt != null) {
                manualBolusReconciliationTracker?.onCurrentBolus(
                    reconciliationAttempt,
                    response.bolusId,
                )
            }
            if (response.isActiveManualBolus()) {
                currentManualBolusIdentity(response.bolusId)?.let { identity ->
                    manualBolusOperations.onActiveEvidence(identity, response.requestedVolume)
                    if (manualBolusReconciliationTracker?.identity == identity &&
                        manualBolusOperations.operation?.phase in setOf(
                            ManualBolusOperationPhase.ACTIVE,
                            ManualBolusOperationPhase.CANCELLATION_NOT_COMPLETED,
                        )
                    ) {
                        // Exact active evidence resolves ambiguity but delivery remains active, so
                        // invalidate recovery reads without releasing the bolus fast lane.
                        clearManualBolusReconciliation()
                    }
                }
            } else if (callbacks.quickBolusLifecycleEnabled() && response.isActiveQuickBolus()) {
                currentOrNewQuickBolusIdentity(response.bolusId)?.let { identity ->
                    quickBolusOperations.observeActive(identity, response.requestedVolume)
                    beginBolusFastLane("pump-button Quick Bolus active")
                }
            }
            val shouldBroadcast = response.bolusId != 0 ||
                (response.bolusId == 0 && lastBolusStatusId != null && lastBolusStatusId != 0) ||
                quickBolusOperations.operation?.isUnresolved == true

            if (shouldBroadcast) {
                callbacks.broadcastBolusStatusUpdate(PumpMessageSerializer.toBytes(response))
            }

            lastBolusStatusId = response.bolusId

            if (response.bolusId == 0) {
                if (awaitingAutomaticBolusTerminal) {
                    awaitingAutomaticBolusTerminal = false
                    endBolusFastLane("automatic bolus reached terminal status")
                }
                val unresolvedManual = manualBolusOperations.operation?.isUnresolved == true
                val sid = getPumpSid()
                val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
                if (sid != null && sessionId != null && sessionId > 0L) {
                    quickBolusOperations.onCurrentCleared(sid, sessionId)
                }
                if (unresolvedManual) {
                    val apiVersion = PumpState.getPumpAPIVersion()
                        ?: com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.API_V2_5.get()
                    val lastRequest = if (apiVersion.compareTo(
                            com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.MOBI_API_V3_5,
                        ) >= 0
                    ) {
                        LastBolusStatusV3Request()
                    } else {
                        com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder
                            .create(apiVersion)
                    }
                    pump.command(lastRequest, PumpCommandPriority.BOLUS)
                }
                requestQuickBolusTerminalEvidenceIfDue()
                this.postDelayed({
                    if (lastBolusStatusId == 0) {
                        lastBolusStatusId = null
                    }
                }, 2000)
            }

            validatePendingAutomaticBolusCancellation(response)
            validatePendingManualBolusCancellation(response)
            validatePendingQuickBolusCancellation(response)
            evaluateManualBolusReconciliation()
        }
    }

    private fun onReceiveLastBolusStatusResponse(response: LastBolusStatusAbstractResponse) {
        manualBolusReconciliationAttempt?.let { attempt ->
            manualBolusReconciliationTracker?.onLastBolusObserved(attempt)
        }
        when (response.bolusSource) {
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS ->
                currentManualBolusIdentity(response.bolusId)?.let { identity ->
                    manualBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = response.deliveredVolume,
                        terminalStatusId = response.bolusStatusId,
                    )
                    finishManualBolusReconciliation("manual bolus reached terminal status")
                }
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS ->
                currentQuickBolusIdentity(response.bolusId)?.let { identity ->
                    quickBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = response.deliveredVolume,
                        terminalStatusId = response.bolusStatusId,
                    )
                    endBolusFastLane("Quick Bolus reached terminal status")
                }
            else -> Unit
        }
        evaluateManualBolusReconciliation()
    }

    private fun onReceiveLastBolusStatusV3Response(response: LastBolusStatusV3Response) {
        manualBolusReconciliationAttempt?.let { attempt ->
            manualBolusReconciliationTracker?.onLastBolusObserved(attempt)
        }
        if (!response.isStandardBolusPresent) {
            evaluateManualBolusReconciliation()
            return
        }
        when (response.standardBolusSource) {
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS ->
                currentManualBolusIdentity(response.standardBolusId)?.let { identity ->
                    manualBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = response.standardBolusDeliveredVolume,
                        terminalStatusId = response.standardBolusStatusId,
                        terminalEndReasonId = response.standardBolusEndReasonId,
                    )
                    attachActiveTerminalAlarm()
                    finishManualBolusReconciliation("manual bolus reached terminal status")
                }
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS ->
                currentQuickBolusIdentity(response.standardBolusId)?.let { identity ->
                    quickBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = response.standardBolusDeliveredVolume,
                        terminalStatusId = response.standardBolusStatusId,
                        terminalEndReasonId = response.standardBolusEndReasonId,
                    )
                    endBolusFastLane("Quick Bolus reached terminal status")
                }
            else -> Unit
        }
        evaluateManualBolusReconciliation()
    }

    private fun onReceiveBolusDeliveryHistoryLog(log: BolusDeliveryHistoryLog) {
        // The first delivery-history record is emitted when delivery begins.  It is not terminal
        // evidence and must not hide the active progress panel.
        if (log.bolusDeliveryStatusId == 1 && log.deliveredTotal == 0) return
        when (log.bolusSource) {
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS ->
                currentManualBolusIdentity(log.bolusID)?.let { identity ->
                    manualBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = log.deliveredTotal.toLong(),
                        terminalStatusId = log.bolusDeliveryStatusId,
                    )
                    attachActiveTerminalAlarm()
                    finishManualBolusReconciliation("manual bolus history reconciled")
                }
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS ->
                currentQuickBolusIdentity(log.bolusID)?.let { identity ->
                    quickBolusOperations.onTerminalEvidence(
                        identity = identity,
                        deliveredMilliunits = log.deliveredTotal.toLong(),
                        terminalStatusId = log.bolusDeliveryStatusId,
                    )
                    endBolusFastLane("Quick Bolus history reconciled")
                }
            else -> Unit
        }
    }

    private fun onReceiveBolusCompletedHistoryLog(log: BolusCompletedHistoryLog) {
        currentManualBolusIdentity(log.bolusId)?.let { identity ->
            manualBolusOperations.onTerminalEvidence(
                identity = identity,
                deliveredMilliunits = (log.insulinDelivered.toDouble() * 1_000.0).roundToLong(),
                terminalStatusId = log.completionStatusId,
                terminalCompletionStatusId = log.completionStatusId,
            )
            attachActiveTerminalAlarm()
            finishManualBolusReconciliation("manual bolus completion history reconciled")
        }
        currentQuickBolusIdentity(log.bolusId)?.let { identity ->
            quickBolusOperations.onTerminalEvidence(
                identity = identity,
                deliveredMilliunits = (log.insulinDelivered.toDouble() * 1_000.0).roundToLong(),
                terminalStatusId = log.completionStatusId,
                terminalCompletionStatusId = log.completionStatusId,
            )
            endBolusFastLane("Quick Bolus completion history reconciled")
        }
    }

    private fun onReceiveAlarmStatusResponse(response: AlarmStatusResponse) {
        emptyCartridgeAlarmActive =
            AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM in response.alarms
        attachActiveTerminalAlarm()
    }

    private fun attachActiveTerminalAlarm() {
        if (emptyCartridgeAlarmActive) {
            manualBolusOperations.onTerminalAlarmEvidence(
                AlarmStatusResponse.AlarmResponseType.EMPTY_CARTRIDGE_ALARM.bitmask().toLong(),
            )
        }
    }

    private fun currentManualBolusIdentity(bolusId: Int): ManualBolusIdentity? {
        val operationIdentity = manualBolusOperations.operation?.identity ?: return null
        val sid = getPumpSid() ?: return null
        val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId() ?: return null
        return operationIdentity.takeIf {
            it.pumpSid == sid &&
                pumpSessionIdsMatch(it.pumpSessionId, sessionId) &&
                it.bolusId == bolusId
        }
    }

    private fun currentOrNewQuickBolusIdentity(bolusId: Int): QuickBolusIdentity? {
        val sid = getPumpSid() ?: return null
        val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId() ?: return null
        if (bolusId <= 0 || sessionId <= 0L) return null
        return currentQuickBolusIdentity(bolusId) ?: QuickBolusIdentity(sid, sessionId, bolusId)
    }

    private fun currentQuickBolusIdentity(bolusId: Int): QuickBolusIdentity? {
        val operationIdentity = quickBolusOperations.operation?.identity ?: return null
        val sid = getPumpSid() ?: return null
        val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId() ?: return null
        return operationIdentity.takeIf {
            it.pumpSid == sid &&
                pumpSessionIdsMatch(it.pumpSessionId, sessionId) &&
                it.bolusId == bolusId
        }
    }

    /**
     * Current Bolus can clear before Last Bolus advances. Keep reconciliation read-only and
     * bounded by the existing status poll, while admitting the two terminal-evidence reads to the
     * bolus fast lane. No modifying request is sent or replayed here.
     */
    private fun requestQuickBolusTerminalEvidenceIfDue() {
        val operation = quickBolusOperations.operation ?: return
        if (!operation.isUnresolved ||
            operation.phase != com.jwoglom.controlx2.shared.QuickBolusOperationPhase.RECONCILING_TERMINAL
        ) return
        if (!this::pump.isInitialized || currentSession?.isActive != true) return

        val now = SystemClock.elapsedRealtime()
        if (lastQuickBolusTerminalReadIdentity == operation.identity &&
            now - lastQuickBolusTerminalReadElapsedMillis < QuickBolusTerminalReconcileIntervalMs
        ) return
        lastQuickBolusTerminalReadIdentity = operation.identity
        lastQuickBolusTerminalReadElapsedMillis = now

        val apiVersion = PumpState.getPumpAPIVersion()
            ?: com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.API_V2_5.get()
        val lastRequest = if (apiVersion.compareTo(
                com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.MOBI_API_V3_5,
            ) >= 0
        ) {
            LastBolusStatusV3Request()
        } else {
            LastBolusStatusRequestBuilder.create(apiVersion)
        }
        pump.command(lastRequest, PumpCommandPriority.BOLUS)
        pump.command(HistoryLogStatusRequest(), PumpCommandPriority.BOLUS)
    }

    /** Fetch only the newest protocol-sized history batch while exact Quick Bolus evidence is due. */
    private fun requestQuickBolusTerminalHistory(response: HistoryLogStatusResponse): Boolean {
        val operation = quickBolusOperations.operation ?: return false
        if (!operation.isUnresolved ||
            operation.phase != com.jwoglom.controlx2.shared.QuickBolusOperationPhase.RECONCILING_TERMINAL
        ) return false

        val available = if (response.lastSequenceNum < response.firstSequenceNum) {
            0L
        } else {
            response.lastSequenceNum - response.firstSequenceNum + 1L
        }
        val count = minOf(available, QuickBolusTerminalHistoryBatchSize.toLong()).toInt()
        if (count > 0) {
            Timber.i(
                "Requesting newest $count history records for exact Quick Bolus terminal evidence " +
                    "identity=${operation.identity}",
            )
            pump.command(
                HistoryLogRequest(response.lastSequenceNum, count),
                PumpCommandPriority.BOLUS,
            )
        }
        return true
    }

    private fun requestManualBolusReconciliation() {
        val operation = manualBolusOperations.operation ?: return
        if (!operation.isUnresolved) {
            return
        }
        pendingManualBolusNegativeEvidence?.let { pending ->
            if (pending.identity == operation.identity &&
                SystemClock.elapsedRealtime() < pending.notBeforeElapsedRealtime
            ) {
                Timber.i("Manual bolus negative evidence is still settling; coalescing status check")
                return
            }
        }
        if (!this::pump.isInitialized || currentSession?.isActive != true) return
        val observedPumpSid = getPumpSid()
        val observedPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (observedPumpSid == null || observedPumpSessionId == null ||
            observedPumpSessionId <= 0L
        ) {
            Timber.w("Manual bolus reconciliation is waiting for a stable pump identity")
            return
        }
        if (!manualBolusOperations.onPumpSession(observedPumpSid, observedPumpSessionId)) {
            Timber.e(
                "Blocking manual bolus reconciliation against a different pump session: %s",
                operation.identity,
            )
            finishManualBolusReconciliation(
                "manual bolus reconciliation requires user review after pump-session change",
            )
            return
        }
        if (manualBolusReconciliationTracker?.identity == operation.identity &&
            manualBolusReconciliationAttempt != null
        ) {
            Timber.i("Coalescing duplicate manual bolus reconciliation request")
            return
        }

        val historyBoundary = operation.historySequenceAtOrBeforeInitiation
        if (historyBoundary == null) {
            Timber.w(
                "Legacy manual bolus has no pre-initiation history boundary; " +
                    "bounded reconciliation will require exact identity: %s",
                operation.identity,
            )
        }

        manualBolusReconciliationNonce += 1L
        val nonce = manualBolusReconciliationNonce
        val tracker = ManualBolusReconciliationTracker(
            identity = operation.identity,
            historySequenceAtOrBeforeInitiation = historyBoundary,
        )
        val attempt = tracker.beginAttempt()
        manualBolusReconciliationTracker = tracker
        manualBolusReconciliationAttempt = attempt

        Timber.w(
            "Reconciling persisted manual bolus from Current, Last, and bounded history: %s",
            operation.identity,
        )
        beginBolusFastLane("manual bolus reconciliation")
        pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
        pump.command(manualBolusLastStatusRequest(), PumpCommandPriority.BOLUS)
        pump.command(HistoryLogStatusRequest(), PumpCommandPriority.BOLUS)
        scheduleManualBolusReconciliationRetry(nonce, completedAttempts = 1)
    }

    private fun requestManualBolusTerminalHistory(response: HistoryLogStatusResponse): Boolean {
        val tracker = manualBolusReconciliationTracker ?: return false
        val attempt = manualBolusReconciliationAttempt ?: return false
        val operation = manualBolusOperations.operation ?: return false
        if (!operation.isUnresolved || operation.identity != tracker.identity) return false

        val requests = if (response.numEntries <= 0L) {
            tracker.onHistoryStatus(
                attempt,
                firstRetainedSequence = 1L,
                newestSequence = 0L,
            )
        } else {
            tracker.onHistoryStatus(
                attempt,
                firstRetainedSequence = response.firstSequenceNum,
                newestSequence = response.lastSequenceNum,
            )
        }
        Timber.i(
            "Manual bolus reconciliation history window requires ${requests.size} read(s)",
        )
        requests.firstOrNull()?.let { request ->
            pump.command(
                HistoryLogRequest(request.endSequence, request.count),
                PumpCommandPriority.BOLUS,
            )
        }
        evaluateManualBolusReconciliation()
        return true
    }

    /**
     * History range reads share one response opcode, so the transport correctly coalesces them.
     * Advance exactly one missing protocol-sized range after each stream response instead of
     * queueing multiple parameterized requests under the same response key.
     */
    private fun requestNextManualBolusHistoryChunk() {
        val tracker = manualBolusReconciliationTracker ?: return
        val attempt = manualBolusReconciliationAttempt ?: return
        val operation = manualBolusOperations.operation ?: return
        if (!operation.isUnresolved || operation.identity != tracker.identity) return
        tracker.missingHistoryRequests(attempt).firstOrNull()?.let { request ->
            pump.command(
                HistoryLogRequest(request.endSequence, request.count),
                PumpCommandPriority.BOLUS,
            )
        }
    }

    private fun observeManualBolusReconciliationHistory(log: HistoryLog) {
        val tracker = manualBolusReconciliationTracker ?: return
        val attempt = manualBolusReconciliationAttempt ?: return
        val operation = manualBolusOperations.operation ?: return
        if (!operation.isUnresolved || operation.identity != tracker.identity) return

        val matchingIdentity = when (log) {
            is BolusDeliveryHistoryLog -> operation.identity.takeIf {
                log.bolusID == operation.identity.bolusId &&
                    log.bolusSource ==
                    BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
            }
            is BolusCompletedHistoryLog ->
                operation.identity.takeIf { log.bolusId == operation.identity.bolusId }
            else -> null
        }
        tracker.onHistoryRecord(
            attempt,
            ManualBolusHistoryObservation(
                sequenceNumber = log.sequenceNum,
                manualBolusIdentity = matchingIdentity,
            ),
        )
    }

    private fun rememberPumpHistorySequenceBoundary(response: HistoryLogStatusResponse) {
        val pumpSid = getPumpSid() ?: return
        val pumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId() ?: return
        val newestSequence = response.lastSequenceNum
        if (pumpSessionId <= 0L || newestSequence !in 0L..0xFFFF_FFFFL) return
        val current = latestPumpHistorySequenceBoundary
        if (current == null || current.pumpSid != pumpSid ||
            !com.jwoglom.controlx2.shared.pumpSessionIdsMatch(
                current.pumpSessionId,
                pumpSessionId,
            ) || newestSequence >= current.newestSequence
        ) {
            latestPumpHistorySequenceBoundary = PumpHistorySequenceBoundary(
                pumpSid = pumpSid,
                pumpSessionId = pumpSessionId,
                newestSequence = newestSequence,
            )
        }
    }

    private fun historySequenceBoundaryFor(identity: ManualBolusIdentity): Long? =
        latestPumpHistorySequenceBoundary
            ?.takeIf {
                it.pumpSid == identity.pumpSid &&
                    com.jwoglom.controlx2.shared.pumpSessionIdsMatch(
                        it.pumpSessionId,
                        identity.pumpSessionId,
                    )
            }
            ?.newestSequence

    private fun evaluateManualBolusReconciliation() {
        val tracker = manualBolusReconciliationTracker ?: return
        val attempt = manualBolusReconciliationAttempt ?: return
        val operation = manualBolusOperations.operation ?: return
        if (!operation.isUnresolved) {
            finishManualBolusReconciliation("manual bolus reached terminal evidence")
            return
        }
        val observedPumpSid = getPumpSid()
        val observedPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (observedPumpSid == null || observedPumpSessionId == null ||
            observedPumpSessionId <= 0L ||
            observedPumpSid != tracker.identity.pumpSid ||
            !com.jwoglom.controlx2.shared.pumpSessionIdsMatch(
                tracker.identity.pumpSessionId,
                observedPumpSessionId,
            )
        ) {
            if (observedPumpSid != null && observedPumpSessionId != null &&
                observedPumpSessionId > 0L
            ) {
                manualBolusOperations.onPumpSession(observedPumpSid, observedPumpSessionId)
            }
            finishManualBolusReconciliation(
                "manual bolus reconciliation stopped after pump-session change",
            )
            return
        }
        when (tracker.resolution(attempt)) {
            ManualBolusReconciliationResult.COMPLETE_WITHOUT_MATCH -> {
                Timber.w(
                    "First/second grounded negative manual-bolus snapshot: %s",
                    operation.identity,
                )
                handleManualBolusNegativeEvidence(
                    ManualBolusNegativeEvidenceKind.GROUNDED_ABSENCE,
                    observedPumpSid,
                    observedPumpSessionId,
                )
            }
            ManualBolusReconciliationResult.COMPLETE_UNGROUNDED_WITHOUT_MATCH -> {
                if (operation.phase == ManualBolusOperationPhase.CANCELLATION_CONFIRMED &&
                    operation.historySequenceAtOrBeforeInitiation == null
                ) {
                    Timber.w(
                        "First/second legacy confirmed-cancel negative snapshot: %s",
                        operation.identity,
                    )
                    handleManualBolusNegativeEvidence(
                        ManualBolusNegativeEvidenceKind.LEGACY_CONFIRMED_ENDED,
                        observedPumpSid,
                        observedPumpSessionId,
                    )
                } else {
                    Timber.w(
                        "Legacy manual bolus lacks a grounded history boundary; user review required",
                    )
                }
            }
            ManualBolusReconciliationResult.COVERAGE_INSUFFICIENT ->
                Timber.w("Manual bolus history window does not yet span the operation")
            ManualBolusReconciliationResult.MATCH_FOUND -> {
                pendingManualBolusNegativeEvidence = null
                Timber.i("Manual bolus history contains the exact identity; awaiting terminal record")
            }
            ManualBolusReconciliationResult.WAITING -> Unit
        }
    }

    /**
     * Never clears an ambiguous operation from one potentially lagging status publication. The
     * same independently re-read negative result must be observed again after a settle interval.
     */
    private fun handleManualBolusNegativeEvidence(
        kind: ManualBolusNegativeEvidenceKind,
        pumpSid: Int,
        pumpSessionId: Long,
    ) {
        val operation = manualBolusOperations.operation ?: return
        val existing = pendingManualBolusNegativeEvidence
        if (existing?.identity == operation.identity && existing.kind == kind) {
            when (kind) {
                ManualBolusNegativeEvidenceKind.GROUNDED_ABSENCE ->
                    manualBolusOperations.onHistoryCoverageCompleteWithoutMatch(
                        pumpSid,
                        pumpSessionId,
                    )
                ManualBolusNegativeEvidenceKind.LEGACY_CONFIRMED_ENDED ->
                    manualBolusOperations.onLegacyCancellationEndedWithoutAmount(
                        pumpSid,
                        pumpSessionId,
                    )
            }
            finishManualBolusReconciliation(
                "two stable pump snapshots show no matching manual bolus",
            )
            return
        }

        val candidate = PendingManualBolusNegativeEvidence(
            identity = operation.identity,
            kind = kind,
            notBeforeElapsedRealtime = SystemClock.elapsedRealtime() +
                ManualBolusNegativeEvidenceSettleMs,
        )
        pendingManualBolusNegativeEvidence = candidate
        clearManualBolusReconciliation(keepNegativeEvidence = true)
        val settleNonce = manualBolusReconciliationNonce
        postDelayed({
            if (settleNonce != manualBolusReconciliationNonce ||
                pendingManualBolusNegativeEvidence != candidate ||
                manualBolusOperations.operation?.identity != candidate.identity ||
                manualBolusOperations.operation?.isUnresolved != true ||
                currentSession?.isActive != true
            ) return@postDelayed
            requestManualBolusReconciliation()
        }, ManualBolusNegativeEvidenceSettleMs)
    }

    private fun scheduleManualBolusReconciliationRetry(
        nonce: Long,
        completedAttempts: Int,
    ) {
        postDelayed({
            if (nonce != manualBolusReconciliationNonce) return@postDelayed
            val tracker = manualBolusReconciliationTracker ?: return@postDelayed
            val attempt = manualBolusReconciliationAttempt ?: return@postDelayed
            val operation = manualBolusOperations.operation
            if (operation?.isUnresolved != true) {
                finishManualBolusReconciliation("manual bolus reconciliation completed")
                return@postDelayed
            }

            if (manualBolusReconciliationReadsOutstanding()) {
                Timber.i(
                    "Manual bolus reconciliation reads are still queued/in flight; " +
                        "preserving attempt budget",
                )
                scheduleManualBolusReconciliationRetry(nonce, completedAttempts)
                return@postDelayed
            }

            evaluateManualBolusReconciliation()
            if (manualBolusOperations.operation?.isUnresolved != true) return@postDelayed
            if (completedAttempts >= ManualBolusReconciliationMaxAttempts) {
                Timber.w(
                    "Manual bolus reconciliation paused after $completedAttempts bounded read attempts",
                )
                finishManualBolusReconciliation(
                    "manual bolus reconciliation paused for user retry",
                )
                return@postDelayed
            }

            pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
            pump.command(manualBolusLastStatusRequest(), PumpCommandPriority.BOLUS)
            val missingHistory = tracker.missingHistoryRequests(attempt)
            if (missingHistory.isEmpty()) {
                pump.command(HistoryLogStatusRequest(), PumpCommandPriority.BOLUS)
            } else {
                missingHistory.firstOrNull()?.let { request ->
                    pump.command(
                        HistoryLogRequest(request.endSequence, request.count),
                        PumpCommandPriority.BOLUS,
                    )
                }
            }
            scheduleManualBolusReconciliationRetry(nonce, completedAttempts + 1)
        }, ManualBolusReconciliationRetryIntervalMs)
    }

    private fun finishManualBolusReconciliation(reason: String) {
        clearManualBolusReconciliation()
        endBolusFastLane(reason)
    }

    private fun clearManualBolusReconciliation(keepNegativeEvidence: Boolean = false) {
        manualBolusReconciliationNonce += 1L
        manualBolusReconciliationTracker = null
        manualBolusReconciliationAttempt = null
        if (!keepNegativeEvidence) {
            pendingManualBolusNegativeEvidence = null
        }
    }

    private fun manualBolusReconciliationReadsOutstanding(): Boolean {
        val session = currentSession ?: return false
        val historyProbe = HistoryLogRequest(1L, 1)
        return session.hasOutstandingResponseFor(CurrentBolusStatusRequest()) ||
            session.hasOutstandingResponseFor(manualBolusLastStatusRequest()) ||
            session.hasOutstandingResponseFor(HistoryLogStatusRequest()) ||
            session.hasOutstandingResponseFor(historyProbe)
    }

    private fun manualBolusLastStatusRequest(): com.jwoglom.pumpx2.pump.messages.Message {
        val apiVersion = PumpState.getPumpAPIVersion()
            ?: com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.API_V2_5.get()
        return if (apiVersion.compareTo(
                com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.MOBI_API_V3_5,
            ) >= 0
        ) {
            LastBolusStatusV3Request()
        } else {
            LastBolusStatusRequestBuilder.create(apiVersion)
        }
    }

    private fun requestQuickBolusReconciliation() {
        if (!this::pump.isInitialized || currentSession?.isActive != true) return
        Timber.w("Reconciling persisted Quick Bolus operation from pump-authoritative status")
        beginBolusFastLane("Quick Bolus reconciliation")
        pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
        val apiVersion = PumpState.getPumpAPIVersion()
            ?: com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.API_V2_5.get()
        val lastBolusRequest = if (
            apiVersion.compareTo(
                com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion.MOBI_API_V3_5,
            ) >= 0
        ) {
            LastBolusStatusV3Request()
        } else {
            com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder.create(apiVersion)
        }
        pump.command(lastBolusRequest, PumpCommandPriority.BOLUS)
    }

    private fun beginAutomaticBolusCancellation(identity: AutomaticBolusIdentity) {
        if (pendingManualBolusCancel != null || pendingQuickBolusCancel != null) {
            reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }
        pendingAutomaticBolusCancel?.let { pending ->
            if (pending.identity == identity) {
                Timber.w("Ignoring duplicate automatic bolus cancellation request: $identity")
            } else {
                reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.IDENTITY_MISMATCH)
            }
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }

        val session = currentSession
        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (session == null || !session.isActive || currentPumpSid == null) {
            reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (identity.pumpSid != currentPumpSid ||
            (currentPumpSessionId != null &&
                !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId))
        ) {
            reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }

        beginBolusFastLane("automatic bolus cancellation")
        val statusRequest = CurrentBolusStatusRequest()
        automaticBolusCancelNonce += 1
        val nonce = automaticBolusCancelNonce
        pendingAutomaticBolusCancel = PendingAutomaticBolusCancel(
            identity = identity,
            connectionToken = session.token,
            nonce = nonce,
            stage = AutomaticBolusCancelStage.VERIFYING,
            matchingVerificationReads = if (
                currentPumpSessionId != null &&
                !session.hasInFlightResponseFor(statusRequest)
            ) 1 else 0,
        )
        Timber.w("Verifying active automatic bolus before cancellation: $identity")
        if (currentPumpSessionId == null) {
            Timber.w("Pump session identity is still loading; prioritizing it for cancellation")
            pump.command(TimeSinceResetRequest(), PumpCommandPriority.URGENT_CANCEL)
        } else {
            pump.command(statusRequest, PumpCommandPriority.URGENT_CANCEL)
        }

        postDelayed({
            val pending = pendingAutomaticBolusCancel
            if (pending?.nonce == nonce) {
                val outcome = if (currentSession?.token != pending.connectionToken) {
                    AutomaticBolusCancelOutcome.DISCONNECTED
                } else {
                    AutomaticBolusCancelOutcome.TIMEOUT
                }
                finishPendingAutomaticBolusCancel(outcome)
            }
        }, BolusCancellationOperationTimeoutMs)
    }

    private fun validatePendingAutomaticBolusCancellation(response: CurrentBolusStatusResponse) {
        val pending = pendingAutomaticBolusCancel ?: return
        if (pending.stage != AutomaticBolusCancelStage.VERIFYING) return

        val session = currentSession
        if (session == null || !session.isActive || session.token != pending.connectionToken) {
            finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }

        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (currentPumpSid == null || currentPumpSessionId == null) {
            // A stale status response can arrive just before the prioritized TimeSinceReset
            // response during reconnect. It cannot authorize cancellation, but it also does not
            // mean the recovered secure session is disconnected.
            return
        }

        when (validateAutomaticBolusForCancellation(
            pending.identity,
            currentPumpSid,
            currentPumpSessionId,
            response,
        )) {
            AutomaticBolusValidation.ALREADY_ENDED ->
                finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.ALREADY_ENDED)
            AutomaticBolusValidation.IDENTITY_MISMATCH ->
                finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.IDENTITY_MISMATCH)
            AutomaticBolusValidation.MATCHED_ACTIVE_AUTOMATIC_BOLUS -> {
                if (pending.matchingVerificationReads == 0) {
                    // A periodic status request may already have been in flight when
                    // the user tapped Cancel. Require a second matching pump read so
                    // that a delayed pre-tap response cannot authorize cancellation.
                    pendingAutomaticBolusCancel = pending.copy(matchingVerificationReads = 1)
                    Timber.w("First automatic bolus verification matched; requesting confirmation read")
                    pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
                    return
                }

                pendingAutomaticBolusCancel = pending.copy(stage = AutomaticBolusCancelStage.CANCELLING)
                Timber.w("Pump revalidated the automatic bolus; sending one cancellation request: ${pending.identity}")
                try {
                    pump.command(
                        CancelBolusRequest(pending.identity.bolusId),
                        PumpCommandPriority.URGENT_CANCEL,
                    )
                } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                    Timber.e(e, "Automatic bolus cancellation blocked by pumpx2 safety gate")
                    finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.ACTIONS_DISABLED)
                } catch (e: Exception) {
                    Timber.e(e, "Automatic bolus cancellation command failed")
                    finishPendingAutomaticBolusCancel(AutomaticBolusCancelOutcome.PUMP_REJECTED)
                }
            }
        }
    }

    private fun beginManualBolusCancellation(identity: ManualBolusIdentity) {
        if (pendingAutomaticBolusCancel != null || pendingQuickBolusCancel != null) {
            reportManualBolusCancel(identity, ManualBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }
        pendingManualBolusCancel?.let { pending ->
            if (pending.identity == identity) {
                Timber.w("Ignoring duplicate manual bolus cancellation request: $identity")
            } else {
                reportManualBolusCancel(identity, ManualBolusCancelOutcome.IDENTITY_MISMATCH)
            }
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            reportManualBolusCancel(identity, ManualBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }

        val session = currentSession
        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (session == null || !session.isActive || currentPumpSid == null) {
            reportManualBolusCancel(identity, ManualBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (identity.pumpSid != currentPumpSid ||
            (currentPumpSessionId != null &&
                !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId))
        ) {
            reportManualBolusCancel(identity, ManualBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }

        // A user-confirmed fresh cancel must never reuse a pre-cancel status/history snapshot.
        clearManualBolusReconciliation()
        beginBolusFastLane("manual bolus cancellation")
        val statusRequest = CurrentBolusStatusRequest()
        manualBolusCancelNonce += 1
        val nonce = manualBolusCancelNonce
        pendingManualBolusCancel = PendingManualBolusCancel(
            identity = identity,
            connectionToken = session.token,
            nonce = nonce,
            stage = ManualBolusCancelStage.VERIFYING,
            matchingVerificationReads = if (
                currentPumpSessionId != null &&
                !session.hasInFlightResponseFor(statusRequest)
            ) 1 else 0,
        )
        Timber.w("Verifying active manual bolus before cancellation: $identity")
        if (currentPumpSessionId == null) {
            Timber.w("Pump session identity is still loading; prioritizing it for cancellation")
            pump.command(TimeSinceResetRequest(), PumpCommandPriority.URGENT_CANCEL)
        } else {
            pump.command(statusRequest, PumpCommandPriority.URGENT_CANCEL)
        }

        postDelayed({
            val pending = pendingManualBolusCancel
            if (pending?.nonce == nonce) {
                val outcome = if (currentSession?.token != pending.connectionToken) {
                    ManualBolusCancelOutcome.DISCONNECTED
                } else {
                    ManualBolusCancelOutcome.TIMEOUT
                }
                finishPendingManualBolusCancel(outcome)
            }
        }, BolusCancellationOperationTimeoutMs)
    }

    private fun validatePendingManualBolusCancellation(response: CurrentBolusStatusResponse) {
        val pending = pendingManualBolusCancel ?: return
        if (pending.stage != ManualBolusCancelStage.VERIFYING) return

        val session = currentSession
        if (session == null || !session.isActive || session.token != pending.connectionToken) {
            finishPendingManualBolusCancel(ManualBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            finishPendingManualBolusCancel(ManualBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }

        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (currentPumpSid == null || currentPumpSessionId == null) {
            return
        }

        when (validateManualBolusForCancellation(
            pending.identity,
            currentPumpSid,
            currentPumpSessionId,
            response,
        )) {
            ManualBolusValidation.ALREADY_ENDED -> {
                manualBolusOperations.onCancellationAlreadyEnded(pending.identity)
                finishPendingManualBolusCancel(ManualBolusCancelOutcome.ALREADY_ENDED)
            }
            ManualBolusValidation.IDENTITY_MISMATCH ->
                finishPendingManualBolusCancel(ManualBolusCancelOutcome.IDENTITY_MISMATCH)
            ManualBolusValidation.MATCHED_ACTIVE_MANUAL_BOLUS -> {
                if (pending.matchingVerificationReads == 0) {
                    pendingManualBolusCancel = pending.copy(matchingVerificationReads = 1)
                    Timber.w("First manual bolus verification matched; requesting confirmation read")
                    pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
                    return
                }

                pendingManualBolusCancel = pending.copy(stage = ManualBolusCancelStage.CANCELLING)
                Timber.w("Pump revalidated the manual bolus; sending one cancellation request: ${pending.identity}")
                try {
                    val sent = manualBolusOperations.beginCancellation(pending.identity) {
                        pump.command(
                            CancelBolusRequest(pending.identity.bolusId),
                            PumpCommandPriority.URGENT_CANCEL,
                        )
                    }
                    if (!sent) {
                        Timber.e("Manual bolus operation state blocked cancellation: ${pending.identity}")
                        finishPendingManualBolusCancel(ManualBolusCancelOutcome.IDENTITY_MISMATCH)
                    }
                } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                    Timber.e(e, "Manual bolus cancellation blocked by pumpx2 safety gate")
                    manualBolusOperations.markCancellationNotSent(pending.identity)
                    finishPendingManualBolusCancel(ManualBolusCancelOutcome.ACTIONS_DISABLED)
                } catch (e: Exception) {
                    Timber.e(e, "Manual bolus cancellation send outcome is unknown")
                    manualBolusOperations.markCancellationOutcomeUnknown(pending.identity)
                    finishPendingManualBolusCancel(ManualBolusCancelOutcome.TIMEOUT)
                }
            }
        }
    }

    private fun beginQuickBolusCancellation(identity: QuickBolusIdentity) {
        if (pendingAutomaticBolusCancel != null || pendingManualBolusCancel != null) {
            reportQuickBolusCancel(identity, QuickBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }
        pendingQuickBolusCancel?.let { pending ->
            if (pending.identity == identity) {
                Timber.w("Ignoring duplicate Quick Bolus cancellation request: $identity")
            } else {
                reportQuickBolusCancel(identity, QuickBolusCancelOutcome.IDENTITY_MISMATCH)
            }
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            reportQuickBolusCancel(identity, QuickBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }

        val session = currentSession
        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (session == null || !session.isActive || currentPumpSid == null) {
            reportQuickBolusCancel(identity, QuickBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (identity.pumpSid != currentPumpSid ||
            (currentPumpSessionId != null &&
                !pumpSessionIdsMatch(identity.pumpSessionId, currentPumpSessionId))
        ) {
            reportQuickBolusCancel(identity, QuickBolusCancelOutcome.IDENTITY_MISMATCH)
            return
        }

        beginBolusFastLane("Quick Bolus cancellation")
        val statusRequest = CurrentBolusStatusRequest()
        quickBolusCancelNonce += 1
        val nonce = quickBolusCancelNonce
        pendingQuickBolusCancel = PendingQuickBolusCancel(
            identity = identity,
            connectionToken = session.token,
            nonce = nonce,
            stage = QuickBolusCancelStage.VERIFYING,
            matchingVerificationReads = if (
                currentPumpSessionId != null && !session.hasInFlightResponseFor(statusRequest)
            ) 1 else 0,
        )
        Timber.w("Verifying active Quick Bolus before cancellation: $identity")
        if (currentPumpSessionId == null) {
            pump.command(TimeSinceResetRequest(), PumpCommandPriority.URGENT_CANCEL)
        } else {
            pump.command(statusRequest, PumpCommandPriority.URGENT_CANCEL)
        }

        postDelayed({
            val pending = pendingQuickBolusCancel
            if (pending?.nonce == nonce) {
                val outcome = if (currentSession?.token != pending.connectionToken) {
                    QuickBolusCancelOutcome.DISCONNECTED
                } else {
                    QuickBolusCancelOutcome.TIMEOUT
                }
                finishPendingQuickBolusCancel(outcome)
            }
        }, BolusCancellationOperationTimeoutMs)
    }

    private fun validatePendingQuickBolusCancellation(response: CurrentBolusStatusResponse) {
        val pending = pendingQuickBolusCancel ?: return
        if (pending.stage != QuickBolusCancelStage.VERIFYING) return

        val session = currentSession
        if (session == null || !session.isActive || session.token != pending.connectionToken) {
            finishPendingQuickBolusCancel(QuickBolusCancelOutcome.DISCONNECTED)
            return
        }
        if (!callbacks.prefInsulinDeliveryActions()) {
            finishPendingQuickBolusCancel(QuickBolusCancelOutcome.ACTIONS_DISABLED)
            return
        }
        val currentPumpSid = getPumpSid()
        val currentPumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
        if (currentPumpSid == null || currentPumpSessionId == null) return

        when (validateQuickBolusForCancellation(
            pending.identity,
            currentPumpSid,
            currentPumpSessionId,
            response,
        )) {
            QuickBolusValidation.ALREADY_ENDED ->
                finishPendingQuickBolusCancel(QuickBolusCancelOutcome.ALREADY_ENDED)
            QuickBolusValidation.IDENTITY_MISMATCH ->
                finishPendingQuickBolusCancel(QuickBolusCancelOutcome.IDENTITY_MISMATCH)
            QuickBolusValidation.MATCHED_ACTIVE_QUICK_BOLUS -> {
                if (pending.matchingVerificationReads == 0) {
                    pendingQuickBolusCancel = pending.copy(matchingVerificationReads = 1)
                    Timber.w("First Quick Bolus verification matched; requesting confirmation read")
                    pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
                    return
                }

                pendingQuickBolusCancel = pending.copy(stage = QuickBolusCancelStage.CANCELLING)
                Timber.w(
                    "Pump revalidated the Quick Bolus; sending one cancellation request: " +
                        pending.identity,
                )
                try {
                    val sent = quickBolusOperations.beginCancellation(pending.identity) {
                        pump.command(
                            CancelBolusRequest(pending.identity.bolusId),
                            PumpCommandPriority.URGENT_CANCEL,
                        )
                    }
                    if (!sent) {
                        finishPendingQuickBolusCancel(QuickBolusCancelOutcome.IDENTITY_MISMATCH)
                    }
                } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                    Timber.e(e, "Quick Bolus cancellation blocked by pumpx2 safety gate")
                    quickBolusOperations.markCancellationNotSent(pending.identity)
                    finishPendingQuickBolusCancel(QuickBolusCancelOutcome.ACTIONS_DISABLED)
                } catch (e: Exception) {
                    Timber.e(e, "Quick Bolus cancellation send outcome is unknown")
                    quickBolusOperations.markCancellationOutcomeUnknown(pending.identity)
                    finishPendingQuickBolusCancel(QuickBolusCancelOutcome.TIMEOUT)
                    requestQuickBolusReconciliation()
                }
            }
        }
    }

    private fun onReceiveCancelBolusResponse(response: CancelBolusResponse) {
        pendingAutomaticBolusCancel?.let { pending ->
            if (pending.stage == AutomaticBolusCancelStage.CANCELLING &&
                response.bolusId == pending.identity.bolusId
            ) {
                finishPendingAutomaticBolusCancel(
                    if (response.wasCancelled()) {
                        AutomaticBolusCancelOutcome.CONFIRMED
                    } else if (response.reason == CancelBolusResponse.CancelReason.INVALID_OR_ALREADY_DELIVERED) {
                        AutomaticBolusCancelOutcome.ALREADY_ENDED
                    } else {
                        AutomaticBolusCancelOutcome.PUMP_REJECTED
                    },
                )
                return
            }
        }

        pendingManualBolusCancel?.let { pending ->
            if (pending.stage == ManualBolusCancelStage.CANCELLING &&
                response.bolusId == pending.identity.bolusId
            ) {
                val outcome = if (response.wasCancelled()) {
                    ManualBolusCancelOutcome.CONFIRMED
                } else if (
                    response.reason ==
                    CancelBolusResponse.CancelReason.INVALID_OR_ALREADY_DELIVERED
                ) {
                    ManualBolusCancelOutcome.ALREADY_ENDED
                } else {
                    ManualBolusCancelOutcome.PUMP_REJECTED
                }
                manualBolusOperations.onCancellationResponse(
                    pending.identity,
                    outcome = outcome,
                )
                finishPendingManualBolusCancel(outcome)
                return
            }
        }

        pendingQuickBolusCancel?.let { pending ->
            if (pending.stage == QuickBolusCancelStage.CANCELLING &&
                response.bolusId == pending.identity.bolusId
            ) {
                quickBolusOperations.onCancellationResponse(
                    pending.identity,
                    accepted = response.wasCancelled(),
                )
                finishPendingQuickBolusCancel(
                    if (response.wasCancelled()) {
                        QuickBolusCancelOutcome.CONFIRMED
                    } else if (response.reason ==
                        CancelBolusResponse.CancelReason.INVALID_OR_ALREADY_DELIVERED
                    ) {
                        QuickBolusCancelOutcome.ALREADY_ENDED
                    } else {
                        QuickBolusCancelOutcome.PUMP_REJECTED
                    },
                )
            }
        }
    }

    private fun finishPendingAutomaticBolusCancel(outcome: AutomaticBolusCancelOutcome) {
        val pending = pendingAutomaticBolusCancel ?: return
        pendingAutomaticBolusCancel = null
        when (outcome) {
            AutomaticBolusCancelOutcome.CONFIRMED,
            AutomaticBolusCancelOutcome.TIMEOUT,
            AutomaticBolusCancelOutcome.PUMP_REJECTED,
            -> {
                awaitingAutomaticBolusTerminal = true
                if (currentSession?.isActive == true) {
                    pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.BOLUS)
                }
            }
            AutomaticBolusCancelOutcome.ALREADY_ENDED,
            AutomaticBolusCancelOutcome.IDENTITY_MISMATCH,
            AutomaticBolusCancelOutcome.ACTIONS_DISABLED,
            AutomaticBolusCancelOutcome.DISCONNECTED,
            -> endBolusFastLane("automatic bolus cancellation ended: $outcome")
        }
        reportAutomaticBolusCancel(pending.identity, outcome)
    }

    private fun reportAutomaticBolusCancel(
        identity: AutomaticBolusIdentity,
        outcome: AutomaticBolusCancelOutcome,
    ) {
        Timber.w("Automatic bolus cancellation result: identity=$identity outcome=$outcome")
        callbacks.broadcastAutomaticBolusCancelResult(
            AutomaticBolusCancelResult(identity, outcome).encode(),
        )
    }

    private fun finishPendingManualBolusCancel(outcome: ManualBolusCancelOutcome) {
        val pending = pendingManualBolusCancel ?: return
        if (pending.stage == ManualBolusCancelStage.CANCELLING &&
            outcome in setOf(
                ManualBolusCancelOutcome.DISCONNECTED,
                ManualBolusCancelOutcome.TIMEOUT,
            )
        ) {
            manualBolusOperations.markCancellationOutcomeUnknown(pending.identity, outcome)
        }
        pendingManualBolusCancel = null
        when (outcome) {
            ManualBolusCancelOutcome.CONFIRMED,
            ManualBolusCancelOutcome.ALREADY_ENDED,
            ManualBolusCancelOutcome.PUMP_REJECTED,
            ManualBolusCancelOutcome.TIMEOUT,
            -> requestManualBolusReconciliation()
            ManualBolusCancelOutcome.DISCONNECTED ->
                endBolusFastLane("manual bolus cancellation disconnected")
            ManualBolusCancelOutcome.IDENTITY_MISMATCH,
            ManualBolusCancelOutcome.ACTIONS_DISABLED,
            -> endBolusFastLane("manual bolus cancellation ended: $outcome")
        }
        reportManualBolusCancel(pending.identity, outcome)
    }

    private fun reportManualBolusCancel(
        identity: ManualBolusIdentity,
        outcome: ManualBolusCancelOutcome,
    ) {
        Timber.w("Manual bolus cancellation result: identity=$identity outcome=$outcome")
        callbacks.broadcastManualBolusCancelResult(
            ManualBolusCancelResult(identity, outcome).encode(),
        )
    }

    private fun finishPendingQuickBolusCancel(outcome: QuickBolusCancelOutcome) {
        val pending = pendingQuickBolusCancel ?: return
        if (pending.stage == QuickBolusCancelStage.CANCELLING &&
            outcome in setOf(
                QuickBolusCancelOutcome.DISCONNECTED,
                QuickBolusCancelOutcome.TIMEOUT,
            )
        ) {
            quickBolusOperations.markCancellationOutcomeUnknown(pending.identity)
        }
        pendingQuickBolusCancel = null
        if (outcome in setOf(
                QuickBolusCancelOutcome.IDENTITY_MISMATCH,
                QuickBolusCancelOutcome.ACTIONS_DISABLED,
            )
        ) {
            endBolusFastLane("Quick Bolus cancellation ended: $outcome")
        }
        reportQuickBolusCancel(pending.identity, outcome)
    }

    private fun reportQuickBolusCancel(
        identity: QuickBolusIdentity,
        outcome: QuickBolusCancelOutcome,
    ) {
        Timber.w("Quick Bolus cancellation result: identity=$identity outcome=$outcome")
        callbacks.broadcastQuickBolusCancelResult(
            QuickBolusCancelResult(identity, outcome).encode(),
        )
    }

    private fun onReceiveTimeSinceResetResponse(response: TimeSinceResetResponse?) {
        Timber.i("pumpCommState.lastTimeSinceReset = $response")
        callbacks.pumpCommState.lastTimeSinceReset = response
        val sid = getPumpSid()
        val sessionId = response?.pumpSessionId()
        if (manualBolusOperations.operation?.isUnresolved == true) {
            if (sid != null && sessionId != null && sessionId > 0L &&
                manualBolusOperations.onPumpSession(sid, sessionId)
            ) {
                requestManualBolusReconciliation()
            } else {
                finishManualBolusReconciliation(
                    "manual bolus recovery requires user review after identity check",
                )
            }
        }
        if (sid != null && sessionId != null && sessionId > 0L) {
            quickBolusOperations.onPumpSession(sid, sessionId)
            if (quickBolusOperations.operation?.isUnresolved == true) {
                requestQuickBolusReconciliation()
            }
        }
        if (sid != null && sessionId != null && sessionId > 0L) {
            executeBolusPermissionAction(
                bolusPermissionOperations.onPumpSession(
                    pumpSid = sid,
                    pumpSessionId = sessionId,
                    permissionRequestInFlight = bolusPermissionReconcileInFlight,
                ),
            )
        }
        if (bolusPreparationRequested) {
            continueBolusPreparationWithCurrentIdentity()
        }
        pendingAutomaticBolusCancel
            ?.takeIf { it.stage == AutomaticBolusCancelStage.VERIFYING }
            ?.let { pending ->
                pendingAutomaticBolusCancel = pending.copy(matchingVerificationReads = 1)
                pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
            }
        pendingManualBolusCancel
            ?.takeIf { it.stage == ManualBolusCancelStage.VERIFYING }
            ?.let { pending ->
                pendingManualBolusCancel = pending.copy(matchingVerificationReads = 1)
                pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
            }
        pendingQuickBolusCancel
            ?.takeIf { it.stage == QuickBolusCancelStage.VERIFYING }
            ?.let { pending ->
                pendingQuickBolusCancel = pending.copy(matchingVerificationReads = 1)
                pump.command(CurrentBolusStatusRequest(), PumpCommandPriority.URGENT_CANCEL)
            }
    }

    private fun executeBolusPermissionAction(action: BolusPermissionAction) {
        when (action) {
            BolusPermissionAction.None -> Unit
            BolusPermissionAction.RequestPermission -> {
                if (!bolusPermissionReconcileInFlight && pumpConnectedPrecondition()) {
                    bolusPermissionReconcileInFlight = true
                    Timber.i("Reconciling outstanding bolus permission before cleanup")
                    pump.command(BolusPermissionRequest(), PumpCommandPriority.BOLUS)
                }
            }
            is BolusPermissionAction.RecordBg -> {
                if (pumpConnectedPrecondition()) {
                    val sent = bolusPermissionOperations.beginBgEntry(action) {
                        Timber.i(
                            "Recording abandoned-bolus BG bg=${action.bgMgdl} " +
                                "type=${action.recordType} bolusId=${action.bolusId} " +
                                "before permission release",
                        )
                        pump.command(
                            RemoteBgEntryRequest(
                                action.bgMgdl,
                                false,
                                if (action.recordType == BolusBgRecordType.AUTOMATIC) {
                                    BloodGlucoseReadingType.AUTO
                                } else {
                                    BloodGlucoseReadingType.MANUAL
                                },
                                if (action.recordType == BolusBgRecordType.AUTOMATIC) {
                                    BloodGlucoseReadingSource.REMOTE
                                } else {
                                    BloodGlucoseReadingSource.PUMP
                                },
                                action.pumpTimeSeconds,
                                action.bolusId,
                            ),
                            PumpCommandPriority.BOLUS,
                        )
                    }
                    if (sent) {
                        abandonedBolusBgNonce += 1
                        val nonce = abandonedBolusBgNonce
                        postDelayed({
                            val operation = bolusPermissionOperations.operation
                            if (abandonedBolusBgNonce == nonce &&
                                operation?.bolusId == action.bolusId &&
                                operation.phase ==
                                com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING
                            ) {
                                Timber.w(
                                    "Abandoned-bolus BG response timed out; " +
                                        "reconciling permission before release",
                                )
                                bolusPermissionOperations.markAutomaticBgEntryOutcomeUnknown()
                                val sid = getPumpSid()
                                val sessionId = callbacks.pumpCommState.lastTimeSinceReset
                                    ?.pumpSessionId()
                                if (sid != null && sessionId != null && sessionId > 0L) {
                                    executeBolusPermissionAction(
                                        bolusPermissionOperations.onPumpSession(sid, sessionId),
                                    )
                                }
                            }
                        }, 8_000L)
                    }
                }
            }
            is BolusPermissionAction.ReleasePermission -> {
                if (pumpConnectedPrecondition()) {
                    val sent = bolusPermissionOperations.beginRelease(action.bolusId) {
                        Timber.i("Releasing durable bolus permission bolusId=${action.bolusId}")
                        pump.command(
                            BolusPermissionReleaseRequest(action.bolusId),
                            PumpCommandPriority.BOLUS,
                        )
                    }
                    if (sent) {
                        bolusPermissionReleaseNonce += 1
                        val nonce = bolusPermissionReleaseNonce
                        postDelayed({
                            val operation = bolusPermissionOperations.operation
                            if (bolusPermissionReleaseNonce == nonce &&
                                operation?.bolusId == action.bolusId &&
                                operation.phase ==
                                com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.RELEASE_SENDING
                            ) {
                                Timber.w("Bolus permission release reply timed out; reconciling before retry")
                                bolusPermissionOperations.markReleaseOutcomeUnknown()
                                val sid = getPumpSid()
                                val sessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
                                if (sid != null && sessionId != null && sessionId > 0L) {
                                    executeBolusPermissionAction(
                                        bolusPermissionOperations.onPumpSession(sid, sessionId),
                                    )
                                }
                            }
                        }, 8_000L)
                    }
                }
            }
        }
    }

    /**
     * Advances the last pump clock snapshot by its local receive age. This matches the official
     * app's abandonment trace more closely than reusing the workflow-entry timestamp.
     */
    private fun currentPumpTimeSecondsSinceReset(): Long? {
        val response = callbacks.pumpCommState.lastTimeSinceReset ?: return null
        val receivedAt = callbacks.pumpCommState.lastResponseMessage[
            Pair(Characteristic.CURRENT_STATUS, TimeSinceResetResponse().opCode()),
        ]?.second
        val elapsedSeconds = receivedAt
            ?.let { Duration.between(it, Instant.now()).seconds.coerceAtLeast(0L) }
            ?: 0L
        return (response.pumpTimeSecondsSinceReset + elapsedSeconds)
            .takeIf { it in 1L..0xFFFF_FFFFL }
    }

    private fun pumpConnectedPrecondition(checkConnected: Boolean = true): Boolean {
        if (!this::pump.isInitialized) {
            Timber.e("pumpConnectedPrecondition: pump not initialized")
            reportPumpNotConnected("not_initialized")
        } else if (pump.lastPeripheral == null) {
            Timber.e("pumpConnectedPrecondition: pump not saved peripheral")
            ensurePumpConnection(foregroundRequest = false)
            reportPumpNotConnected("null_peripheral")
        } else if (checkConnected && !pump.isConnected) {
            Timber.e("pumpConnectedPrecondition: pump not connected")
            ensurePumpConnection(foregroundRequest = false)
            reportPumpNotConnected("not_connected")
        } else {
            return true
        }
        return false
    }

    private fun isBolusCommand(message: com.jwoglom.pumpx2.pump.messages.Message): Boolean {
        return (message is InitiateBolusRequest) || (message.opCode() == InitiateBolusRequest().opCode() && message.characteristic == Characteristic.CONTROL)
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            CommServiceCodes.INIT_PUMP_COMM.ordinal -> {
                if (pumpConnectedPrecondition(checkConnected = false)) {
                    Timber.w("pumpCommHandler: init_pump_comm already run, ignoring")
                    return
                }
                try {
                    var pairingCodeType: PairingCodeType? = null
                    var filterToBluetoothMac: String? = null
                    if (msg.obj != null && msg.obj != "") {
                        var parts = (msg.obj as String).split(" ")
                        pairingCodeType = PairingCodeType.fromLabel(parts[0])
                        if (parts.size == 2) {
                            filterToBluetoothMac = parts[1]
                        }
                    }

                    if (!ensurePumpUnbondedForFreshInit(filterToBluetoothMac)) {
                        return
                    }
                    Timber.i("pumpCommHandler: init_pump_comm: msg.obj=${msg.obj as String} pairingCodeType=$pairingCodeType filterToBluetoothMac=$filterToBluetoothMac")
                    val cfg = TandemConfig()
                        .withFilterToBluetoothMac(filterToBluetoothMac)
                        .withPairingCodeType(pairingCodeType)
                        // A Mobi can temporarily ignore a fresh authentication handshake while
                        // it is completing an active bolus. Keep recovering in the foreground
                        // instead of misclassifying three no-reply windows as an invalid pairing.
                        .withContinueInitialAuthNoReplyRetries(true)
                        .withInitialAuthNoReplyRecoveryDelayMs(15_000L)

                    pump = Pump(cfg)
                    tandemBTHandler =
                        TandemBluetoothHandler.getInstance(callbacks.getApplicationContext(), pump, null)
                } catch (e: SecurityException) {
                    Timber.e("pumpCommHandler: SecurityException starting pump $e")
                }
                while (true) {
                    try {
                        Timber.i("pumpCommHandler: Starting scan...")
                        tandemBTHandler.startScan()
                        break
                    } catch (e: SecurityException) {
                        Timber.e("pumpCommHandler: Waiting for BT permissions $e")
                        Thread.sleep(500)
                    }
                }
            }
            CommServiceCodes.ENSURE_PUMP_CONNECTION.ordinal -> {
                ensurePumpConnection(foregroundRequest = true)
            }
            CommServiceCodes.STOP_PUMP_COMM.ordinal -> {
                tandemBTHandler.stop()
            }
            CommServiceCodes.CANCEL_AUTOMATIC_BOLUS.ordinal -> {
                val identity = msg.obj as? AutomaticBolusIdentity
                if (identity == null) {
                    Timber.e("Invalid automatic bolus cancellation payload")
                } else if (!this::pump.isInitialized) {
                    reportAutomaticBolusCancel(identity, AutomaticBolusCancelOutcome.DISCONNECTED)
                } else {
                    beginAutomaticBolusCancellation(identity)
                }
            }
            CommServiceCodes.CANCEL_MANUAL_BOLUS.ordinal -> {
                val identity = msg.obj as? ManualBolusIdentity
                if (identity == null) {
                    Timber.e("Invalid manual bolus cancellation payload")
                } else if (!this::pump.isInitialized) {
                    reportManualBolusCancel(identity, ManualBolusCancelOutcome.DISCONNECTED)
                } else {
                    beginManualBolusCancellation(identity)
                }
            }
            CommServiceCodes.RECONCILE_MANUAL_BOLUS.ordinal -> {
                requestManualBolusReconciliation()
            }
            CommServiceCodes.CANCEL_QUICK_BOLUS.ordinal -> {
                val identity = msg.obj as? QuickBolusIdentity
                if (identity == null) {
                    Timber.e("Invalid Quick Bolus cancellation payload")
                } else if (!this::pump.isInitialized) {
                    reportQuickBolusCancel(identity, QuickBolusCancelOutcome.DISCONNECTED)
                } else {
                    beginQuickBolusCancellation(identity)
                }
            }
            CommServiceCodes.ACKNOWLEDGE_MANUAL_BOLUS_TERMINAL.ordinal -> {
                val acknowledgement = msg.obj as? ManualBolusTerminalAcknowledgement
                val current = manualBolusOperations.operation
                if (acknowledgement == null || current?.identity != acknowledgement.identity) {
                    Timber.e("Ignoring mismatched manual bolus terminal acknowledgement")
                } else {
                    when (acknowledgement.stage) {
                        ManualBolusTerminalAcknowledgementStage.ALARM ->
                            manualBolusOperations.acknowledgeTerminalAlarm(acknowledgement.identity)
                        ManualBolusTerminalAcknowledgementStage.RESULT ->
                            manualBolusOperations.acknowledgeTerminalResult(acknowledgement.identity)
                    }
                }
            }
            CommServiceCodes.ACKNOWLEDGE_QUICK_BOLUS_TERMINAL.ordinal -> {
                val identity = msg.obj as? QuickBolusIdentity
                val current = quickBolusOperations.operation
                if (identity == null || current?.identity != identity) {
                    Timber.e("Ignoring mismatched Quick Bolus terminal acknowledgement")
                } else {
                    quickBolusOperations.acknowledgeTerminalResult(identity)
                }
            }
            CommServiceCodes.PREPARE_BOLUS_WORKFLOW.ordinal -> {
                prepareBolusWorkflow()
            }
            CommServiceCodes.ACQUIRE_BOLUS_PERMISSION.ordinal -> {
                val pumpSid = getPumpSid()
                val pumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
                if (manualBolusOperations.operation?.isUnresolved == true) {
                    Timber.w(
                        "Blocking bolus permission request while a prior manual bolus is unresolved",
                    )
                    requestManualBolusReconciliation()
                    callbacks.sendWearCommMessage(
                        MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                        "unresolved_bolus_operation".toByteArray(),
                    )
                } else if (pumpSid == null || pumpSessionId == null || pumpSessionId <= 0L) {
                    Timber.e("Blocking bolus permission request because stable pump identity is unavailable")
                } else if (pumpConnectedPrecondition()) {
                    beginBolusFastLane("bolus permission acquisition")
                    val sent = bolusPermissionOperations.beginAcquisition(
                        pumpSid = pumpSid,
                        pumpSessionId = pumpSessionId,
                    ) {
                        bolusPermissionReconcileInFlight = true
                        try {
                            pump.command(BolusPermissionRequest())
                        } catch (t: Throwable) {
                            bolusPermissionReconcileInFlight = false
                            throw t
                        }
                    }
                    if (!sent) {
                        bolusPermissionOperations.operation?.let(
                            callbacks::persistBolusPermissionOperation,
                        )
                    }
                }
            }
            CommServiceCodes.RELEASE_BOLUS_PERMISSION.ordinal -> {
                bolusPreparationRequested = false
                val cleanupRequest = msg.obj as? AbandonedBolusCleanupRequest
                val action = bolusPermissionOperations.queueRelease(
                    request = cleanupRequest,
                    automaticBgPumpTimeSeconds = cleanupRequest?.bgMgdl
                        ?.let { currentPumpTimeSecondsSinceReset() },
                )
                executeBolusPermissionAction(action)
            }
            CommServiceCodes.CHECK_PUMP_CONNECTED.ordinal -> {
                if (pumpConnectedPrecondition()) {
                    callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED,
                        pump.lastPeripheral?.name!!.toByteArray()
                    )
                }
            }
            CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal -> {
                if (pumpConnectedPrecondition(checkConnected = false)) {
                    if (canDispatchPairingRetry(
                            hasPeripheral = pump.lastPeripheral != null,
                            pairingCodeType = PumpState.pairingCodeType,
                            hasCentralChallenge = pump.pairingCodeCentralChallenge != null,
                        )
                    ) {
                        Timber.i("sendPumpPairingMessage: running performPairing")
                        pump.performPairing(
                            pump.lastPeripheral,
                            pump.pairingCodeCentralChallenge,
                            true
                        )
                    } else {
                        Timber.w(
                            "sendPumpPairingMessage: cannot retry pairing: hasPeripheral=${pump.lastPeripheral != null} " +
                                "pairingCodeType=${PumpState.pairingCodeType} " +
                                "hasLegacyCentralChallenge=${pump.pairingCodeCentralChallenge != null}"
                        )
                    }
                }
            }
            CommServiceCodes.SEND_PUMP_COMMAND.ordinal -> {
                Timber.i("pumpCommHandler send command raw: ${String(msg.obj as ByteArray)}")
                val pumpMsg = PumpMessageSerializer.fromBytes(msg.obj as ByteArray)
                if (isBolusCommand(pumpMsg)) {
                    Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                } else if (pumpConnectedPrecondition()) {
                    Timber.i("pumpCommHandler send command: $pumpMsg")
                    pump.command(pumpMsg)
                }
            }
            CommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal -> {
                Timber.i("pumpCommHandler send commands raw: ${String(msg.obj as ByteArray)}")
                PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                    if (isBolusCommand(it)) {
                        Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                    } else if (pumpConnectedPrecondition()) {
                        Timber.i("pumpCommHandler send command: $it")
                        pump.command(it)
                    }
                }
            }
            CommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal -> {
                Timber.i("pumpCommHandler send commands bust cache raw: ${String(msg.obj as ByteArray)}")
                PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                    if (callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                        Timber.i("pumpCommHandler busted cache: $it")
                        callbacks.pumpCommState.lastResponseMessage.remove(Pair(it.characteristic, it.responseOpCode))
                    }
                    if (isBolusCommand(it)) {
                        Timber.e("SEND_PUMP_COMMAND blocked bolus command")
                    } else if (pumpConnectedPrecondition()) {
                        Timber.i("pumpCommHandler send command bust cache: $it")
                        pump.command(it)
                    }
                }
            }
            CommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal -> {
                Timber.i("pumpCommHandler cached pump commands raw: ${String(msg.obj as ByteArray)}")
                PumpMessageSerializer.fromBulkBytes(msg.obj as ByteArray).forEach {
                    if (callbacks.pumpCommState.lastResponseMessage.containsKey(Pair(it.characteristic, it.responseOpCode)) && !isBolusCommand(it)) {
                        val response = callbacks.pumpCommState.lastResponseMessage.get(Pair(it.characteristic, it.responseOpCode))
                        val ageSeconds = Duration.between(response?.second, Instant.now()).seconds
                        if (ageSeconds <= CacheSeconds) {
                            Timber.i("pumpCommHandler cached hit: $response")
                            callbacks.sendWearCommMessage(
                                MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE,
                                PumpMessageSerializer.toBytes(response?.first)
                            )
                        } else {
                            Timber.i("pumpCommHandler expired cache hit $ageSeconds sec: $response")
                            if (!isBolusCommand(it) && pumpConnectedPrecondition()) {
                                pump.command(it)
                            }
                        }
                    } else if (isBolusCommand(it)) {
                        Timber.e("CACHED_PUMP_COMMANDS_BULK blocked bolus command")
                    } else if (pumpConnectedPrecondition()) {
                        Timber.i("pumpCommHandler cached miss: $it")
                        pump.command(it)
                    }
                }
            }
            CommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal -> {
                Timber.i("pumpCommHandler send bolus raw: ${String(msg.obj as ByteArray)}")
                val secretKey = callbacks.getWearPrefs()?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, msg.obj as ByteArray)

                val messageOk = confirmedBolus.left
                val pumpMsg = confirmedBolus.right
                if (!messageOk) {
                    Timber.w("pumpCommHandler bolus invalid signature")
                    callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_BLOCKED_SIGNATURE, "WearCommHandler".toByteArray())
                } else if (!isBolusCommand(pumpMsg)) {
                    Timber.e("SEND_PUMP_COMMAND_BOLUS not a bolus command: $pumpMsg")
                } else if (pumpConnectedPrecondition()) {
                    if (!callbacks.prefInsulinDeliveryActions()) {
                        Timber.e("No insulin delivery messages enabled -- blocking bolus command $pumpMsg")
                        executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED, "from_self".toByteArray())
                        return
                    }
                    Timber.i("pumpCommHandler send bolus command with valid signature: $pumpMsg")
                    val initiateRequest = pumpMsg as InitiateBolusRequest
                    val pumpSid = getPumpSid()
                    val pumpSessionId = callbacks.pumpCommState.lastTimeSinceReset?.pumpSessionId()
                    if (pumpSid == null || pumpSessionId == null || pumpSessionId <= 0L) {
                        Timber.e("Blocking bolus initiation because stable pump identity is unavailable")
                        executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "missing_pump_identity".toByteArray(),
                        )
                        return
                    }
                    val identity = bolusPermissionOperations.grantedIdentity(
                        pumpSid = pumpSid,
                        observedPumpSessionId = pumpSessionId,
                        bolusId = initiateRequest.bolusID,
                    )
                    if (identity == null) {
                        Timber.e(
                            "Blocking bolus because no granted permission matches sid=%s session=%s bolusId=%s",
                            pumpSid,
                            pumpSessionId,
                            initiateRequest.bolusID,
                        )
                        val permission = bolusPermissionOperations.operation
                        if (permission?.phase ==
                            com.jwoglom.controlx2.shared.BolusPermissionOperationPhase.GRANTED &&
                            permission.pumpSid == pumpSid &&
                            pumpSessionIdsMatch(permission.pumpSessionId, pumpSessionId)
                        ) {
                            executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        }
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "invalid_bolus_permission".toByteArray(),
                        )
                        return
                    }
                    if (manualBolusOperations.operation?.isUnresolved == true) {
                        Timber.e("Blocking bolus because another operation is unresolved")
                        executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "unresolved_bolus_operation".toByteArray(),
                        )
                        return
                    }
                    if (!bolusPermissionOperations.markInitiationSubmitted(identity)) {
                        Timber.e("Blocking bolus because the durable permission does not match $identity")
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "invalid_bolus_permission".toByteArray(),
                        )
                        return
                    }
                    try {
                        manualBolusOperations.beginInitiation(
                            identity = identity,
                            requestedMilliunits = initiateRequest.totalVolume +
                                initiateRequest.extendedVolume,
                            historySequenceAtOrBeforeInitiation =
                                historySequenceBoundaryFor(identity),
                        ) {
                            pump.command(initiateRequest)
                        }
                        manualBolusInitiationNonce += 1
                        val nonce = manualBolusInitiationNonce
                        postDelayed({
                            val operation = manualBolusOperations.operation
                            if (manualBolusInitiationNonce == nonce &&
                                operation?.identity == identity &&
                                operation.phase == ManualBolusOperationPhase.INITIATION_SENDING
                            ) {
                                Timber.w("Bolus initiation reply timed out; reconciling without resending")
                                manualBolusOperations.markInitiationOutcomeUnknown(identity)
                                requestManualBolusReconciliation()
                            }
                        }, 8_000L)
                    } catch (e: Packetize.ActionsAffectingInsulinDeliveryNotEnabledInPumpX2Exception) {
                        Timber.e(e)
                        manualBolusOperations.markInitiationNotSent(identity)
                        bolusPermissionOperations.markInitiationNotSent(identity)
                        executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        callbacks.sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED, "from_pumpx2_lib".toByteArray())
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Blocking bolus because another operation is unresolved")
                        val operation = manualBolusOperations.operation
                        if (operation?.identity != identity ||
                            operation.phase != ManualBolusOperationPhase.INITIATION_SENDING
                        ) {
                            bolusPermissionOperations.markInitiationNotSent(identity)
                            executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        } else {
                            manualBolusOperations.markInitiationOutcomeUnknown(identity)
                            requestManualBolusReconciliation()
                        }
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "unresolved_bolus_operation".toByteArray(),
                        )
                    } catch (e: IllegalStateException) {
                        Timber.e(e, "Blocking bolus because another operation is unresolved")
                        val operation = manualBolusOperations.operation
                        if (operation?.identity != identity ||
                            operation.phase != ManualBolusOperationPhase.INITIATION_SENDING
                        ) {
                            bolusPermissionOperations.markInitiationNotSent(identity)
                            executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                        } else {
                            manualBolusOperations.markInitiationOutcomeUnknown(identity)
                            requestManualBolusReconciliation()
                        }
                        callbacks.sendWearCommMessage(
                            MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                            "unresolved_bolus_operation".toByteArray(),
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Bolus initiation send outcome is unknown")
                        manualBolusOperations.markInitiationOutcomeUnknown(identity)
                        requestManualBolusReconciliation()
                    }
                } else {
                    Timber.e("Authenticated bolus could not be submitted because the pump is disconnected")
                    executeBolusPermissionAction(bolusPermissionOperations.queueRelease())
                    callbacks.sendWearCommMessage(
                        MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                        "pump_disconnected_before_submission".toByteArray(),
                    )
                }
            }
            CommServiceCodes.DEBUG_WRITE_BT_CHARACTERISTIC.ordinal -> {
                Timber.i("pumpCommHandler debug_write_bt_characteristic: ${String(msg.obj as ByteArray)}")
                try {
                    val contents = JSONObject(String(msg.obj as ByteArray));
                    val uuidStr = contents.getString("characteristicUuid")
                    val uuid = UUID.fromString(uuidStr)

                    val valuesStr = contents.getJSONArray("valuesHex")
                    var valuesHex = ArrayList<ByteArray>()
                    for (i in 0..valuesStr.length()) {
                        valuesHex.add(Hex.decodeHex(valuesStr.getString(i)))
                    }

                    if (pumpConnectedPrecondition()) {
                        if (!callbacks.prefInsulinDeliveryActions()) {
                            return
                        }
                        valuesHex.forEach {
                            Packetize.txId.increment()
                            pump.lastPeripheral?.writeCharacteristic(
                                ServiceUUID.PUMP_SERVICE_UUID,
                                uuid,
                                it,
                                WriteType.WITH_RESPONSE
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
            CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal -> {
                val uuidStr = msg.obj as String
                if (pumpConnectedPrecondition(checkConnected = false)) {
                    if (uuidStr == "${CharacteristicUUID.AUTHORIZATION_CHARACTERISTICS}") {
                        Timber.w("writeCharacteristicFailedCallback: calling onPumpConnected pump=$pump from authorization error")
                        pump.onPumpConnected(pump.lastPeripheral)
                    } else {
                        Timber.w("writeCharacteristicFailedCallback: triggering disconnection from non-authorization error")
                        pump.lastPeripheral?.cancelConnection();
                        pump.lastPeripheral?.let { pump.onPumpDisconnected(it, null) }
                    }
                }
            }
            CommServiceCodes.DEBUG_GET_MESSAGE_CACHE.ordinal -> {
                Timber.i("pumpCommHandler debug get message cache: ${callbacks.pumpCommState}.lastResponseMessage")
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_DEBUG_MESSAGE_CACHE, PumpMessageSerializer.toDebugMessageCacheBytes(callbacks.pumpCommState.lastResponseMessage.values))
            }
            CommServiceCodes.DEBUG_GET_HISTORYLOG_CACHE.ordinal -> {
                Timber.i("pumpCommHandler debug get historylog cache: ${callbacks.pumpCommState}.historyLogCache")
                if (callbacks.pumpCommState.historyLogCache.size <= 100) {
                    callbacks.sendWearCommMessage(
                        MessagePaths.FROM_PUMP_DEBUG_HISTORYLOG_CACHE,
                        PumpMessageSerializer.toDebugHistoryLogCacheBytes(callbacks.pumpCommState.historyLogCache)
                    )
                } else {
                    callbacks.pumpCommState.historyLogCache.entries.toList().chunked(100).forEach {
                        callbacks.sendWearCommMessage(
                            MessagePaths.FROM_PUMP_DEBUG_HISTORYLOG_CACHE,
                            PumpMessageSerializer.toDebugHistoryLogCacheBytes(it.associate {
                                Pair(it.key, it.value)
                            })
                        )
                    }
                }
            }
        }
    }
}
