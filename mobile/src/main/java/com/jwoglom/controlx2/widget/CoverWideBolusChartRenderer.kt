package com.jwoglom.controlx2.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal data class WidgetBolusLabelLayout(
    val event: WidgetBolusEvent,
    val text: String,
    val anchorX: Float,
    val bubble: RectF,
)

internal data class LargeCoverBolusLayout(
    val scale: Float,
    val gap: Float,
    val labels: List<WidgetBolusLabelLayout>,
)

/**
 * Cover-wide-only bolus treatment. The shared chart owns all plot geometry and CGM paint; this
 * renderer uses only the existing low-glucose/bolus track below the 50 mg/dL line.
 */
internal object CoverWideBolusChartRenderer {
    fun draw(
        bitmap: Bitmap,
        canvas: Canvas,
        snapshot: MobiControlWidgetSnapshot,
        plot: RectF,
        bounds: WidgetChartBounds,
        chartStart: Long,
        labelSize: Float,
        baselineY: Float,
        cgmRadius: Float,
    ) {
        if (snapshot.boluses.isEmpty()) return

        // Keep bolus paint below 50 even when the upper bound expands to 400. Shortening the
        // leader is preferable to moving or shrinking the glucose plot.
        val y50 = plot.top + plot.height() * widgetChartYFraction(50, bounds)
        val initial = largeCoverBolusLayout(
            snapshot = snapshot,
            plot = plot,
            chartStart = chartStart,
            baseLabelSize = labelSize,
            baselineY = baselineY,
            minimumBubbleTop = ceil(y50) + 2f,
        )
        val protectedPoints = snapshot.cgmPoints.mapNotNull { point ->
            val x = plot.left + plot.width() * widgetChartXFraction(
                point.timestamp,
                chartStart,
                snapshot.chartEndTimestamp,
            )
            val y = plot.top + plot.height() * widgetChartYFraction(point.glucoseMgdl, bounds)
            val auditMargin = 5f
            if (
                y + cgmRadius + auditMargin < initial.labels.first().bubble.top - 2f ||
                y - cgmRadius - auditMargin > initial.labels.first().bubble.bottom + 2f
            ) null else ProtectedCgmPoint(x, y, cgmRadius)
        }
        val layout = initial.copy(
            labels = moveLabelsAwayFromCgmPoints(initial.labels, protectedPoints, plot, initial.gap),
        )
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = labelSize * 0.82f * layout.scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val patches = protectedPoints.mapNotNull { point ->
            // Include the antialiased outline and the audited five-pixel CGM neighborhood.
            val margin = ceil(point.radius + 4f).toInt()
            val left = floor(point.x).toInt().minus(margin).coerceAtLeast(0)
            val top = floor(point.y).toInt().minus(margin).coerceAtLeast(0)
            val right = ceil(point.x).toInt().plus(margin).coerceAtMost(bitmap.width)
            val bottom = ceil(point.y).toInt().plus(margin).coerceAtMost(bitmap.height)
            if (right <= left || bottom <= top) {
                null
            } else {
                // createBitmap can share source pixels, so preserve a deep copy before painting.
                val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val preserved = cropped.copy(Bitmap.Config.ARGB_8888, false)
                if (preserved !== cropped) cropped.recycle()
                PixelPatch(preserved, left, top)
            }
        }

        // Leaders remain behind every pill and begin at the exact event timestamp.
        layout.labels.forEach { label ->
            val outline = outlineColor(label.event)
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outline
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
            }
            val radius = label.bubble.height() / 2f
            val attachmentX = label.anchorX.coerceIn(
                label.bubble.left + radius,
                label.bubble.right - radius,
            )
            canvas.drawPath(
                Path().apply {
                    moveTo(label.anchorX, baselineY)
                    lineTo(label.anchorX, baselineY - 2f)
                    lineTo(attachmentX, label.bubble.bottom)
                },
                linePaint,
            )
            canvas.drawCircle(label.anchorX, baselineY, 1.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outline
            })
        }

        // Chronological order keeps newer colliding events visibly above older ones.
        layout.labels.forEach { label ->
            val radius = label.bubble.height() / 2f
            canvas.drawRoundRect(label.bubble, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor(label.event)
            })
            canvas.drawRoundRect(label.bubble, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outlineColor(label.event)
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
            })
            canvas.drawText(
                label.text,
                label.bubble.centerX(),
                label.bubble.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint,
            )
        }

        // Restore every low CGM neighborhood over callouts. This also covers diagonal leaders and
        // pathological dense clusters without dropping an event or changing plot geometry.
        val restorePaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        patches.forEach { patch ->
            canvas.drawBitmap(patch.bitmap, patch.left.toFloat(), patch.top.toFloat(), restorePaint)
            patch.bitmap.recycle()
        }
    }

    private fun fillColor(event: WidgetBolusEvent): Int =
        if (event.automatic) Color.rgb(126, 87, 194) else Color.rgb(25, 118, 210)

    private fun outlineColor(event: WidgetBolusEvent): Int =
        if (event.automatic) Color.rgb(69, 39, 160) else Color.rgb(13, 71, 161)
}

internal fun largeCoverBolusLayout(
    snapshot: MobiControlWidgetSnapshot,
    plot: RectF,
    chartStart: Long,
    baseLabelSize: Float,
    baselineY: Float,
    minimumBubbleTop: Float,
): LargeCoverBolusLayout {
    val events = snapshot.boluses.takeLast(12).sortedBy(WidgetBolusEvent::timestamp)
    if (events.isEmpty()) return LargeCoverBolusLayout(1.60f, 5f, emptyList())

    fun measurements(scale: Float): Pair<Paint, List<Float>> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = baseLabelSize * 0.82f * scale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        return paint to events.map { event ->
            val text = String.format(Locale.US, "%.2f", event.units)
            max(32f * scale, paint.measureText(text) + 10f * scale)
        }
    }

    val maximumVerticalScale = ((baselineY - minimumBubbleTop) / 14f).coerceAtLeast(1f)
    var scale = min(1.60f, maximumVerticalScale)
    var measured = measurements(scale)
    while (
        scale > 1.001f &&
        measured.second.sum() + (events.size - 1).coerceAtLeast(0) * 5f > plot.width()
    ) {
        scale = (scale - 0.05f).coerceAtLeast(1f)
        measured = measurements(scale)
    }
    val widths = measured.second
    val preferredGap = 5f
    val gap = if (events.size <= 1 || widths.sum() + (events.size - 1) * preferredGap <= plot.width()) {
        preferredGap
    } else {
        // At the 12-event limit, overlap instead of throwing, dropping, or moving the graph.
        (plot.width() - widths.sum()) / (events.size - 1)
    }
    val anchors = events.map { event ->
        plot.left + plot.width() * widgetChartXFraction(
            event.timestamp,
            chartStart,
            snapshot.chartEndTimestamp,
        )
    }
    val lefts = MutableList(events.size) { index -> anchors[index] - widths[index] / 2f }
    lefts[0] = max(plot.left, lefts[0])
    for (index in 1..lefts.lastIndex) {
        lefts[index] = max(lefts[index], lefts[index - 1] + widths[index - 1] + gap)
    }
    if (lefts.last() + widths.last() > plot.right) {
        lefts[lefts.lastIndex] = plot.right - widths.last()
        for (index in lefts.lastIndex - 1 downTo 0) {
            lefts[index] = min(lefts[index], lefts[index + 1] - gap - widths[index])
        }
    }
    if (lefts.first() < plot.left) {
        val shift = plot.left - lefts.first()
        lefts.indices.forEach { lefts[it] += shift }
    }

    val pillHeight = 14f * scale
    val leaderClearance = min(6f, (baselineY - minimumBubbleTop - pillHeight).coerceAtLeast(0f))
    val bubbleBottom = baselineY - leaderClearance
    return LargeCoverBolusLayout(
        scale = scale,
        gap = gap,
        labels = events.mapIndexed { index, event ->
            WidgetBolusLabelLayout(
                event = event,
                text = String.format(Locale.US, "%.2f", event.units),
                anchorX = anchors[index],
                bubble = RectF(
                    lefts[index],
                    bubbleBottom - pillHeight,
                    lefts[index] + widths[index],
                    bubbleBottom,
                ),
            )
        },
    )
}

private fun moveLabelsAwayFromCgmPoints(
    labels: List<WidgetBolusLabelLayout>,
    points: List<ProtectedCgmPoint>,
    plot: RectF,
    gap: Float,
): List<WidgetBolusLabelLayout> {
    val result = labels.toMutableList()
    result.indices.forEach { index ->
        val current = result[index]
        if (points.none(current.bubble::horizontallyIntersects)) return@forEach
        val minimum = if (index == 0) plot.left else result[index - 1].bubble.right + gap
        val maximum = if (index == result.lastIndex) {
            plot.right - current.bubble.width()
        } else {
            result[index + 1].bubble.left - gap - current.bubble.width()
        }
        if (maximum < minimum) return@forEach
        val candidate = (ceil(minimum).toInt()..floor(maximum).toInt())
            .asSequence()
            .map(Int::toFloat)
            .filter { left ->
                val candidateBubble = RectF(
                    left,
                    current.bubble.top,
                    left + current.bubble.width(),
                    current.bubble.bottom,
                )
                points.none(candidateBubble::horizontallyIntersects)
            }
            .minByOrNull { left -> abs(left - current.bubble.left) }
            ?: return@forEach
        result[index] = current.copy(
            bubble = RectF(
                candidate,
                current.bubble.top,
                candidate + current.bubble.width(),
                current.bubble.bottom,
            ),
        )
    }
    return result
}

private data class ProtectedCgmPoint(
    val x: Float,
    val y: Float,
    val radius: Float,
)

private fun RectF.horizontallyIntersects(point: ProtectedCgmPoint): Boolean =
    point.x + point.radius + 2f >= left && point.x - point.radius - 2f <= right

private data class PixelPatch(
    val bitmap: Bitmap,
    val left: Int,
    val top: Int,
)
