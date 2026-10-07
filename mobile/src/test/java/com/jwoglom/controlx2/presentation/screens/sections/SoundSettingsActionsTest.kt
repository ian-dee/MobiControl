package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.SetPumpSoundsRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.PumpGlobalsResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundSettingsActionsTest {
    private val globals = PumpGlobalsResponse(
        1,
        500,
        0,
        0,
        0,
        0,
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.AUDIO_LOW.id(),
        PumpGlobalsResponse.AnnunciationEnum.AUDIO_MEDIUM.id(),
        PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(),
        PumpGlobalsResponse.AnnunciationEnum.AUDIO_LOW.id(),
        PumpGlobalsResponse.AnnunciationEnum.AUDIO_MEDIUM.id(),
    )

    @Test
    fun generalChoiceUpdatesAllGeneralCategoriesAndPreservesQuickBolus() {
        val request = buildMobiSoundSettingsRequest(
            globals = globals,
            general = MobiPumpSoundOption.BEEP,
        )

        assertEquals(PumpGlobalsResponse.AnnunciationEnum.VIBRATE.id(), request.quickBolusAnnunRaw)
        assertEquals(PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(), request.generalAnnunRaw)
        assertEquals(PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(), request.reminderAnnunRaw)
        assertEquals(PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(), request.alertAnnunRaw)
        assertEquals(PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(), request.alarmAnnunRaw)
        assertEquals(
            SetPumpSoundsRequest.ChangeBitmask.toBitmask(
                SetPumpSoundsRequest.ChangeBitmask.GENERAL,
                SetPumpSoundsRequest.ChangeBitmask.REMINDER,
                SetPumpSoundsRequest.ChangeBitmask.ALERT,
                SetPumpSoundsRequest.ChangeBitmask.ALARM,
            ),
            request.changeBitmaskRaw,
        )
    }

    @Test
    fun quickBolusChoiceChangesOnlyQuickBolus() {
        val request = buildMobiSoundSettingsRequest(
            globals = globals,
            quickBolus = MobiPumpSoundOption.BEEP,
        )

        assertEquals(PumpGlobalsResponse.AnnunciationEnum.AUDIO_HIGH.id(), request.quickBolusAnnunRaw)
        assertEquals(globals.fillTubingAnnun.id(), request.generalAnnunRaw)
        assertEquals(globals.reminderAnnun.id(), request.reminderAnnunRaw)
        assertEquals(globals.alertAnnun.id(), request.alertAnnunRaw)
        assertEquals(globals.alarmAnnun.id(), request.alarmAnnunRaw)
        assertEquals(SetPumpSoundsRequest.ChangeBitmask.QUICK_BOLUS.id(), request.changeBitmaskRaw)
    }

    @Test
    fun cgmChoiceUsesDocumentedHypoRepeatEncodingAndCgmBitOnly() {
        val request = buildMobiSoundSettingsRequest(
            globals = globals,
            cgm = MobiCgmSoundOption.HYPO_REPEAT,
        )

        assertEquals(0, request.cgmAlertAnnunA)
        assertEquals(3, request.cgmAlertAnnunB)
        assertEquals(SetPumpSoundsRequest.ChangeBitmask.CGM_ALERT.id(), request.changeBitmaskRaw)
    }

    @Test
    fun officialMobiChoicesDoNotExposeX2VolumeLevels() {
        assertEquals(listOf("Beep", "Vibrate"), MobiPumpSoundOption.values().map { it.displayName })
        assertEquals(
            listOf("Beep", "Vibrate", "HypoRepeat"),
            MobiCgmSoundOption.values().map { it.displayName },
        )
        assertEquals(listOf(10, 20, 30), MOBI_SNOOZE_DURATIONS_MINUTES)
    }
}
