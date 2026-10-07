package com.jwoglom.controlx2.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bright orange is for filled controls; the darker primary keeps small text/icons readable.
internal val InstrumentOrange = Color(0xFFFF642F)
internal val InstrumentGroupBackground = Color(0xFF363B32)
internal val InstrumentGroupContent = Color(0xFFF6F7ED)
internal val InstrumentGroupMuted = Color(0xFFCBD0C4)

internal val InstrumentColorScheme = lightColorScheme(
    primary = Color(0xFFA9401C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE5D8),
    onPrimaryContainer = Color(0xFF793216),
    secondary = Color(0xFF2B59C3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E9F6),
    onSecondaryContainer = Color(0xFF263A5D),
    tertiary = Color(0xFF586448),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1E9D8),
    onTertiaryContainer = Color(0xFF33472B),
    error = Color(0xFFB52D39),
    onError = Color.White,
    errorContainer = Color(0xFFFFF0F0),
    onErrorContainer = Color(0xFFB52D39),
    background = Color(0xFFE7E5DD),
    onBackground = Color(0xFF242721),
    surface = Color(0xFFFAF9F3),
    onSurface = Color(0xFF242721),
    surfaceVariant = Color(0xFFDEDFD4),
    onSurfaceVariant = Color(0xFF60645A),
    surfaceContainer = Color(0xFFDEDFD4),
    surfaceContainerLow = Color(0xFFF0EFE7),
    surfaceContainerHigh = Color(0xFFD5D8CB),
    outline = Color(0xFF747B6B),
    outlineVariant = Color(0xFFC8C9BF),
    inverseSurface = Color(0xFF252B24),
    inverseOnSurface = Color(0xFFF4F4EC),
    inversePrimary = InstrumentOrange,
    scrim = Color.Black,
)

internal val InstrumentShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

internal val InstrumentTypography = Typography(
    displayLarge = instrumentReadout(57.sp, 64.sp, FontWeight.Medium),
    displayMedium = instrumentReadout(45.sp, 52.sp, FontWeight.Medium),
    displaySmall = instrumentReadout(36.sp, 44.sp, FontWeight.Medium),
    headlineLarge = instrumentReadout(32.sp, 38.sp, FontWeight.Medium),
    headlineMedium = instrumentReadout(28.sp, 34.sp, FontWeight.Medium),
    headlineSmall = instrumentReadout(24.sp, 30.sp, FontWeight.Medium),
    titleLarge = instrumentBody(22.sp, 28.sp, FontWeight.SemiBold),
    titleMedium = instrumentBody(16.sp, 22.sp, FontWeight.SemiBold),
    titleSmall = instrumentBody(14.sp, 20.sp, FontWeight.SemiBold),
    bodyLarge = instrumentBody(16.sp, 23.sp, FontWeight.Normal),
    bodyMedium = instrumentBody(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = instrumentBody(12.sp, 17.sp, FontWeight.Normal),
    labelLarge = instrumentLabel(14.sp, 20.sp, FontWeight.Medium),
    labelMedium = instrumentLabel(12.sp, 16.sp, FontWeight.Medium),
    labelSmall = instrumentLabel(10.sp, 14.sp, FontWeight.Medium),
)

private fun instrumentReadout(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = (-0.4).sp,
)

private fun instrumentBody(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
)

private fun instrumentLabel(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = 0.3.sp,
)

internal fun instrumentSkinTokens() = SkinTokens(
    skin = AppSkin.INSTRUMENT,
    canvas = InstrumentColorScheme.background,
    chassis = Color(0xFFDEDFD4),
    panel = InstrumentColorScheme.surface,
    raisedPanel = InstrumentColorScheme.surface,
    readout = Color(0xFFE1E7D6),
    readoutText = InstrumentColorScheme.onSurface,
    readoutMuted = InstrumentColorScheme.onSurfaceVariant,
    keyline = InstrumentColorScheme.onSurface,
    mutedKeyline = Color(0xFFB5BBAC),
    accent = InstrumentOrange,
    accentSecondary = Color(0xFF2B59C3),
    indicatorYellow = Color(0xFFEEE6BB),
    navigation = Color(0xFFD5D8CB),
    panelShape = RoundedCornerShape(6.dp),
    controlShape = RoundedCornerShape(4.dp),
    keylineWidth = 1.dp,
    panelElevation = 0.dp,
)
