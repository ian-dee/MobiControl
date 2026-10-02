package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalcCondition
import com.jwoglom.pumpx2.pump.messages.calculator.BolusCalculatorBuilder
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.BolusCalcDataSnapshotResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LastBGResponse

internal enum class BolusBgTargetRelation {
    ABOVE,
    BELOW,
    AT_TARGET,
    UNAVAILABLE,
}

internal data class BolusCorrectionPreflight(
    val bgMgdl: Int?,
    val targetMgdl: Int?,
    val iobUnits: Double?,
    val relation: BolusBgTargetRelation,
) {
    /** Tandem automatically applies reverse correction below 70 mg/dL. */
    val correctionRequired: Boolean
        get() = relation == BolusBgTargetRelation.BELOW && (bgMgdl ?: Int.MAX_VALUE) < 70

    val requiresChoice: Boolean
        get() = relation == BolusBgTargetRelation.ABOVE ||
            (relation == BolusBgTargetRelation.BELOW && !correctionRequired)
}

/** Uses the same automatic/manual BG selection that PumpX2 will use for the bolus calculation. */
internal fun bolusCorrectionPreflight(
    snapshot: BolusCalcDataSnapshotResponse?,
    lastBg: LastBGResponse?,
): BolusCorrectionPreflight {
    if (snapshot == null) {
        return BolusCorrectionPreflight(null, null, null, BolusBgTargetRelation.UNAVAILABLE)
    }
    val bg = BolusCalculatorBuilder(snapshot, lastBg).glucoseMgdl.orElse(null)
    val target = snapshot.targetBg.takeIf { it in 40..400 }
    val relation = when {
        bg == null || bg <= 0 || target == null -> BolusBgTargetRelation.UNAVAILABLE
        bg > target -> BolusBgTargetRelation.ABOVE
        bg < target -> BolusBgTargetRelation.BELOW
        else -> BolusBgTargetRelation.AT_TARGET
    }
    return BolusCorrectionPreflight(
        bgMgdl = bg,
        targetMgdl = target,
        iobUnits = InsulinUnit.from1000To1(snapshot.iob),
        relation = relation,
    )
}

/**
 * PumpX2 applies BG and IOB together behind these two calculator decisions. Keeping both in sync
 * makes the explicit Correction switch authoritative for high and low corrections.
 */
internal fun correctionExclusions(
    current: Set<BolusCalcCondition>?,
    correctionEnabled: Boolean,
): MutableSet<BolusCalcCondition> = (current ?: emptySet()).toMutableSet().apply {
    remove(BolusCalcCondition.POSITIVE_BG_CORRECTION)
    remove(BolusCalcCondition.NEGATIVE_BG_CORRECTION)
    if (!correctionEnabled) {
        add(BolusCalcCondition.POSITIVE_BG_CORRECTION)
        add(BolusCalcCondition.NEGATIVE_BG_CORRECTION)
    }
}

internal fun isCorrectionDecision(condition: BolusCalcCondition): Boolean =
    condition == BolusCalcCondition.POSITIVE_BG_CORRECTION ||
        condition == BolusCalcCondition.NEGATIVE_BG_CORRECTION
