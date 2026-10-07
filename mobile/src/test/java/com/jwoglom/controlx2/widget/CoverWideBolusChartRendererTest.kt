package com.jwoglom.controlx2.widget

import android.graphics.Paint
import android.graphics.RectF
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
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
class CoverWideBolusChartRendererTest {
    @Test
    fun `default cover labels stay sixty percent larger and below 50 for every chart bound`() {
        listOf(200, 250, 300, 400).forEach { maximum ->
            listOf(320 to 140, 400 to 180, 640 to 260).forEach { (width, height) ->
                val snapshot = snapshot(maximum = maximum)
                val geometry = geometry(snapshot, width, height)
                val layout = largeCoverBolusLayout(
                    snapshot,
                    geometry.plot,
                    END - WIDGET_CHART_WINDOW_SECONDS,
                    geometry.labelSize,
                    geometry.baselineY,
                    ceil(geometry.y50) + 2f,
                )

                assertEquals("maximum=$maximum size=${width}x$height", 1.60f, layout.scale, 0.001f)
                assertTrue(
                    "maximum=$maximum size=${width}x$height top=${layout.labels.first().bubble.top}",
                    layout.labels.all { it.bubble.top >= ceil(geometry.y50) + 1.99f },
                )

                val unmarked = WidgetChartRenderer.render(
                    snapshot.copy(boluses = emptyList()),
                    width,
                    height,
                    bolusPresentation = WidgetBolusPresentation.LARGE_COVER,
                )
                val marked = WidgetChartRenderer.render(
                    snapshot,
                    width,
                    height,
                    bolusPresentation = WidgetBolusPresentation.LARGE_COVER,
                )
                for (y in 0..ceil(geometry.y50).toInt()) {
                    for (x in 0 until marked.width) {
                        assertEquals(
                            "maximum=$maximum size=${width}x$height pixel=$x,$y",
                            unmarked.getPixel(x, y),
                            marked.getPixel(x, y),
                        )
                    }
                }
                unmarked.recycle()
                marked.recycle()
            }
        }
    }

    @Test
    fun `dense history retains twelve exact values and bounded timestamp anchors without throwing`() {
        val events = (0 until 12).map { index ->
            WidgetBolusEvent(
                timestamp = END - 3_000L + index * 90L,
                units = 0.25f + index * 0.5f,
                automatic = index % 2 == 1,
            )
        }
        val snapshot = snapshot(events = events)
        val geometry = geometry(snapshot, 320, 140)
        val layout = largeCoverBolusLayout(
            snapshot,
            geometry.plot,
            END - WIDGET_CHART_WINDOW_SECONDS,
            geometry.labelSize,
            geometry.baselineY,
            ceil(geometry.y50) + 2f,
        )

        assertEquals(12, layout.labels.size)
        assertEquals(events.map { "%.2f".format(java.util.Locale.US, it.units) }, layout.labels.map { it.text })
        layout.labels.forEachIndexed { index, label ->
            val expectedAnchor = geometry.plot.left + geometry.plot.width() * widgetChartXFraction(
                events[index].timestamp,
                END - WIDGET_CHART_WINDOW_SECONDS,
                END,
            )
            assertEquals(expectedAnchor, label.anchorX, 0.001f)
            assertTrue(label.bubble.left >= geometry.plot.left - 0.01f)
            assertTrue(label.bubble.right <= geometry.plot.right + 0.01f)
        }

        WidgetChartRenderer.render(
            snapshot,
            320,
            140,
            bolusPresentation = WidgetBolusPresentation.LARGE_COVER,
        ).recycle()
    }

    @Test
    fun `edge events keep exact time anchors while their pills remain fully visible`() {
        val events = listOf(
            WidgetBolusEvent(END - WIDGET_CHART_WINDOW_SECONDS, 15f, false),
            WidgetBolusEvent(END, 0.05f, true),
        )
        val snapshot = snapshot(events = events)
        val geometry = geometry(snapshot, 400, 180)
        val layout = largeCoverBolusLayout(
            snapshot,
            geometry.plot,
            END - WIDGET_CHART_WINDOW_SECONDS,
            geometry.labelSize,
            geometry.baselineY,
            ceil(geometry.y50) + 2f,
        )

        assertEquals(geometry.plot.left, layout.labels.first().anchorX, 0.001f)
        assertEquals(geometry.plot.right, layout.labels.last().anchorX, 0.001f)
        assertEquals(geometry.plot.left, layout.labels.first().bubble.left, 0.001f)
        assertEquals(geometry.plot.right, layout.labels.last().bubble.right, 0.001f)
    }

    @Test
    fun `unavoidable dense low glucose collisions restore the exact CGM pixels`() {
        val events = (0 until 12).map { index ->
            WidgetBolusEvent(END - 3_000L + index * 120L, 10f + index * 0.25f, index % 2 == 0)
        }
        val lowPoints = (0 until 12).map { index ->
            WidgetCgmPoint(END - 3_000L + index * 120L, 45 + index % 2)
        }
        val snapshot = snapshot(events = events, extraPoints = lowPoints)
        val unmarked = WidgetChartRenderer.render(
            snapshot.copy(boluses = emptyList()),
            320,
            140,
            bolusPresentation = WidgetBolusPresentation.LARGE_COVER,
        )
        val marked = WidgetChartRenderer.render(
            snapshot,
            320,
            140,
            bolusPresentation = WidgetBolusPresentation.LARGE_COVER,
        )
        val geometry = geometry(snapshot, 320, 140)
        val bounds = widgetChartBounds(snapshot.cgmPoints)
        lowPoints.forEach { point ->
            val x = geometry.plot.left + geometry.plot.width() * widgetChartXFraction(
                point.timestamp,
                END - WIDGET_CHART_WINDOW_SECONDS,
                END,
            )
            val y = geometry.plot.top + geometry.plot.height() * widgetChartYFraction(
                point.glucoseMgdl,
                bounds,
            )
            for (pixelY in (y - 5f).toInt()..(y + 5f).toInt()) {
                for (pixelX in (x - 5f).toInt()..(x + 5f).toInt()) {
                    assertEquals(
                        "CGM pixel must win at $pixelX,$pixelY",
                        unmarked.getPixel(pixelX, pixelY),
                        marked.getPixel(pixelX, pixelY),
                    )
                }
            }
        }
        unmarked.recycle()
        marked.recycle()
    }

    private fun snapshot(
        maximum: Int = 200,
        events: List<WidgetBolusEvent> = listOf(
            WidgetBolusEvent(END - 9_000L, 1.25f, false),
            WidgetBolusEvent(END - 4_800L, 0.65f, true),
        ),
        extraPoints: List<WidgetCgmPoint> = emptyList(),
    ): MobiControlWidgetSnapshot {
        val now = Instant.ofEpochSecond(END)
        val points = (0..48).map { index ->
            WidgetCgmPoint(END - (48 - index) * 300L, 110 + (index % 12) * 4)
        }.toMutableList().apply {
            add(WidgetCgmPoint(END - 600L, maximum))
            addAll(extraPoints)
        }.sortedBy(WidgetCgmPoint::timestamp)
        return MobiControlWidgetSnapshot(
            glucose = WidgetMetric("155", now.minusSeconds(45)),
            iob = WidgetMetric("3.00", now.minusSeconds(75)),
            glucoseUnit = GlucoseUnit.MGDL,
            chartEndTimestamp = END,
            cgmPoints = points,
            boluses = events,
            lowerControlLimitMgdl = 70,
            upperControlLimitMgdl = 180,
            colorLowDots = true,
            colorHighDots = true,
            trendArrow = WidgetMetric("▶", now.minusSeconds(45)),
        )
    }

    private fun geometry(snapshot: MobiControlWidgetSnapshot, width: Int, height: Int): Geometry {
        val actualWidth = width.coerceIn(320, 640)
        val actualHeight = height.coerceIn(120, 260)
        val labelSize = max(15f, actualHeight * 0.064f)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = labelSize }
        val timeLabelHeight = labelSize + 7f
        val bolusTrackHeight = max(25f, labelSize + 11f)
        val plot = RectF(
            labelPaint.measureText("400") + 11f,
            7f,
            actualWidth - 7f,
            actualHeight - timeLabelHeight - bolusTrackHeight,
        )
        val baseline = actualHeight - timeLabelHeight - 3f
        val y50 = plot.top + plot.height() * widgetChartYFraction(50, widgetChartBounds(snapshot.cgmPoints))
        return Geometry(plot, labelSize, baseline, y50)
    }

    private data class Geometry(
        val plot: RectF,
        val labelSize: Float,
        val baselineY: Float,
        val y50: Float,
    )

    companion object {
        private const val END = 1_788_890_400L
    }
}
