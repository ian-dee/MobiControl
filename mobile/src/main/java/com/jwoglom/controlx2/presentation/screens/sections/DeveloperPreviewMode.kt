package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.UserMode

/**
 * Applies the same mutual-exclusion rule as the pump: Sleep and Exercise cannot be active at
 * the same time. A null result means the requested switch transition is not valid.
 */
internal fun developerPreviewModeAfterToggle(
    currentMode: UserMode?,
    targetMode: UserMode,
    checked: Boolean,
): UserMode? = when {
    checked && currentMode == UserMode.NONE -> targetMode
    !checked && currentMode == targetMode -> UserMode.NONE
    else -> null
}
