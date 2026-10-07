package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DeleteIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RenameIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSettingsRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileModelsTest {
    @Test
    fun syntheticConstantRateProfileHasExpectedDailyBasal() {
        assertEquals(14.40, totalDailyBasalUnits(SYNTHETIC_DEVELOPER_PROFILE.segments), 0.0001)
    }

    @Test
    fun syntheticProfileSelectsTheExpectedEveningSegment() {
        assertEquals(
            7,
            activeProfileSegmentIndex(
                SYNTHETIC_DEVELOPER_PROFILE.segments,
                20 * 60 + 47,
            ),
        )
    }

    @Test
    fun newSegmentUsesTheChronologicallyPrecedingSegmentsSettings() {
        val segments = listOf(
            ProfileSegmentSnapshot(0, 0, 500, 6_000, 110, 25),
            ProfileSegmentSnapshot(1, 6 * 60, 650, 7_000, 105, 30),
            ProfileSegmentSnapshot(2, 18 * 60, 800, 8_000, 100, 35),
        )

        assertEquals(
            segments[0],
            precedingProfileSegmentForStartTime(segments, 5 * 60 + 45),
        )
        assertEquals(
            segments[1],
            precedingProfileSegmentForStartTime(segments, 12 * 60),
        )
        assertEquals(
            segments[2],
            precedingProfileSegmentForStartTime(segments, 23 * 60 + 59),
        )
    }

    @Test
    fun newSegmentAutofillWrapsAcrossMidnightAndSkipsAnExactConflict() {
        val segments = listOf(
            ProfileSegmentSnapshot(0, 0, 500, 6_000, 110, 25),
            ProfileSegmentSnapshot(1, 6 * 60, 650, 7_000, 105, 30),
            ProfileSegmentSnapshot(2, 18 * 60, 800, 8_000, 100, 35),
        )

        assertEquals(segments[2], precedingProfileSegmentForStartTime(segments, 0))
        assertEquals(segments[0], precedingProfileSegmentForStartTime(segments, 6 * 60))
        assertNull(precedingProfileSegmentForStartTime(emptyList(), 12 * 60))
    }

    @Test
    fun profileDisplayValuesRoundTripThroughExactIntegers() {
        assertEquals(6_000L, profileRatioToPumpValue("6"))
        assertEquals("6", formatProfileRatio(6_000L))
        assertEquals(650, basalRateToPumpValue("0.65"))
        assertEquals("0.65", formatProfileBasal(650))
        assertEquals(1_500, basalRateToPumpValue("1.5"))
        assertEquals(15_000, basalRateToPumpValue("15"))
    }

    @Test
    fun onePointFiveAndFifteenSerializeToDifferentExactPumpValues() {
        val onePointFive = CreateIDPRequest("1.5 Fixture", 6_000, basalRateToPumpValue("1.5")!!, 110, 25, 300, 1)
        val fifteen = CreateIDPRequest("15 Fixture", 6_000, basalRateToPumpValue("15")!!, 110, 25, 300, 1)

        assertEquals(1_500, onePointFive.firstSegmentProfileBasalRate)
        assertEquals(15_000, fifteen.firstSegmentProfileBasalRate)
        assertArrayEquals(byteArrayOf(0xDC.toByte(), 0x05), onePointFive.cargo.copyOfRange(23, 25))
        assertArrayEquals(byteArrayOf(0x98.toByte(), 0x3A), fifteen.cargo.copyOfRange(23, 25))
    }

    @Test
    fun conversionRejectsAmbiguousOrOverPreciseInputsInsteadOfDefaulting() {
        assertNull(profileRatioToPumpValue(""))
        assertNull(profileRatioToPumpValue("six"))
        assertNull(profileRatioToPumpValue("6.0001"))
        assertNull(basalRateToPumpValue("1.5001"))
    }

    @Test
    fun createProfileCommandCarriesExpectedThousandthsAndBytes() {
        val request = CreateIDPRequest(
            "Fixture",
            profileRatioToPumpValue("6")!!.toInt(),
            basalRateToPumpValue("0.65")!!,
            110,
            20,
            300,
            1,
        )

        assertEquals(6_000, request.firstSegmentProfileCarbRatio)
        assertEquals(650, request.firstSegmentProfileBasalRate)
        assertArrayEquals(
            byteArrayOf(0x70, 0x17, 0x00, 0x00),
            request.cargo.copyOfRange(17, 21),
        )
        assertArrayEquals(
            byteArrayOf(0x8A.toByte(), 0x02),
            request.cargo.copyOfRange(23, 25),
        )
    }

    @Test
    fun modifySegmentCommandPreservesIndexAndExactIntegerValues() {
        val request = SetIDPSegmentRequest(
            0,
            0,
            7,
            SetIDPSegmentRequest.IDPSegmentOperation.MODIFY_SEGMENT_ID,
            19 * 60 + 30,
            basalRateToPumpValue("0.5")!!,
            profileRatioToPumpValue("6")!!,
            110,
            20,
            31,
        )

        assertEquals(SetIDPSegmentRequest.IDPSegmentOperation.MODIFY_SEGMENT_ID, request.operation)
        assertEquals(7, request.segmentIndex)
        assertEquals(500, request.profileBasalRate)
        assertEquals(6_000L, request.profileCarbRatio)
        assertArrayEquals(
            byteArrayOf(0x70, 0x17, 0x00, 0x00),
            request.cargo.copyOfRange(8, 12),
        )
    }

    @Test
    fun createAndDeleteSegmentCommandsUseExplicitOperationsAndSlot() {
        val create = SetIDPSegmentRequest(
            4,
            2,
            0,
            SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT,
            7 * 60,
            700,
            6_000,
            110,
            20,
            31,
        )
        val delete = SetIDPSegmentRequest(
            4,
            2,
            3,
            SetIDPSegmentRequest.IDPSegmentOperation.DELETE_SEGMENT_ID,
            7 * 60,
            700,
            6_000,
            110,
            20,
            31,
        )

        assertEquals(2, create.profileIndex)
        assertEquals(SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT, create.operation)
        assertEquals(SetIDPSegmentRequest.IDPSegmentOperation.DELETE_SEGMENT_ID, delete.operation)
        assertEquals(3, delete.segmentIndex)
        assertEquals(700, create.profileBasalRate)
        assertEquals(6_000L, create.profileCarbRatio)
    }

    @Test
    fun profileLifecycleCommandsCarryIdAndSlotWithoutUnitConversion() {
        val rename = RenameIDPRequest(4, 2, "Weekend")
        val activate = SetActiveIDPRequest(4, 2)
        val delete = DeleteIDPRequest(4, 2)

        assertEquals(4, rename.idpId)
        assertEquals(2, rename.profileIndex)
        assertEquals("Weekend", rename.profileName)
        assertEquals(4, activate.idpId)
        assertEquals(2, activate.profileIndex)
        assertEquals(4, delete.idpId)
        assertEquals(2, delete.profileIndex)
    }

    @Test
    fun bolusSettingsCommandsAreSeparatedByChangeType() {
        val duration = SetIDPSettingsRequest(
            4,
            2,
            300,
            1,
            SetIDPSettingsRequest.ChangeType.CHANGE_INSULIN_DURATION,
        )
        val carbEntry = SetIDPSettingsRequest(
            4,
            2,
            300,
            0,
            SetIDPSettingsRequest.ChangeType.CHANGE_CARB_ENTRY,
        )

        assertEquals(2, duration.profileIndex)
        assertEquals(300, duration.profileInsulinDuration)
        assertEquals(SetIDPSettingsRequest.ChangeType.CHANGE_INSULIN_DURATION, duration.changeType)
        assertEquals(0, carbEntry.profileCarbEntry)
        assertEquals(SetIDPSettingsRequest.ChangeType.CHANGE_CARB_ENTRY, carbEntry.changeType)
    }

    @Test
    fun syntheticProfileSurvivesManagerProtocolRoundTrip() {
        val manager = profileManagerFromSnapshots(
            listOf(SYNTHETIC_DEVELOPER_PROFILE),
            minuteOfDay = 20 * 60 + 47,
        )
        val roundTrip = profileSnapshots(manager).single()

        assertEquals(SYNTHETIC_DEVELOPER_PROFILE, roundTrip)
        assertEquals(10_000L, roundTrip.segments[7].carbRatioMilliunits)
        assertEquals(600, roundTrip.segments[7].basalMilliunitsPerHour)
    }

    @Test
    fun pumpProfileAndTimeSegmentLimitsUseTandemBoundaries() {
        val fiveProfiles = List(5) { SYNTHETIC_DEVELOPER_PROFILE.copy(idpId = it, slotIndex = it) }
        val sixProfiles = List(6) { SYNTHETIC_DEVELOPER_PROFILE.copy(idpId = it, slotIndex = it) }
        val fifteenSegments = SYNTHETIC_DEVELOPER_PROFILE.copy(
            segments = List(15) { index ->
                SYNTHETIC_DEVELOPER_PROFILE.segments.first().copy(
                    index = index,
                    startMinutes = index * 90,
                )
            },
        )
        val sixteenSegments = fifteenSegments.copy(
            segments = fifteenSegments.segments +
                fifteenSegments.segments.first().copy(index = 15, startMinutes = 15 * 90),
        )

        assertTrue(canAddProfile(fiveProfiles))
        assertFalse(canAddProfile(sixProfiles))
        assertTrue(canAddProfileSegment(fifteenSegments))
        assertFalse(canAddProfileSegment(sixteenSegments))
    }

    @Test
    fun controlIqLocksInsulinDurationAndRequiresCarbohydrates() {
        val policy = profileBolusSettingsPolicy(true, SYNTHETIC_DEVELOPER_PROFILE)

        assertEquals(300, policy.displayedDurationMinutes)
        assertFalse(policy.durationEditable)
        assertTrue(policy.carbohydratesRequired)
        assertFalse(policy.carbohydratesEditable)
    }

    @Test
    fun controlIqOffAllowsProfileBolusSettingsToBeEdited() {
        val policy = profileBolusSettingsPolicy(false, SYNTHETIC_DEVELOPER_PROFILE)

        assertEquals(SYNTHETIC_DEVELOPER_PROFILE.insulinDurationMinutes, policy.displayedDurationMinutes)
        assertTrue(policy.durationEditable)
        assertTrue(policy.carbohydratesEditable)
        assertFalse(policy.carbohydratesRequired)
    }

    @Test
    fun carbohydratesOffProfilePromptsForControlIqOffBeforeActivation() {
        val profile = SYNTHETIC_DEVELOPER_PROFILE.copy(active = false, carbEntryEnabled = false)

        assertEquals(
            "Turn off Control-IQ before activating a profile with Carbohydrates turned off.",
            profileActivationValidationError(true, profile),
        )
        assertNull(profileActivationValidationError(false, profile))
    }

    @Test
    fun carbohydratesOffActiveProfileBlocksEnablingControlIq() {
        val profile = SYNTHETIC_DEVELOPER_PROFILE.copy(carbEntryEnabled = false)

        assertEquals(
            "Turn on Carbohydrates in the active Personal Profile before enabling Control-IQ.",
            controlIqEnableProfileValidationError(true, profile),
        )
        assertNull(controlIqEnableProfileValidationError(false, profile))
    }
}
