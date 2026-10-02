package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import org.json.JSONArray
import org.json.JSONObject

private const val PROFILE_DRAFTS_VERSION = 1

internal data class SavedProfileDraft(
    val draftId: String,
    val pumpSid: Int,
    val sourceProfileId: Int,
    val savedAtEpochMillis: Long,
    val profile: InsulinProfileSnapshot,
)

internal data class PendingProfileDraft(
    val sourceProfileId: Int,
    val profile: InsulinProfileSnapshot,
)

internal fun encodeSavedProfileDrafts(drafts: List<SavedProfileDraft>): String =
    JSONObject().apply {
        put("version", PROFILE_DRAFTS_VERSION)
        put("drafts", JSONArray().apply {
            drafts.forEach { draft ->
                require(draft.draftId.isNotBlank())
                require(draft.pumpSid >= 0)
                require(draft.savedAtEpochMillis > 0)
                require(draft.profile.segments.isNotEmpty())
                require(draft.profile.segments.size <= MAX_PROFILE_TIME_SEGMENTS)
                put(JSONObject().apply {
                    put("draftId", draft.draftId)
                    put("pumpSid", draft.pumpSid)
                    put("sourceProfileId", draft.sourceProfileId)
                    put("savedAtEpochMillis", draft.savedAtEpochMillis)
                    // Reuse the verified snapshot codec so all exact pump-unit fields follow one
                    // serialization path. A draft remains distinct and is never read as cache.
                    put(
                        "profileSnapshot",
                        JSONObject(
                            encodeVerifiedPumpProfileCache(
                                VerifiedPumpProfileCache(
                                    pumpSid = draft.pumpSid,
                                    verifiedAtEpochMillis = draft.savedAtEpochMillis,
                                    profiles = listOf(draft.profile.copy(active = false)),
                                ),
                            ),
                        ),
                    )
                })
            }
        })
    }.toString()

internal fun decodeSavedProfileDrafts(json: String?, expectedPumpSid: Int): List<SavedProfileDraft> {
    if (json.isNullOrBlank() || expectedPumpSid < 0) return emptyList()
    return runCatching {
        val root = JSONObject(json)
        require(root.getInt("version") == PROFILE_DRAFTS_VERSION)
        val array = root.getJSONArray("drafts")
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val pumpSid = item.getInt("pumpSid")
                require(pumpSid == expectedPumpSid)
                val savedAt = item.getLong("savedAtEpochMillis")
                val snapshot = decodeVerifiedPumpProfileCache(
                    item.getJSONObject("profileSnapshot").toString(),
                    expectedPumpSid,
                ) ?: error("Invalid profile draft snapshot")
                val profile = snapshot.profiles.single().copy(active = false)
                require(profile.segments.isNotEmpty())
                add(
                    SavedProfileDraft(
                        draftId = item.getString("draftId").also { require(it.isNotBlank()) },
                        pumpSid = pumpSid,
                        sourceProfileId = item.getInt("sourceProfileId"),
                        savedAtEpochMillis = savedAt.also { require(it > 0) },
                        profile = profile,
                    ),
                )
            }
        }.also { drafts ->
            require(drafts.map { it.draftId }.distinct().size == drafts.size)
            require(drafts.map { it.profile.name.lowercase() }.distinct().size == drafts.size)
        }
    }.getOrElse { emptyList() }
}

internal fun profileContentMatches(
    actual: InsulinProfileSnapshot,
    expected: InsulinProfileSnapshot,
): Boolean {
    val actualSegments = actual.segments.sortedBy { it.startMinutes }
    val expectedSegments = expected.segments.sortedBy { it.startMinutes }
    return actual.name == expected.name &&
        actual.insulinDurationMinutes == expected.insulinDurationMinutes &&
        actual.carbEntryEnabled == expected.carbEntryEnabled &&
        actualSegments.size == expectedSegments.size &&
        actualSegments.zip(expectedSegments).all { (first, second) ->
            first.startMinutes == second.startMinutes &&
                first.basalMilliunitsPerHour == second.basalMilliunitsPerHour &&
                first.carbRatioMilliunits == second.carbRatioMilliunits &&
                first.targetBgMgdl == second.targetBgMgdl &&
                first.correctionFactorMgdlPerUnit == second.correctionFactorMgdlPerUnit
        }
}

internal fun buildSavedDraftCreateRequest(profile: InsulinProfileSnapshot): CreateIDPRequest? {
    val segments = profile.segments.sortedBy { it.startMinutes }
    val first = segments.firstOrNull() ?: return null
    if (segments.size > MAX_PROFILE_TIME_SEGMENTS || first.startMinutes != 0 ||
        first.carbRatioMilliunits !in 1..Int.MAX_VALUE.toLong()
    ) return null
    return CreateIDPRequest(
        profile.name,
        first.carbRatioMilliunits.toInt(),
        first.basalMilliunitsPerHour,
        first.targetBgMgdl,
        first.correctionFactorMgdlPerUnit,
        profile.insulinDurationMinutes,
        if (profile.carbEntryEnabled) 1 else 0,
    )
}

internal fun buildSavedDraftRemainingSegmentRequests(
    createdProfile: InsulinProfileSnapshot,
    desiredProfile: InsulinProfileSnapshot,
): List<SetIDPSegmentRequest> = desiredProfile.segments
    .sortedBy { it.startMinutes }
    .drop(1)
    .map { segment ->
        SetIDPSegmentRequest(
            createdProfile.idpId,
            createdProfile.slotIndex,
            0,
            SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT,
            segment.startMinutes,
            segment.basalMilliunitsPerHour,
            segment.carbRatioMilliunits,
            segment.targetBgMgdl,
            segment.correctionFactorMgdlPerUnit,
            PROFILE_SEGMENT_STATUS_ALL_FIELDS,
        )
    }
