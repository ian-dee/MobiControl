@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.theme.PorMenuPageHeader
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.HighestAamResponse
import com.jwoglom.controlx2.presentation.theme.PorMenuPage
import com.jwoglom.controlx2.presentation.theme.PorMenuPageDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuNotice
import com.jwoglom.controlx2.presentation.theme.PorNotificationSurface
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.components.LoadSpinner
import com.jwoglom.controlx2.presentation.screens.sections.components.NotificationItem
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.util.LifecycleStateObserver
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.pumpNotificationStatusRequests
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.PumpState
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownApiVersion
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun Notifications(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    historyLogViewModel: HistoryLogViewModel? = null
) {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val deviceName = ds.setupDeviceName.observeAsState()
    val notificationBundle by ds.notificationBundle.observeAsState()
    val notificationStatusEvent by ds.notificationStatusEvent.observeAsState()

    val refreshScope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(true) }
    var refreshAttempt by remember { mutableStateOf<NotificationRefreshAttempt?>(null) }
    var refreshAttemptId by remember { mutableStateOf(0L) }
    var refreshError by remember { mutableStateOf<String?>(null) }

    fun fetchDataStoreFields(type: SendType) {
        sendPumpCommands(type, notificationsCommands)
    }

    fun refresh() = refreshScope.launch {
        if (!Prefs(context).serviceEnabled()) {
            refreshing = false
            refreshError = "Pump communication is disabled."
            return@launch
        }
        Timber.i("reloading Notifications with force")
        refreshing = true
        refreshError = null
        refreshAttemptId += 1L
        refreshAttempt = NotificationRefreshAttempt(
            id = refreshAttemptId,
            acceptEventsAfterSequence = ds.latestPumpMessageSequence.value ?: 0L,
        )

        ds.notificationBundle.value = null
        fetchDataStoreFields(SendType.BUST_CACHE)
    }

    val notificationDismissal = rememberNotificationDismissalUiState(
        sendPumpCommands = sendPumpCommands,
        refreshNotifications = ::refresh,
    )

    val state = rememberPullRefreshState(refreshing, ::refresh)

    LifecycleStateObserver(lifecycleOwner = LocalLifecycleOwner.current, onStop = {
        refreshAttempt = null
        refreshing = false
    }) {
        Timber.i("reloading Notifications from onStart lifecyclestate")
        refresh()
    }

    LaunchedEffect(intervalOf(60)) {
        if (refreshAttempt == null) {
            Timber.i("reloading Notifications from interval")
            refresh()
        }
    }

    LaunchedEffect(notificationStatusEvent?.sequence, refreshAttempt) {
        val attempt = refreshAttempt ?: return@LaunchedEffect
        val event = notificationStatusEvent
        if (isFreshReminderStatus(attempt, event?.sequence, event?.message)) {
            Timber.i("Notifications loading completed by fresh ReminderStatusResponse")
            refreshAttempt = null
            refreshing = false
            refreshError = null
        }
    }

    LaunchedEffect(refreshAttempt) {
        val attempt = refreshAttempt ?: return@LaunchedEffect
        delay(NOTIFICATION_REFRESH_TIMEOUT_MILLIS)
        if (refreshAttempt != attempt) return@LaunchedEffect
        Timber.w("Notifications loading timed out without fresh reminder status")
        refreshAttempt = null
        refreshing = false
        refreshError = "Pump reminder status was not received. Pull to retry."
    }

    PorMenuPage(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        PorMenuPageHeader("Notifications")
        PorMenuPageDivider()

        val notifications = notificationBundle?.get()?.toList().orEmpty()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pullRefresh(state)
        ) {
            if (showNotificationPullRefreshIndicator(refreshing)) {
                PullRefreshIndicator(
                    refreshing = false,
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(10f),
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp),
                content = {
                    item {
                        val model = determinePumpModel(deviceName.value ?: "")
                        if (model == KnownDeviceModel.TSLIM_X2) {
                            Line("Notifications cannot be dismissed on this device model (${model}).")
                            Line("")
                        }
                    }

                    Timber.i("Notifications fetched: ${notifications}")
                    if (refreshing) {
                        item {
                            PorMenuNotice { LoadSpinner("Loading notifications...") }
                        }
                    } else if (refreshError != null) {
                        item {
                            PorMenuNotice {
                                Line(refreshError!!)
                                Line("")
                            }
                        }
                    } else if (notifications.isEmpty()) {
                        item {
                            PorMenuNotice {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                                    Spacer(Modifier.height(64.dp))
                                    Line("No notifications", style = TextStyle(textAlign = TextAlign.Center))
                                }
                            }
                        }
                    }
                    notifications.forEach { notification ->
                        item(key = notificationDismissalTarget(notification)?.toString() ?: notification.toString()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .wrapContentSize(Alignment.TopStart)
                            ) {
                                PorNotificationSurface(
                                    kind = when (notification) {
                                        is ReminderStatusResponse.ReminderType -> "Reminder"
                                        is AlarmStatusResponse.AlarmResponseType -> "Pump alarm"
                                        is HighestAamResponse -> "Pump malfunction"
                                        else -> "Pump notification"
                                    },
                                    urgent = notification is AlarmStatusResponse.AlarmResponseType || notification is HighestAamResponse,
                                ) {
                                    NotificationItem(
                                        notification = notification,
                                        dismissalPending =
                                            notificationDismissal.pendingTarget ==
                                                notificationDismissalTarget(notification),
                                        onDismiss = notificationDismissal.onDismiss,
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

fun notificationsApiVersion(): ApiVersion {
    var apiVersion = PumpState.getPumpAPIVersion()
    if (apiVersion == null) {
        apiVersion = KnownApiVersion.API_V2_5.get()
    }
    return apiVersion
}

val notificationsCommands = listOf(
    HomeScreenMirrorRequest(),
    *pumpNotificationStatusRequests(notificationsApiVersion()).toTypedArray(),
)

@Preview(showBackground = true)
@Composable
internal fun NotificationsDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            Notifications(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun NotificationsDefaultPreview_WithNotifications() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.cgmSetupG6TxId.value = "ABC123"
            LocalDataStore.current.cgmSetupG6SensorCode.value = "1234"
            Notifications(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
            )
        }
    }
}
