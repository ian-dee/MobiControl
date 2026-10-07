package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

const val NOTIFICATION_DISMISS_RESPONSE_TIMEOUT_MILLIS = 8_000L

data class NotificationDismissalTarget(
    val type: DismissNotificationRequest.NotificationType,
    val notificationId: Long,
) {
    fun request(): DismissNotificationRequest = DismissNotificationRequest(type, notificationId)
}

data class NotificationDismissalAttempt(
    val target: NotificationDismissalTarget,
    val startedAfterSequence: Long,
    val phase: NotificationDismissalPhase = NotificationDismissalPhase.REQUESTING,
    val verifyAfterSequence: Long = -1L,
)

enum class NotificationDismissalPhase { REQUESTING, VERIFYING }

enum class NotificationDismissalOutcome { CONFIRMED, STILL_ACTIVE, REJECTED, TIMEOUT }

data class NotificationDismissalResult(
    val target: NotificationDismissalTarget,
    val outcome: NotificationDismissalOutcome,
)

/**
 * The dismiss response does not include the notification ID, so only one notification dismissal
 * may be in flight. This also turns repeated callbacks from a single swipe into a no-op.
 */
class NotificationDismissalGate(
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val staleAfterMillis: Long = NOTIFICATION_DISMISS_RESPONSE_TIMEOUT_MILLIS * 2,
) {
    private data class ActiveDismissal(
        val target: NotificationDismissalTarget,
        val startedAtMillis: Long,
    )

    private var activeDismissal: ActiveDismissal? = null

    private fun discardIfStale(nowMillis: Long) {
        val active = activeDismissal ?: return
        if (nowMillis - active.startedAtMillis >= staleAfterMillis) {
            activeDismissal = null
        }
    }

    @Synchronized
    fun begin(target: NotificationDismissalTarget): Boolean {
        val nowMillis = elapsedMillis()
        discardIfStale(nowMillis)
        if (activeDismissal != null) return false
        activeDismissal = ActiveDismissal(target, nowMillis)
        return true
    }

    @Synchronized
    fun finish(target: NotificationDismissalTarget): Boolean {
        discardIfStale(elapsedMillis())
        if (activeDismissal?.target != target) return false
        activeDismissal = null
        return true
    }

    @Synchronized
    fun active(): NotificationDismissalTarget? {
        discardIfStale(elapsedMillis())
        return activeDismissal?.target
    }
}

private val appNotificationDismissalGate = NotificationDismissalGate()

data class NotificationDismissalUiState(
    val pendingTarget: NotificationDismissalTarget?,
    val lastResult: NotificationDismissalResult?,
    val onDismiss: (Any) -> Boolean,
    val clearResult: () -> Unit,
)

/** A single response-driven dismissal lifecycle shared by every notification list in the app. */
@Composable
fun rememberNotificationDismissalUiState(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    refreshNotifications: () -> Job,
): NotificationDismissalUiState {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val responseEvent by dataStore.dismissNotificationResponseEvent.observeAsState()
    val notificationStatusEvent by dataStore.notificationStatusEvent.observeAsState()
    val gate = remember { appNotificationDismissalGate }
    var attempt by remember { mutableStateOf<NotificationDismissalAttempt?>(null) }
    var lastResult by remember { mutableStateOf<NotificationDismissalResult?>(null) }

    fun dismiss(notification: Any): Boolean {
        val target = notificationDismissalTarget(notification) ?: return false
        if (!gate.begin(target)) return false

        lastResult = null
        attempt = NotificationDismissalAttempt(
            target = target,
            startedAfterSequence = dataStore.latestPumpMessageSequence.value ?: 0L,
        )
        sendPumpCommands(SendType.STANDARD, listOf(target.request()))
        return true
    }

    LaunchedEffect(responseEvent?.sequence, attempt) {
        val activeAttempt = attempt ?: return@LaunchedEffect
        val response = responseEvent ?: return@LaunchedEffect
        if (response.sequence <= activeAttempt.startedAfterSequence) return@LaunchedEffect
        if (response.message.isStatusOK) {
            attempt = activeAttempt.copy(
                phase = NotificationDismissalPhase.VERIFYING,
                verifyAfterSequence = dataStore.latestPumpMessageSequence.value ?: response.sequence,
            )
            refreshNotifications()
        } else {
            if (!gate.finish(activeAttempt.target)) return@LaunchedEffect
            lastResult = NotificationDismissalResult(
                activeAttempt.target,
                NotificationDismissalOutcome.REJECTED,
            )
            attempt = null
            Toast.makeText(
                context,
                "The pump could not dismiss this notification",
                Toast.LENGTH_LONG,
            ).show()
            refreshNotifications()
        }
    }

    LaunchedEffect(notificationStatusEvent?.sequence, attempt) {
        val activeAttempt = attempt
            ?.takeIf { it.phase == NotificationDismissalPhase.VERIFYING }
            ?: return@LaunchedEffect
        val statusEvent = notificationStatusEvent ?: return@LaunchedEffect
        if (statusEvent.sequence <= activeAttempt.verifyAfterSequence) return@LaunchedEffect
        val stillActive = notificationStatusContainsTarget(
            message = statusEvent.message,
            target = activeAttempt.target,
        ) ?: return@LaunchedEffect
        if (!gate.finish(activeAttempt.target)) return@LaunchedEffect

        lastResult = NotificationDismissalResult(
            activeAttempt.target,
            if (stillActive) {
                NotificationDismissalOutcome.STILL_ACTIVE
            } else {
                NotificationDismissalOutcome.CONFIRMED
            },
        )
        attempt = null
        Toast.makeText(
            context,
            if (stillActive) {
                "The pump is still reporting this notification"
            } else {
                "Notification dismissed"
            },
            if (stillActive) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
    }

    LaunchedEffect(attempt) {
        val activeAttempt = attempt ?: return@LaunchedEffect
        delay(NOTIFICATION_DISMISS_RESPONSE_TIMEOUT_MILLIS)
        if (attempt != activeAttempt) return@LaunchedEffect
        if (!gate.finish(activeAttempt.target)) return@LaunchedEffect

        lastResult = NotificationDismissalResult(
            activeAttempt.target,
            NotificationDismissalOutcome.TIMEOUT,
        )
        attempt = null
        Toast.makeText(
            context,
            "No dismissal response received; notifications were refreshed",
            Toast.LENGTH_LONG,
        ).show()
        refreshNotifications()
    }

    return NotificationDismissalUiState(
        pendingTarget = attempt?.target,
        lastResult = lastResult,
        onDismiss = ::dismiss,
        clearResult = { lastResult = null },
    )
}

internal fun notificationStatusContainsTarget(
    message: Message,
    target: NotificationDismissalTarget,
): Boolean? {
    val matchingStatusType = when (target.type) {
        DismissNotificationRequest.NotificationType.ALERT -> message is AlertStatusResponse
        DismissNotificationRequest.NotificationType.REMINDER -> message is ReminderStatusResponse
        DismissNotificationRequest.NotificationType.ALARM -> message is AlarmStatusResponse
        DismissNotificationRequest.NotificationType.CGM_ALERT -> message is CGMAlertStatusResponse
    }
    if (!matchingStatusType) return null
    return NotificationBundle()
        .add(message)
        .get()
        .any { notificationDismissalTarget(it) == target }
}

fun notificationDismissalTarget(notification: Any): NotificationDismissalTarget? =
    when (notification) {
        is AlertStatusResponse.AlertResponseType -> NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALERT,
            notification.bitmask().toLong(),
        )

        is ReminderStatusResponse.ReminderType -> NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.REMINDER,
            notification.id().toLong(),
        )

        is AlarmStatusResponse.AlarmResponseType -> NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALARM,
            notification.bitmask().toLong(),
        )

        is CGMAlertStatusResponse.CGMAlert -> NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            notification.id().toLong(),
        )

        else -> null
    }
