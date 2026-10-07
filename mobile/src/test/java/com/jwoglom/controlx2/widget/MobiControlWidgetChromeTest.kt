package com.jwoglom.controlx2.widget

import android.content.Context
import android.graphics.Outline
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import org.junit.Assert.assertEquals
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobiControlWidgetChromeTest {
    @Test
    fun `all skins own a single background outline at small and large host sizes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        AppSkin.entries.forEach { skin ->
            WidgetPresentation.entries.forEach { presentation ->
                for ((widthDp, heightDp) in listOf(150 to 84, 320 to 220)) {
                    val label = "$skin $presentation ${widthDp}x$heightDp"
                    val root = MobiControlWidgetRenderer.render(
                        context, 42, Bundle(), MobiControlWidgetSnapshot(
                            glucose = WidgetMetric(null, null), iob = WidgetMetric(null, null),
                            glucoseUnit = GlucoseUnit.MGDL, chartEndTimestamp = 0,
                            cgmPoints = emptyList(), boluses = emptyList(),
                            lowerControlLimitMgdl = 70, upperControlLimitMgdl = 180,
                            colorLowDots = true, colorHighDots = true,
                        ),
                        Instant.parse("2026-09-13T20:00:00Z"), presentation, skin,
                    ).apply(context, FrameLayout(context))
                    val width = (widthDp * density).toInt()
                    val height = (heightDp * density).toInt()
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, width, height)
                    root.draw(Canvas()) // Android assigns drawable bounds on the first draw.

                    // Launcher3 only defers its own corner mask when this exact background
                    // view owns clipping. An app-private root ID lets the host cut the frame.
                    assertSame(label, root, root.findViewById(android.R.id.background))
                    assertEquals(label, 1, countBackgrounds(root))
                    assertTrue(label, root.clipToOutline)
                    assertSame(label, android.view.ViewOutlineProvider.BACKGROUND, root.outlineProvider)
                    val outline = Outline()
                    root.outlineProvider.getOutline(root, outline)
                    val bounds = Rect()
                    assertTrue(label, outline.getRect(bounds))
                    assertEquals(label, Rect(0, 0, width, height), bounds)
                    assertEquals(label, widgetSkinPalette(skin, presentation).outerRadiusDp * density,
                        outline.radius, 0.01f)
                }
            }
        }
    }

    private fun countBackgrounds(view: View): Int =
        (if (view.id == android.R.id.background) 1 else 0) +
            if (view is ViewGroup) (0 until view.childCount).sumOf {
                countBackgrounds(view.getChildAt(it))
            } else 0

    @Test
    fun `home inset metric cards share the outer corner geometry`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layouts = listOf(R.layout.mobicontrol_widget_compact, R.layout.mobicontrol_widget)
        layouts.forEach { layout ->
            val root = LayoutInflater.from(context).inflate(layout, null) as ViewGroup
            val content = if (root.paddingLeft == 0) root.getChildAt(0) as ViewGroup else root
            val outerRadius = (root.background as GradientDrawable).cornerRadius
            for (id in listOf(R.id.widget_bg_value, R.id.widget_iob_value)) {
                var tile = root.findViewById<View>(id)
                while (tile.background !is GradientDrawable) tile = tile.parent as View
                val radius = (tile.background as GradientDrawable).cornerRadius
                assertEquals("layout=$layout metric=$id must use outer radius minus inset",
                    outerRadius, radius + content.paddingLeft, 0.01f)
            }
            assertEquals("balanced outer insets", content.paddingLeft, content.paddingRight)
            assertEquals("balanced outer insets", content.paddingLeft, content.paddingBottom)
        }
    }

    @Test
    fun `no widget layout begins with a decorative top strip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layouts = listOf(
            R.layout.mobicontrol_widget_compact,
            R.layout.mobicontrol_widget,
            R.layout.mobicontrol_cover_widget_compact,
            R.layout.mobicontrol_cover_widget_wide,
        )

        layouts.forEach { layout ->
            val root = LayoutInflater.from(context).inflate(layout, null) as ViewGroup
            assertFalse(
                "Widget layout $layout must not restore the decorative blue top strip",
                root.getChildAt(0) is ImageView,
            )
            assertNotNull(
                "Widget layout $layout must expose the shared trend-arrow view",
                root.findViewById<TextView>(R.id.widget_bg_trend),
            )
        }
    }
}
