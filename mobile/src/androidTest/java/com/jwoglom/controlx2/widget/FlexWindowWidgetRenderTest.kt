package com.jwoglom.controlx2.widget

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlexWindowWidgetRenderTest {
    @Test
    fun renderExactCoverSlotsForVisualReview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val now = Instant.parse("2026-08-22T16:00:00Z")
        val chartEnd = now.epochSecond
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now.minusSeconds(45)),
            iob = WidgetMetric("3.00", now.minusSeconds(75)),
            glucoseUnit = GlucoseUnit.MGDL,
            chartEndTimestamp = chartEnd,
            cgmPoints = (0..48).map { index ->
                WidgetCgmPoint(
                    timestamp = chartEnd - (48 - index) * 300L,
                    glucoseMgdl = 118 + ((index % 12) * 4) + if (index > 32) 15 else 0,
                )
            },
            boluses = listOf(
                WidgetBolusEvent(chartEnd - 9_000, 1.25f, automatic = false),
                WidgetBolusEvent(chartEnd - 4_800, 0.65f, automatic = true),
                WidgetBolusEvent(chartEnd - 1_500, 2.10f, automatic = false),
            ),
            lowerControlLimitMgdl = 70,
            upperControlLimitMgdl = 180,
            colorLowDots = true,
            colorHighDots = true,
        )

        val compact = render(
            widthDp = 150,
            heightDp = 133,
            presentation = WidgetPresentation.COVER_COMPACT,
            snapshot = snapshot,
            now = now,
        )
        val wide = render(
            widthDp = 312,
            heightDp = 133,
            presentation = WidgetPresentation.COVER_WIDE,
            snapshot = snapshot,
            now = now,
        )

        assertEquals((150 * density).toInt(), compact.width)
        assertEquals((133 * density).toInt(), compact.height)
        assertEquals((312 * density).toInt(), wide.width)
        assertEquals((133 * density).toInt(), wide.height)

        val preview = Bitmap.createBitmap(948, 839, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        canvas.drawColor(Color.rgb(183, 190, 199))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(25, 32, 42)
            textSize = 24f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("MobiControl Cover Compact · 2 × 2", 28f, 40f, labelPaint)
        canvas.drawBitmap(compact, ((preview.width - compact.width) / 2f), 58f, null)
        canvas.drawText("MobiControl Cover History · 4 × 2", 28f, 410f, labelPaint)
        canvas.drawBitmap(wide, ((preview.width - wide.width) / 2f), 428f, null)

        val output = File(context.getExternalFilesDir(null), "flexwindow-widget-preview.png")
        FileOutputStream(output).use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(output.exists())
        assertTrue(output.length() > 0)
    }

    private fun render(
        widthDp: Int,
        heightDp: Int,
        presentation: WidgetPresentation,
        snapshot: MobiControlWidgetSnapshot,
        now: Instant,
    ): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        val remoteViews = MobiControlWidgetRenderer.render(
            context = context,
            appWidgetId = 9_000 + widthDp,
            options = options,
            snapshot = snapshot,
            now = now,
            presentationOverride = presentation,
        )
        val parent = FrameLayout(context)
        val view = remoteViews.apply(context, parent)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, widthPx, heightPx)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
            view.draw(Canvas(bitmap))
        }
    }
}
