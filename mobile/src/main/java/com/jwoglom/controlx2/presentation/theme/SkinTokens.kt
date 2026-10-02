package com.jwoglom.controlx2.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SkinTokens(
    val skin: AppSkin,
    val canvas: Color,
    val chassis: Color,
    val panel: Color,
    val raisedPanel: Color,
    val readout: Color,
    val readoutText: Color,
    val readoutMuted: Color,
    val keyline: Color,
    val mutedKeyline: Color,
    val accent: Color,
    val accentSecondary: Color,
    val indicatorYellow: Color,
    val navigation: Color,
    val panelShape: Shape,
    val controlShape: Shape,
    val keylineWidth: Dp,
    val panelElevation: Dp,
) {
    val usesTechnicalChrome: Boolean
        get() = skin != AppSkin.CURRENT

    /** Shared presentation hierarchy only; never a workflow or data gate. */
    val usesRefinedChrome: Boolean
        get() = skin == AppSkin.CURRENT || skin == AppSkin.INSTRUMENT || skin == AppSkin.FIELD_KIT

    val isFieldKit: Boolean
        get() = skin == AppSkin.FIELD_KIT
}

internal val LocalSkinTokens = staticCompositionLocalOf {
    currentSkinTokens(darkTheme = false)
}

object MobiControlTheme {
    val tokens: SkinTokens
        @androidx.compose.runtime.Composable
        get() = LocalSkinTokens.current
}
