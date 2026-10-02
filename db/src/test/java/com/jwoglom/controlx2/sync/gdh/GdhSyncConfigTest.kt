package com.jwoglom.controlx2.sync.gdh

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GdhSyncConfigTest {
    @Test
    fun exportIsOptInAndIobDefaultsOn() {
        val config = GdhSyncConfig()

        assertFalse(config.enabled)
        assertTrue(config.sendPumpIob)
    }

    @Test
    fun persistedValuesLoadIndependently() {
        val prefs = mockk<SharedPreferences>()
        every { prefs.getBoolean("gdh_enabled", false) } returns true
        every { prefs.getBoolean("gdh_send_pump_iob", true) } returns false

        val config = GdhSyncConfig.load(prefs)

        assertTrue(config.enabled)
        assertFalse(config.sendPumpIob)
    }
}
