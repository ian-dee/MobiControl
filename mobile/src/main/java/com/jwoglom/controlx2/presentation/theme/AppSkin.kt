package com.jwoglom.controlx2.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/** A presentation choice only. It must never be used to gate pump behavior. */
enum class AppSkin(
    val persistedValue: String,
    val displayName: String,
    val description: String,
) {
    CURRENT(
        persistedValue = "current",
        displayName = "POR",
        description = "The production-of-record MobiControl appearance.",
    ),
    INSTRUMENT(
        persistedValue = "instrument",
        displayName = "Instrument",
        description = "A precise, tactile technical-instrument appearance.",
    ),
    FIELD_KIT(
        persistedValue = com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSkin.id,
        displayName = com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSkin.displayName,
        description = com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSkin.description,
    );

    companion object {
        fun fromPersistedValue(value: String?): AppSkin =
            entries.firstOrNull { it.persistedValue == value } ?: CURRENT
    }
}

@Stable
class AppSkinController internal constructor(
    val selectedSkin: AppSkin,
    private val onSelect: (AppSkin) -> Unit,
) {
    fun select(skin: AppSkin) = onSelect(skin)
}

val LocalAppSkinController = staticCompositionLocalOf {
    AppSkinController(AppSkin.CURRENT) {}
}
