package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PrefsCgmAlertSettingsTest {
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun developerPreviewStartsWithMobiDefaults() {
        assertTrue(prefs.developerPreviewCgmHighEnabled())
        assertEquals(200, prefs.developerPreviewCgmHighThreshold())
        assertEquals(0, prefs.developerPreviewCgmHighRepeatMinutes())
        assertTrue(prefs.developerPreviewCgmLowEnabled())
        assertEquals(80, prefs.developerPreviewCgmLowThreshold())
        assertFalse(prefs.developerPreviewCgmRiseEnabled())
        assertEquals(3, prefs.developerPreviewCgmRiseRate())
        assertFalse(prefs.developerPreviewCgmFallEnabled())
        assertTrue(prefs.developerPreviewCgmOutOfRangeEnabled())
        assertEquals(20, prefs.developerPreviewCgmOutOfRangeDelayMinutes())
    }

    @Test
    fun developerPreviewSettingsPersist() {
        prefs.setDeveloperPreviewCgmAlertSettings(
            highEnabled = true,
            highThreshold = 175,
            highRepeatMinutes = 60,
            lowEnabled = true,
            lowThreshold = 75,
            lowRepeatMinutes = 30,
            riseEnabled = true,
            riseRate = 2,
            fallEnabled = true,
            fallRate = 2,
            outOfRangeEnabled = true,
            outOfRangeDelayMinutes = 45,
        )

        assertEquals(175, prefs.developerPreviewCgmHighThreshold())
        assertEquals(60, prefs.developerPreviewCgmHighRepeatMinutes())
        assertEquals(75, prefs.developerPreviewCgmLowThreshold())
        assertEquals(30, prefs.developerPreviewCgmLowRepeatMinutes())
        assertTrue(prefs.developerPreviewCgmRiseEnabled())
        assertEquals(2, prefs.developerPreviewCgmRiseRate())
        assertEquals(45, prefs.developerPreviewCgmOutOfRangeDelayMinutes())
    }

    @Test
    fun developerPreviewRejectsValuesThePumpUiCannotRepresent() {
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setDeveloperPreviewCgmAlertSettings(
                true, 401, 0,
                true, 80, 0,
                false, 3,
                false, 3,
                true, 20,
            )
        }
    }
}
