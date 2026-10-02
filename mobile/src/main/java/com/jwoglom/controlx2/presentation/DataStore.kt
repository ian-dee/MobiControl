package com.jwoglom.controlx2.presentation

import androidx.lifecycle.MutableLiveData
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.PairingCodeType
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CancelBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteCarbEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RemoteBgEntryResponse
import com.jwoglom.pumpx2.pump.messages.response.control.FillCannulaResponse
import com.jwoglom.pumpx2.pump.messages.response.control.EnterFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ExitFillTubingModeResponse
import com.jwoglom.pumpx2.pump.messages.response.control.ResumePumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SuspendPumpingResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmHighLowAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmOutOfRangeAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.CgmRiseFallAlertResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DisconnectPumpResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DismissNotificationResponse
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
import com.jwoglom.pumpx2.pump.messages.response.control.StopDexcomCGMSensorSessionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HistoryLogStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBolusStatusAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.RemindersResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.presentation.pairing.PumpPairingCandidate
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusOperation
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusOperation
import com.jwoglom.controlx2.shared.PumpHydrationState
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillTubingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.PumpingStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMGlucoseAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMOORAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMRateAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CgmStatusV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQInfoAbstractResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQSleepScheduleResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GetSavedG7PairingCodeResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HomeScreenMirrorResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import timber.log.Timber
import java.time.Instant

data class PumpMessageEvent<T : Message>(
    val sequence: Long,
    val receivedAt: Instant,
    val message: T,
    /** True when the UI received a replay from PumpCommHandler's response cache. */
    val cached: Boolean = false,
)

data class PumpStateEvent<T>(
    val sequence: Long,
    val receivedAt: Instant,
    val value: T,
)

class DataStore {
    val pumpConnected = MutableLiveData<Boolean>()
    val pumpLastConnectionTimestamp = MutableLiveData<Instant>()
    val pumpLastMessageTimestamp = MutableLiveData<Instant>()
    val pumpHydrationState = MutableLiveData<PumpHydrationState>()
    val watchConnected = MutableLiveData<Boolean>()

    val pumpSetupStage = MutableLiveData<PumpSetupStage>(PumpSetupStage.WAITING_PUMP_FINDER_INIT)
    val pumpFinderPumps = MutableLiveData<List<PumpPairingCandidate>>(emptyList())
    val selectedPumpFinderMac = MutableLiveData<String?>()
    val setupDeviceName = MutableLiveData<String>()
    val pumpReadyState = MutableLiveData<PumpReadyState>()
    val setupPairingCodeType = MutableLiveData<PairingCodeType>()
    val pumpSid = MutableLiveData<Int>()
    val setupDeviceModel = MutableLiveData<String>()
    val pumpCriticalError = MutableLiveData<Pair<String, Instant>>()

    val notificationBundle = MutableLiveData<NotificationBundle>(NotificationBundle())
    val batteryPercent = MutableLiveData<Int>()
    val batteryCharging = MutableLiveData<Boolean>()
    val iobUnits = MutableLiveData<Double>()
    val cartridgeRemainingUnits = MutableLiveData<Int>()
    val cartridgeRemainingEstimate = MutableLiveData<Boolean>()
    /** Raw pump estimate flag retained for diagnostics; UI derives the legacy boolean from it. */
    val cartridgeRemainingEstimateFlag = MutableLiveData<Int>()
    val cartridgeLowAlertUnits = MutableLiveData<Int>()
    val lastBolusStatus = MutableLiveData<String>()
    val lastBolusSource = MutableLiveData<BolusDeliveryHistoryLog.BolusSource>()
    val controlIQStatus = MutableLiveData<String>()
    val controlIQMode = MutableLiveData<UserMode>()
    val controlIQEnabled = MutableLiveData<Boolean>()
    val controlIQWeight = MutableLiveData<Int>()
    val controlIQWeightUnit = MutableLiveData<String>()
    val controlIQTotalDailyInsulin = MutableLiveData<Int>()
    val basalRate = MutableLiveData<String>()
    var basalStatus = MutableLiveData<BasalStatus>()
    var tempRateActive = MutableLiveData<Boolean>()
    var tempRateDetails = MutableLiveData<TempRateResponse>()
    val pumpGlobalsResponse = MutableLiveData<PumpGlobalsResponse>()
    val controlIQInfoResponse = MutableLiveData<ControlIQInfoAbstractResponse>()
    val controlIQSleepScheduleResponse = MutableLiveData<ControlIQSleepScheduleResponse>()
    val globalMaxBolusSettingsResponse = MutableLiveData<GlobalMaxBolusSettingsResponse>()
    val basalLimitSettingsResponse = MutableLiveData<BasalLimitSettingsResponse>()
    val setMaxBolusLimitResponse = MutableLiveData<SetMaxBolusLimitResponse>()
    val setMaxBasalLimitResponse = MutableLiveData<SetMaxBasalLimitResponse>()
    val setQuickBolusSettingsResponseEvent =
        MutableLiveData<PumpMessageEvent<SetQuickBolusSettingsResponse>>()
    val pumpGlobalsEvent = MutableLiveData<PumpMessageEvent<PumpGlobalsResponse>>()
    val cgmSessionState = MutableLiveData<CGMSessionState>()
    val cgmStatusV2Response = MutableLiveData<CgmStatusV2Response>()
    val cgmGlucoseAlertSettingsResponse = MutableLiveData<CGMGlucoseAlertSettingsResponse>()
    val cgmRateAlertSettingsResponse = MutableLiveData<CGMRateAlertSettingsResponse>()
    val cgmOutOfRangeAlertSettingsResponse = MutableLiveData<CGMOORAlertSettingsResponse>()
    val cgmSessionExpireRelative = MutableLiveData<String>()
    val cgmSessionExpireExact = MutableLiveData<String>()
    val cgmTransmitterStatus = MutableLiveData<String>()
    val cgmReading = MutableLiveData<Int>()
    /** Sensor-start identity associated with [cgmReading]; prevents cross-session current values. */
    val cgmReadingSensorStartedTimestamp = MutableLiveData<Long>()
    val cgmDelta = MutableLiveData<Int>()
    val cgmStatusText = MutableLiveData<String>()
    val cgmHighLowState = MutableLiveData<String>()
    val cgmDeltaArrow = MutableLiveData<String>()
    val cgmSetupG6TxId = MutableLiveData<String>()
    val cgmSetupG6SensorCode = MutableLiveData<String>()
    val cgmSetupG7SensorCode = MutableLiveData<String>()
    val savedG7PairingCode = MutableLiveData<Int>()
    val idpManager = MutableLiveData<IDPManager>(IDPManager())
    val profileReadProgress = MutableLiveData<ProfileReadProgress>(ProfileReadProgress())
    val initialProfileReadError = MutableLiveData<String?>()
    val profileReadCompletedAtEpochMillis = MutableLiveData<Long>()

    val bolusCalcDataSnapshot = MutableLiveData<BolusCalcDataSnapshotResponse>()
    val bolusCalcLastBG = MutableLiveData<LastBGResponse>()
    val maxBolusAmount = MutableLiveData<Int>()

    val landingBasalDisplayedText = MutableLiveData<String>()
    val landingControlIQDisplayedText = MutableLiveData<String>()

    val bolusCalculatorBuilder = MutableLiveData<BolusCalculatorBuilder>()
    val bolusCurrentParameters = MutableLiveData<BolusParameters>()
    val bolusCurrentConditions = MutableLiveData<List<BolusCalcCondition>>()
    val bolusConditionsPrompt = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsPromptAcknowledged = MutableLiveData<MutableList<BolusCalcCondition>>()
    val bolusConditionsExcluded = MutableLiveData<MutableSet<BolusCalcCondition>>()
    val bolusFinalParameters = MutableLiveData<BolusParameters>()
    val bolusFinalCalcUnits = MutableLiveData<BolusCalcUnits>()
    val bolusFinalConditions = MutableLiveData<Set<BolusCalcCondition>>()

    val bolusUnitsRawValue = MutableLiveData<String?>()
    val bolusCarbsRawValue = MutableLiveData<String?>()
    val bolusGlucoseRawValue = MutableLiveData<String?>()
    /** Current BG only when it came from the pump's automatic CGM population path. */
    val bolusAutomaticBgForAbandonment = MutableLiveData<Int?>()
    /** Final manually entered BG after the user confirms text entry with the keyboard Done action. */
    val bolusManualBgForAbandonment = MutableLiveData<Int?>()

    val tempRatePercentRawValue = MutableLiveData<String?>()
    val tempRateMinutesRawValue = MutableLiveData<String?>()
    val tempRateHoursRawValue = MutableLiveData<String?>()

    val timeSinceResetResponse = MutableLiveData<TimeSinceResetResponse>()
    val loadStatusResponse = MutableLiveData<LoadStatusResponse>()
    val remindersResponse = MutableLiveData<RemindersResponse>()
    val bolusPermissionResponse = MutableLiveData<BolusPermissionResponse>()
    val bolusCarbEntryResponse = MutableLiveData<RemoteCarbEntryResponse>()
    val bolusInitiateResponse = MutableLiveData<InitiateBolusResponse>()
    val bolusCancelResponse = MutableLiveData<CancelBolusResponse>()
    val lastBolusStatusResponse = MutableLiveData<LastBolusStatusAbstractResponse>()
    val bolusCurrentResponse = MutableLiveData<CurrentBolusStatusResponse>()
    val automaticBolusCancelResult = MutableLiveData<AutomaticBolusCancelResult>()
    val manualBolusCancelResult = MutableLiveData<ManualBolusCancelResult>()
    val manualBolusOperation = MutableLiveData<ManualBolusOperation>()
    val quickBolusCancelResult = MutableLiveData<QuickBolusCancelResult>()
    val quickBolusOperation = MutableLiveData<QuickBolusOperation>()
    val bolusPermissionOperation = MutableLiveData<BolusPermissionOperation>()

    val inChangeCartridgeMode = MutableLiveData<Boolean>()
    val inFillTubingMode = MutableLiveData<Boolean>()
    val enterChangeCartridgeState = MutableLiveData<EnterChangeCartridgeModeStateStreamResponse>()
    val detectingCartridgeState = MutableLiveData<DetectingCartridgeStateStreamResponse>()
    val fillTubingState = MutableLiveData<FillTubingStateStreamResponse>()
    val exitFillTubingState = MutableLiveData<ExitFillTubingModeStateStreamResponse>()
    val fillCannulaState = MutableLiveData<FillCannulaStateStreamResponse>()
    val pumpingState = MutableLiveData<PumpingStateStreamResponse>()

    val latestPumpMessageSequence = MutableLiveData<Long>(0L)
    val timeSinceResetEvent = MutableLiveData<PumpMessageEvent<TimeSinceResetResponse>>()
    val basalStatusEvent = MutableLiveData<PumpStateEvent<BasalStatus>>()
    val loadStatusEvent = MutableLiveData<PumpMessageEvent<LoadStatusResponse>>()
    val remindersEvent = MutableLiveData<PumpMessageEvent<RemindersResponse>>()
    val fillCannulaResponseEvent = MutableLiveData<PumpMessageEvent<FillCannulaResponse>>()
    val enterFillTubingModeResponseEvent =
        MutableLiveData<PumpMessageEvent<EnterFillTubingModeResponse>>()
    val exitFillTubingModeResponseEvent =
        MutableLiveData<PumpMessageEvent<ExitFillTubingModeResponse>>()
    val resumePumpingResponseEvent = MutableLiveData<PumpMessageEvent<ResumePumpingResponse>>()
    val suspendPumpingResponseEvent = MutableLiveData<PumpMessageEvent<SuspendPumpingResponse>>()
    val disconnectPumpResponseEvent = MutableLiveData<PumpMessageEvent<DisconnectPumpResponse>>()
    val dismissNotificationResponseEvent =
        MutableLiveData<PumpMessageEvent<DismissNotificationResponse>>()
    val notificationStatusEvent = MutableLiveData<PumpMessageEvent<Message>>()
    val fillCannulaStateEvent = MutableLiveData<PumpMessageEvent<FillCannulaStateStreamResponse>>()
    val siteChangeReminderResponseEvent = MutableLiveData<PumpMessageEvent<SetSiteChangeReminderResponse>>()
    val cgmStatusEvent = MutableLiveData<PumpMessageEvent<CGMStatusResponse>>()
    val cgmStatusV2Event = MutableLiveData<PumpMessageEvent<CgmStatusV2Response>>()
    val currentEgvEvent = MutableLiveData<PumpMessageEvent<CurrentEGVGuiDataResponse>>()
    val homeScreenMirrorEvent = MutableLiveData<PumpMessageEvent<HomeScreenMirrorResponse>>()
    val alertStatusEvent = MutableLiveData<PumpMessageEvent<AlertStatusResponse>>()
    val setG7PairingCodeResponseEvent =
        MutableLiveData<PumpMessageEvent<SetDexcomG7PairingCodeResponse>>()
    val savedG7PairingCodeEvent =
        MutableLiveData<PumpMessageEvent<GetSavedG7PairingCodeResponse>>()
    val stopCgmSessionResponseEvent =
        MutableLiveData<PumpMessageEvent<StopDexcomCGMSensorSessionResponse>>()
    val remoteBgEntryResponseEvent =
        MutableLiveData<PumpMessageEvent<RemoteBgEntryResponse>>()
    val cgmGlucoseAlertSettingsEvent =
        MutableLiveData<PumpMessageEvent<CGMGlucoseAlertSettingsResponse>>()
    val cgmRateAlertSettingsEvent =
        MutableLiveData<PumpMessageEvent<CGMRateAlertSettingsResponse>>()
    val cgmOutOfRangeAlertSettingsEvent =
        MutableLiveData<PumpMessageEvent<CGMOORAlertSettingsResponse>>()
    val cgmHighLowAlertResponseEvent =
        MutableLiveData<PumpMessageEvent<CgmHighLowAlertResponse>>()
    val cgmRiseFallAlertResponseEvent =
        MutableLiveData<PumpMessageEvent<CgmRiseFallAlertResponse>>()
    val cgmOutOfRangeAlertResponseEvent =
        MutableLiveData<PumpMessageEvent<CgmOutOfRangeAlertResponse>>()
    val controlIqInfoEvent =
        MutableLiveData<PumpMessageEvent<ControlIQInfoAbstractResponse>>()
    val controlIqSleepScheduleEvent =
        MutableLiveData<PumpMessageEvent<ControlIQSleepScheduleResponse>>()
    val changeControlIqSettingsResponseEvent =
        MutableLiveData<PumpMessageEvent<ChangeControlIQSettingsResponse>>()
    val setSleepScheduleResponseEvent =
        MutableLiveData<PumpMessageEvent<SetSleepScheduleResponse>>()
    val setTempRateResponseEvent = MutableLiveData<PumpMessageEvent<SetTempRateResponse>>()
    val stopTempRateResponseEvent = MutableLiveData<PumpMessageEvent<StopTempRateResponse>>()
    val tempRateResponseEvent = MutableLiveData<PumpMessageEvent<TempRateResponse>>()
    val profileMutationResponseEvent = MutableLiveData<PumpMessageEvent<StatusMessage>>()
    val changeTimeDateResponseEvent = MutableLiveData<PumpMessageEvent<ChangeTimeDateResponse>>()

    val historyLogStatus = MutableLiveData<HistoryLogStatusResponse>()

    val historyLogCache = MutableLiveData<MutableMap<Long, HistoryLog>>(mutableMapOf())
    val debugMessageCache = MutableLiveData<List<Pair<Message, Instant>>>()
    val debugPromptAwaitingResponses = MutableLiveData<MutableSet<String>>()

    val glucoseUnitPreference = MutableLiveData<GlucoseUnit>()

    private fun <T> MutableLiveData<T>.logOnChange(fieldName: String) {
        var initialized = false
        var previous: Any? = null
        observeForever { current ->
            if (!initialized || previous != current) {
                Timber.d("DataStore.$fieldName=$current")
                previous = current
                initialized = true
            }
        }
    }

    init {
        pumpConnected.logOnChange("pumpConnected")
        pumpLastConnectionTimestamp.logOnChange("pumpLastConnectionTimestamp")
        pumpLastMessageTimestamp.logOnChange("pumpLastMessageTimestamp")
        pumpHydrationState.logOnChange("pumpHydrationState")
        watchConnected.logOnChange("watchConnected")

        pumpSetupStage.logOnChange("setupStage")
        pumpFinderPumps.logOnChange("pumpFinderPumps")
        selectedPumpFinderMac.logOnChange("selectedPumpFinderMac")
        setupDeviceName.logOnChange("setupDeviceName")
        setupPairingCodeType.logOnChange("setupPairingCodeType")
        pumpSid.logOnChange("pumpSid")
        setupDeviceModel.logOnChange("setupDeviceModel")
        pumpCriticalError.logOnChange("pumpCriticalError")

        notificationBundle.logOnChange("notificationBundle")
        batteryPercent.logOnChange("batteryPercent")
        batteryCharging.logOnChange("batteryCharging")
        iobUnits.logOnChange("iobUnits")
        cartridgeRemainingUnits.logOnChange("cartridgeRemainingUnits")
        cartridgeRemainingEstimate.logOnChange("cartridgeRemainingEstimate")
        cartridgeRemainingEstimateFlag.logOnChange("cartridgeRemainingEstimateFlag")
        cartridgeLowAlertUnits.logOnChange("cartridgeLowAlertUnits")
        lastBolusStatus.logOnChange("lastBolusStatus")
        lastBolusSource.logOnChange("lastBolusSource")
        controlIQStatus.logOnChange("controlIQStatus")
        controlIQMode.logOnChange("controlIQMode")
        controlIQEnabled.logOnChange("controlIQEnabled")
        controlIQWeight.logOnChange("controlIQWeight")
        controlIQWeightUnit.logOnChange("controlIQWeightUnit")
        controlIQTotalDailyInsulin.logOnChange("controlIQTotalDailyInsulin")
        basalRate.logOnChange("basalRate")
        basalStatus.logOnChange("basalStatus")
        tempRateActive.logOnChange("tempRateActive")
        tempRateDetails.logOnChange("tempRateDetails")
        pumpGlobalsResponse.logOnChange("pumpGlobalsResponse")
        controlIQInfoResponse.logOnChange("controlIQInfoResponse")
        controlIQSleepScheduleResponse.logOnChange("controlIQSleepScheduleResponse")
        globalMaxBolusSettingsResponse.logOnChange("globalMaxBolusSettingsResponse")
        basalLimitSettingsResponse.logOnChange("basalLimitSettingsResponse")
        setMaxBolusLimitResponse.logOnChange("setMaxBolusLimitResponse")
        setMaxBasalLimitResponse.logOnChange("setMaxBasalLimitResponse")
        cgmSessionState.logOnChange("cgmSessionState")
        cgmStatusV2Response.logOnChange("cgmStatusV2Response")
        cgmGlucoseAlertSettingsResponse.logOnChange("cgmGlucoseAlertSettingsResponse")
        cgmRateAlertSettingsResponse.logOnChange("cgmRateAlertSettingsResponse")
        cgmOutOfRangeAlertSettingsResponse.logOnChange("cgmOutOfRangeAlertSettingsResponse")
        cgmSessionExpireRelative.logOnChange("cgmSessionExpireRelative")
        cgmSessionExpireExact.logOnChange("cgmSessionExpireExact")
        cgmTransmitterStatus.logOnChange("cgmTransmitterStatus")
        cgmReading.logOnChange("cgmReading")
        cgmReadingSensorStartedTimestamp.logOnChange("cgmReadingSensorStartedTimestamp")
        cgmDelta.logOnChange("cgmDelta")
        cgmStatusText.logOnChange("cgmStatusText")
        cgmHighLowState.logOnChange("cgmHighLowState")
        cgmDeltaArrow.logOnChange("cgmDeltaArrow")
        cgmSetupG6TxId.logOnChange("cgmSetupG6TxId")
        cgmSetupG6SensorCode.logOnChange("cgmSetupG6SensorId")
        cgmSetupG7SensorCode.logOnChange("cgmSetupG7SensorId")
        savedG7PairingCode.logOnChange("savedG7PairingCode")
        idpManager.logOnChange("idpManager")
        profileReadProgress.logOnChange("profileReadProgress")
        initialProfileReadError.logOnChange("initialProfileReadError")


        bolusCalcDataSnapshot.logOnChange("bolusCalcDataSnapshot")
        bolusCalcLastBG.logOnChange("bolusCalcLastBG")
        maxBolusAmount.logOnChange("maxBolusAmount")
        bolusAutomaticBgForAbandonment.logOnChange("bolusAutomaticBgForAbandonment")
        bolusManualBgForAbandonment.logOnChange("bolusManualBgForAbandonment")

        landingBasalDisplayedText.logOnChange("landingBasalDisplayedText")
        landingControlIQDisplayedText.logOnChange("landingControlIQDisplayedText")

        bolusCalculatorBuilder.logOnChange("bolusCalculatorBuilder")
        bolusCurrentParameters.logOnChange("bolusCurrentParameters")
        bolusCurrentConditions.logOnChange("bolusCurrentConditions")
        bolusConditionsPrompt.logOnChange("bolusConditionsPrompt")
        bolusConditionsPromptAcknowledged.logOnChange("bolusConditionsPromptAcknowledged")
        bolusConditionsExcluded.logOnChange("bolusConditionsExcluded")
        bolusFinalParameters.logOnChange("bolusFinalParameters")
        bolusFinalCalcUnits.logOnChange("bolusFinalCalcUnits")
        bolusFinalConditions.logOnChange("bolusFinalConditions")

        bolusUnitsRawValue.logOnChange("bolusUnitsRawValue")
        bolusCarbsRawValue.logOnChange("bolusCarbsRawValue")
        bolusGlucoseRawValue.logOnChange("bolusGlucoseRawValue")

        tempRatePercentRawValue.logOnChange("tempRatePercentRawValue")
        tempRateMinutesRawValue.logOnChange("tempRateMinutesRawValue")
        tempRateHoursRawValue.logOnChange("tempRateHoursRawValue")

        timeSinceResetResponse.logOnChange("timeSinceResetResponse")
        loadStatusResponse.logOnChange("loadStatusResponse")
        remindersResponse.logOnChange("remindersResponse")
        bolusPermissionResponse.logOnChange("bolusPermissionResponse")
        bolusCarbEntryResponse.logOnChange("bolusCarbEntryResponse")
        bolusInitiateResponse.logOnChange("bolusInitiateResponse")
        bolusCancelResponse.logOnChange("bolusCancelResponse")
        bolusCurrentResponse.logOnChange("bolusCurrentResponse")
        automaticBolusCancelResult.logOnChange("automaticBolusCancelResult")
        manualBolusCancelResult.logOnChange("manualBolusCancelResult")
        quickBolusCancelResult.logOnChange("quickBolusCancelResult")
        quickBolusOperation.logOnChange("quickBolusOperation")

        inChangeCartridgeMode.logOnChange("inChangeCartridgeMode")
        inFillTubingMode.logOnChange("inFillTubingMode")
        enterChangeCartridgeState.logOnChange("enterChangeCartridgeState")
        detectingCartridgeState.logOnChange("detectingCartridgeState")
        fillTubingState.logOnChange("fillTubingState")
        exitFillTubingState.logOnChange("exitFillTubingState")
        fillCannulaState.logOnChange("fillCannulaState")
        pumpingState.logOnChange("pumpingState")

        latestPumpMessageSequence.logOnChange("latestPumpMessageSequence")
        basalStatusEvent.logOnChange("basalStatusEvent")
        loadStatusEvent.logOnChange("loadStatusEvent")
        remindersEvent.logOnChange("remindersEvent")
        fillCannulaResponseEvent.logOnChange("fillCannulaResponseEvent")
        enterFillTubingModeResponseEvent.logOnChange("enterFillTubingModeResponseEvent")
        exitFillTubingModeResponseEvent.logOnChange("exitFillTubingModeResponseEvent")
        resumePumpingResponseEvent.logOnChange("resumePumpingResponseEvent")
        suspendPumpingResponseEvent.logOnChange("suspendPumpingResponseEvent")
        fillCannulaStateEvent.logOnChange("fillCannulaStateEvent")
        siteChangeReminderResponseEvent.logOnChange("siteChangeReminderResponseEvent")
        cgmStatusEvent.logOnChange("cgmStatusEvent")
        cgmStatusV2Event.logOnChange("cgmStatusV2Event")
        currentEgvEvent.logOnChange("currentEgvEvent")
        homeScreenMirrorEvent.logOnChange("homeScreenMirrorEvent")
        alertStatusEvent.logOnChange("alertStatusEvent")
        setG7PairingCodeResponseEvent.logOnChange("setG7PairingCodeResponseEvent")
        savedG7PairingCodeEvent.logOnChange("savedG7PairingCodeEvent")
        stopCgmSessionResponseEvent.logOnChange("stopCgmSessionResponseEvent")
        remoteBgEntryResponseEvent.logOnChange("remoteBgEntryResponseEvent")
        cgmGlucoseAlertSettingsEvent.logOnChange("cgmGlucoseAlertSettingsEvent")
        cgmRateAlertSettingsEvent.logOnChange("cgmRateAlertSettingsEvent")
        cgmOutOfRangeAlertSettingsEvent.logOnChange("cgmOutOfRangeAlertSettingsEvent")
        cgmHighLowAlertResponseEvent.logOnChange("cgmHighLowAlertResponseEvent")
        cgmRiseFallAlertResponseEvent.logOnChange("cgmRiseFallAlertResponseEvent")
        cgmOutOfRangeAlertResponseEvent.logOnChange("cgmOutOfRangeAlertResponseEvent")
        controlIqInfoEvent.logOnChange("controlIqInfoEvent")
        controlIqSleepScheduleEvent.logOnChange("controlIqSleepScheduleEvent")
        changeControlIqSettingsResponseEvent.logOnChange("changeControlIqSettingsResponseEvent")
        setSleepScheduleResponseEvent.logOnChange("setSleepScheduleResponseEvent")
        setTempRateResponseEvent.logOnChange("setTempRateResponseEvent")
        stopTempRateResponseEvent.logOnChange("stopTempRateResponseEvent")
        tempRateResponseEvent.logOnChange("tempRateResponseEvent")
        profileMutationResponseEvent.logOnChange("profileMutationResponseEvent")
        changeTimeDateResponseEvent.logOnChange("changeTimeDateResponseEvent")

        historyLogStatus.logOnChange("historyLogStatus")

        historyLogCache.logOnChange("historyLogCache")
        debugMessageCache.logOnChange("debugMessageCache")
        debugPromptAwaitingResponses.logOnChange("debugPromptAwaitingResponses")

        glucoseUnitPreference.logOnChange("glucoseUnitPreference")
    }


}
