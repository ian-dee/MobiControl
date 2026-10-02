package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.SetQuickBolusSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse

internal enum class QuickBolusIncrementType(val displayName: String) {
    UNITS("Units of insulin"),
    CARBS("Grams of carbohydrate"),
}

internal data class QuickBolusSettingsDraft(
    val enabled: Boolean,
    val incrementType: QuickBolusIncrementType,
    val increment: SetQuickBolusSettingsRequest.QuickBolusIncrement,
) {
    init {
        require(increment.enabled) { "A settings draft retains an enabled increment" }
        require(increment.quickBolusIncrementType() == incrementType) {
            "Increment must match its selected type"
        }
    }

    fun protocolIncrement(): SetQuickBolusSettingsRequest.QuickBolusIncrement =
        if (enabled) increment else SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED

    fun withType(type: QuickBolusIncrementType): QuickBolusSettingsDraft = copy(
        incrementType = type,
        increment = quickBolusIncrementOptions(type).first(),
    )
}

internal fun quickBolusSettingsDraft(
    increment: SetQuickBolusSettingsRequest.QuickBolusIncrement?,
): QuickBolusSettingsDraft {
    val retained = increment?.takeIf { it.enabled }
        ?: SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_0_5
    return QuickBolusSettingsDraft(
        enabled = increment?.enabled == true,
        incrementType = requireNotNull(retained.quickBolusIncrementType()),
        increment = retained,
    )
}

internal fun quickBolusIncrementOptions(
    type: QuickBolusIncrementType,
): List<SetQuickBolusSettingsRequest.QuickBolusIncrement> = when (type) {
    QuickBolusIncrementType.UNITS -> listOf(
        SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_0_5,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_1_0,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_2_0,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_5_0,
    )
    QuickBolusIncrementType.CARBS -> listOf(
        SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_2G,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_5G,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_10G,
        SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_15G,
    )
}

internal fun SetQuickBolusSettingsRequest.QuickBolusIncrement.quickBolusIncrementType():
    QuickBolusIncrementType? = when (this) {
    SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED -> null
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_0_5,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_1_0,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_2_0,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_5_0 -> QuickBolusIncrementType.UNITS
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_2G,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_5G,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_10G,
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_15G -> QuickBolusIncrementType.CARBS
}

internal fun SetQuickBolusSettingsRequest.QuickBolusIncrement.displayName(): String = when (this) {
    SetQuickBolusSettingsRequest.QuickBolusIncrement.DISABLED -> "Off"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_0_5 -> "0.5 U"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_1_0 -> "1.0 U"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_2_0 -> "2.0 U"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.UNITS_5_0 -> "5.0 U"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_2G -> "2 g"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_5G -> "5 g"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_10G -> "10 g"
    SetQuickBolusSettingsRequest.QuickBolusIncrement.CARBS_15G -> "15 g"
}

internal fun PumpGlobalsResponse.matchesQuickBolusSettings(
    draft: QuickBolusSettingsDraft,
): Boolean = quickBolusIncrement == draft.protocolIncrement()
