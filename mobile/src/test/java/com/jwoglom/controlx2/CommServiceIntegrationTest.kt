package com.jwoglom.controlx2

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.AutomaticBolusCancelOutcome
import com.jwoglom.controlx2.shared.AutomaticBolusCancelResult
import com.jwoglom.controlx2.shared.AutomaticBolusIdentity
import com.jwoglom.controlx2.shared.ManualBolusCancelOutcome
import com.jwoglom.controlx2.shared.ManualBolusCancelResult
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.QuickBolusCancelOutcome
import com.jwoglom.controlx2.shared.QuickBolusCancelResult
import com.jwoglom.controlx2.shared.QuickBolusIdentity
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.testutil.RecordingMessageBus
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ApiVersionRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ControlIQIOBRequest
import com.jwoglom.pumpx2.shared.Hex
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import com.jwoglom.pumpx2.pump.bluetooth.TandemBluetoothHandler
import com.jwoglom.pumpx2.pump.bluetooth.TandemPump
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.jwoglom.pumpx2.pump.messages.bluetooth.Characteristic
import com.jwoglom.pumpx2.pump.messages.helpers.Bytes
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ApiVersionResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ControlIQIOBResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBatteryV2Response
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentEGVGuiDataResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogStreamResponse
import com.welie.blessed.BluetoothPeripheral
import java.time.Instant

/**
 * Integration tests for CommService covering all major behaviors:
 * - Message routing
 * - Response caching
 * - Bolus security
 * - Data forwarding to wear
 * - Pump connection lifecycle
 * - PumpFinder mode
 * - Service lifecycle
 *
 * These tests serve as a safety net before decomposing CommService (Phase 0).
 * They test behavior (message in → message out) rather than implementation details.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CommServiceIntegrationTest {

    private lateinit var context: Context
    private lateinit var messageBus: RecordingMessageBus
    private lateinit var serviceController: ServiceController<CommService>
    private lateinit var service: CommService
    private lateinit var prefs: SharedPreferences

    // Captured pump reference from TandemBluetoothHandler.getInstance() —
    // this is the Pump inner class instance, typed as TandemPump.
    private var capturedPump: TandemPump? = null
    private val mockPeripheral: BluetoothPeripheral = mockk(relaxed = true)
    private lateinit var mockBtHandler: TandemBluetoothHandler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Prefs class stores everything in "WearX2" SharedPreferences
        prefs = context.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("tos-accepted", true)
            .putBoolean("service-enabled", true)
            .putBoolean("pumpfinder-service-enabled", false)
            .commit()

        // Create and inject test MessageBus
        messageBus = RecordingMessageBus()
        MessageBusFactory.setInstanceForTesting(messageBus)

        // Mock TandemBluetoothHandler — capture the TandemPump callback
        mockkStatic(TandemBluetoothHandler::class)
        mockBtHandler = mockk(relaxed = true)
        every { TandemBluetoothHandler.getInstance(any(), any(), any()) } answers {
            capturedPump = secondArg<TandemPump>()
            mockBtHandler
        }

        // Mock peripheral name for extractPumpSid()
        every { mockPeripheral.name } returns "tslim X2 ***12345"

        // Mock TandemPumpFinder constructor to prevent BT scanning
        mockkConstructor(TandemPumpFinder::class)
        every { anyConstructed<TandemPumpFinder>().startScan() } returns Unit
        every { anyConstructed<TandemPumpFinder>().stop() } returns Unit

        // Build the service via Robolectric
        serviceController = Robolectric.buildService(CommService::class.java)
    }

    @After
    fun teardown() {
        BolusTimeoutReminder(context).cancelSession()
        try {
            serviceController.destroy()
        } catch (_: Exception) {}
        MessageBusFactory.setInstanceForTesting(null)
        unmockkAll()
        prefs.edit().clear().commit()
    }

    // --- Helpers ---

    @Test
    fun `developer preview service can stop before message bus initialization`() {
        prefs.edit().putBoolean("developer-preview-enabled", true).commit()

        serviceController.create()
        shadowOf(Looper.getMainLooper()).idle()
        serviceController.destroy()
    }

    @Test
    fun `routine connection notification is tap only and reuses root activity`() {
        startServiceNormal()

        val createNotification = CommService::class.java
            .getDeclaredMethod("createNotification")
            .apply { isAccessible = true }
        val notification = createNotification.invoke(service) as Notification

        assertNull(notification.fullScreenIntent)
        assertNotNull(notification.contentIntent)
        val openIntent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(
            ComponentName(context, MainActivity::class.java),
            openIntent.component,
        )
        assertEquals(CommService.ACTION_OPEN_CONNECTION_STATUS, openIntent.action)
        assertTrue(openIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(openIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)

        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
    }

    /**
     * Create and start the service in normal (pump comm) mode.
     */
    private fun startServiceNormal() {
        prefs.edit().putBoolean("pumpfinder-service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        // Process pending looper messages
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear() // Clear setup messages
    }

    /**
     * Create and start the service in PumpFinder mode.
     */
    private fun startServicePumpFinder() {
        prefs.edit().putBoolean("pumpfinder-service-enabled", true).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()
    }

    /**
     * Send a message to the service as if it came from outside.
     */
    private fun sendMessage(path: String, data: String = "") {
        service.handleMessageReceived(path, data.toByteArray(), "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sendMessage(path: String, data: ByteArray) {
        service.handleMessageReceived(path, data, "test-node")
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Start service in normal mode, trigger INIT_PUMP_COMM via onStartCommand,
     * and simulate a connected pump using the @VisibleForTesting bridge method.
     * This calls initializeConnection() which sets up real state (session, SID,
     * isConnected) without Thread.sleep loops or external dependencies
     * (NightscoutSyncWorker, HistoryLogFetcher, etc).
     */
    private fun startServiceAndConnectPump() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(500) // Let handler thread process INIT_PUMP_COMM
        shadowOf(Looper.getMainLooper()).idle()

        // At this point, INIT_PUMP_COMM has created the Pump and called startScan()
        // Our mock captured the pump reference via getInstance()
        assertNotNull("Pump should have been captured via TandemBluetoothHandler.getInstance()", capturedPump)

        // Use the @VisibleForTesting method to set up connected state
        service.simulateConnectedPump(mockPeripheral)

        messageBus.clear()
    }

    /**
     * Access the lastResponseMessage cache on CommService via pumpCommState.
     */
    @Suppress("UNCHECKED_CAST")
    private fun getLastResponseMessageCache(): MutableMap<Pair<Characteristic, Byte>, Pair<com.jwoglom.pumpx2.pump.messages.Message, Instant>> {
        return service.pumpCommState.lastResponseMessage
    }

    /**
     * Populate the response cache with a message, as if it had been received from the pump.
     */
    private fun populateCache(message: com.jwoglom.pumpx2.pump.messages.Message, age: Instant = Instant.now()) {
        val cache = getLastResponseMessageCache()
        cache[Pair(message.characteristic, message.opCode())] = Pair(message, age)
    }

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    @Test
    fun service_onTaskRemoved_retainsForegroundPumpService() {
        startServiceNormal()

        assertFalse(shadowOf(service).isStoppedBySelf)
        service.onTaskRemoved(null)

        assertFalse("Dismissing the UI must not stop pump communication", shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun service_onCreate_normalMode_createsPumpCommHandler() {
        startServiceNormal()
        // Service should have registered a message listener on the bus
        // and be in normal comm mode. Verify by checking it responds to
        // service status request with comm-started (not pump-finder-started)
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(
            "Expected /to-server/comm-started",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
        assertFalse(
            "Should NOT send pump-finder-started in normal mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        )
        assertTrue(
            "Disconnected service snapshot must resolve an unknown UI state",
            messageBus.hasMessageWithData(
                MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED,
                "service_snapshot_disconnected",
            ),
        )
        assertTrue(
            "Disconnected service snapshot must include current hydration state",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE),
        )
    }

    @Test
    fun disconnectedPump_eachServiceStatusRequestGetsAuthoritativeSnapshot() {
        startServiceNormal()

        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)

        assertEquals(
            2,
            messageBus.messagesForPath(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED).size,
        )
        assertEquals(
            2,
            messageBus.messagesForPath(MessagePaths.TO_CLIENT_PUMP_HYDRATION_STATE).size,
        )
    }

    @Test
    fun service_onCreate_pumpFinderMode_createsPumpFinderHandler() {
        startServicePumpFinder()
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(
            "Expected /to-server/pump-finder-started",
            messageBus.hasMessage(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        )
        assertFalse(
            "Should NOT send comm-started in pump-finder mode",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }

    @Test
    fun service_onCreate_tosNotAccepted_shortCircuits() {
        prefs.edit().putBoolean("tos-accepted", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        // When TOS is not accepted, service should not set up handlers.
        // Sending a message should not crash (handlers are null).
        // And no status messages should have been sent.
        val statusMessages = messageBus.messagesForPath(MessagePaths.TO_SERVER_COMM_STARTED) +
            messageBus.messagesForPath(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        assertTrue("No status messages expected when TOS not accepted", statusMessages.isEmpty())
    }

    @Test
    fun service_onCreate_serviceDisabled_shortCircuits() {
        prefs.edit().putBoolean("service-enabled", false).commit()
        serviceController.create()
        service = serviceController.get()
        shadowOf(Looper.getMainLooper()).idle()
        val statusMessages = messageBus.messagesForPath(MessagePaths.TO_SERVER_COMM_STARTED) +
            messageBus.messagesForPath(MessagePaths.TO_SERVER_PUMP_FINDER_STARTED)
        assertTrue("No status messages expected when service disabled", statusMessages.isEmpty())
    }

    @Test
    fun service_onStartCommand_alreadyRunning_withoutEstablishedPump_doesNotRearm() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)

        verify(exactly = 0) { mockBtHandler.ensureConnectionActive(true) }
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
    }

    @Test
    fun service_onStartCommand_alreadyRunning_withEstablishedPump_rearmsConnectionOnce() {
        prefs.edit().putInt("current-pump-sid", 68).commit()
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)

        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)

        verify(exactly = 1) { mockBtHandler.ensureConnectionActive(true) }
        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        assertTrue(messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
    }

    @Test
    fun explicitConnectionCheck_rearmsTransportOnce() {
        prefs.edit().putInt("current-pump-sid", 68).commit()
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100)

        sendMessage(MessagePaths.TO_SERVER_ENSURE_PUMP_CONNECTION)
        Thread.sleep(100)

        verify(exactly = 1) { mockBtHandler.ensureConnectionActive(true) }
    }

    @Test
    fun automaticBolusCancellation_isBlockedWhenInsulinActionsAreDisabled() {
        startServiceAndConnectPump()
        prefs.edit().putBoolean("insulin-delivery-actions", false).commit()
        val identity = AutomaticBolusIdentity(12345, 700_000L, 42)

        sendMessage(MessagePaths.TO_SERVER_CANCEL_AUTOMATIC_BOLUS, identity.encode())
        repeat(20) {
            if (messageBus.hasMessage(MessagePaths.TO_CLIENT_AUTOMATIC_BOLUS_CANCEL_RESULT)) {
                return@repeat
            }
            Thread.sleep(25)
        }

        val result = messageBus.lastMessage(MessagePaths.TO_CLIENT_AUTOMATIC_BOLUS_CANCEL_RESULT)
            ?.data
            ?.let(AutomaticBolusCancelResult::decode)
        assertEquals(AutomaticBolusCancelOutcome.ACTIONS_DISABLED, result?.outcome)
        assertEquals(identity, result?.identity)
    }

    @Test
    fun manualBolusCancellation_isBlockedWhenInsulinActionsAreDisabled() {
        startServiceAndConnectPump()
        prefs.edit().putBoolean("insulin-delivery-actions", false).commit()
        val identity = ManualBolusIdentity(12345, 700_000L, 42)

        sendMessage(MessagePaths.TO_SERVER_CANCEL_MANUAL_BOLUS, identity.encode())
        repeat(20) {
            if (messageBus.hasMessage(MessagePaths.TO_CLIENT_MANUAL_BOLUS_CANCEL_RESULT)) {
                return@repeat
            }
            Thread.sleep(25)
        }

        val result = messageBus.lastMessage(MessagePaths.TO_CLIENT_MANUAL_BOLUS_CANCEL_RESULT)
            ?.data
            ?.let(ManualBolusCancelResult::decode)
        assertEquals(ManualBolusCancelOutcome.ACTIONS_DISABLED, result?.outcome)
        assertEquals(identity, result?.identity)
    }

    @Test
    fun quickBolusCancellation_isBlockedWhenInsulinActionsAreDisabled() {
        startServiceAndConnectPump()
        prefs.edit().putBoolean("insulin-delivery-actions", false).commit()
        val identity = QuickBolusIdentity(12345, 700_000L, 42)

        sendMessage(MessagePaths.TO_SERVER_CANCEL_QUICK_BOLUS, identity.encode())
        repeat(20) {
            if (messageBus.hasMessage(MessagePaths.TO_CLIENT_QUICK_BOLUS_CANCEL_RESULT)) {
                return@repeat
            }
            Thread.sleep(25)
        }

        val result = messageBus.lastMessage(MessagePaths.TO_CLIENT_QUICK_BOLUS_CANCEL_RESULT)
            ?.data
            ?.let(QuickBolusCancelResult::decode)
        assertEquals(QuickBolusCancelOutcome.ACTIONS_DISABLED, result?.outcome)
        assertEquals(identity, result?.identity)
    }

    // =========================================================================
    // Message Routing
    // =========================================================================

    @Test
    fun routing_serviceStatusAcknowledged_stopsPolling() {
        startServiceNormal()
        // After acknowledgment, requesting status should still respond,
        // but the periodic sender should stop (we verify no duplicate sends)
        sendMessage(MessagePaths.TO_SERVER_SERVICE_STATUS_ACKNOWLEDGED)
        messageBus.clear()
        // Now advance time — no periodic status should be sent
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5))
        assertFalse(
            "No periodic comm-started after acknowledgment",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )
    }


    // =========================================================================
    // Bolus Security
    // =========================================================================

    @Test
    fun bolus_bolusCommandOnNormalPath_rejected() {
        startServiceNormal()
        // InitiateBolusRequest on the normal SEND_PUMP_COMMAND path should be blocked
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage(MessagePaths.TO_PUMP_COMMAND, msgBytes)
        shadowOf(Looper.getMainLooper()).idle()
        // The bolus should be blocked (logged as "SEND_PUMP_COMMAND blocked bolus command")
        // It should NOT be forwarded to the pump
        // We verify it doesn't crash and doesn't send a bolus confirmation
        assertFalse(
            "Bolus should not be confirmed via normal command path",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BOLUS_BLOCKED_SIGNATURE)
        )
    }

    @Test
    fun bolus_authenticatedPhoneRequest_isSignedAndSubmittedWithoutSecondConfirmation() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()
        val unfinishedBolusReminder = BolusTimeoutReminder(context)
        unfinishedBolusReminder.startSession(sessionId = "authenticated-phone-request")

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage(MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE, msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should set up bolus prefs
        assertNotNull(
            "initiateBolusRequest should be stored",
            prefs.getString("initiateBolusRequest", null)
        )
        assertNotNull(
            "initiateBolusSecret should be stored",
            prefs.getString("initiateBolusSecret", null)
        )
        assertEquals("phone", prefs.getString("initiateBolusSource", null))
        assertEquals(
            "The authenticated phone path must emit exactly one signed submission",
            1,
            messageBus.messagesForPath(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS).size,
        )
        assertFalse(
            "The authenticated phone path must not create a second confirmation dialog",
            messageBus.hasMessage(MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG),
        )

        val secret = prefs.getString("initiateBolusSecret", null)!!
        val signedPayload = messageBus.lastMessage(
            MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS,
        )!!.data
        val decoded = InitiateConfirmedBolusSerializer.fromBytes(secret, signedPayload)
        assertTrue("The submitted payload must have a valid one-time signature", decoded.left)
        assertEquals(bolusRequest, decoded.right)
        assertNull(
            "The unfinished-bolus reminder must clear after durable authenticated submission",
            unfinishedBolusReminder.activeSession(),
        )
    }

    @Test
    fun bolus_confirmRequest_wear_isRejectedWhileWatchIntegrationIsDisabled() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", 0).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage(MessagePaths.TO_SERVER_BOLUS_REQUEST_WEAR, msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull("A disabled watch request must not enter the bolus lifecycle", prefs.getString("initiateBolusSource", null))
        assertNull("A disabled watch request must not stage a bolus", prefs.getString("initiateBolusRequest", null))
        assertNull("A disabled watch request must not create an authorization secret", prefs.getString("initiateBolusSecret", null))
        assertFalse(messageBus.hasMessage(MessagePaths.TO_CLIENT_BOLUS_MIN_NOTIFY_THRESHOLD))
        assertFalse(messageBus.hasMessage(MessagePaths.TO_CLIENT_WEAR_AUTO_APPROVE_TIMEOUT))
    }

    @Test
    fun bolus_initiateConfirmedBolus_validSignature_forwardedToHandler() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()

        // Set up the secret in prefs (mimicking the confirmBolusRequest flow)
        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        // Create a signed bolus message
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT send blocked-bolus-signature since signature is valid
        assertFalse(
            "Valid signature should not be blocked",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
    }

    @Test
    fun bolus_initiateConfirmedBolus_invalidSignature_blocked() {
        startServiceNormal()

        // Set up a different secret than what we sign with
        prefs.edit().putString("initiateBolusSecret", "correct-secret").commit()

        // Sign with wrong secret
        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes("wrong-secret", bolusRequest)

        sendMessage(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()

        // Should send blocked-bolus-signature
        assertTrue(
            "Invalid signature should be blocked",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
    }

    @Test
    fun bolus_cancel_clearsBolusPrefs() {
        startServiceNormal()

        // Set up bolus prefs
        prefs.edit()
            .putString("initiateBolusRequest", "test-hex")
            .putString("initiateBolusSecret", "secret")
            .putLong("initiateBolusTime", System.currentTimeMillis())
            .commit()

        sendMessage(MessagePaths.TO_SERVER_BOLUS_CANCEL)

        // initiateBolusRequest and initiateBolusTime should be cleared
        assertNull(prefs.getString("initiateBolusRequest", null))
        assertEquals(0L, prefs.getLong("initiateBolusTime", 0L))
    }

    // =========================================================================
    // Data Forwarding to Wear
    // =========================================================================

    @Test
    fun sendWearCommMessage_sendsViaMessageBus() {
        startServiceNormal()
        service.sendWearCommMessage("/test/path", "test-data".toByteArray())
        assertTrue(messageBus.hasMessage("/test/path"))
        assertEquals(
            MessageBusSender.COMM_SERVICE,
            messageBus.lastMessage("/test/path")?.sender
        )
    }

    @Test
    fun sendWearCommMessage_preservesData() {
        startServiceNormal()
        val data = "hello-world"
        service.sendWearCommMessage("/test/data-check", data.toByteArray())
        val msg = messageBus.lastMessage("/test/data-check")
        assertNotNull(msg)
        assertEquals(data, msg!!.dataString)
    }

    // =========================================================================
    // Pump Connection Lifecycle
    // =========================================================================

    @Test
    fun lifecycle_isPumpConnected_whenNotConnected_sendsPumpNotConnected() {
        startServiceNormal()
        sendMessage(MessagePaths.TO_SERVER_IS_PUMP_CONNECTED)

        // Need to process the Handler message
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(100) // Allow handler thread to process
        shadowOf(Looper.getMainLooper()).idle()

        // Since pump was never initialized in test, should get not-connected
        assertTrue(
            "Expected pump-not-connected when pump handler not initialized",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED)
        )
    }

    @Test
    fun disconnectedBulkRead_coalescesNotConnectedSignal_andOnlyRearmsTransport() {
        startServiceNormal()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        messageBus.clear()

        val bulkBytes = PumpMessageSerializer.toBulkBytes(
            listOf(ApiVersionRequest(), ControlIQIOBRequest()),
        )
        sendMessage(MessagePaths.TO_PUMP_COMMANDS, bulkBytes)
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "One disconnected bulk read should publish one coalesced status signal",
            1,
            messageBus.messagesForPath(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED).size,
        )
        verify(atLeast = 1) { mockBtHandler.ensureConnectionActive(false) }
    }

    @Test
    fun lifecycle_pumpReadyForHistoryFetch_whenNoSession_returnsFalse() {
        startServiceNormal()
        assertFalse(
            "No active session, should return false",
            service.isPumpReadyForHistoryFetch()
        )
    }

    @Test
    fun lifecycle_getPumpSession_whenNoSession_returnsNull() {
        startServiceNormal()
        assertNull(
            "No active session, should return null",
            service.getPumpSession()
        )
    }

    // =========================================================================
    // PumpFinder Mode
    // =========================================================================

    @Test
    fun pumpFinder_stopPumpFinder_withInitComm_transitionsToNormalMode() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        // Transition to normal comm mode
        sendMessage(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, "init_comm")
        shadowOf(Looper.getMainLooper()).idle()

        // Should send comm-started
        assertTrue(
            "Expected comm-started after transition",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED)
        )

        // pumpfinder-service-enabled pref should be false now
        assertFalse(prefs.getBoolean("pumpfinder-service-enabled", true))
    }

    @Test
    fun pumpFinder_stopPumpFinder_withoutInitComm_stopsOnly() {
        startServicePumpFinder()
        serviceController.startCommand(0, 0)
        shadowOf(Looper.getMainLooper()).idle()
        messageBus.clear()

        // Stop without transitioning
        sendMessage(MessagePaths.TO_SERVER_STOP_PUMP_FINDER, "")
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT send comm-started (just stopped finder)
        assertFalse(messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED))
    }

    @Test
    fun bolus_authenticatedPhoneRequest_ignoresLegacyNotificationThreshold() {
        startServiceNormal()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()
        val thresholdMilliunits = InsulinUnit.from1To1000(5.0) // 5 units threshold
        prefs.edit().putLong("bolus-confirmation-insulin-threshold", thresholdMilliunits.toLong()).commit()

        val bolusRequest = InitiateBolusRequest(500, 1, 0, 0, 0, 0, 0, 0)
        val msgBytes = PumpMessageSerializer.toBytes(bolusRequest)
        sendMessage(MessagePaths.TO_SERVER_BOLUS_REQUEST_PHONE, msgBytes)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(prefs.getString("initiateBolusSecret", null))
        assertEquals(
            1,
            messageBus.messagesForPath(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS).size,
        )
        assertFalse(messageBus.hasMessage(MessagePaths.TO_SERVER_BOLUS_CONFIRM_DIALOG))
    }

    // =========================================================================
    // Connected Pump — Lifecycle
    // =========================================================================

    @Test
    fun connectedPump_isPumpReadyForHistoryFetch_returnsTrue() {
        startServiceAndConnectPump()
        assertTrue(
            "isPumpReadyForHistoryFetch should be true when session is active",
            service.isPumpReadyForHistoryFetch()
        )
    }

    @Test
    fun connectedPump_getPumpSession_returnsNonNull() {
        startServiceAndConnectPump()
        assertNotNull(
            "getPumpSession should return active session",
            service.getPumpSession()
        )
    }

    @Test
    fun connectedPump_isPumpConnected_sendsPumpConnected() {
        startServiceAndConnectPump()
        sendMessage(MessagePaths.TO_SERVER_IS_PUMP_CONNECTED)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected /from-pump/pump-connected when pump is connected",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED)
        )
    }

    @Test
    fun connectedPump_serviceStatusRepublishesConnectionForRecreatedUi() {
        startServiceAndConnectPump()

        sendMessage(MessagePaths.TO_SERVER_REQUEST_SERVICE_STATUS)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "Expected service status response",
            messageBus.hasMessage(MessagePaths.TO_SERVER_COMM_STARTED),
        )
        assertTrue(
            "Expected active pump connection to be republished",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_CONNECTED),
        )
    }

    // =========================================================================
    // Connected Pump — Response Caching
    // =========================================================================

    @Test
    fun connectedPump_cachedCommands_freshHit_returnsCachedResponse() {
        startServiceAndConnectPump()

        // Populate cache with a fresh ApiVersionResponse
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        // Send cached-commands request for ApiVersionRequest
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage(MessagePaths.TO_PUMP_CACHED_COMMANDS, bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should return cached response via /from-pump/receive-cached-message
        assertTrue(
            "Expected cached response via /from-pump/receive-cached-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE)
        )
    }

    @Test
    fun connectedPump_cachedCommands_expiredHit_sendsNewCommand() {
        startServiceAndConnectPump()

        // Populate cache with an expired response (older than 30s)
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now().minusSeconds(60))

        // Send cached-commands request
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage(MessagePaths.TO_PUMP_CACHED_COMMANDS, bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should NOT return cached response (it's expired)
        assertFalse(
            "Expired cache should not return /from-pump/receive-cached-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE)
        )
        // The command should have been forwarded to the pump (via pump.command)
        // We can't directly verify this without the pump mock, but we verify no crash
    }

    @Test
    fun connectedPump_cachedCommands_miss_doesNotReturnCached() {
        startServiceAndConnectPump()

        // Don't populate cache — it's empty

        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage(MessagePaths.TO_PUMP_CACHED_COMMANDS, bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // No cached response should be sent
        assertFalse(
            "Cache miss should not return /from-pump/receive-cached-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_CACHED_MESSAGE)
        )
    }

    @Test
    fun connectedPump_bustCache_removesCacheEntry() {
        startServiceAndConnectPump()

        // Populate cache
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        // Verify cache is populated
        val cache = getLastResponseMessageCache()
        val key = Pair(response.characteristic, response.opCode())
        assertTrue("Cache should contain the response", cache.containsKey(key))

        // Send bust-cache command
        val requests = listOf(ApiVersionRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage(MessagePaths.TO_PUMP_COMMANDS_BUST_CACHE, bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Cache entry should have been removed
        assertFalse(
            "bust-cache should remove the cache entry",
            cache.containsKey(key)
        )
    }

    @Test
    fun connectedPump_debugMessageCache_returnsPopulatedCache() {
        startServiceAndConnectPump()

        // Populate cache with a response
        val response = ApiVersionResponse(2, 5)
        populateCache(response, Instant.now())

        sendMessage(MessagePaths.TO_PUMP_DEBUG_MESSAGE_CACHE)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should respond with debug-message-cache containing data
        assertTrue(
            "Expected /from-pump/debug-message-cache with populated cache",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_DEBUG_MESSAGE_CACHE)
        )
        val msg = messageBus.lastMessage(MessagePaths.FROM_PUMP_DEBUG_MESSAGE_CACHE)
        assertNotNull(msg)
        assertTrue("Cache data should be non-empty", msg!!.data.isNotEmpty())
    }

    // =========================================================================
    // Connected Pump — Message Forwarding to Wear
    // =========================================================================

    @Test
    fun connectedPump_onReceiveMessage_forwardsViaFromPump() {
        startServiceAndConnectPump()

        // Trigger onReceiveMessage directly on the captured pump
        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "All responses should be forwarded via /from-pump/receive-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_batteryResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = CurrentBatteryV2Response(0, 66, 0, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        // Battery responses should be forwarded to wear via /to-client/service-receive-message
        assertTrue(
            "Battery response should be forwarded to wear",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_iobResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = ControlIQIOBResponse(1450, 0, 0, 0, 0)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "IOB response should be forwarded to wear",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_cgmResponse_forwardedToWear() {
        startServiceAndConnectPump()

        val response = CurrentEGVGuiDataResponse(1710000000, 123, 1, 2)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "CGM response should be forwarded to wear",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_otherResponse_notForwardedToWear() {
        startServiceAndConnectPump()

        // ApiVersionResponse is NOT one of the three types forwarded to wear
        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        // Should be forwarded via /from-pump/receive-message but NOT /to-client/service-receive-message
        assertTrue(
            "Response should go to /from-pump/receive-message",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_RECEIVE_MESSAGE)
        )
        assertFalse(
            "Non-battery/IOB/CGM response should NOT go to /to-client/service-receive-message",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_SERVICE_RECEIVE_MESSAGE)
        )
    }

    @Test
    fun connectedPump_onReceiveMessage_populatesCache() {
        startServiceAndConnectPump()

        val cache = getLastResponseMessageCache()
        assertTrue("Cache should be empty initially", cache.isEmpty())

        val response = ApiVersionResponse(2, 5)
        capturedPump!!.onReceiveMessage(mockPeripheral, response)
        shadowOf(Looper.getMainLooper()).idle()

        val key = Pair(response.characteristic, response.opCode())
        assertTrue(
            "onReceiveMessage should populate the response cache",
            cache.containsKey(key)
        )
    }

    @Test
    fun connectedPump_quickBolusStatusCreatesPumpIssuedLifecycleWithoutPhonePermission() {
        startServiceAndConnectPump()
        capturedPump!!.onReceiveMessage(
            mockPeripheral,
            TimeSinceResetResponse(700_100L, 100L),
        )
        capturedPump!!.onReceiveMessage(
            mockPeripheral,
            CurrentBolusStatusResponse(
                CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING.id,
                7_743,
                700_105L,
                500L,
                BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS.id(),
                1,
            ),
        )
        repeat(20) {
            if (Prefs(context).quickBolusOperation() != null) return@repeat
            Thread.sleep(25)
        }

        val operation = Prefs(context).quickBolusOperation()
        assertEquals(7_743, operation?.identity?.bolusId)
        assertEquals(500L, operation?.requestedMilliunits)
        assertNull(Prefs(context).manualBolusOperation())
        assertNull(Prefs(context).bolusPermissionOperation())
        assertTrue(messageBus.hasMessage(MessagePaths.TO_CLIENT_QUICK_BOLUS_OPERATION))
        assertTrue(BolusStatusPolling.isRunning())

        capturedPump!!.onReceiveMessage(
            mockPeripheral,
            CurrentBolusStatusResponse(
                CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID.id,
                0,
                0L,
                0L,
                BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS.id(),
                1,
            ),
        )
        assertTrue(
            "Polling must continue while Last Bolus/history terminal evidence is still unresolved",
            BolusStatusPolling.isRunning(),
        )

        val terminal = BolusDeliveryHistoryLog(
            700_110L,
            10_001L,
            7_743,
            2,
            setOf(BolusDeliveryHistoryLog.BolusType.FOOD2),
            BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            0,
            500,
            0,
            0,
            0,
            500,
        )
        capturedPump!!.onReceiveMessage(
            mockPeripheral,
            HistoryLogStreamResponse(1, 1, listOf(terminal.cargo)),
        )
        repeat(20) {
            if (Prefs(context).quickBolusOperation()?.isUnresolved == false) return@repeat
            Thread.sleep(25)
        }
        assertFalse(Prefs(context).quickBolusOperation()?.isUnresolved ?: true)
        assertFalse(BolusStatusPolling.isRunning())
    }

    // =========================================================================
    // Connected Pump — Bolus with Connection
    // =========================================================================

    @Test
    fun connectedPump_bolus_validSignature_sentToPump() {
        startServiceAndConnectPump()
        prefs.edit().putBoolean("insulin-delivery-actions", true).commit()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Valid signature should NOT be blocked
        assertFalse(
            "Valid signature should not be blocked",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
        // Should NOT send bolus-not-enabled since insulin-delivery-actions is true
        assertFalse(
            "Insulin delivery is enabled, should not send bolus-not-enabled",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED)
        )
    }

    @Test
    fun connectedPump_bolus_insulinDeliveryDisabled_blocked() {
        startServiceAndConnectPump()
        // Explicitly disable insulin delivery
        prefs.edit().putBoolean("insulin-delivery-actions", false).commit()

        val secret = Hex.encodeHexString(Bytes.getSecureRandom10Bytes())
        prefs.edit().putString("initiateBolusSecret", secret).commit()

        val bolusRequest = InitiateBolusRequest(1000, 1, 0, 0, 0, 0, 0, 0)
        val signedBytes = InitiateConfirmedBolusSerializer.toBytes(secret, bolusRequest)

        sendMessage(MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, signedBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Signature is valid, but insulin delivery is disabled — should be blocked
        assertFalse(
            "Signature is valid, should not block for signature",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BLOCKED_BOLUS_SIGNATURE)
        )
        assertTrue(
            "Insulin delivery disabled should send /to-client/bolus-not-enabled",
            messageBus.hasMessage(MessagePaths.TO_CLIENT_BOLUS_NOT_ENABLED)
        )
    }

    @Test
    fun connectedPump_command_routedToPump() {
        startServiceAndConnectPump()

        // Send a non-bolus command to the connected pump
        val request = ApiVersionRequest()
        val msgBytes = PumpMessageSerializer.toBytes(request)
        sendMessage(MessagePaths.TO_PUMP_COMMAND, msgBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        // Should not send pump-not-connected (pump IS connected)
        assertFalse(
            "Connected pump should not send pump-not-connected",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED)
        )
    }

    @Test
    fun connectedPump_bulkCommands_routedToPump() {
        startServiceAndConnectPump()

        val requests = listOf(ApiVersionRequest(), ControlIQIOBRequest())
        val bulkBytes = PumpMessageSerializer.toBulkBytes(requests)
        sendMessage(MessagePaths.TO_PUMP_COMMANDS, bulkBytes)
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "Connected pump should not send pump-not-connected for bulk commands",
            messageBus.hasMessage(MessagePaths.FROM_PUMP_PUMP_NOT_CONNECTED)
        )
    }
}
