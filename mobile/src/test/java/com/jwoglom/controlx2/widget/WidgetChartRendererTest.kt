package com.jwoglom.controlx2.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetChartRendererTest {
    @Test
    fun `chart bounds retain 40 floor and round high data upward`() {
        assertEquals(
            WidgetChartBounds(40, 300),
            widgetChartBounds(listOf(WidgetCgmPoint(1, 273))),
        )
    }

    @Test
    fun `chart bounds keep a useful minimum range`() {
        assertEquals(
            WidgetChartBounds(40, 200),
            widgetChartBounds(listOf(WidgetCgmPoint(1, 105))),
        )
    }

    @Test
    fun `time positions clamp to the four hour window`() {
        assertEquals(0f, widgetChartXFraction(99, 100, 200), 0.0001f)
        assertEquals(0.5f, widgetChartXFraction(150, 100, 200), 0.0001f)
        assertEquals(1f, widgetChartXFraction(201, 100, 200), 0.0001f)
    }

    @Test
    fun `glucose positions run from bottom to top`() {
        val bounds = WidgetChartBounds(40, 240)
        assertEquals(1f, widgetChartYFraction(40, bounds), 0.0001f)
        assertEquals(0.5f, widgetChartYFraction(140, bounds), 0.0001f)
        assertEquals(0f, widgetChartYFraction(240, bounds), 0.0001f)
    }
}
