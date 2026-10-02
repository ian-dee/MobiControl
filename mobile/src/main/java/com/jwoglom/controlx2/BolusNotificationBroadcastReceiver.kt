package com.jwoglom.controlx2

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.jwoglom.controlx2.pump.confirmBolusRequestBaseNotification
import com.jwoglom.controlx2.messaging.MessageBusFactory
import com.jwoglom.controlx2.shared.InitiateConfirmedBolusSerializer
import com.jwoglom.controlx2.shared.messaging.MessageBus
import com.jwoglom.controlx2.shared.messaging.MessageBusSender
import com.jwoglom.controlx2.shared.PumpMessageSerializer
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.controlx2.shared.ManualBolusIdentity
import com.jwoglom.controlx2.shared.isActiveBolus
import com.jwoglom.controlx2.shared.isActiveManualBolus
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import com.jwoglom.controlx2.shared.util.twoDecimalPlaces
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.InsulinUnit
import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.CurrentBolusStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.control.InitiateBolusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.shared.Hex
import com.jwoglom.controlx2.Prefs
import timber.log.Timber
import java.time.Instant

internal fun manualBolusSystemNotificationEnabled(context: Context): Boolean =
    Prefs(context).systemNotificationPreferences().manualBolusProgress

/**
 * Notification-only routing for the legacy manual-bolus Android notification.
 *
 * CurrentBolusStatusResponse is shared by manual, Quick Bolus, and Control-IQ boluses. The legacy
 * receiver also retains the most recent manual InitiateBolusRequest so it can render the requested
 * amount. Passing every pump status to that receiver lets a later automatic/Quick bolus be rendered
 * with the retained manual amount. This route carries a manual identity only while the response is
 * demonstrably part of that exact manual lifecycle.
 */
internal data class ManualBolusSystemNotificationRoute(
    val notificationIdentity: ManualBolusIdentity?,
    val nextActiveIdentity: ManualBolusIdentity?,
)

internal fun routeManualBolusSystemNotification(
    response: CurrentBolusStatusResponse,
    activeIdentity: ManualBolusIdentity?,
    responseIdentity: ManualBolusIdentity?,
): ManualBolusSystemNotificationRoute = when {
    response.isActiveManualBolus() -> {
        val exactIdentity = responseIdentity?.takeIf { it.bolusId == response.bolusId }
        ManualBolusSystemNotificationRoute(exactIdentity, exactIdentity)
    }
    response.isActiveBolus() -> {
        // A different active source cannot complete or update the retained manual notification.
        ManualBolusSystemNotificationRoute(null, null)
    }
    response.isTerminalBolusStatus() &&
        response.bolusId > 0 &&
        response.bolusSource == BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS -> {
        val exactIdentity = responseIdentity?.takeIf { it.bolusId == response.bolusId }
        ManualBolusSystemNotificationRoute(exactIdentity, null)
    }
    response.isTerminalBolusStatus() &&
        response.bolusId == 0 -> ManualBolusSystemNotificationRoute(activeIdentity, null)
    response.isTerminalBolusStatus() -> ManualBolusSystemNotificationRoute(null, null)
    else -> ManualBolusSystemNotificationRoute(null, activeIdentity)
}

/** Defense in depth at the receiver boundary: identity, source, ID, and amount must agree. */
internal fun matchesManualBolusSystemNotification(
    request: InitiateBolusRequest,
    identity: ManualBolusIdentity?,
    response: CurrentBolusStatusResponse,
): Boolean {
    if (identity == null || request.bolusID != identity.bolusId) return false
    // An inactive all-zero pump response decodes source ID 0 as QUICK_BOLUS even though there is
    // no current bolus. Its retained, service-issued manual identity is the source of truth here.
    if (response.bolusId == 0 && response.isTerminalBolusStatus()) return true
    if (response.bolusId != identity.bolusId ||
        response.bolusSource != BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
    ) return false
    return response.requestedVolume == request.totalVolume &&
        (response.isActiveManualBolus() || response.isTerminalBolusStatus())
}

class BolusNotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.i("BolusNotificationBroadcastReceiver $context $intent")

        val messageBus = MessageBusFactory.createMessageBus(context!!)

        val action = intent?.getStringExtra("action")
        val notifId = getCurrentNotificationId(context)
        when (action) {
            "INITIATE" -> {
                CommService.cancelAutoApproveStatic(context)
                validateBolusInfo(intent, context)?.let { intentRequest ->
                    Timber.w("BolusNotificationBroadcastReceiver performing $intentRequest")
                    val secretKey = prefs(context)?.getString("initiateBolusSecret", "") ?: ""

                    val rawBytes = InitiateConfirmedBolusSerializer.toBytes(
                        secretKey,
                        intentRequest
                    )

                    Timber.i(
                        "BolusNotificationBroadcastReceiver initiate-confirmed-bolus bytes: ${
                            String(
                                rawBytes
                            )
                        }"
                    )

                    val bolusSource = prefs(context)?.getString("initiateBolusSource", "") ?: ""
                    if (bolusSource == "wear") {
                        sendMessageWhenReady(messageBus, MessagePaths.TO_CLIENT_INITIATE_CONFIRMED_BOLUS, rawBytes, context)
                    } else if (bolusSource == "phone") {
                        sendMessageWhenReady(messageBus, MessagePaths.TO_SERVER_INITIATE_CONFIRMED_BOLUS, rawBytes, context)
                    }
                    if (!PumpState.actionsAffectingInsulinDeliveryEnabled()) {
                        // The same message will appear on the wearable
                        reply(
                            context, notifId, confirmBolusRequestBaseNotification(
                                context,
                                "Bolus Not Enabled",
                                "A bolus was requested, but actions affecting insulin delivery are not enabled in the phone app settings."
                            )
                        )
                        return
                    }

                    reply(
                        context, notifId, confirmBolusRequestBaseNotification(
                            context,
                            "Requesting Bolus",
                            "${bolusSummaryText(intentRequest)} " +
                                    "will be delivered"
                        )
                    )
                }
            }
            "INITIATE_RESPONSE" -> {
                getCurrentBolusToConfirm(context)?.let { initiateRequest ->
                    getInitiateBolusResponse(intent)?.let { initiateResponse ->
                        if (initiateResponse.status != 0 || initiateResponse.statusType != InitiateBolusResponse.BolusResponseStatus.SUCCESS) {
                            Timber.e("Bolus was not initiated. $initiateRequest $initiateResponse")
                            reply(
                                context,
                                notifId,
                                confirmBolusRequestBaseNotification(
                                    context,
                                    "Bolus Rejected By Pump",
                                    "The pump reported that it could not initiate the bolus: ${initiateResponse.statusType}"
                                )
                            )
                            resetBolusPrefs(context)
                            return
                        }

                        if (initiateRequest.bolusID != initiateResponse.bolusId) {
                            Timber.e("Sanity error. bolus id does not match: $initiateRequest $initiateResponse")
                            reply(
                                context,
                                notifId,
                                confirmBolusRequestBaseNotification(
                                    context,
                                    "Sanity Check Error",
                                    "Please check your pump to see if the bolus was delivered."
                                )
                            )
                            resetBolusPrefs(context)
                            return
                        }

                        val cancelIntent =
                            Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                                putExtra("action", "CANCEL")
                                intent.getByteArrayExtra(EXTRA_MANUAL_BOLUS_IDENTITY)?.let {
                                    putExtra(EXTRA_MANUAL_BOLUS_IDENTITY, it)
                                }
                            }
                        val cancelPendingIntent = PendingIntent.getBroadcast(
                            context,
                            2003,
                            cancelIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                        )

                        reply(
                            context, notifId, confirmBolusRequestBaseNotification(
                                context,
                                "Bolus Requested",
                                "The ${bolusSummaryText(initiateRequest)} is being prepared."
                            ).addAction(R.drawable.decline, "Cancel", cancelPendingIntent)
                        )
                        
                        // Save initial status and bolusId
                        prefs(context)?.edit()
                            ?.putString("lastBolusStatus", CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING.name)
                            ?.putInt("lastBolusId", initiateResponse.bolusId)
                            ?.apply()
                        
                        // Start periodic status updates
                        startBolusStatusUpdates(context, messageBus)
                    }
                }
            }
            "STATUS_UPDATE" -> {
                getCurrentBolusToConfirm(context)?.let { initiateRequest ->
                    getCurrentBolusStatusResponse(intent)?.let { statusResponse ->
                        val identity = intent.getByteArrayExtra(EXTRA_MANUAL_BOLUS_IDENTITY)
                            ?.let(ManualBolusIdentity::decode)
                        if (!matchesManualBolusSystemNotification(
                                request = initiateRequest,
                                identity = identity,
                                response = statusResponse,
                            )
                        ) {
                            Timber.w(
                                "Ignoring non-matching manual bolus system notification status: " +
                                    "requestId=${initiateRequest.bolusID}, identity=$identity, " +
                                    "statusId=${statusResponse.bolusId}, source=${statusResponse.bolusSource}",
                            )
                            return
                        }
                        // Only update notification if status has changed
                        val previousStatus = prefs(context)?.getString("lastBolusStatus", null)
                        val currentStatus = statusResponse.status.name
                        val previousBolusId = prefs(context)?.getInt("lastBolusId", -1) ?: -1
                        val currentBolusId = statusResponse.bolusId
                        
                        // Update if status changed OR if bolusId changed (especially when it becomes 0 for completion)
                        val statusChanged = previousStatus != currentStatus
                        val bolusIdChanged = previousBolusId != currentBolusId
                        val bolusCompleted = statusResponse.isTerminalBolusStatus()
                        
                        if (statusChanged || bolusIdChanged) {
                            Timber.d("BolusNotificationBroadcastReceiver status changed: $previousStatus -> $currentStatus, bolusId: $previousBolusId -> $currentBolusId")
                            
                            // Save the new status and bolusId
                            prefs(context)?.edit()
                                ?.putString("lastBolusStatus", currentStatus)
                                ?.putInt("lastBolusId", currentBolusId)
                                ?.apply()
                            
                            val notifId = getCurrentNotificationId(context)
                            
                            // Determine status text - prioritize completion detection
                            val statusText = when {
                                bolusCompleted -> "was completed."
                                statusResponse.status == CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING -> "is being prepared."
                                statusResponse.status == CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING -> "is being delivered."
                                else -> "was completed."
                            }
                            
                            // Only show cancel button if bolus is still active
                            val notificationBuilder = confirmBolusRequestBaseNotification(
                                context,
                                "Bolus Initiated",
                                "The ${bolusSummaryText(initiateRequest)} $statusText"
                            )
                            
                            if (bolusCompleted) {
                                val dismissIntent =
                                    Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                                        putExtra("action", "DISMISS")
                                    }
                                val dismissPendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    2004,
                                    dismissIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                                )
                                notificationBuilder.addAction(R.drawable.confirm, "Done", dismissPendingIntent)
                            } else {
                                val cancelIntent =
                                    Intent(context, BolusNotificationBroadcastReceiver::class.java).apply {
                                        putExtra("action", "CANCEL")
                                        intent.getByteArrayExtra(EXTRA_MANUAL_BOLUS_IDENTITY)?.let {
                                            putExtra(EXTRA_MANUAL_BOLUS_IDENTITY, it)
                                        }
                                    }
                                val cancelPendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    2003,
                                    cancelIntent,
                                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                                )
                                notificationBuilder.addAction(R.drawable.decline, "Cancel", cancelPendingIntent)
                            }
                            
                            reply(context, notifId, notificationBuilder)
                        } else {
                            Timber.d("BolusNotificationBroadcastReceiver status unchanged: $currentStatus, bolusId: $currentBolusId, skipping notification update")
                        }
                        
                        // A terminal status can retain the completed bolus ID briefly. Stop on the
                        // pump status itself rather than waiting for a later zero-ID response.
                        if (bolusCompleted) {
                            stopBolusStatusUpdates()
                            // Clear only legacy notification staging after the terminal notification is posted.
                            // Durable manual-bolus reconciliation is owned elsewhere and is intentionally untouched.
                            Handler(Looper.getMainLooper()).postDelayed({
                                resetBolusPrefs(context)
                            }, 1000)
                        }
                    }
                }
            }
            "REJECT" -> {
                CommService.cancelAutoApproveStatic(context)
                stopBolusStatusUpdates()
                reply(
                    context, notifId, confirmBolusRequestBaseNotification(
                        context,
                        "Bolus Rejected By Phone",
                        "The bolus will not be completed."
                    )
                )
                resetBolusPrefs(context)
                sendMessageWhenReady(messageBus, MessagePaths.TO_CLIENT_BOLUS_REJECTED, "from_phone".toByteArray(), context)
            }
            "DISMISS" -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notifId)
                resetBolusPrefs(context)
            }
            "CANCEL" -> {
                val identity = intent.getByteArrayExtra(EXTRA_MANUAL_BOLUS_IDENTITY)
                    ?.let(ManualBolusIdentity::decode)
                if (identity == null) {
                    reply(
                        context,
                        notifId,
                        confirmBolusRequestBaseNotification(
                            context,
                            "Open MobiControl to cancel",
                            "The pump session could not be verified from this notification.",
                        ),
                    )
                } else {
                    reply(
                        context,
                        notifId,
                        confirmBolusRequestBaseNotification(
                            context,
                            "Verifying bolus before cancellation",
                            "Re-reading current pump status before sending one cancellation request.",
                        ),
                    )
                    sendMessageWhenReady(
                        messageBus,
                        MessagePaths.TO_SERVER_CANCEL_MANUAL_BOLUS,
                        identity.encode(),
                        context,
                    )
                }
            }
            else -> {
                Timber.w("BolusNotificationBroadcastReceiver request invalid: $action ${intent?.extras}")
                reply(
                    context,
                    notifId,
                    confirmBolusRequestBaseNotification(
                        context,
                        "Bolus Receiver Request Invalid",
                        "The bolus will not be delivered."
                    )
                )
                resetBolusPrefs(context)
            }
        }
    }

    companion object {
        const val EXTRA_MANUAL_BOLUS_IDENTITY = "manualBolusIdentity"

        /** Removes only the Android progress/result presentation for the current manual bolus. */
        fun dismissSystemNotification(context: Context) {
            val notificationId = context
                .getSharedPreferences("WearX2", Context.MODE_PRIVATE)
                .getInt("initiateBolusNotificationId", 100)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
        }
    }

    private fun validateBolusInfo(intent: Intent?, context: Context?): InitiateBolusRequest? {
        val intentRequestBytes = intent?.getByteArrayExtra("request")
        val notifId = getCurrentNotificationId(context)
        if (intentRequestBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid intent: $intentRequestBytes ${intent?.extras}")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Invalid intent.")
            )
            resetBolusPrefs(context)
            return null
        }

        val currentBolusToConfirm = getCurrentBolusToConfirm(context)

        val intentRequest = PumpMessageSerializer.fromBytes(intentRequestBytes)
        val expired = checkBolusTimeExpired(context)
        if (expired != null) {
            Timber.e("Bolus expired: $intentRequest")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(
                    context,
                    "Bolus Request Expired",
                    "The bolus request expired ${shortTimeAgo(Instant.now().plusMillis(expired))}. Boluses time out 1 minute after they are requested."
                )
            )
            resetBolusPrefs(context)
            return null
        }

        if (currentBolusToConfirm == null) {
            Timber.e("No current bolus to confirm, bolus request invalid: $intentRequest")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(
                    context,
                    "Bolus Request Invalid",
                    "The bolus request was invalid: $intentRequest"
                )
            )
            resetBolusPrefs(context)
            return null
        }

        if (!intentRequest.cargo.contentEquals(currentBolusToConfirm.cargo) || intentRequest !is InitiateBolusRequest) {
            Timber.e("BolusNotificationBroadcastReceiver mismatched intent intentRequest=$intentRequest currentBolusToConfirm=$currentBolusToConfirm ${intentRequest.cargo} != ${currentBolusToConfirm.cargo}")
            reply(
                context,
                notifId,
                confirmBolusRequestBaseNotification(context, "Bolus Error", "Mismatched intent.")
            )
            resetBolusPrefs(context)
            return null
        }


        return intentRequest
    }

    private fun bolusSummaryText(intentRequest: InitiateBolusRequest): String {
        return "${
            twoDecimalPlaces(
                InsulinUnit.from1000To1(intentRequest.totalVolume)
            )
        }u bolus"
    }

    private fun getCurrentBolusToConfirm(context: Context?): InitiateBolusRequest? {
        val prefBytes = Hex.decodeHex(prefs(context)?.getString("initiateBolusRequest", ""))
        if (prefBytes == null || prefBytes.isEmpty()) {
            return null
        }

        val prefRequest = PumpMessageSerializer.fromBytes(prefBytes)
        if (prefRequest !is InitiateBolusRequest) {
            return null
        }

        return prefRequest
    }

    private fun checkBolusTimeExpired(context: Context?): Long? {
        val initiateMillis = prefs(context)?.getLong("initiateBolusTime", 0L) ?: return -1

        val nowMillis = Instant.now().toEpochMilli()

        // Bolus expires after 1 minute, or after auto-approve timeout + 30s buffer
        val autoApproveTimeout = Prefs(context!!).wearBolusAutoApproveTimeoutSeconds()
        val expiryMs = if (autoApproveTimeout > 0) {
            (autoApproveTimeout + 30) * 1000L
        } else {
            60 * 1000L
        }
        if (nowMillis - initiateMillis > expiryMs) {
            return nowMillis - initiateMillis
        }

        return null
    }

    private fun getInitiateBolusResponse(intent: Intent?): InitiateBolusResponse? {
        val intentResponseBytes = intent?.getByteArrayExtra("response")
        if (intentResponseBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid response intent")
            return null
        }

        return PumpMessageSerializer.fromBytes(intentResponseBytes) as InitiateBolusResponse
    }

    private fun getCurrentBolusStatusResponse(intent: Intent?): CurrentBolusStatusResponse? {
        val intentStatusBytes = intent?.getByteArrayExtra("status")
        if (intentStatusBytes == null) {
            Timber.w("BolusNotificationBroadcastReceiver invalid status intent")
            return null
        }

        return PumpMessageSerializer.fromBytes(intentStatusBytes) as CurrentBolusStatusResponse
    }

    private fun resetBolusPrefs(context: Context?) {
        prefs(context)?.edit()
            ?.remove("initiateBolusRequest")
            ?.remove("initiateBolusTime")
            ?.remove("lastBolusStatus")
            ?.remove("lastBolusId")
            ?.apply()
    }

    private fun getCurrentNotificationId(context: Context?): Int {
        return prefs(context)?.getInt("initiateBolusNotificationId", 100) ?: 0
    }

    private fun reply(
        context: Context?,
        bolusNotificationId: Int,
        builder: NotificationCompat.Builder
    ) {
        cancelNotif(context, bolusNotificationId)
        makeNotif(context, bolusNotificationId, builder.build())
    }

    private fun makeNotif(context: Context?, id: Int, notif: Notification) {
        if (context == null) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manualBolusSystemNotificationEnabled(context)) {
            notificationManager.cancel(id)
            return
        }
        notificationManager.notify(id, notif)
    }

    private fun cancelNotif(context: Context?, id: Int) {
        val notificationManager =
            context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        notificationManager.cancel(id)
    }

    private fun prefs(context: Context?): SharedPreferences? {
        return context?.getSharedPreferences("WearX2", Context.MODE_PRIVATE)
    }

    /**
     * Start periodic status updates for an active bolus
     */
    private fun startBolusStatusUpdates(
        context: Context,
        messageBus: MessageBus,
    ) {
        BolusStatusPolling.start(
            requestStatus = {
                sendMessageWhenReady(
                    messageBus,
                    MessagePaths.TO_PUMP_COMMANDS_BUST_CACHE,
                    PumpMessageSerializer.toBulkBytes(listOf(CurrentBolusStatusRequest())),
                    context,
                    delayMs = 0,
                )
            },
        )
    }

    /**
     * Stop periodic status updates
     */
    private fun stopBolusStatusUpdates() = BolusStatusPolling.stop()

    /**
     * Send a message through the MessageBus, ensuring the bus is ready first.
     * Uses a Handler-based approach with a small delay to allow MessageBus initialization,
     * similar to the original waitForApiClient() but non-blocking.
     * 
     * @param messageBus The MessageBus instance to use
     * @param path The message path
     * @param data The message data
     * @param context Android context
     * @param delayMs Optional delay before sending (for staggered retries)
     */
    private fun sendMessageWhenReady(
        messageBus: MessageBus,
        path: String,
        data: ByteArray,
        context: Context,
        delayMs: Long = 0
    ) {
        val handler = Handler(Looper.getMainLooper())
        val totalDelay = delayMs + 100L // Add 100ms base delay to ensure MessageBus is initialized
        
        handler.postDelayed({
            Timber.d("BolusNotificationBroadcastReceiver sending message: $path")
            messageBus.sendMessage(path, data, MessageBusSender.MOBILE_UI)
            Timber.d("BolusNotificationBroadcastReceiver message queued: $path")
        }, totalDelay)
    }

}
