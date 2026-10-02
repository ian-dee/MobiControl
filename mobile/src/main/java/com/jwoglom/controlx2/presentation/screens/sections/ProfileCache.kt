package com.jwoglom.controlx2.presentation.screens.sections

import org.json.JSONArray
import org.json.JSONObject

private const val PROFILE_CACHE_VERSION = 1

/** A read-only local copy created only after PumpX2 reports a complete pump profile read. */
internal data class VerifiedPumpProfileCache(
    val pumpSid: Int,
    val verifiedAtEpochMillis: Long,
    val profiles: List<InsulinProfileSnapshot>,
)

internal fun encodeVerifiedPumpProfileCache(cache: VerifiedPumpProfileCache): String {
    require(cache.pumpSid >= 0) { "A valid pump SID is required" }
    require(cache.verifiedAtEpochMillis > 0) { "The verification timestamp must be positive" }
    require(cache.profiles.size <= MAX_PERSONAL_PROFILES) { "Too many pump profiles" }
    cache.profiles.forEach { require(it.segments.size <= MAX_PROFILE_TIME_SEGMENTS) }

    return JSONObject().apply {
        put("version", PROFILE_CACHE_VERSION)
        put("pumpSid", cache.pumpSid)
        put("verifiedAtEpochMillis", cache.verifiedAtEpochMillis)
        put("profiles", JSONArray().apply {
            cache.profiles.forEach { profile ->
                put(JSONObject().apply {
                    put("idpId", profile.idpId)
                    put("slotIndex", profile.slotIndex)
                    put("name", profile.name)
                    put("active", profile.active)
                    put("insulinDurationMinutes", profile.insulinDurationMinutes)
                    put("maxBolusMilliunits", profile.maxBolusMilliunits)
                    put("carbEntryEnabled", profile.carbEntryEnabled)
                    put("segments", JSONArray().apply {
                        profile.segments.forEach { segment ->
                            put(JSONObject().apply {
                                put("index", segment.index)
                                put("startMinutes", segment.startMinutes)
                                put("basalMilliunitsPerHour", segment.basalMilliunitsPerHour)
                                put("carbRatioMilliunits", segment.carbRatioMilliunits)
                                put("targetBgMgdl", segment.targetBgMgdl)
                                put("correctionFactorMgdlPerUnit", segment.correctionFactorMgdlPerUnit)
                            })
                        }
                    })
                })
            }
        })
    }.toString()
}

internal fun decodeVerifiedPumpProfileCache(
    json: String?,
    expectedPumpSid: Int,
): VerifiedPumpProfileCache? {
    if (json.isNullOrBlank() || expectedPumpSid < 0) return null
    return runCatching {
        val root = JSONObject(json)
        require(root.getInt("version") == PROFILE_CACHE_VERSION)
        val pumpSid = root.getInt("pumpSid")
        require(pumpSid == expectedPumpSid)
        val verifiedAt = root.getLong("verifiedAtEpochMillis")
        require(verifiedAt > 0)
        val profileArray = root.getJSONArray("profiles")
        require(profileArray.length() <= MAX_PERSONAL_PROFILES)

        val profiles = buildList {
            repeat(profileArray.length()) { profileIndex ->
                val source = profileArray.getJSONObject(profileIndex)
                val segmentArray = source.getJSONArray("segments")
                require(segmentArray.length() <= MAX_PROFILE_TIME_SEGMENTS)
                val segments = buildList {
                    repeat(segmentArray.length()) { segmentIndex ->
                        val segment = segmentArray.getJSONObject(segmentIndex)
                        add(
                            ProfileSegmentSnapshot(
                                index = segment.getInt("index"),
                                startMinutes = segment.getInt("startMinutes"),
                                basalMilliunitsPerHour = segment.getInt("basalMilliunitsPerHour"),
                                carbRatioMilliunits = segment.getLong("carbRatioMilliunits"),
                                targetBgMgdl = segment.getInt("targetBgMgdl"),
                                correctionFactorMgdlPerUnit =
                                    segment.getInt("correctionFactorMgdlPerUnit"),
                            ),
                        )
                    }
                }
                add(
                    InsulinProfileSnapshot(
                        idpId = source.getInt("idpId"),
                        slotIndex = source.getInt("slotIndex"),
                        name = source.getString("name"),
                        active = source.getBoolean("active"),
                        insulinDurationMinutes = source.getInt("insulinDurationMinutes"),
                        maxBolusMilliunits = source.getInt("maxBolusMilliunits"),
                        carbEntryEnabled = source.getBoolean("carbEntryEnabled"),
                        segments = segments,
                    ),
                )
            }
        }

        require(profiles.map { it.idpId }.distinct().size == profiles.size)
        require(profiles.map { it.slotIndex }.distinct().size == profiles.size)
        require(profiles.count { it.active } <= 1)
        profiles.forEach { profile ->
            require(profile.name.isNotBlank())
            require(profile.segments.map { it.index }.distinct().size == profile.segments.size)
            require(profile.segments.map { it.startMinutes }.distinct().size == profile.segments.size)
            require(profile.segments.all { it.startMinutes in 0 until PROFILE_DAY_MINUTES })
        }
        VerifiedPumpProfileCache(pumpSid, verifiedAt, profiles)
    }.getOrNull()
}

internal fun samePumpProfileData(
    first: List<InsulinProfileSnapshot>,
    second: List<InsulinProfileSnapshot>,
): Boolean = first == second
