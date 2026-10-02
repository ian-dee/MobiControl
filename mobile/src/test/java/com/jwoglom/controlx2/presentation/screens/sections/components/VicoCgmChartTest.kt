package com.jwoglom.controlx2.presentation.screens.sections.components

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.itemLruCache
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TimeChangedHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

class VicoCgmChartTest {
    @Before
    fun clearHistoryLogCache() {
        itemLruCache.evictAll()
    }

    private fun historyItem(pumpSid: Int, seqId: Long): HistoryLogItem = HistoryLogItem(
        seqId = seqId,
        pumpSid = pumpSid,
        typeId = 0,
        cargo = byteArrayOf(),
        pumpTimeSec = 0L,
        pumpTime = LocalDateTime.of(2008, 1, 1, 0, 0),
    )

    @Test
    fun toCgmDataPoint_appliesPumpLocalTimezoneCorrection() {
        val originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            val pumpWallClock = LocalDateTime.of(2026, 3, 11, 23, 54)
            val rawPumpInstant = pumpWallClock.toInstant(ZoneOffset.UTC)
            val pumpEpochStart = Instant.parse("2008-01-01T00:00:00Z")
            val pumpTimeSec = Duration.between(pumpEpochStart, rawPumpInstant).seconds

            val message = DexcomG6CGMHistoryLog(
                pumpTimeSec,
                9_042L,
                0,
                1,
                -2,
                6,
                -89,
                149,
                pumpTimeSec,
                481,
                0
            )
            val item = HistoryLogItem(message)

            val point = item.toCgmDataPoint()
            val formatter = SimpleDateFormat("h:mm a", Locale.US)

            assertEquals(149f, point?.value)
            assertEquals("11:54 PM", formatter.format(point!!.timestamp * 1000L))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun toCgmDataPoint_readsLibreFsl3ViaPumpX2Parse() {
        // tconnectsync FSL3_DATA_EVENT_1 (26 bytes); bytes 16–17 patched to LE 149 for PumpX2 1.8.8 (Python fixture had different field packing at 16–17).
        val cargo = byteArrayOf(
            0x01, 0xe0.toByte(), 0x22, 0x3d, 0x2d, 0xd9.toByte(), 0x00, 0x0b, 0xfc.toByte(),
            0x3e, 0x00, 0x00, 0x20, 0x00, 0x00, 0x95.toByte(),
            0x95.toByte(), 0x00,
            0x22, 0x3d, 0x2d, 0xd9.toByte(), 0x00, 0x00, 0x23, 0xe0.toByte()
        )
        val item = HistoryLogItem(
            seqId = 785470L,
            pumpSid = 0,
            typeId = CgmDataFsl3HistoryLog().typeId(),
            cargo = cargo,
            pumpTime = LocalDateTime.of(2026, 3, 15, 13, 12, 57)
        )

        val point = item.toCgmDataPoint()

        assertEquals(149f, point?.value)
    }

    @Test
    fun formatChartTimestamp_formatsUnixEpochSecondsDirectly() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        assertEquals("10:13 PM", formatChartTimestamp(1_700_000_000L, formatter))
    }

    @Test
    fun relativeTimeAxisTicks_useElapsedTimeAndPumpNowAtLatestRightEdge() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val historyEnd = 1_700_000_000L

        assertEquals(
            listOf(
                RelativeTimeAxisTick(0f, "4 hrs ago"),
                RelativeTimeAxisTick(0.5f, "2 hrs ago"),
                RelativeTimeAxisTick(1f, "10:13 PM"),
            ),
            relativeTimeAxisTicks(
                historyEndTimeSeconds = historyEnd,
                viewportStartTimeSeconds = historyEnd - DASHBOARD_VIEWPORT_SECONDS,
                viewportEndTimeSeconds = historyEnd,
                timeFormat = formatter,
            ),
        )
    }

    @Test
    fun relativeTimeAxisTicks_panAcrossTheSharedTwentyFourHourTimeline() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val historyEnd = 24L * 60L * 60L

        assertEquals(
            listOf(
                RelativeTimeAxisTick(0.25f, "10 hrs ago"),
                RelativeTimeAxisTick(0.75f, "8 hrs ago"),
            ),
            relativeTimeAxisTicks(
                historyEndTimeSeconds = historyEnd,
                viewportStartTimeSeconds = historyEnd - 11L * 60L * 60L,
                viewportEndTimeSeconds = historyEnd - 7L * 60L * 60L,
                timeFormat = formatter,
            ),
        )
    }

    @Test
    fun relativeTimeAxisTicks_reachTwentyFourHoursAtOldestViewport() {
        val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val historyEnd = 24L * 60L * 60L

        assertEquals(
            listOf(
                RelativeTimeAxisTick(0f, "24 hrs ago"),
                RelativeTimeAxisTick(0.5f, "22 hrs ago"),
                RelativeTimeAxisTick(1f, "20 hrs ago"),
            ),
            relativeTimeAxisTicks(
                historyEndTimeSeconds = historyEnd,
                viewportStartTimeSeconds = 0L,
                viewportEndTimeSeconds = DASHBOARD_VIEWPORT_SECONDS,
                timeFormat = formatter,
            ),
        )
    }

    @Test
    fun chartViewportStartsAtLatestAndPansTowardOlderHistory() {
        val historyEnd = 24L * 60L * 60L
        val pannedEnd = panChartViewportEndSeconds(
            currentVisibleEndSeconds = historyEnd.toDouble(),
            horizontalDragPixels = 50f,
            viewportWidthPixels = 100f,
            historyStartSeconds = 0L,
            historyEndSeconds = historyEnd,
        )

        assertEquals(22L * 60L * 60L, pannedEnd.toLong())
    }

    @Test
    fun chartViewportClampsAtBothEndsOfTwentyFourHourHistory() {
        val historyEnd = DASHBOARD_HISTORY_SECONDS
        val earliestVisibleEnd = DASHBOARD_VIEWPORT_SECONDS.toDouble()

        assertEquals(
            earliestVisibleEnd,
            panChartViewportEndSeconds(
                currentVisibleEndSeconds = earliestVisibleEnd,
                horizontalDragPixels = 1_000f,
                viewportWidthPixels = 100f,
                historyStartSeconds = 0L,
                historyEndSeconds = historyEnd,
            ),
            0.0,
        )
        assertEquals(
            historyEnd.toDouble(),
            panChartViewportEndSeconds(
                currentVisibleEndSeconds = historyEnd.toDouble(),
                horizontalDragPixels = -1_000f,
                viewportWidthPixels = 100f,
                historyStartSeconds = 0L,
                historyEndSeconds = historyEnd,
            ),
            0.0,
        )
    }

    @Test
    fun chartViewportSnapsToFiveMinuteOffsetsFromLatest() {
        val historyEnd = DASHBOARD_HISTORY_SECONDS

        assertEquals(
            (historyEnd - 10L * 60L).toDouble(),
            snapChartViewportEndSeconds(
                visibleEndSeconds = (historyEnd - 8L * 60L).toDouble(),
                historyStartSeconds = 0L,
                historyEndSeconds = historyEnd,
            ),
            0.0,
        )
    }

    @Test
    fun chartTimelineUsesEqualGuttersAndKeepsNowInsideTheContainer() {
        assertEquals(260f, chartTimelineWidth(containerWidth = 300f))
        assertEquals(
            20f,
            chartTimelineX(fraction = 0f, containerWidth = 300f),
        )
        assertEquals(
            280f,
            chartTimelineX(fraction = 1f, containerWidth = 300f),
        )
    }

    @Test
    fun glucoseAxisTickValues_usesFiftyMgdlIntervalsWithinVisibleRange() {
        assertEquals(
            listOf(50f, 100f, 150f, 200f, 250f, 300f, 350f, 400f),
            glucoseAxisTickValues(
                minimumGlucoseMgdl = 40f,
                maximumGlucoseMgdl = 400f,
            ),
        )
    }

    @Test
    fun glucoseAxisMinorTickValues_usesMidpointsBetweenFiftyMgdlTicks() {
        assertEquals(
            listOf(75f, 125f, 175f, 225f, 275f, 325f, 375f),
            glucoseAxisMinorTickValues(
                minimumGlucoseMgdl = 40f,
                maximumGlucoseMgdl = 400f,
            ),
        )
    }

    @Test
    fun cgmYAxisLabelValuesMgdl_usesRequestedSparseScale() {
        assertEquals(
            listOf(400, 200, 150, 100, 50),
            cgmYAxisLabelValuesMgdl(),
        )
    }

    @Test
    fun glucoseAxisTickValues_excludesTicksOutsideVisibleRange() {
        assertEquals(
            listOf(100f, 150f, 200f),
            glucoseAxisTickValues(
                minimumGlucoseMgdl = 75f,
                maximumGlucoseMgdl = 225f,
            ),
        )
    }

    @Test
    fun compactGlucoseScale_usesAThreeIntervalWindowForStableData() {
        assertEquals(
            CompactGlucoseScale(
                minimumMgdl = 50f,
                maximumMgdl = 200f,
                chartHeightDp = 60,
            ),
            compactGlucoseScale(listOf(145f, 155f, 165f)),
        )
    }

    @Test
    fun compactGlucoseScale_expandsForVolatileDataAndCapsItsHeight() {
        assertEquals(
            CompactGlucoseScale(
                minimumMgdl = 30f,
                maximumMgdl = 410f,
                chartHeightDp = 104,
            ),
            compactGlucoseScale(listOf(40f, 400f)),
        )
    }

    @Test
    fun compactGlucoseScale_usesReadableFallbackWhenNoValuesAreAvailable() {
        assertEquals(
            CompactGlucoseScale(
                minimumMgdl = 50f,
                maximumMgdl = 250f,
                chartHeightDp = 64,
            ),
            compactGlucoseScale(emptyList()),
        )
    }

    @Test
    fun cgmInteractionGeometry_changesAcrossFullCoverAndCompactScaleTransitions() {
        val standard = cgmInteractionGeometry(
            compactCover = false,
            minimumMgdl = 30f,
            maximumMgdl = 410f,
            compactChartHeightDp = 64,
        )
        val coverStable = cgmInteractionGeometry(
            compactCover = true,
            minimumMgdl = 50f,
            maximumMgdl = 200f,
            compactChartHeightDp = 60,
        )
        val coverVolatile = cgmInteractionGeometry(
            compactCover = true,
            minimumMgdl = 30f,
            maximumMgdl = 410f,
            compactChartHeightDp = 104,
        )

        assertEquals(CGM_CHART_HEIGHT_DP, standard.chartHeightDp)
        assertNotEquals(standard, coverStable)
        assertNotEquals(coverStable, coverVolatile)
    }

    @Test
    fun vicoInteractionGlucoseValue_insetsOnlyBitmapBoundaryValues() {
        val minimum = 30f
        val maximum = 410f

        assertTrue(vicoInteractionGlucoseValue(30.0, minimum, maximum) > minimum)
        assertTrue(vicoInteractionGlucoseValue(410.0, minimum, maximum) < maximum)
        assertEquals(40.0, vicoInteractionGlucoseValue(40.0, minimum, maximum), 0.0)
        assertEquals(400.0, vicoInteractionGlucoseValue(400.0, minimum, maximum), 0.0)
        assertEquals(220.0, vicoInteractionGlucoseValue(Double.NaN, minimum, maximum), 0.0)
    }

    @Test
    fun pumpChartTimeline_forwardClockChange_keepsEarlierEventAdjacent() {
        val beforeChange = DexcomG6CGMHistoryLog(
            10_000L, 10L, 0, 1, 0, 0, 0, 120, 10_000L, 0, 0,
        )
        val timeChange = TimeChangedHistoryLog(
            13_601L, 20L, 10_000_000L, 13_600_000L, 500L,
        )
        val timeline = PumpChartTimeline.from(
            listOf(HistoryLogItem(beforeChange), HistoryLogItem(timeChange)),
        )

        val beforeTimestamp = timeline.chartTimestamp(10L, 10_000L)
        val marker = timeline.markers().single()

        assertEquals(1L, marker.timestamp - beforeTimestamp)
        assertEquals(PumpClockChangeDirection.FORWARD, marker.direction)
        assertEquals(3_600L, timeline.maximumPositiveHistoricalCorrectionSeconds())
    }

    @Test
    fun pumpChartTimeline_backwardClockChange_doesNotLeaveEarlierEventInFuture() {
        val beforeChange = DexcomG6CGMHistoryLog(
            20_000L, 10L, 0, 1, 0, 0, 0, 120, 20_000L, 0, 0,
        )
        val timeChange = TimeChangedHistoryLog(
            16_401L, 20L, 20_000_000L, 16_400_000L, 500L,
        )
        val timeline = PumpChartTimeline.from(
            listOf(HistoryLogItem(beforeChange), HistoryLogItem(timeChange)),
        )

        val beforeTimestamp = timeline.chartTimestamp(10L, 20_000L)
        val marker = timeline.markers().single()

        assertEquals(1L, marker.timestamp - beforeTimestamp)
        assertEquals(PumpClockChangeDirection.BACKWARD, marker.direction)
        assertEquals(0L, timeline.maximumPositiveHistoricalCorrectionSeconds())
    }

    @Test
    fun pumpChartTimeline_combinesPairedDateAndTimeRecordsIntoOneChange() {
        val day = 24L * 60L * 60L
        val timeChange = TimeChangedHistoryLog(
            101L * day + 3_600L,
            20L,
            23L * 3_600L * 1_000L,
            3_600L * 1_000L,
            500L,
        )
        val dateChange = DateChangeHistoryLog(
            101L * day + 3_600L,
            21L,
            100L,
            101L,
            500L,
        )
        val timeline = PumpChartTimeline.from(
            listOf(HistoryLogItem(timeChange), HistoryLogItem(dateChange)),
        )

        val marker = timeline.markers().single()

        assertEquals(PumpClockChangeDirection.FORWARD, marker.direction)
        assertEquals(
            pumpTimeSecToChartTimestamp(101L * day + 3_600L),
            marker.destinationTimestamp,
        )
        assertEquals(2L * 3_600L, timeline.maximumPositiveHistoricalCorrectionSeconds())
    }

    @Test
    fun pumpChartTimeline_flip8Sync_convertsMillisecondDeltaToSeconds() {
        val beforeChange = DexcomG6CGMHistoryLog(
            587_940_038L, 2_267_982L, 0, 1, 0, 0, 0, 120, 587_940_038L, 0, 0,
        )
        val timeChange = TimeChangedHistoryLog(
            587_940_177L,
            2_268_009L,
            74_581_256L, // 20:43:01.256
            74_577_000L, // 20:42:57.000
            0L,
        )
        val timeline = PumpChartTimeline.from(
            listOf(HistoryLogItem(beforeChange), HistoryLogItem(timeChange)),
        )

        val uncorrected = pumpTimeSecToChartTimestamp(beforeChange.pumpTimeSec)
        val corrected = timeline.chartTimestamp(beforeChange.sequenceNum, beforeChange.pumpTimeSec)
        val marker = timeline.markers().single()

        assertEquals(-4L, corrected - uncorrected)
        assertEquals(PumpClockChangeDirection.BACKWARD, marker.direction)
        assertEquals(0L, timeline.maximumPositiveHistoricalCorrectionSeconds())
    }

    @Test
    fun pumpChartTimeline_timezoneChanges_preserveElapsedFiveMinuteSpacing() {
        val hourSeconds = 60L * 60L
        val daySeconds = 24L * hourSeconds
        val fiveMinutes = 5L * 60L
        val beforePumpTimeSec = 1_000_000L

        data class ClockChange(
            val shiftHours: Int,
            val timePriorHours: Int,
            val timeAfterHours: Int,
            val dateDeltaDays: Long = 0L,
        )

        val changes = listOf(
            ClockChange(shiftHours = 1, timePriorHours = 12, timeAfterHours = 13),
            ClockChange(shiftHours = -1, timePriorHours = 12, timeAfterHours = 11),
            ClockChange(shiftHours = 5, timePriorHours = 12, timeAfterHours = 17),
            ClockChange(shiftHours = -5, timePriorHours = 12, timeAfterHours = 7),
            ClockChange(shiftHours = 13, timePriorHours = 18, timeAfterHours = 7, dateDeltaDays = 1),
            ClockChange(shiftHours = -13, timePriorHours = 7, timeAfterHours = 18, dateDeltaDays = -1),
        )

        changes.forEach { change ->
            val shiftSeconds = change.shiftHours * hourSeconds
            val timeChangePumpTimeSec = beforePumpTimeSec + fiveMinutes + shiftSeconds
            val beforeChange = DexcomG6CGMHistoryLog(
                beforePumpTimeSec,
                10L,
                0,
                1,
                0,
                0,
                0,
                120,
                beforePumpTimeSec,
                0,
                0,
            )
            val timeChange = TimeChangedHistoryLog(
                timeChangePumpTimeSec,
                20L,
                change.timePriorHours * hourSeconds * 1_000L,
                change.timeAfterHours * hourSeconds * 1_000L,
                0L,
            )
            val records = mutableListOf(
                HistoryLogItem(beforeChange),
                HistoryLogItem(timeChange),
            )
            if (change.dateDeltaDays != 0L) {
                records += HistoryLogItem(
                    DateChangeHistoryLog(
                        timeChangePumpTimeSec,
                        21L,
                        100L,
                        100L + change.dateDeltaDays,
                        0L,
                    ),
                )
            }

            val timeline = PumpChartTimeline.from(records)
            val correctedBefore = timeline.chartTimestamp(
                beforeChange.sequenceNum,
                beforeChange.pumpTimeSec,
            )
            val changeMarker = timeline.markers().single()

            assertEquals(
                "${change.shiftHours}h clock change must preserve five-minute elapsed spacing",
                fiveMinutes,
                changeMarker.timestamp - correctedBefore,
            )
            assertEquals(
                "${change.shiftHours}h clock change must preserve five-minute site age",
                fiveMinutes,
                timeline.elapsedSecondsSince(
                    seqId = beforeChange.sequenceNum,
                    historicalPumpTimeSec = beforeChange.pumpTimeSec,
                    currentPumpTimeSec = timeChangePumpTimeSec,
                ),
            )
        }
    }

    @Test
    fun snapTimestampToBucket_roundsToNearestBucket() {
        assertEquals(
            1_000_000_300L,
            snapTimestampToBucket(
                timestampSeconds = 1_000_000_389L,
                startTimeSeconds = 1_000_000_000L,
                bucketSeconds = 300L,
                bucketCount = 13
            )
        )
        assertEquals(
            1_000_000_600L,
            snapTimestampToBucket(
                timestampSeconds = 1_000_000_451L,
                startTimeSeconds = 1_000_000_000L,
                bucketSeconds = 300L,
                bucketCount = 13
            )
        )
    }

    @Test
    fun cgmBucketTimestampsCoveredByBolusPills_includesCenterAndOneNeighborEachSide() {
        assertEquals(
            setOf(1_000L, 1_300L, 1_600L),
            cgmBucketTimestampsCoveredByBolusPills(
                cgmTimestamps = listOf(1_389L),
                startTimeSeconds = 1_000L,
                bucketSeconds = 300L,
                bucketCount = 4,
            ),
        )
    }

    @Test
    fun basalHistoryQueryCutoff_staysStableInsideFiveMinuteBucket() {
        assertEquals(9_900L, basalHistoryQueryCutoff(10_000L))
        assertEquals(9_900L, basalHistoryQueryCutoff(10_199L))
        assertEquals(10_200L, basalHistoryQueryCutoff(10_200L))
    }

    @Test
    fun selectBasalHistory_preservesSamePumpCompletedRowsWhileReplacementLoads() {
        val retained = RetainedBasalHistory(
            pumpSid = 68,
            items = listOf(historyItem(68, 1L), historyItem(68, 2L)),
        )

        val selection = selectBasalHistory(
            currentPumpSid = 68,
            observedItems = null,
            retained = retained,
        )

        assertEquals(BasalHistoryLoadSource.RETAINED_WHILE_LOADING, selection.source)
        assertEquals(listOf(1L, 2L), selection.items.map(HistoryLogItem::seqId))
        assertEquals(retained, selection.retained)
    }

    @Test
    fun selectBasalHistory_completedEmptyResultReplacesRetainedRows() {
        val retained = RetainedBasalHistory(
            pumpSid = 68,
            items = listOf(historyItem(68, 1L)),
        )

        val selection = selectBasalHistory(
            currentPumpSid = 68,
            observedItems = emptyList(),
            retained = retained,
        )

        assertEquals(BasalHistoryLoadSource.LIVE_EMPTY, selection.source)
        assertTrue(selection.items.isEmpty())
        assertEquals(68, selection.retained?.pumpSid)
        assertTrue(selection.retained?.items?.isEmpty() == true)
    }

    @Test
    fun selectBasalHistory_rejectsMismatchedPumpObservation() {
        val retained = RetainedBasalHistory(
            pumpSid = 68,
            items = listOf(historyItem(68, 10L)),
        )

        val selection = selectBasalHistory(
            currentPumpSid = 68,
            observedItems = listOf(historyItem(71, 11L)),
            retained = retained,
        )

        assertEquals(BasalHistoryLoadSource.MISMATCHED_OBSERVATION, selection.source)
        assertEquals(listOf(10L), selection.items.map(HistoryLogItem::seqId))
        assertEquals(retained, selection.retained)
    }

    @Test
    fun selectBasalHistory_doesNotCarryRetainedRowsAcrossPumpIdentityChange() {
        val selection = selectBasalHistory(
            currentPumpSid = 71,
            observedItems = null,
            retained = RetainedBasalHistory(
                pumpSid = 68,
                items = listOf(historyItem(68, 10L)),
            ),
        )

        assertEquals(BasalHistoryLoadSource.LOADING, selection.source)
        assertTrue(selection.items.isEmpty())
        assertNull(selection.retained)
    }

    @Test
    fun selectBasalHistory_invalidPumpClearsRetainedRows() {
        val selection = selectBasalHistory(
            currentPumpSid = -1,
            observedItems = null,
            retained = RetainedBasalHistory(
                pumpSid = 68,
                items = listOf(historyItem(68, 10L)),
            ),
        )

        assertEquals(BasalHistoryLoadSource.INVALID_PUMP, selection.source)
        assertTrue(selection.items.isEmpty())
        assertNull(selection.retained)
    }

    @Test
    fun selectBasalHistory_acceptsCompletedRowsForNewPump() {
        val newPumpRows = listOf(historyItem(71, 20L), historyItem(71, 21L))

        val selection = selectBasalHistory(
            currentPumpSid = 71,
            observedItems = newPumpRows,
            retained = RetainedBasalHistory(
                pumpSid = 68,
                items = listOf(historyItem(68, 10L)),
            ),
        )

        assertEquals(BasalHistoryLoadSource.LIVE, selection.source)
        assertEquals(listOf(20L, 21L), selection.items.map(HistoryLogItem::seqId))
        assertEquals(71, selection.retained?.pumpSid)
    }

    @Test
    fun toBasalDataPoint_usesBaseRateWhenResumeLikeEventHasZeroCommandRate() {
        val event = BasalRateChangeHistoryLog(
            1_000_000L,
            42L,
            0f,
            0.85f,
            3.0f,
            1,
            8
        )

        val point = event.toBasalDataPoint()

        assertEquals(0.85f, point?.rate)
        assertFalse(point?.isTemp ?: true)
    }

    @Test
    fun activeBasalAtTimestamp_returnsMostRecentEarlierBasalRate() {
        val basalPoints = listOf(
            BasalDataPoint(timestamp = 1_000L, rate = 1.2f, isTemp = false, duration = null),
            BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null)
        )

        val point = activeBasalAtTimestamp(basalPoints, 1_100L)

        assertEquals(1.2f, point?.rate)
    }

    @Test
    fun activeBasalAtTimestamp_fallsBackToEarliestKnownBasalBeforeFirstPoint() {
        val basalPoints = listOf(
            BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
            BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
        )

        val point = activeBasalAtTimestamp(basalPoints, 1_000L)

        assertNull(point)
    }

    @Test
    fun buildBasalIntervals_doesNotBackfillWithoutPreWindowBasalPoint() {
        val intervals = buildBasalIntervals(
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            basalDataPoints = listOf(
                BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
            )
        )

        assertEquals(2, intervals.size)
        assertEquals(1_200L, intervals[0].startTimestamp)
        assertEquals(1_400L, intervals[0].endTimestamp)
        assertEquals(1.4f, intervals[0].rate)
        assertEquals(1_400L, intervals[1].startTimestamp)
        assertEquals(1_600L, intervals[1].endTimestamp)
        assertEquals(1.6f, intervals[1].rate)
    }

    @Test
    fun buildBasalIntervals_backfillsFromChartStartWhenLeadInBasalPointExists() {
        val intervals = buildBasalIntervals(
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            basalDataPoints = listOf(
                BasalDataPoint(timestamp = 900L, rate = 1.2f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_200L, rate = 1.4f, isTemp = false, duration = null),
                BasalDataPoint(timestamp = 1_400L, rate = 1.6f, isTemp = false, duration = null)
            )
        )

        assertEquals(3, intervals.size)
        assertEquals(1_000L, intervals[0].startTimestamp)
        assertEquals(1_200L, intervals[0].endTimestamp)
        assertEquals(1.2f, intervals[0].rate)
        assertEquals(1_200L, intervals[1].startTimestamp)
        assertEquals(1_400L, intervals[1].endTimestamp)
        assertEquals(1.4f, intervals[1].rate)
        assertEquals(1_400L, intervals[2].startTimestamp)
        assertEquals(1_600L, intervals[2].endTimestamp)
        assertEquals(1.6f, intervals[2].rate)
    }

    @Test
    fun formatBasalRateValue_requiresOneDecimalAndKeepsUsefulPrecision() {
        assertEquals("0.0", formatBasalRateValue(0f))
        assertEquals("0.5", formatBasalRateValue(0.5f))
        assertEquals("0.15", formatBasalRateValue(0.15f))
        assertEquals("1.0", formatBasalRateValue(1f))
        assertEquals("0.5 u/hr", formatBasalRate(0.5f))
    }

    @Test
    fun layoutBasalRateMarkers_labelsOnlyActualRateChangesAndUnitsOnlyFirst() {
        val intervals = listOf(
            BasalInterval(1_000L, 1_100L, 0.5f, false),
            BasalInterval(1_100L, 1_200L, 0.5f, false),
            BasalInterval(1_200L, 1_400L, 0.15f, true),
            BasalInterval(1_400L, 1_600L, 0f, false),
        )

        val markers = layoutBasalRateMarkers(
            intervals = intervals,
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            containerWidthDp = 400f,
            chartHeightDp = 72f,
            basalMaxUnitsPerHour = 5f,
        )

        assertEquals(listOf("0.5 u/hr", "0.15", "0.0"), markers.map { it.text })
        assertEquals(listOf(0, 2, 3), markers.map { it.intervalIndex })
        assertEquals(listOf(false, false, true), markers.map { it.isZero })
    }

    @Test
    fun layoutBasalRateMarkers_clampsLabelsInsideTheTimeline() {
        val markers = layoutBasalRateMarkers(
            intervals = listOf(
                BasalInterval(1_000L, 1_590L, 0.5f, false),
                BasalInterval(1_590L, 1_600L, 1f, false),
            ),
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            containerWidthDp = 300f,
            chartHeightDp = 72f,
            basalMaxUnitsPerHour = 5f,
        )

        assertEquals(2, markers.size)
        markers.forEach { marker ->
            assertTrue(marker.leftDp >= CHART_MARKER_GUTTER_DP)
            assertTrue(marker.leftDp + marker.widthDp <= 300f - CHART_MARKER_GUTTER_DP + 0.001f)
        }
    }

    @Test
    fun layoutBasalRateMarkers_prioritizesExplicitZeroDuringCollision() {
        val markers = layoutBasalRateMarkers(
            intervals = listOf(
                BasalInterval(1_000L, 1_030L, 0.05f, false),
                BasalInterval(1_030L, 1_060L, 0.04f, false),
                BasalInterval(1_060L, 1_090L, 0f, false),
                BasalInterval(1_090L, 1_600L, 0.03f, false),
            ),
            startTimeSeconds = 1_000L,
            currentTimeSeconds = 1_600L,
            containerWidthDp = 120f,
            chartHeightDp = 72f,
            basalMaxUnitsPerHour = 5f,
        )

        assertTrue(markers.any { it.isZero && it.text == "0.0" })
        assertTrue(markers.none { it.intervalIndex == 0 })
    }

    @Test
    fun hoverTimestampForPosition_snapsToSharedBucketGrid() {
        assertEquals(
            1_000_000_300L,
            hoverTimestampForPosition(
                x = 50f,
                width = 100f,
                startTimeSeconds = 1_000_000_000L,
                currentTimeSeconds = 1_000_000_600L,
                bucketSeconds = 300L,
                bucketCount = 3
            )
        )
    }

    @Test
    fun buildBolusGlucoseMarkers_usesNearestCgmReadingWithinSensorInterval() {
        val bolus = BolusEvent(
            timestamp = 1_300L,
            units = 2.5f,
            isAutomated = false,
            bolusType = "STANDARD",
        )

        val markers = buildBolusGlucoseMarkers(
            bolusEvents = listOf(bolus),
            cgmDataPoints = listOf(
                CgmDataPoint(timestamp = 1_000L, value = 126f),
                CgmDataPoint(timestamp = 1_290L, value = 141f),
                CgmDataPoint(timestamp = 1_600L, value = 139f),
            ),
        )

        assertEquals(1, markers.size)
        assertEquals(bolus, markers.single().event)
        assertEquals(1_290L, markers.single().cgmTimestamp)
        assertEquals(141f, markers.single().glucoseMgdl)
    }

    @Test
    fun buildBolusGlucoseMarkers_doesNotInventReadingAcrossCgmGap() {
        val markers = buildBolusGlucoseMarkers(
            bolusEvents = listOf(
                BolusEvent(
                    timestamp = 2_000L,
                    units = 0.6f,
                    isAutomated = true,
                    bolusType = "CONTROL_IQ_AUTO_BOLUS",
                ),
            ),
            cgmDataPoints = listOf(
                CgmDataPoint(timestamp = 1_400L, value = 162f),
            ),
        )

        assertEquals(emptyList<BolusGlucoseMarker>(), markers)
    }
}
