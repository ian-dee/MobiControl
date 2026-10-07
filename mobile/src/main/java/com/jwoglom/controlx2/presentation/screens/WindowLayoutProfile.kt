package com.jwoglom.controlx2.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

internal enum class WindowLayoutProfile {
    STANDARD,
    COVER_COMPACT,
}

/**
 * Selects the compact presentation from the current app window rather than a device model.
 *
 * The Flip 8 cover display can report approximately 399 x 441 dp without Samsung's compatibility
 * scaling or 474 x 523 dp when FlipSizeCompatPolicy is active. Its unfolded portrait window is
 * approximately 360 x 840 dp. Requiring both dimensions to remain below 600 dp covers both cover
 * modes without mistaking the tall unfolded window for the cover UI.
 */
internal fun windowLayoutProfile(widthDp: Int, heightDp: Int): WindowLayoutProfile =
    if (widthDp < 600 && heightDp < 600) {
        WindowLayoutProfile.COVER_COMPACT
    } else {
        WindowLayoutProfile.STANDARD
    }

@Composable
internal fun currentWindowLayoutProfile(): WindowLayoutProfile {
    val configuration = LocalConfiguration.current
    return windowLayoutProfile(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
}
