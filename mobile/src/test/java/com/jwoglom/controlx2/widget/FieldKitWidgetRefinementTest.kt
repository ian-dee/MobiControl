package com.jwoglom.controlx2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xhdpi")
class FieldKitWidgetRefinementTest {
    @Test
    fun `approved resources are scoped to only three Field Kit presentations`() {
        val compact = widgetSkinPalette(AppSkin.FIELD_KIT, WidgetPresentation.COMPACT)
        assertEquals(R.drawable.mobicontrol_widget_background_field_kit_light, compact.backgroundDrawable)
        assertEquals(R.drawable.mobicontrol_widget_glucose_tile_field_kit_light, compact.glucoseTileDrawable)
        assertEquals(R.drawable.mobicontrol_widget_iob_tile_field_kit, compact.iobTileDrawable)

        val expanded = widgetSkinPalette(AppSkin.FIELD_KIT, WidgetPresentation.EXPANDED)
        assertEquals(R.drawable.mobicontrol_widget_background_field_kit, expanded.backgroundDrawable)
        assertEquals(R.drawable.mobicontrol_widget_glucose_tile_field_kit_light, expanded.glucoseTileDrawable)
        assertEquals(R.drawable.mobicontrol_widget_iob_tile_field_kit, expanded.iobTileDrawable)
        assertEquals(R.drawable.mobicontrol_widget_chart_panel_field_kit_light, expanded.chartPanelDrawable)

        val coverWide = widgetSkinPalette(AppSkin.FIELD_KIT, WidgetPresentation.COVER_WIDE)
        assertEquals(R.drawable.mobicontrol_widget_glucose_tile_field_kit_light, coverWide.glucoseTileDrawable)
        assertEquals(R.drawable.mobicontrol_cover_widget_iob_tile_field_kit, coverWide.iobTileDrawable)
        assertEquals(R.drawable.mobicontrol_cover_widget_chart_panel_field_kit_light,
            coverWide.chartPanelDrawable)

        val coverCompact = widgetSkinPalette(AppSkin.FIELD_KIT, WidgetPresentation.COVER_COMPACT)
        assertEquals(R.drawable.mobicontrol_widget_glucose_tile_field_kit,
            coverCompact.glucoseTileDrawable)
        assertEquals(R.drawable.mobicontrol_cover_widget_iob_tile_field_kit,
            coverCompact.iobTileDrawable)
        assertEquals(R.drawable.mobicontrol_cover_widget_chart_panel_field_kit,
            coverCompact.chartPanelDrawable)
    }

    @Test
    fun `approved native drawable materials match revision five`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        assertDrawableEquals(
            context.getDrawable(R.drawable.mobicontrol_widget_glucose_tile_field_kit_light)!!,
            glucoseBackground(density), (98 * density).toInt(), (54 * density).toInt(),
        )
        assertDrawableEquals(
            context.getDrawable(R.drawable.mobicontrol_widget_background_field_kit_light)!!,
            compactBackground(density), (196 * density).toInt(), (100 * density).toInt(),
        )
        assertDrawableEquals(
            context.getDrawable(R.drawable.mobicontrol_widget_chart_panel_field_kit_light)!!,
            chartBackground(density), (304 * density).toInt(), (120 * density).toInt(),
        )
        assertDrawableEquals(
            context.getDrawable(R.drawable.mobicontrol_cover_widget_chart_panel_field_kit_light)!!,
            chartBackground(density, cover = true), (192 * density).toInt(), (113 * density).toInt(),
        )
    }

    @Test
    fun `six approved sizes preserve padding and original cream IOB`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val cases = listOf(
            Triple(WidgetPresentation.COMPACT, 168, 84),
            Triple(WidgetPresentation.COMPACT, 196, 100),
            Triple(WidgetPresentation.EXPANDED, 320, 220),
            Triple(WidgetPresentation.EXPANDED, 320, 260),
            Triple(WidgetPresentation.COVER_WIDE, 312, 133),
            Triple(WidgetPresentation.COVER_WIDE, 312, 153),
        )
        cases.forEach { (presentation, widthDp, heightDp) ->
            val root = render(context, presentation, widthDp, heightDp)
            val bg = root.findViewById<View>(R.id.widget_bg_tile)
            val iob = root.findViewById<View>(R.id.widget_iob_tile)
            val expectedBgPadding = when (presentation) {
                WidgetPresentation.COMPACT -> listOf(6, 0, 5, 0)
                WidgetPresentation.EXPANDED -> listOf(8, 0, 7, 0)
                WidgetPresentation.COVER_WIDE -> listOf(5, 0, 5, 0)
                else -> error("Unexpected case")
            }.map { (it * density).toInt() }
            assertEquals("$presentation $widthDp x $heightDp glucose padding",
                expectedBgPadding, bg.padding())

            val expectedIob = context.getDrawable(
                if (presentation == WidgetPresentation.COVER_WIDE) {
                    R.drawable.mobicontrol_cover_widget_iob_tile_field_kit
                } else R.drawable.mobicontrol_widget_iob_tile_field_kit,
            )!!
            assertDrawableEquals(iob.background, expectedIob, iob.width, iob.height)

            if (presentation != WidgetPresentation.COMPACT) {
                val chart = root.findViewById<View>(R.id.widget_chart_panel)
                val expectedChartPadding = when (presentation) {
                    WidgetPresentation.EXPANDED -> listOf(5, 4, 5, 3)
                    WidgetPresentation.COVER_WIDE -> listOf(4, 4, 4, 3)
                    else -> error("Unexpected case")
                }.map { (it * density).toInt() }
                assertEquals("$presentation $widthDp x $heightDp chart padding",
                    expectedChartPadding, chart.padding())
            }
        }
    }

    private fun render(
        context: Context,
        presentation: WidgetPresentation,
        widthDp: Int,
        heightDp: Int,
    ): View {
        val now = Instant.parse("2026-09-27T18:00:00Z")
        val end = now.epochSecond
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now.minusSeconds(45)),
            iob = WidgetMetric("3.00", now.minusSeconds(75)),
            glucoseUnit = GlucoseUnit.MGDL,
            chartEndTimestamp = end,
            cgmPoints = (0..48).map { WidgetCgmPoint(end - (48 - it) * 300L, 110 + (it % 12) * 4) },
            boluses = listOf(
                WidgetBolusEvent(end - 9_000, 1.25f, false),
                WidgetBolusEvent(end - 4_800, 0.65f, true),
            ),
            lowerControlLimitMgdl = 70,
            upperControlLimitMgdl = 180,
            colorLowDots = true,
            colorHighDots = true,
        )
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        val root = MobiControlWidgetRenderer.render(
            context, 42, options, snapshot, now, presentation, AppSkin.FIELD_KIT,
        ).apply(context, FrameLayout(context))
        val width = (widthDp * context.resources.displayMetrics.density).toInt()
        val height = (heightDp * context.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        return root
    }

    private fun View.padding() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

    private fun assertDrawableEquals(actual: Drawable, expected: Drawable, width: Int, height: Int) {
        assertTrue("Drawable dimensions must be positive", width > 0 && height > 0)
        val actualBitmap = draw(actual, width, height)
        val expectedBitmap = draw(expected, width, height)
        assertTrue("Native drawable pixels differ from revision 5", actualBitmap.sameAs(expectedBitmap))
        actualBitmap.recycle()
        expectedBitmap.recycle()
    }

    private fun draw(drawable: Drawable, width: Int, height: Int) =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(this))
        }

    private fun gradient(
        density: Float,
        orientation: GradientDrawable.Orientation,
        colors: List<String>,
        radius: Float,
        stroke: String,
    ) = GradientDrawable(orientation, colors.map(Color::parseColor).toIntArray()).apply {
        cornerRadius = radius * density
        setStroke((0.5f * density).toInt().coerceAtLeast(1), Color.parseColor(stroke))
    }

    private fun layers(density: Float, outer: Drawable, inner: Drawable): Drawable =
        LayerDrawable(arrayOf(outer, inner)).apply {
            val inset = (1.5f * density).toInt()
            setLayerInset(1, inset, inset, inset, inset)
        }

    private fun glucoseBackground(d: Float) = layers(d,
        gradient(d, GradientDrawable.Orientation.TOP_BOTTOM,
            listOf("#86917F", "#73816A", "#65725E"), 8f, "#697660"),
        gradient(d, GradientDrawable.Orientation.TL_BR,
            listOf("#BFCDB5", "#D9E1CD", "#CDD8C0"), 6.5f, "#5587957C"))

    private fun compactBackground(d: Float) = layers(d,
        gradient(d, GradientDrawable.Orientation.TOP_BOTTOM,
            listOf("#656E63", "#596357", "#515B50"), 14f, "#515D51"),
        gradient(d, GradientDrawable.Orientation.TOP_BOTTOM,
            listOf("#F4F2E9", "#ECEBE1", "#D8D9D1"), 12.5f, "#66858D80"))

    private fun chartBackground(d: Float, cover: Boolean = false) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        listOf("#E8EBDF", "#F2F2E8", "#E0E5D8").map(Color::parseColor).toIntArray(),
    ).apply {
        cornerRadius = 8f * d
        if (cover) {
            cornerRadii = floatArrayOf(8f, 8f, 8f, 8f, 16f, 16f, 8f, 8f)
                .map { it * d }.toFloatArray()
        }
        setStroke(d.toInt().coerceAtLeast(1), Color.parseColor("#9AA18F"))
    }
}
