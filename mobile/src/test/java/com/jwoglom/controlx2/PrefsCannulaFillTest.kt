package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PrefsCannulaFillTest {
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
    fun pumpConfirmedTimestampPersistsPerPump() {
        prefs.setLastCannulaFillEpochMillis(1_700_000_000_000L, pumpSid = 101)
        prefs.setLastCannulaFillEpochMillis(1_800_000_000_000L, pumpSid = 202)

        assertEquals(1_700_000_000_000L, prefs.lastCannulaFillEpochMillis(101))
        assertEquals(1_800_000_000_000L, prefs.lastCannulaFillEpochMillis(202))
        assertNull(prefs.lastCannulaFillEpochMillis(303))
    }

    @Test
    fun reminderPreferenceDefaultsToThreeDaysAtNineAmAndPersistsUserChoice() {
        assertEquals(3, prefs.cannulaReminderDays())
        assertEquals(9 * 60, prefs.cannulaReminderTimeMinutes())

        prefs.setCannulaReminderPreference(dayCount = 2, timeOfDayMinutes = 7 * 60 + 15)

        val reloaded = Prefs(context)
        assertEquals(2, reloaded.cannulaReminderDays())
        assertEquals(7 * 60 + 15, reloaded.cannulaReminderTimeMinutes())
    }

    @Test
    fun primeAmountDefaultsToPointSevenUnitsAndPersistsUserChoice() {
        assertEquals(0.7, prefs.cannulaPrimeAmount(), 0.0001)

        prefs.setCannulaPrimeAmount(1.1)

        assertEquals(1.1, Prefs(context).cannulaPrimeAmount(), 0.0001)
    }

    @Test
    fun reminderPreferenceRejectsValuesThePumpCannotRepresent() {
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaReminderPreference(dayCount = 0, timeOfDayMinutes = 9 * 60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaReminderPreference(dayCount = 5, timeOfDayMinutes = 9 * 60)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaReminderPreference(dayCount = 3, timeOfDayMinutes = 24 * 60)
        }
    }

    @Test
    fun primeAmountRejectsUnsupportedVolumesAndPrecision() {
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaPrimeAmount(0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaPrimeAmount(2.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setCannulaPrimeAmount(0.75)
        }
    }
}
