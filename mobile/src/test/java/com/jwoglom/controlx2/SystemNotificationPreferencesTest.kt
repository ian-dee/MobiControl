package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SystemNotificationPreferencesTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun everyOptionalFamilyDefaultsEnabled() {
        assertEquals(SystemNotificationPreferences(), prefs.systemNotificationPreferences())
    }

    @Test
    fun everyOptionalFamilyPersistsIndependently() {
        val disabled = SystemNotificationPreferences(
            incompleteBolusReminders = false,
            manualBolusProgress = true,
            automaticBolusActivity = false,
            cgmGlucoseLevelAlerts = true,
            cgmTrendAlerts = false,
            cgmSensorAndCalibrationAlerts = true,
            lowPumpBattery = false,
            lowCartridge = true,
            emptyCartridge = false,
            siteChangeReminders = true,
        )

        prefs.setSystemNotificationPreferences(disabled)

        assertEquals(disabled, Prefs(context).systemNotificationPreferences())
    }

    @Test
    fun manualBolusSystemPresentationReadsOnlyItsOwnPreference() {
        prefs.setSystemNotificationPreferences(
            prefs.systemNotificationPreferences().copy(manualBolusProgress = false),
        )

        assertFalse(manualBolusSystemNotificationEnabled(context))
    }
}
