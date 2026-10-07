package com.jwoglom.controlx2.test.snapshots

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.widget.MobiControlWidgetRenderer
import com.jwoglom.controlx2.widget.MobiControlWidgetSnapshot
import com.jwoglom.controlx2.widget.WidgetBolusEvent
import com.jwoglom.controlx2.widget.WidgetCgmPoint
import com.jwoglom.controlx2.widget.WidgetMetric
import com.jwoglom.controlx2.widget.WidgetPresentation
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class ReadmeWidgetsTest {
    @Test fun allWidgetSkins() {
        AppSkin.entries.forEach { skin ->
            capture(skin, WidgetPresentation.COMPACT, 168, 84, "widget-compact")
            capture(skin, WidgetPresentation.EXPANDED, 320, 220, "widget-history")
            capture(skin, WidgetPresentation.COVER_COMPACT, 150, 133, "widget-cover-compact")
            capture(skin, WidgetPresentation.COVER_WIDE, 312, 133, "widget-cover-wide")
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(skin: AppSkin, presentation: WidgetPresentation, width: Int, height: Int, name: String) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val context = controller.get()
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("150", now.minusSeconds(45)),
            iob = WidgetMetric("1.50", now.minusSeconds(60)),
            glucoseUnit = GlucoseUnit.MGDL,
            chartEndTimestamp = now.epochSecond,
            cgmPoints = (0..48).map { index ->
                WidgetCgmPoint(now.epochSecond - (48 - index) * 300L,
                    (150 + 35 * sin(index * Math.PI / 24)).roundToInt())
            },
            boluses = listOf(
                WidgetBolusEvent(now.epochSecond - 9_000, 1.25f, automatic = false),
                WidgetBolusEvent(now.epochSecond - 4_800, 0.65f, automatic = true),
            ),
            lowerControlLimitMgdl = 70,
            upperControlLimitMgdl = 180,
            colorLowDots = true,
            colorHighDots = true,
            trendArrow = WidgetMetric("▶", now.minusSeconds(45)),
            controlIqMode = WidgetMetric("STANDARD", now.minusSeconds(45)),
        )
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
        try {
            val view = MobiControlWidgetRenderer.render(context, 42, options, snapshot, now, presentation, skin)
                .apply(context, FrameLayout(context))
            val host = FrameLayout(context)
            host.addView(view, FrameLayout.LayoutParams(width, height))
            context.setContentView(host)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            view.captureRoboImage(galleryFile(skin, name).absolutePath,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
