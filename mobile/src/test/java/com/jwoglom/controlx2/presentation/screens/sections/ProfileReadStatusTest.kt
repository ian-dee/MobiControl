package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReadStatusTest {
    @Test
    fun reportsEachRealPumpReadMilestoneBeforeCompletion() {
        val manager = IDPManager()
        var progress = ProfileReadProgress()

        assertEquals(ProfileReadPhase.CHECKING_PUMP, profileReadStatus(manager, progress).phase)

        progress = process(
            manager,
            progress,
            ProfileStatusResponse(2, 0, 1, -1, -1, -1, -1, 0),
        )
        val initialSettings = profileReadStatus(manager, progress)
        assertEquals(ProfileReadPhase.READING_PROFILE_SETTINGS, initialSettings.phase)
        assertEquals(0, initialSettings.profilesRead)
        assertEquals(2, initialSettings.totalProfiles)

        progress = process(manager, progress, IDPSettingsResponse(0, "Weekday", 2, 300, 15_000, true))
        val oneProfile = profileReadStatus(manager, progress)
        assertEquals(ProfileReadPhase.READING_PROFILE_SETTINGS, oneProfile.phase)
        assertEquals("Profile details: 1 of 2 received.", oneProfile.detail)

        progress = process(manager, progress, IDPSettingsResponse(1, "Weekend", 1, 300, 15_000, true))
        val segmentRead = profileReadStatus(manager, progress)
        assertEquals(ProfileReadPhase.READING_TIME_SEGMENTS, segmentRead.phase)
        assertEquals(0, segmentRead.segmentsRead)
        assertEquals(3, segmentRead.totalSegments)
        assertFalse(segmentRead.complete)
    }

    @Test
    fun onlyReportsCompleteAfterEveryProfileSegmentArrives() {
        val manager = IDPManager()
        var progress = ProfileReadProgress()
        val messages = listOf(
            ProfileStatusResponse(1, 0, -1, -1, -1, -1, -1, 0),
            IDPSettingsResponse(0, "Daily", 2, 300, 15_000, true),
            IDPSegmentResponse(0, 0, 0, 500, 6_000, 110, 25, 31),
        )
        messages.forEach { progress = process(manager, progress, it) }

        assertFalse(manager.isComplete)
        assertEquals(ProfileReadPhase.READING_TIME_SEGMENTS, profileReadStatus(manager, progress).phase)

        progress = process(
            manager,
            progress,
            IDPSegmentResponse(0, 1, 720, 600, 7_000, 110, 30, 31),
        )

        val complete = profileReadStatus(manager, progress)
        assertTrue(manager.isComplete)
        assertTrue(complete.complete)
        assertEquals(ProfileReadPhase.COMPLETE, complete.phase)
        assertEquals("Profiles loaded from pump", complete.title)
        assertTrue(complete.detail.contains("1 profile"))
        assertTrue(complete.detail.contains("2 time segments"))
        assertTrue(complete.detail.contains("Active: Daily"))
    }

    @Test
    fun reportsAnEmptyPumpAsACompletedRead() {
        val manager = IDPManager()
        val response = ProfileStatusResponse(0, -1, -1, -1, -1, -1, -1, 0)
        manager.processMessage(response)
        val progress = ProfileReadProgress().process(response)

        val status = profileReadStatus(manager, progress)
        assertTrue(manager.isComplete)
        assertTrue(status.complete)
        assertEquals(ProfileReadPhase.EMPTY, status.phase)
        assertEquals("Pump reports no profiles", status.title)
    }

    @Test
    fun progressDoesNotDoubleCountDuplicatePumpResponses() {
        val settings = IDPSettingsResponse(0, "Daily", 1, 300, 15_000, true)
        val segment = IDPSegmentResponse(0, 0, 0, 500, 6_000, 110, 25, 31)
        val progress = ProfileReadProgress(totalProfiles = 1)
            .process(settings)
            .process(settings)
            .process(segment)
            .process(segment)

        assertEquals(1, progress.profilesRead)
        assertEquals(1, progress.segmentsRead)
    }

    private fun process(
        manager: IDPManager,
        progress: ProfileReadProgress,
        message: Message,
    ): ProfileReadProgress {
        manager.processMessage(message)
        return progress.process(message)
    }
}
