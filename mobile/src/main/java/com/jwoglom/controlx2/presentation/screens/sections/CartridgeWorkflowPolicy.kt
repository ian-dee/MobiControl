package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import java.math.BigDecimal
import java.math.RoundingMode

internal const val FILL_TUBING_ENTER_RESPONSE_TIMEOUT_MS = 10_000L
internal const val FILL_TUBING_EXIT_RESPONSE_TIMEOUT_MS = 10_000L
internal const val CARTRIDGE_DELIVERY_ACTION_TIMEOUT_MS = 10_000L

internal fun BasalStatus?.isManuallySuspendedForCartridgeWorkflow(): Boolean =
    this == BasalStatus.PUMP_SUSPENDED

internal data class CartridgeResumeDecision(
    val canResume: Boolean,
    val message: String,
)

/**
 * Pump load state is authoritative for Resume. PRIME_NUDGE is the optional cannula stage on Mobi;
 * when loading is inactive, the required cartridge/tubing work has completed and Resume is valid.
 */
internal fun evaluateCartridgeResume(
    loadStatus: LoadStatusResponse?,
    checking: Boolean = false,
): CartridgeResumeDecision {
    if (checking && loadStatus == null) {
        return CartridgeResumeDecision(false, "Checking pump load state...")
    }
    if (loadStatus == null) {
        return CartridgeResumeDecision(false, "Couldn't read load state from pump. Try again.")
    }

    return when (resolveLoadState(loadStatus)) {
        LoadStatusResponse.LoadState.CHANGE_CARTRIDGE ->
            CartridgeResumeDecision(false, "Finish Change Cartridge first.")
        LoadStatusResponse.LoadState.LOAD_CARTRIDGE ->
            CartridgeResumeDecision(false, "Complete Fill Tubing first, then try Resume insulin again.")
        LoadStatusResponse.LoadState.PRIME_TUBING -> {
            val nextAction = when (loadStatus.getPrimeTubingStatus()) {
                LoadStatusResponse.PrimeTubingStatus.ENTERED_CANNOT_EXIT ->
                    "Hold the pump button to fill tubing, then confirm when drops appear."
                LoadStatusResponse.PrimeTubingStatus.ENTERED_CAN_EXIT ->
                    "Confirm that drops appeared and exit Fill Tubing mode."
                LoadStatusResponse.PrimeTubingStatus.SUSPENDED ->
                    "Continue and complete Fill Tubing, then exit the mode."
                else -> "Complete Fill Tubing and exit Fill Tubing mode."
            }
            CartridgeResumeDecision(false, "$nextAction Then try Resume insulin again.")
        }
        LoadStatusResponse.LoadState.PRIME_NUDGE,
        LoadStatusResponse.LoadState.PRIME_CANNULA -> {
            if (loadStatus.getIsLoadingActive()) {
                CartridgeResumeDecision(false, "Complete the active cannula fill first.")
            } else {
                CartridgeResumeDecision(true, "Resume all insulin deliveries?")
            }
        }
        LoadStatusResponse.LoadState.INVALID ->
            CartridgeResumeDecision(false, "Pump load state is invalid. Complete cartridge loading steps on the pump first.")
        LoadStatusResponse.LoadState.UNKNOWN,
        null -> CartridgeResumeDecision(false, "Pump load state is unknown. Complete any pending load steps on the pump first.")
    }
}

internal fun isFillTubingPauseAlert(notification: Any): Boolean =
    notification == AlertStatusResponse.AlertResponseType.FILL_TUBING_STILL_IN_PROGRESS

internal fun fillTubingPauseDismissRequest(): DismissNotificationRequest =
    DismissNotificationRequest(
        DismissNotificationRequest.NotificationType.ALERT,
        AlertStatusResponse.AlertResponseType.FILL_TUBING_STILL_IN_PROGRESS.bitmask().toLong(),
        false,
    )

/** Convert a user-reviewed cannula amount to exact pump milliunits without floating-point rounding. */
internal fun cannulaPrimeMilliUnitsOrNull(units: Double): Int? = runCatching {
    if (!units.isFinite() || units < 0.1 || units > 2.0) return null
    val normalized = BigDecimal.valueOf(units).setScale(1, RoundingMode.UNNECESSARY)
    normalized.movePointRight(3).intValueExact()
}.getOrNull()
