package com.jwoglom.controlx2.widget

import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.presentation.theme.AppSkin
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MobiControlCarGlanceWidgetTest {
    private val now = Instant.parse("2026-08-25T18:00:00Z")
    private val snapshot = MobiControlWidgetSnapshot(
        glucose = WidgetMetric("155", now.minusSeconds(120)),
        iob = WidgetMetric("3.0", now.minusSeconds(180)),
        glucoseUnit = GlucoseUnit.MGDL,
        chartEndTimestamp = now.epochSecond,
        cgmPoints = listOf(
            WidgetCgmPoint(now.minusSeconds(600).epochSecond, 148),
            WidgetCgmPoint(now.minusSeconds(300).epochSecond, 152),
            WidgetCgmPoint(now.epochSecond, 155),
        ),
        boluses = emptyList(),
        lowerControlLimitMgdl = 70,
        upperControlLimitMgdl = 180,
        colorLowDots = true,
        colorHighDots = true,
        trendArrow = WidgetMetric("▶", now.minusSeconds(120)),
        controlIqMode = WidgetMetric("EXERCISE", now.minusSeconds(120)),
    )

    @Test
    fun `compact car presentation composes`() = assertPresentation(
        presentation = CarWidgetPresentation.COMPACT,
        expectedTag = CAR_COMPACT_TEST_TAG,
        expectsChart = false,
    )

    @Test
    fun `wide car presentation composes`() = assertPresentation(
        presentation = CarWidgetPresentation.WIDE,
        expectedTag = CAR_WIDE_TEST_TAG,
        expectsChart = false,
    )

    @Test
    fun `large car presentation composes with history`() = assertPresentation(
        presentation = CarWidgetPresentation.LARGE,
        expectedTag = CAR_LARGE_TEST_TAG,
        expectsChart = true,
    )

    @Test
    fun `all car presentations compose with every app skin`() {
        AppSkin.entries.forEach { skin ->
            CarWidgetPresentation.entries.forEach { presentation ->
                runGlanceAppWidgetUnitTest {
                    provideComposable {
                        MobiControlCarWidgetContentForPresentation(
                            presentation = presentation,
                            snapshot = snapshot,
                            now = now,
                            appSkin = skin,
                        )
                    }
                    onNode(hasText("155")).assertExists()
                    onNode(hasText("LIVE")).assertExists()
                    onNode(hasText("Exercise")).assertExists()
                    // Exactly one background must own its explicit corner radius. Merely
                    // rounding an unmarked frame lets launchers apply a second corner mask.
                    onNode(GlanceNodeMatcher<MappedNode>("widget background") { node ->
                        node.value.emittable.modifier.any {
                            it == GlanceModifier.appWidgetBackground()
                        }
                    }).assert(GlanceNodeMatcher("explicit corner radius on background") { node ->
                        node.value.emittable.modifier.any {
                            it.javaClass == GlanceModifier.cornerRadius(0.dp).javaClass
                        }
                    })
                }
            }
        }
    }

    private fun assertPresentation(
        presentation: CarWidgetPresentation,
        expectedTag: String,
        expectsChart: Boolean,
    ) = runGlanceAppWidgetUnitTest {
        provideComposable {
            MobiControlCarWidgetContentForPresentation(presentation, snapshot, now)
        }

        onNode(hasTestTag(expectedTag)).assertExists()
        onNode(hasText("155")).assertExists()
        onNode(hasText("LIVE")).assertExists()
        onNode(hasText("Exercise")).assertExists()
        if (expectsChart) {
            onNode(hasTestTag(CAR_CHART_TEST_TAG)).assertExists()
        } else {
            onNode(hasTestTag(CAR_CHART_TEST_TAG)).assertDoesNotExist()
        }
    }
}
