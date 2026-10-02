package com.jwoglom.controlx2.presentation.pairing

import com.jwoglom.controlx2.presentation.screens.PumpSetupStage
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel

/**
 * Keeps the Mobi instruction order independent from Compose timing:
 * charger detection -> button gesture -> fresh pump selection -> pairing code.
 */
internal fun pairingSelectionStage(
    candidates: List<PumpPairingCandidate>,
    nowElapsedRealtimeMillis: Long,
): PumpSetupStage? = when {
    candidates.any {
        it.model == KnownDeviceModel.TSLIM_X2 &&
            it.isSelectable(nowElapsedRealtimeMillis)
    } -> PumpSetupStage.PUMP_FINDER_SELECT_PUMP
    candidates.any { it.isFreshMobiPairingMode(nowElapsedRealtimeMillis) } ->
        PumpSetupStage.PUMP_FINDER_MOBI_SELECT_PUMP
    else -> null
}

internal fun nextMobiInstructionStage(
    currentStage: PumpSetupStage?,
    candidate: PumpPairingCandidate,
    nowElapsedRealtimeMillis: Long,
    allCandidates: List<PumpPairingCandidate> = listOf(candidate),
): PumpSetupStage? {
    if (candidate.isFreshMobiPairingMode(nowElapsedRealtimeMillis) &&
        currentStage in setOf(
            PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS,
            PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD,
            PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP,
        )
    ) {
        return pairingSelectionStage(allCandidates, nowElapsedRealtimeMillis)
    }

    if (candidate.indicatesMobiOnCharger() &&
        currentStage in setOf(
            PumpSetupStage.PUMP_FINDER_SEARCHING_FOR_PUMPS,
            PumpSetupStage.PUMP_FINDER_MOBI_PLACE_ON_CHARGING_PAD,
        )
    ) {
        return PumpSetupStage.PUMP_FINDER_MOBI_PICK_UP_AND_TAP
    }

    return null
}
