package com.jwoglom.controlx2

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundConnectionAccessTest {
    @Test
    fun `battery exemption request targets this application`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = BackgroundConnectionAccess.requestBatteryOptimizationExemptionIntent(context)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    @Test
    fun `Samsung manufacturer match is case insensitive`() {
        assertTrue(BackgroundConnectionAccess.isSamsungDevice("Samsung"))
        assertTrue(BackgroundConnectionAccess.isSamsungDevice("SAMSUNG"))
        assertFalse(BackgroundConnectionAccess.isSamsungDevice("Google"))
    }

    @Test
    fun `Samsung never sleeping intent follows documented entry point`() {
        val intent = BackgroundConnectionAccess.samsungNeverSleepingAppsIntent()

        assertEquals("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY", intent.action)
        assertEquals("com.samsung.android.lool", intent.`package`)
        assertEquals(2, intent.getIntExtra("activity_type", -1))
    }
}
