package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.response.controlStream.DetectingCartridgeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.EnterChangeCartridgeModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.ExitFillTubingModeStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse

internal const val INCOMPLETE_CARTRIDGE_INITIAL_ALERT_MILLIS = 3 * 60_000L
internal const val INCOMPLETE_CARTRIDGE_REPEAT_ALERT_MILLIS = 5 * 60_000L

enum class IncompleteCartridgePhase {
    CHANGE_CARTRIDGE,
    FILL_TUBING,
    FILL_CANNULA,
}

/**
 * Local presentation state for Tandem-style incomplete-workflow reminders.
 *
 * Pump status remains authoritative for the active phase. This record only preserves when the
 * app first observed that phase and when the user acknowledged its last in-app reminder. It is
 * never used to authorize, construct, send, repeat, or reconcile a pump command.
 */
internal data class IncompleteCartridgeReminder(
    val pumpSid: Int,
    val phase: IncompleteCartridgePhase,
    val startedAtEpochMillis: Long,
    val lastAcknowledgedAtEpochMillis: Long? = null,
) {
    init {
        require(pumpSid >= 0) { "Incomplete cartridge reminder requires a valid pump SID" }
        require(startedAtEpochMillis > 0L) { "Incomplete cartridge reminder requires a start time" }
        require(
            lastAcknowledgedAtEpochMillis == null ||
                lastAcknowledgedAtEpochMillis >= startedAtEpochMillis,
        ) { "Incomplete cartridge reminder acknowledgement cannot predate its start" }
    }

    fun nextAlertAtEpochMillis(): Long =
        lastAcknowledgedAtEpochMillis?.plus(INCOMPLETE_CARTRIDGE_REPEAT_ALERT_MILLIS)
            ?: startedAtEpochMillis.plus(INCOMPLETE_CARTRIDGE_INITIAL_ALERT_MILLIS)

    fun isDue(nowEpochMillis: Long): Boolean = nowEpochMillis >= nextAlertAtEpochMillis()

    fun acknowledge(nowEpochMillis: Long): IncompleteCartridgeReminder = copy(
        lastAcknowledgedAtEpochMillis = nowEpochMillis.coerceAtLeast(startedAtEpochMillis),
    )

    fun encode(): String = listOf(
        "1",
        pumpSid,
        phase.name,
        startedAtEpochMillis,
        lastAcknowledgedAtEpochMillis?.toString().orEmpty(),
    ).joinToString("|")

    companion object {
        fun decode(value: String?): IncompleteCartridgeReminder? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 5 || parts[0] != "1") return null
            return runCatching {
                IncompleteCartridgeReminder(
                    pumpSid = parts[1].toInt(),
                    phase = IncompleteCartridgePhase.valueOf(parts[2]),
                    startedAtEpochMillis = parts[3].toLong(),
                    lastAcknowledgedAtEpochMillis = parts[4]
                        .takeIf(String::isNotEmpty)
                        ?.toLong(),
                )
            }.getOrNull()
        }
    }
}

internal fun reconcileIncompleteCartridgeReminder(
    existing: IncompleteCartridgeReminder?,
    pumpSid: Int,
    phase: IncompleteCartridgePhase?,
    nowEpochMillis: Long,
): IncompleteCartridgeReminder? {
    if (pumpSid < 0 || phase == null || nowEpochMillis <= 0L) return null
    if (existing?.pumpSid == pumpSid && existing.phase == phase) return existing
    return IncompleteCartridgeReminder(
        pumpSid = pumpSid,
        phase = phase,
        startedAtEpochMillis = nowEpochMillis,
    )
}

/**
 * Resolves the pump-required maintenance phase without issuing or inferring a state change.
 * Fresh Load Status is preferred; stream values cover the interval before that read arrives.
 */
internal fun resolveIncompleteCartridgePhase(
    loadStatus: LoadStatusResponse?,
    inChangeCartridgeMode: Boolean,
    enterChangeCartridgeState: EnterChangeCartridgeModeStateStreamResponse?,
    detectingCartridgeState: DetectingCartridgeStateStreamResponse?,
    inFillTubingMode: Boolean,
    exitFillTubingState: ExitFillTubingModeStateStreamResponse?,
): IncompleteCartridgePhase? {
    val resolvedLoadState = resolveLoadState(loadStatus)

    // Completed cartridge detection is the pump-authored handoff into mandatory tubing fill.
    if (
        detectingCartridgeState?.isComplete == true &&
        (resolvedLoadState == LoadStatusResponse.LoadState.CHANGE_CARTRIDGE ||
            resolvedLoadState == LoadStatusResponse.LoadState.LOAD_CARTRIDGE ||
            resolvedLoadState == LoadStatusResponse.LoadState.PRIME_TUBING)
    ) {
        return IncompleteCartridgePhase.FILL_TUBING
    }

    // A fresh inactive terminal/unknown status supersedes older stream observations. The Resume
    // policy still independently blocks delivery when pump state is not known to be compatible.
    if (loadStatus != null && !loadStatus.getIsLoadingActive()) {
        when (resolvedLoadState) {
            LoadStatusResponse.LoadState.PRIME_NUDGE,
            LoadStatusResponse.LoadState.PRIME_CANNULA,
            LoadStatusResponse.LoadState.INVALID,
            LoadStatusResponse.LoadState.UNKNOWN,
            null -> return null
            else -> {}
        }
    }

    // A successful Enter Change Cartridge response is the app's correlated acknowledgement that
    // the pump accepted this user-authored workflow. During the pump's handoff, Load Status may
    // briefly remain active at the preceding PRIME_CANNULA stage. Keep the accepted Change
    // Cartridge phase authoritative over that prior-stage carryover; otherwise it can incorrectly
    // route the user into Fill Cannula. A later tubing stage remains forward progress, and a fresh
    // inactive terminal status above still clears stale restored state after process death.
    if (
        inChangeCartridgeMode &&
        (resolvedLoadState == LoadStatusResponse.LoadState.PRIME_CANNULA ||
            resolvedLoadState == LoadStatusResponse.LoadState.PRIME_NUDGE)
    ) {
        return IncompleteCartridgePhase.CHANGE_CARTRIDGE
    }

    when (resolvedLoadState) {
        LoadStatusResponse.LoadState.CHANGE_CARTRIDGE ->
            return IncompleteCartridgePhase.CHANGE_CARTRIDGE
        LoadStatusResponse.LoadState.LOAD_CARTRIDGE,
        LoadStatusResponse.LoadState.PRIME_TUBING ->
            return IncompleteCartridgePhase.FILL_TUBING
        LoadStatusResponse.LoadState.PRIME_CANNULA,
        LoadStatusResponse.LoadState.PRIME_NUDGE ->
            return IncompleteCartridgePhase.FILL_CANNULA
        LoadStatusResponse.LoadState.INVALID,
        LoadStatusResponse.LoadState.UNKNOWN,
        null -> {}
    }

    if (inFillTubingMode) return IncompleteCartridgePhase.FILL_TUBING
    if (
        exitFillTubingState?.state ==
        ExitFillTubingModeStateStreamResponse.ExitFillTubingModeState.NOT_COMPLETE
    ) {
        return IncompleteCartridgePhase.FILL_TUBING
    }
    if (inChangeCartridgeMode || enterChangeCartridgeState != null || detectingCartridgeState != null) {
        return IncompleteCartridgePhase.CHANGE_CARTRIDGE
    }
    return null
}
