package com.jwoglom.controlx2.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.io.File
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
@Config(sdk = [35], qualifiers = "mdpi")
class WidgetBrandGeometryTest {
    @Test fun `portrait mark scales uniformly and cover badge clears host corner`() = checkBrand()

    @Test @Config(qualifiers = "xhdpi")
    fun `portrait mark and cover corner clearance survive higher density`() = checkBrand()

    private fun checkBrand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val density = context.resources.displayMetrics.density
        val now = Instant.parse("2026-09-19T12:00:00Z")
        val snapshot = MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now), iob = WidgetMetric("3.0", now),
            glucoseUnit = GlucoseUnit.MGDL, chartEndTimestamp = now.epochSecond,
            cgmPoints = emptyList(), boluses = emptyList(),
            lowerControlLimitMgdl = 70, upperControlLimitMgdl = 180,
            colorLowDots = true, colorHighDots = true,
        )
        AppSkin.entries.forEach { skin ->
            WidgetPresentation.entries.forEach { presentation ->
                val cover = presentation == WidgetPresentation.COVER_COMPACT ||
                    presentation == WidgetPresentation.COVER_WIDE
                val (widthDp, heightDp) = when (presentation) {
                    WidgetPresentation.COMPACT -> 168 to 84
                    WidgetPresentation.EXPANDED -> 320 to 220
                    WidgetPresentation.COVER_COMPACT -> 150 to 133
                    WidgetPresentation.COVER_WIDE -> 312 to 133
                }
                val root = MobiControlWidgetRenderer.render(
                    context, 42, Bundle(), snapshot, now, presentation, skin,
                ).apply(context, FrameLayout(context)) as ViewGroup
                val width = (widthDp * density).toInt()
                val height = (heightDp * density).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val brand = root.findViewById<ImageView>(R.id.widget_brand)
                val label = "$skin $presentation density=$density"
                assertEquals(label, 2f / 3f,
                    brand.drawable.intrinsicWidth.toFloat() / brand.drawable.intrinsicHeight, 0.001f)
                assertEquals(label, ImageView.ScaleType.FIT_CENTER, brand.scaleType)
                val matrix = FloatArray(9).also { brand.imageMatrix.getValues(it) }
                assertEquals("$label must not stretch the mark", matrix[Matrix.MSCALE_X],
                    matrix[Matrix.MSCALE_Y], 0.001f)
                val parameters = brand.layoutParams as ViewGroup.MarginLayoutParams
                val originalAllocation = when (presentation) {
                    WidgetPresentation.COMPACT -> 22
                    WidgetPresentation.EXPANDED -> 27
                    else -> 18
                }
                assertEquals("$label must retain header allocation", (originalAllocation * density).toInt(),
                    brand.width + parameters.leftMargin + parameters.rightMargin)
                if (cover) {
                    val bounds = Rect().also { brand.getDrawingRect(it); root.offsetDescendantRectToMyCoords(brand, it) }
                    // More conservative than the 24 dp widget frame; test the whole badge,
                    // not just the logo pixels or the outer widget background.
                    val hostRadius = 32 * density
                    for (x in listOf(bounds.left.toFloat(), bounds.right.toFloat())) {
                        for (y in listOf(bounds.top.toFloat(), bounds.bottom.toFloat())) {
                            if (x < hostRadius && y < hostRadius) {
                                val dx = hostRadius - x
                                val dy = hostRadius - y
                                assertTrue("$label full badge must clear host corner",
                                    dx * dx + dy * dy <= hostRadius * hostRadius)
                            }
                        }
                    }
                    if (skin == AppSkin.INSTRUMENT && density == 2f) {
                        val output = File("build/outputs/widget-logo-review/${presentation.name.lowercase()}.png")
                        output.parentFile?.mkdirs()
                        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
                bitmap.recycle()
            }
        }
    }
}
