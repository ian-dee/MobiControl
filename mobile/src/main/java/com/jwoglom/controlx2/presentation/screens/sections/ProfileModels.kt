package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import java.math.BigDecimal
import java.math.RoundingMode

internal const val PROFILE_VALUE_SCALE = 1_000L
internal const val PROFILE_DAY_MINUTES = 24 * 60
internal const val CONTROL_IQ_FIXED_INSULIN_DURATION_MINUTES = 5 * 60
internal const val MAX_PERSONAL_PROFILES = 6
internal const val MAX_PROFILE_TIME_SEGMENTS = 16
internal const val PROFILE_SEGMENT_STATUS_ALL_FIELDS = 31

internal data class ProfileSegmentSnapshot(
    val index: Int,
    val startMinutes: Int,
    val basalMilliunitsPerHour: Int,
    val carbRatioMilliunits: Long,
    val targetBgMgdl: Int,
    val correctionFactorMgdlPerUnit: Int,
)

internal data class InsulinProfileSnapshot(
    val idpId: Int,
    val slotIndex: Int,
    val name: String,
    val active: Boolean,
    val insulinDurationMinutes: Int,
    val maxBolusMilliunits: Int,
    val carbEntryEnabled: Boolean,
    val segments: List<ProfileSegmentSnapshot>,
)

internal data class ProfileBolusSettingsPolicy(
    val displayedDurationMinutes: Int,
    val durationEditable: Boolean,
    val carbohydratesEditable: Boolean,
    val carbohydratesRequired: Boolean,
)

internal fun canAddProfile(profiles: List<InsulinProfileSnapshot>): Boolean =
    profiles.size < MAX_PERSONAL_PROFILES

internal fun canAddProfileSegment(profile: InsulinProfileSnapshot): Boolean =
    profile.segments.size < MAX_PROFILE_TIME_SEGMENTS

internal fun profileBolusSettingsPolicy(
    controlIqEnabled: Boolean,
    profile: InsulinProfileSnapshot,
): ProfileBolusSettingsPolicy = ProfileBolusSettingsPolicy(
    displayedDurationMinutes = if (controlIqEnabled) {
        CONTROL_IQ_FIXED_INSULIN_DURATION_MINUTES
    } else {
        profile.insulinDurationMinutes
    },
    durationEditable = !controlIqEnabled,
    // Carbohydrates cannot be turned off while Control-IQ is running. A legacy/inactive profile
    // already set to off may still be corrected back to on.
    carbohydratesEditable = !controlIqEnabled || !profile.carbEntryEnabled,
    carbohydratesRequired = controlIqEnabled,
)

internal fun profileActivationValidationError(
    controlIqEnabled: Boolean,
    profile: InsulinProfileSnapshot,
): String? = when {
    controlIqEnabled && !profile.carbEntryEnabled ->
        "Turn off Control-IQ before activating a profile with Carbohydrates turned off."
    else -> null
}

internal fun controlIqEnableProfileValidationError(
    requestedEnabled: Boolean,
    activeProfile: InsulinProfileSnapshot?,
): String? = when {
    !requestedEnabled -> null
    activeProfile == null -> "An active Personal Profile must be loaded before Control-IQ can be enabled."
    !activeProfile.carbEntryEnabled ->
        "Turn on Carbohydrates in the active Personal Profile before enabling Control-IQ."
    activeProfile.segments.isEmpty() ->
        "Complete the active Personal Profile before enabling Control-IQ."
    else -> null
}

internal val SYNTHETIC_DEVELOPER_PROFILE = InsulinProfileSnapshot(
    idpId = 0,
    slotIndex = 0,
    name = "Synthetic demo",
    active = true,
    insulinDurationMinutes = 5 * 60,
    maxBolusMilliunits = 15_000,
    carbEntryEnabled = true,
    segments = listOf(
        // Deliberately regular synthetic schedule for UI and serialization tests.
        ProfileSegmentSnapshot(0, 0, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(1, 2 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(2, 4 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(3, 6 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(4, 8 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(5, 12 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(6, 16 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(7, 20 * 60, 600, 10_000, 110, 40),
        ProfileSegmentSnapshot(8, 22 * 60, 600, 10_000, 110, 40),
    ),
)

internal fun profileSnapshots(manager: IDPManager?): List<InsulinProfileSnapshot> {
    if (manager?.isComplete != true) return emptyList()

    // ProfileStatusResponse defines slot 0 as the active profile. IDPManager preserves slot order.
    return manager.profiles.mapIndexed { slotIndex, profile ->
        val settings = profile.idpSettingsResponse
        InsulinProfileSnapshot(
            idpId = settings.idpId,
            slotIndex = slotIndex,
            name = settings.name,
            active = slotIndex == 0,
            insulinDurationMinutes = settings.insulinDuration,
            maxBolusMilliunits = settings.maxBolus,
            carbEntryEnabled = settings.carbEntry,
            segments = profile.segments
                .sortedBy { it.segmentIndex }
                .map { segment ->
                    ProfileSegmentSnapshot(
                        index = segment.segmentIndex,
                        startMinutes = segment.profileStartTime,
                        basalMilliunitsPerHour = segment.profileBasalRate,
                        carbRatioMilliunits = segment.profileCarbRatio,
                        targetBgMgdl = segment.profileTargetBG,
                        correctionFactorMgdlPerUnit = segment.profileISF,
                    )
                },
        )
    }
}

internal fun profileManagerFromSnapshots(
    profiles: List<InsulinProfileSnapshot>,
    minuteOfDay: Int,
): IDPManager {
    val activeFirst = profiles.sortedByDescending { it.active }.mapIndexed { index, profile ->
        profile.copy(slotIndex = index, active = index == 0)
    }
    val slotIds = activeFirst.map { it.idpId }.toMutableList()
    while (slotIds.size < 6) slotIds += -1
    val activeSegment = activeFirst.firstOrNull()?.let {
        activeProfileSegmentIndex(it.segments, minuteOfDay)
    } ?: 0

    val manager = IDPManager()
    manager.processMessage(
        ProfileStatusResponse(
            activeFirst.size,
            slotIds[0],
            slotIds[1],
            slotIds[2],
            slotIds[3],
            slotIds[4],
            slotIds[5],
            activeSegment,
        )
    )
    activeFirst.forEach { profile ->
        manager.processMessage(
            IDPSettingsResponse(
                profile.idpId,
                profile.name,
                profile.segments.size,
                profile.insulinDurationMinutes,
                profile.maxBolusMilliunits,
                profile.carbEntryEnabled,
            )
        )
        profile.segments.sortedBy { it.index }.forEachIndexed { index, segment ->
            manager.processMessage(
                IDPSegmentResponse(
                    profile.idpId,
                    index,
                    segment.startMinutes,
                    segment.basalMilliunitsPerHour,
                    segment.carbRatioMilliunits,
                    segment.targetBgMgdl,
                    segment.correctionFactorMgdlPerUnit,
                    31,
                )
            )
        }
    }
    return manager
}

internal fun activeProfileSegmentIndex(
    segments: List<ProfileSegmentSnapshot>,
    minuteOfDay: Int,
): Int {
    if (segments.isEmpty()) return 0
    val normalizedMinute = minuteOfDay.mod(PROFILE_DAY_MINUTES)
    return segments
        .sortedBy { it.startMinutes }
        .lastOrNull { it.startMinutes <= normalizedMinute }
        ?.index
        ?: segments.minBy { it.startMinutes }.index
}

/**
 * Returns the settings that are active immediately before a proposed new segment time.
 * The profile repeats daily, so midnight is preceded by the final segment from the prior day.
 * An exact time match deliberately returns the segment before the match; saving the duplicate
 * time is rejected separately by the editor.
 */
internal fun precedingProfileSegmentForStartTime(
    segments: List<ProfileSegmentSnapshot>,
    startMinutes: Int,
): ProfileSegmentSnapshot? {
    if (segments.isEmpty()) return null
    val normalizedMinute = startMinutes.mod(PROFILE_DAY_MINUTES)
    val sorted = segments.sortedBy { it.startMinutes }
    return sorted.lastOrNull { it.startMinutes < normalizedMinute } ?: sorted.last()
}

internal fun totalDailyBasalUnits(segments: List<ProfileSegmentSnapshot>): Double {
    val sorted = segments.sortedBy { it.startMinutes }
    if (sorted.isEmpty()) return 0.0
    val milliunitMinutes = sorted.mapIndexed { index, segment ->
        val endMinutes = sorted.getOrNull(index + 1)?.startMinutes ?: PROFILE_DAY_MINUTES
        (endMinutes - segment.startMinutes).toLong() * segment.basalMilliunitsPerHour
    }.sum()
    return milliunitMinutes.toDouble() / 60.0 / PROFILE_VALUE_SCALE
}

internal fun formatProfileBasal(milliunits: Int): String =
    BigDecimal.valueOf(milliunits.toLong(), 3).stripTrailingZeros().toPlainString()

internal fun formatProfileRatio(milliunits: Long): String =
    BigDecimal.valueOf(milliunits, 3).stripTrailingZeros().toPlainString()

internal fun profileRatioToPumpValue(displayValue: String): Long? {
    val normalized = displayValue.trim().replace(',', '.')
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) return null
    return normalized.toBigDecimalOrNull()
        ?.movePointRight(3)
        ?.setScale(0, RoundingMode.UNNECESSARY)
        ?.longValueExactOrNull()
        ?.takeIf { it > 0 }
}

internal fun basalRateToPumpValue(displayValue: String): Int? {
    val normalized = displayValue.trim().replace(',', '.')
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) return null
    return normalized.toBigDecimalOrNull()
        ?.movePointRight(3)
        ?.setScale(0, RoundingMode.UNNECESSARY)
        ?.intValueExactOrNull()
        ?.takeIf { it > 0 }
}

private fun BigDecimal.longValueExactOrNull(): Long? = try {
    longValueExact()
} catch (_: ArithmeticException) {
    null
}

private fun BigDecimal.intValueExactOrNull(): Int? = try {
    intValueExact()
} catch (_: ArithmeticException) {
    null
}
