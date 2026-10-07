package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgePhase
import com.jwoglom.controlx2.presentation.screens.sections.IncompleteCartridgeReminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PrefsCartridgeIncompleteWorkflowTest {
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
    fun reminderStateIsPumpScopedPersistedAndClearable() {
        val pump68 = IncompleteCartridgeReminder(
            pumpSid = 68,
            phase = IncompleteCartridgePhase.CHANGE_CARTRIDGE,
            startedAtEpochMillis = 1_000L,
        )
        val pump72 = IncompleteCartridgeReminder(
            pumpSid = 72,
            phase = IncompleteCartridgePhase.FILL_TUBING,
            startedAtEpochMillis = 2_000L,
        )

        prefs.setIncompleteCartridgeReminder(pump68)
        prefs.setIncompleteCartridgeReminder(pump72)

        val reloaded = Prefs(prefs.context)
        assertEquals(pump68, reloaded.incompleteCartridgeReminder(68))
        assertEquals(pump72, reloaded.incompleteCartridgeReminder(72))

        reloaded.setIncompleteCartridgeReminder(null, pumpSid = 68)
        assertNull(reloaded.incompleteCartridgeReminder(68))
        assertEquals(pump72, reloaded.incompleteCartridgeReminder(72))
    }
}
