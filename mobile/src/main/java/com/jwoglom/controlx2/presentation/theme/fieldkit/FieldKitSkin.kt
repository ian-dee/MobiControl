package com.jwoglom.controlx2.presentation.theme.fieldkit

import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.SkinTokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.jwoglom.controlx2.R
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Packaged open fonts keep the type system identical offline and on every Android device.
internal val FieldKitSans = FontFamily(
    Font(R.font.field_kit_barlow_regular, FontWeight.Normal),
    Font(R.font.field_kit_barlow_medium, FontWeight.Medium),
    Font(R.font.field_kit_barlow_semibold, FontWeight.SemiBold),
)
internal val FieldKitDisplay = FontFamily(
    Font(R.font.field_kit_rajdhani_medium, FontWeight.Medium),
    Font(R.font.field_kit_rajdhani_semibold, FontWeight.SemiBold),
)
internal val FieldKitMono = FieldKitSans
internal val FieldKitFrame = Color(0xFF303735)
internal val FieldKitFrameInk = Color(0xFFEEEDE3)
internal val FieldKitFrameMuted = Color(0xFFBFC7BA)

internal val FieldKitColorScheme = lightColorScheme(
    primary = Color(0xFFA54B34),
    onPrimary = Color(0xFFFFF5E6),
    primaryContainer = Color(0xFFE5D1BA),
    onPrimaryContainer = Color(0xFF3C100B),
    secondary = Color(0xFF3C5661),
    onSecondary = Color(0xFFF3F7F7),
    secondaryContainer = Color(0xFFC9D4D3),
    onSecondaryContainer = Color(0xFF12262E),
    tertiary = Color(0xFFB9A260),
    onTertiary = Color(0xFF1C1908),
    tertiaryContainer = Color(0xFFE8DCA4),
    onTertiaryContainer = Color(0xFF292307),
    error = Color(0xFFA52323),
    onError = Color.White,
    errorContainer = Color(0xFFE7C8B8),
    onErrorContainer = Color(0xFF3C0909),
    background = Color(0xFFBDC2B7),
    onBackground = Color(0xFF28322C),
    surface = Color(0xFFECEBE1),
    onSurface = Color(0xFF28322C),
    surfaceVariant = Color(0xFFCFD3C7),
    onSurfaceVariant = Color(0xFF5B6358),
    outline = Color(0xFF27352C),
    outlineVariant = Color(0xFF858D80),
    inverseSurface = Color(0xFF27352C),
    inverseOnSurface = Color(0xFFEEEDE3),
    inversePrimary = Color(0xFFFFB4A3),
    scrim = Color.Black,
)

internal val FieldKitShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

internal val FieldKitTypography: Typography by lazy { Typography(
    displayLarge = fieldKitReadout(57.sp, 62.sp, FontWeight.Medium),
    displayMedium = fieldKitReadout(45.sp, 50.sp, FontWeight.Medium),
    displaySmall = fieldKitReadout(36.sp, 41.sp, FontWeight.Medium),
    headlineLarge = fieldKitBody(29.sp, 34.sp, FontWeight.SemiBold),
    headlineMedium = fieldKitBody(26.sp, 31.sp, FontWeight.SemiBold),
    headlineSmall = fieldKitBody(22.sp, 27.sp, FontWeight.SemiBold),
    titleLarge = fieldKitBody(22.sp, 27.sp, FontWeight.Bold),
    titleMedium = fieldKitBody(16.sp, 21.sp, FontWeight.Bold),
    titleSmall = fieldKitBody(14.sp, 19.sp, FontWeight.SemiBold),
    bodyLarge = fieldKitBody(16.sp, 23.sp, FontWeight.Normal),
    bodyMedium = fieldKitBody(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = fieldKitBody(12.sp, 17.sp, FontWeight.Normal),
    labelLarge = fieldKitLabel(14.sp, 19.sp, FontWeight.Bold),
    labelMedium = fieldKitLabel(12.sp, 16.sp, FontWeight.SemiBold),
    labelSmall = fieldKitLabel(10.sp, 14.sp, FontWeight.SemiBold),
) }

private fun fieldKitReadout(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FieldKitDisplay,
    fontFeatureSettings = "tnum",
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
)

private fun fieldKitBody(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FieldKitSans,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = 0.05.sp,
)

private fun fieldKitLabel(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
) = TextStyle(
    fontFamily = FieldKitMono,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = 0.7.sp,
)

internal fun fieldKitSkinTokens() = SkinTokens(
    skin = AppSkin.FIELD_KIT,
    canvas = Color(0xFFBDC2B7),
    chassis = Color(0xFFC9CEC0),
    panel = Color(0xFFECEBE1),
    raisedPanel = Color(0xFFF2F0E6),
    readout = Color(0xFFD3DEC5),
    readoutText = Color(0xFF223B28),
    readoutMuted = Color(0xFF56684D),
    keyline = Color(0xFF27352C),
    mutedKeyline = Color(0xFF858D80),
    accent = Color(0xFFA54B34),
    accentSecondary = Color(0xFF3C5661),
    indicatorYellow = Color(0xFFB9A260),
    navigation = Color(0xFFCFD3C7),
    panelShape = RoundedCornerShape(14.dp),
    controlShape = RoundedCornerShape(6.dp),
    keylineWidth = 1.dp,
    panelElevation = 0.dp,
)

/** Registration boundary. The host owns selection, persistence, navigation and app identity. */
internal object FieldKitSkin {
    const val id = "field-kit"
    const val displayName = "Field Kit"
    const val description = "Weathered enamel, graphite framing and recessed instrument readouts."
    val colors get() = FieldKitColorScheme
    val typography get() = FieldKitTypography
    val shapes get() = FieldKitShapes
    fun tokens() = fieldKitSkinTokens()
}
