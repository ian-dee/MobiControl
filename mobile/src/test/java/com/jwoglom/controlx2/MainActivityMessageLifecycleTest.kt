package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.testutil.RecordingMessageBus
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MainActivityMessageLifecycleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var messageBus: RecordingMessageBus

    @Before
    fun setup() {
        context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean("developer-preview-enabled", true)
            .putBoolean("tos-accepted", true)
            .putBoolean("service-enabled", true)
            .commit()
        dataStore = DataStore()
        messageBus = RecordingMessageBus()
        MessageBusFactory.setInstanceForTesting(messageBus)
    }

    @After
    fun teardown() {
        MessageBusFactory.setInstanceForTesting(null)
        dataStore = DataStore()
        context.getSharedPreferences("WearX2", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `UI message listener follows started lifecycle without duplicate registration`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()

        assertEquals(1, messageBus.listenerCount)
        assertEquals(
            1,
            messageBus.messagesForPath(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS).size,
        )
        assertEquals(
            "include_profile",
            messageBus.lastMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)?.dataString,
        )

        controller.pause().stop()
        assertEquals(0, messageBus.listenerCount)

        messageBus.clear()
        controller.start().resume()
        assertEquals(1, messageBus.listenerCount)
        assertEquals(
            1,
            messageBus.messagesForPath(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS).size,
        )

        controller.pause().stop().destroy()
        assertEquals(0, messageBus.listenerCount)
    }

    @Test
    fun `authoritative not-connected snapshot resolves unknown UI connection state`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        dataStore.pumpConnected.value = null

        messageBus.simulateIncomingMessage(
            MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED,
            "service_snapshot_disconnected".toByteArray(),
        )

        assertEquals(false, dataStore.pumpConnected.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun `complete profile cache requests status without restarting profile read`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        dataStore.idpManager.value = IDPManager().also {
            it.processMessage(ProfileStatusResponse(0, -1, -1, -1, -1, -1, -1, 0))
        }
        dataStore.pumpConnected.value = true

        controller.start().resume()

        assertEquals(
            "",
            messageBus.lastMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)?.dataString,
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun `duplicate connected snapshot retains current profile read state`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        dataStore.pumpConnected.value = true
        dataStore.initialProfileReadError.value = "retain-marker"

        messageBus.simulateIncomingMessage(
            MessagePaths.FROM_PUMP_PUMP_CONNECTED,
            "Tandem Mobi ***68".toByteArray(),
        )

        assertEquals(true, dataStore.pumpConnected.value)
        assertEquals("retain-marker", dataStore.initialProfileReadError.value)
        controller.pause().stop().destroy()
    }

    @Test
    fun `out-of-order profile detail is ignored until profile status arrives`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()

        messageBus.simulateIncomingMessage(
            MessagePaths.FROM_PUMP_RECEIVE_MESSAGE,
            com.jwoglom.controlx2.shared.PumpMessageSerializer.toBytes(
                IDPSettingsResponse(0, "Daily", 1, 300, 15_000, true),
            ),
        )

        assertFalse(dataStore.idpManager.value?.isComplete == true)
        assertEquals(null, dataStore.profileReadProgress.value?.totalProfiles)
        assertEquals(0, dataStore.profileReadProgress.value?.profilesRead)
        controller.pause().stop().destroy()
    }
}
