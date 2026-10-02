package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.Prefs
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProfileCacheTest {
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun verifiedPumpSnapshotRoundTripsWithoutChangingExactPumpUnits() {
        val expected = VerifiedPumpProfileCache(
            pumpSid = 12345,
            verifiedAtEpochMillis = 1_787_000_000_000L,
            profiles = listOf(SYNTHETIC_DEVELOPER_PROFILE),
        )

        val decoded = decodeVerifiedPumpProfileCache(
            encodeVerifiedPumpProfileCache(expected),
            expectedPumpSid = 12345,
        )

        assertEquals(expected, decoded)
        assertEquals(10_000L, decoded?.profiles?.single()?.segments?.first()?.carbRatioMilliunits)
        assertEquals(600, decoded?.profiles?.single()?.segments?.first()?.basalMilliunitsPerHour)
    }

    @Test
    fun cacheIsIsolatedByPumpSidAndRejectsTheWrongPump() {
        val cache = VerifiedPumpProfileCache(
            pumpSid = 111,
            verifiedAtEpochMillis = 1_787_000_000_000L,
            profiles = listOf(SYNTHETIC_DEVELOPER_PROFILE),
        )
        val json = encodeVerifiedPumpProfileCache(cache)
        prefs.setVerifiedPumpProfileCacheJson(json, 111)

        assertEquals(json, prefs.verifiedPumpProfileCacheJson(111))
        assertNull(prefs.verifiedPumpProfileCacheJson(222))
        assertNull(decodeVerifiedPumpProfileCache(json, expectedPumpSid = 222))
    }

    @Test
    fun corruptOrOverLimitCacheIsIgnoredInsteadOfDisplayed() {
        val valid = VerifiedPumpProfileCache(
            pumpSid = 111,
            verifiedAtEpochMillis = 1_787_000_000_000L,
            profiles = listOf(SYNTHETIC_DEVELOPER_PROFILE),
        )
        val overLimitJson = encodeVerifiedPumpProfileCache(valid)
            .replace("\"segments\":[", "\"segments\":[" +
                List(MAX_PROFILE_TIME_SEGMENTS + 1) { "{}" }.joinToString(",") + ",")

        assertNull(decodeVerifiedPumpProfileCache("not-json", 111))
        assertNull(decodeVerifiedPumpProfileCache(overLimitJson, 111))
        assertTrue(decodeVerifiedPumpProfileCache(encodeVerifiedPumpProfileCache(valid), 111) != null)
    }

    @Test
    fun disconnectedEditDraftRoundTripsAndRemainsInactive() {
        val draft = SavedProfileDraft(
            draftId = "draft-1",
            pumpSid = 111,
            sourceProfileId = SYNTHETIC_DEVELOPER_PROFILE.idpId,
            savedAtEpochMillis = 1_787_000_000_000L,
            profile = SYNTHETIC_DEVELOPER_PROFILE.copy(
                name = "Travel copy",
                active = false,
                segments = SYNTHETIC_DEVELOPER_PROFILE.segments.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(basalMilliunitsPerHour = 725) else segment
                },
            ),
        )

        val json = encodeSavedProfileDrafts(listOf(draft))
        prefs.setSavedProfileDraftsJson(json, 111)
        val decoded = decodeSavedProfileDrafts(prefs.savedProfileDraftsJson(111), 111).single()

        assertEquals(draft, decoded)
        assertEquals(725, decoded.profile.segments.first().basalMilliunitsPerHour)
        assertTrue(!decoded.profile.active)
        assertNull(prefs.savedProfileDraftsJson(222))
    }

    @Test
    fun profileCopyComparisonIgnoresPumpIdentityButNotTherapySettings() {
        val recreated = SYNTHETIC_DEVELOPER_PROFILE.copy(
            idpId = 5,
            slotIndex = 4,
            active = false,
        )

        assertTrue(profileContentMatches(recreated, SYNTHETIC_DEVELOPER_PROFILE))
        assertTrue(
            !profileContentMatches(
                recreated.copy(
                    segments = recreated.segments.mapIndexed { index, segment ->
                        if (index == 0) segment.copy(carbRatioMilliunits = 7_000) else segment
                    },
                ),
                SYNTHETIC_DEVELOPER_PROFILE,
            ),
        )
    }

    @Test
    fun savedDraftCreateCommandPreservesExactPumpUnitsAndCreatesAnInactiveCopy() {
        val desired = SYNTHETIC_DEVELOPER_PROFILE.copy(
            name = "Disconnected copy",
            active = false,
            segments = SYNTHETIC_DEVELOPER_PROFILE.segments.mapIndexed { index, segment ->
                if (index == 0) {
                    segment.copy(
                        basalMilliunitsPerHour = 1_500,
                        carbRatioMilliunits = 6_000,
                    )
                } else {
                    segment
                }
            },
        )

        val request = buildSavedDraftCreateRequest(desired)!!

        assertEquals("Disconnected copy", request.profileName)
        assertEquals(1_500, request.firstSegmentProfileBasalRate)
        assertEquals(6_000, request.firstSegmentProfileCarbRatio)
        assertEquals(300, request.profileInsulinDuration)
        assertFalse(desired.active)
    }

    @Test
    fun savedDraftRestoresEveryRemainingSegmentIntoTheNewPumpSlotWithoutScaling() {
        val desired = SYNTHETIC_DEVELOPER_PROFILE.copy(
            name = "Sixteen segment copy",
            active = false,
            segments = List(MAX_PROFILE_TIME_SEGMENTS) { index ->
                ProfileSegmentSnapshot(
                    index = index,
                    startMinutes = index * 90,
                    basalMilliunitsPerHour = 500 + index,
                    carbRatioMilliunits = 6_000L + index,
                    targetBgMgdl = 110 + index,
                    correctionFactorMgdlPerUnit = 25 + index,
                )
            },
        )
        val created = desired.copy(idpId = 4, slotIndex = 2, segments = desired.segments.take(1))

        val requests = buildSavedDraftRemainingSegmentRequests(created, desired)

        assertEquals(MAX_PROFILE_TIME_SEGMENTS - 1, requests.size)
        requests.forEachIndexed { index, request ->
            val expected = desired.segments[index + 1]
            assertEquals(4, request.idpId)
            assertEquals(2, request.profileIndex)
            assertEquals(SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT, request.operation)
            assertEquals(expected.startMinutes, request.profileStartTime)
            assertEquals(expected.basalMilliunitsPerHour, request.profileBasalRate)
            assertEquals(expected.carbRatioMilliunits, request.profileCarbRatio)
            assertEquals(expected.targetBgMgdl, request.profileTargetBG)
            assertEquals(expected.correctionFactorMgdlPerUnit, request.profileISF)
            assertEquals(PROFILE_SEGMENT_STATUS_ALL_FIELDS, request.statusId)
        }
    }

    @Test
    fun savedDraftCreateRejectsAnIncompleteOrOverLimitProfile() {
        assertNull(buildSavedDraftCreateRequest(SYNTHETIC_DEVELOPER_PROFILE.copy(segments = emptyList())))
        assertNull(
            buildSavedDraftCreateRequest(
                SYNTHETIC_DEVELOPER_PROFILE.copy(
                    segments = SYNTHETIC_DEVELOPER_PROFILE.segments.mapIndexed { index, segment ->
                        if (index == 0) segment.copy(startMinutes = 30) else segment
                    },
                ),
            ),
        )
        assertNull(
            buildSavedDraftCreateRequest(
                SYNTHETIC_DEVELOPER_PROFILE.copy(
                    segments = List(MAX_PROFILE_TIME_SEGMENTS + 1) { index ->
                        SYNTHETIC_DEVELOPER_PROFILE.segments.first().copy(
                            index = index,
                            startMinutes = index * 60,
                        )
                    },
                ),
            ),
        )
    }
}
