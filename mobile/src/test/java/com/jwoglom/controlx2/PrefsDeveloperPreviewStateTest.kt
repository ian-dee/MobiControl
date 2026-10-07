package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PrefsDeveloperPreviewStateTest {
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
    fun simulatedSuspensionDefaultsOffAndPersistsAcrossPrefsInstances() {
        assertFalse(prefs.developerPreviewDeliverySuspended())

        prefs.setDeveloperPreviewDeliverySuspended(true)
        assertTrue(Prefs(context).developerPreviewDeliverySuspended())

        prefs.setDeveloperPreviewDeliverySuspended(false)
        assertFalse(Prefs(context).developerPreviewDeliverySuspended())
    }

    @Test
    fun simulatedTempRatePersistsAndExpiredStateIsCleared() {
        val now = 1_000_000L
        val tempRate = DeveloperPreviewTempRate(
            percent = 150,
            durationMinutes = 30,
            startedAtEpochMillis = now,
        )

        prefs.setDeveloperPreviewTempRate(tempRate)
        assertEquals(tempRate, Prefs(context).developerPreviewTempRate(now + 60_000L))
        assertEquals(null, Prefs(context).developerPreviewTempRate(now + 30 * 60_000L))
        assertFalse(
            prefs.prefs().getBoolean("developer-preview-temp-rate-active", false),
        )
    }

    @Test
    fun simulatedControlIqSettingsAndSleepSchedulesPersistAcrossPrefsInstances() {
        assertTrue(prefs.developerPreviewControlIqEnabled())
        assertEquals(170, prefs.developerPreviewControlIqWeightPounds())
        assertEquals(35, prefs.developerPreviewControlIqTotalDailyInsulin())
        assertEquals(
            DeveloperPreviewSleepSchedule(
                enabled = true,
                activeDaysBitmask = 31,
                startMinutes = 22 * 60,
                endMinutes = 6 * 60,
            ),
            prefs.developerPreviewSleepSchedule(0),
        )

        prefs.setDeveloperPreviewControlIqSettings(
            enabled = false,
            weightPounds = 185,
            totalDailyInsulinUnits = 42,
        )
        prefs.setDeveloperPreviewSleepSchedule(
            slot = 1,
            schedule = DeveloperPreviewSleepSchedule(
                enabled = true,
                activeDaysBitmask = 96,
                startMinutes = 23 * 60 + 15,
                endMinutes = 7 * 60 + 30,
            ),
        )

        val restored = Prefs(context)
        assertFalse(restored.developerPreviewControlIqEnabled())
        assertEquals(185, restored.developerPreviewControlIqWeightPounds())
        assertEquals(42, restored.developerPreviewControlIqTotalDailyInsulin())
        assertEquals(
            DeveloperPreviewSleepSchedule(
                enabled = true,
                activeDaysBitmask = 96,
                startMinutes = 23 * 60 + 15,
                endMinutes = 7 * 60 + 30,
            ),
            restored.developerPreviewSleepSchedule(1),
        )
    }

    @Test
    fun simulatedDeliveryLimitsUseMobiDefaultsAndPersist() {
        assertEquals(15_000, prefs.developerPreviewMaxBolusMilliunits())
        assertEquals(3_000L, prefs.developerPreviewBasalLimitMilliunits())

        prefs.setDeveloperPreviewMaxBolusMilliunits(10_000)
        prefs.setDeveloperPreviewBasalLimitMilliunits(200L)

        val restored = Prefs(context)
        assertEquals(10_000, restored.developerPreviewMaxBolusMilliunits())
        assertEquals(200L, restored.developerPreviewBasalLimitMilliunits())
    }
}
