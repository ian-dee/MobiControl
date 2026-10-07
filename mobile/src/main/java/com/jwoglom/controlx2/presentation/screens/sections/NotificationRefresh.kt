package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse

const val NOTIFICATION_REFRESH_TIMEOUT_MILLIS = 10_000L

data class NotificationRefreshAttempt(
    val id: Long,
    val acceptEventsAfterSequence: Long,
)

internal fun showNotificationPullRefreshIndicator(refreshing: Boolean): Boolean = !refreshing

internal fun isFreshReminderStatus(
    attempt: NotificationRefreshAttempt,
    eventSequence: Long?,
    message: Message?,
): Boolean = eventSequence != null &&
    eventSequence > attempt.acceptEventsAfterSequence &&
    message is ReminderStatusResponse
