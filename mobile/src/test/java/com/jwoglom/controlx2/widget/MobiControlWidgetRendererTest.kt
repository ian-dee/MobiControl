package com.jwoglom.controlx2.widget

import android.content.Context
import android.os.Bundle
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobiControlWidgetRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-08-24T20:00:00Z")

    @Test
    fun `all four presentations render a fresh trend arrow`() {
        WidgetPresentation.entries.forEach { presentation ->
            val remoteViews = MobiControlWidgetRenderer.render(
                context = context,
                appWidgetId = 42,
                options = Bundle(),
                snapshot = snapshot(WidgetMetric("▲▲", now)),
                now = now,
                presentationOverride = presentation,
                appSkin = AppSkin.CURRENT,
            )
            val applied = remoteViews.apply(context, FrameLayout(context))
            val arrow = applied.findViewById<TextView>(R.id.widget_bg_trend)

            assertEquals("presentation=$presentation", "▲▲", arrow.text.toString())
            assertEquals("presentation=$presentation", View.VISIBLE, arrow.visibility)
        }
    }

    @Test
    fun `renderer removes an unavailable trend view`() {
        val remoteViews = MobiControlWidgetRenderer.render(
            context = context,
            appWidgetId = 42,
            options = Bundle(),
            snapshot = snapshot(WidgetMetric(null, null)),
            now = now,
            presentationOverride = WidgetPresentation.COMPACT,
            appSkin = AppSkin.CURRENT,
        )
        val applied = remoteViews.apply(context, FrameLayout(context))

        assertEquals(View.GONE, applied.findViewById<View>(R.id.widget_bg_trend).visibility)
    }

    @Test
    fun `all presentations apply every app skin`() {
        AppSkin.entries.forEach { skin ->
            val palette = widgetSkinPalette(skin)
            WidgetPresentation.entries.forEach { presentation ->
                val applied = MobiControlWidgetRenderer.render(
                    context = context,
                    appWidgetId = 42,
                    options = Bundle(),
                    snapshot = snapshot(WidgetMetric("▶", now)),
                    now = now,
                    presentationOverride = presentation,
                    appSkin = skin,
                ).apply(context, FrameLayout(context))

                val root = applied.findViewById<View>(android.R.id.background)
                assertNotNull("skin=$skin presentation=$presentation", root.background)
                if (skin != AppSkin.FIELD_KIT) {
                    val background = root.background as? GradientDrawable
                    assertNotNull("skin=$skin presentation=$presentation", background)
                    assertEquals(
                        "skin=$skin presentation=$presentation",
                        palette.surfaceColor,
                        background?.color?.defaultColor,
                    )
                }
                assertEquals(
                    "skin=$skin presentation=$presentation",
                    palette.primaryTextColor,
                    applied.findViewById<TextView>(R.id.widget_title).currentTextColor,
                )
                assertEquals(
                    "skin=$skin presentation=$presentation",
                    palette.liveStatusColor,
                    applied.findViewById<TextView>(R.id.widget_status).currentTextColor,
                )
            }
        }
    }

    @Test
    fun `all presentations show the pump reported mode without replacing data status`() {
        WidgetPresentation.entries.forEach { presentation ->
            val applied = MobiControlWidgetRenderer.render(
                context = context,
                appWidgetId = 42,
                options = Bundle(),
                snapshot = snapshot(WidgetMetric("▶", now)).copy(
                    controlIqMode = WidgetMetric("SLEEP", now),
                ),
                now = now,
                presentationOverride = presentation,
                appSkin = AppSkin.CURRENT,
            ).apply(context, FrameLayout(context))

            assertEquals(
                "presentation=$presentation",
                View.VISIBLE,
                applied.findViewById<View>(R.id.widget_mode_chip).visibility,
            )
            assertEquals(
                "presentation=$presentation",
                "Sleep",
                applied.findViewById<TextView>(R.id.widget_mode_label).text.toString(),
            )
            assertEquals(
                "presentation=$presentation",
                "LIVE",
                applied.findViewById<TextView>(R.id.widget_status).text.toString(),
            )
        }
    }

    @Test
    fun `unknown mode leaves the compact indicator out of the layout`() {
        val applied = MobiControlWidgetRenderer.render(
            context = context,
            appWidgetId = 42,
            options = Bundle(),
            snapshot = snapshot(WidgetMetric("▶", now)).copy(
                controlIqMode = WidgetMetric("UNKNOWN", now),
            ),
            now = now,
            presentationOverride = WidgetPresentation.COMPACT,
            appSkin = AppSkin.CURRENT,
        ).apply(context, FrameLayout(context))

        assertEquals(View.GONE, applied.findViewById<View>(R.id.widget_mode_chip).visibility)
    }

    private fun snapshot(trendArrow: WidgetMetric) = MobiControlWidgetSnapshot(
        glucose = WidgetMetric("159", now),
        iob = WidgetMetric("2.75", now),
        glucoseUnit = GlucoseUnit.MGDL,
        chartEndTimestamp = now.epochSecond,
        cgmPoints = emptyList(),
        boluses = emptyList(),
        lowerControlLimitMgdl = 70,
        upperControlLimitMgdl = 180,
        colorLowDots = true,
        colorHighDots = true,
        trendArrow = trendArrow,
    )
}
