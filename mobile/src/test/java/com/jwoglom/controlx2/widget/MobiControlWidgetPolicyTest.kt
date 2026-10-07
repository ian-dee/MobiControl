package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import com.jwoglom.controlx2.WidgetCgmSessionPhase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiControlWidgetPolicyTest {
    @Test
    fun startupStateHidesPriorSessionCurrentGlucoseButKeepsHistory() {
        val snapshot = snapshot().copy(
            cgmSessionPhase = WidgetCgmSessionPhase.STARTUP,
            cgmStartupRemainingSeconds = 1_500L,
            currentGlucoseBelongsToSession = false,
            cgmPoints = listOf(WidgetCgmPoint(1L, 155)),
        )

        assertEquals("Startup", snapshot.glucoseDisplay())
        assertEquals("25 min remaining", snapshot.glucoseLabel())
        assertFalse(snapshot.displaysCurrentGlucose())
        assertTrue(snapshot.cgmPoints.isNotEmpty())
    }
    @Test
    fun `one ui two by one size stays compact`() {
        assertEquals(WidgetPresentation.COMPACT, widgetPresentation(176, 93))
    }

    @Test
    fun `one ui three by one size stays compact`() {
        assertEquals(WidgetPresentation.COMPACT, widgetPresentation(276, 93))
    }

    @Test
    fun `one ui three by two size exposes chart`() {
        assertEquals(WidgetPresentation.EXPANDED, widgetPresentation(276, 210))
    }

    @Test
    fun `fixed compact preset stays compact even in a larger host allocation`() {
        assertEquals(
            WidgetPresentation.COMPACT,
            resolvedWidgetPresentation(276, 210, WidgetPresentation.COMPACT),
        )
    }

    @Test
    fun `fixed history preset exposes chart even before launcher options settle`() {
        assertEquals(
            WidgetPresentation.EXPANDED,
            resolvedWidgetPresentation(180, 110, WidgetPresentation.EXPANDED),
        )
    }

    @Test
    fun `cover presets retain their dedicated presentations`() {
        assertEquals(
            WidgetPresentation.COVER_COMPACT,
            resolvedWidgetPresentation(150, 133, WidgetPresentation.COVER_COMPACT),
        )
        assertEquals(
            WidgetPresentation.COVER_WIDE,
            resolvedWidgetPresentation(312, 133, WidgetPresentation.COVER_WIDE),
        )
    }

    @Test
    fun `passive refresh fans out to all home and cover providers`() {
        assertEquals(
            listOf(
                MobiControlWidgetProvider::class.java,
                MobiControlWideWidgetProvider::class.java,
                MobiControlHistoryWidgetProvider::class.java,
                MobiControlCoverCompactWidgetProvider::class.java,
                MobiControlCoverWideWidgetProvider::class.java,
                MobiControlCarGlanceWidgetReceiver::class.java,
            ),
            MobiControlWidgetProvider.PROVIDER_CLASSES,
        )
    }

    @Test
    fun `car glance sizes select compact wide and large presentations`() {
        assertEquals(CarWidgetPresentation.COMPACT, carWidgetPresentation(CAR_WIDGET_COMPACT_SIZE))
        assertEquals(CarWidgetPresentation.WIDE, carWidgetPresentation(CAR_WIDGET_WIDE_SIZE))
        assertEquals(CarWidgetPresentation.LARGE, carWidgetPresentation(CAR_WIDGET_LARGE_SIZE))
    }

    @Test
    fun `wide and tall size exposes chart`() {
        assertEquals(WidgetPresentation.EXPANDED, widgetPresentation(320, 180))
    }

    @Test
    fun `wide but shallow size does not clip a chart`() {
        assertEquals(WidgetPresentation.COMPACT, widgetPresentation(360, 110))
    }

    @Test
    fun `freshness is reported independently for glucose and IOB`() {
        val now = Instant.parse("2026-08-21T20:00:00Z")
        val snapshot = snapshot(
            glucose = WidgetMetric("155", now.minusSeconds(120)),
            iob = WidgetMetric("3.0", now.minusSeconds(360)),
        )

        assertEquals("BG 2m  •  IOB 6m", snapshot.freshnessText(now))
        assertFalse(snapshot.glucose.isStale(now))
        assertFalse(snapshot.iob.isStale(now))
    }

    @Test
    fun `metric becomes stale after two CGM intervals`() {
        val now = Instant.parse("2026-08-21T20:00:00Z")
        assertTrue(WidgetMetric("155", now.minusSeconds(601)).isStale(now))
        assertFalse(WidgetMetric("155", now.minusSeconds(600)).isStale(now))
    }

    @Test
    fun `widget status distinguishes live stale partial and missing data`() {
        val now = Instant.parse("2026-08-21T20:00:00Z")
        assertEquals(
            WidgetDataStatus.LIVE,
            snapshot(
                glucose = WidgetMetric("155", now),
                iob = WidgetMetric("3", now),
            ).dataStatus(now),
        )
        assertEquals(
            WidgetDataStatus.STALE,
            snapshot(
                glucose = WidgetMetric("155", now.minusSeconds(601)),
                iob = WidgetMetric("3", now),
            ).dataStatus(now),
        )
        assertEquals(
            WidgetDataStatus.PARTIAL,
            snapshot(
                glucose = WidgetMetric("155", now),
                iob = WidgetMetric(null, null),
            ).dataStatus(now),
        )
        assertEquals(
            WidgetDataStatus.NO_DATA,
            snapshot(
                glucose = WidgetMetric(null, null),
                iob = WidgetMetric(null, null),
            ).dataStatus(now),
        )
    }

    @Test
    fun `display honors mmol preference without changing stored mgdl`() {
        val snapshot = snapshot(glucoseUnit = GlucoseUnit.MMOL)
        assertEquals("8.6", snapshot.glucoseDisplay())
        assertEquals("3.00", snapshot.iobDisplay())
    }

    @Test
    fun `fresh active same-session glucose displays its tandem trend arrow`() {
        val now = Instant.parse("2026-08-24T20:00:00Z")
        val snapshot = snapshot(
            glucose = WidgetMetric("159", now),
            trendArrow = WidgetMetric("▶", now),
        )

        assertEquals("▶", snapshot.trendArrowDisplay(now))
    }

    @Test
    fun `one second timestamp wobble still belongs to current widget session`() {
        assertTrue(
            widgetGlucoseBelongsToSession(
                phase = WidgetCgmSessionPhase.ACTIVE,
                sessionSensorStartedTimestamp = 590_003_156L,
                readingSensorStartedTimestamp = 590_003_157L,
            ),
        )
    }

    @Test
    fun `replacement and inactive states reject cached widget glucose`() {
        assertFalse(
            widgetGlucoseBelongsToSession(
                phase = WidgetCgmSessionPhase.ACTIVE,
                sessionSensorStartedTimestamp = 590_003_156L,
                readingSensorStartedTimestamp = 590_003_162L,
            ),
        )
        assertFalse(
            widgetGlucoseBelongsToSession(
                phase = WidgetCgmSessionPhase.STOPPED,
                sessionSensorStartedTimestamp = 590_003_156L,
                readingSensorStartedTimestamp = 590_003_156L,
            ),
        )
    }

    @Test
    fun `stale inactive and session-mismatched glucose suppress trend arrows`() {
        val now = Instant.parse("2026-08-24T20:00:00Z")
        val fresh = snapshot(
            glucose = WidgetMetric("159", now),
            trendArrow = WidgetMetric("▲", now),
        )

        assertNull(fresh.copy(glucose = WidgetMetric("159", now.minusSeconds(601))).trendArrowDisplay(now))
        assertNull(fresh.copy(cgmSessionPhase = WidgetCgmSessionPhase.STARTUP).trendArrowDisplay(now))
        assertNull(fresh.copy(currentGlucoseBelongsToSession = false).trendArrowDisplay(now))
        assertNull(fresh.copy(trendArrow = WidgetMetric(null, null)).trendArrowDisplay(now))
    }

    @Test
    fun `widget mode accepts only pump reported standard exercise and sleep values`() {
        assertEquals(WidgetControlIqMode.STANDARD, WidgetControlIqMode.fromPersistedValue("STANDARD"))
        assertEquals(WidgetControlIqMode.STANDARD, WidgetControlIqMode.fromPersistedValue("none"))
        assertEquals(WidgetControlIqMode.EXERCISE, WidgetControlIqMode.fromPersistedValue("exercise"))
        assertEquals(WidgetControlIqMode.SLEEP, WidgetControlIqMode.fromPersistedValue("Sleep"))
        assertNull(WidgetControlIqMode.fromPersistedValue("UNKNOWN"))
        assertNull(WidgetControlIqMode.fromPersistedValue(null))
    }

    private fun snapshot(
        glucose: WidgetMetric = WidgetMetric("155", Instant.EPOCH),
        iob: WidgetMetric = WidgetMetric("3", Instant.EPOCH),
        glucoseUnit: GlucoseUnit = GlucoseUnit.MGDL,
        trendArrow: WidgetMetric = WidgetMetric(null, null),
    ) = MobiControlWidgetSnapshot(
        glucose = glucose,
        iob = iob,
        glucoseUnit = glucoseUnit,
        chartEndTimestamp = 10_000,
        cgmPoints = emptyList(),
        boluses = emptyList(),
        lowerControlLimitMgdl = 70,
        upperControlLimitMgdl = 180,
        colorLowDots = true,
        colorHighDots = true,
        trendArrow = trendArrow,
    )
}
