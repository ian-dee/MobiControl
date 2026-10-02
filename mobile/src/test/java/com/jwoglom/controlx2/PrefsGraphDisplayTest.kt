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
class PrefsGraphDisplayTest {
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
    fun defaultsShowBothLimitsAndColorBothOutOfRangeDirections() {
        val settings = prefs.graphDisplayPreferences()

        assertEquals(70, settings.lowerThresholdMgdl)
        assertEquals(180, settings.upperThresholdMgdl)
        assertTrue(settings.showLowerLimitLine)
        assertTrue(settings.showUpperLimitLine)
        assertTrue(settings.colorDotsBelowLowerLimit)
        assertTrue(settings.colorDotsAboveUpperLimit)
    }

    @Test
    fun graphDisplaySettingsPersistIndependently() {
        prefs.setGraphDisplayPreferences(
            GraphDisplayPreferences(
                lowerThresholdMgdl = 75,
                upperThresholdMgdl = 190,
                showLowerLimitLine = false,
                showUpperLimitLine = true,
                colorDotsBelowLowerLimit = true,
                colorDotsAboveUpperLimit = false,
            ),
        )

        val settings = prefs.graphDisplayPreferences()
        assertEquals(75, settings.lowerThresholdMgdl)
        assertEquals(190, settings.upperThresholdMgdl)
        assertFalse(settings.showLowerLimitLine)
        assertTrue(settings.showUpperLimitLine)
        assertTrue(settings.colorDotsBelowLowerLimit)
        assertFalse(settings.colorDotsAboveUpperLimit)
    }

    @Test
    fun invalidOrReversedLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setGraphDisplayPreferences(
                GraphDisplayPreferences(lowerThresholdMgdl = 180, upperThresholdMgdl = 180),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.setGraphDisplayPreferences(
                GraphDisplayPreferences(lowerThresholdMgdl = 39, upperThresholdMgdl = 180),
            )
        }
    }

    @Test
    fun corruptStoredThresholdPairFallsBackToSafeDefaults() {
        prefs.prefs().edit()
            .putInt("graph-display-lower-threshold-mgdl", 250)
            .putInt("graph-display-upper-threshold-mgdl", 100)
            .commit()

        val settings = prefs.graphDisplayPreferences()
        assertEquals(70, settings.lowerThresholdMgdl)
        assertEquals(180, settings.upperThresholdMgdl)
    }
}
