package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.request.control.ResumePumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SuspendPumpingRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.HomeScreenMirrorRequest
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.LoadStatusRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val DELIVERY_TRANSITION_RESPONSE_TIMEOUT_MS = 5_000L
internal const val DELIVERY_TRANSITION_STATUS_TIMEOUT_MS = 8_000L
internal const val DELIVERY_TRANSITION_STATUS_RECHECK_MS = 1_000L

enum class DeliveryTransitionAction {
    SUSPEND,
    RESUME,
}

enum class DeliveryTransitionPhase {
    AWAITING_RESPONSE,
    VERIFYING_STATUS,
    RECONCILING,
    STATUS_UNAVAILABLE,
}

/**
 * Persisted single-flight state for app-initiated Suspend/Resume commands.
 *
 * The command is sent exactly once, immediately after this record is committed. A restored record
 * can only issue read-only status requests; it must never replay the state-changing command.
 */
data class DeliveryTransitionOperation(
    val pumpSid: Int,
    val action: DeliveryTransitionAction,
    val phase: DeliveryTransitionPhase,
    val startedAtEpochMillis: Long,
    val acceptResponseAfterSequence: Long,
    val acceptStatusAfterSequence: Long,
) {
    fun encode(): String = listOf(
        ENCODING_VERSION,
        pumpSid,
        action.name,
        phase.name,
        startedAtEpochMillis,
        acceptResponseAfterSequence,
        acceptStatusAfterSequence,
    ).joinToString("|")

    companion object {
        private const val ENCODING_VERSION = 1

        fun decode(encoded: String?): DeliveryTransitionOperation? = runCatching {
            val parts = encoded?.split('|') ?: return null
            if (parts.size != 7 || parts[0].toInt() != ENCODING_VERSION) return null
            DeliveryTransitionOperation(
                pumpSid = parts[1].toInt(),
                action = DeliveryTransitionAction.valueOf(parts[2]),
                phase = DeliveryTransitionPhase.valueOf(parts[3]),
                startedAtEpochMillis = parts[4].toLong(),
                acceptResponseAfterSequence = parts[5].toLong(),
                acceptStatusAfterSequence = parts[6].toLong(),
            )
        }.getOrNull()
    }
}

internal enum class DeliveryStatusResolution {
    IGNORE,
    CONFIRMED,
    SOURCE_STATE,
}

internal fun beginDeliveryTransition(
    action: DeliveryTransitionAction,
    pumpSid: Int,
    currentPumpMessageSequence: Long,
    nowEpochMillis: Long,
): DeliveryTransitionOperation = DeliveryTransitionOperation(
    pumpSid = pumpSid,
    action = action,
    phase = DeliveryTransitionPhase.AWAITING_RESPONSE,
    startedAtEpochMillis = nowEpochMillis,
    acceptResponseAfterSequence = currentPumpMessageSequence,
    acceptStatusAfterSequence = currentPumpMessageSequence,
)

internal fun DeliveryTransitionOperation.afterAcceptedResponse(
    currentPumpMessageSequence: Long,
    nowEpochMillis: Long,
): DeliveryTransitionOperation = copy(
    phase = DeliveryTransitionPhase.VERIFYING_STATUS,
    startedAtEpochMillis = nowEpochMillis,
    acceptResponseAfterSequence = currentPumpMessageSequence,
    acceptStatusAfterSequence = currentPumpMessageSequence,
)

internal fun DeliveryTransitionOperation.beginReconciliation(
    currentPumpMessageSequence: Long,
    nowEpochMillis: Long,
): DeliveryTransitionOperation = copy(
    phase = DeliveryTransitionPhase.RECONCILING,
    startedAtEpochMillis = nowEpochMillis,
    acceptStatusAfterSequence = currentPumpMessageSequence,
)

internal fun DeliveryTransitionOperation.restoreForReconciliation(
    currentPumpSid: Int,
    currentPumpMessageSequence: Long,
    nowEpochMillis: Long,
): DeliveryTransitionOperation? = takeIf { it.pumpSid == currentPumpSid }?.beginReconciliation(
    currentPumpMessageSequence = currentPumpMessageSequence,
    nowEpochMillis = nowEpochMillis,
)

internal fun DeliveryTransitionOperation.resolveFreshStatus(
    statusSequence: Long,
    basalStatus: BasalStatus?,
): DeliveryStatusResolution {
    if (phase == DeliveryTransitionPhase.AWAITING_RESPONSE ||
        statusSequence <= acceptStatusAfterSequence ||
        basalStatus == null ||
        basalStatus == BasalStatus.UNKNOWN
    ) {
        return DeliveryStatusResolution.IGNORE
    }
    val matchesRequestedState = when (action) {
        DeliveryTransitionAction.SUSPEND -> basalStatus == BasalStatus.PUMP_SUSPENDED
        DeliveryTransitionAction.RESUME -> basalStatus != BasalStatus.PUMP_SUSPENDED
    }
    return if (matchesRequestedState) {
        DeliveryStatusResolution.CONFIRMED
    } else {
        DeliveryStatusResolution.SOURCE_STATE
    }
}

internal fun DeliveryTransitionOperation.sourceStateNotice(): String = when (action) {
    DeliveryTransitionAction.SUSPEND ->
        "The pump still reports insulin delivery active. You may explicitly try Stop Insulin again."
    DeliveryTransitionAction.RESUME ->
        "The pump still reports insulin delivery suspended. You may explicitly try Resume Insulin again."
}

internal fun DeliveryTransitionOperation.statusUnavailable(
    nowEpochMillis: Long,
): DeliveryTransitionOperation = copy(
    phase = DeliveryTransitionPhase.STATUS_UNAVAILABLE,
    startedAtEpochMillis = nowEpochMillis,
)

internal fun DeliveryTransitionOperation.pendingLabel(): String = when (phase) {
    DeliveryTransitionPhase.AWAITING_RESPONSE -> when (action) {
        DeliveryTransitionAction.SUSPEND -> "Suspending insulin..."
        DeliveryTransitionAction.RESUME -> "Resuming insulin..."
    }
    DeliveryTransitionPhase.VERIFYING_STATUS -> "Confirming pump status..."
    DeliveryTransitionPhase.RECONCILING -> "Checking pump status..."
    DeliveryTransitionPhase.STATUS_UNAVAILABLE -> "Pump status unavailable"
}

internal data class DeliveryTransitionController(
    val operation: DeliveryTransitionOperation?,
    val pumpConnected: Boolean,
    val notice: String?,
    val requestSuspend: () -> Unit,
    val requestResume: () -> Unit,
    val reconcile: () -> Unit,
    val dismissNotice: () -> Unit,
)

/**
 * One application-level owner for every phone Suspend/Resume entry point.
 *
 * All state-changing sends originate in [requestTransition]. Recovery paths call only
 * [requestFreshState], which contains read-only requests.
 */
@Composable
internal fun rememberDeliveryTransitionController(
    sendPumpCommands: (SendType, List<Message>) -> Unit,
): DeliveryTransitionController {
    val context = LocalContext.current
    val ds = LocalDataStore.current
    val prefs = remember(context) { Prefs(context) }
    val scope = rememberCoroutineScope()
    val developerPreviewEnabled = prefs.developerPreviewEnabled()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpSid = ds.pumpSid.observeAsState(prefs.currentPumpSid())
    val basalStatus = ds.basalStatus.observeAsState()
    val basalStatusEvent = ds.basalStatusEvent.observeAsState()
    val loadStatusResponse = ds.loadStatusResponse.observeAsState()
    val latestPumpMessageSequence = ds.latestPumpMessageSequence.observeAsState(0L)
    val resumePumpingResponseEvent = ds.resumePumpingResponseEvent.observeAsState()
    val suspendPumpingResponseEvent = ds.suspendPumpingResponseEvent.observeAsState()

    var operation by remember {
        mutableStateOf(
            prefs.deliveryTransitionOperation()?.restoreForReconciliation(
                currentPumpSid = pumpSid.value,
                currentPumpMessageSequence = latestPumpMessageSequence.value,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
    }
    var notice by remember { mutableStateOf<String?>(null) }

    fun updateOperation(next: DeliveryTransitionOperation?) {
        operation = next
        prefs.setDeliveryTransitionOperation(next)
    }

    fun requestFreshState() {
        if (developerPreviewEnabled || pumpConnected.value != true) return
        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(HomeScreenMirrorRequest(), LoadStatusRequest()),
        )
    }

    fun reconcile() {
        val current = operation ?: return
        updateOperation(
            current.beginReconciliation(
                currentPumpMessageSequence = latestPumpMessageSequence.value,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
        if (pumpConnected.value == true) {
            requestFreshState()
        } else {
            notice =
                "The pump is disconnected. Reconnect it, then the app will check delivery status without repeating the command."
        }
    }

    fun requestTransition(action: DeliveryTransitionAction) {
        if (operation != null) return
        val currentStatus = basalStatus.value
        if (action == DeliveryTransitionAction.SUSPEND &&
            currentStatus == BasalStatus.PUMP_SUSPENDED
        ) {
            notice = "The pump already reports insulin delivery suspended."
            return
        }
        if (action == DeliveryTransitionAction.RESUME) {
            if (currentStatus != BasalStatus.PUMP_SUSPENDED) {
                notice = "The pump does not report a manual insulin suspension."
                return
            }
            val resumeDecision = evaluateCartridgeResume(loadStatusResponse.value)
            if (!developerPreviewEnabled && !resumeDecision.canResume) {
                notice = resumeDecision.message
                requestFreshState()
                return
            }
        }
        if (!developerPreviewEnabled && pumpConnected.value != true) {
            notice = "The pump is disconnected. Reconnect and check pump status before trying again."
            return
        }

        val next = beginDeliveryTransition(
            action = action,
            pumpSid = pumpSid.value,
            currentPumpMessageSequence = latestPumpMessageSequence.value,
            nowEpochMillis = System.currentTimeMillis(),
        )
        // Commit before the single authorized state-changing send.
        updateOperation(next)
        notice = null

        if (developerPreviewEnabled) {
            scope.launch {
                delay(300)
                val suspended = action == DeliveryTransitionAction.SUSPEND
                prefs.setDeveloperPreviewDeliverySuspended(suspended)
                ds.basalStatus.value = if (suspended) BasalStatus.PUMP_SUSPENDED else BasalStatus.ON
                updateOperation(null)
            }
            return
        }

        sendPumpCommands(
            SendType.BUST_CACHE,
            listOf(
                when (action) {
                    DeliveryTransitionAction.SUSPEND -> SuspendPumpingRequest()
                    DeliveryTransitionAction.RESUME -> ResumePumpingRequest()
                },
            ),
        )
    }

    LaunchedEffect(Unit) {
        // Persist normalized recovery state, or clear a record belonging to another pump.
        prefs.setDeliveryTransitionOperation(operation)
    }

    LaunchedEffect(pumpSid.value) {
        val current = operation ?: return@LaunchedEffect
        if (current.pumpSid != pumpSid.value) updateOperation(null)
    }

    LaunchedEffect(pumpConnected.value) {
        val current = operation ?: return@LaunchedEffect
        if (pumpConnected.value == true) {
            if (current.phase == DeliveryTransitionPhase.RECONCILING ||
                current.phase == DeliveryTransitionPhase.STATUS_UNAVAILABLE
            ) {
                reconcile()
            }
        } else if (current.phase != DeliveryTransitionPhase.STATUS_UNAVAILABLE &&
            current.phase != DeliveryTransitionPhase.RECONCILING
        ) {
            updateOperation(
                current.beginReconciliation(
                    currentPumpMessageSequence = latestPumpMessageSequence.value,
                    nowEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    val responseEvent = when (operation?.action) {
        DeliveryTransitionAction.SUSPEND -> suspendPumpingResponseEvent.value
        DeliveryTransitionAction.RESUME -> resumePumpingResponseEvent.value
        null -> null
    }
    LaunchedEffect(responseEvent?.sequence, operation?.acceptResponseAfterSequence) {
        val current = operation ?: return@LaunchedEffect
        val event = responseEvent ?: return@LaunchedEffect
        if (event.sequence <= current.acceptResponseAfterSequence) return@LaunchedEffect
        if (!event.message.isStatusOK) {
            updateOperation(null)
            notice =
                "The pump rejected the ${current.action.name.lowercase()} request. Pump status is being refreshed; the command was not repeated."
            requestFreshState()
            return@LaunchedEffect
        }
        updateOperation(
            current.afterAcceptedResponse(
                currentPumpMessageSequence = latestPumpMessageSequence.value,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        )
        requestFreshState()
    }

    LaunchedEffect(
        basalStatusEvent.value?.sequence,
        operation?.phase,
        operation?.acceptStatusAfterSequence,
    ) {
        val current = operation ?: return@LaunchedEffect
        val event = basalStatusEvent.value ?: return@LaunchedEffect
        when (current.resolveFreshStatus(event.sequence, event.value)) {
            DeliveryStatusResolution.IGNORE -> Unit
            DeliveryStatusResolution.CONFIRMED -> updateOperation(null)
            DeliveryStatusResolution.SOURCE_STATE -> {
                // A successful control response can precede Home Screen state propagation. Keep
                // the single flight open and issue only a bounded, read-only recheck. The
                // state-changing command is never replayed.
                delay(DELIVERY_TRANSITION_STATUS_RECHECK_MS)
                if (operation == current && pumpConnected.value == true) {
                    requestFreshState()
                }
            }
        }
    }

    LaunchedEffect(operation?.phase, operation?.startedAtEpochMillis) {
        val current = operation ?: return@LaunchedEffect
        when (current.phase) {
            DeliveryTransitionPhase.AWAITING_RESPONSE -> {
                delay(DELIVERY_TRANSITION_RESPONSE_TIMEOUT_MS)
                if (operation == current) reconcile()
            }
            DeliveryTransitionPhase.VERIFYING_STATUS,
            DeliveryTransitionPhase.RECONCILING -> {
                delay(DELIVERY_TRANSITION_STATUS_TIMEOUT_MS)
                if (operation == current) {
                    val latestStatus = basalStatusEvent.value
                    when (
                        latestStatus?.let {
                            current.resolveFreshStatus(it.sequence, it.value)
                        } ?: DeliveryStatusResolution.IGNORE
                    ) {
                        DeliveryStatusResolution.CONFIRMED -> updateOperation(null)
                        DeliveryStatusResolution.SOURCE_STATE -> {
                            updateOperation(null)
                            notice = current.sourceStateNotice()
                        }
                        DeliveryStatusResolution.IGNORE -> {
                            updateOperation(current.statusUnavailable(System.currentTimeMillis()))
                        }
                    }
                }
            }
            DeliveryTransitionPhase.STATUS_UNAVAILABLE -> Unit
        }
    }

    return DeliveryTransitionController(
        operation = operation,
        pumpConnected = pumpConnected.value == true,
        notice = notice,
        requestSuspend = { requestTransition(DeliveryTransitionAction.SUSPEND) },
        requestResume = { requestTransition(DeliveryTransitionAction.RESUME) },
        reconcile = ::reconcile,
        dismissNotice = { notice = null },
    )
}
