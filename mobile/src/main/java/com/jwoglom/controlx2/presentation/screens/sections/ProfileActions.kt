@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterialApi::class,
)

package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.dataStore
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.controlx2.presentation.authentication.ActionAuthenticationRequest
import com.jwoglom.controlx2.presentation.authentication.BolusAuthenticationResult
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.AddProfileDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.AddSegmentDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.dialogs.EditSegmentDialog
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.shared.util.SendType
import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import com.jwoglom.pumpx2.pump.messages.models.StatusMessage
import com.jwoglom.pumpx2.pump.messages.request.control.CreateIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.DeleteIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.RenameIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetActiveIDPRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSegmentRequest
import com.jwoglom.pumpx2.pump.messages.request.control.SetIDPSettingsRequest
import com.jwoglom.pumpx2.pump.messages.response.control.CreateIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.DeleteIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.RenameIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetActiveIDPResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetIDPSettingsResponse
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val PROFILE_ACK_TIMEOUT_MS = 8_000L
private const val PROFILE_READBACK_TIMEOUT_MS = 15_000L
private const val PROFILE_READ_STALL_TIMEOUT_MS = 15_000L

private data class PendingProfileMutation(
    val description: String,
    val messages: List<Message>,
    val messageIndex: Int,
    val baselineSequence: Long,
    val acknowledged: Boolean = false,
    val verify: (List<InsulinProfileSnapshot>) -> Boolean,
    val onVerified: ((List<InsulinProfileSnapshot>) -> Unit)? = null,
)

private enum class ProfileNameAction { RENAME, DUPLICATE }

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProfileActions(
    innerPadding: PaddingValues = PaddingValues(),
    navController: NavHostController? = null,
    sendMessage: (String, ByteArray) -> Unit,
    sendPumpCommands: (SendType, List<Message>) -> Unit,
    requestActionAuthentication: ActionAuthenticationRequest = { _, onResult ->
        onResult(BolusAuthenticationResult.Unavailable("Preview authentication unavailable."))
    },
    historyLogViewModel: HistoryLogViewModel? = null,
    _changeCartridgeMenuState: Boolean = false,
    _fillTubingMenuState: Boolean = false,
    _fillCannulaMenuState: Boolean = false,
    developerPreviewEnabledOverride: Boolean? = null,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val developerPreviewEnabled = developerPreviewEnabledOverride ?: prefs.developerPreviewEnabled()
    val ds = LocalDataStore.current
    val observedManager by ds.idpManager.observeAsState()
    val observedReadProgress by ds.profileReadProgress.observeAsState(ProfileReadProgress())
    val profileReadCompletedAt by ds.profileReadCompletedAtEpochMillis.observeAsState()
    val mutationEvent by ds.profileMutationResponseEvent.observeAsState()
    val deviceName by ds.setupDeviceName.observeAsState()
    val controlIqEnabled by ds.controlIQEnabled.observeAsState(false)
    val pumpConnected by ds.pumpConnected.observeAsState(false)
    val pumpSid by ds.pumpSid.observeAsState()
    val effectivePumpSid = pumpSid?.takeIf { it >= 0 }
        ?: prefs.currentPumpSid().takeIf { it >= 0 }

    var verifiedProfileCache by remember(effectivePumpSid) {
        mutableStateOf(
            effectivePumpSid?.let { sid ->
                decodeVerifiedPumpProfileCache(prefs.verifiedPumpProfileCacheJson(sid), sid)
            },
        )
    }
    var savedProfileDrafts by remember(effectivePumpSid) {
        mutableStateOf(
            effectivePumpSid?.let { sid ->
                decodeSavedProfileDrafts(prefs.savedProfileDraftsJson(sid), sid)
            }.orEmpty(),
        )
    }

    var previewProfiles by remember {
        mutableStateOf(
            profileSnapshots(ds.idpManager.value).ifEmpty { listOf(SYNTHETIC_DEVELOPER_PROFILE) },
        )
    }
    var selectedProfileId by remember { mutableStateOf<Int?>(null) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var addSegmentProfileId by remember { mutableStateOf<Int?>(null) }
    var editSegmentTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var bolusSettingsProfileId by remember { mutableStateOf<Int?>(null) }
    var profileOptionsId by remember { mutableStateOf<Int?>(null) }
    var nameDialog by remember { mutableStateOf<Pair<ProfileNameAction, Int>?>(null) }
    var confirmationTitle by remember { mutableStateOf<String?>(null) }
    var confirmationText by remember { mutableStateOf("") }
    var confirmationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var informationDialogText by remember { mutableStateOf<String?>(null) }
    var pendingProfileDraft by remember { mutableStateOf<PendingProfileDraft?>(null) }
    var selectedSavedDraftId by remember { mutableStateOf<String?>(null) }
    var showEditConnectionLostDialog by remember { mutableStateOf(false) }
    var editorConnectionLost by remember { mutableStateOf(false) }
    var editingProfileBaseline by remember { mutableStateOf<InsulinProfileSnapshot?>(null) }
    var wasPumpConnected by remember { mutableStateOf(pumpConnected) }
    var pendingMutation by remember { mutableStateOf<PendingProfileMutation?>(null) }
    var profileAuthenticationPending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var profileReadError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(!developerPreviewEnabled) }
    var profileReadSendType by remember { mutableStateOf(SendType.STANDARD) }

    val liveReadComplete = observedManager?.isComplete == true
    val liveProfiles = profileSnapshots(observedManager)
    val profiles = when {
        developerPreviewEnabled -> previewProfiles
        liveReadComplete -> liveProfiles
        else -> verifiedProfileCache?.profiles.orEmpty()
    }
    val readStatus = profileReadStatus(observedManager, observedReadProgress)
    val showingCachedProfiles = !developerPreviewEnabled && !liveReadComplete && verifiedProfileCache != null
    val profilesAvailable = developerPreviewEnabled || liveReadComplete || verifiedProfileCache != null
    val selectedProfile = profiles.firstOrNull { it.idpId == selectedProfileId }
    val managerForDialogs = remember(profiles, editingProfileBaseline) {
        val baseline = editingProfileBaseline
        val dialogProfiles = if (baseline == null) {
            profiles
        } else {
            val replaced = profiles.map { if (it.idpId == baseline.idpId) baseline else it }
            if (replaced.any { it.idpId == baseline.idpId }) replaced else replaced + baseline
        }
        if (dialogProfiles.isNotEmpty()) {
            profileManagerFromSnapshots(dialogProfiles, currentMinuteOfDay())
        } else {
            null
        }
    }
    val readOnlyModel = determinePumpModel(deviceName ?: "") == KnownDeviceModel.TSLIM_X2
    val pumpProfileWriteReady = developerPreviewEnabled || (pumpConnected && liveReadComplete)
    val writeEnabled = !readOnlyModel && pendingMutation == null &&
        !profileAuthenticationPending && pumpProfileWriteReady
    val canAddNewProfile = writeEnabled && canAddProfile(profiles)

    fun normalizePreviewProfiles(next: List<InsulinProfileSnapshot>): List<InsulinProfileSnapshot> =
        next.sortedByDescending { it.active }.mapIndexed { index, profile ->
            profile.copy(slotIndex = index, active = index == 0)
        }

    fun updatePreviewProfiles(next: List<InsulinProfileSnapshot>, success: String) {
        previewProfiles = normalizePreviewProfiles(next)
        ds.idpManager.value = profileManagerFromSnapshots(previewProfiles, currentMinuteOfDay())
        statusMessage = success
        errorMessage = null
    }

    fun persistSavedProfileDrafts(next: List<SavedProfileDraft>) {
        val sid = effectivePumpSid
        if (sid == null) {
            errorMessage = "The pump identity is unavailable, so the draft copy could not be saved."
            return
        }
        savedProfileDrafts = next
        prefs.setSavedProfileDraftsJson(
            next.takeIf { it.isNotEmpty() }?.let(::encodeSavedProfileDrafts),
            sid,
        )
    }

    fun closeProfileEditors() {
        addSegmentProfileId = null
        editSegmentTarget = null
        bolusSettingsProfileId = null
        editingProfileBaseline = null
        editorConnectionLost = false
    }

    fun prepareDraftCopy(sourceProfileId: Int, profile: InsulinProfileSnapshot) {
        showEditConnectionLostDialog = false
        pendingProfileDraft = PendingProfileDraft(
            sourceProfileId = sourceProfileId,
            profile = profile.copy(active = false),
        )
    }

    fun requestProfileData(type: SendType, clearFirst: Boolean = false) {
        if (developerPreviewEnabled) {
            refreshing = false
            return
        }
        profileReadError = null
        if (clearFirst) {
            ds.idpManager.value = IDPManager()
            ds.profileReadProgress.value = ProfileReadProgress()
            ds.profileReadCompletedAtEpochMillis.value = null
        }
        if (!prefs.serviceEnabled()) {
            refreshing = false
            profileReadError = "The pump service is not running. Start it, then retry."
            return
        }
        if (!pumpConnected) {
            refreshing = false
            profileReadError = "The pump is not connected. Reconnect the pump, then retry."
            return
        }
        profileReadSendType = type
        refreshing = true
    }

    fun beginPumpMutation(
        description: String,
        messages: List<Message>,
        onVerified: ((List<InsulinProfileSnapshot>) -> Unit)? = null,
        verify: (List<InsulinProfileSnapshot>) -> Boolean,
    ) {
        if (developerPreviewEnabled || readOnlyModel || messages.isEmpty() ||
            pendingMutation != null || profileAuthenticationPending
        ) return
        if (!pumpConnected || observedManager?.isComplete != true) {
            errorMessage = "Wait for the pump profile refresh to finish before making this change."
            return
        }
        val authorizationPumpSid = effectivePumpSid
        val authorizationProfiles = profileSnapshots(observedManager)
        profileAuthenticationPending = true
        statusMessage = "Waiting for device authentication…"
        errorMessage = null
        requestActionAuthentication("Profile Change") { result ->
            profileAuthenticationPending = false
            when (result) {
                BolusAuthenticationResult.Succeeded -> {
                    val currentPumpSid = ds.pumpSid.value?.takeIf { it >= 0 }
                        ?: prefs.currentPumpSid().takeIf { it >= 0 }
                    val currentManager = ds.idpManager.value
                    when {
                        ds.pumpConnected.value != true || currentManager?.isComplete != true -> {
                            statusMessage = null
                            errorMessage =
                                "The pump connection or profile refresh changed during authentication. Review and try again."
                        }
                        currentPumpSid != authorizationPumpSid ||
                            profileSnapshots(currentManager) != authorizationProfiles -> {
                            statusMessage = null
                            errorMessage =
                                "Pump profile data changed during authentication. Review the current profile and try again."
                        }
                        pendingMutation != null -> {
                            statusMessage = null
                            errorMessage = "Another profile change is already in progress."
                        }
                        else -> {
                            val baseline = ds.latestPumpMessageSequence.value ?: 0L
                            pendingMutation = PendingProfileMutation(
                                description,
                                messages,
                                0,
                                baseline,
                                verify = verify,
                                onVerified = onVerified,
                            )
                            statusMessage = "$description…"
                            errorMessage = null
                            sendPumpCommands(SendType.STANDARD, listOf(messages.first()))
                        }
                    }
                }
                is BolusAuthenticationResult.Cancelled -> {
                    statusMessage = null
                    errorMessage = result.message
                }
                is BolusAuthenticationResult.Unavailable -> {
                    statusMessage = null
                    errorMessage = result.message
                }
                is BolusAuthenticationResult.Error -> {
                    statusMessage = null
                    errorMessage = result.message
                }
            }
        }
    }

    fun removeSavedDraft(draftId: String) {
        persistSavedProfileDrafts(savedProfileDrafts.filterNot { it.draftId == draftId })
    }

    fun createSavedDraftOnPump(draft: SavedProfileDraft) {
        if (!pumpProfileWriteReady) {
            errorMessage = "Reconnect and finish reading pump profiles before creating this copy."
            return
        }
        if (!canAddProfile(profiles)) {
            errorMessage = "The pump's six-profile limit has been reached. The local draft was kept."
            return
        }
        if (profiles.any { it.name.equals(draft.profile.name, ignoreCase = true) }) {
            errorMessage =
                "A pump profile named '${draft.profile.name}' already exists. The local draft was kept."
            return
        }
        val desired = draft.profile.copy(active = false, segments = reindexSegments(draft.profile.segments))
        val first = desired.segments.firstOrNull()
        if (first == null || first.startMinutes != 0 ||
            first.carbRatioMilliunits !in 1..Int.MAX_VALUE.toLong()
        ) {
            errorMessage = "The saved copy is incomplete and cannot be created on the pump."
            return
        }

        val createRequest = buildSavedDraftCreateRequest(desired) ?: run {
            errorMessage = "The saved copy is incomplete and cannot be created on the pump."
            return
        }
        beginPumpMutation(
            description = "Creating saved copy '${desired.name}'",
            messages = listOf(createRequest),
            verify = { readback ->
                readback.any { candidate ->
                    candidate.name == desired.name &&
                        candidate.insulinDurationMinutes == desired.insulinDurationMinutes &&
                        candidate.carbEntryEnabled == desired.carbEntryEnabled &&
                        candidate.segments.firstOrNull()?.matches(first, true) == true
                }
            },
            onVerified = { readback ->
                val created = readback.firstOrNull { it.name == desired.name }
                if (created == null) {
                    errorMessage = "The copy was created, but its pump identity could not be resolved. The draft was kept."
                    return@beginPumpMutation
                }
                val remainingSegmentRequests = buildSavedDraftRemainingSegmentRequests(created, desired)
                if (remainingSegmentRequests.isEmpty()) {
                    removeSavedDraft(draft.draftId)
                    statusMessage = "Saved copy '${desired.name}' was created and verified on the pump."
                } else {
                    beginPumpMutation(
                        description = "Restoring time segments for '${desired.name}'",
                        messages = remainingSegmentRequests,
                        verify = { finalReadback ->
                            finalReadback.firstOrNull { it.name == desired.name }
                                ?.let { profileContentMatches(it, desired) } == true
                        },
                        onVerified = {
                            removeSavedDraft(draft.draftId)
                            statusMessage =
                                "Saved copy '${desired.name}' was created and verified on the pump."
                        },
                    )
                }
            },
        )
    }

    fun confirm(title: String, text: String, action: () -> Unit) {
        confirmationTitle = title
        confirmationText = text
        confirmationAction = action
    }

    fun rawProfile(idpId: Int): IDPManager.Profile? = managerForDialogs
        ?.profiles
        ?.firstOrNull { it.idpId == idpId }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = { requestProfileData(SendType.BUST_CACHE, clearFirst = true) },
    )

    LaunchedEffect(developerPreviewEnabled, pumpSid, pumpConnected) {
        if (developerPreviewEnabled) {
            ds.idpManager.value = profileManagerFromSnapshots(previewProfiles, currentMinuteOfDay())
            refreshing = false
        } else if (!pumpConnected || observedManager?.isComplete != true) {
            // Always begin with a new PumpX2 manager so this page reflects the connected pump,
            // rather than accepting a profile snapshot left over from an earlier connection.
            requestProfileData(SendType.BUST_CACHE, clearFirst = true)
        } else {
            refreshing = false
            profileReadError = null
        }
    }

    LaunchedEffect(
        pumpConnected,
        addSegmentProfileId,
        editSegmentTarget,
        bolusSettingsProfileId,
    ) {
        val editorOpen = addSegmentProfileId != null || editSegmentTarget != null ||
            bolusSettingsProfileId != null
        if (!developerPreviewEnabled && wasPumpConnected && !pumpConnected && editorOpen) {
            editorConnectionLost = true
            showEditConnectionLostDialog = true
        }
        wasPumpConnected = pumpConnected
    }

    LaunchedEffect(
        observedManager,
        profileReadCompletedAt,
        effectivePumpSid,
        developerPreviewEnabled,
    ) {
        val sid = effectivePumpSid ?: return@LaunchedEffect
        val verifiedAt = profileReadCompletedAt ?: return@LaunchedEffect
        if (developerPreviewEnabled || observedManager?.isComplete != true) return@LaunchedEffect

        val pumpProfiles = profileSnapshots(observedManager)
        val previous = verifiedProfileCache
        val next = VerifiedPumpProfileCache(sid, verifiedAt, pumpProfiles)
        prefs.setVerifiedPumpProfileCacheJson(encodeVerifiedPumpProfileCache(next), sid)
        verifiedProfileCache = next
        if (previous != null && !samePumpProfileData(previous.profiles, pumpProfiles) &&
            pendingMutation == null
        ) {
            statusMessage = "Pump profiles changed. The local pump-verified copy was updated."
        }
    }

    LaunchedEffect(observedManager, refreshing, pumpConnected) {
        if (developerPreviewEnabled || !refreshing) return@LaunchedEffect
        val manager = observedManager ?: return@LaunchedEffect
        if (manager.isComplete) {
            refreshing = false
            profileReadError = null
        } else if (prefs.serviceEnabled() && pumpConnected) {
            delay(120)
            sendPumpCommands(profileReadSendType, manager.nextMessages())
        }
    }

    // Treat this as an inactivity timeout. Every pump response replaces IDPManager and restarts
    // the clock, so a slow multi-profile read can continue as long as data is still arriving.
    LaunchedEffect(observedManager, refreshing, developerPreviewEnabled, pumpConnected) {
        if (developerPreviewEnabled || !refreshing || observedManager?.isComplete == true) {
            return@LaunchedEffect
        }
        delay(PROFILE_READ_STALL_TIMEOUT_MS)
        if (refreshing && ds.idpManager.value?.isComplete != true) {
            refreshing = false
            profileReadError =
                "No new profile data was received for 15 seconds. The pump connection may have stalled."
        }
    }

    LaunchedEffect(mutationEvent?.sequence) {
        val pending = pendingMutation ?: return@LaunchedEffect
        val event = mutationEvent ?: return@LaunchedEffect
        if (event.sequence <= pending.baselineSequence || pending.acknowledged) return@LaunchedEffect
        val request = pending.messages[pending.messageIndex]
        if (!event.message.isResponseFor(request)) return@LaunchedEffect

        if (!event.message.isStatusOK) {
            errorMessage = "${pending.description} was rejected by the pump (status ${event.message.status})."
            statusMessage = null
            pendingMutation = null
            return@LaunchedEffect
        }

        val nextIndex = pending.messageIndex + 1
        if (nextIndex < pending.messages.size) {
            val next = pending.copy(messageIndex = nextIndex, baselineSequence = event.sequence)
            pendingMutation = next
            sendPumpCommands(SendType.STANDARD, listOf(next.messages[nextIndex]))
        } else {
            pendingMutation = pending.copy(baselineSequence = event.sequence, acknowledged = true)
            statusMessage = "Pump accepted the change. Verifying pump readback…"
            requestProfileData(SendType.BUST_CACHE, clearFirst = true)
        }
    }

    LaunchedEffect(observedManager, pendingMutation?.acknowledged) {
        val pending = pendingMutation ?: return@LaunchedEffect
        if (!pending.acknowledged || observedManager?.isComplete != true) return@LaunchedEffect
        val readback = profileSnapshots(observedManager)
        if (pending.verify(readback)) {
            statusMessage = "${pending.description} verified on the pump."
            errorMessage = null
            pendingMutation = null
            refreshing = false
            pending.onVerified?.invoke(readback)
        } else {
            statusMessage = null
            errorMessage = "The pump acknowledged the change, but its profile readback did not match. No success was assumed."
            pendingMutation = null
            refreshing = false
        }
    }

    LaunchedEffect(pendingMutation?.baselineSequence, pendingMutation?.acknowledged) {
        val pending = pendingMutation ?: return@LaunchedEffect
        delay(if (pending.acknowledged) PROFILE_READBACK_TIMEOUT_MS else PROFILE_ACK_TIMEOUT_MS)
        val stillPending = pendingMutation
        if (stillPending?.baselineSequence == pending.baselineSequence &&
            stillPending.acknowledged == pending.acknowledged
        ) {
            errorMessage = if (pending.acknowledged) {
                "The pump accepted the change, but profile readback timed out. Reopen Profiles to verify before making another change."
            } else {
                "${pending.description} timed out without a matching pump response."
            }
            statusMessage = null
            pendingMutation = null
            refreshing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            item {
                HeaderLine(if (selectedProfile == null) "Personal Profiles" else selectedProfile.name)
                HorizontalDivider()
            }

            if (developerPreviewEnabled) {
                item {
                    ListItem(
                        headlineContent = { Text("Developer Preview") },
                        supportingContent = { Text("Changes on this page are simulated and are never sent to a pump.") },
                    )
                }
            }
            if (readOnlyModel) {
                item {
                    ListItem(
                        headlineContent = { Text("Read only") },
                        supportingContent = { Text("Profile changes are not supported for t:slim X2.") },
                    )
                }
            }
            if (!developerPreviewEnabled) {
                item {
                    ProfileReadStatusIndicator(
                        status = readStatus,
                        errorMessage = profileReadError,
                        showingCachedProfiles = showingCachedProfiles,
                        cachedVerifiedAtEpochMillis = verifiedProfileCache?.verifiedAtEpochMillis,
                        onRetry = {
                            requestProfileData(SendType.BUST_CACHE, clearFirst = true)
                        },
                    )
                }
            }
            statusMessage?.let { message ->
                item {
                    ListItem(
                        headlineContent = { Text(message) },
                        leadingContent = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    )
                }
            }
            errorMessage?.let { message ->
                item {
                    ListItem(headlineContent = { Text(message, color = MaterialTheme.colorScheme.error) })
                }
            }

            if (selectedProfile == null) {
                if (profilesAvailable && profiles.isEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("No profiles present") },
                            supportingContent = { Text("Create a profile to continue.") },
                        )
                    }
                } else {
                    profiles.forEach { profile ->
                        item(key = profile.idpId) {
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                                        if (profile.active) {
                                            Text(
                                                "  ACTIVE",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                },
                                supportingContent = {
                                    Text("${profile.segments.size} time segments • ${formatTotalBasal(profile)} U/day")
                                },
                                trailingContent = { Icon(Icons.Filled.MoreVert, contentDescription = "Open profile") },
                                modifier = Modifier.clickable { selectedProfileId = profile.idpId },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                if (savedProfileDrafts.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text("Saved Draft Copies", fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text("Stored locally and never substituted for a pump profile")
                            },
                        )
                        HorizontalDivider()
                    }
                    savedProfileDrafts.sortedByDescending { it.savedAtEpochMillis }.forEach { draft ->
                        item(key = "profile-draft-${draft.draftId}") {
                            ListItem(
                                headlineContent = { Text(draft.profile.name) },
                                supportingContent = {
                                    Text(
                                        "${draft.profile.segments.size} time segments • " +
                                            "Saved ${formatProfileCacheTime(draft.savedAtEpochMillis)} • Not on pump",
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                },
                                trailingContent = {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Open saved draft")
                                },
                                modifier = Modifier.clickable {
                                    selectedSavedDraftId = draft.draftId
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                item {
                    ListItem(
                        headlineContent = { Text("Add New Profile") },
                        supportingContent = {
                            Text(
                                when {
                                    !pumpProfileWriteReady -> "Refreshing pump profiles before changes can be made"
                                    canAddProfile(profiles) -> "Create a new insulin delivery profile"
                                    else -> "The pump's six-profile limit has been reached"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = canAddNewProfile) { showAddProfileDialog = true },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Back") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable { navigateBack() },
                    )
                }
            } else {
                val activeSegmentIndex = if (selectedProfile.active) {
                    activeProfileSegmentIndex(selectedProfile.segments, currentMinuteOfDay())
                } else -1
                item {
                    ListItem(
                        headlineContent = { Text(if (selectedProfile.active) "Active Profile" else "Inactive Profile") },
                        supportingContent = { Text("Total Daily Basal  ${formatTotalBasal(selectedProfile)} U") },
                    )
                    HorizontalDivider()
                }

                selectedProfile.segments.sortedBy { it.startMinutes }.forEach { segment ->
                    item(key = "${selectedProfile.idpId}-${segment.index}") {
                        ListItem(
                            headlineContent = {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatProfileTime(segment.startMinutes), fontWeight = FontWeight.Bold)
                                    if (segment.index == activeSegmentIndex) {
                                        Text(
                                            "CURRENT",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    "Basal  ${formatProfileBasal(segment.basalMilliunitsPerHour)} U/hr\n" +
                                        "Correction  1:${segment.correctionFactorMgdlPerUnit} mg/dL\n" +
                                        "Carb Ratio  1:${formatProfileRatio(segment.carbRatioMilliunits)} g\n" +
                                        "Target BG  ${segment.targetBgMgdl} mg/dL",
                                )
                            },
                            trailingContent = { Icon(Icons.Filled.Edit, contentDescription = "Edit segment") },
                            modifier = Modifier.clickable(enabled = writeEnabled) {
                                editingProfileBaseline = selectedProfile
                                editorConnectionLost = false
                                editSegmentTarget = selectedProfile.idpId to segment.index
                            },
                        )
                        HorizontalDivider()
                    }
                }

                item {
                    val canAddTimeSegment = writeEnabled && canAddProfileSegment(selectedProfile)
                    ListItem(
                        headlineContent = { Text("Add Segment") },
                        supportingContent = {
                            Text(
                                if (canAddProfileSegment(selectedProfile)) {
                                    "Add another time-based setting"
                                } else {
                                    "The pump's 16-segment limit has been reached"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = canAddTimeSegment) {
                            editingProfileBaseline = selectedProfile
                            editorConnectionLost = false
                            addSegmentProfileId = selectedProfile.idpId
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Bolus Settings") },
                        supportingContent = {
                            val policy = profileBolusSettingsPolicy(controlIqEnabled, selectedProfile)
                            val hours = policy.displayedDurationMinutes / 60.0
                            Text(
                                "Insulin Duration  ${formatDurationHours(hours)} hr\n" +
                                    "Carbohydrates  ${if (selectedProfile.carbEntryEnabled) "On" else "Off"}",
                            )
                        },
                        leadingContent = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = writeEnabled) {
                            editingProfileBaseline = selectedProfile
                            editorConnectionLost = false
                            bolusSettingsProfileId = selectedProfile.idpId
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Profile Options") },
                        supportingContent = { Text("Activate, rename, duplicate, or delete") },
                        leadingContent = { Icon(Icons.Filled.MoreVert, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = writeEnabled) {
                            profileOptionsId = selectedProfile.idpId
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Back to Personal Profiles") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable { selectedProfileId = null },
                    )
                }
            }
        }

        PullRefreshIndicator(refreshing = refreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showAddProfileDialog) {
        AddProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onConfirm = { name, ratio, basal, target, correction, duration, carbEntry ->
                if (!canAddProfile(profiles)) {
                    errorMessage = "The pump supports at most six profiles."
                    showAddProfileDialog = false
                    return@AddProfileDialog
                }
                if (profiles.any { it.name.equals(name, ignoreCase = true) }) {
                    errorMessage = "A profile named '$name' already exists."
                    return@AddProfileDialog
                }
                showAddProfileDialog = false
                val expectedFirstSegment = ProfileSegmentSnapshot(0, 0, basal, ratio.toLong(), target, correction)
                if (developerPreviewEnabled) {
                    val newId = nextAvailableProfileId(profiles)
                    updatePreviewProfiles(
                        profiles + InsulinProfileSnapshot(
                            newId,
                            profiles.size,
                            name,
                            profiles.isEmpty(),
                            duration,
                            15_000,
                            carbEntry,
                            listOf(expectedFirstSegment),
                        ),
                        "Profile '$name' created in Developer Preview.",
                    )
                } else {
                    val request = CreateIDPRequest(name, ratio, basal, target, correction, duration, if (carbEntry) 1 else 0)
                    beginPumpMutation("Creating profile '$name'", listOf(request)) { readback ->
                        readback.any { profile ->
                            profile.name == name && profile.insulinDurationMinutes == duration &&
                                profile.carbEntryEnabled == carbEntry &&
                                profile.segments.firstOrNull()?.matches(expectedFirstSegment, true) == true
                        }
                    }
                }
            },
        )
    }

    addSegmentProfileId?.let { profileId ->
        val snapshot = editingProfileBaseline?.takeIf { it.idpId == profileId }
            ?: profiles.firstOrNull { it.idpId == profileId }
        val raw = rawProfile(profileId)
        if (snapshot != null && raw != null) {
            val saveAsDraftCopy = !developerPreviewEnabled &&
                (editorConnectionLost || !pumpProfileWriteReady)
            AddSegmentDialog(
                profile = raw,
                existingStartMinutes = snapshot.segments.map { it.startMinutes }.toSet(),
                segmentsForAutofill = snapshot.segments,
                saveAsDraftCopy = saveAsDraftCopy,
                onDismiss = { closeProfileEditors() },
                onConfirm = { time, basal, ratio, target, correction ->
                    if (!canAddProfileSegment(snapshot)) {
                        errorMessage = "The pump supports at most 16 time segments per profile."
                        return@AddSegmentDialog
                    }
                    val expected = ProfileSegmentSnapshot(
                        snapshot.segments.size,
                        time.encode(),
                        basal,
                        ratio,
                        target,
                        correction,
                    )
                    val updatedProfile = snapshot.copy(
                        segments = reindexSegments(snapshot.segments + expected),
                    )
                    if (saveAsDraftCopy) {
                        prepareDraftCopy(profileId, updatedProfile)
                        return@AddSegmentDialog
                    }
                    closeProfileEditors()
                    if (developerPreviewEnabled) {
                        updatePreviewProfiles(
                            profiles.map { profile ->
                                if (profile.idpId != profileId) profile else profile.copy(
                                    segments = updatedProfile.segments,
                                )
                            },
                            "Segment added in Developer Preview.",
                        )
                    } else {
                        val request = SetIDPSegmentRequest(
                            snapshot.idpId,
                            snapshot.slotIndex,
                            0,
                            SetIDPSegmentRequest.IDPSegmentOperation.CREATE_SEGMENT,
                            expected.startMinutes,
                            expected.basalMilliunitsPerHour,
                            expected.carbRatioMilliunits,
                            expected.targetBgMgdl,
                            expected.correctionFactorMgdlPerUnit,
                            PROFILE_SEGMENT_STATUS_ALL_FIELDS,
                        )
                        beginPumpMutation("Adding profile segment", listOf(request)) { readback ->
                            readback.profile(profileId)?.segments?.any { it.matches(expected, true) } == true
                        }
                    }
                },
            )
        }
    }

    editSegmentTarget?.let { (profileId, segmentIndex) ->
        val snapshot = editingProfileBaseline?.takeIf { it.idpId == profileId }
            ?: profiles.firstOrNull { it.idpId == profileId }
        val segment = snapshot?.segments?.firstOrNull { it.index == segmentIndex }
        val raw = rawProfile(profileId)
        val rawSegment = raw?.segments?.firstOrNull { it.segmentIndex == segmentIndex }
        if (snapshot != null && segment != null && raw != null && rawSegment != null) {
            val saveAsDraftCopy = !developerPreviewEnabled &&
                (editorConnectionLost || !pumpProfileWriteReady)
            EditSegmentDialog(
                profile = raw,
                segment = rawSegment,
                segmentIndex = segmentIndex,
                existingStartMinutes = snapshot.segments.map { it.startMinutes }.toSet(),
                saveAsDraftCopy = saveAsDraftCopy,
                onDismiss = { closeProfileEditors() },
                onDelete = if (segmentIndex == 0) null else {
                    {
                        closeProfileEditors()
                        confirm(
                            "Delete Segment?",
                            "Delete the ${formatProfileTime(segment.startMinutes)} segment from '${snapshot.name}'?",
                        ) {
                            if (developerPreviewEnabled) {
                                updatePreviewProfiles(
                                    profiles.map { profile ->
                                        if (profile.idpId != profileId) profile else profile.copy(
                                            segments = reindexSegments(profile.segments.filterNot { it.index == segmentIndex }),
                                        )
                                    },
                                    "Segment deleted in Developer Preview.",
                                )
                            } else {
                                val request = SetIDPSegmentRequest(
                                    snapshot.idpId,
                                    snapshot.slotIndex,
                                    segment.index,
                                    SetIDPSegmentRequest.IDPSegmentOperation.DELETE_SEGMENT_ID,
                                    segment.startMinutes,
                                    segment.basalMilliunitsPerHour,
                                    segment.carbRatioMilliunits,
                                    segment.targetBgMgdl,
                                    segment.correctionFactorMgdlPerUnit,
                                    PROFILE_SEGMENT_STATUS_ALL_FIELDS,
                                )
                                beginPumpMutation("Deleting profile segment", listOf(request)) { readback ->
                                    readback.profile(profileId)?.segments?.none { it.matches(segment, true) } == true
                                }
                            }
                        }
                    }
                },
                onConfirm = { time, basal, ratio, target, correction ->
                    val expected = segment.copy(
                        startMinutes = time.encode(),
                        basalMilliunitsPerHour = basal,
                        carbRatioMilliunits = ratio,
                        targetBgMgdl = target,
                        correctionFactorMgdlPerUnit = correction,
                    )
                    val updatedProfile = snapshot.copy(
                        segments = reindexSegments(snapshot.segments.map {
                            if (it.index == segmentIndex) expected else it
                        }),
                    )
                    if (saveAsDraftCopy) {
                        prepareDraftCopy(profileId, updatedProfile)
                        return@EditSegmentDialog
                    }
                    closeProfileEditors()
                    if (developerPreviewEnabled) {
                        updatePreviewProfiles(
                            profiles.map { profile ->
                                if (profile.idpId != profileId) profile else profile.copy(
                                    segments = updatedProfile.segments,
                                )
                            },
                            "Segment updated in Developer Preview.",
                        )
                    } else {
                        val request = SetIDPSegmentRequest(
                            snapshot.idpId,
                            snapshot.slotIndex,
                            segment.index,
                            SetIDPSegmentRequest.IDPSegmentOperation.MODIFY_SEGMENT_ID,
                            expected.startMinutes,
                            expected.basalMilliunitsPerHour,
                            expected.carbRatioMilliunits,
                            expected.targetBgMgdl,
                            expected.correctionFactorMgdlPerUnit,
                            PROFILE_SEGMENT_STATUS_ALL_FIELDS,
                        )
                        beginPumpMutation("Updating profile segment", listOf(request)) { readback ->
                            readback.profile(profileId)?.segments?.any { it.matches(expected, true) } == true
                        }
                    }
                },
            )
        }
    }

    bolusSettingsProfileId?.let { profileId ->
        (editingProfileBaseline?.takeIf { it.idpId == profileId }
            ?: profiles.firstOrNull { it.idpId == profileId })?.let { profile ->
            val saveAsDraftCopy = !developerPreviewEnabled &&
                (editorConnectionLost || !pumpProfileWriteReady)
            ProfileBolusSettingsDialog(
                profile = profile,
                controlIqEnabled = controlIqEnabled,
                saveAsDraftCopy = saveAsDraftCopy,
                onDismiss = { closeProfileEditors() },
                onConfirm = { duration, carbEntry ->
                    val policy = profileBolusSettingsPolicy(controlIqEnabled, profile)
                    val updatedProfile = profile.copy(
                        insulinDurationMinutes = if (policy.durationEditable) {
                            duration
                        } else {
                            CONTROL_IQ_FIXED_INSULIN_DURATION_MINUTES
                        },
                        carbEntryEnabled = carbEntry,
                    )
                    if (saveAsDraftCopy) {
                        prepareDraftCopy(profileId, updatedProfile)
                        return@ProfileBolusSettingsDialog
                    }
                    closeProfileEditors()
                    if (developerPreviewEnabled) {
                        updatePreviewProfiles(
                            profiles.map {
                                if (it.idpId != profileId) it else updatedProfile
                            },
                            "Bolus settings saved in Developer Preview.",
                        )
                    } else {
                        val messages = buildList<Message> {
                            if (policy.durationEditable && duration != profile.insulinDurationMinutes) {
                                add(
                                    SetIDPSettingsRequest(
                                        profile.idpId,
                                        profile.slotIndex,
                                        duration,
                                        if (carbEntry) 1 else 0,
                                        SetIDPSettingsRequest.ChangeType.CHANGE_INSULIN_DURATION,
                                    ),
                                )
                            }
                            if (carbEntry != profile.carbEntryEnabled) {
                                add(
                                    SetIDPSettingsRequest(
                                        profile.idpId,
                                        profile.slotIndex,
                                        profile.insulinDurationMinutes,
                                        if (carbEntry) 1 else 0,
                                        SetIDPSettingsRequest.ChangeType.CHANGE_CARB_ENTRY,
                                    ),
                                )
                            }
                        }
                        beginPumpMutation("Saving bolus settings", messages) { readback ->
                            readback.profile(profileId)?.let {
                                (!policy.durationEditable || it.insulinDurationMinutes == duration) &&
                                    it.carbEntryEnabled == carbEntry
                            } == true
                        }
                    }
                },
            )
        }
    }

    profileOptionsId?.let { profileId ->
        profiles.firstOrNull { it.idpId == profileId }?.let { profile ->
            ProfileOptionsDialog(
                profile = profile,
                canDuplicate = canAddProfile(profiles),
                activationBlockedReason = profileActivationValidationError(controlIqEnabled, profile),
                onDismiss = { profileOptionsId = null },
                onActivate = {
                    profileOptionsId = null
                    profileActivationValidationError(controlIqEnabled, profile)?.let {
                        informationDialogText = it
                        return@ProfileOptionsDialog
                    }
                    confirm("Activate Profile?", "Activate '${profile.name}' on the pump?") {
                        if (developerPreviewEnabled) {
                            updatePreviewProfiles(
                                profiles.map { it.copy(active = it.idpId == profileId) },
                                "'${profile.name}' activated in Developer Preview.",
                            )
                        } else {
                            val request = SetActiveIDPRequest(profile.idpId, profile.slotIndex)
                            beginPumpMutation("Activating '${profile.name}'", listOf(request)) { readback ->
                                readback.firstOrNull()?.idpId == profileId && readback.firstOrNull()?.active == true
                            }
                        }
                    }
                },
                onRename = {
                    profileOptionsId = null
                    nameDialog = ProfileNameAction.RENAME to profileId
                },
                onDuplicate = {
                    profileOptionsId = null
                    nameDialog = ProfileNameAction.DUPLICATE to profileId
                },
                onDelete = {
                    profileOptionsId = null
                    confirm("Delete Profile?", "Permanently delete '${profile.name}' from the pump?") {
                        if (developerPreviewEnabled) {
                            updatePreviewProfiles(
                                profiles.filterNot { it.idpId == profileId },
                                "'${profile.name}' deleted in Developer Preview.",
                            )
                            selectedProfileId = null
                        } else {
                            val request = DeleteIDPRequest(profile.idpId, profile.slotIndex)
                            beginPumpMutation("Deleting '${profile.name}'", listOf(request)) { readback ->
                                readback.none { it.idpId == profileId }
                            }
                            selectedProfileId = null
                        }
                    }
                },
            )
        }
    }

    nameDialog?.let { (action, profileId) ->
        profiles.firstOrNull { it.idpId == profileId }?.let { profile ->
            ProfileNameDialog(
                title = if (action == ProfileNameAction.RENAME) "Rename Profile" else "Duplicate Profile",
                initialValue = if (action == ProfileNameAction.RENAME) profile.name else "${profile.name} copy",
                confirmLabel = if (action == ProfileNameAction.RENAME) "Rename" else "Duplicate",
                existingNames = profiles.map { it.name }.toSet() -
                    if (action == ProfileNameAction.RENAME) setOf(profile.name) else emptySet(),
                onDismiss = { nameDialog = null },
                onConfirm = { name ->
                    nameDialog = null
                    if (action == ProfileNameAction.RENAME) {
                        if (developerPreviewEnabled) {
                            updatePreviewProfiles(
                                profiles.map { if (it.idpId == profileId) it.copy(name = name) else it },
                                "Profile renamed to '$name' in Developer Preview.",
                            )
                        } else {
                            val request = RenameIDPRequest(profile.idpId, profile.slotIndex, name)
                            beginPumpMutation("Renaming profile", listOf(request)) { readback ->
                                readback.profile(profileId)?.name == name
                            }
                        }
                    } else {
                        if (!canAddProfile(profiles)) {
                            errorMessage = "The pump supports at most six profiles."
                            return@ProfileNameDialog
                        }
                        if (developerPreviewEnabled) {
                            updatePreviewProfiles(
                                profiles + profile.copy(
                                    idpId = nextAvailableProfileId(profiles),
                                    slotIndex = profiles.size,
                                    name = name,
                                    active = false,
                                ),
                                "Profile duplicated as '$name' in Developer Preview.",
                            )
                        } else {
                            val request = CreateIDPRequest(name, profile.idpId)
                            beginPumpMutation("Duplicating profile", listOf(request)) { readback ->
                                readback.any { it.name == name && it.segments.size == profile.segments.size }
                            }
                        }
                    }
                },
            )
        }
    }

    confirmationTitle?.let { title ->
        AlertDialog(
            onDismissRequest = {
                confirmationTitle = null
                confirmationAction = null
            },
            title = { Text(title) },
            text = { Text(confirmationText) },
            confirmButton = {
                Button(
                    onClick = {
                        val action = confirmationAction
                        confirmationTitle = null
                        confirmationAction = null
                        action?.invoke()
                    },
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmationTitle = null
                        confirmationAction = null
                    },
                ) { Text("Cancel") }
            },
        )
    }

    informationDialogText?.let { message ->
        AlertDialog(
            onDismissRequest = { informationDialogText = null },
            title = { Text("Turn Off Control-IQ First") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { informationDialogText = null }) {
                    Text("OK")
                }
            },
        )
    }

    if (showEditConnectionLostDialog) {
        AlertDialog(
            onDismissRequest = { showEditConnectionLostDialog = false },
            title = { Text("Pump Connection Lost") },
            text = {
                Text(
                    "Your unsaved profile changes are still open and the original pump profile " +
                        "has not been changed. Continue editing, then choose Save Draft Copy. " +
                        "You will be asked to name a separate local copy.",
                )
            },
            confirmButton = {
                Button(onClick = { showEditConnectionLostDialog = false }) {
                    Text("Continue Editing")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditConnectionLostDialog = false
                        closeProfileEditors()
                    },
                ) {
                    Text("Discard Edits")
                }
            },
        )
    }

    pendingProfileDraft?.let { candidate ->
        ProfileNameDialog(
            title = "Name Saved Draft Copy",
            initialValue = "${candidate.profile.name} copy",
            confirmLabel = "Save Copy",
            existingNames = (profiles.map { it.name } + savedProfileDrafts.map { it.profile.name }).toSet(),
            onDismiss = {
                // Return to the still-open editor with every entered value intact.
                pendingProfileDraft = null
            },
            onConfirm = { name ->
                val sid = effectivePumpSid
                if (sid == null) {
                    errorMessage = "The pump identity is unavailable, so the draft copy could not be saved."
                    return@ProfileNameDialog
                }
                val saved = SavedProfileDraft(
                    draftId = UUID.randomUUID().toString(),
                    pumpSid = sid,
                    sourceProfileId = candidate.sourceProfileId,
                    savedAtEpochMillis = System.currentTimeMillis(),
                    profile = candidate.profile.copy(name = name, active = false),
                )
                persistSavedProfileDrafts(savedProfileDrafts + saved)
                pendingProfileDraft = null
                closeProfileEditors()
                statusMessage =
                    "Draft copy '$name' was saved locally. The original pump profile was not changed."
                errorMessage = null
            },
        )
    }

    savedProfileDrafts.firstOrNull { it.draftId == selectedSavedDraftId }?.let { draft ->
        AlertDialog(
            onDismissRequest = { selectedSavedDraftId = null },
            title = { Text(draft.profile.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Saved locally ${formatProfileCacheTime(draft.savedAtEpochMillis)} with " +
                            "${draft.profile.segments.size} time segments. It is inactive and has " +
                            "not replaced the original pump profile.",
                    )
                    Text(
                        when {
                            !pumpProfileWriteReady ->
                                "Reconnect and finish reading pump profiles to create this copy on the pump."
                            !canAddProfile(profiles) ->
                                "The pump's six-profile limit must be resolved before creating this copy."
                            else ->
                                "Create on Pump makes a new inactive profile and leaves the original unchanged."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            removeSavedDraft(draft.draftId)
                            selectedSavedDraftId = null
                            statusMessage = "Saved draft copy '${draft.profile.name}' was deleted."
                        },
                    ) {
                        Text("Delete Saved Draft", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = pumpProfileWriteReady && canAddProfile(profiles),
                    onClick = {
                        selectedSavedDraftId = null
                        createSavedDraftOnPump(draft)
                    },
                ) {
                    Text("Create on Pump")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSavedDraftId = null }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun ProfileOptionsDialog(
    profile: InsulinProfileSnapshot,
    canDuplicate: Boolean,
    activationBlockedReason: String?,
    onDismiss: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profile Options") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onActivate,
                    enabled = !profile.active,
                ) {
                    Text(if (profile.active) "Active Profile" else "Activate")
                }
                if (!profile.active && activationBlockedReason != null) {
                    Text(
                        activationBlockedReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("  Rename")
                }
                TextButton(onClick = onDuplicate, enabled = canDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("  Duplicate")
                }
                TextButton(onClick = onDelete, enabled = !profile.active) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("  Delete", color = if (profile.active) Color.Gray else MaterialTheme.colorScheme.error)
                }
                if (profile.active) {
                    Text("The active profile cannot be deleted.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialValue) { mutableStateOf(initialValue.take(16)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = name.trim()
                    when {
                        normalized.isEmpty() || normalized.toByteArray().size > 16 ->
                            error = "Profile name must contain 1–16 bytes."
                        existingNames.any { it.equals(normalized, ignoreCase = true) } ->
                            error = "That profile name is already in use."
                        else -> onConfirm(normalized)
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProfileBolusSettingsDialog(
    profile: InsulinProfileSnapshot,
    controlIqEnabled: Boolean,
    saveAsDraftCopy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit,
) {
    val policy = profileBolusSettingsPolicy(controlIqEnabled, profile)
    var duration by remember(profile.idpId) { mutableStateOf(profile.insulinDurationMinutes.toString()) }
    var carbEntry by remember(profile.idpId) { mutableStateOf(profile.carbEntryEnabled) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTurnOffControlIqPrompt by remember { mutableStateOf(false) }
    val parsedDuration = if (policy.durationEditable) {
        duration.toIntOrNull()
    } else {
        policy.displayedDurationMinutes
    }
    val dirty = (policy.durationEditable && parsedDuration != profile.insulinDurationMinutes) ||
        carbEntry != profile.carbEntryEnabled

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bolus Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (saveAsDraftCopy) {
                    Text(
                        "Pump connection was lost. Your entries remain here. Saving will " +
                            "create a named local draft copy and will not change the original profile.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedTextField(
                    value = if (policy.durationEditable) {
                        duration
                    } else {
                        policy.displayedDurationMinutes.toString()
                    },
                    onValueChange = {
                        if (policy.durationEditable) duration = it.filter(Char::isDigit)
                        error = null
                    },
                    label = { Text("Insulin Duration (minutes)") },
                    supportingText = {
                        Text(
                            if (policy.durationEditable) {
                                "120–480 minutes (2–8 hours)"
                            } else {
                                "Fixed at 5 hours while Control-IQ is on"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = policy.durationEditable,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Carbohydrates")
                    Switch(
                        checked = carbEntry,
                        onCheckedChange = { requested ->
                            if (controlIqEnabled && !requested) {
                                showTurnOffControlIqPrompt = true
                            } else {
                                carbEntry = requested
                            }
                        },
                    )
                }
                Text(
                    when {
                        policy.carbohydratesRequired ->
                            "Control-IQ requires Carbohydrates to remain on. Tap the toggle for details."
                        carbEntry -> "Bolus entry uses grams and the profile's Carb Ratio."
                        else -> "Bolus entry uses units of insulin instead of grams."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = dirty,
                onClick = {
                    if (parsedDuration == null || parsedDuration !in 120..480) {
                        error = "Insulin duration must be 120–480 minutes."
                    } else if (policy.carbohydratesRequired && !carbEntry) {
                        error = "Carbohydrates must remain on while Control-IQ is on."
                    } else {
                        onConfirm(parsedDuration, carbEntry)
                    }
                },
            ) { Text(if (saveAsDraftCopy) "Save Draft Copy" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTurnOffControlIqPrompt) {
        AlertDialog(
            onDismissRequest = { showTurnOffControlIqPrompt = false },
            title = { Text("Turn Off Control-IQ First") },
            text = {
                Text(
                    "Control-IQ requires Carbohydrates to remain on. " +
                        "Turn off Control-IQ before changing this setting to off.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showTurnOffControlIqPrompt = false }) {
                    Text("OK")
                }
            },
        )
    }
}

private fun StatusMessage.isResponseFor(request: Message): Boolean = when (request) {
    is CreateIDPRequest -> this is CreateIDPResponse
    is DeleteIDPRequest -> this is DeleteIDPResponse
    is RenameIDPRequest -> this is RenameIDPResponse
    is SetActiveIDPRequest -> this is SetActiveIDPResponse
    is SetIDPSegmentRequest -> this is SetIDPSegmentResponse
    is SetIDPSettingsRequest -> this is SetIDPSettingsResponse
    else -> false
}

private fun List<InsulinProfileSnapshot>.profile(idpId: Int): InsulinProfileSnapshot? =
    firstOrNull { it.idpId == idpId }

private fun ProfileSegmentSnapshot.matches(other: ProfileSegmentSnapshot, ignoreIndex: Boolean): Boolean =
    (ignoreIndex || index == other.index) &&
        startMinutes == other.startMinutes &&
        basalMilliunitsPerHour == other.basalMilliunitsPerHour &&
        carbRatioMilliunits == other.carbRatioMilliunits &&
        targetBgMgdl == other.targetBgMgdl &&
        correctionFactorMgdlPerUnit == other.correctionFactorMgdlPerUnit

private fun reindexSegments(segments: List<ProfileSegmentSnapshot>): List<ProfileSegmentSnapshot> =
    segments.sortedBy { it.startMinutes }.mapIndexed { index, segment -> segment.copy(index = index) }

private fun nextAvailableProfileId(profiles: List<InsulinProfileSnapshot>): Int {
    val used = profiles.map { it.idpId }.toSet()
    return (0..255).first { it !in used }
}

private fun currentMinuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun formatProfileTime(minutes: Int): String = LocalTime
    .of((minutes / 60).mod(24), minutes.mod(60))
    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

private fun formatTotalBasal(profile: InsulinProfileSnapshot): String =
    String.format(Locale.US, "%.2f", totalDailyBasalUnits(profile.segments))

private fun formatDurationHours(hours: Double): String =
    if (hours % 1.0 == 0.0) hours.toInt().toString() else String.format(Locale.US, "%.1f", hours)

val profileActionsCommands = listOf(*IDPManager().nextMessages().toTypedArray())

val profileActionsFields = listOf(dataStore.idpManager)

@Preview(showBackground = true)
@Composable
internal fun ProfileActionsDefaultPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            ProfileActions(
                sendMessage = { _, _ -> },
                sendPumpCommands = { _, _ -> },
                developerPreviewEnabledOverride = true,
                navigateBack = {},
            )
        }
    }
}
