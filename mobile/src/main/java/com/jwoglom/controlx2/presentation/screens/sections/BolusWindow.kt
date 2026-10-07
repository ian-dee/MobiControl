@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.createAbandonedBolusCleanupRequestOrNull
import com.jwoglom.controlx2.automaticBgForAbandonmentOrNull
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationRequest
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.navigation.BolusInputPrefill
import com.jwoglom.controlx2.presentation.simulation.applyDeveloperPreviewBolusInputs
import com.jwoglom.controlx2.presentation.screens.BolusPreview
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusConditionPromptRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusDeliverActionRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusEntryFormRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.toBolusDeliveryCalculation
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusPermissionDialogRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusReviewDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.MaxBolusAlertDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.MaxHourlyBolusAlertDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.LowInsulinAlertDialog
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.pumpSessionIdsMatch
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.bluetooth.PumpStateSupplier
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcDecision
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.function.Supplier
import kotlin.math.roundToInt

@Composable
fun BolusWindow(
    sendMessage: (String, ByteArray) -> Unit = { _, _ -> },
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    sendServiceBolusRequest: (Int, BolusParameters, BolusCalcUnits, BolusCalcDataSnapshotResponse, TimeSinceResetResponse, Boolean, Boolean, Boolean, Boolean) -> Unit,
    requestBolusAuthentication: BolusAuthenticationRequest,
    prefill: BolusInputPrefill? = null,
    onPrefillConsumed: () -> Unit = {},
    onPermissionGranted: () -> Unit = {},
    onBolusSessionCompleted: (submittedToPump: Boolean) -> Unit = {},
    onDeveloperPreviewBolusInitiated: (Double) -> Unit = {},
    compactCover: Boolean = false,
    closeWindow: () -> Unit,
) {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val developerPreviewEnabled = Prefs(context).developerPreviewEnabled()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val snapshotRefreshGate = remember { BolusSnapshotRefreshGate() }

    val glucoseUnit by dataStore.glucoseUnitPreference.observeAsState(GlucoseUnit.MGDL)
    val unitAbbrev = glucoseUnit.abbreviation

    val unitsRawValue = dataStore.bolusUnitsRawValue.observeAsState()
    val carbsRawValue = dataStore.bolusCarbsRawValue.observeAsState()
    val glucoseRawValue = dataStore.bolusGlucoseRawValue.observeAsState()

    var unitsSubtitle by remember { mutableStateOf<String>("Units") }
    var carbsSubtitle by remember { mutableStateOf<String>("Carbs (g)") }
    var glucoseSubtitle by remember { mutableStateOf<String>("BG ($unitAbbrev)") }

    var unitsHumanEntered by remember { mutableStateOf<Double?>(null) }
    var unitsHumanFocus by remember { mutableStateOf(false) }
    var lastCalculatedUnitsRawValue by remember { mutableStateOf<String?>(null) }
    var glucoseHumanEntered by remember { mutableStateOf(false) }
    var glucoseFieldEdited by remember { mutableStateOf(false) }

    var bolusButtonEnabled by remember { mutableStateOf(false) }

    val bolusCalcDataSnapshot = dataStore.bolusCalcDataSnapshot.observeAsState()
    val carbEntryEnabled = bolusCalcDataSnapshot.value?.carbEntryEnabled ?: true
    val bolusCalcLastBG = dataStore.bolusCalcLastBG.observeAsState()
    val bolusConditionsExcluded = dataStore.bolusConditionsExcluded.observeAsState()
    val bolusPermissionOperation = dataStore.bolusPermissionOperation.observeAsState()
    val bolusPermissionResponse = dataStore.bolusPermissionResponse.observeAsState()

    var showPermissionCheckDialog by remember { mutableStateOf(false) }
    var showBolusReviewDialog by remember { mutableStateOf(false) }
    var showBolusConfirmedDialog by remember { mutableStateOf(false) }
    var developerPreviewAuthenticatedUnits by remember { mutableStateOf<Double?>(null) }
    var bolusWorkflowError by remember { mutableStateOf<String?>(null) }
    var permissionGrantReported by remember { mutableStateOf(false) }
    var pendingMaxBolusAlert by remember {
        mutableStateOf<BolusLimitDecision.RequiresCappedConfirmation?>(null)
    }
    var pendingMaxHourlyBolusAlert by remember {
        mutableStateOf<BolusPumpSafetyDecision.RequiresMaxHourlyConfirmation?>(null)
    }
    var pendingLowInsulinAlert by remember {
        mutableStateOf<BolusPumpSafetyDecision.RequiresLowInsulinAcknowledgement?>(null)
    }
    var maxHourlyBolusConfirmed by remember { mutableStateOf(false) }
    var lowInsulinAcknowledged by remember { mutableStateOf(false) }
    var pendingSafetyRequirements by remember {
        mutableStateOf(BolusPumpSafetyRequirements())
    }
    var pendingPrefill by remember(prefill) { mutableStateOf(prefill) }
    var correctionEnabled by remember {
        mutableStateOf(prefill?.correctionEnabled ?: true)
    }
    var correctionRequired by remember { mutableStateOf(false) }
    val overrideActive = unitsHumanEntered != null

    val baseFields = listOf(
        dataStore.bolusCalcDataSnapshot,
    )

    fun queueUnusedBolusPermissionRelease() {
        if (developerPreviewEnabled) return
        Timber.i("Queueing durable cleanup for unused bolus permission")
        val cleanup = createAbandonedBolusCleanupRequestOrNull(
            permission = dataStore.bolusPermissionOperation.value,
            automaticBgMgdl = dataStore.bolusAutomaticBgForAbandonment.value,
            manualBgMgdl = dataStore.bolusManualBgForAbandonment.value,
        )
        sendMessage(
            MessagePaths.TO_SERVER_RELEASE_BOLUS_PERMISSION,
            cleanup?.encode() ?: byteArrayOf(),
        )
    }

    fun refresh() = refreshScope.launch {
        if (!snapshotRefreshGate.tryBegin()) {
            Timber.i("BolusScreen refresh coalesced because a snapshot request is already active")
            return@launch
        }
        refreshing = true

        try {
            if (developerPreviewEnabled) {
                applyDeveloperPreviewBolusInputs(
                    dataStore,
                    maxBolusMilliunits = Prefs(context).developerPreviewMaxBolusMilliunits(),
                )
                return@launch
            }

            dataStore.bolusCalcDataSnapshot.value = null
            dataStore.bolusCalcLastBG.value = null
            sendMessage(MessagePaths.TO_SERVER_PREPARE_BOLUS_WORKFLOW, byteArrayOf())

            var attempts = 0
            while (dataStore.bolusCalcDataSnapshot.value == null &&
                attempts < BOLUS_PREPARATION_MAX_ATTEMPTS
            ) {
                delay(BOLUS_PREPARATION_POLL_MILLIS)
                attempts += 1
            }
            Timber.i("BolusScreen preparation complete: ${baseFields.map { it.value }}")
        } finally {
            refreshing = false
            snapshotRefreshGate.finish()
        }
    }

    fun applyPrefill(prefillValues: BolusInputPrefill) {
        dataStore.bolusUnitsRawValue.value = prefillValues.unitsRawValue
        dataStore.bolusCarbsRawValue.value = prefillValues.carbsRawValue
        dataStore.bolusGlucoseRawValue.value = prefillValues.glucoseRawValue
        unitsHumanEntered = prefillValues.unitsRawValue?.toDoubleOrNull()
        glucoseHumanEntered = parseBolusGlucoseMgdl(prefillValues.glucoseRawValue, glucoseUnit) != null
        glucoseFieldEdited = prefillValues.glucoseRawValue != null
    }

    LaunchedEffect(Unit) {
        val initialPrefill = pendingPrefill
        val preparedSnapshot = initialPrefill?.pumpSnapshotPrepared == true &&
            dataStore.bolusCalcDataSnapshot.value != null &&
            dataStore.timeSinceResetResponse.value != null
        resetBolusDataStoreState(
            dataStore,
            preservePumpCalculationInputs = preparedSnapshot,
        )
        initialPrefill?.let {
            applyPrefill(it)
            pendingPrefill = null
            onPrefillConsumed()
        }
        if (!preparedSnapshot) {
            refresh()
        }
    }

    LaunchedEffect(
        refreshing,
        bolusPermissionOperation.value,
        dataStore.pumpSid.value,
        dataStore.timeSinceResetResponse.value,
        dataStore.pumpConnected.value,
    ) {
        if (refreshing) return@LaunchedEffect

        if (developerPreviewEnabled) {
            if (dataStore.bolusPermissionResponse.value?.isPermissionGranted != true) {
                dataStore.bolusPermissionResponse.value = BolusPermissionResponse(
                    0,
                    DEVELOPER_PREVIEW_BOLUS_ID,
                    0,
                )
            }
            if (!permissionGrantReported) {
                permissionGrantReported = true
                onPermissionGranted()
            }
            return@LaunchedEffect
        }

        val pumpSid = dataStore.pumpSid.value ?: return@LaunchedEffect
        val pumpSessionId =
            dataStore.timeSinceResetResponse.value?.pumpSessionId() ?: return@LaunchedEffect
        val operation = bolusPermissionOperation.value
        if (operation?.phase == BolusPermissionOperationPhase.GRANTED &&
            operation.pumpSid == pumpSid &&
            pumpSessionIdsMatch(operation.pumpSessionId, pumpSessionId) &&
            operation.bolusId != null
        ) {
            val persistedBolusId = operation.bolusId ?: return@LaunchedEffect
            if (dataStore.bolusPermissionResponse.value?.bolusId != persistedBolusId) {
                dataStore.bolusPermissionResponse.value = BolusPermissionResponse(
                    0,
                    persistedBolusId,
                    0,
                )
                PumpStateSupplier.inProgressBolusId = Supplier { persistedBolusId }
            }
            if (!permissionGrantReported) {
                permissionGrantReported = true
                onPermissionGranted()
            }
            return@LaunchedEffect
        }

        // Permission acquisition is owned by PREPARE_BOLUS_WORKFLOW. Keeping a second UI-side
        // acquisition path here allowed a denied or stale lifecycle to be retried independently
        // of the workflow which created it.
    }

    LaunchedEffect(Unit) {
        Timber.i(
            "BolusWindow: composed prefillApplied units=%s carbs=%s bg=%s",
            dataStore.bolusUnitsRawValue.value,
            dataStore.bolusCarbsRawValue.value,
            dataStore.bolusGlucoseRawValue.value
        )
    }

    fun recalculate() {
        val glucoseMgdlValue = parseBolusGlucoseMgdl(glucoseRawValue.value, glucoseUnit)
        dataStore.bolusCalculatorBuilder.value = buildBolusCalculator(
            bolusCalcDataSnapshot.value,
            bolusCalcLastBG.value,
            if (unitsHumanEntered != null) rawToDouble(unitsRawValue.value) else null,
            bolusCarbsForProfile(carbsRawValue.value, carbEntryEnabled),
            glucoseMgdlValue,
            allowAutomaticBg = !glucoseFieldEdited,
        )

        val correctionAvailable =
            dataStore.bolusCalculatorBuilder.value?.glucoseMgdl?.isPresent == true
        val currentGlucoseMgdl = dataStore.bolusCalculatorBuilder.value
            ?.glucoseMgdl
            ?.orElse(null)
        correctionRequired = correctionAvailable && (currentGlucoseMgdl ?: Int.MAX_VALUE) < 70
        if (correctionRequired) {
            correctionEnabled = true
        }
        val activeExclusions = correctionExclusions(
            dataStore.bolusConditionsExcluded.value,
            correctionEnabled = correctionEnabled && correctionAvailable,
        )
        dataStore.bolusConditionsExcluded.value = activeExclusions

        dataStore.bolusCurrentParameters.value =
            bolusCalcParameters(dataStore.bolusCalculatorBuilder.value, activeExclusions).first

        fun sortConditions(set: Set<BolusCalcCondition>?): List<BolusCalcCondition> {
            if (set == null) {
                return emptyList()
            }

            return set.sortedWith(
                compareBy(
                    { it.javaClass == BolusCalcCondition.FailedSanityCheck::class.java },
                    { it.javaClass == BolusCalcCondition.FailedPrecondition::class.java },
                    { it.javaClass == BolusCalcCondition.WaitingOnPrecondition::class.java },
                    { it.javaClass == BolusCalcCondition.Decision::class.java },
                    { it.javaClass == BolusCalcCondition.NonActionDecision::class.java },
                )
            )
        }
        val conditions = bolusCalcDecision(
            dataStore.bolusCalculatorBuilder.value,
            activeExclusions,
        )?.conditions
        dataStore.bolusCurrentConditions.value = sortConditions(conditions)

        val conditionsWithPrompt = conditions?.filter {
            it.prompt != null && !isCorrectionDecision(it)
        }
        val conditionsAcknowledged = dataStore.bolusConditionsPromptAcknowledged.value
        Timber.d("bolusCalc conditionsWithPrompt: $conditionsWithPrompt conditionsAcknowledged: $conditionsAcknowledged conditions: $conditions")
        if (conditionsWithPrompt?.size == 1) {
            var alreadyAcked = false
            conditionsAcknowledged?.forEach {
                if (conditionsWithPrompt[0] == it) {
                    alreadyAcked = true
                }
            }
            if (alreadyAcked) {
                Timber.d("bolusCalc: Not displaying acknowledged prompt again: $conditionsAcknowledged")
                dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>()
            } else {
                dataStore.bolusConditionsPrompt.value = mutableListOf<BolusCalcCondition>().let {
                    it.addAll(conditionsWithPrompt)
                    it
                }
                // showBolusConditionPrompt = true
            }
        } else {
            dataStore.bolusConditionsPrompt.value = null
            // showBolusConditionPrompt = false
        }

        val calculatedUnits = dataStore.bolusCurrentParameters.value?.units
        val calculatedValueIsStillDisplayed =
            unitsRawValue.value == lastCalculatedUnitsRawValue
        if (calculatedUnits != null &&
            calculatedUnits >= 0.0 &&
            unitsHumanEntered == null &&
            (!unitsHumanFocus || calculatedValueIsStillDisplayed)
        ) {
            val formattedCalculatedUnits = twoDecimalPlaces(calculatedUnits)
            lastCalculatedUnitsRawValue = formattedCalculatedUnits
            dataStore.bolusUnitsRawValue.value = formattedCalculatedUnits
        }

        // TODO: invalid logic for attributing override vs pre-filled units
        unitsSubtitle = when {
            unitsHumanEntered == null -> when {
                dataStore.bolusCurrentParameters.value == null -> "Units"
                dataStore.bolusCurrentParameters.value?.units == 0.0 -> "Units"
                else -> "Calculated"
            }
            else -> "Override"
        }

        val autofilledBg = dataStore.bolusCalculatorBuilder.value?.glucoseMgdl?.orElse(null)
        dataStore.bolusAutomaticBgForAbandonment.value = automaticBgForAbandonmentOrNull(
            calculatedBgMgdl = autofilledBg,
            glucoseWasEnteredManually = glucoseHumanEntered,
            dataSnapshot = bolusCalcDataSnapshot.value,
        )
        if (dataStore.bolusCurrentParameters.value != null &&
            (dataStore.bolusCurrentParameters.value!!.glucoseMgdl ?: 0) > 0 &&
            autofilledBg != null
        ) {
            dataStore.bolusGlucoseRawValue.value = GlucoseConverter.format(autofilledBg, glucoseUnit)
        }
        glucoseSubtitle = when {
            glucoseHumanEntered -> "Entered ($unitAbbrev)"
            autofilledBg != null -> "CGM ($unitAbbrev)"
            else -> "BG ($unitAbbrev)"
        }
    }

    fun showFinalBolusReview() {
        showBolusReviewDialog = true
    }

    fun prepareFinalBolusAndRequestPermission(decision: BolusLimitDecision) {
        maxHourlyBolusConfirmed = false
        lowInsulinAcknowledged = false
        dataStore.bolusFinalConditions.value =
            bolusCalcDecision(
                dataStore.bolusCalculatorBuilder.value,
                dataStore.bolusConditionsExcluded.value,
            )?.conditions

        val calculated = bolusCalcParameters(
            dataStore.bolusCalculatorBuilder.value,
            dataStore.bolusConditionsExcluded.value,
        )
        val final = applyBolusLimitDecision(calculated.first, calculated.second, decision)
        dataStore.bolusFinalParameters.value = final.first
        dataStore.bolusFinalCalcUnits.value = final.second
        val snapshot = dataStore.bolusCalcDataSnapshot.value ?: return
        val safety = evaluateMobiPumpSafetyLimits(
            requestedUnits = final.first.units,
            cartridgeRemainingInsulinUnits = snapshot.cartridgeRemainingInsulin,
            maxBolusAmountMilliunits = snapshot.maxBolusAmount,
            maxBolusHourlyTotalMilliunits = snapshot.maxBolusHourlyTotal,
            maxBolusEventsExceeded = snapshot.maxBolusEventsExceeded,
        )
        pendingSafetyRequirements = safety
        when {
            safety.maxHourly != null -> pendingMaxHourlyBolusAlert = safety.maxHourly
            safety.lowInsulin != null -> pendingLowInsulinAlert = safety.lowInsulin
            else -> showFinalBolusReview()
        }
    }

    fun requestBolusDeliveryFromInputSubmit() {
        if (refreshing) {
            return
        }

        when (val decision = evaluateMobiBolusLimit(
            params = dataStore.bolusCurrentParameters.value,
            maxBolusAmountMilliunits = dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount?.toLong(),
        )) {
            is BolusLimitDecision.WithinLimit -> prepareFinalBolusAndRequestPermission(decision)
            is BolusLimitDecision.RequiresCappedConfirmation -> pendingMaxBolusAlert = decision
            is BolusLimitDecision.Invalid -> Unit
        }
    }

    LaunchedEffect(carbEntryEnabled) {
        if (!carbEntryEnabled) {
            dataStore.bolusCarbsRawValue.value = null
        }
    }

    LaunchedEffect(
        unitsRawValue.value,
        carbsRawValue.value,
        glucoseRawValue.value,
        bolusCalcDataSnapshot.value,
        bolusCalcLastBG.value,
        unitsHumanFocus,
    ) {
        recalculate()
    }

    if (!compactCover) {
        HeaderLine("Bolus")
    }

    if (developerPreviewEnabled) {
        Line(
            if (compactCover) "Simulation only — no pump delivery."
            else "Using the simulated Developer Profile. Pump delivery is disabled."
        )
    } else {
        when (bolusPermissionOperation.value?.phase) {
            BolusPermissionOperationPhase.REQUESTING ->
                Line("Reserving bolus permission from the pump…")
            BolusPermissionOperationPhase.AUTOMATIC_BG_PENDING,
            BolusPermissionOperationPhase.AUTOMATIC_BG_SENDING,
            BolusPermissionOperationPhase.AUTOMATIC_BG_OUTCOME_UNKNOWN,
            BolusPermissionOperationPhase.RELEASE_PENDING,
            BolusPermissionOperationPhase.RELEASE_RECONCILING,
            BolusPermissionOperationPhase.RELEASE_SENDING,
            BolusPermissionOperationPhase.RELEASE_OUTCOME_UNKNOWN,
            -> Line("Finishing the previous bolus-permission cleanup…")
            BolusPermissionOperationPhase.BLOCKED_SESSION_MISMATCH ->
                Line("Bolus is unavailable because the saved permission belongs to another pump session.")
            BolusPermissionOperationPhase.INITIATION_SUBMITTED,
            BolusPermissionOperationPhase.INITIATION_REJECTED,
            BolusPermissionOperationPhase.INITIATION_REJECTED_OUTCOME_UNKNOWN,
            -> Line("Bolus is unavailable while the previous request is being reconciled.")
            BolusPermissionOperationPhase.DENIED ->
                Line("The pump did not grant bolus permission.")
            else -> Unit
        }
    }

    BolusEntryFormRegion(
        unitsSubtitle = unitsSubtitle,
        carbsSubtitle = carbsSubtitle,
        glucoseSubtitle = glucoseSubtitle,
        glucoseUnit = glucoseUnit,
        carbEntryEnabled = carbEntryEnabled,
        onUnitsChanged = {
            val previousValue = dataStore.bolusUnitsRawValue.value
            dataStore.bolusUnitsRawValue.value = it
            if (it != previousValue) {
                unitsHumanEntered = if (it == "") null else it.toDoubleOrNull()
            }
        },
        onUnitsFocusChanged = { isFocused ->
            unitsHumanFocus = isFocused
        },
        onCarbsChanged = {
            // Receiving input here proves the units field is no longer the active editor, even if
            // Compose has not yet delivered its focus-loss callback in this frame.
            unitsHumanFocus = false
            dataStore.bolusCarbsRawValue.value = it
        },
        onGlucoseChanged = {
            unitsHumanFocus = false
            glucoseFieldEdited = true
            dataStore.bolusGlucoseRawValue.value = it
            glucoseHumanEntered = parseBolusGlucoseMgdl(it, glucoseUnit) != null
            dataStore.bolusManualBgForAbandonment.value = null
        },
        onGlucoseSubmitRequested = {
            dataStore.bolusManualBgForAbandonment.value =
                parseBolusGlucoseMgdl(dataStore.bolusGlucoseRawValue.value, glucoseUnit)
                    ?.takeIf { glucoseHumanEntered }
        },
    )

    if (!carbEntryEnabled) {
        Line("Carbohydrates are off in the active profile. Enter the requested bolus in units.")
    }

    BolusConditionPromptRegion(
        recalculate = { recalculate() }
    )

    val bolusCurrentParameters = dataStore.bolusCurrentParameters.observeAsState()

    LaunchedEffect(
        bolusCurrentParameters.value,
        bolusCalcDataSnapshot.value?.maxBolusAmount,
        bolusPermissionResponse.value,
    ) {
        bolusButtonEnabled = evaluateMobiBolusLimit(
            params = bolusCurrentParameters.value,
            maxBolusAmountMilliunits = dataStore.bolusCalcDataSnapshot.value?.maxBolusAmount?.toLong(),
        ) !is BolusLimitDecision.Invalid &&
            bolusPermissionResponse.value?.isPermissionGranted == true
    }

    BolusDeliverActionRegion(
        refreshing = refreshing,
        bolusButtonEnabled = bolusButtonEnabled,
        bolusUnits = bolusCurrentParameters.value?.units,
        calculation = bolusCalcDecision(
            dataStore.bolusCalculatorBuilder.value,
            dataStore.bolusConditionsExcluded.value,
        )?.units?.toBolusDeliveryCalculation(),
        correctionEnabled = correctionEnabled,
        overrideActive = overrideActive,
        correctionRequired = correctionRequired,
        correctionAvailable = dataStore.bolusCalculatorBuilder.value
            ?.glucoseMgdl
            ?.isPresent == true,
        correctionBgLabel = dataStore.bolusCalculatorBuilder.value
            ?.glucoseMgdl
            ?.orElse(null)
            ?.let { GlucoseConverter.format(it, glucoseUnit) },
        correctionTargetLabel = bolusCalcDataSnapshot.value
            ?.targetBg
            ?.let { GlucoseConverter.format(it, glucoseUnit) },
        correctionGlucoseUnitLabel = unitAbbrev,
        onCorrectionEnabledChanged = { enabled ->
            if (!overrideActive) {
                correctionEnabled = enabled
                recalculate()
            }
        },
        onDeliverRequested = { requestBolusDeliveryFromInputSubmit() },
    )

    pendingMaxBolusAlert?.let { decision ->
        MaxBolusAlertDialog(
            requestedUnits = decision.requestedUnits,
            maxBolusUnits = decision.maxBolusUnits,
            onCancel = { pendingMaxBolusAlert = null },
            onContinueAtMaximum = {
                pendingMaxBolusAlert = null
                prepareFinalBolusAndRequestPermission(decision)
            },
        )
    }

    pendingMaxHourlyBolusAlert?.let {
        MaxHourlyBolusAlertDialog(
            requestedMilliunits = pendingSafetyRequirements.lowInsulin?.requestedMilliunits
                ?: dataStore.bolusFinalParameters.value
                    ?.units
                    ?.let(InsulinUnit::from1To1000)
                ?: 0L,
            onNo = { pendingMaxHourlyBolusAlert = null },
            onYes = {
                pendingMaxHourlyBolusAlert = null
                maxHourlyBolusConfirmed = true
                val lowInsulin = pendingSafetyRequirements.lowInsulin
                if (lowInsulin != null) {
                    pendingLowInsulinAlert = lowInsulin
                } else {
                    showFinalBolusReview()
                }
            },
        )
    }

    pendingLowInsulinAlert?.let {
        LowInsulinAlertDialog(
            onAcknowledge = {
                pendingLowInsulinAlert = null
                lowInsulinAcknowledged = true
                showFinalBolusReview()
            },
        )
    }

    if (showBolusReviewDialog) {
        val finalParameters = dataStore.bolusFinalParameters.value
        if (finalParameters != null) {
            BolusReviewDialog(
                bolusUnits = finalParameters.units,
                calculation = dataStore.bolusFinalCalcUnits.value?.toBolusDeliveryCalculation(),
                correctionEnabled = correctionEnabled,
                overrideActive = overrideActive,
                compactCover = compactCover,
                onBack = { showBolusReviewDialog = false },
                onConfirm = {
                    showBolusReviewDialog = false
                    showPermissionCheckDialog = true
                },
            )
        }
    }

    if (showPermissionCheckDialog) {
        BolusPermissionDialogRegion(
            showPermissionCheckDialog = showPermissionCheckDialog,
            onDismiss = {
                showPermissionCheckDialog = false
                showBolusReviewDialog = true
            },
            onPermissionNoLongerUsable = { message ->
                queueUnusedBolusPermissionRelease()
                showPermissionCheckDialog = false
                dataStore.bolusPermissionResponse.value = null
                bolusButtonEnabled = false
                bolusWorkflowError = message
            },
            onShowInProgress = {
                showPermissionCheckDialog = false
                showBolusConfirmedDialog = true
            },
            onDeveloperPreviewAuthenticated = { units ->
                showPermissionCheckDialog = false
                developerPreviewAuthenticatedUnits = units
                showBolusConfirmedDialog = true
            },
            correctionEnabled = correctionEnabled,
            glucoseWasEnteredManually = glucoseHumanEntered,
            maxHourlyBolusConfirmed = maxHourlyBolusConfirmed,
            lowInsulinAcknowledged = lowInsulinAcknowledged,
            requestBolusAuthentication = requestBolusAuthentication,
            sendServiceBolusRequest = sendServiceBolusRequest
        )
    }

    bolusWorkflowError?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bolus not sent") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        bolusWorkflowError = null
                        resetBolusDataStoreState(dataStore)
                        closeWindow()
                    },
                ) {
                    Text("Return to dashboard")
                }
            },
        )
    }

    fun finishConfirmedSubmission() {
        val authenticatedUnits = developerPreviewAuthenticatedUnits
        showBolusConfirmedDialog = false
        developerPreviewAuthenticatedUnits = null
        dataStore.bolusPermissionResponse.value = null
        resetBolusDataStoreState(dataStore)
        authenticatedUnits?.let(onDeveloperPreviewBolusInitiated)
        onBolusSessionCompleted(authenticatedUnits == null)
    }

    if (showBolusConfirmedDialog) {
        LaunchedEffect(showBolusConfirmedDialog) {
            delay(BOLUS_CONFIRMED_DISPLAY_MILLIS)
            finishConfirmedSubmission()
        }

        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bolus Confirmed") },
            text = {
                Text(
                    if (developerPreviewAuthenticatedUnits != null) {
                        "Android verified the device credential. Developer Preview will simulate " +
                            "delivery without sending a pump command. Returning to the dashboard…"
                    } else {
                        "Android verified the device credential and the bolus request was submitted. " +
                            "Returning to the dashboard…"
                    },
                )
            },
            confirmButton = {},
        )
    }
}

private const val BOLUS_CONFIRMED_DISPLAY_MILLIS = 1_200L
private const val DEVELOPER_PREVIEW_BOLUS_ID = 1
private const val BOLUS_PREPARATION_POLL_MILLIS = 100L
private const val BOLUS_PREPARATION_MAX_ATTEMPTS = 300

internal class BolusSnapshotRefreshGate {
    private var inFlight = false

    @Synchronized
    fun tryBegin(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    @Synchronized
    fun finish() {
        inFlight = false
    }
}


fun buildBolusCalculator(
    dataSnapshot: BolusCalcDataSnapshotResponse?,
    lastBG: LastBGResponse?,
    bolusUnitsUserInput: Double?,
    bolusCarbsGramsUserInput: Int?,
    bolusBgMgdlUserInput: Int?,
    allowAutomaticBg: Boolean = true,
): BolusCalculatorBuilder {
    Timber.i("buildBolusCalculator: INPUT units=$bolusUnitsUserInput carbs=$bolusCarbsGramsUserInput bg=$bolusBgMgdlUserInput dataSnapshot=$dataSnapshot lastBG=$lastBG")
    // The fresh Mobi snapshot can carry the current automatic CGM value without a separate
    // LastBG response. Use PumpX2's snapshot constructor, as the preflight calculation does,
    // so that value remains available on the calculation screen. Incremental initialization
    // waits for LastBG and incorrectly reports that no BG is available in this pump response shape.
    val bolusCalc = if (dataSnapshot != null && allowAutomaticBg) {
        BolusCalculatorBuilder(dataSnapshot, lastBG)
    } else if (dataSnapshot != null) {
        // PumpX2 requires a BolusCalcLastBG object even when the user supplies a finger-stick BG.
        // Build that object from a calculation-only copy whose automatic CGM value is disabled.
        // A non-empty manual value below is therefore calculated normally, while an explicitly
        // cleared field remains unavailable instead of silently falling back to hidden CGM data.
        BolusCalculatorBuilder(dataSnapshot.withoutAutomaticBgForCalculation(), lastBG)
    } else {
        BolusCalculatorBuilder().also { calculator ->
            lastBG?.let(calculator::onLastBGResponse)
        }
    }

    bolusCalc.setInsulinUnits(bolusUnitsUserInput)
    bolusCalc.setCarbsValueGrams(bolusCarbsGramsUserInput)
    bolusCalc.setGlucoseMgdl(bolusBgMgdlUserInput)
    return bolusCalc
}

private fun BolusCalcDataSnapshotResponse.withoutAutomaticBgForCalculation() =
    BolusCalcDataSnapshotResponse(
        isUnacked,
        0,
        iob,
        cartridgeRemainingInsulin,
        targetBg,
        isf,
        carbEntryEnabled,
        carbRatio,
        maxBolusAmount,
        maxBolusHourlyTotal,
        maxBolusEventsExceeded,
        maxIobEventsExceeded,
        false,
    )
fun bolusCalcParameters(
    bolusCalc: BolusCalculatorBuilder?,
    bolusConditionsExcluded: MutableSet<BolusCalcCondition>?
): Pair<BolusParameters, BolusCalcUnits> {
    val decision = bolusCalcDecision(bolusCalc, bolusConditionsExcluded)
    return Pair(
        BolusParameters(
            decision?.units?.total,
            bolusCalc?.carbsValueGrams?.orElse(0),
            bolusCalc?.glucoseMgdl?.orElse(null)
        ),
        decision!!.units!!)
}

fun bolusCalcDecision(
    bolusCalc: BolusCalculatorBuilder?,
    bolusConditionsExcluded: MutableSet<BolusCalcCondition>?
): BolusCalcDecision? {
    val excluded = (bolusConditionsExcluded ?: mutableSetOf()).stream().toArray { arrayOfNulls<BolusCalcCondition>(it) }
    val decision = bolusCalc?.build()?.parse(*excluded)
    Timber.i("bolusCalcDecision: OUTPUT units=${decision?.units} conditions=${decision?.conditions} excluded=$excluded")
    return decision
}

fun rawToDouble(s: String?): Double? {
    return if (s == "") 0.0 else s?.replace(',', '.')?.toDoubleOrNull()
}

fun rawToInt(s: String?): Int? {
    return s?.takeIf { it.isNotBlank() }?.toIntOrNull()
}

fun bolusCarbsForProfile(raw: String?, carbEntryEnabled: Boolean): Int? =
    if (carbEntryEnabled) rawToInt(raw) else null

fun parseBolusGlucoseMgdl(raw: String?, unit: GlucoseUnit): Int? {
    val normalized = raw?.trim()?.replace(',', '.')?.takeIf { it.isNotEmpty() } ?: return null

    return when (unit) {
        GlucoseUnit.MGDL -> normalized
            .takeIf { it.matches(Regex("[0-9]+")) }
            ?.toIntOrNull()
        GlucoseUnit.MMOL -> normalized
            .takeIf { it.matches(Regex("[0-9]+\\.[0-9]+|[0-9]+")) }
            ?.toDoubleOrNull()
            ?.let { GlucoseConverter.convert(it, GlucoseUnit.MMOL, GlucoseUnit.MGDL).roundToInt() }
    }
}

fun resetBolusDataStoreState(
    dataStore: DataStore,
    preservePumpCalculationInputs: Boolean = false,
) {
    Timber.d("bolusCalc resetBolusDataStoreState")
    if (!preservePumpCalculationInputs) {
        dataStore.bolusCalcDataSnapshot.value = null
        dataStore.bolusCalcLastBG.value = null
    }
    dataStore.bolusPermissionResponse.value = null
    dataStore.bolusCancelResponse.value = null
    dataStore.bolusInitiateResponse.value = null
    dataStore.lastBolusStatusResponse.value = null
    dataStore.bolusCalculatorBuilder.value = null
    dataStore.bolusCurrentParameters.value = null
    dataStore.bolusCurrentConditions.value = null
    dataStore.bolusConditionsPrompt.value = null
    dataStore.bolusConditionsPromptAcknowledged.value = null
    dataStore.bolusConditionsExcluded.value = null
    dataStore.bolusFinalParameters.value = null
    dataStore.bolusFinalCalcUnits.value = null
    dataStore.bolusFinalConditions.value = null
    dataStore.bolusUnitsRawValue.value = null
    dataStore.bolusCarbsRawValue.value = null
    dataStore.bolusGlucoseRawValue.value = null
    dataStore.bolusAutomaticBgForAbandonment.value = null
    dataStore.bolusManualBgForAbandonment.value = null
}

@Preview
@Composable
fun DefaultBolusPreview() {
    BolusPreview()
}
