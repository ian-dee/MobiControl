package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.PumpStateEvent
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.request.control.SetSiteChangeReminderRequest
import com.jwoglom.pumpx2.pump.messages.response.control.FillCannulaResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSiteChangeReminderResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.RemindersResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal const val FILL_CANNULA_RESPONSE_TIMEOUT_MS = 10_000L
internal const val FILL_CANNULA_COMPLETION_TIMEOUT_MS = 30_000L
internal const val SITE_CHANGE_REMINDER_TIMEOUT_MS = 10_000L

internal enum class CannulaFillPhase {
    CHECKING_PUMP,
    READY,
    REQUESTING_FILL,
    WAITING_FOR_COMPLETION,
    FILL_CONFIRMED,
    LOADING_REMINDER_CONFIGURATION,
    SETTING_REMINDER,
    REMINDER_RESPONSE_ACCEPTED,
    VERIFYING_REMINDER,
    COMPLETE,
    FILL_FAILED,
    COMPLETE_REMINDER_FAILED,
}

internal data class SiteChangeReminderPlan(
    val dayCount: Int,
    val timeOfDayMinutes: Long,
    val changeBitmask: Int = 0x07,
)

internal data class SiteChangeReminderPlanningResult(
    val plan: SiteChangeReminderPlan? = null,
    val error: String? = null,
)

internal data class CannulaFillAttemptState(
    val attemptId: Long,
    val phase: CannulaFillPhase,
    val acceptEventsAfterSequence: Long,
    val reminderPlan: SiteChangeReminderPlan? = null,
    val error: String? = null,
    val completedAtEpochMillis: Long? = null,
    val sendFillWhenReady: Boolean = false,
) {
    companion object {
        fun begin(attemptId: Long, currentSequence: Long) = CannulaFillAttemptState(
            attemptId = attemptId,
            phase = CannulaFillPhase.CHECKING_PUMP,
            acceptEventsAfterSequence = currentSequence,
        )
    }
}

internal enum class CannulaFillReadinessKind {
    NEEDS_SUSPEND,
    WAITING_FOR_FRESH_STATE,
    READY,
    BLOCKED,
}

internal data class CannulaFillReadiness(
    val kind: CannulaFillReadinessKind,
    val message: String,
)

internal fun evaluateCannulaFillReadiness(
    basalStatusEvent: PumpStateEvent<BasalStatus>?,
    loadStatusEvent: PumpMessageEvent<LoadStatusResponse>?,
    acceptEventsAfterSequence: Long,
): CannulaFillReadiness {
    if (
        basalStatusEvent == null || basalStatusEvent.sequence <= acceptEventsAfterSequence ||
        loadStatusEvent == null || loadStatusEvent.sequence <= acceptEventsAfterSequence
    ) {
        return CannulaFillReadiness(
            CannulaFillReadinessKind.WAITING_FOR_FRESH_STATE,
            "Checking the pump's current delivery and cartridge state...",
        )
    }
    if (!basalStatusEvent.value.isManuallySuspendedForCartridgeWorkflow()) {
        return CannulaFillReadiness(
            CannulaFillReadinessKind.NEEDS_SUSPEND,
            "Suspend insulin delivery before filling the cannula.",
        )
    }

    val loadStatus = loadStatusEvent.message
    val loadState = resolveLoadState(loadStatus)
    if (
        loadStatus.getIsLoadingActive() &&
        (loadState == LoadStatusResponse.LoadState.PRIME_CANNULA ||
            loadState == LoadStatusResponse.LoadState.PRIME_NUDGE)
    ) {
        return CannulaFillReadiness(
            CannulaFillReadinessKind.BLOCKED,
            "The pump reports an active cannula-fill operation. The app will not send another fill request; wait for pump completion or refresh after inspecting the pump.",
        )
    }
    return if (
        loadState == LoadStatusResponse.LoadState.PRIME_CANNULA ||
        loadState == LoadStatusResponse.LoadState.PRIME_NUDGE
    ) {
        CannulaFillReadiness(
            CannulaFillReadinessKind.READY,
            "Pump confirmed ${describeLoadStatus(loadStatus)}.",
        )
    } else {
        CannulaFillReadiness(
            CannulaFillReadinessKind.BLOCKED,
            "Cannula fill is unavailable because the pump reports ${describeLoadStatus(loadStatus)}.",
        )
    }
}

/**
 * Resolves the load state defensively across PumpX2 response variants. Keeping this mapper in the
 * pure workflow module also makes the readiness rules testable without initializing Android UI
 * state.
 */
internal fun resolveLoadState(loadStatus: LoadStatusResponse?): LoadStatusResponse.LoadState? {
    if (loadStatus == null) {
        return null
    }
    val direct = loadStatus.getLoadState()
    if (direct != null && direct != LoadStatusResponse.LoadState.UNKNOWN) {
        return direct
    }

    val fromId = when (loadStatus.getLoadStateId()) {
        0 -> LoadStatusResponse.LoadState.CHANGE_CARTRIDGE
        1 -> LoadStatusResponse.LoadState.LOAD_CARTRIDGE
        2 -> LoadStatusResponse.LoadState.PRIME_TUBING
        3 -> LoadStatusResponse.LoadState.PRIME_CANNULA
        4 -> LoadStatusResponse.LoadState.PRIME_NUDGE
        5 -> LoadStatusResponse.LoadState.INVALID
        6 -> LoadStatusResponse.LoadState.UNKNOWN
        else -> null
    }
    if (fromId != null && fromId != LoadStatusResponse.LoadState.UNKNOWN) {
        return fromId
    }

    val cargo = loadStatus.getCargo()
    if (cargo != null && cargo.size >= 2) {
        val cargoLoadStateId = cargo[1].toInt() and 0xFF
        return when (cargoLoadStateId) {
            0 -> LoadStatusResponse.LoadState.CHANGE_CARTRIDGE
            1 -> LoadStatusResponse.LoadState.LOAD_CARTRIDGE
            2 -> LoadStatusResponse.LoadState.PRIME_TUBING
            3 -> LoadStatusResponse.LoadState.PRIME_CANNULA
            4 -> LoadStatusResponse.LoadState.PRIME_NUDGE
            5 -> LoadStatusResponse.LoadState.INVALID
            else -> null
        }
    }
    return null
}

internal fun markCannulaFillRequested(
    state: CannulaFillAttemptState,
    currentSequence: Long,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.REQUESTING_FILL,
    acceptEventsAfterSequence = currentSequence,
    error = null,
    sendFillWhenReady = false,
)

internal fun markFinalCannulaReadinessCheck(
    state: CannulaFillAttemptState,
    currentSequence: Long,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.CHECKING_PUMP,
    acceptEventsAfterSequence = currentSequence,
    error = null,
    sendFillWhenReady = true,
)

internal fun applyFillCannulaResponse(
    state: CannulaFillAttemptState,
    event: PumpMessageEvent<FillCannulaResponse>?,
): CannulaFillAttemptState {
    if (
        state.phase != CannulaFillPhase.REQUESTING_FILL ||
        event == null ||
        event.sequence <= state.acceptEventsAfterSequence
    ) {
        return state
    }
    return if (event.message.status == 0) {
        state.copy(
            phase = CannulaFillPhase.WAITING_FOR_COMPLETION,
            error = null,
        )
    } else {
        state.copy(
            phase = CannulaFillPhase.FILL_FAILED,
            acceptEventsAfterSequence = event.sequence,
            error = "The pump rejected the cannula-fill request (status ${event.message.status}).",
        )
    }
}

internal fun applyFillCannulaCompletion(
    state: CannulaFillAttemptState,
    event: PumpMessageEvent<FillCannulaStateStreamResponse>?,
): CannulaFillAttemptState {
    if (
        state.phase != CannulaFillPhase.WAITING_FOR_COMPLETION ||
        event == null ||
        event.sequence <= state.acceptEventsAfterSequence ||
        event.message.state != FillCannulaStateStreamResponse.FillCannulaState.CANNULA_FILLED
    ) {
        return state
    }
    return state.copy(
        phase = CannulaFillPhase.FILL_CONFIRMED,
        acceptEventsAfterSequence = event.sequence,
        error = null,
        completedAtEpochMillis = event.receivedAt.toEpochMilli(),
    )
}

internal fun planSiteChangeReminder(response: RemindersResponse?): SiteChangeReminderPlanningResult {
    if (response == null) {
        return SiteChangeReminderPlanningResult(error = "The pump's site-change reminder configuration is unavailable.")
    }
    val dayCount = response.siteChangeDays
    // RemindersResponse is not a StatusMessage. Its trailing `status` byte is a bitmask of
    // configured reminder groups (for example, a Mobi with only the site reminder configured
    // reports 4), not a zero-is-success result. Site-change time is also carried in the
    // reminder's frequency field; startTime is used by scheduled missed-bolus reminders.
    val timeOfDayMinutes = response.siteChangeReminder?.frequencyMins
    if (dayCount !in 1..30) {
        return SiteChangeReminderPlanningResult(error = "The pump's site-change interval is invalid ($dayCount days).")
    }
    if (timeOfDayMinutes == null || timeOfDayMinutes !in 0L..1439L) {
        return SiteChangeReminderPlanningResult(error = "The pump's site-change reminder time is invalid.")
    }
    return SiteChangeReminderPlanningResult(
        plan = SiteChangeReminderPlan(dayCount, timeOfDayMinutes),
    )
}

internal fun createSiteChangeReminderRequest(plan: SiteChangeReminderPlan) =
    SetSiteChangeReminderRequest(
        true,
        plan.dayCount,
        plan.timeOfDayMinutes,
        plan.changeBitmask,
    )

internal fun markLoadingReminderConfiguration(
    state: CannulaFillAttemptState,
    currentSequence: Long,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.LOADING_REMINDER_CONFIGURATION,
    acceptEventsAfterSequence = currentSequence,
    error = null,
)

internal fun markSettingSiteChangeReminder(
    state: CannulaFillAttemptState,
    plan: SiteChangeReminderPlan,
    currentSequence: Long,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.SETTING_REMINDER,
    acceptEventsAfterSequence = currentSequence,
    reminderPlan = plan,
    error = null,
)

internal fun applySiteChangeReminderResponse(
    state: CannulaFillAttemptState,
    event: PumpMessageEvent<SetSiteChangeReminderResponse>?,
): CannulaFillAttemptState {
    if (
        state.phase != CannulaFillPhase.SETTING_REMINDER ||
        event == null ||
        event.sequence <= state.acceptEventsAfterSequence
    ) {
        return state
    }
    return if (event.message.status == 0) {
        state.copy(
            phase = CannulaFillPhase.REMINDER_RESPONSE_ACCEPTED,
            acceptEventsAfterSequence = event.sequence,
            error = null,
        )
    } else {
        state.copy(
            phase = CannulaFillPhase.COMPLETE_REMINDER_FAILED,
            acceptEventsAfterSequence = event.sequence,
            error = "Cannula fill completed, but the pump rejected the site-change reminder (status ${event.message.status}).",
        )
    }
}

internal fun markVerifyingSiteChangeReminder(
    state: CannulaFillAttemptState,
    currentSequence: Long,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.VERIFYING_REMINDER,
    acceptEventsAfterSequence = currentSequence,
)

internal fun applySiteChangeReminderReadback(
    state: CannulaFillAttemptState,
    event: PumpMessageEvent<RemindersResponse>?,
): CannulaFillAttemptState {
    if (
        state.phase != CannulaFillPhase.VERIFYING_REMINDER ||
        event == null ||
        event.sequence <= state.acceptEventsAfterSequence
    ) {
        return state
    }
    val plan = state.reminderPlan
    val response = event.message
    val reminder = response.siteChangeReminder
    val matches = plan != null &&
        response.siteChangeDays == plan.dayCount &&
        reminder != null &&
        reminder.enabled != 0 &&
        reminder.frequencyMins == plan.timeOfDayMinutes

    return if (matches) {
        state.copy(
            phase = CannulaFillPhase.COMPLETE,
            acceptEventsAfterSequence = event.sequence,
            error = null,
        )
    } else {
        state.copy(
            phase = CannulaFillPhase.COMPLETE_REMINDER_FAILED,
            acceptEventsAfterSequence = event.sequence,
            error = "Cannula fill completed, but the site-change reminder readback did not match the requested settings.",
        )
    }
}

internal fun failCannulaFill(
    state: CannulaFillAttemptState,
    error: String,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.FILL_FAILED,
    error = error,
)

internal fun failSiteChangeReminder(
    state: CannulaFillAttemptState,
    error: String,
): CannulaFillAttemptState = state.copy(
    phase = CannulaFillPhase.COMPLETE_REMINDER_FAILED,
    error = error,
)

internal fun describeLoadStatus(loadStatus: LoadStatusResponse?): String {
    if (loadStatus == null) return "no fresh load status"
    val state = resolveLoadState(loadStatus)?.name ?: "UNKNOWN"
    val activity = if (loadStatus.getIsLoadingActive()) "active" else "inactive"
    return "$state ($activity, prime status ${loadStatus.getPrimeStatusId()})"
}

internal fun formatCannulaFillTimestamp(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zoneId)
    .format(Instant.ofEpochMilli(epochMillis))

internal fun formatPumpRecordedCannulaFillTimestamp(
    pumpLocalTime: LocalDateTime,
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .format(pumpLocalTime)

internal fun latestCompletedCannulaFill(items: List<HistoryLogItem>): HistoryLogItem? = items
    .asSequence()
    .mapNotNull { item ->
        val log = runCatching { item.parse() }.getOrNull() as? CannulaFilledHistoryLog
            ?: return@mapNotNull null
        item to log
    }
    .filter { (_, log) ->
        log.completionStatus == CannulaFilledHistoryLog.CompletionStatus.COMPLETED
    }
    .maxByOrNull { (item, _) -> item.seqId }
    ?.first

internal fun formatCannulaFillAge(elapsedSeconds: Long): String? {
    if (elapsedSeconds < 0L) return null
    val totalHours = elapsedSeconds / 3_600L
    val days = totalHours / 24L
    val hours = totalHours % 24L
    val dayLabel = if (days == 1L) "day" else "days"
    val hourLabel = if (hours == 1L) "hour" else "hours"
    return "$days $dayLabel, $hours $hourLabel"
}

/**
 * Derives the next site-change reminder from pump-authored fill history and the pump's current
 * reminder readback. A disabled, unavailable, or malformed reminder intentionally has no inferred
 * schedule.
 */
internal fun nextSiteChangeReminderEpochMillis(
    lastCannulaFillEpochMillis: Long,
    remindersResponse: RemindersResponse?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? {
    if (lastCannulaFillEpochMillis <= 0L) return null
    val response = remindersResponse ?: return null
    val reminder = response.siteChangeReminder ?: return null
    if (reminder.enabled == 0) return null

    val dayCount = response.siteChangeDays
    val timeOfDayMinutes = reminder.frequencyMins
    if (dayCount !in 1..30 || timeOfDayMinutes !in 0L..1439L) return null

    val reminderTime = LocalTime.of(
        (timeOfDayMinutes / 60).toInt(),
        (timeOfDayMinutes % 60).toInt(),
    )
    return Instant.ofEpochMilli(lastCannulaFillEpochMillis)
        .atZone(zoneId)
        .toLocalDate()
        .plusDays(dayCount.toLong())
        .atTime(reminderTime)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

internal fun formatSiteChangeReminderPlan(
    plan: SiteChangeReminderPlan,
    locale: Locale = Locale.getDefault(),
): String {
    val hours = plan.timeOfDayMinutes / 60
    val minutes = plan.timeOfDayMinutes % 60
    val dayLabel = if (plan.dayCount == 1) "day" else "days"
    val formattedTime = LocalTime.of(hours.toInt(), minutes.toInt())
        .format(DateTimeFormatter.ofPattern("h:mm a", locale))
    return "${plan.dayCount} $dayLabel at $formattedTime"
}
