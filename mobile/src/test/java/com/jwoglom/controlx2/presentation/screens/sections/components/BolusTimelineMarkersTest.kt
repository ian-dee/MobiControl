package com.jwoglom.controlx2.presentation.screens.sections.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BolusTimelineMarkersTest {
    @Test
    fun markersPreserveSourceAndUseExactTimelinePosition() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(100, 2.5f, false, "MANUAL"),
                BolusEvent(150, 0.45f, true, "CONTROL_IQ_AUTO_BOLUS"),
            ),
            startTimestamp = 0,
            endTimestamp = 200,
            minimumSeparationFraction = 0f,
        )

        assertEquals(listOf(0.5f, 0.75f), markers.map { it.anchorFraction })
        assertFalse(markers[0].event.isAutomated)
        assertTrue(markers[1].event.isAutomated)
    }

    @Test
    fun nearbyMarkersUseSeparateLanesAndDistantMarkersReuseFirstLane() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(10, 1f, false, "MANUAL"),
                BolusEvent(12, 0.4f, true, "AUTO"),
                BolusEvent(70, 2f, false, "MANUAL"),
            ),
            startTimestamp = 0,
            endTimestamp = 100,
            minimumSeparationFraction = 0.15f,
        )

        assertEquals(listOf(0, 1, 0), markers.map { it.lane })
    }

    @Test
    fun threeCloselyTimedBolusesUseThreeLanes() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(10, 1f, false, "MANUAL"),
                BolusEvent(11, 0.4f, true, "AUTO"),
                BolusEvent(12, 0.5f, true, "AUTO"),
            ),
            startTimestamp = 0,
            endTimestamp = 100,
            minimumSeparationFraction = 0.15f,
        )

        assertEquals(listOf(0, 1, 2), markers.map { it.lane })
    }

    @Test
    fun chronologicallyConnectedBolusStackKeepsEachNewerBolusHigher() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(0, 2.5f, false, "MANUAL"),
                BolusEvent(3L * 60L, 3f, false, "MANUAL"),
                BolusEvent(14L * 60L, 0.5f, false, "MANUAL"),
                BolusEvent(30L * 60L, 0.5f, false, "MANUAL"),
            ),
            startTimestamp = 0,
            endTimestamp = DASHBOARD_VIEWPORT_SECONDS,
        )

        assertEquals(listOf(0, 1, 2, 3), markers.map { it.lane })
        assertEquals(listOf(4, 4, 4, 4), markers.map { it.stackSize })
    }

    @Test
    fun invalidAndOutOfRangeBolusesAreNotRendered() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(-1, 1f, false, "MANUAL"),
                BolusEvent(50, 0f, false, "MANUAL"),
                BolusEvent(101, 1f, true, "AUTO"),
            ),
            startTimestamp = 0,
            endTimestamp = 100,
        )

        assertTrue(markers.isEmpty())
    }

    @Test
    fun bubbleValuesUseCompactUnitFormatting() {
        assertEquals("0.45u", formatBolusBubbleValue(0.45f))
        assertEquals("3.25u", formatBolusBubbleValue(3.25f))
        assertEquals("5u", formatBolusBubbleValue(5f))
    }

    @Test
    fun threeBubbleLanesFitAndPlaceNewestBolusHighest() {
        val offsets = bolusLaneTopOffsets(
            containerHeight = 72f,
            highestLane = 2,
        )

        assertEquals(52.4f, offsets[0], 0.001f)
        assertEquals(33.4f, offsets[1], 0.001f)
        assertEquals(14.4f, offsets[2], 0.001f)
        assertTrue(offsets.zipWithNext().all { (older, newer) -> older - newer >= 18f })
        assertTrue(offsets.first() + 18f <= 72f)
    }

    @Test
    fun isolatedBolusKeepsNormalTopPositionWhileNearbyStackUsesHierarchy() {
        val markers = layoutBolusTimelineMarkers(
            events = listOf(
                BolusEvent(10, 1f, false, "MANUAL"),
                BolusEvent(60, 2f, false, "MANUAL"),
                BolusEvent(61, 0.5f, true, "AUTO"),
                BolusEvent(62, 0.4f, true, "AUTO"),
            ),
            startTimestamp = 0,
            endTimestamp = 100,
            minimumSeparationFraction = 0.15f,
        )

        assertEquals(listOf(1, 3, 3, 3), markers.map { it.stackSize })
        val topOffsets = markers.map { marker ->
            bolusLaneTopOffsets(
                containerHeight = 72f,
                highestLane = marker.stackSize - 1,
            )[marker.lane]
        }
        assertEquals(14.4f, topOffsets[0], 0.001f)
        assertEquals(52.4f, topOffsets[1], 0.001f)
        assertEquals(33.4f, topOffsets[2], 0.001f)
        assertEquals(14.4f, topOffsets[3], 0.001f)
    }

    @Test
    fun denseBolusStackUsesEqualResponsiveOverlapWithNewestHighest() {
        val offsets = bolusLaneTopOffsets(
            containerHeight = 72f,
            highestLane = 5,
        )

        assertEquals(6, offsets.size)
        assertEquals(54f, offsets.first(), 0.001f)
        assertEquals(14.4f, offsets.last(), 0.001f)

        val upwardSteps = offsets.zipWithNext { older, newer -> older - newer }
        upwardSteps.forEach { step ->
            assertEquals(upwardSteps.first(), step, 0.001f)
            assertTrue(step < 18f)
        }
    }

}
