package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import java.util.Locale

internal const val FIXED_LOW_ALERT_MGDL = 55

internal enum class CgmAlertThresholdLineKind {
    FIXED_LOW,
    LOW_ALERT,
    HIGH_ALERT,
    LOWER_LIMIT,
    UPPER_LIMIT,
}

internal fun CgmAlertThresholdLineKind.isControlLimit(): Boolean =
    this == CgmAlertThresholdLineKind.LOWER_LIMIT ||
        this == CgmAlertThresholdLineKind.UPPER_LIMIT

internal fun CgmAlertThresholdLineKind.isLabeledOnChart(): Boolean =
    this == CgmAlertThresholdLineKind.LOW_ALERT ||
        this == CgmAlertThresholdLineKind.HIGH_ALERT

internal data class CgmAlertThresholdLine(
    val kind: CgmAlertThresholdLineKind,
    val glucoseMgdl: Int,
)

internal fun buildCgmAlertThresholdLines(
    highEnabled: Boolean,
    highThresholdMgdl: Int,
    lowEnabled: Boolean,
    lowThresholdMgdl: Int,
): List<CgmAlertThresholdLine> = buildList {
    add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.FIXED_LOW, FIXED_LOW_ALERT_MGDL))
    if (lowEnabled) {
        add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.LOW_ALERT, lowThresholdMgdl))
    }
    if (highEnabled) {
        add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.HIGH_ALERT, highThresholdMgdl))
    }
}

internal fun buildGraphDisplayThresholdLines(
    showUpperLimitLine: Boolean,
    upperThresholdMgdl: Int,
    showLowerLimitLine: Boolean,
    lowerThresholdMgdl: Int,
): List<CgmAlertThresholdLine> = buildList {
    // Preserve the pump's non-configurable Fixed Low Alert reference line.
    add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.FIXED_LOW, FIXED_LOW_ALERT_MGDL))
    if (showLowerLimitLine) {
        add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.LOWER_LIMIT, lowerThresholdMgdl))
    }
    if (showUpperLimitLine) {
        add(CgmAlertThresholdLine(CgmAlertThresholdLineKind.UPPER_LIMIT, upperThresholdMgdl))
    }
}

internal fun combineCgmAndGraphDisplayThresholdLines(
    cgmAlertLines: List<CgmAlertThresholdLine>,
    graphDisplayLines: List<CgmAlertThresholdLine>,
): List<CgmAlertThresholdLine> = buildList {
    addAll(cgmAlertLines)
    addAll(graphDisplayLines.filter { graphLine ->
        none { existingLine -> existingLine.kind == graphLine.kind }
    })
}

internal fun cgmAlertThresholdYFraction(
    glucoseMgdl: Int,
    minimumGlucoseMgdl: Float,
    maximumGlucoseMgdl: Float,
): Float {
    return glucoseValueYFraction(
        glucoseMgdl = glucoseMgdl.toFloat(),
        minimumGlucoseMgdl = minimumGlucoseMgdl,
        maximumGlucoseMgdl = maximumGlucoseMgdl,
    )
}

internal fun CgmAlertThresholdLine.displayLabel(glucoseUnit: GlucoseUnit): String {
    val prefix = when (kind) {
        CgmAlertThresholdLineKind.FIXED_LOW -> "Fixed Low Alert"
        CgmAlertThresholdLineKind.LOW_ALERT -> "CGM Low Alert"
        CgmAlertThresholdLineKind.HIGH_ALERT -> "CGM High Alert"
        CgmAlertThresholdLineKind.LOWER_LIMIT -> "Lower Control Limit"
        CgmAlertThresholdLineKind.UPPER_LIMIT -> "Upper Control Limit"
    }
    val value = when (glucoseUnit) {
        GlucoseUnit.MGDL -> "$glucoseMgdl"
        GlucoseUnit.MMOL -> String.format(
            Locale.getDefault(),
            "%.1f",
            glucoseMgdl * GlucoseConverter.MGDL_TO_MMOL_FACTOR,
        )
    }
    return "$prefix $value"
}
