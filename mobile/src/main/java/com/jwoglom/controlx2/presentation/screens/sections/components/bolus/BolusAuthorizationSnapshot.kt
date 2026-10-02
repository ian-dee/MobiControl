package com.jwoglom.controlx2.presentation.screens.sections.components.bolus

import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.pumpSessionId
import com.jwoglom.controlx2.shared.pumpSessionIdsMatch
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.calculator.BolusParameters
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.control.BolusPermissionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse

/** Immutable request captured immediately before the system authentication prompt opens. */
internal data class BolusAuthorizationSnapshot(
    val permissionResponse: BolusPermissionResponse,
    val parameters: BolusParameters,
    val calculation: BolusCalcUnits,
    val pumpDataSnapshot: BolusCalcDataSnapshotResponse,
    val pumpTime: TimeSinceResetResponse,
    val pumpConnected: Boolean?,
    val basalStatus: BasalStatus?,
    val notificationBundle: NotificationBundle?,
    val developerPreviewEnabled: Boolean,
    val correctionEnabled: Boolean,
    val glucoseWasEnteredManually: Boolean = false,
    val maxHourlyBolusConfirmed: Boolean = false,
    val lowInsulinAcknowledged: Boolean = false,
) {
    val bolusId: Int = permissionResponse.bolusId
    private val pumpDataCargo: ByteArray = pumpDataSnapshot.cargo.copyOf()
    private val pumpSessionId: Long = pumpTime.pumpSessionId()
    private val requestedBolusSafetyState =
        BolusCommandSafetyState.from(parameters, calculation)
    private val notificationSafetyState: NotificationSafetyState =
        notificationBundle.toNotificationSafetyState()

    fun validateCurrentState(
        permissionResponse: BolusPermissionResponse?,
        parameters: BolusParameters?,
        calculation: BolusCalcUnits?,
        pumpDataSnapshot: BolusCalcDataSnapshotResponse?,
        pumpTime: TimeSinceResetResponse?,
        pumpConnected: Boolean?,
        basalStatus: BasalStatus?,
        notificationBundle: NotificationBundle?,
        correctionEnabled: Boolean,
        glucoseWasEnteredManually: Boolean = false,
        maxHourlyBolusConfirmed: Boolean = false,
        lowInsulinAcknowledged: Boolean = false,
    ): BolusAuthorizationValidation {
        if (!developerPreviewEnabled && pumpConnected != true) {
            return BolusAuthorizationValidation.Invalid(
                "The pump disconnected while authenticating. The bolus was not sent.",
            )
        }
        if (basalStatus != this.basalStatus || isDeliverySuspended(basalStatus)) {
            return BolusAuthorizationValidation.Invalid(
                "Insulin delivery state changed while authenticating. Review the pump state and try again.",
            )
        }
        if (notificationBundle.toNotificationSafetyState() != notificationSafetyState) {
            return BolusAuthorizationValidation.Invalid(
                "Pump alerts changed while authenticating. Review the alerts and try again.",
            )
        }
        if (
            permissionResponse?.isPermissionGranted != true ||
            permissionResponse.bolusId != bolusId
        ) {
            return BolusAuthorizationValidation.Invalid(
                "Pump bolus permission changed while authenticating. Request permission again.",
            )
        }
        if (parameters == null || calculation == null ||
            BolusCommandSafetyState.from(parameters, calculation) != requestedBolusSafetyState ||
            correctionEnabled != this.correctionEnabled ||
            glucoseWasEnteredManually != this.glucoseWasEnteredManually ||
            maxHourlyBolusConfirmed != this.maxHourlyBolusConfirmed ||
            lowInsulinAcknowledged != this.lowInsulinAcknowledged ||
            pumpDataSnapshot == null || !pumpDataSnapshot.cargo.contentEquals(pumpDataCargo)
        ) {
            return BolusAuthorizationValidation.Invalid(
                "The bolus calculation changed while authenticating. Review it and try again.",
            )
        }
        if (!developerPreviewEnabled &&
            (pumpTime == null || !pumpSessionIdsMatch(pumpSessionId, pumpTime.pumpSessionId()))
        ) {
            return BolusAuthorizationValidation.Invalid(
                "The pump session changed while authenticating. The bolus was not sent.",
            )
        }
        if (parameters.units < 0.05) {
            return BolusAuthorizationValidation.Invalid(
                "The bolus amount is no longer valid. The bolus was not sent.",
            )
        }
        if (!isWithinPumpMaxBolus(parameters, pumpDataSnapshot)) {
            return BolusAuthorizationValidation.Invalid(
                "The bolus is above the pump's current Max Bolus setting. The bolus was not sent.",
            )
        }
        return BolusAuthorizationValidation.Valid
    }

    companion object {
        fun captureOrNull(
            permissionResponse: BolusPermissionResponse?,
            parameters: BolusParameters?,
            calculation: BolusCalcUnits?,
            pumpDataSnapshot: BolusCalcDataSnapshotResponse?,
            pumpTime: TimeSinceResetResponse?,
            pumpConnected: Boolean?,
            basalStatus: BasalStatus?,
            notificationBundle: NotificationBundle?,
            developerPreviewEnabled: Boolean,
            correctionEnabled: Boolean,
            glucoseWasEnteredManually: Boolean = false,
            maxHourlyBolusConfirmed: Boolean = false,
            lowInsulinAcknowledged: Boolean = false,
        ): BolusAuthorizationSnapshot? {
            if (
                permissionResponse?.isPermissionGranted != true ||
                parameters == null ||
                parameters.units < 0.05 ||
                calculation == null ||
                pumpDataSnapshot == null ||
                !isWithinPumpMaxBolus(parameters, pumpDataSnapshot) ||
                pumpTime == null ||
                (!developerPreviewEnabled && pumpConnected != true) ||
                isDeliverySuspended(basalStatus)
            ) {
                return null
            }
            return BolusAuthorizationSnapshot(
                permissionResponse = permissionResponse,
                parameters = parameters,
                calculation = calculation,
                pumpDataSnapshot = pumpDataSnapshot,
                pumpTime = pumpTime,
                pumpConnected = pumpConnected,
                basalStatus = basalStatus,
                notificationBundle = notificationBundle,
                developerPreviewEnabled = developerPreviewEnabled,
                correctionEnabled = correctionEnabled,
                glucoseWasEnteredManually = glucoseWasEnteredManually,
                maxHourlyBolusConfirmed = maxHourlyBolusConfirmed,
                lowInsulinAcknowledged = lowInsulinAcknowledged,
            )
        }
    }
}

internal sealed interface BolusAuthorizationValidation {
    data object Valid : BolusAuthorizationValidation

    data class Invalid(val message: String) : BolusAuthorizationValidation
}

private fun isDeliverySuspended(basalStatus: BasalStatus?): Boolean =
    basalStatus == BasalStatus.PUMP_SUSPENDED || basalStatus == BasalStatus.BASALIQ_SUSPENDED

private data class NotificationSafetyState(
    val available: Boolean,
    val activeAlerts: List<String>,
)

private fun NotificationBundle?.toNotificationSafetyState(): NotificationSafetyState =
    NotificationSafetyState(
        available = this != null,
        activeAlerts = this?.get()
            ?.map { notification ->
                "${notification.javaClass.name}:${notification.id}:${notification.description}"
            }
            ?.sorted()
            ?: emptyList(),
    )

/**
 * Exact values that the app will turn into an InitiateBolusRequest after authentication.
 *
 * PumpX2's calculator models are Java value objects, but their generated equality can compare
 * boxed integers by identity. Comparing the normalized command fields avoids false rejections
 * (for example, a refreshed 155 mg/dL object) without weakening the amount check.
 */
private data class BolusCommandSafetyState(
    val totalMilliunits: Long,
    val carbsGrams: Int?,
    val glucoseMgdl: Int?,
    val foodMilliunits: Long,
    val bgMilliunits: Long,
    val iobMilliunits: Long,
    val userMilliunits: Long,
) {
    companion object {
        fun from(
            parameters: BolusParameters,
            calculation: BolusCalcUnits,
        ): BolusCommandSafetyState = BolusCommandSafetyState(
            totalMilliunits = InsulinUnit.from1To1000(parameters.units),
            carbsGrams = parameters.carbsGrams,
            glucoseMgdl = parameters.glucoseMgdl,
            foodMilliunits = InsulinUnit.from1To1000(calculation.fromCarbs),
            bgMilliunits = InsulinUnit.from1To1000(calculation.fromBG),
            iobMilliunits = InsulinUnit.from1To1000(calculation.fromIOB),
            userMilliunits = InsulinUnit.from1To1000(calculation.fromUser),
        )
    }
}

private fun isWithinPumpMaxBolus(
    parameters: BolusParameters,
    pumpDataSnapshot: BolusCalcDataSnapshotResponse,
): Boolean {
    val maxBolusUnits = InsulinUnit.from1000To1(pumpDataSnapshot.maxBolusAmount.toLong())
    return parameters.units.isFinite() && parameters.units <= maxBolusUnits
}
