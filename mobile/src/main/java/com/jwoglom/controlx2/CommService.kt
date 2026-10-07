package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.pump.BleChangeReceiver
import com.jwoglom.controlx2.pump.BolusManager
import com.jwoglom.controlx2.pump.CommServiceCallbacks
import com.jwoglom.controlx2.pump.PumpCommHandler
import com.jwoglom.controlx2.pump.PumpCommState
import com.jwoglom.controlx2.pump.PumpFinderCommHandler
import com.jwoglom.controlx2.pump.PumpHistoryLogFetcher
import com.jwoglom.controlx2.pump.PumpHistoryLogSyncWorker
import com.jwoglom.controlx2.pump.PumpSession
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.sync.gdh.GdhMessageDispatcher
import com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
import com.jwoglom.controlx2.sync.xdrip.XdripMessageDispatcher
import com.jwoglom.controlx2.util.DataClientState
import com.jwoglom.controlx2.widget.MobiControlWidgetProvider
import com.jwoglom.controlx2.widget.currentEgvWidgetSample
import com.jwoglom.controlx2.widget.WidgetCgmPumpPhase
import com.jwoglom.controlx2.widget.WidgetCgmSessionObservation
import com.jwoglom.controlx2.widget.WidgetCgmSessionSource
import com.jwoglom.controlx2.widget.reconcileWidgetCgmSession
import com.jwoglom.controlx2.util.HistoryLogFetcher
import com.jwoglom.controlx2.util.HistoryLogSyncWorker
import com.jwoglom.pumpx2.pump.TandemError
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.controlx2.shared.AbandonedBolusCleanupRequest
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.ManualBolusTerminalAcknowledgement
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.AuthenticatedPhoneBolusSubmission
import com.jwoglom.controlx2.shared.PostBolusRecordPlan
import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.IntegrationGates
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.shared.messaging.StateKeys
import com.jwoglom.controlx2.shared.isActiveManualBolus
import com.jwoglom.controlx2.shared.isActiveQuickBolus
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.pumpSessionIdsMatch
import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.controlx2.shared.util.registerAppReloadShutdownHook
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.triggerAppReload
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.unregisterAppReloadShutdownHook
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.CurrentBatteryRequestBuilder
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HistoryLogStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.InsulinStatusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LastBolusStatusV3Request
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import java.time.Instant

internal fun periodicPumpStatusRequests(apiVersion: ApiVersion): List<Message> = listOf(
    CurrentBatteryRequestBuilder.create(apiVersion),
    ControlIQIOBRequest(),
    InsulinStatusRequest(),
) + pumpNotificationStatusRequests(apiVersion)

class CommService : Service(), CommServiceCallbacks {
    override val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main.immediate)

    private var serviceLooper: Looper? = null
    private var pumpCommHandler: PumpCommHandler? = null
    private var pumpFinderCommHandler: PumpFinderCommHandler? = null

    private lateinit var messageBus: MessageBus
    var httpDebugApiService: HttpDebugApiService? = null
        private set

    @androidx.annotation.VisibleForTesting
    internal fun setMessageBusForTesting(bus: MessageBus) {
        messageBus = bus
    }

    @androidx.annotation.VisibleForTesting
    internal fun simulateConnectedPump(peripheral: BluetoothPeripheral) {
        pumpCommHandler!!.simulateConnectedPump(peripheral)
    }

    private lateinit var bolusManager: BolusManager
    private val automaticBolusNotifier by lazy { AutomaticBolusNotifier(applicationContext) }
    private var activeManualBolusIdentity: ManualBolusIdentity? = null

    // Hook used by triggerAppReload to give pumpx2 a chance to drop the GATT
    // link cleanly before Runtime.exit(0). Held as a field so we can
    // unregister in onDestroy and avoid duplicate registrations on
    // service-restart cycles.
    private val appReloadShutdownHook: (Long) -> Unit = { timeoutMs ->
        try {
            val ok = pumpCommHandler?.shutdownAndAwaitDisconnect(timeoutMs) ?: false
            Timber.i("appReloadShutdownHook: graceful BLE shutdown ok=$ok timeoutMs=$timeoutMs")
        } catch (t: Throwable) {
            Timber.w(t, "appReloadShutdownHook: graceful BLE shutdown threw")
        }
    }

    private var serviceStatusAcknowledged = false
    private val serviceStatusTask = object : Runnable {
        override fun run() {
            if (!serviceStatusAcknowledged) {
                Timber.i("Periodically sending service status (not yet acknowledged)")
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED, "".toByteArray())
                } else {
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
                serviceLooper?.let { looper ->
                    Handler(looper).postDelayed(this, 2000)
                }
            }
        }
    }

    @androidx.annotation.VisibleForTesting
    override val pumpCommState = PumpCommState()

    val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }


    // PumpCommHandler extracted to pump/PumpCommHandler.kt
    // PumpFinderCommHandler extracted to pump/PumpFinderCommHandler.kt

    override fun sendWearCommMessage(path: String, message: ByteArray) {
        Timber.i("service sendMessage: $path ${MessagePaths.payloadForLog(path, message)}")
        messageBus.sendMessage(path, message, MessageBusSender.COMM_SERVICE)
    }

    // BleChangeReceiver and BondState extracted to pump/BleChangeReceiver.kt
    private var bleChangeReceiver = BleChangeReceiver()

    private val periodicUpdateIntervalMs: Long = 1000 * 60 * 5 // 5 minutes
    private var periodicUpdateTask: Runnable = Runnable {}
    init {
        periodicUpdateTask = Runnable {
            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)

            fun apiVersion(): ApiVersion {
                var apiVersion = PumpState.getPumpAPIVersion()
                if (apiVersion == null) {
                    apiVersion = KnownApiVersion.API_V2_5.get()
                }
                return apiVersion
            }

            Timber.i("running periodicUpdateTask")
            sendPumpCommMessages(
                PumpMessageSerializer.toBulkBytes(periodicPumpStatusRequests(apiVersion()))
            )
        }
    }

    // Qualifying events are the primary bolus trigger. This slower watchdog catches a missed
    // event without maintaining the former 2-second idle poll which congested normal traffic.
    private val automaticBolusMonitorIntervalMs: Long = 15_000L
    private val automaticBolusMonitorTask: Runnable = object : Runnable {
        override fun run() {
            pumpCommHandler?.postDelayed(this, automaticBolusMonitorIntervalMs)
            if (pumpCommHandler?.currentSession?.isActive == true) {
                pumpCommHandler?.requestBolusStatusWatchdog()
            }
        }
    }

    fun isPumpReadyForHistoryFetch(): Boolean {
        return pumpCommHandler?.isPumpReadyForHistoryFetch() == true
    }

    fun getPumpSession(): PumpSession? {
        return pumpCommHandler?.currentSession
    }

    override fun onCreate() {
        super.onCreate()

        // Timber already set in up MUA, but for good measure:
        setupTimber("MWC",
            context = this,
            logToFile = true,
            shouldLog = ShouldLogToFile(this),
            writeCharacteristicFailedCallback = handleWriteCharacteristicFailedCallback)
        Timber.i("service onCreate")

        registerAppReloadShutdownHook(appReloadShutdownHook)

        if (Prefs(applicationContext).developerPreviewEnabled()) {
            Timber.w("commService is short-circuiting in developer preview")
            stopSelf()
            return
        }

        // The Wear data layer auto-starts CommService whenever a /to-phone or
        // /from-pump message arrives (see AndroidManifest intent-filter). When
        // the phone is in CLIENT mode the watch is the pump-host and CommService
        // must NOT scan/bond — otherwise the OS pairing popup keeps appearing
        // and the pump's bond stays on the phone, blocking watch-side pairing.
        if (Prefs(applicationContext).deviceRole() != DeviceRole.PUMP_HOST) {
            Timber.w("commService is short-circuiting because deviceRole is CLIENT")
            stopSelf()
            return
        }

        // Listen to BLE state changes
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
        registerReceiver(bleChangeReceiver, intentFilter)


        if (!Prefs(applicationContext).tosAccepted()) {
            Timber.w("commService is short-circuiting because first TOS not accepted")
            return
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.w("commService is short-circuiting because service not enabled")
            return
        }

        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        val handlerThread = HandlerThread("PumpCommServiceThread", Process.THREAD_PRIORITY_FOREGROUND)
        handlerThread.start()
        Timber.d("service thread start")

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = handlerThread.looper

        messageBus = MessageBusFactory.createMessageBus(this)
        messageBus.addMessageListener(object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                handleMessageReceived(path, data, sourceNodeId)
            }
        })

        bolusManager = BolusManager(applicationContext, ::sendWearCommMessage)

        // Initialize and start HTTP Debug API service
        httpDebugApiService = HttpDebugApiService(applicationContext)
        httpDebugApiService?.getCurrentPumpDataCallback = { getCurrentPumpDataJson() }
        httpDebugApiService?.getCurrentPumpSidCallback = { pumpCommHandler?.getPumpSid() ?: Prefs(applicationContext).currentPumpSid() }
        httpDebugApiService?.sendPumpMessagesCallback = { data -> sendPumpCommMessages(data) }
        httpDebugApiService?.sendMessagingCallback = { path, data -> sendWearCommMessage(path, data) }
        httpDebugApiService?.onHistoryLogInsertedCallback = { item -> httpDebugApiService?.onHistoryLogInserted(item) }
        httpDebugApiService?.start()

        if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
            pumpFinderCommHandler = PumpFinderCommHandler(serviceLooper!!, this)
        } else {
            pumpCommHandler = PumpCommHandler(serviceLooper!!, this)

            pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
            pumpCommHandler?.postDelayed(automaticBolusMonitorTask, automaticBolusMonitorIntervalMs)
        }


        // Start periodic status sender (will stop when acknowledged)
        serviceLooper?.let { looper ->
            Handler(looper).postDelayed(serviceStatusTask, 500)
        }
    }

    override fun onBind(intent: Intent?) = null

    @androidx.annotation.VisibleForTesting
    internal fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        // Ignore noisy loopback logs for pump-originated broadcasts.
        if (!path.startsWith(MessagePaths.PREFIX_FROM_PUMP)) {
            Timber.d("service messageReceived: $path ${MessagePaths.payloadForLog(path, data)} from $sourceNodeId")
        }
        httpDebugApiService?.onMessagingReceived(path, data, sourceNodeId)
        when (path) {
            MessagePaths.TO_SERVER_FORCE_RELOAD -> {
                Timber.i("force-reload")
                triggerAppReload(applicationContext)
            }
            MessagePaths.TO_SERVER_WIZARD_PEER_RESCUED -> {
                if (!com.jwoglom.controlx2.shared.FeatureFlag.enabled(
                        applicationContext,
                        com.jwoglom.controlx2.shared.FeatureFlag.BTHostSwitch,
                    )) {
                    Timber.w("peer-rescued ignored: BTHostSwitch feature flag disabled")
                } else {
                    Timber.i("peer-rescued: peer is taking pump-host, flipping to CLIENT")
                    com.jwoglom.controlx2.util.applyPeerRescueFromService(
                        applicationContext, becomeClient = true,
                    )
                }
            }
            MessagePaths.TO_SERVER_SET_PAIRING_CODE -> {
                Timber.i("set-pairing-code received in service")
            }
            MessagePaths.TO_SERVER_STOP_PUMP_FINDER -> {
                Timber.i("stop-pump-finder")
                sendStopPumpFinderComm()
                if (String(data) == "init_comm") {
                    pumpFinderCommHandler = null
                    pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
                    val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
                    val pairingCodeType = Prefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
                    val pairingCodeTypeEnum = if (pairingCodeType.isNotEmpty())
                        PairingCodeType.fromLabel(pairingCodeType)
                    else
                        PairingCodeType.SHORT_6CHAR
                    Prefs(applicationContext).setPumpFinderServiceEnabled(false)
                    Prefs(applicationContext).setUnbondOnNextCommInitMac(filterToMac)
                    Timber.i("stop-pump-finder-next: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")

                    pumpCommHandler?.postDelayed(periodicUpdateTask, periodicUpdateIntervalMs)
                    pumpCommHandler?.postDelayed(automaticBolusMonitorTask, automaticBolusMonitorIntervalMs)
                    sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                }
            }
            MessagePaths.TO_SERVER_RESTART_PUMP_FINDER -> {
                Timber.i("restart-pump-finder")
                sendStopPumpFinderComm()
                pumpCommHandler = PumpCommHandler(serviceLooper!!, this)
                Prefs(applicationContext).setPumpFinderServiceEnabled(true)
                Timber.i("restart-pump-finder")
                triggerAppReload(applicationContext)
                //sendInitPumpComm()
            }
            MessagePaths.TO_SERVER_CHECK_PUMP_FINDER_FOUND_PUMPS -> {
                Timber.i("check-pump-finder-found-pumps")
                sendCheckPumpFinderFoundPumps()
            }
            MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS -> {
                val includeProfileSnapshot = String(data) == SERVICE_SNAPSHOT_WITH_PROFILE
                Timber.i(
                    "request-service-status received, responding with current status " +
                        "(includeProfile=$includeProfileSnapshot)",
                )
                if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
                    sendWearCommMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED, "".toByteArray())
                } else {
                    sendWearCommMessage(MessagePaths.TO_SERVER_COMM_STARTED, "".toByteArray())
                    pumpCommHandler?.refreshConnectedPumpSnapshotForUi(includeProfileSnapshot)
                }
            }
            MessagePaths.TO_SERVER_RETRY_INITIAL_PROFILE_READ -> {
                Timber.i("retry-initial-profile-read received")
                pumpCommHandler?.retryInitialPumpProfileRead()
            }
            MessagePaths.TO_SERVER_REFRESH_HISTORY_LOG_SYNC -> {
                Timber.i("refresh-history-log-sync received")
                pumpCommHandler?.refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            }
            MessagePaths.TO_SERVER_APPLY_RUNTIME_PREFS -> {
                // Soft-apply preference changes to the live pump session so the
                // caller does not have to trigger a process-killing app reload
                // (which leaves the BLE link in a "peer vanished" state and
                // breaks the next CONFIRM-mode JPAKE handshake — see post-pair
                // pairing-failure repro). pumpx2 only exposes one-way enables
                // for these statics, so we can move off→on at runtime; off is
                // still left to the legacy reload path.
                val prefs = Prefs(applicationContext)
                if (prefs.insulinDeliveryActions() && !PumpState.actionsAffectingInsulinDeliveryEnabled()) {
                    Timber.i("apply-runtime-prefs: enabling actionsAffectingInsulinDelivery")
                    PumpState.enableActionsAffectingInsulinDelivery()
                }
                if (prefs.connectionSharingEnabled()) {
                    if (!PumpState.tconnectAppConnectionSharing) {
                        Timber.i("apply-runtime-prefs: enabling tconnectAppConnectionSharing")
                        PumpState.tconnectAppConnectionSharing = true
                    }
                    if (!PumpState.sendSharedConnectionResponseMessages) {
                        Timber.i("apply-runtime-prefs: enabling sendSharedConnectionResponseMessages")
                        PumpState.sendSharedConnectionResponseMessages = true
                    }
                }
                if (prefs.onlySnoopBluetoothEnabled() && !PumpState.onlySnoopBluetooth) {
                    Timber.i("apply-runtime-prefs: enabling onlySnoopBluetooth")
                    PumpState.onlySnoopBluetooth = true
                }
            }
            MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED -> {
                Timber.i("service-status acknowledged, stopping periodic sender")
                serviceStatusAcknowledged = true
            }
            MessagePaths.TO_SERVER_STOP_COMM -> {
                Timber.w("stop-comm")
                sendStopPumpComm()
            }
            MessagePaths.TO_SERVER_IS_PUMP_CONNECTED -> {
                sendCheckPumpConnected()
            }
            MessagePaths.TO_SERVER_ENSURE_PUMP_CONNECTION -> {
                Timber.i("explicit connection check requested")
                sendEnsurePumpConnection()
            }
            MessagePaths.TO_PUMP_PAIR -> {
                sendPumpPairingMessage()
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_WEAR -> {
                if (IntegrationGates.WATCH_INTEGRATION_ENABLED) {
                    bolusManager.confirmBolusRequest(
                        PumpMessageSerializer.fromBytes(data) as InitiateBolusRequest,
                        BolusManager.BolusRequestSource.WEAR,
                    )
                } else {
                    // Defense in depth: reject stale or injected watch requests even though the
                    // Wear message transport is also disabled in MessageBusFactory.
                    Timber.w("Blocked watch-originated bolus request: watch integration is disabled")
                }
            }
            MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE -> {
                // MainActivity emits this path only after successful device authentication and
                // immutable request revalidation. Do not send it through the legacy notification
                // confirmation path: that creates a second decision point and can strand the
                // already-granted pump permission while the user dismisses another dialog.
                val phoneSubmission = AuthenticatedPhoneBolusSubmission.decode(data) ?: run {
                    // Compatibility for a request staged by the immediately previous app build.
                    // It can still be authenticated and delivered, but it has no trustworthy
                    // metadata for a post-acceptance BG/carb history entry.
                    val legacyRequest = runCatching {
                        PumpMessageSerializer.fromBytes(data) as? InitiateBolusRequest
                    }.getOrNull()
                    legacyRequest?.let {
                        AuthenticatedPhoneBolusSubmission(
                            request = it,
                            postBolusRecords = PostBolusRecordPlan(
                                bolusId = it.bolusID,
                                bgMgdl = null,
                                bgRecordType = BolusBgRecordType.NONE,
                                carbsGrams = 0,
                            ),
                        )
                    }
                }
                val submitted = phoneSubmission?.let {
                    bolusManager.submitAuthenticatedPhoneBolus(
                        request = it.request,
                        postBolusRecords = it.postBolusRecords,
                    )
                } ?: false
                if (submitted) {
                    // The unfinished-draft lifecycle ends only after the authenticated request
                    // has been synchronously persisted, signed, and handed to the service.
                    BolusTimeoutReminder(this).completeSession()
                } else {
                    Timber.e("Authenticated phone bolus could not be durably staged")
                    sendBolusPermissionRelease()
                    // Staging failure is terminal for this draft and starts durable permission
                    // cleanup, so do not leave a stale unfinished-bolus reminder behind.
                    BolusTimeoutReminder(this).completeSession()
                    sendWearCommMessage(
                        MessagePaths.TO_CLIENT_BOLUS_REJECTED,
                        "local_submission_persistence_failed".toByteArray(),
                    )
                }
            }
            MessagePaths.TO_SERVER_BOLUS_CANCEL -> {
                Timber.i("bolus state cancelled")
                bolusManager.resetBolusPrefs()
            }
            MessagePaths.TO_SERVER_CANCEL_AUTOMATIC_BOLUS -> {
                val identity = AutomaticBolusIdentity.decode(data)
                if (identity == null) {
                    Timber.e("Invalid automatic bolus cancellation identity")
                } else {
                    sendAutomaticBolusCancellation(identity)
                }
            }
            MessagePaths.TO_SERVER_CANCEL_MANUAL_BOLUS -> {
                val identity = ManualBolusIdentity.decode(data)
                if (identity == null) {
                    Timber.e("Invalid manual bolus cancellation identity")
                } else {
                    sendManualBolusCancellation(identity)
                }
            }
            MessagePaths.TO_SERVER_CANCEL_QUICK_BOLUS -> {
                val identity = QuickBolusIdentity.decode(data)
                if (identity == null) {
                    Timber.e("Invalid Quick Bolus cancellation identity")
                } else {
                    sendQuickBolusCancellation(identity)
                }
            }
            MessagePaths.TO_SERVER_ACKNOWLEDGE_MANUAL_BOLUS_TERMINAL -> {
                val acknowledgement = ManualBolusTerminalAcknowledgement.decode(data)
                if (acknowledgement == null) {
                    Timber.e("Invalid manual bolus terminal acknowledgement")
                } else {
                    sendManualBolusTerminalAcknowledgement(acknowledgement)
                }
            }
            MessagePaths.TO_SERVER_RECONCILE_MANUAL_BOLUS -> {
                sendManualBolusReconciliation()
            }
            MessagePaths.TO_SERVER_ACKNOWLEDGE_QUICK_BOLUS_TERMINAL -> {
                val identity = QuickBolusIdentity.decode(data)
                if (identity == null) {
                    Timber.e("Invalid Quick Bolus terminal acknowledgement")
                } else {
                    sendQuickBolusTerminalAcknowledgement(identity)
                }
            }
            MessagePaths.TO_SERVER_ACQUIRE_BOLUS_PERMISSION -> {
                sendBolusPermissionAcquisition()
            }
            MessagePaths.TO_SERVER_PREPARE_BOLUS_WORKFLOW -> {
                sendBolusWorkflowPreparation()
            }
            MessagePaths.TO_SERVER_RELEASE_BOLUS_PERMISSION -> {
                sendBolusPermissionRelease(AbandonedBolusCleanupRequest.decode(data))
            }
            MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS -> {
                // removed: initialized check
                val secretKey = prefs(this)?.getString("initiateBolusSecret", "") ?: ""
                val confirmedBolus =
                    InitiateConfirmedBolusSerializer.fromBytes(secretKey, data)

                val messageOk = confirmedBolus.left
                val initiateMessage = confirmedBolus.right
                if (!messageOk) {
                    Timber.e("invalid message -- blocked signature $messageOk $initiateMessage")
                    sendWearCommMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE,
                        "CommService".toByteArray()
                    )
                    NotificationCompat.Builder(this)
                        .setContentTitle("Bolus Blocked")
                        .setContentText("Bolus message was blocked due to an invalid signature.")
                        .build()
                    return
                }

                Timber.i("sending confirmed bolus request: $initiateMessage")
                sendPumpCommBolusMessage(data)
            }
            MessagePaths.TO_SERVER_WRITE_CHARACTERISTIC_FAILED_CALLBACK -> {
                Timber.i("writeCharacteristicFailedCallback from message")
                handleWriteCharacteristicFailedCallback(String(data))
            }
            MessagePaths.TO_PUMP_COMMAND -> {
                sendPumpCommMessage(data)
            }
            MessagePaths.TO_PUMP_COMMANDS -> {
                sendPumpCommMessages(data)
            }
            MessagePaths.TO_PUMP_DEBUG_COMMANDS -> {
                sendPumpCommDebugMessages(data)
            }
            MessagePaths.TO_PUMP_COMMANDS_BUST_CACHE -> {
                sendPumpCommMessagesBustCache(data)
            }
            MessagePaths.TO_PUMP_CACHED_COMMANDS -> {
                handleCachedCommandsRequest(data)
            }
            MessagePaths.TO_PUMP_DEBUG_MESSAGE_CACHE -> {
                handleDebugGetMessageCache()
            }
            MessagePaths.TO_PUMP_DEBUG_HISTORYLOG_CACHE -> {
                handleDebugGetHistoryLogCache()
            }
            MessagePaths.TO_PUMP_DEBUG_WRITE_BT_CHARACTERISTIC -> {
                sendDebugWriteBtCharacteristic(data)
            }
        }
    }

    // --- Preference accessors ---
    override fun prefAutoFetchHistoryLogs() = Prefs(applicationContext).autoFetchHistoryLogs()
    override fun prefConnectionSharingEnabled() = Prefs(applicationContext).connectionSharingEnabled()
    override fun prefOnlySnoopBluetoothEnabled() = Prefs(applicationContext).onlySnoopBluetoothEnabled()
    override fun prefInsulinDeliveryActions() = Prefs(applicationContext).insulinDeliveryActions()
    override fun prefQualifyingEventToastsEnabled() = Prefs(applicationContext).qualifyingEventToastsEnabled()
    override fun prefGlucoseUnit(): GlucoseUnit? = Prefs(applicationContext).glucoseUnit()
    override fun prefSetPumpModelName(name: String) { Prefs(applicationContext).setPumpModelName(name) }
    override fun prefSetCurrentPumpSid(sid: Int) { Prefs(applicationContext).setCurrentPumpSid(sid) }
    override fun prefPumpFinderPumpMac(): String? = Prefs(applicationContext).pumpFinderPumpMac()
    override fun prefUnbondOnNextCommInitMac(): String? = Prefs(applicationContext).unbondOnNextCommInitMac()
    override fun prefSetUnbondOnNextCommInitMac(mac: String?) { Prefs(applicationContext).setUnbondOnNextCommInitMac(mac) }

    // --- Sync/dispatch callbacks ---
    override fun onPumpConnectedSync(pumpSid: Int) {
        val ctx = applicationContext
        NightscoutSyncWorker.startIfEnabled(ctx, ctx.getSharedPreferences("controlx2", Context.MODE_PRIVATE), pumpSid)
    }

    private val xdripMessageDispatcher by lazy { XdripMessageDispatcher(applicationContext) }
    private val gdhMessageDispatcher by lazy { GdhMessageDispatcher(applicationContext) }
    private val pumpAlertNotifier by lazy { PumpAlertNotifier(applicationContext) }

    override fun dispatchExternalMessage(message: com.jwoglom.pumpx2.pump.messages.Message) {
        xdripMessageDispatcher.onReceiveMessage(message)
        gdhMessageDispatcher.onReceiveMessage(message)
    }

    override fun updateComplicationData(key: String, value: String, timestamp: Instant) {
        val state = DataClientState(applicationContext)
        val pair = Pair(value, timestamp)
        val prefs = Prefs(applicationContext)
        when (key) {
            "pumpBattery" -> state.pumpBattery = pair
            "pumpIOB" -> state.pumpIOB = pair
            "pumpCartridgeUnits" -> state.pumpCartridgeUnits = pair
            "pumpCurrentBasal" -> state.pumpCurrentBasal = pair
            StateKeys.PUMP_CONTROL_IQ_MODE -> state.pumpControlIqMode = pair
            "cgmReading" -> {
                state.cgmReading = pair
                val currentSession = prefs.widgetCgmSessionCache()
                prefs.setWidgetCgmReadingSensorStartedTimestamp(
                    currentSession
                        ?.takeIf { it.phase == WidgetCgmSessionPhase.ACTIVE }
                        ?.sensorStartedTimestamp,
                )
            }
        }
        // The widget only consumes state the existing pump path has already received. Its
        // debounced redraw does not enqueue or request any pump command.
        MobiControlWidgetProvider.requestUpdate(applicationContext)
    }

    // --- Debug API callbacks ---
    override fun onPumpMessageReceived(message: com.jwoglom.pumpx2.pump.messages.Message, source: SendType) {
        httpDebugApiService?.onPumpMessageReceived(message, source = source)
        val widgetPrefs = Prefs(applicationContext)
        when (message) {
            is CgmStatusV2Response -> {
                updateWidgetCgmSession(
                    widgetPrefs,
                    WidgetCgmSessionObservation(
                        source = WidgetCgmSessionSource.V2,
                        phase = when (message.sessionState) {
                            CgmStatusV2Response.SessionState.SESSION_ACTIVE ->
                                WidgetCgmPumpPhase.ACTIVE
                            CgmStatusV2Response.SessionState.STATUS_START_PENDING ->
                                WidgetCgmPumpPhase.START_PENDING
                            CgmStatusV2Response.SessionState.SESSION_STOP_PENDING ->
                                WidgetCgmPumpPhase.STOP_PENDING
                            CgmStatusV2Response.SessionState.STATUS_STOPPED ->
                                WidgetCgmPumpPhase.STOPPED
                            else -> WidgetCgmPumpPhase.UNAVAILABLE
                        },
                        sensorStartedTimestamp = message.sensorStartedTimestamp,
                        sessionTimeRemainingSeconds = message.sessionTimeRemainingSeconds,
                        sessionDurationSeconds = message.sessionDurationSeconds,
                    ),
                )
            }
            is CGMStatusResponse -> {
                updateWidgetCgmSession(
                    widgetPrefs,
                    WidgetCgmSessionObservation(
                        source = WidgetCgmSessionSource.LEGACY,
                        phase = when (message.sessionState) {
                            CGMStatusResponse.SessionState.SESSION_ACTIVE ->
                                WidgetCgmPumpPhase.ACTIVE
                            CGMStatusResponse.SessionState.SESSION_START_PENDING ->
                                WidgetCgmPumpPhase.START_PENDING
                            CGMStatusResponse.SessionState.SESSION_STOP_PENDING ->
                                WidgetCgmPumpPhase.STOP_PENDING
                            CGMStatusResponse.SessionState.SESSION_STOPPED ->
                                WidgetCgmPumpPhase.STOPPED
                            else -> WidgetCgmPumpPhase.UNAVAILABLE
                        },
                        sensorStartedTimestamp = message.sensorStartedTimestamp,
                    ),
                )
            }
            is com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse -> {
                widgetPrefs.setWidgetCgmSampleCache(
                    currentEgvWidgetSample(
                        response = message,
                        session = widgetPrefs.widgetCgmSessionCache(),
                        observedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                MobiControlWidgetProvider.requestUpdate(applicationContext)
            }
        }
        val pumpSystemNotificationStatus = when (message) {
            is com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse,
            is com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse,
            is com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse,
            is com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse,
            -> true
            else -> false
        }
        if (pumpSystemNotificationStatus) {
            val pumpSid = pumpCommHandler?.getPumpSid() ?: Prefs(applicationContext).currentPumpSid()
            val pumpSessionId = pumpCommState.lastTimeSinceReset?.pumpSessionId()
            if (pumpSessionId != null) when (message) {
                is com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse ->
                    pumpAlertNotifier.onStatus(message, pumpSid, pumpSessionId)
                is com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse ->
                    pumpAlertNotifier.onStatus(message, pumpSid, pumpSessionId)
                is com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse ->
                    pumpAlertNotifier.onStatus(message, pumpSid, pumpSessionId)
                is com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse ->
                    pumpAlertNotifier.onStatus(message, pumpSid, pumpSessionId)
            }
        }
    }

    private fun updateWidgetCgmSession(
        prefs: Prefs,
        observation: WidgetCgmSessionObservation,
    ) {
        when (observation.phase) {
            WidgetCgmPumpPhase.START_PENDING -> {
                // Passive evidence only: bind an app-authored start marker to the pump's observed
                // lifecycle so a legitimate long warm-up cannot look like an external session.
                prefs.markCgmSessionStartPendingObserved()
            }
            WidgetCgmPumpPhase.STOP_PENDING,
            WidgetCgmPumpPhase.STOPPED,
            -> if (prefs.cgmSessionStartPendingObserved()) {
                // The pump moved away from an observed app-authored start before it became Active.
                // Do not let that abandoned marker suppress adoption of a later sensor.
                prefs.clearCgmSessionStartInitiated()
            }
            else -> Unit
        }
        val reconciled = reconcileWidgetCgmSession(
            current = prefs.widgetCgmSessionCache(),
            observation = observation,
            observedAtEpochMillis = System.currentTimeMillis(),
        )
        prefs.setWidgetCgmSessionCache(reconciled)
        if (reconciled.phase != WidgetCgmSessionPhase.ACTIVE) {
            prefs.setWidgetCgmReadingSensorStartedTimestamp(null)
            prefs.setWidgetCgmSampleCache(null)
        }
        MobiControlWidgetProvider.requestUpdate(applicationContext)
    }

    override fun onPumpCriticalError(error: TandemError, source: SendType) {
        httpDebugApiService?.onPumpCriticalError(error, source = source)
    }

    // --- Bolus response broadcasting ---
    override fun broadcastBolusInitiateResponse(responseBytes: ByteArray) {
        val response = PumpMessageSerializer.fromBytes(responseBytes) as?
            com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
        val intent = android.content.Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
            putExtra("action", "INITIATE_RESPONSE")
            putExtra("response", responseBytes)
            response?.takeIf { it.wasBolusInitiated() }
                ?.let { manualBolusIdentityOrNull(it.bolusId) }
                ?.let { putExtra(BolusNotificationBroadcastReceiver.EXTRA_MANUAL_BOLUS_IDENTITY, it.encode()) }
        }
        applicationContext.sendBroadcast(intent)
    }

    override fun broadcastBolusStatusUpdate(statusBytes: ByteArray) {
        val status = PumpMessageSerializer.fromBytes(statusBytes) as?
            com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
        var manualNotificationIdentity: ManualBolusIdentity? = null
        if (status != null) {
            val transition = automaticBolusNotifier.onStatus(
                response = status,
                pumpSid = pumpCommHandler?.getPumpSid() ?: Prefs(applicationContext).currentPumpSid(),
                pumpSession = pumpCommState.lastTimeSinceReset,
                cancellationEnabled = Prefs(applicationContext).insulinDeliveryActions(),
            )
            var reachedTerminalState = transition == AutomaticBolusTransition.ENDED
            val previouslyActiveManualIdentity = activeManualBolusIdentity
            val responseIdentity = if (
                status.bolusId > 0 &&
                status.bolusSource == BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
            ) {
                manualBolusIdentityOrNull(status.bolusId)
            } else {
                null
            }
            val notificationRoute = routeManualBolusSystemNotification(
                response = status,
                activeIdentity = activeManualBolusIdentity,
                responseIdentity = responseIdentity,
            )
            manualNotificationIdentity = notificationRoute.notificationIdentity
            activeManualBolusIdentity = notificationRoute.nextActiveIdentity
            // Preserve the pre-existing pump/history refresh lifecycle exactly. The source-aware
            // route above changes only which Android notification receives this shared status.
            if (previouslyActiveManualIdentity != null && !status.isActiveManualBolus()) {
                reachedTerminalState = true
            }
            if (manualNotificationIdentity != null && status.isTerminalBolusStatus()) {
                Timber.i(
                    "Manual bolus reached pump-confirmed terminal state: " +
                        "$manualNotificationIdentity",
                )
            }
            val quickOperation = Prefs(applicationContext).quickBolusOperation()
            if (status.isActiveQuickBolus() || quickOperation?.isUnresolved == true) {
                if (!BolusStatusPolling.isRunning()) {
                    BolusStatusPolling.start(
                        requestStatus = { pumpCommHandler?.requestBolusStatusWatchdog() },
                    )
                }
            }

            if (reachedTerminalState) {
                sendPumpCommMessagesBustCache(
                    PumpMessageSerializer.toBulkBytes(listOf(
                        lastBolusStatusRequest(),
                        HistoryLogStatusRequest(),
                    )),
                )
                pumpCommHandler?.refreshHistoryLogSyncWorker(triggerImmediateSync = true)
            }
        }
        manualNotificationIdentity?.let { identity ->
            val intent = android.content.Intent(
                applicationContext,
                BolusNotificationBroadcastReceiver::class.java,
            ).apply {
                putExtra("action", "STATUS_UPDATE")
                putExtra("status", statusBytes)
                putExtra(BolusNotificationBroadcastReceiver.EXTRA_MANUAL_BOLUS_IDENTITY, identity.encode())
            }
            applicationContext.sendBroadcast(intent)
        }
    }

    override fun broadcastAutomaticBolusCancelResult(resultBytes: ByteArray) {
        AutomaticBolusCancelResult.decode(resultBytes)?.let(automaticBolusNotifier::onCancelResult)
        sendWearCommMessage(MessagePaths.TO_CLIENT_AUTOMATIC_BOLUS_CANCEL_RESULT, resultBytes)
        // Cancellation acknowledgement is not terminal bolus status. Refresh
        // current and last status so Dashboard/history use pump confirmation.
        sendPumpCommMessagesBustCache(
            PumpMessageSerializer.toBulkBytes(listOf(
                CurrentBolusStatusRequest(),
                lastBolusStatusRequest(),
            )),
        )
    }

    override fun broadcastManualBolusCancelResult(resultBytes: ByteArray) {
        sendWearCommMessage(MessagePaths.TO_CLIENT_MANUAL_BOLUS_CANCEL_RESULT, resultBytes)
        // PumpCommHandler owns the bounded Current/Last/history reconciliation. Keeping that
        // sequence in one place avoids duplicate status bursts after a cancellation response.
    }

    override fun loadManualBolusOperation(): ManualBolusOperation? =
        Prefs(applicationContext).manualBolusOperation()

    override fun persistManualBolusOperation(operation: ManualBolusOperation) {
        // commit() in Prefs is intentional: the operation must survive a process or radio failure
        // immediately after the following BLE write.
        Prefs(applicationContext).setManualBolusOperation(operation)
        if (!operation.isUnresolved) {
            BolusStatusPolling.stop()
        }
        sendWearCommMessage(MessagePaths.TO_CLIENT_MANUAL_BOLUS_OPERATION, operation.encode())
    }

    override fun broadcastQuickBolusCancelResult(resultBytes: ByteArray) {
        sendWearCommMessage(MessagePaths.TO_CLIENT_QUICK_BOLUS_CANCEL_RESULT, resultBytes)
        sendPumpCommMessagesBustCache(
            PumpMessageSerializer.toBulkBytes(listOf(
                CurrentBolusStatusRequest(),
                lastBolusStatusRequest(),
            )),
        )
    }

    override fun loadQuickBolusOperation(): QuickBolusOperation? =
        Prefs(applicationContext).quickBolusOperation()

    override fun quickBolusLifecycleEnabled(): Boolean = true

    override fun persistQuickBolusOperation(operation: QuickBolusOperation) {
        Prefs(applicationContext).setQuickBolusOperation(operation)
        if (!operation.isUnresolved) {
            BolusStatusPolling.stop()
        }
        sendWearCommMessage(MessagePaths.TO_CLIENT_QUICK_BOLUS_OPERATION, operation.encode())
    }

    override fun loadBolusPermissionOperation(): BolusPermissionOperation? =
        Prefs(applicationContext).bolusPermissionOperation()

    override fun persistBolusPermissionOperation(operation: BolusPermissionOperation) {
        // The reservation/release phase must be durable before the corresponding BLE write.
        Prefs(applicationContext).setBolusPermissionOperation(operation)
        sendWearCommMessage(MessagePaths.TO_CLIENT_BOLUS_PERMISSION_OPERATION, operation.encode())
    }

    override fun loadPostBolusRecordPlan(): PostBolusRecordPlan? =
        bolusManager.loadPostBolusRecordPlan()

    override fun persistPostBolusRecordPlan(plan: PostBolusRecordPlan): Boolean =
        bolusManager.persistPostBolusRecordPlan(plan)

    override fun clearPostBolusRecordPlan(): Boolean = bolusManager.clearPostBolusRecordPlan()

    private fun lastBolusStatusRequest(): Message {
        val apiVersion = PumpState.getPumpAPIVersion() ?: KnownApiVersion.API_V2_5.get()
        return if (apiVersion.compareTo(KnownApiVersion.MOBI_API_V3_5) >= 0) {
            LastBolusStatusV3Request()
        } else {
            com.jwoglom.pumpx2.pump.messages.builders.LastBolusStatusRequestBuilder.create(apiVersion)
        }
    }

    // --- History log factories ---
    override fun createHistoryLogFetcher(
        pumpSid: Int,
        pumpSession: PumpSession,
        autoFetchEnabled: () -> Boolean,
    ): PumpHistoryLogFetcher {
        return HistoryLogFetcher(
            historyLogRepo = historyLogRepo,
            pumpSid = pumpSid,
            pumpSession = pumpSession,
            autoFetchEnabled = autoFetchEnabled,
            broadcastCallback = { item ->
                httpDebugApiService?.onHistoryLogInsertedCallback?.invoke(item)
                xdripMessageDispatcher.onHistoryLogItem(item)
                MobiControlWidgetProvider.requestUpdate(applicationContext)
            }
        )
    }

    override fun createHistoryLogSyncWorker(requestSync: () -> Unit): PumpHistoryLogSyncWorker {
        return HistoryLogSyncWorker(requestSync = requestSync)
    }

    override fun markConnectionTime() {
        currentPumpData.connectionTime = Instant.now()
    }

    override fun showToast(text: String, duration: Int) {
        Toast.makeText(this, text, duration).show()
    }

    override fun getWearPrefs(): SharedPreferences? {
        return getSharedPreferences("WearX2", MODE_PRIVATE)
    }

    private var started = false
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("CommService onStartCommand $intent $flags $startId")

        if (started) {
            if (!Prefs(applicationContext).pumpFinderServiceEnabled() &&
                Prefs(applicationContext).currentPumpSid() >= 0) {
                Timber.i("CommService already running; ensuring the saved pump connection is active")
                sendEnsurePumpConnection()
            } else {
                Timber.i("CommService already running without an established pump; no reconnect re-arm")
            }
            return START_STICKY
        }

        started = true
        Toast.makeText(this, "${getString(R.string.app_name)} service starting", Toast.LENGTH_SHORT).show()

        updateNotification("Initializing...")

        Timber.i("CommService onStartCommand has pumpFinderCommHandler=${pumpFinderCommHandler} pumpCommHandler=${pumpCommHandler}")

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        if (Prefs(applicationContext).pumpFinderServiceEnabled()) {
            Timber.i("Starting CommService in PumpFinder mode")
            sendInitPumpFinderComm()
        } else {
            val pairingCodeType = Prefs(applicationContext).pumpFinderPairingCodeType().orEmpty()
            val pairingCodeTypeEnum = if (!pairingCodeType.isEmpty())
                PairingCodeType.fromLabel(pairingCodeType)
            else
                PairingCodeType.SHORT_6CHAR
            val filterToMac = Prefs(applicationContext).pumpFinderPumpMac().orEmpty()
            Timber.i("Starting CommService in standard mode: filterToMac=$filterToMac pairingCodeType=$pairingCodeType")

            sendInitPumpComm(pairingCodeTypeEnum, filterToMac)
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    private fun startForegroundWrapped(id: Int, notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(id, notification)
            }
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Unable to start foreground service: missing connectedDevice permissions")
            Toast.makeText(
                this,
                "${getString(R.string.app_name)} needs Bluetooth permissions before starting the background service",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            false
        }
    }


    override fun updateNotification(statusText: String?) {
        if (statusText != null) {
            currentPumpData.statusText = statusText
        }
        var notification = createNotification()
        startForegroundWrapped(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Dismissing the UI task is not a request to disconnect from the pump. CommService is a
        // START_STICKY connected-device foreground service, so retain the live BLE session and
        // any in-flight bolus/status reconciliation. The previous reload + stopSelf sequence
        // created multiple service processes and guaranteed a mid-bolus disconnect.
        Timber.i("CommService onTaskRemoved: retaining foreground pump connection")
    }

    private data class DisplayablePumpData(
        var statusText: String = "Initializing...",
        var connectionTime: Instant? = null,
        var lastMessageTime: Instant? = null,
        var batteryPercent: Int? = null,
        var iobUnits: Double? = null,
        var cartridgeRemainingUnits: Int? = null,
    )

    private val currentPumpData: DisplayablePumpData = DisplayablePumpData()

    private fun getCurrentPumpDataJson(): String {
        return """{"statusText":"${currentPumpData.statusText}","connectionTime":"${currentPumpData.connectionTime}","lastMessageTime":"${currentPumpData.lastMessageTime}","batteryPercent":${currentPumpData.batteryPercent},"iobUnits":${currentPumpData.iobUnits},"cartridgeRemainingUnits":${currentPumpData.cartridgeRemainingUnits}}"""
    }

    override fun updateNotificationWithPumpData(message: com.jwoglom.pumpx2.pump.messages.Message) {
        var changed = false
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                changed = currentPumpData.batteryPercent != message.batteryPercent
                currentPumpData.batteryPercent = message.batteryPercent
            }
            is ControlIQIOBResponse -> {
                changed = currentPumpData.iobUnits != InsulinUnit.from1000To1(message.pumpDisplayedIOB)
                currentPumpData.iobUnits = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is InsulinStatusResponse -> {
                changed = currentPumpData.cartridgeRemainingUnits != message.currentInsulinAmount
                currentPumpData.cartridgeRemainingUnits = message.currentInsulinAmount
            }
        }

        if (changed) {
            currentPumpData.lastMessageTime = Instant.now()
            updateNotification()
        }
    }

    private fun sendInitPumpFinderComm() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_FINDER_COMM.ordinal
            pumpFinderCommHandler?.sendMessage(msg)
        }
    }

    private fun sendStopPumpFinderComm() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.STOP_PUMP_FINDER_COMM.ordinal
            pumpFinderCommHandler?.sendMessage(msg)
        }
    }

    private fun sendCheckPumpFinderFoundPumps() {
        pumpFinderCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal
            pumpFinderCommHandler?.sendMessage(msg)
        }
    }

    private fun sendInitPumpComm(pairingCodeType: PairingCodeType, filterToBluetoothMac: String) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.INIT_PUMP_COMM.ordinal
            if (filterToBluetoothMac.length > 0) {
                msg.obj = "${pairingCodeType.label} $filterToBluetoothMac"
            } else {
                msg.obj = "${pairingCodeType.label}"
            }
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendEnsurePumpConnection() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.ENSURE_PUMP_CONNECTION.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }
    
    private fun sendCheckPumpConnected() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CHECK_PUMP_CONNECTED.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendPumpPairingMessage() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_PAIRING_MESSAGE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommMessage(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMAND.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    override fun sendPumpCommMessages(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMANDS_BULK.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun sendPumpCommDebugMessages(pumpMsgBytes: ByteArray) {
        try {
            val messages = PumpMessageSerializer.fromBulkBytes(pumpMsgBytes)
            synchronized(pumpCommState.debugPromptResponseCounts) {
                messages.forEach {
                    val key = Pair(it.characteristic, it.responseOpCode)
                    pumpCommState.debugPromptResponseCounts[key] = (pumpCommState.debugPromptResponseCounts[key] ?: 0) + 1
                }
            }
            Timber.i("Registered ${messages.size} debug-prompt request(s) for stream tagging")
        } catch (e: Exception) {
            Timber.w(e, "Failed to register debug-prompt requests")
        }
        sendPumpCommMessages(pumpMsgBytes)
    }
    private fun sendPumpCommMessagesBustCache(pumpMsgBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMANDS_BUST_CACHE_BULK.ordinal
            msg.obj = pumpMsgBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }
    private fun handleCachedCommandsRequest(rawBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CACHED_PUMP_COMMANDS_BULK.ordinal
            msg.obj = rawBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun consumeDebugPromptResponse(characteristic: Characteristic, opCode: Byte): Boolean {
        return pumpCommState.consumeDebugPromptResponse(characteristic, opCode)
    }
    private fun sendPumpCommBolusMessage(initiateConfirmedBolusBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.SEND_PUMP_COMMAND_BOLUS.ordinal
            msg.obj = initiateConfirmedBolusBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private val handleWriteCharacteristicFailedCallback: (String) -> Unit = { uuid ->
        Timber.i("handleWriteCharacteristicFailedCallback($uuid)")
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.WRITE_CHARACTERISTIC_FAILED_CALLBACK.ordinal
            msg.obj = uuid
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun handleDebugGetMessageCache() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_GET_MESSAGE_CACHE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun handleDebugGetHistoryLogCache() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_GET_HISTORYLOG_CACHE.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendDebugWriteBtCharacteristic(jsonBlobBytes: ByteArray) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.DEBUG_WRITE_BT_CHARACTERISTIC.ordinal
            msg.obj = jsonBlobBytes
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendStopPumpComm() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.STOP_PUMP_COMM.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendAutomaticBolusCancellation(identity: AutomaticBolusIdentity) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CANCEL_AUTOMATIC_BOLUS.ordinal
            msg.obj = identity
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendManualBolusCancellation(identity: ManualBolusIdentity) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CANCEL_MANUAL_BOLUS.ordinal
            msg.obj = identity
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendManualBolusReconciliation() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.RECONCILE_MANUAL_BOLUS.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendQuickBolusCancellation(identity: QuickBolusIdentity) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.CANCEL_QUICK_BOLUS.ordinal
            msg.obj = identity
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendManualBolusTerminalAcknowledgement(
        acknowledgement: ManualBolusTerminalAcknowledgement,
    ) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.ACKNOWLEDGE_MANUAL_BOLUS_TERMINAL.ordinal
            msg.obj = acknowledgement
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendQuickBolusTerminalAcknowledgement(identity: QuickBolusIdentity) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.ACKNOWLEDGE_QUICK_BOLUS_TERMINAL.ordinal
            msg.obj = identity
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendBolusPermissionAcquisition() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.ACQUIRE_BOLUS_PERMISSION.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendBolusWorkflowPreparation() {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.PREPARE_BOLUS_WORKFLOW.ordinal
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun sendBolusPermissionRelease(cleanupRequest: AbandonedBolusCleanupRequest? = null) {
        pumpCommHandler?.obtainMessage()?.also { msg ->
            msg.what = CommServiceCodes.RELEASE_BOLUS_PERMISSION.ordinal
            msg.obj = cleanupRequest
            pumpCommHandler?.sendMessage(msg)
        }
    }

    private fun manualBolusIdentityOrNull(bolusId: Int): ManualBolusIdentity? {
        val sid = pumpCommHandler?.getPumpSid() ?: Prefs(applicationContext).currentPumpSid()
        val sessionId = pumpCommState.lastTimeSinceReset?.pumpSessionId()
        return if (sid >= 0 && sessionId != null && sessionId > 0L && bolusId > 0) {
            val persistedIdentity = Prefs(applicationContext).manualBolusOperation()?.identity
            persistedIdentity?.takeIf {
                it.pumpSid == sid &&
                    pumpSessionIdsMatch(it.pumpSessionId, sessionId) &&
                    it.bolusId == bolusId
            } ?: ManualBolusIdentity(sid, sessionId, bolusId)
        } else {
            null
        }
    }


    companion object {
        const val ACTION_OPEN_CONNECTION_STATUS =
            "com.jwoglom.controlx2.action.OPEN_CONNECTION_STATUS"
        private const val SERVICE_SNAPSHOT_WITH_PROFILE = "include_profile"

        fun cancelAutoApproveStatic(context: Context?) {
            BolusManager.cancelAutoApproveStatic(context)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterAppReloadShutdownHook(appReloadShutdownHook)
        pumpCommHandler?.stopHistoryLogSyncWorker()
        pumpCommHandler?.removeCallbacks(automaticBolusMonitorTask)
        BolusStatusPolling.stop()
        scope.cancel()
        // Developer Preview and other precondition failures stop the service before the
        // MessageBus is created. Service teardown must remain valid on those short paths.
        if (::messageBus.isInitialized) {
            messageBus.close()
        }
        httpDebugApiService?.stop()
        Toast.makeText(this, "${getString(R.string.app_name)} service destroyed", Toast.LENGTH_SHORT).show()
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "$packageName Background Notification"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        val channel = NotificationChannel(
            notificationChannelId,
            "Endless Service notifications channel",
            NotificationManager.IMPORTANCE_NONE
        ).let {
            it.description = "Endless Service channel"
            it.setShowBadge(false)
            it.lockscreenVisibility = 0

            it
        }
        notificationManager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_CONNECTION_STATUS
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }.let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            this,
            notificationChannelId
        )

        var title = "${currentPumpData.statusText}"
        var atTime = currentPumpData.lastMessageTime
        currentPumpData.connectionTime?.let {
            if (atTime == null || it.isAfter(atTime)) atTime = it
        }
        atTime?.let {
            title += " at ${shortTime(it)}"
        }

        var contentText = ""
        if (currentPumpData.batteryPercent != null) {
            contentText += "Battery: ${currentPumpData.batteryPercent}%\u00A0\u00A0\u00A0"
        }
        if (currentPumpData.iobUnits != null) {
            contentText += "IOB: ${currentPumpData.iobUnits}u\u00A0\u00A0\u00A0"
        }
        if (currentPumpData.cartridgeRemainingUnits != null) {
            contentText += "Cartridge: ${currentPumpData.cartridgeRemainingUnits}u"
        }
        currentPumpData.connectionTime?.let {
            contentText += "    \nConnection established at: ${shortTime(it)}"
        }

        return builder
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setSmallIcon(
                IconCompat.createWithResource(this, R.drawable.ic_mobicontrol_notification),
            )
            .setTicker(currentPumpData.statusText)
            .setPriority(NotificationCompat.PRIORITY_MAX) // for under android 26 compatibility
            .setOngoing(true)
            .build()
    }

    private fun prefs(context: Context): SharedPreferences? {
        return context.getSharedPreferences("WearX2", MODE_PRIVATE)
    }

}
