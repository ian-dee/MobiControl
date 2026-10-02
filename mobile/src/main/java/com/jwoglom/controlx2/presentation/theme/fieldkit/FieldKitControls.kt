package com.jwoglom.controlx2.presentation.theme.fieldkit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Fits within the existing native navigation indicator footprint; layout and click stay native. */
internal fun Modifier.fieldKitNavigationPlate(selected: Boolean): Modifier = drawWithCache {
    val width = 58.dp.toPx()
    val height = 30.dp.toPx()
    val origin = Offset((size.width - width) / 2f, 4.dp.toPx())
    val face = Brush.verticalGradient(listOf(Color(0xFF4E574C), Color(0xFF303C32)),
        startY = origin.y, endY = origin.y + height)
    onDrawWithContent {
        if (selected) {
            drawRoundRect(face, origin, Size(width, height), CornerRadius(5.dp.toPx()))
            drawRoundRect(Color(0xFF26382C), origin, Size(width, height), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.24f), origin + Offset(5.dp.toPx(), 1.dp.toPx()),
                origin + Offset(width - 5.dp.toPx(), 1.dp.toPx()), 0.7.dp.toPx())
            drawLine(Color(0xFFC8B57B), origin + Offset(width - 8.dp.toPx(), 11.dp.toPx()),
                origin + Offset(width - 8.dp.toPx(), 19.dp.toPx()), 1.4.dp.toPx())
        }
        drawContent()
    }
}
