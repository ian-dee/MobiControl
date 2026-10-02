package com.jwoglom.controlx2.presentation.theme

import android.app.Activity
import android.os.Build
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSkin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override */
    //background = Color(0xFFFFFBFE),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun ControlX2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    appSkin: AppSkin = AppSkin.CURRENT,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        appSkin == AppSkin.INSTRUMENT -> InstrumentColorScheme
        appSkin == AppSkin.FIELD_KIT -> FieldKitSkin.colors
        appSkin == AppSkin.CURRENT -> porColorScheme(darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val typography = when (appSkin) {
        AppSkin.INSTRUMENT -> InstrumentTypography
        AppSkin.FIELD_KIT -> FieldKitSkin.typography
        AppSkin.CURRENT -> Typography
    }
    val shapes = when (appSkin) {
        AppSkin.INSTRUMENT -> InstrumentShapes
        AppSkin.FIELD_KIT -> FieldKitSkin.shapes
        AppSkin.CURRENT -> Shapes()
    }
    val skinTokens = when (appSkin) {
        AppSkin.INSTRUMENT -> instrumentSkinTokens()
        AppSkin.FIELD_KIT -> FieldKitSkin.tokens()
        AppSkin.CURRENT -> currentSkinTokens(darkTheme = darkTheme, colorScheme = colorScheme)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            if (context is Activity) {
                context.window.statusBarColor = if (appSkin == AppSkin.INSTRUMENT) {
                    colorScheme.inverseSurface.toArgb()
                } else if (appSkin == AppSkin.FIELD_KIT) {
                    skinTokens.chassis.toArgb()
                } else {
                    colorScheme.primary.toArgb()
                }
                ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
                    if (appSkin == AppSkin.INSTRUMENT) false
                    else if (appSkin == AppSkin.FIELD_KIT) true else darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSkinTokens provides skinTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

internal fun currentSkinTokens(
    darkTheme: Boolean,
    colorScheme: androidx.compose.material3.ColorScheme = LightColorScheme,
) = SkinTokens(
    skin = AppSkin.CURRENT,
    canvas = colorScheme.background,
    chassis = colorScheme.surface,
    panel = colorScheme.surface,
    raisedPanel = colorScheme.surface,
    readout = colorScheme.surface,
    readoutText = colorScheme.onSurface,
    readoutMuted = colorScheme.onSurfaceVariant,
    keyline = colorScheme.outline,
    mutedKeyline = colorScheme.outlineVariant,
    accent = colorScheme.primary,
    accentSecondary = colorScheme.secondary,
    indicatorYellow = colorScheme.tertiary,
    navigation = colorScheme.surface,
    panelShape = RoundedCornerShape(Spacing.CardCornerRadius),
    controlShape = Shapes().small,
    keylineWidth = 0.dp,
    panelElevation = Elevation.Card,
)
