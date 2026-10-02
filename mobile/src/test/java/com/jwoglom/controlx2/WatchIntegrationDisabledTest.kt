package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.messaging.BroadcastMessageBus
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.IntegrationGates
import com.jwoglom.controlx2.shared.enums.DeviceRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WatchIntegrationDisabledTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        MessageBusFactory.clearInstance()
        context.getSharedPreferences("WearX2", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun buildGate_isDisabled() {
        assertFalse(IntegrationGates.WATCH_INTEGRATION_ENABLED)
    }

    @Test
    fun phoneAlwaysActsAsPumpHost_whenStoredRoleWasClient() {
        Prefs(context).setDeviceRole(DeviceRole.CLIENT)

        assertEquals(DeviceRole.PUMP_HOST, Prefs(context).deviceRole())
    }

    @Test
    fun messageTransportStaysLocal() {
        val bus = MessageBusFactory.createMessageBus(context)

        assertTrue(bus is BroadcastMessageBus)
        bus.close()
    }
}
