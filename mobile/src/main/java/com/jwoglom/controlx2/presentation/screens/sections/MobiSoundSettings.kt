package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpSoundsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse

internal val MOBI_SNOOZE_DURATIONS_MINUTES = listOf(10, 20, 30)

internal enum class MobiPumpSoundOption(
    val displayName: String,
    val protocolValue: PumpGlobalsResponse.AnnunciationEnum,
) {
    BEEP("Beep", PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH),
    VIBRATE("Vibrate", PumpGlobalsResponse.AnnunciationEnum.VIBRATE),
    ;

    companion object {
        fun fromProtocol(value: PumpGlobalsResponse.AnnunciationEnum?): MobiPumpSoundOption =
            if (value == PumpGlobalsResponse.AnnunciationEnum.VIBRATE) VIBRATE else BEEP
    }
}

internal enum class MobiCgmSoundOption(
    val displayName: String,
    val protocolValue: SetPumpSoundsRequest.CgmAlertAnnunciationEnum,
) {
    BEEP("Beep", SetPumpSoundsRequest.CgmAlertAnnunciationEnum.BEEP),
    VIBRATE("Vibrate", SetPumpSoundsRequest.CgmAlertAnnunciationEnum.VIBRATE),
    HYPO_REPEAT("HypoRepeat", SetPumpSoundsRequest.CgmAlertAnnunciationEnum.HYPO_REPEAT),
}

/**
 * Builds the low-level request used by the official Mobi sound choices.
 *
 * The official app presents one General choice, while the protocol stores general sounds,
 * reminders, alerts, and alarms separately. Changing General therefore updates those four
 * categories together. Values outside the selected category are copied from the pump and their
 * change bits remain clear.
 */
internal fun buildMobiSoundSettingsRequest(
    globals: PumpGlobalsResponse,
    general: MobiPumpSoundOption? = null,
    quickBolus: MobiPumpSoundOption? = null,
    cgm: MobiCgmSoundOption? = null,
): SetPumpSoundsRequest {
    val changed = mutableListOf<SetPumpSoundsRequest.ChangeBitmask>()
    if (quickBolus != null) {
        changed += SetPumpSoundsRequest.ChangeBitmask.QUICK_BOLUS
    }
    if (general != null) {
        changed += SetPumpSoundsRequest.ChangeBitmask.GENERAL
        changed += SetPumpSoundsRequest.ChangeBitmask.REMINDER
        changed += SetPumpSoundsRequest.ChangeBitmask.ALERT
        changed += SetPumpSoundsRequest.ChangeBitmask.ALARM
    }
    if (cgm != null) {
        changed += SetPumpSoundsRequest.ChangeBitmask.CGM_ALERT
    }

    val generalRaw = general?.protocolValue?.id()
    val cgmProtocol = cgm?.protocolValue ?: SetPumpSoundsRequest.CgmAlertAnnunciationEnum.VIBRATE

    return SetPumpSoundsRequest(
        quickBolus?.protocolValue?.id() ?: globals.quickBolusAnnun?.id()
            ?: PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
        generalRaw ?: globals.fillTubingAnnun?.id()
            ?: PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
        generalRaw ?: globals.reminderAnnun?.id()
            ?: PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
        generalRaw ?: globals.alertAnnun?.id()
            ?: PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
        generalRaw ?: globals.alarmAnnun?.id()
            ?: PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(),
        cgmProtocol.idA,
        cgmProtocol.idB,
        SetPumpSoundsRequest.ChangeBitmask.toBitmask(*changed.toTypedArray()),
    )
}
