package com.jwoglom.controlx2

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.common.base.Strings
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.messaging.MessageListener
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModelFactory
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.DeviceAuthenticator
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.controlx2.presentation.PumpStateEvent
import com.jwoglom.controlx2.presentation.MobileApp
import com.jwoglom.controlx2.presentation.navigation.BolusInputPrefill
import com.jwoglom.controlx2.presentation.navigation.Screen
import com.jwoglom.controlx2.presentation.navigation.SheetLaunchRequest
import com.jwoglom.controlx2.presentation.navigation.SheetLaunchTarget
import com.jwoglom.controlx2.presentation.navigation.TempRateInputPrefill
import com.jwoglom.controlx2.presentation.pairing.PumpPairingDiscoveryState
import com.jwoglom.controlx2.presentation.pairing.nextMobiInstructionStage
import com.jwoglom.controlx2.presentation.pairing.pairingSelectionStage
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.screens.sections.VerifiedPumpProfileCache
import com.jwoglom.controlx2.presentation.screens.sections.evaluateMobiPumpSafetyLimits
import com.jwoglom.controlx2.presentation.screens.sections.encodeVerifiedPumpProfileCache
import com.jwoglom.controlx2.presentation.screens.sections.messagePairToJson
import com.jwoglom.controlx2.presentation.screens.sections.profileSnapshots
import com.jwoglom.controlx2.presentation.screens.sections.verbosePumpMessage
import com.jwoglom.controlx2.presentation.util.ShouldLogToFile
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.AuthenticatedPhoneBolusSubmission
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.pumpx2.shared.Hex
import com.jwoglom.controlx2.shared.enums.DeviceRole
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.pumpTimeToLocalTz
import com.jwoglom.controlx2.shared.util.setupTimber
import com.jwoglom.controlx2.shared.util.triggerAppReload
import com.jwoglom.controlx2.shared.util.shortTime
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces1000Unit
import com.jwoglom.controlx2.shared.util.extractPumpSid
import com.jwoglom.controlx2.pump.pairing.PairingCodeEntry
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteCarbEntryRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ProfileStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionReleaseResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitChangeCartridgeModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.FillCannulaResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ResumePumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmHighLowAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmOutOfRangeAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmRiseFallAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteBgEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DisconnectPumpResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DismissNotificationResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetDexcomG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetMaxBasalLimitResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetMaxBolusLimitResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetQuickBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSiteChangeReminderResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSleepScheduleResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopTempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ChangeControlIQSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ChangeTimeDateResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CreateIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DeleteIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RenameIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetActiveIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.control.StopDexcomCGMSensorSessionResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMGlucoseAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMOORAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMRateAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQSleepScheduleResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBasalStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GetSavedG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.InsulinStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.RemindersResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Objects
import java.util.function.Supplier
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess


var dataStore = DataStore()
val LocalDataStore = compositionLocalOf { dataStore }

class MainActivity : FragmentActivity() {
    companion object {
        val ACTION_OPEN_BOLUS = "${BuildConfig.APPLICATION_ID}.action.OPEN_BOLUS"
        val ACTION_OPEN_TEMP_RATE = "${BuildConfig.APPLICATION_ID}.action.OPEN_TEMP_RATE"
        const val EXTRA_OPEN_BOLUS = "open_bolus"
        const val EXTRA_OPEN_TEMP_RATE = "open_temp_rate"
        const val EXTRA_BOLUS_UNITS = "units"
        const val EXTRA_BOLUS_CARBS = "carbs"
        const val EXTRA_BOLUS_BG = "bg"
        const val EXTRA_TEMP_RATE_PERCENT = "percent"
        const val EXTRA_TEMP_RATE_HOURS = "hours"
        const val EXTRA_TEMP_RATE_MINUTES = "minutes"

        private const val REQUEST_BT_PERMISSIONS = 2
        private const val SERVICE_SNAPSHOT_WITH_PROFILE = "include_profile"
    }

    private lateinit var messageBus: MessageBus
    private lateinit var messageListener: MessageListener
    private var messageListenerRegistered = false
    private var serviceSnapshotRequestedSinceStart = false
    private val sheetLaunchRequestState = mutableStateOf<SheetLaunchRequest?>(null)
    private var btPermissionsRequestInFlight = false
    private var notificationPermissionRequestInFlight = false
    private val pumpMessageSequence = AtomicLong(0L)
    private val pumpPairingDiscoveryState = PumpPairingDiscoveryState()
    private lateinit var deviceAuthenticator: DeviceAuthenticator

    private val applicationScope = CoroutineScope(SupervisorJob())
    private val historyLogDb by lazy { HistoryLogDatabase.getDatabase(this) }
    private val historyLogRepo by lazy { HistoryLogRepo(historyLogDb.historyLogDao()) }
    // hack: the ViewModel references the current pumpSid, so that streaming works in the UI, which we store lazily in the app preferences
    private val historyLogViewModel: HistoryLogViewModel by viewModels {
        HistoryLogViewModelFactory(historyLogRepo, Prefs(applicationContext).currentPumpSid())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("mobile UIActivity onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
        deviceAuthenticator = DeviceAuthenticator(this)
        setupTimber(
            "MUA",
            context = this,
            logToFile = true,
            shouldLog = ShouldLogToFile(this),
            writeCharacteristicFailedCallback = writeCharacteristicFailedCallback
        )
        val startDestination = determineStartDestination()
        Timber.d("startDestination=%s", startDestination)
        logIncomingIntent("onCreate", intent)
        if (startDestination == Screen.Landing.route) {
            sheetLaunchRequestState.value = parseSheetLaunchRequest(intent, "onCreate")
        }

        // On a clean install the history ViewModel is created before pump discovery and therefore
        // starts on SID -1. Keep it bound to the live pump SID so onboarding can transition to the
        // real pump database partition without restarting the app (and the authenticated BLE link).
        dataStore.pumpSid.observe(this) { pumpSid ->
            if (!Prefs(applicationContext).developerPreviewEnabled()) {
                historyLogViewModel.setPumpSid(pumpSid ?: -1)
            }
        }
        if (Prefs(applicationContext).developerPreviewEnabled()) {
            lifecycleScope.launch {
                val importedHistoryPumpSid = withContext(Dispatchers.IO) {
                    historyLogRepo.getMostRecentlyActivePumpSid()
                }
                if (importedHistoryPumpSid != null) {
                    Timber.i(
                        "Developer Preview selected imported history pump SID %d",
                        importedHistoryPumpSid,
                    )
                    historyLogViewModel.setPumpSid(importedHistoryPumpSid)
                } else {
                    Timber.i("Developer Preview has no imported pump history; using samples")
                }
            }
        }

        setContent {
            MobileApp(
                startDestination = startDestination,
                sendMessage = { path, message -> sendMessage(path, message) },
                sendPumpCommands = { type, messages -> sendPumpCommands(type, messages) },
                sendServiceBolusRequest = { bolusId, bolusParameters, unitBreakdown, dataSnapshot, timeSinceReset, correctionEnabled, glucoseWasEnteredManually, maxHourlyBolusConfirmed, lowInsulinAcknowledged ->
                    sendServiceBolusRequest(
                        bolusId,
                        bolusParameters,
                        unitBreakdown,
                        dataSnapshot,
                        timeSinceReset,
                        correctionEnabled,
                        glucoseWasEnteredManually,
                        maxHourlyBolusConfirmed,
                        lowInsulinAcknowledged,
                    )
                },
                requestBolusAuthentication = deviceAuthenticator::authenticate,
                requestSettingsAuthentication = deviceAuthenticator::authenticateSettings,
                requestActionAuthentication = deviceAuthenticator::authenticateAction,
                historyLogViewModel = historyLogViewModel,
                sheetLaunchRequest = sheetLaunchRequestState.value,
                onSheetLaunchHandled = { handledRequestId ->
                    if (sheetLaunchRequestState.value?.requestId == handledRequestId) {
                        sheetLaunchRequestState.value = null
                    }
                }
            )
        }

        // Load glucose unit preference into DataStore
        val savedGlucoseUnit = Prefs(applicationContext).glucoseUnit()
        if (savedGlucoseUnit != null) {
            dataStore.glucoseUnitPreference.value = savedGlucoseUnit
        } else {
            // Default to mg/dL if not set
            dataStore.glucoseUnitPreference.value = GlucoseUnit.MGDL
        }
        if (Prefs(applicationContext).developerPreviewEnabled()) {
            val prefs = Prefs(applicationContext)
            applyDeveloperPreviewProfile(
                dataStore,
                deliverySuspended = prefs.developerPreviewDeliverySuspended(),
                controlIqMode = prefs.developerPreviewControlIqMode(),
                controlIqEnabled = prefs.developerPreviewControlIqEnabled(),
                controlIqWeightPounds = prefs.developerPreviewControlIqWeightPounds(),
                controlIqTotalDailyInsulinUnits =
                    prefs.developerPreviewControlIqTotalDailyInsulin(),
                tempRate = prefs.developerPreviewTempRate(),
                maxBolusMilliunits = prefs.developerPreviewMaxBolusMilliunits(),
                basalLimitMilliunits = prefs.developerPreviewBasalLimitMilliunits(),
            )
        }
        dataStore.manualBolusOperation.value = Prefs(applicationContext).manualBolusOperation()
        dataStore.quickBolusOperation.value = Prefs(applicationContext).quickBolusOperation()
        dataStore.bolusPermissionOperation.value =
            Prefs(applicationContext).bolusPermissionOperation()

        // Always initialize MessageBus (factory handles Play Services fallback internally)
        messageBus = MessageBusFactory.createMessageBus(this)
        messageListener = object : MessageListener {
            override fun onMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
                handleMessageReceived(path, data, sourceNodeId)
            }
        }
        // Show warning dialog if Play Services missing (non-blocking)
        checkPlayServicesAndInitialize()

        startCommServiceWithPreconditions()
    }

    private val writeCharacteristicFailedCallback: (String) -> Unit = { uuid ->
        sendMessage(MessagePaths.TO_SERVER_WRITE_CHARACTERISTIC_FAILED_CALLBACK, uuid.toByteArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logIncomingIntent("onNewIntent", intent)

        if (determineStartDestination() != Screen.Landing.route) {
            Timber.i("Ignoring sheet launch while app is not in Landing flow")
            return
        }

        parseSheetLaunchRequest(intent, "onNewIntent")?.let {
            sheetLaunchRequestState.value = it
        }
    }

    override fun onResume() {
        Timber.i("activity onResume")
//        if (!mApiClient.isConnected && !mApiClient.isConnecting) {
//            mApiClient.connect()
//        }

        if (Prefs(applicationContext).developerPreviewEnabled()) {
            Timber.i("BTPermissionsCheck not started in developer preview")
        } else if (!Prefs(applicationContext).tosAccepted()) {
            Timber.i("BTPermissionsCheck not started because TOS not accepted")
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.i("BTPermissionsCheck not started because service not enabled")
        } else if (notificationPermissionRequestInFlight || btPermissionsRequestInFlight) {
            Timber.i("BTPermissionsCheck not started because a permission request is already in flight")
        } else {
            startBTPermissionsCheck()
        }

        super.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (::messageBus.isInitialized && ::messageListener.isInitialized &&
            !messageListenerRegistered) {
            messageBus.addMessageListener(messageListener)
            messageListenerRegistered = true
            Timber.i("MainActivity UI message listener registered")
        }
        requestAuthoritativeServiceSnapshot()
    }

    override fun onStop() {
        unregisterUiMessageListener()
        serviceSnapshotRequestedSinceStart = false
        super.onStop()
    }

    override fun onDestroy() {
        unregisterUiMessageListener()
        super.onDestroy()
    }

    private fun unregisterUiMessageListener() {
        if (::messageBus.isInitialized && ::messageListener.isInitialized &&
            messageListenerRegistered) {
            messageBus.removeMessageListener(messageListener)
            messageListenerRegistered = false
            Timber.i("MainActivity UI message listener unregistered")
        }
    }

    private fun resetProfileReadState() {
        dataStore.idpManager.value = IDPManager()
        dataStore.profileReadProgress.value = ProfileReadProgress()
        dataStore.initialProfileReadError.value = null
        dataStore.profileReadCompletedAtEpochMillis.value = null
    }

    /**
     * Requests the current service-owned connection state only after this Activity can receive the
     * reply. A complete profile snapshot is retained across an Activity recreation; a missing or
     * incomplete snapshot is restarted from ProfileStatus so later IDP responses cannot be applied
     * out of order.
     */
    private fun requestAuthoritativeServiceSnapshot() {
        if (!messageListenerRegistered || serviceSnapshotRequestedSinceStart ||
            !Prefs(applicationContext).serviceEnabled()) {
            return
        }
        val includeProfile = dataStore.pumpConnected.value != true ||
            dataStore.idpManager.value?.isComplete != true
        val payload = if (includeProfile) SERVICE_SNAPSHOT_WITH_PROFILE else ""
        serviceSnapshotRequestedSinceStart = true
        Timber.i("Requesting authoritative service snapshot (includeProfile=$includeProfile)")
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS, payload.toByteArray())
    }

    private fun startCommServiceWithPreconditions() {
        if (Prefs(applicationContext).developerPreviewEnabled()) {
            Timber.i("commService not started in developer preview")
        } else if (!Prefs(applicationContext).tosAccepted()) {
            Timber.i("commService not started because first TOS not accepted")
        } else if (!Prefs(applicationContext).serviceEnabled()) {
            Timber.i("commService not started because service not enabled")
        } else if (notificationPermissionRequestInFlight) {
            Timber.i("commService not started because notification permission request is in flight")
        } else if (!hasCommServicePermissions()) {
            Timber.i("commService not started because required permissions are not granted yet")
            startBTPermissionsCheck()
        } else {
            startCommService()
        }
    }

    private fun hasCommServicePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val hasConnectedDevicePermission =
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        if (!hasConnectedDevicePermission) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    private fun startCommService() {
        val role = Prefs(applicationContext).deviceRole()
        when (role) {
            DeviceRole.PUMP_HOST -> {
                Timber.i("starting CommService (pump-host mode)")
                val intent = Intent(applicationContext, CommService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
                applicationContext.bindService(intent, commServiceConnection, BIND_AUTO_CREATE)
            }
            DeviceRole.CLIENT -> {
                Timber.i("starting MobileClientService (client mode)")
                val intent = Intent(applicationContext, MobileClientService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
                applicationContext.bindService(intent, commServiceConnection, BIND_AUTO_CREATE)
            }
        }
    }

    private val commServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            //retrieve an instance of the service here from the IBinder returned
            //from the onBind method to communicate with
            Timber.i("CommService onServiceConnected")
            // onStart registers the listener before requesting the snapshot. If binding completed
            // unusually late, this is a no-op because the start-scoped request was already sent.
            requestAuthoritativeServiceSnapshot()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.i("CommService onServiceDisconnected")
        }
    }


    private fun checkPlayServicesAndInitialize(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        val resultCode = availability.isGooglePlayServicesAvailable(this)
        if (resultCode == ConnectionResult.SUCCESS) {
            return true
        } else {
            when (resultCode) {
                ConnectionResult.API_UNAVAILABLE -> {
                    AlertDialog.Builder(this)
                        .setMessage(
                            """
                        The 'Wear OS' application is not installed on this device.
                        This is required due to the current implementation.
                        To resolve, install the 'Wear OS' app from Google Play.
                        """.trimIndent()
                        )
                        .setPositiveButton("Install") { _, _ ->
                            openPlayStore("com.google.android.wearable.app")
                        }
                        .show()
                }
                else -> {
                    if (availability.isUserResolvableError(resultCode)) {
                        availability.getErrorDialog(this, resultCode, 1000) {
                            exitProcess(0)
                        }?.show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Error connecting to Google Play Services")
                            .setMessage("Error code: $resultCode")
                            .setPositiveButton("OK") { d, _ -> d.cancel() }
                            .show()
                    }
                }
            }
            return false
        }
    }

    private fun sendMessage(path: String, message: ByteArray) {
        Timber.i("mobile sendMessage: $path ${MessagePaths.payloadForLog(path, message)}")
        messageBus.sendMessage(path, message, MessageBusSender.MOBILE_UI)
    }

    private fun sendPumpCommands(type: SendType, msgs: List<Message>) {
        if (msgs.isEmpty()) return
        if (Prefs(applicationContext).developerPreviewEnabled()) {
            Timber.w("Ignoring pump commands in developer preview: %s", msgs)
            return
        }
        if (dataStore.pumpConnected.value != true) {
            Timber.w("Skipping sendPumpCommands while pump disconnected: %s", msgs)
            return
        }
        if (type == SendType.DEBUG_PROMPT) {
            synchronized (dataStore.debugPromptAwaitingResponses) {
                val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
                awaiting.addAll(msgs.map { it.responseClass.name })
                dataStore.debugPromptAwaitingResponses.value = awaiting
                Timber.d("added %s to debugPromptAwaitingResponses = %s", msgs, dataStore.debugPromptAwaitingResponses.value)
            }
        }
        sendMessage("${MessagePaths.PREFIX_TO_PUMP}${type.slug}", PumpMessageSerializer.toBulkBytes(msgs))
    }

    private fun sendServiceBolusRequest(
        bolusId: Int,
        params: BolusParameters,
        unitBreakdown: BolusCalcUnits,
        dataSnapshot: BolusCalcDataSnapshotResponse,
        timeSinceReset: TimeSinceResetResponse,
        correctionEnabled: Boolean,
        glucoseWasEnteredManually: Boolean,
        maxHourlyBolusConfirmed: Boolean,
        lowInsulinAcknowledged: Boolean,
    ) {
        if (Prefs(applicationContext).developerPreviewEnabled()) {
            Timber.w("Ignoring bolus request in developer preview")
            return
        }
        val maxBolusUnits = InsulinUnit.from1000To1(dataSnapshot.maxBolusAmount.toLong())
        if (!params.units.isFinite() || params.units < 0.05 || params.units > maxBolusUnits) {
            Timber.e(
                "Refusing bolus outside current pump limits: requested=%s max=%s",
                params.units,
                maxBolusUnits,
            )
            return
        }
        val safetyRequirements = evaluateMobiPumpSafetyLimits(
            requestedUnits = params.units,
            cartridgeRemainingInsulinUnits = dataSnapshot.cartridgeRemainingInsulin,
            maxBolusAmountMilliunits = dataSnapshot.maxBolusAmount,
            maxBolusHourlyTotalMilliunits = dataSnapshot.maxBolusHourlyTotal,
            maxBolusEventsExceeded = dataSnapshot.maxBolusEventsExceeded,
        )
        if (safetyRequirements.maxHourly != null && !maxHourlyBolusConfirmed) {
            Timber.e("Refusing bolus because Max Hourly Bolus was not confirmed")
            return
        }
        if (safetyRequirements.lowInsulin != null && !lowInsulinAcknowledged) {
            Timber.e("Refusing bolus because the Low Insulin warning was not acknowledged")
            return
        }
        val numCarbs = params.carbsGrams
        val bgValue = params.glucoseMgdl

        val corrVolume = plannedCorrectionVolumeMilliunits(
            unitBreakdown,
            correctionEnabled = correctionEnabled,
        )

        val iobUnits = dataSnapshot.iob
        val bolusRequest = createInitiateBolusRequest(
            totalUnits = params.units,
            bolusId = bolusId,
            foodUnits = unitBreakdown.fromCarbs,
            correctionVolumeMilliunits = corrVolume,
            carbsGrams = numCarbs,
            bgMgdl = bgValue,
            iobMilliunits = iobUnits,
        )

        val submission = AuthenticatedPhoneBolusSubmission(
            request = bolusRequest,
            postBolusRecords = createPostBolusRecordPlan(
                bgValue = bgValue,
                carbsGrams = numCarbs,
                bolusId = bolusId,
                dataSnapshot = dataSnapshot,
                glucoseWasEnteredManually = glucoseWasEnteredManually,
            ),
        )
        Timber.i(
            "sendServiceBolusRequest: numUnits=${bolusRequest.totalVolume} numCarbs=$numCarbs bgValue=$bgValue " +
                "foodVolume=${bolusRequest.foodVolume} correctionEnabled=$correctionEnabled corrVolume=$corrVolume " +
                "iobUnits=$iobUnits: bolusRequest=$bolusRequest; post-initiation records are staged",
        )
        this.sendMessage(MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE, submission.encode())
    }


    private var bolusConfirmDialog: AlertDialog? = null

    private fun showBolusConfirmDialog(units: String, requestBytes: ByteArray, source: String, autoApproveTimeout: Int) {
        bolusConfirmDialog?.dismiss()

        val message = if (autoApproveTimeout > 0) {
            "$units units from $source. Will auto-approve in ${autoApproveTimeout}s unless canceled."
        } else {
            "$units units from $source. Press Confirm to deliver."
        }

        bolusConfirmDialog = AlertDialog.Builder(this)
            .setTitle("Bolus Request")
            .setMessage(message)
            .setPositiveButton("Confirm ${units}u") { dialog, _ ->
                val intent = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "INITIATE")
                    putExtra("request", requestBytes)
                }
                val confirmPendingIntent = android.app.PendingIntent.getBroadcast(
                    this, 2001, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_ONE_SHOT
                )
                confirmPendingIntent.send()
                dialog.dismiss()
            }
            .setNegativeButton("Reject") { dialog, _ ->
                val intent = Intent(applicationContext, BolusNotificationBroadcastReceiver::class.java).apply {
                    putExtra("action", "REJECT")
                }
                val rejectPendingIntent = android.app.PendingIntent.getBroadcast(
                    this, 2000, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_ONE_SHOT
                )
                rejectPendingIntent.send()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // Message received from Wear or CommService via MessageBus
    private fun handleMessageReceived(path: String, data: ByteArray, sourceNodeId: String) {
        Timber.i("phone messageReceived: $path: ${MessagePaths.payloadForLog(path, data)} from $sourceNodeId")
        val inInitialSetupFlow = !Prefs(applicationContext).pumpSetupComplete()
        when (path) {
            MessagePaths.TO_SERVER_START_COMM -> {
                when (String(data)) {
                    "skip_notif_permission" -> {
                        startBTPermissionsCheck()
                        dataStore.pumpSetupStage.value =
                            dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                    }
                    else -> {
                        launchNotificationPermissionRequest { isGranted ->
                            if (isGranted) {
                                startBTPermissionsCheck()
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMPX2_INIT)
                            } else {
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
                            }
                        }
                    }
                }
            }

            MessagePaths.TO_SERVER_COMM_STARTED -> {
                if (inInitialSetupFlow) {
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SEARCHING_FOR_PUMP)
                } else {
                    Timber.i("comm-started: setup complete, skipping setup-stage transition")
                }
                // Acknowledge receipt to stop periodic sender
                sendMessage(MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED, "".toByteArray())
            }

            MessagePaths.TO_SERVER_START_PUMP_FINDER -> {
                when (String(data)) {
                    "skip_notif_permission" -> {
                        startBTPermissionsCheck()
                        dataStore.pumpSetupStage.value =
                            dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
                    }
                    else -> {
                        launchNotificationPermissionRequest { isGranted ->
                            if (isGranted) {
                                startBTPermissionsCheck()
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
                            } else {
                                dataStore.pumpSetupStage.value =
                                    dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PERMISSIONS_NOT_GRANTED)
                            }
                        }
                    }
                }
            }

            MessagePaths.TO_SERVER_PUMP_FINDER_STARTED -> {
                if (dataStore.pumpSetupStage.value == PumpSetupStage.WAITING_PUMP_FINDER_INIT) {
                    pumpPairingDiscoveryState.reset()
                    dataStore.pumpFinderPumps.value = emptyList()
                    dataStore.selectedPumpFinderMac.value = null
                    dataStore.setupDeviceName.value = null
                    dataStore.pumpReadyState.value = null
                }
                if (inInitialSetupFlow) {
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS)
                } else {
                    Timber.i("pump-finder-started: setup complete, skipping setup-stage transition")
                }
                // Acknowledge receipt to stop periodic sender
                sendMessage(MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED, "".toByteArray())
                // Rehydrate the current in-memory scan after activity/process recreation. The
                // finder replays only its current generation and never persisted readiness.
                sendMessage(MessagePaths.TO_SERVER_CHECK_PUMP_FINDER_FOUND_PUMPS, "".toByteArray())
            }

            MessagePaths.FROM_PUMP_PUMP_FINDER_FOUND_PUMPS -> {
                val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
                val candidates = pumpPairingDiscoveryState.observeFoundPumpsPayload(
                    payload = String(data),
                    nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                )
                dataStore.pumpFinderPumps.value = candidates
                if (dataStore.pumpSetupStage.value == PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS) {
                    pairingSelectionStage(candidates, nowElapsedRealtimeMillis)?.let { selectionStage ->
                        dataStore.pumpSetupStage.value = selectionStage
                    }
                }
            }

            MessagePaths.FROM_PUMP_PUMP_FINDER_PUMP_DISCOVERED -> {
                val payload = String(data)
                val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
                val candidate = pumpPairingDiscoveryState.observeDiscoveredPayload(
                    payload = payload,
                    nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                )
                if (candidate == null) {
                    Timber.w("pump-finder-pump-discovered: ignored malformed discovery payload")
                } else {
                    Timber.i(
                        "pump-finder-pump-discovered: name=%s mac=%s readyState=%s",
                        candidate.name ?: "unnamed",
                        candidate.address,
                        candidate.readyState,
                    )
                    val candidates = pumpPairingDiscoveryState.candidates()
                    dataStore.pumpFinderPumps.value = candidates
                    val routedMobiStage = nextMobiInstructionStage(
                        currentStage = dataStore.pumpSetupStage.value,
                        candidate = candidate,
                        nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                        allCandidates = candidates,
                    )
                    when {
                        routedMobiStage != null -> dataStore.pumpSetupStage.value = routedMobiStage
                        dataStore.pumpSetupStage.value == PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS -> {
                            pairingSelectionStage(candidates, nowElapsedRealtimeMillis)?.let { selectionStage ->
                                dataStore.pumpSetupStage.value = selectionStage
                            }
                        }
                    }
                    if (dataStore.selectedPumpFinderMac.value == candidate.address) {
                        candidate.name?.let { dataStore.setupDeviceName.value = it }
                        dataStore.pumpReadyState.value = candidate.readyState
                    }
                }
            }

            MessagePaths.TO_SERVER_SET_PAIRING_CODE -> {
                val pairingCodeText = String(data)
                Toast.makeText(applicationContext, "Pairing code saved", Toast.LENGTH_SHORT).show()
                when (dataStore.pumpSetupStage.value) {
                    PumpSetupStage.WAITING_PUMP_FINDER_CLEANUP -> {
                        Prefs(applicationContext).setPumpFinderServiceEnabled(false)
                        PairingCodeEntry.applyForInitialPumpComm(
                            context = applicationContext,
                            code = pairingCodeText,
                            sendMessage = ::sendMessage,
                        )
                    }
                    PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE -> {
                        PairingCodeEntry.applyForRePair(
                            context = applicationContext,
                            code = pairingCodeText,
                            sendMessage = ::sendMessage,
                        )
                    }
                    else -> {
                        Timber.w(
                            "TO_SERVER_SET_PAIRING_CODE: unexpected stage=%s, ignoring",
                            dataStore.pumpSetupStage.value,
                        )
                    }
                }
            }

            MessagePaths.TO_SERVER_APP_RELOAD -> {
                triggerAppReload(applicationContext)
            }

            MessagePaths.TO_SERVER_CONNECTED -> {
                dataStore.watchConnected.value = true
            }

            MessagePaths.FROM_PUMP_PUMP_DISCOVERED -> {
                val payload = String(data)
                val setupDeviceName = payload.substringBefore(";;")
                val readyState = Strings.emptyToNull(payload.substringAfter(";;", "UNKNOWN")) ?: "UNKNOWN"
                Timber.i("pump-discovered: name=$setupDeviceName readyState=$readyState")
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCOVERED)
                dataStore.setupDeviceName.value = setupDeviceName
                dataStore.pumpReadyState.value = PumpReadyState.valueOf(readyState)
                extractPumpSid(setupDeviceName)?.let {
                    dataStore.pumpSid.value = it
                }
            }

            MessagePaths.FROM_PUMP_PUMP_MODEL -> {
                dataStore.setupDeviceModel.value = String(data)
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_MODEL_METADATA)
            }

            MessagePaths.FROM_PUMP_INITIAL_PUMP_CONNECTION -> {
                dataStore.setupDeviceName.value = String(data)
                extractPumpSid(String(data))?.let {
                    dataStore.pumpSid.value = it
                }
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INITIAL_PUMP_CONNECTION)
            }

            MessagePaths.FROM_PUMP_ENTERED_PAIRING_CODE -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_SENDING_PAIRING_CODE)
            }

            MessagePaths.FROM_PUMP_MISSING_PAIRING_CODE -> {
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_WAITING_FOR_PAIRING_CODE)
            }

            MessagePaths.FROM_PUMP_INVALID_PAIRING_CODE -> {
                Timber.w("Pump rejected the pairing code")
                PumpState.setPairingCode(applicationContext, "")
                dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_INVALID_PAIRING_CODE)
                sendMessage(MessagePaths.TO_SERVER_STOP_COMM, "invalid_pairing_code".toByteArray())
            }

            MessagePaths.FROM_PUMP_PUMP_CRITICAL_ERROR -> {
                Timber.w("pump-critical-error: ${String(data)}")
                dataStore.pumpCriticalError.value = Pair(String(data), Instant.now())
            }

            MessagePaths.FROM_PUMP_INITIAL_PROFILE_READ_FAILED -> {
                dataStore.initialProfileReadError.value = String(data)
            }

            MessagePaths.FROM_PUMP_PUMP_CONNECTED -> {
                val isNewConnectionForUi = dataStore.pumpConnected.value != true
                if (isNewConnectionForUi) {
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(
                        if (inInitialSetupFlow) {
                            PumpSetupStage.PUMPX2_READING_PUMP_PROFILES
                        } else {
                            PumpSetupStage.PUMPX2_PUMP_CONNECTED
                        },
                    )
                }
                dataStore.setupDeviceName.value = String(data)
                extractPumpSid(String(data))?.let {
                    dataStore.pumpSid.value = it
                }
                if (isNewConnectionForUi) {
                    // A profile snapshot belongs to one live pump connection. Duplicate service
                    // snapshots must not erase an already-complete read for that connection.
                    resetProfileReadState()
                }
                dataStore.pumpConnected.value = true
                dataStore.pumpLastConnectionTimestamp.value = Instant.now()
            }

            // on explicit disconnection
            MessagePaths.FROM_PUMP_PUMP_DISCONNECTED -> {
                val isNewDisconnectionForUi = dataStore.pumpConnected.value != false
                if (isNewDisconnectionForUi) {
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                    resetProfileReadState()
                }
                dataStore.setupDeviceModel.value = String(data)
                dataStore.pumpConnected.value = false
            }

            // on implicit disconnection (i.e. we didn't get the explicit disconnect)
            MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED -> {
                val isNewDisconnectionForUi = dataStore.pumpConnected.value != false
                if (isNewDisconnectionForUi) {
                    Timber.i("tracked authoritative implicit disconnection: ${String(data)}")
                    dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(PumpSetupStage.PUMPX2_PUMP_DISCONNECTED)
                    resetProfileReadState()
                }
                dataStore.pumpConnected.value = false
            }

            MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG -> {
                val parts = String(data).split("|")
                if (parts.size >= 3) {
                    val requestHex = parts[0]
                    val source = parts[1]
                    val autoApproveTimeout = parts[2].toIntOrNull() ?: 0
                    val requestBytes = Hex.decodeHex(requestHex)
                    val request = PumpMessageSerializer.fromBytes(requestBytes) as? com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
                    if (request != null) {
                        val units = com.jwoglom.controlx2.shared.util.twoDecimalPlaces(
                            com.jwoglom.pumpx2.pump.messages.models.InsulinUnit.from1000To1(request.totalVolume))
                        runOnUiThread {
                            showBolusConfirmDialog(units, requestBytes, source, autoApproveTimeout)
                        }
                    }
                }
            }

            MessagePaths.FROM_PUMP_RECEIVE_MESSAGE -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(data)
                onPumpMessageReceived(pumpMessage, false)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }
            MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE -> {
                val pumpMessage = PumpMessageSerializer.fromBytes(data)
                onPumpMessageReceived(pumpMessage, true)
                dataStore.pumpLastMessageTimestamp.value = Instant.now()
            }

            MessagePaths.TO_CLIENT_AUTOMATIC_BOLUS_CANCEL_RESULT -> {
                AutomaticBolusCancelResult.decode(data)?.let {
                    dataStore.automaticBolusCancelResult.value = it
                } ?: Timber.e("Invalid automatic bolus cancellation result")
            }

            MessagePaths.TO_CLIENT_MANUAL_BOLUS_CANCEL_RESULT -> {
                ManualBolusCancelResult.decode(data)?.let {
                    dataStore.manualBolusCancelResult.value = it
                } ?: Timber.e("Invalid manual bolus cancellation result")
            }

            MessagePaths.TO_CLIENT_MANUAL_BOLUS_OPERATION -> {
                ManualBolusOperation.decode(data)?.let {
                    Prefs(applicationContext).setManualBolusOperation(it)
                    dataStore.manualBolusOperation.value = it
                } ?: Timber.e("Invalid manual bolus operation")
            }

            MessagePaths.TO_CLIENT_QUICK_BOLUS_CANCEL_RESULT -> {
                QuickBolusCancelResult.decode(data)?.let {
                    dataStore.quickBolusCancelResult.value = it
                } ?: Timber.e("Invalid Quick Bolus cancellation result")
            }

            MessagePaths.TO_CLIENT_QUICK_BOLUS_OPERATION -> {
                QuickBolusOperation.decode(data)?.let {
                    Prefs(applicationContext).setQuickBolusOperation(it)
                    dataStore.quickBolusOperation.value = it
                } ?: Timber.e("Invalid Quick Bolus operation")
            }

            MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE -> {
                com.jwoglom.controlx2.shared.PumpHydrationState.decode(data)?.let {
                    dataStore.pumpHydrationState.value = it
                } ?: Timber.e("Invalid pump hydration state")
            }

            MessagePaths.TO_CLIENT_BOLUS_PERMISSION_OPERATION -> {
                BolusPermissionOperation.decode(data)?.let {
                    Prefs(applicationContext).setBolusPermissionOperation(it)
                    dataStore.bolusPermissionOperation.value = it
                    if (it.phase in setOf(
                            BolusPermissionOperationPhase.REQUESTING,
                            BolusPermissionOperationPhase.RELEASED,
                            BolusPermissionOperationPhase.DENIED,
                            BolusPermissionOperationPhase.INITIATION_REJECTED,
                            BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
                        )
                    ) {
                        // A response object from an earlier workflow must never authorize the
                        // newly opened workflow or remain visible after that permission ended.
                        dataStore.bolusPermissionResponse.value = null
                        PumpStateSupplier.inProgressBolusId = Supplier { null }
                    }
                } ?: Timber.e("Invalid bolus permission operation")
            }

            MessagePaths.FROM_PUMP_DEBUG_MESSAGE_CACHE -> {
                val processed = PumpMessageSerializer.fromDebugMessageCacheBytes(data)
                dataStore.debugMessageCache.value = processed
            }

            MessagePaths.FROM_PUMP_DEBUG_HISTORYLOG_CACHE -> {
                val processed = PumpMessageSerializer.fromDebugHistoryLogCacheBytes(data)
                var mp = dataStore.historyLogCache.value
                if (mp == null) {
                     mp = mutableMapOf()
                }
                mp.putAll(processed)
                dataStore.historyLogCache.value = mp
            }
        }
    }

    private fun onPumpMessageReceived(message: Message, cached: Boolean) {
        val eventSequence = pumpMessageSequence.incrementAndGet()
        val eventReceivedAt = Instant.now()
        dataStore.latestPumpMessageSequence.value = eventSequence
        when (message) {
            is TimeSinceResetResponse -> dataStore.timeSinceResetEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is LoadStatusResponse -> dataStore.loadStatusEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is RemindersResponse -> dataStore.remindersEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is FillCannulaResponse -> dataStore.fillCannulaResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is EnterFillTubingModeResponse -> dataStore.enterFillTubingModeResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ExitFillTubingModeResponse -> dataStore.exitFillTubingModeResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ResumePumpingResponse -> dataStore.resumePumpingResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SuspendPumpingResponse -> dataStore.suspendPumpingResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is DisconnectPumpResponse -> dataStore.disconnectPumpResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is DismissNotificationResponse -> dataStore.dismissNotificationResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is FillCannulaStateStreamResponse -> dataStore.fillCannulaStateEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SetSiteChangeReminderResponse -> dataStore.siteChangeReminderResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CGMStatusResponse -> dataStore.cgmStatusEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CgmStatusV2Response -> dataStore.cgmStatusV2Event.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is HomeScreenMirrorResponse -> dataStore.homeScreenMirrorEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CurrentEGVGuiDataResponse -> dataStore.currentEgvEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is AlertStatusResponse -> dataStore.alertStatusEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SetDexcomG7PairingCodeResponse -> dataStore.setG7PairingCodeResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is GetSavedG7PairingCodeResponse -> dataStore.savedG7PairingCodeEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is StopDexcomCGMSensorSessionResponse -> dataStore.stopCgmSessionResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is RemoteBgEntryResponse -> dataStore.remoteBgEntryResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CGMGlucoseAlertSettingsResponse -> dataStore.cgmGlucoseAlertSettingsEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CGMRateAlertSettingsResponse -> dataStore.cgmRateAlertSettingsEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CGMOORAlertSettingsResponse -> dataStore.cgmOutOfRangeAlertSettingsEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CgmHighLowAlertResponse -> dataStore.cgmHighLowAlertResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CgmRiseFallAlertResponse -> dataStore.cgmRiseFallAlertResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CgmOutOfRangeAlertResponse -> dataStore.cgmOutOfRangeAlertResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ControlIQInfoAbstractResponse -> dataStore.controlIqInfoEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ControlIQSleepScheduleResponse -> dataStore.controlIqSleepScheduleEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ChangeControlIQSettingsResponse ->
                dataStore.changeControlIqSettingsResponseEvent.value =
                    PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SetSleepScheduleResponse -> dataStore.setSleepScheduleResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is ChangeTimeDateResponse -> dataStore.changeTimeDateResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SetTempRateResponse -> dataStore.setTempRateResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is SetQuickBolusSettingsResponse ->
                dataStore.setQuickBolusSettingsResponseEvent.value =
                    PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is PumpGlobalsResponse -> dataStore.pumpGlobalsEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is StopTempRateResponse -> dataStore.stopTempRateResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is TempRateResponse -> dataStore.tempRateResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            is CreateIDPResponse,
            is DeleteIDPResponse,
            is RenameIDPResponse,
            is SetActiveIDPResponse,
            is SetIDPSegmentResponse,
            is SetIDPSettingsResponse -> dataStore.profileMutationResponseEvent.value =
                PumpMessageEvent(eventSequence, eventReceivedAt, message as StatusMessage, cached)
        }

        if (dataStore.debugPromptAwaitingResponses.value?.contains(message.javaClass.name) == true) {
            synchronized (dataStore.debugPromptAwaitingResponses) {
                val awaiting = dataStore.debugPromptAwaitingResponses.value ?: mutableSetOf()
                awaiting.remove(message.javaClass.name)
                dataStore.debugPromptAwaitingResponses.value = awaiting
                Timber.d("removed %s from debugPromptAwaitingResponses = %s", message.javaClass.name, dataStore.debugPromptAwaitingResponses.value)
            }
            fun setClipboard(str: String) {
                val clipboard: ClipboardManager =
                    getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(str, str)
                clipboard.setPrimaryClip(clip)
            }
            val verboseStr = verbosePumpMessage(message)
            AlertDialog.Builder(this)
                .setMessage(verboseStr)
                .setNeutralButton("Copy JSON") { dialog, which ->
                    setClipboard(messagePairToJson(Pair(message, Instant.now())))
                }
                .setNegativeButton("Copy") { dialog, which ->
                    setClipboard(verboseStr)
                }
                .setPositiveButton("OK") { dialog, which -> dialog.cancel() }
                .show()
        }

        // Preserve delivery-limit write responses for the Safety Limits screen before the generic
        // status handler returns on a pump rejection.
        when (message) {
            is SetMaxBolusLimitResponse -> dataStore.setMaxBolusLimitResponse.value = message
            is SetMaxBasalLimitResponse -> dataStore.setMaxBasalLimitResponse.value = message
        }

        // Always process error handlers first
        when (message) {
            is StatusMessage -> {
                if (!message.isStatusOK) {
                    // Notification dismissal has its own response-aware UI. A generic dialog here
                    // cannot identify the notification and previously reported rejected duplicate
                    // requests even after the first request succeeded.
                    if (message !is DismissNotificationResponse) {
                        unsuccessfulAlert(message.messageName())
                    }
                    return
                }
            }
        }

        if (NotificationBundle.isNotificationResponse(message)) {
            synchronized(dataStore.notificationBundle) {
                val updated = (dataStore.notificationBundle.value ?: NotificationBundle()).add(message)
                // re-create to trigger state update via object change
                dataStore.notificationBundle.value = NotificationBundle(updated)
                dataStore.notificationStatusEvent.value =
                    PumpMessageEvent(eventSequence, eventReceivedAt, message, cached)
            }
        }

        if (IDPManager.isIDPManagerResponse(message)) {
            synchronized(dataStore.idpManager) {
                val currentManager = dataStore.idpManager.value ?: IDPManager()
                val awaitingProfileStatus =
                    currentManager.nextMessages().firstOrNull() is ProfileStatusRequest
                if (awaitingProfileStatus &&
                    (message is IDPSettingsResponse || message is IDPSegmentResponse)) {
                    Timber.w(
                        "Ignoring out-of-order %s while awaiting ProfileStatusResponse",
                        message.messageName(),
                    )
                    return
                }
                dataStore.initialProfileReadError.value = null
                val wasComplete = currentManager.isComplete
                dataStore.profileReadProgress.value =
                    (dataStore.profileReadProgress.value ?: ProfileReadProgress()).process(message)
                val updated = currentManager.processMessage(message)
                // re-create to trigger state update via object change
                val newIdpManager = IDPManager(updated)
                dataStore.idpManager.value = newIdpManager

                // Upload profile to Nightscout when all profile data is loaded
                if (newIdpManager.isComplete && !wasComplete) {
                    val completedAt = System.currentTimeMillis()
                    dataStore.profileReadCompletedAtEpochMillis.value = completedAt
                    persistVerifiedPumpProfileSnapshot(newIdpManager, completedAt)
                    if (!Prefs(applicationContext).pumpSetupComplete()) {
                        val completedStage = if (
                            Prefs(applicationContext).onboardingTimeDecisionComplete()
                        ) {
                            PumpSetupStage.PUMPX2_SYNC_PUMP_DATA
                        } else {
                            PumpSetupStage.PUMPX2_PUMP_CONNECTED
                        }
                        dataStore.pumpSetupStage.value = dataStore.pumpSetupStage.value?.nextStage(
                            completedStage,
                        )
                    }
                    com.jwoglom.controlx2.sync.nightscout.NightscoutSyncWorker
                        .uploadProfileIfRunning(newIdpManager)
                }
            }
        }
        when (message) {
            is CurrentBatteryAbstractResponse -> {
                dataStore.batteryPercent.value = message.batteryPercent
                if (message is CurrentBatteryV2Response) {
                    dataStore.batteryCharging.value = message.isCharging
                }
            }
            is ControlIQIOBResponse -> {
                dataStore.iobUnits.value = InsulinUnit.from1000To1(message.pumpDisplayedIOB)
            }
            is ControlIQInfoAbstractResponse -> {
                dataStore.controlIQMode.value = when (message.currentUserModeType) {
                    ControlIQInfoAbstractResponse.UserModeType.STANDARD -> UserMode.NONE
                    ControlIQInfoAbstractResponse.UserModeType.SLEEP -> UserMode.SLEEP
                    ControlIQInfoAbstractResponse.UserModeType.EXERCISE -> UserMode.EXERCISE
                    else -> UserMode.UNKNOWN
                }
                dataStore.controlIQInfoResponse.value = message
                // Extract CIQ fields via reflection since abstract getters are package-private in Java
                try {
                    val closedLoopMethod = message.javaClass.getMethod("getClosedLoopEnabled")
                    val weightMethod = message.javaClass.getMethod("getWeight")
                    val tdiMethod = message.javaClass.getMethod("getTotalDailyInsulin")
                    dataStore.controlIQEnabled.value = closedLoopMethod.invoke(message) as Boolean
                    dataStore.controlIQWeight.value = weightMethod.invoke(message) as Int
                    dataStore.controlIQTotalDailyInsulin.value = tdiMethod.invoke(message) as Int
                } catch (e: Exception) {
                    Timber.w(e, "Failed to extract CIQ info fields")
                }
                dataStore.controlIQWeightUnit.value = when (message.weightUnit) {
                    ControlIQInfoAbstractResponse.WeightUnit.POUNDS -> "lbs"
                    ControlIQInfoAbstractResponse.WeightUnit.KILOGRAMS -> "kg"
                    else -> ""
                }
            }
            is ControlIQSleepScheduleResponse -> {
                dataStore.controlIQSleepScheduleResponse.value = message
            }
            is BasalLimitSettingsResponse -> {
                dataStore.basalLimitSettingsResponse.value = message
            }
            is InsulinStatusResponse -> {
                dataStore.cartridgeRemainingUnits.value = message.currentInsulinAmount
                dataStore.cartridgeRemainingEstimateFlag.value = message.isEstimate
                dataStore.cartridgeRemainingEstimate.value = message.isEstimate != 0
                dataStore.cartridgeLowAlertUnits.value = message.insulinLowAmount
            }
            is LastBolusStatusAbstractResponse -> {
                dataStore.lastBolusSource.value = message.bolusSource
                dataStore.lastBolusStatus.value = if (message.timestamp > 0)
                    "${twoDecimalPlaces1000Unit(message.deliveredVolume)}u at ${shortTime(pumpTimeToLocalTz(message.timestampInstant))}"
                else
                    null
                dataStore.lastBolusStatusResponse.value = message
            }
            is HomeScreenMirrorResponse -> {
                dataStore.controlIQStatus.value = when (message.apControlStateIcon) {
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY -> "On"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_RED_BIQ_CIQ_BASAL_SUSPENDED -> "Suspended"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_BLUE_CIQ_INCREASE_BASAL -> "Increase"
                    HomeScreenMirrorResponse.ApControlStateIcon.STATE_GRAY_ORANGE_CIQ_ATTENUATION_BASAL -> "Reduced"
                    else -> "CIQ Off"
                }
                dataStore.cgmStatusText.value = when (message.cgmAlertIcon) {
                    HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_1, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_2, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_3, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_4 -> "Starting up"
                    HomeScreenMirrorResponse.CGMAlertIcon.CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.STARTUP_CALIBRATE, HomeScreenMirrorResponse.CGMAlertIcon.CHECKMARK_BLOOD_DROP -> "Calibration Needed"
                    HomeScreenMirrorResponse.CGMAlertIcon.ERROR_HIGH_WEDGE, HomeScreenMirrorResponse.CGMAlertIcon.ERROR_LOW_WEDGE -> "Error"
                    HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_SENSOR -> "Replace Sensor"
                    HomeScreenMirrorResponse.CGMAlertIcon.REPLACE_TRANSMITTER -> "Replace Transmitter"
                    HomeScreenMirrorResponse.CGMAlertIcon.OUT_OF_RANGE -> "Out Of Range"
                    HomeScreenMirrorResponse.CGMAlertIcon.FAILED_SENSOR -> "Sensor Failed"
                    HomeScreenMirrorResponse.CGMAlertIcon.TRIPLE_DASHES -> "---"
                    else -> ""
                }
                dataStore.cgmHighLowState.value = when (message.cgmAlertIcon) {
                    HomeScreenMirrorResponse.CGMAlertIcon.LOW -> "LOW"
                    HomeScreenMirrorResponse.CGMAlertIcon.HIGH -> "HIGH"
                    else -> "IN_RANGE"
                }
                dataStore.cgmDeltaArrow.value = message.cgmTrendIcon.arrow()
                val basalStatus = when (message.basalStatusIcon) {
                    HomeScreenMirrorResponse.BasalStatusIcon.BASAL -> BasalStatus.ON
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_BASAL -> BasalStatus.ZERO
                    HomeScreenMirrorResponse.BasalStatusIcon.TEMP_RATE -> BasalStatus.TEMP_RATE
                    HomeScreenMirrorResponse.BasalStatusIcon.ZERO_TEMP_RATE -> BasalStatus.ZERO_TEMP_RATE
                    HomeScreenMirrorResponse.BasalStatusIcon.SUSPEND -> BasalStatus.PUMP_SUSPENDED
                    HomeScreenMirrorResponse.BasalStatusIcon.HYPO_SUSPEND_BASAL_IQ -> BasalStatus.BASALIQ_SUSPENDED
                    HomeScreenMirrorResponse.BasalStatusIcon.INCREASE_BASAL -> BasalStatus.CONTROLIQ_INCREASED
                    HomeScreenMirrorResponse.BasalStatusIcon.ATTENUATED_BASAL -> BasalStatus.CONTROLIQ_REDUCED
                    else -> BasalStatus.UNKNOWN
                }
                dataStore.basalStatus.value = basalStatus
                dataStore.basalStatusEvent.value = PumpStateEvent(
                    eventSequence,
                    eventReceivedAt,
                    basalStatus,
                )
                dataStore.cartridgeRemainingEstimate.value = message.remainingInsulinPlusIcon
            }
            is CurrentBasalStatusResponse -> {
                dataStore.basalRate.value = "${twoDecimalPlaces1000Unit(message.currentBasalRate)}u"
            }
            is TempRateResponse -> {
                dataStore.tempRateActive.value = message.active
                dataStore.tempRateDetails.value = message
            }
            is CGMStatusResponse -> {
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> CGMSessionState.ACTIVE
                    CGMStatusResponse.SessionState.SESSION_STOPPED -> CGMSessionState.STOPPED
                    CGMStatusResponse.SessionState.SESSION_START_PENDING -> CGMSessionState.STARTING
                    CGMStatusResponse.SessionState.SESSION_STOP_PENDING -> CGMSessionState.STOPPING
                    else -> null
                }
                dataStore.cgmSessionExpireRelative.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTimeAgo(
                            pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                                .plus(10, ChronoUnit.DAYS),
                        suffix = "left")
                    else -> ""
                }
                dataStore.cgmSessionExpireExact.value = when (message.sessionState) {
                    CGMStatusResponse.SessionState.SESSION_ACTIVE -> shortTime(
                        pumpTimeToLocalTz(message.sensorStartedTimestampInstant)
                            .plus(10, ChronoUnit.DAYS))
                    else -> ""
                }
                dataStore.cgmTransmitterStatus.value = when (message.transmitterBatteryStatus) {
                    CGMStatusResponse.TransmitterBatteryStatus.ERROR -> "Error"
                    CGMStatusResponse.TransmitterBatteryStatus.EXPIRED -> "Expired"
                    CGMStatusResponse.TransmitterBatteryStatus.OK -> "OK"
                    CGMStatusResponse.TransmitterBatteryStatus.OUT_OF_RANGE -> "OOR"
                    else -> ""
                }
            }
            is CgmStatusV2Response -> {
                val previousSensorStart = dataStore.cgmStatusV2Response.value
                    ?.sensorStartedTimestamp
                dataStore.cgmStatusV2Response.value = message
                dataStore.cgmSessionState.value = when (message.sessionState) {
                    CgmStatusV2Response.SessionState.SESSION_ACTIVE -> CGMSessionState.ACTIVE
                    CgmStatusV2Response.SessionState.STATUS_STOPPED -> CGMSessionState.STOPPED
                    CgmStatusV2Response.SessionState.STATUS_START_PENDING -> CGMSessionState.STARTING
                    CgmStatusV2Response.SessionState.SESSION_STOP_PENDING -> CGMSessionState.STOPPING
                    else -> null
                }
                if (message.sessionState != CgmStatusV2Response.SessionState.SESSION_ACTIVE ||
                    (previousSensorStart != null &&
                        previousSensorStart != message.sensorStartedTimestamp)
                ) {
                    // Keep chart history in the database, but never let an old session's cached
                    // current value survive into pairing/startup or a replacement sensor.
                    dataStore.cgmReadingSensorStartedTimestamp.value = null
                }
                dataStore.cgmTransmitterStatus.value = when (message.transmitterBatteryStatus) {
                    CgmStatusV2Response.TransmitterBatteryStatus.ERROR -> "Error"
                    CgmStatusV2Response.TransmitterBatteryStatus.EXPIRED -> "Expired"
                    CgmStatusV2Response.TransmitterBatteryStatus.OKAY -> "OK"
                    CgmStatusV2Response.TransmitterBatteryStatus.OUT_OF_RANGE -> "OOR"
                    else -> ""
                }
            }
            is CGMGlucoseAlertSettingsResponse -> {
                dataStore.cgmGlucoseAlertSettingsResponse.value = message
            }
            is CGMRateAlertSettingsResponse -> {
                dataStore.cgmRateAlertSettingsResponse.value = message
            }
            is CGMOORAlertSettingsResponse -> {
                dataStore.cgmOutOfRangeAlertSettingsResponse.value = message
            }
            is CurrentEGVGuiDataResponse -> {
                dataStore.cgmReading.value = message.cgmReading
                dataStore.cgmDelta.value = message.trendRate
                dataStore.cgmReadingSensorStartedTimestamp.value =
                    dataStore.cgmStatusV2Response.value
                        ?.takeIf {
                            it.sessionState == CgmStatusV2Response.SessionState.SESSION_ACTIVE
                        }
                        ?.sensorStartedTimestamp
            }
            is GetSavedG7PairingCodeResponse -> {
                dataStore.savedG7PairingCode.value = message.pairingCode
            }
            is BolusCalcDataSnapshotResponse -> {
                if (!cached) {
                    dataStore.bolusCalcDataSnapshot.value = message
                }
            }
            is LastBGResponse -> {
                dataStore.bolusCalcLastBG.value = message
            }
            is PumpGlobalsResponse -> {
                dataStore.pumpGlobalsResponse.value = message
            }
            is GlobalMaxBolusSettingsResponse -> {
                dataStore.maxBolusAmount.value = message.maxBolus
                dataStore.globalMaxBolusSettingsResponse.value = message
            }
            is HistoryLogStatusResponse -> {
                dataStore.historyLogStatus.value = message
            }
            is BolusPermissionResponse -> {
                dataStore.bolusPermissionResponse.value = message
                PumpStateSupplier.inProgressBolusId = Supplier {
                    message.bolusId.takeIf { message.isPermissionGranted && it > 0 }
                }
            }
            is BolusPermissionReleaseResponse -> {
                if (message.releaseStatus == BolusPermissionReleaseResponse.ReleaseStatus.SUCCESS) {
                    dataStore.bolusPermissionResponse.value = null
                    PumpStateSupplier.inProgressBolusId = Supplier { null }
                }
            }
            is RemoteCarbEntryResponse -> {
                dataStore.bolusCarbEntryResponse.value = message
            }
            is InitiateBolusResponse -> {
                dataStore.bolusInitiateResponse.value = message
                runOnUiThread { bolusConfirmDialog?.dismiss() }
            }
            is CancelBolusResponse -> {
                if (dataStore.bolusCancelResponse.value == null || message.wasCancelled()) {
                    dataStore.bolusCancelResponse.value = message
                    PumpStateSupplier.inProgressBolusId = Supplier { null }
                } else {
                    Timber.w("skipping population of bolusCancelResponse: $message because a successful cancellation already existed in the state: ${dataStore.bolusCancelResponse.value}");
                }
            }
            is CurrentBolusStatusResponse -> {
                dataStore.bolusCurrentResponse.value = message
            }
            is TimeSinceResetResponse -> {
                dataStore.timeSinceResetResponse.value = message
            }
            is LoadStatusResponse -> {
                dataStore.loadStatusResponse.value = message
            }
            is RemindersResponse -> {
                dataStore.remindersResponse.value = message
            }

            is EnterChangeCartridgeModeStateStreamResponse -> {
                dataStore.enterChangeCartridgeState.value = message
            }
            is DetectingCartridgeStateStreamResponse -> {
                dataStore.detectingCartridgeState.value = message
            }
            is FillTubingStateStreamResponse -> {
                dataStore.fillTubingState.value = message
            }
            is ExitFillTubingModeStateStreamResponse -> {
                dataStore.exitFillTubingState.value = message
            }
            is FillCannulaStateStreamResponse -> {
                dataStore.fillCannulaState.value = message
            }
            is PumpingStateStreamResponse -> {
                dataStore.pumpingState.value = message
            }
            is EnterChangeCartridgeModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = true
                }
            }
            is ExitChangeCartridgeModeResponse -> {
                if (message.status != 0) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inChangeCartridgeMode.value = false
                }
            }
            is EnterFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = true
                }
            }
            is ExitFillTubingModeResponse -> {
                if (!message.isStatusOK) {
                    unsuccessfulAlert(message.messageName())
                } else {
                    dataStore.inFillTubingMode.value = false
                }
            }
            is ResumePumpingResponse -> {
                // The shared delivery-transition coordinator owns this correlated result and
                // fresh-state reconciliation. Avoid a second generic failure dialog.
            }
            is SuspendPumpingResponse -> {
                // The shared delivery-transition coordinator owns this correlated result and
                // fresh-state reconciliation. Avoid a second generic failure dialog.
            }

        }
    }

    private fun persistVerifiedPumpProfileSnapshot(
        manager: IDPManager,
        verifiedAtEpochMillis: Long,
    ) {
        val prefs = Prefs(applicationContext)
        val pumpSid = dataStore.pumpSid.value?.takeIf { it >= 0 }
            ?: prefs.currentPumpSid().takeIf { it >= 0 }
        if (pumpSid == null) {
            Timber.w("Initial profile snapshot is complete but no pump SID is available for cache isolation")
            return
        }

        runCatching {
            val snapshot = VerifiedPumpProfileCache(
                pumpSid = pumpSid,
                verifiedAtEpochMillis = verifiedAtEpochMillis,
                profiles = profileSnapshots(manager),
            )
            prefs.setVerifiedPumpProfileCacheJson(
                encodeVerifiedPumpProfileCache(snapshot),
                pumpSid,
            )
        }.onSuccess {
            Timber.i("Cached pump-verified profile snapshot for pump SID $pumpSid")
        }.onFailure {
            Timber.e(it, "Unable to cache pump-verified profile snapshot")
        }
    }

    private fun unsuccessfulAlert(req: String) {
        AlertDialog.Builder(this@MainActivity)
            .setTitle("Failed Pump Request")
            .setMessage("$req was not successful. The pump returned an error fulfilling the request.")
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * BT permissions
     */

    private fun startBTPermissionsCheck() {
        if (btPermissionsRequestInFlight) {
            Timber.i("startBTPermissionsCheck skipped because a Bluetooth permission request is already in flight")
            return
        }

        val missingPermissions = getMissingPermissions(getRequiredPermissions())
        if (missingPermissions.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                btPermissionsRequestInFlight = true
                requestPermissions(missingPermissions, REQUEST_BT_PERMISSIONS)
            }
            return
        }

        if (getBluetoothManager().getAdapter() != null) {
            if (!isBluetoothEnabled()) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                val REQUEST_ENABLE_BT = 1
                startActivityForResult(
                    enableBtIntent,
                    REQUEST_ENABLE_BT
                )
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private fun getBluetoothManager(): BluetoothManager {
        return Objects.requireNonNull(
            getSystemService(BLUETOOTH_SERVICE) as BluetoothManager,
            "cannot get BluetoothManager"
        )
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = getBluetoothManager().adapter ?: return false
        return bluetoothAdapter.isEnabled
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = getMissingPermissions(getRequiredPermissions())
            if (missingPermissions.size > 0) {
                val ACCESS_LOCATION_REQUEST = 2
                requestPermissions(
                    missingPermissions,
                    ACCESS_LOCATION_REQUEST
                )
            } else {
                permissionsGranted()
            }
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (requiredPermission in requiredPermissions) {
                if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission)
                }
            }
        }
        return missingPermissions.toTypedArray()
    }

    private fun getRequiredPermissions(): Array<String> =
        BluetoothPermissions.required(Build.VERSION.SDK_INT, applicationInfo.targetSdkVersion)

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work for SDK < 31
        val targetSdkVersion = applicationInfo.targetSdkVersion
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            if (checkLocationServices()) {
                startCommServiceWithPreconditions()
            }
        } else {
            startCommServiceWithPreconditions()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        if (locationManager == null) {
            Timber.e("could not get location manager")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton(
                    "Enable"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton(
                    "Cancel"
                ) { dialog, which -> // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_BT_PERMISSIONS) {
            Timber.i("Ignoring onRequestPermissionsResult for unrelated requestCode=$requestCode")
            return
        }

        btPermissionsRequestInFlight = false

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permissions required for Bluetooth scanning")
                .setMessage("This app requires Nearby Devices and Location permissions to scan for Bluetooth devices. Please grant all requested permissions.")
                .setPositiveButton(
                    "Retry"
                ) { dialogInterface, i ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }

    /**
     * Notification permission
     */
    private var requestNotificationCallback: (Boolean) -> Unit = {}
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        notificationPermissionRequestInFlight = false
        val callback = requestNotificationCallback
        requestNotificationCallback = {}
        callback(isGranted)
    }

    private fun launchNotificationPermissionRequest(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            callback(true)
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        if (notificationPermissionRequestInFlight) {
            Timber.i("Notification permission request already in flight")
            return
        }

        notificationPermissionRequestInFlight = true
        requestNotificationCallback = callback
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }



    private fun openPlayStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun parseSheetLaunchRequest(intent: Intent?, source: String): SheetLaunchRequest? {
        if (intent == null) {
            Timber.i("parseSheetLaunchRequest[$source]: null intent")
            return null
        }

        val uri = intent.data
        val path = uri?.path?.trim('/')?.lowercase()

        val openBolus =
            intent.action == ACTION_OPEN_BOLUS ||
                intent.getBooleanExtra(EXTRA_OPEN_BOLUS, false) ||
                path == "bolus"

        val openTempRate =
            intent.action == ACTION_OPEN_TEMP_RATE ||
                intent.getBooleanExtra(EXTRA_OPEN_TEMP_RATE, false) ||
                path == "temp-rate" ||
                path == "temp_rate" ||
                path == "temprate"

        Timber.i("parseSheetLaunchRequest[%s]: action=%s uri=%s path=%s openBolus=%s openTempRate=%s",
            source, intent.action, uri, path, openBolus, openTempRate)

        if (openBolus == openTempRate) {
            Timber.i("parseSheetLaunchRequest[%s]: no unique target selected", source)
            return null
        }

        return when {
            openBolus -> {
                val prefill = parseBolusInputPrefill(intent, uri)
                val target = SheetLaunchTarget.Bolus(
                    prefill = prefill
                )
                SheetLaunchRequest(target = target).also {
                    Timber.i("Parsed bolus sheet launch request [%s] id=%s prefill=%s",
                        source, it.requestId, prefill)
                }
            }
            openTempRate -> {
                val prefill = parseTempRateInputPrefill(intent, uri)
                val target = SheetLaunchTarget.TempRate(
                    prefill = prefill
                )
                SheetLaunchRequest(target = target).also {
                    Timber.i("Parsed temp rate sheet launch request [%s] id=%s prefill=%s",
                        source, it.requestId, prefill)
                }
            }
            else -> null
        }
    }

    private fun logIncomingIntent(source: String, intent: Intent?) {
        if (intent == null) {
            Timber.i("Incoming intent [%s]: null", source)
            return
        }
        val extras = intent.extras
        val extrasSummary = if (extras == null) {
            "none"
        } else {
            extras.keySet().joinToString(prefix = "{", postfix = "}") { key ->
                "$key=${extras.get(key)}"
            }
        }

        Timber.i(
            "Incoming intent [%s]: action=%s data=%s scheme=%s host=%s path=%s query=%s extras=%s",
            source,
            intent.action,
            intent.dataString,
            intent.data?.scheme,
            intent.data?.host,
            intent.data?.path,
            intent.data?.query,
            extrasSummary
        )
    }

    private fun parseBolusInputPrefill(intent: Intent, uri: Uri?): BolusInputPrefill? {
        val unitsRawValue = sanitizeDecimalValue(readLaunchValue(intent, uri, EXTRA_BOLUS_UNITS))
        val carbsRawValue = sanitizeIntegerValue(readLaunchValue(intent, uri, EXTRA_BOLUS_CARBS))
        val glucoseRawValue = sanitizeIntegerValue(readLaunchValue(intent, uri, EXTRA_BOLUS_BG))

        if (unitsRawValue == null && carbsRawValue == null && glucoseRawValue == null) {
            return null
        }
        return BolusInputPrefill(
            unitsRawValue = unitsRawValue,
            carbsRawValue = carbsRawValue,
            glucoseRawValue = glucoseRawValue
        )
    }

    private fun parseTempRateInputPrefill(intent: Intent, uri: Uri?): TempRateInputPrefill? {
        val percentRawValue = sanitizeIntegerValue(readLaunchValue(intent, uri, EXTRA_TEMP_RATE_PERCENT))
        val hoursRawValue = sanitizeIntegerValue(readLaunchValue(intent, uri, EXTRA_TEMP_RATE_HOURS))
        val minutesRawValue = sanitizeIntegerValue(readLaunchValue(intent, uri, EXTRA_TEMP_RATE_MINUTES))

        if (percentRawValue == null && hoursRawValue == null && minutesRawValue == null) {
            return null
        }
        return TempRateInputPrefill(
            percentRawValue = percentRawValue,
            hoursRawValue = hoursRawValue,
            minutesRawValue = minutesRawValue
        )
    }

    private fun readLaunchValue(intent: Intent, uri: Uri?, key: String): String? {
        val fromExtras = intent.extras?.get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (fromExtras != null) {
            return fromExtras
        }
        return uri?.getQueryParameter(key)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun sanitizeIntegerValue(raw: String?): String? {
        val filtered = raw?.filter { it.isDigit() } ?: return null
        if (filtered.isEmpty()) {
            return null
        }
        return filtered.trimStart('0').ifEmpty { "0" }
    }

    private fun sanitizeDecimalValue(raw: String?): String? {
        if (raw == null) {
            return null
        }

        var filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.isEmpty()) {
            return null
        }

        val firstDot = filtered.indexOf('.')
        if (firstDot >= 0) {
            val integral = filtered.substring(0, firstDot + 1)
            val fractional = filtered.substring(firstDot + 1).replace(".", "")
            filtered = integral + fractional
            if (filtered.length - firstDot > 3) {
                filtered = filtered.substring(0, firstDot + 3)
            }
        }

        if (filtered == ".") {
            return null
        }

        val parsed = filtered.toDoubleOrNull() ?: return null
        if (parsed < 0) {
            return null
        }
        return filtered
    }

    private fun determineStartDestination(): String {
        val prefs = Prefs(applicationContext)
        return when {
            !prefs.tosAccepted() -> Screen.FirstLaunch.route
            prefs.developerPreviewEnabled() -> Screen.Landing.route
            // In CLIENT mode the watch handles pump pairing — the phone has no
            // pump-finder service running, so PumpSetup would wait forever.
            prefs.deviceRole() == DeviceRole.CLIENT ->
                Screen.Landing.route
            !prefs.pumpSetupComplete() -> Screen.PumpSetup.route
            else -> Screen.Landing.route
        }
    }
}
