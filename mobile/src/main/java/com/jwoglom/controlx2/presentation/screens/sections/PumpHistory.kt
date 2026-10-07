package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pumpHistoryEventTimestampFormatter =
    DateTimeFormatter.ofPattern("MMMM d, uuuu h:mma", Locale.getDefault())
private val pumpHistoryDayFormatter =
    DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

@Composable
fun PumpHistory(
    innerPadding: PaddingValues = PaddingValues(),
    historyLogViewModel: HistoryLogViewModel? = null,
    navigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val dataStore = LocalDataStore.current
    val developerPreviewEnabled = Prefs(context).developerPreviewEnabled()
    val pumpClock by dataStore.timeSinceResetResponse.observeAsState()
    val latestHistoryItem = historyLogViewModel?.latest?.observeAsState()?.value
    val initialAnchor = if (developerPreviewEnabled) {
        latestHistoryItem?.pumpTimeSec ?: HistoryLogViewModel.estimateCurrentPumpTimeSec()
    } else {
        pumpClock?.currentTime ?: HistoryLogViewModel.estimateCurrentPumpTimeSec()
    }
    var anchorPumpTimeSec by rememberSaveable { mutableLongStateOf(initialAnchor) }
    var anchoredToImportedHistory by rememberSaveable { mutableStateOf(false) }
    var pageCount by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(developerPreviewEnabled, latestHistoryItem?.seqId) {
        if (developerPreviewEnabled && latestHistoryItem != null && !anchoredToImportedHistory) {
            anchorPumpTimeSec = latestHistoryItem.pumpTimeSec
            pageCount = 1
            anchoredToImportedHistory = true
        }
    }
    val oldestPumpTimeSec = pumpHistoryOldestInclusive(anchorPumpTimeSec, pageCount)

    val emptyHistory = remember { MutableLiveData<List<HistoryLogItem>>(emptyList()) }
    val historySource = remember(historyLogViewModel, oldestPumpTimeSec, anchorPumpTimeSec) {
        historyLogViewModel?.itemsForTypesInTimeRange(
            typeClasses = PumpHistoryLogClasses,
            minPumpTimeSec = oldestPumpTimeSec,
            maxPumpTimeSec = anchorPumpTimeSec,
        ) ?: emptyHistory
    }
    val historyItems by historySource.observeAsState(emptyList())
    val storedEvents = remember(historyItems) {
        historyLogItemsToPumpHistoryEvents(historyItems)
            .sortedWith(
                compareByDescending<PumpHistoryEvent> { it.pumpTimeSec }
                    .thenByDescending { it.stableId },
            )
    }
    val showingPreviewSamples = developerPreviewEnabled && storedEvents.isEmpty()
    val events = if (showingPreviewSamples) {
        developerPreviewPumpHistoryEvents(
            anchorPumpTimeSec = anchorPumpTimeSec,
            oldestPumpTimeSec = oldestPumpTimeSec,
        )
    } else {
        storedEvents
    }
    val eventDays = remember(events) { groupPumpHistoryEventsByDay(events) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Settings")
                }
                Text(
                    text = "Pump History",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Divider()
        }

        item(key = "range-summary") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Last ${pageCount * PUMP_HISTORY_PAGE_HOURS} hours",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Newest events appear first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showingPreviewSamples) {
                    Text(
                        text = "Developer Preview sample history — no pump events",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Divider()
        }

        if (events.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "No pump events in this time range",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Use the button below to look farther back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            eventDays.forEach { day ->
                item(key = "day-${day.date}") {
                    PumpHistoryDayHeader(day.date.format(pumpHistoryDayFormatter))
                }
                items(day.events, key = { it.stableId }) { event ->
                    PumpHistoryEventRow(event)
                    Divider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }

        item(key = "view-more-$pageCount") {
            Button(
                onClick = { pageCount += 1 },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Text("View 24 more hours")
            }
        }
    }
}

@Composable
private fun PumpHistoryDayHeader(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun PumpHistoryEventRow(event: PumpHistoryEvent) {
    val (icon, color) = pumpHistoryEventStyle(event.kind)
    ListItem(
        headlineContent = {
            Text(event.title, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.detail)
                Text(
                    text = event.occurredAt.format(pumpHistoryEventTimestampFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun pumpHistoryEventStyle(kind: PumpHistoryEventKind): Pair<ImageVector, Color> = when (kind) {
    PumpHistoryEventKind.BOLUS -> Icons.Filled.Send to MaterialTheme.colorScheme.primary
    PumpHistoryEventKind.AUTOMATIC_BOLUS -> Icons.Filled.Refresh to MaterialTheme.colorScheme.tertiary
    PumpHistoryEventKind.BASAL -> Icons.Filled.Info to MaterialTheme.colorScheme.secondary
    PumpHistoryEventKind.ALERT -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
}
