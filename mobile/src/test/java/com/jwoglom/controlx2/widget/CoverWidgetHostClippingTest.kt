package com.jwoglom.controlx2.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Samsung's cover container clips independently of the widget's background ID/outline. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class CoverWidgetHostClippingTest {
    @Test
    fun `cover skin changes only its outer frame and keeps enamel concentric`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (skin in AppSkin.entries) {
            val home = widgetSkinPalette(skin)
            for (presentation in listOf(WidgetPresentation.COVER_COMPACT, WidgetPresentation.COVER_WIDE)) {
                val cover = widgetSkinPalette(skin, presentation)
                assertEquals(home, cover.copy(
                    backgroundDrawable = home.backgroundDrawable,
                    glucoseTileDrawable = home.glucoseTileDrawable,
                    iobTileDrawable = home.iobTileDrawable,
                    chartPanelDrawable = home.chartPanelDrawable,
                    outerRadiusDp = home.outerRadiusDp,
                ))
                if (skin == AppSkin.FIELD_KIT) {
                    val frame = context.getDrawable(cover.backgroundDrawable) as LayerDrawable
                    val outer = frame.getDrawable(0) as GradientDrawable
                    val enamel = frame.getDrawable(1) as GradientDrawable
                    assertEquals(outer.cornerRadius, enamel.cornerRadius + frame.getLayerInsetLeft(1), 0.01f)
                    assertEquals(frame.getLayerInsetLeft(1), frame.getLayerInsetTop(1))
                    assertEquals(frame.getLayerInsetLeft(1), frame.getLayerInsetRight(1))
                    assertEquals(frame.getLayerInsetLeft(1), frame.getLayerInsetBottom(1))
                }
            }
        }
    }

    @Test
    fun `cover bottom tiles follow outer corner centers without rounding internal corners`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val now = Instant.parse("2026-09-14T16:00:00Z")
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now), iob = WidgetMetric("3.0", now),
            glucoseUnit = GlucoseUnit.MGDL, chartEndTimestamp = now.epochSecond,
            cgmPoints = emptyList(), boluses = emptyList(),
            lowerControlLimitMgdl = 70, upperControlLimitMgdl = 180,
            colorLowDots = true, colorHighDots = true,
        )

        AppSkin.entries.forEach { skin ->
            for ((presentation, widthDp) in listOf(
                WidgetPresentation.COVER_COMPACT to 150,
                WidgetPresentation.COVER_WIDE to 312,
            )) {
                val root = MobiControlWidgetRenderer.render(
                    context, 42, Bundle(), snapshot, now, presentation, skin,
                ).apply(context, FrameLayout(context))
                val width = (widthDp * density).toInt()
                val height = (133 * density).toInt()
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, width, height)

                val expectedOuterAdjacentRadius =
                    widgetSkinPalette(skin, presentation).outerRadiusDp * density - root.paddingLeft
                val ordinaryInnerRadius = baseInnerRadiusDp(skin) * density
                val iob = outerShape(root.findViewById<View>(R.id.widget_iob_tile).background)
                val iobRadii = requireNotNull(iob.cornerRadii)
                assertEquals("$skin $presentation IOB lower-left shares the widget corner center",
                    expectedOuterAdjacentRadius, iobRadii[6], 0.01f)
                assertEquals("$skin $presentation IOB lower-right remains an internal corner",
                    ordinaryInnerRadius, iobRadii[4], 0.01f)
                assertTrue("$skin $presentation short IOB tile must not clamp its paired radii",
                    iobRadii[0] + iobRadii[6] <=
                        root.findViewById<View>(R.id.widget_iob_tile).height + 0.01f)

                if (presentation == WidgetPresentation.COVER_WIDE) {
                    val chartDrawable = root.findViewById<View>(R.id.widget_chart_panel).background
                    val chart = outerShape(chartDrawable)
                    val chartRadii = requireNotNull(chart.cornerRadii)
                    assertEquals("$skin chart lower-right shares the widget corner center",
                        expectedOuterAdjacentRadius, chartRadii[4], 0.01f)
                    assertEquals("$skin chart lower-left remains an internal corner",
                        ordinaryInnerRadius, chartRadii[6], 0.01f)
                    if (skin == AppSkin.FIELD_KIT) {
                        if (presentation == WidgetPresentation.COVER_WIDE) {
                            assertTrue("Refined Field Kit cover graph is one quiet outline",
                                chartDrawable is GradientDrawable)
                        }
                    }
                }

                if (skin == AppSkin.FIELD_KIT) {
                    val iobLayers = root.findViewById<View>(R.id.widget_iob_tile).background as LayerDrawable
                    val innerIob = iobLayers.getDrawable(1) as GradientDrawable
                    assertEquals("Field Kit inner IOB layer shares the same lower-left center",
                        expectedOuterAdjacentRadius - iobLayers.getLayerInsetLeft(1),
                        requireNotNull(innerIob.cornerRadii)[6], 0.01f)
                }
            }
        }
    }

    private fun outerShape(drawable: Drawable): GradientDrawable = when (drawable) {
        is GradientDrawable -> drawable
        is LayerDrawable -> drawable.getDrawable(0) as GradientDrawable
        else -> error("Unexpected widget tile drawable ${drawable::class.java.name}")
    }

    private fun baseInnerRadiusDp(skin: AppSkin): Float = when (skin) {
        AppSkin.CURRENT -> 14f
        AppSkin.INSTRUMENT -> 4f
        AppSkin.FIELD_KIT -> 8f
    }

    @Test fun `cover frames survive the separate host mask at mdpi`() = checkCoverFrames()

    @Test
    @Config(qualifiers = "xhdpi")
    fun `cover frames survive the separate host mask at xhdpi`() = checkCoverFrames()

    private fun checkCoverFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val now = Instant.parse("2026-09-14T16:00:00Z")
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now), iob = WidgetMetric("3.0", now),
            glucoseUnit = GlucoseUnit.MGDL, chartEndTimestamp = now.epochSecond,
            cgmPoints = emptyList(), boluses = emptyList(),
            lowerControlLimitMgdl = 70, upperControlLimitMgdl = 180,
            colorLowDots = true, colorHighDots = true,
        )
        AppSkin.entries.forEach { skin ->
            for ((presentation, widthDp) in listOf(
                WidgetPresentation.COVER_COMPACT to 150,
                WidgetPresentation.COVER_WIDE to 312,
            )) {
                for (heightDp in listOf(133, 153)) {
                    val root = MobiControlWidgetRenderer.render(
                        context, 42, Bundle(), snapshot, now, presentation, skin,
                    ).apply(context, FrameLayout(context))
                    val width = (widthDp * density).toInt()
                    val height = (heightDp * density).toInt()
                    val original = drawFrame(root.background, width, height, null)
                    // Conservative envelope of the independently imposed cover outline,
                    // including the ~23dp mask visible in the saved Flip 8 capture.
                    val hosted = drawFrame(root.background, width, height, 24f * density)
                    val lost = (0 until width).sumOf { x ->
                        (0 until height).count { y ->
                            Color.alpha(original.getPixel(x, y)) >= 250 &&
                                Color.alpha(hosted.getPixel(x, y)) < 230
                        }
                    }
                    original.recycle()
                    hosted.recycle()
                    assertTrue("$skin $presentation ${widthDp}x$heightDp density=$density: " +
                        "$lost opaque frame pixels cut off by cover host", lost <= 4)
                }
            }
        }
    }

    private fun drawFrame(background: Drawable, width: Int, height: Int, hostRadius: Float?): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            if (hostRadius != null) {
                canvas.clipPath(Path().apply {
                    addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
                        hostRadius, hostRadius, Path.Direction.CW)
                })
            }
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        }
}
