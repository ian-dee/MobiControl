package com.jwoglom.controlx2.presentation.simulation

import com.jwoglom.controlx2.DeveloperPreviewTempRate
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.screens.sections.profileSnapshots
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.enums.CGMSessionState
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.enums.UserMode
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BasalLimitSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.GlobalMaxBolusSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TempRateResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DeveloperPreviewProfile(
    val basalRateUnitsPerHour: Double,
    val carbRatioGramsPerUnit: Double,
    val correctionFactorMgdlPerUnit: Int,
    val targetGlucoseMgdl: Int,
    val insulinDurationHours: Int,
    val maximumBolusUnits: Double,
    val glucoseUnit: GlucoseUnit,
    val startingIobUnits: Double,
    val cgmValueMgdl: Int,
) {
    companion object {
        val DEFAULT = DeveloperPreviewProfile(
            // Synthetic demonstration data, not a patient profile or treatment recommendation.
            basalRateUnitsPerHour = 0.6,
            carbRatioGramsPerUnit = 10.0,
            correctionFactorMgdlPerUnit = 40,
            targetGlucoseMgdl = 110,
            insulinDurationHours = 5,
            maximumBolusUnits = 15.0,
            glucoseUnit = GlucoseUnit.MGDL,
            startingIobUnits = 1.5,
            cgmValueMgdl = 150,
        )
    }
}

fun createDeveloperPreviewBolusSnapshot(
    profile: DeveloperPreviewProfile = DeveloperPreviewProfile.DEFAULT,
    carbEntryEnabled: Boolean = true,
    cartridgeRemainingInsulinUnits: Int = 100,
): BolusCalcDataSnapshotResponse {
    val iobMilliunits = InsulinUnit.from1To1000(profile.startingIobUnits)
    val carbRatioMilliunits = InsulinUnit.from1To1000(profile.carbRatioGramsPerUnit)
    val maxBolusMilliunits = InsulinUnit.from1To1000(profile.maximumBolusUnits)

    return BolusCalcDataSnapshotResponse(
        false, // isUnacked
        profile.cgmValueMgdl, // pump snapshot's CGM/autopop value
        iobMilliunits,
        cartridgeRemainingInsulinUnits,
        profile.targetGlucoseMgdl,
        profile.correctionFactorMgdlPerUnit,
        carbEntryEnabled,
        carbRatioMilliunits,
        maxBolusMilliunits.toInt(),
        maxBolusMilliunits,
        false,
        false,
        true, // allow the simulated CGM value to populate the BG field
    )
}

fun createDeveloperPreviewLastBg(): LastBGResponse {
    // A zero LastBG value tells the calculator to use the fresh CGM/autopop
    // value in BolusCalcDataSnapshotResponse instead of a manual BG record.
    return LastBGResponse(0L, 0, LastBGResponse.BgSource.CGM)
}

fun applyDeveloperPreviewBolusInputs(
    dataStore: DataStore,
    profile: DeveloperPreviewProfile = DeveloperPreviewProfile.DEFAULT,
    maxBolusMilliunits: Int = InsulinUnit.from1To1000(profile.maximumBolusUnits).toInt(),
) {
    val profileWithCurrentLimit = profile.copy(
        maximumBolusUnits = InsulinUnit.from1000To1(maxBolusMilliunits.toLong()),
    )
    val carbEntryEnabled = profileSnapshots(dataStore.idpManager.value)
        .firstOrNull { it.active }
        ?.carbEntryEnabled
        ?: true
    dataStore.bolusCalcDataSnapshot.value = createDeveloperPreviewBolusSnapshot(
        profileWithCurrentLimit,
        carbEntryEnabled = carbEntryEnabled,
    )
    dataStore.bolusCalcLastBG.value = createDeveloperPreviewLastBg()
    dataStore.timeSinceResetResponse.value = TimeSinceResetResponse(0L, 0L)
}

fun applyDeveloperPreviewProfile(
    dataStore: DataStore,
    profile: DeveloperPreviewProfile = DeveloperPreviewProfile.DEFAULT,
    deliverySuspended: Boolean = false,
    controlIqMode: UserMode = UserMode.NONE,
    controlIqEnabled: Boolean = true,
    controlIqWeightPounds: Int = 170,
    controlIqTotalDailyInsulinUnits: Int = 35,
    tempRate: DeveloperPreviewTempRate? = null,
    maxBolusMilliunits: Int = InsulinUnit.from1To1000(profile.maximumBolusUnits).toInt(),
    basalLimitMilliunits: Long = 3_000L,
) {
    dataStore.pumpConnected.value = false
    dataStore.pumpSetupStage.value = PumpSetupStage.PUMPX2_PUMP_CONNECTED
    dataStore.setupDeviceName.value = "Simulated Mobi profile"
    dataStore.setupDeviceModel.value = "Mobi (simulated)"

    dataStore.glucoseUnitPreference.value = profile.glucoseUnit
    dataStore.cgmReading.value = profile.cgmValueMgdl
    dataStore.cgmDelta.value = 0
    // Match the filled trend glyph emitted by HomeScreenMirrorResponse on a
    // connected pump so compact-cover previews exercise the production UI.
    dataStore.cgmDeltaArrow.value = "▶"
    dataStore.cgmStatusText.value = "Simulated CGM"
    dataStore.cgmSessionState.value = CGMSessionState.ACTIVE
    dataStore.cgmSessionExpireRelative.value = "8 days"
    dataStore.cgmSessionExpireExact.value = "August 25, 2026 9:00 AM"
    dataStore.cgmTransmitterStatus.value = "Good"

    dataStore.iobUnits.value = profile.startingIobUnits
    val activeTempRate = tempRate?.takeIf { it.isActiveAt(System.currentTimeMillis()) }
    val effectiveBasalRate = activeTempRate?.let {
        profile.basalRateUnitsPerHour * it.percent / 100.0
    } ?: profile.basalRateUnitsPerHour
    dataStore.basalRate.value = String.format(Locale.US, "%.2fu", effectiveBasalRate)
    dataStore.basalStatus.value = when {
        deliverySuspended -> BasalStatus.PUMP_SUSPENDED
        activeTempRate != null -> BasalStatus.TEMP_RATE
        else -> BasalStatus.ON
    }
    dataStore.tempRateActive.value = activeTempRate != null
    dataStore.tempRateDetails.value = activeTempRate?.let {
        TempRateResponse(true, it.percent, 0L, it.durationMinutes * 60L)
    } ?: TempRateResponse(false, 100, 0L, 0L)
    dataStore.controlIQMode.value = controlIqMode
    dataStore.controlIQStatus.value = if (controlIqEnabled) "On" else "Off"
    dataStore.controlIQEnabled.value = controlIqEnabled
    dataStore.controlIQWeight.value = controlIqWeightPounds
    dataStore.controlIQWeightUnit.value = "lbs"
    dataStore.controlIQTotalDailyInsulin.value = controlIqTotalDailyInsulinUnits

    dataStore.maxBolusAmount.value = maxBolusMilliunits
    dataStore.globalMaxBolusSettingsResponse.value =
        GlobalMaxBolusSettingsResponse(maxBolusMilliunits, 10_000)
    dataStore.basalLimitSettingsResponse.value =
        BasalLimitSettingsResponse(basalLimitMilliunits, 3_000L)

    if (dataStore.lastBolusStatus.value.isNullOrBlank()) {
        dataStore.lastBolusSource.value =
            BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
        dataStore.lastBolusStatus.value = String.format(
            Locale.US,
            "%.2fu at %s (demo)",
            5.0,
            LocalTime.now().minusMinutes(8)
                .format(DateTimeFormatter.ofPattern("h:mm a")),
        )
    }

    applyDeveloperPreviewBolusInputs(dataStore, profile, maxBolusMilliunits)
}
