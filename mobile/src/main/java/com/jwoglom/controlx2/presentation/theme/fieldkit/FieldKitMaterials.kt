package com.jwoglom.controlx2.presentation.theme.fieldkit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.random.Random

internal enum class FieldKitMaterial { ENAMEL, GRAPHITE, LCD, CHASSIS, CONTROL, RUBBER, CHART }

/** Cached material drawing only. It does not measure, intercept touches or add semantics. */
internal fun Modifier.fieldKitMaterial(
    material: FieldKitMaterial,
    radius: Float = 0f,
    edge: Boolean = true,
): Modifier = drawWithCache {
    val px = density
    val r = radius * px
    val dark = material == FieldKitMaterial.GRAPHITE || material == FieldKitMaterial.RUBBER
    val glass = material == FieldKitMaterial.LCD || material == FieldKitMaterial.CHART
    val colors = when (material) {
        FieldKitMaterial.ENAMEL -> listOf(Color(0xFFF4F2E9), Color(0xFFE8E7DE), Color(0xFFD8D9D1))
        FieldKitMaterial.GRAPHITE -> listOf(Color(0xFF3E4442), Color(0xFF303735), Color(0xFF292F2E))
        FieldKitMaterial.RUBBER -> listOf(Color(0xFF454B47), Color(0xFF303734), Color(0xFF242C29))
        FieldKitMaterial.CHASSIS -> listOf(Color(0xFFD2D3CA), Color(0xFFC1C4BA), Color(0xFFB1B7AC))
        FieldKitMaterial.CONTROL -> listOf(Color(0xFFC56A50), Color(0xFFAD4D35), Color(0xFF893A29))
        FieldKitMaterial.LCD, FieldKitMaterial.CHART -> listOf(Color(0xFF535B53), Color(0xFF303B34), Color(0xFF242F29))
    }
    val paint = Brush.linearGradient(colors, Offset.Zero, Offset(size.width * 0.2f, size.height))
    val outer = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r))) }
    val inset = (if (material == FieldKitMaterial.CHART) 6f else 5f) * px
    val innerSize = Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
    val innerRadius = CornerRadius((r - inset).coerceAtLeast(0f))
    val inner = Path().apply {
        addRoundRect(RoundRect(Rect(Offset(inset, inset), innerSize), innerRadius))
    }
    val glassPaint = Brush.linearGradient(
        if (material == FieldKitMaterial.CHART) listOf(Color(0xFFE8EBDF), Color(0xFFF2F2E8), Color(0xFFE0E5D8))
        else listOf(Color(0xFFBFCDB5), Color(0xFFD9E1CD), Color(0xFFCDD8C0)),
        Offset.Zero, Offset(size.width, size.height * 1.6f),
    )
    val contactShadow = Brush.verticalGradient(
        listOf(Color(0xFF17271E).copy(alpha = 0.28f), Color.Transparent),
        startY = inset, endY = inset + 10f * px,
    )
    val reflection = Path().apply {
        moveTo(size.width * 0.47f, 0f); lineTo(size.width * 0.78f, 0f)
        lineTo(size.width * 0.34f, size.height); lineTo(size.width * 0.03f, size.height); close()
    }
    val random = Random(8407 + material.ordinal * 97 + size.width.toInt() * 3 + size.height.toInt())
    val grain = List((size.width * size.height / (38f * px * px)).toInt().coerceIn(16, 2200)) {
        Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
    }
    // Wear follows exposed straight edges; rounded corners and readable glass stay clean.
    val edgeWear = List((size.width / (42f * px)).toInt().coerceIn(2, 10)) {
        val x = r + 5f * px + random.nextFloat() * (size.width - 2f * r - 14f * px).coerceAtLeast(0f)
        val y = if (it % 2 == 0) 1.4f * px else size.height - 1.4f * px
        Pair(Offset(x, y), (0.8f + random.nextFloat() * 3f) * px)
    }
    onDrawWithContent {
        clipPath(outer) {
            drawRoundRect(paint, cornerRadius = CornerRadius(r))
            if (glass) {
                drawRoundRect(glassPaint, topLeft = Offset(inset, inset), size = innerSize, cornerRadius = innerRadius)
                clipPath(inner) {
                    drawRect(contactShadow)
                    drawPath(reflection, Color.White.copy(alpha = if (material == FieldKitMaterial.LCD) 0.065f else 0.025f))
                    if (material == FieldKitMaterial.LCD) {
                        var y = inset + 2f * px
                        while (y < size.height - inset) {
                            drawLine(Color(0xFF1B321E).copy(alpha = 0.018f), Offset(inset, y), Offset(size.width - inset, y), 0.35f * px)
                            y += 3f * px
                        }
                    }
                }
                drawRoundRect(Color(0xFF111F17).copy(alpha = 0.58f), topLeft = Offset(inset, inset),
                    size = innerSize, cornerRadius = innerRadius, style = Stroke(px))
                drawRoundRect(Color(0xFFE5E8DB).copy(alpha = 0.46f), topLeft = Offset(1.4f * px, 1.4f * px),
                    size = Size(size.width - 2.8f * px, size.height - 2.8f * px),
                    cornerRadius = CornerRadius((r - 1.4f * px).coerceAtLeast(0f)), style = Stroke(0.8f * px))
            } else {
                grain.forEachIndexed { i, point ->
                    drawCircle(if (i % 3 == 0) Color.White.copy(alpha = if (dark) 0.10f else 0.19f)
                        else Color.Black.copy(alpha = if (dark) 0.12f else 0.055f), 0.26f * px, point)
                }
                if (material == FieldKitMaterial.RUBBER) {
                    // Fine grip ribs belong to the control's face, not to the surrounding paint.
                    var x = 7f * px
                    while (x < size.width - 7f * px) {
                        drawLine(Color.Black.copy(alpha = 0.09f), Offset(x, 5f * px), Offset(x, size.height - 5f * px), 0.4f * px)
                        x += 2.5f * px
                    }
                }
            }
            if (edge && size.width > 4f * px && size.height > 4f * px) {
                drawRoundRect(Color(0xFF17251E).copy(alpha = if (dark || glass) 0.85f else 0.55f),
                    topLeft = Offset(0.5f * px, 0.5f * px), size = Size(size.width - px, size.height - px),
                    cornerRadius = CornerRadius((r - 0.5f * px).coerceAtLeast(0f)), style = Stroke(px))
                if (!glass) {
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.24f else 0.75f),
                        Color.Black.copy(alpha = 0.18f))),
                        topLeft = Offset(1.5f * px, 1.5f * px), size = Size(size.width - 3f * px, size.height - 3f * px),
                        cornerRadius = CornerRadius((r - 1.5f * px).coerceAtLeast(0f)), style = Stroke(0.8f * px))
                }
                if (material != FieldKitMaterial.RUBBER) edgeWear.forEachIndexed { i, (start, length) ->
                    drawLine(Color(0xFF677063).copy(alpha = if (dark || glass) 0.4f else 0.32f),
                        start, start + Offset(length, 0f), 0.65f * px)
                    if (i % 2 == 0) drawLine(Color(0xFFF3F1E6).copy(alpha = 0.4f),
                        start + Offset(0f, px), start + Offset(length * 0.7f, px), 0.35f * px)
                }
            }
        }
        drawContent()
    }
}
