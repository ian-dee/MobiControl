package com.jwoglom.controlx2

import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingSource
import com.jwoglom.pumpx2.pump.messages.models.BloodGlucoseReadingType
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcUnits
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RemoteBgEntryRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.controlx2.shared.AbandonedBolusCleanupRequest
import com.jwoglom.controlx2.shared.BolusPermissionOperation
import com.jwoglom.controlx2.shared.BolusPermissionOperationPhase
import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.controlx2.shared.PostBolusRecordPlan

internal fun automaticBgForAbandonmentOrNull(
    calculatedBgMgdl: Int?,
    glucoseWasEnteredManually: Boolean,
    dataSnapshot: BolusCalcDataSnapshotResponse?,
): Int? = calculatedBgMgdl?.takeIf { bg ->
    !glucoseWasEnteredManually &&
        bg > 0 &&
        dataSnapshot?.isAutopopAllowed == true &&
        bg == dataSnapshot.correctionFactor
}

internal fun createAbandonedBolusCleanupRequestOrNull(
    permission: BolusPermissionOperation?,
    automaticBgMgdl: Int?,
    manualBgMgdl: Int? = null,
): AbandonedBolusCleanupRequest? {
    if (permission?.phase != BolusPermissionOperationPhase.GRANTED) return null
    val identity = permission.identity ?: return null
    return AbandonedBolusCleanupRequest(
        identity = identity,
        bgMgdl = manualBgMgdl?.takeIf { it in 1..0xFFFF }
            ?: automaticBgMgdl?.takeIf { it in 1..0xFFFF },
        bgRecordType = when {
            manualBgMgdl?.takeIf { it in 1..0xFFFF } != null -> BolusBgRecordType.MANUAL
            automaticBgMgdl?.takeIf { it in 1..0xFFFF } != null -> BolusBgRecordType.AUTOMATIC
            else -> BolusBgRecordType.NONE
        },
    )
}

internal fun createRemoteBgEntryRequestOrNull(
    bgValue: Int?,
    bolusId: Int,
    dataSnapshot: BolusCalcDataSnapshotResponse,
    timeSinceReset: TimeSinceResetResponse,
): RemoteBgEntryRequest? {
    val validBgValue = bgValue?.takeIf { it > 0 } ?: return null
    val pumpTime = timeSinceReset.pumpTimeSecondsSinceReset.takeIf { it > 0 } ?: return null
    val autopopBg = dataSnapshot.isAutopopAllowed &&
        dataSnapshot.correctionFactor > 0 &&
        validBgValue == dataSnapshot.correctionFactor

    return RemoteBgEntryRequest(
        validBgValue,
        false, // useForCgmCalibration
        if (autopopBg) BloodGlucoseReadingType.AUTO else BloodGlucoseReadingType.MANUAL,
        if (autopopBg) BloodGlucoseReadingSource.REMOTE else BloodGlucoseReadingSource.PUMP,
        pumpTime,
        bolusId,
    )
}

internal fun createPostBolusRecordPlan(
    bgValue: Int?,
    carbsGrams: Int,
    bolusId: Int,
    dataSnapshot: BolusCalcDataSnapshotResponse,
    glucoseWasEnteredManually: Boolean = false,
): PostBolusRecordPlan {
    val validBg = bgValue?.takeIf { it > 0 }
    val automaticBg = !glucoseWasEnteredManually && validBg != null &&
        dataSnapshot.isAutopopAllowed &&
        dataSnapshot.correctionFactor > 0 &&
        validBg == dataSnapshot.correctionFactor
    return PostBolusRecordPlan(
        bolusId = bolusId,
        bgMgdl = validBg,
        bgRecordType = when {
            validBg == null -> BolusBgRecordType.NONE
            automaticBg -> BolusBgRecordType.AUTOMATIC
            else -> BolusBgRecordType.MANUAL
        },
        carbsGrams = carbsGrams.coerceAtLeast(0),
    )
}

/**
 * The calculator retains its component breakdown even when a correction decision is declined.
 * This explicit gate prevents a declined correction from leaking into InitiateBolusRequest.
 */
internal fun plannedCorrectionVolumeMilliunits(
    calculation: BolusCalcUnits,
    correctionEnabled: Boolean,
): Long {
    if (!correctionEnabled) return 0L
    return (
        InsulinUnit.from1To1000(calculation.fromBG) +
            InsulinUnit.from1To1000(calculation.fromIOB)
        ).coerceAtLeast(0L)
}

internal fun plannedBolusTypeBitmask(
    carbsGrams: Int,
    correctionVolumeMilliunits: Long,
): Int {
    require(carbsGrams >= 0)
    require(correctionVolumeMilliunits >= 0)

    val types = mutableListOf<BolusDeliveryHistoryLog.BolusType>()
    if (carbsGrams > 0) {
        types.add(BolusDeliveryHistoryLog.BolusType.FOOD1)
    }
    if (correctionVolumeMilliunits > 0) {
        types.add(BolusDeliveryHistoryLog.BolusType.CORRECTION)
    }
    if (types.isEmpty()) {
        types.add(BolusDeliveryHistoryLog.BolusType.FOOD2)
    }
    return BolusDeliveryHistoryLog.BolusType.toBitmask(*types.toTypedArray())
}

internal fun createInitiateBolusRequest(
    totalUnits: Double,
    bolusId: Int,
    foodUnits: Double,
    correctionVolumeMilliunits: Long,
    carbsGrams: Int,
    bgMgdl: Int?,
    iobMilliunits: Long,
): InitiateBolusRequest {
    require(totalUnits.isFinite())
    require(foodUnits.isFinite())
    require(correctionVolumeMilliunits >= 0)
    require(carbsGrams >= 0)
    require(bgMgdl == null || bgMgdl >= 0)
    require(iobMilliunits >= 0)

    return InitiateBolusRequest(
        InsulinUnit.from1To1000(totalUnits),
        bolusId,
        plannedBolusTypeBitmask(carbsGrams, correctionVolumeMilliunits),
        InsulinUnit.from1To1000(foodUnits),
        correctionVolumeMilliunits,
        carbsGrams,
        bgMgdl ?: 0,
        iobMilliunits,
    )
}
