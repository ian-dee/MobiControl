@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.shared.pumpReminderPresentation
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HighestAamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse

val requiredProgress = 0.5f

@Composable
fun NotificationItem(
    notification: Any,
    dismissalPending: Boolean,
    onDismiss: (Any) -> Boolean,
) {
    val context = LocalContext.current
    val cgmPresentation = (notification as? CGMAlertStatusResponse.CGMAlert)
        ?.let(::cgmAlertPresentation)
    val alertPresentation = (notification as? AlertStatusResponse.AlertResponseType)
        ?.let(::alertPresentation)
    val reminderPresentation = (notification as? ReminderStatusResponse.ReminderType)
        ?.let(::pumpReminderPresentation)

    val canDismiss = notification is AlertStatusResponse.AlertResponseType ||
            notification is ReminderStatusResponse.ReminderType ||
            notification is AlarmStatusResponse.AlarmResponseType ||
            notification is CGMAlertStatusResponse.CGMAlert

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                    if (canDismiss && !dismissalPending && onDismiss(notification)) {
                        Toast.makeText(context, "Dismissing notification", Toast.LENGTH_SHORT).show()
                    }
                }
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            // A swipe is an action trigger, not optimistic deletion. Keep the row present until
            // the pump accepts the request and a status refresh removes it.
            return@rememberSwipeToDismissBoxState false
        },
        positionalThreshold = { it * requiredProgress }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { DismissBackground(dismissState)},
        enableDismissFromStartToEnd = canDismiss && !dismissalPending,
        enableDismissFromEndToStart = canDismiss && !dismissalPending,
        content = {
            ListItem(
                headlineContent = {
                    Text(
                        when (notification) {
                            is AlertStatusResponse.AlertResponseType -> alertPresentation!!.headline
                            is ReminderStatusResponse.ReminderType -> reminderPresentation!!.headline
                            is AlarmStatusResponse.AlarmResponseType -> "Alarm: ${notification.name}"
                            is CGMAlertStatusResponse.CGMAlert -> cgmPresentation!!.headline
                            is HighestAamResponse -> "MALFUNCTION: ${notification.errorString}"
                            else -> "$notification"
                        }
                    )
                },
                supportingContent = {
                    when (notification) {
                        is AlertStatusResponse.AlertResponseType -> alertPresentation
                            ?.supportingText
                            ?.let { Text(it) }

                        is AlarmStatusResponse.AlarmResponseType -> Text(
                            notification.description ?: ""
                        )

                        is ReminderStatusResponse.ReminderType -> Text(
                            reminderPresentation!!.supportingText
                        )

                        is CGMAlertStatusResponse.CGMAlert -> cgmPresentation
                            ?.supportingText
                            ?.let { Text(it) }

                        is HighestAamResponse -> Text("This alert cannot be cleared and DIY app developers cannot assist you with this problem.\nFor further instructions please contact Tandem technical support and reference the above code.")
                        else -> {}
                    }
                },
                leadingContent = {
                    when (notification) {
                        is AlertStatusResponse.AlertResponseType -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "Alert"
                        )

                        is ReminderStatusResponse.ReminderType -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "Reminder"
                        )

                        is AlarmStatusResponse.AlarmResponseType -> Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Alarm"
                        )

                        is CGMAlertStatusResponse.CGMAlert -> Icon(
                            Icons.Filled.Info,
                            contentDescription = "CGM Alert"
                        )

                        is HighestAamResponse -> Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Malfunction"
                        )
                    }
                }
            )
        }
    )
}

val darkRedColor = Color(0xFFFF1744)
@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
            if (dismissState.progress >= requiredProgress) {
                darkRedColor
            } else {
                darkRedColor.copy(alpha = (dismissState.progress / (1.5 * requiredProgress)).toFloat())
            }
        }
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Dismiss"
        )
        Spacer(modifier = Modifier)
        Icon(
            Icons.Default.Delete,
            contentDescription = "Dismiss"
        )
    }
}
