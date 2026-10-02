package com.jwoglom.controlx2.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.shared.util.GlucoseConverter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal data class WidgetChartBounds(
    val minimumMgdl: Int,
    val maximumMgdl: Int,
)

internal enum class WidgetBolusPresentation {
    STANDARD,
    LARGE_COVER,
}

internal fun widgetChartBounds(points: List<WidgetCgmPoint>): WidgetChartBounds {
    val maximumPoint = points.maxOfOrNull { it.glucoseMgdl } ?: 180
    val roundedMaximum = (ceil(max(maximumPoint, 180) / 50.0) * 50).toInt()
    return WidgetChartBounds(minimumMgdl = 40, maximumMgdl = roundedMaximum.coerceIn(200, 400))
}

internal fun widgetChartXFraction(timestamp: Long, start: Long, end: Long): Float {
    if (end <= start) return 1f
    return ((timestamp - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

internal fun widgetChartYFraction(glucoseMgdl: Int, bounds: WidgetChartBounds): Float {
    val range = (bounds.maximumMgdl - bounds.minimumMgdl).coerceAtLeast(1)
    return 1f - ((glucoseMgdl - bounds.minimumMgdl).toFloat() / range).coerceIn(0f, 1f)
}

internal object WidgetChartRenderer {
    fun render(
        snapshot: MobiControlWidgetSnapshot,
        widthPx: Int,
        heightPx: Int,
        palette: WidgetChartPalette = widgetSkinPalette(
            com.jwoglom.controlx2.presentation.theme.AppSkin.CURRENT,
        ).chart,
        bolusPresentation: WidgetBolusPresentation = WidgetBolusPresentation.STANDARD,
    ): Bitmap {
        val width = widthPx.coerceIn(320, 640)
        val height = heightPx.coerceIn(120, 260)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.labelColor
            textSize = max(15f, height * 0.064f)
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.gridColor
            strokeWidth = 1f
        }
        val timeLabelHeight = labelPaint.textSize + 7f
        val bolusTrackHeight = max(25f, labelPaint.textSize + 11f)
        val plot = RectF(
            labelPaint.measureText("400") + 11f,
            7f,
            width - 7f,
            height - timeLabelHeight - bolusTrackHeight,
        )
        val bounds = widgetChartBounds(snapshot.cgmPoints)
        val chartStart = snapshot.chartEndTimestamp - WIDGET_CHART_WINDOW_SECONDS

        var tick = 50
        while (tick <= bounds.maximumMgdl) {
            if (tick >= bounds.minimumMgdl) {
                val y = plot.top + plot.height() * widgetChartYFraction(tick, bounds)
                canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
                val label = when (snapshot.glucoseUnit) {
                    GlucoseUnit.MGDL -> tick.toString()
                    GlucoseUnit.MMOL -> String.format(
                        Locale.US,
                        "%.1f",
                        tick * GlucoseConverter.MGDL_TO_MMOL_FACTOR,
                    )
                }
                canvas.drawText(label, 3f, y + labelPaint.textSize * 0.34f, labelPaint)
            }
            tick += 50
        }

        val lowerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 211, 47, 47)
            strokeWidth = 1.5f
        }
        val upperLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(95, 245, 124, 0)
            strokeWidth = 1.5f
        }
        fun thresholdY(value: Int) = plot.top + plot.height() * widgetChartYFraction(value, bounds)
        canvas.drawLine(plot.left, thresholdY(snapshot.lowerControlLimitMgdl), plot.right,
            thresholdY(snapshot.lowerControlLimitMgdl), lowerLinePaint)
        canvas.drawLine(plot.left, thresholdY(snapshot.upperControlLimitMgdl), plot.right,
            thresholdY(snapshot.upperControlLimitMgdl), upperLinePaint)

        snapshot.cgmPoints.forEach { point ->
            val x = plot.left + plot.width() * widgetChartXFraction(
                point.timestamp,
                chartStart,
                snapshot.chartEndTimestamp,
            )
            val y = plot.top + plot.height() * widgetChartYFraction(point.glucoseMgdl, bounds)
            val (fill, outline) = when {
                snapshot.colorLowDots && point.glucoseMgdl < snapshot.lowerControlLimitMgdl ->
                    Color.rgb(255, 205, 210) to Color.rgb(211, 47, 47)
                snapshot.colorHighDots && point.glucoseMgdl > snapshot.upperControlLimitMgdl ->
                    Color.rgb(255, 224, 178) to Color.rgb(245, 124, 0)
                else -> Color.rgb(208, 248, 199) to Color.rgb(46, 125, 50)
            }
            val radius = max(2.8f, min(4.5f, width / 145f))
            canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outline
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
            })
        }

        val bolusBaselineY = height - timeLabelHeight - 3f
        canvas.drawLine(
            plot.left,
            bolusBaselineY,
            plot.right,
            bolusBaselineY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.baselineColor
                strokeWidth = 1.2f
            },
        )
        when (bolusPresentation) {
            WidgetBolusPresentation.STANDARD -> drawBolusEvents(
                canvas,
                snapshot,
                plot,
                chartStart,
                labelPaint.textSize,
                bolusBaselineY,
            )
            WidgetBolusPresentation.LARGE_COVER -> CoverWideBolusChartRenderer.draw(
                bitmap,
                canvas,
                snapshot,
                plot,
                bounds,
                chartStart,
                labelPaint.textSize,
                bolusBaselineY,
                max(2.8f, min(4.5f, width / 145f)),
            )
        }

        canvas.drawText("4h ago", plot.left, height - 3f, labelPaint)
        val nowLabel = "now"
        canvas.drawText(nowLabel, plot.right - labelPaint.measureText(nowLabel), height - 3f, labelPaint)
        return bitmap
    }

    private fun drawBolusEvents(
        canvas: Canvas,
        snapshot: MobiControlWidgetSnapshot,
        plot: RectF,
        chartStart: Long,
        labelSize: Float,
        baselineY: Float,
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = labelSize * 0.82f
        }
        val displayed = snapshot.boluses.takeLast(12)
        displayed.forEachIndexed { index, bolus ->
            val x = plot.left + plot.width() * widgetChartXFraction(
                bolus.timestamp,
                chartStart,
                snapshot.chartEndTimestamp,
            )
            val fill = if (bolus.automatic) Color.rgb(126, 87, 194) else Color.rgb(25, 118, 210)
            val outline = if (bolus.automatic) Color.rgb(69, 39, 160) else Color.rgb(13, 71, 161)
            val stackLevel = overlappingPriorCount(displayed, index)
            val bubbleCenterY = baselineY - 10f - stackLevel * 14f
            val unitsText = String.format(Locale.US, "%.2f", bolus.units)
            val halfWidth = max(16f, textPaint.measureText(unitsText) / 2f + 5f)
            val bubble = RectF(
                (x - halfWidth).coerceAtLeast(plot.left),
                bubbleCenterY - 7f,
                (x + halfWidth).coerceAtMost(plot.right),
                bubbleCenterY + 7f,
            )
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outline
                strokeWidth = 1.8f
            }
            canvas.drawLine(x, baselineY, x, bubble.bottom, linePaint)
            canvas.drawRoundRect(bubble, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            canvas.drawRoundRect(bubble, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = outline
                style = Paint.Style.STROKE
                strokeWidth = 1.4f
            })
            canvas.drawText(unitsText, bubble.centerX(), bubble.centerY() -
                (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }
    }

    private fun overlappingPriorCount(events: List<WidgetBolusEvent>, index: Int): Int {
        val current = events[index]
        return events.take(index).count {
            current.timestamp - it.timestamp in 0..BOLUS_STACK_WINDOW_SECONDS
        }.coerceAtMost(4)
    }
}

private const val BOLUS_STACK_WINDOW_SECONDS = 25L * 60L
