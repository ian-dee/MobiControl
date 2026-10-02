package com.jwoglom.controlx2.presentation.theme.fieldkit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private val fieldKitGlyphs: Map<String, ImageVector> by lazy {
    mapOf(
        "Dashboard" to "M3 3H21V21H3Z M3 8H21 M8 8V21 M12 17V14 M16 17V11",
        "Info" to "M3 3H21V21H3Z M12 10V18 M12 6V7",
        "Settings" to "M3 6H21 M3 12H21 M3 18H21 M7 3V9 M16 9V15 M10 15V21",
        "Tune" to "M3 6H21 M3 12H21 M3 18H21 M7 3V9 M16 9V15 M10 15V21",
        "Create" to "M4 18L4 21L7 21L20 8L16 4Z M13 7L17 11 M3 3H9",
        "Notifications" to "M5 17H19L17 14V9C17 2 7 2 7 9V14Z M10 20H14 M12 2V4",
        "NotificationsActive" to "M5 17H19L17 14V9C17 2 7 2 7 9V14Z M10 20H14 M3 5L2 9 M21 5L22 9",
        "CameraAlt" to "M3 7H7L9 4H15L17 7H21V20H3Z M8 13A4 4 0 1 0 16 13A4 4 0 1 0 8 13 M17 10H19",
        "Palette" to "M3 4H21V20H3Z M8 4V20 M16 4V20 M3 12H21",
        "CloudSync" to "M7 17H5C0 17 1 10 6 10C6 3 17 3 17 9C23 9 24 16 19 17 M9 13L12 10L15 13 M12 10V21",
        "History" to "M4 6V11H9 M4 10C4 0 21 1 21 12C21 23 5 24 4 16 M12 6V12L16 15",
        "Timer" to "M9 2H15 M12 2V5 M6 5L4 3 M19 5L21 3 M4 13A8 8 0 1 0 20 13A8 8 0 1 0 4 13 M12 8V13L16 15",
        "DataThresholding" to "M3 3V21H21 M6 17L10 12L13 15L20 6 M4 8H8 M17 18H21",
        "DevicesOther" to "M3 4H16V18H3Z M8 21H13 M11 18V21 M19 9H22V21H18V9Z",
        "Devices" to "M3 4H16V18H3Z M8 21H13 M11 18V21 M19 9H22V21H18V9Z",
        "Bedtime" to "M18 17A9 9 0 0 1 8 4A9 9 0 1 0 18 17Z M15 3V7 M13 5H17",
        "DirectionsRun" to "M14 2A1.5 1.5 0 1 0 17 2A1.5 1.5 0 1 0 14 2 M10 7L14 6L17 10H21 M14 6L11 13L7 13L3 17 M11 13L16 16L14 22 M10 7L6 10L4 8",
        "Lock" to "M5 10H19V21H5Z M8 10V6C8 1 16 1 16 6V10 M12 14V17",
        "VolumeUp" to "M3 9H7L12 5V19L7 15H3Z M16 8C19 10 19 14 16 16 M19 5C24 9 24 15 19 19",
        "Sync" to "M4 9C7 1 18 2 21 9 M21 4V9H16 M20 15C17 23 6 22 3 15 M3 20V15H8",
        "Check" to "M4 12L9 17L20 6",
        "HealthAndSafety" to "M12 2L21 6V13C20 17 16 20 12 22C8 20 4 17 3 13V6Z M12 7V15 M8 11H16",
        "KeyboardArrowRight" to "M8 5L15 12L8 19",
        "PowerSettingsNew" to "M12 2V12 M7 5C-1 11 3 22 12 22C21 22 25 11 17 5",
        "Speed" to "M3 19C-3 1 27 1 21 19 M12 17L17 8 M7 11L5 9 M12 9V6 M17 11L19 9 M6 19H18",
        "Vaccines" to "M7 3L21 17 M6 8L10 4 M9 11L13 7 M5 16L13 8L18 13L10 21Z M3 23L6 20 M15 4L20 9",
        "Pump" to "M5 3H19V21H5Z M8 6H16V11H8Z M8 15H12 M8 18H14 M17 4V8",
    ).mapValues { (name, data) ->
        ImageVector.Builder("FieldKit.$name", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(PathParser().parsePathString(data).toNodes(), fill = null,
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Round)
        }.build()
    }
}

/** Unknown glyphs fall back to the host icon; this module never owns semantics or hit targets. */
internal fun fieldKitGlyph(name: String): ImageVector? = fieldKitGlyphs[name]
