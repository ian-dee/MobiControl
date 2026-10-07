package com.jwoglom.controlx2.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Stable POR chrome from the approved study. Clinical graph/status colors stay in Color.kt. */
internal fun porColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF15243C),
        primaryContainer = Color(0xFF293D59), onPrimaryContainer = Color(0xFFDCE8FC),
        secondary = Color(0xFFC9BAE2), onSecondary = Color(0xFF241E32),
        secondaryContainer = Color(0xFF343149), onSecondaryContainer = Color(0xFFE3DAF2),
        tertiary = Color(0xFFA6D2BC), onTertiary = Color(0xFF14291F),
        tertiaryContainer = Color(0xFF253E36), onTertiaryContainer = Color(0xFFD4EEE0),
        background = Color(0xFF111A27), onBackground = Color(0xFFE6EDF8),
        surface = Color(0xFF233246), onSurface = Color(0xFFE6EDF8),
        surfaceVariant = Color(0xFF19283B), onSurfaceVariant = Color(0xFFBAC8DB),
        surfaceContainer = Color(0xFF19283B), surfaceContainerLow = Color(0xFF19283B),
        surfaceContainerHigh = Color(0xFF29394D),
        outline = Color(0xFF7D91AD), outlineVariant = Color(0xFF3A4C63),
        error = Color(0xFFFFB5BE), onError = Color(0xFF4A1420),
        errorContainer = Color(0xFF492D37), onErrorContainer = Color(0xFFFFB5BE),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF235BC5), onPrimary = Color.White,
        primaryContainer = Color(0xFFE9F0FF), onPrimaryContainer = Color(0xFF293E5B),
        secondary = Color(0xFF7756AE), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0EBF8), onSecondaryContainer = Color(0xFF564475),
        tertiary = Color(0xFF23744D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEBF6EF), onTertiaryContainer = Color(0xFF23744D),
        background = Color(0xFFF2F5F9), onBackground = Color(0xFF15243C),
        surface = Color.White, onSurface = Color(0xFF15243C),
        surfaceVariant = Color(0xFFE5ECF5), onSurfaceVariant = Color(0xFF5C6B80),
        surfaceContainer = Color(0xFFF8FAFD), surfaceContainerLow = Color(0xFFF2F5F9),
        surfaceContainerHigh = Color(0xFFEDF1F6),
        outline = Color(0xFF71829B), outlineVariant = Color(0xFFDCE3EC),
        error = Color(0xFFB52D39), onError = Color.White,
        errorContainer = Color(0xFFFFF0F0), onErrorContainer = Color(0xFFB52D39),
    )
}

/** Weight only: existing Dashboard type sizes and layout metrics are retained. */
@Composable
internal fun porFontWeight(original: FontWeight): FontWeight =
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        when (original) {
            FontWeight.Bold -> FontWeight.SemiBold
            FontWeight.SemiBold -> FontWeight.Medium
            else -> original
        }
    } else original
