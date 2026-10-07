package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import java.time.Instant

@Composable
internal fun DisconnectedPumpBanner(
    prolongedFailure: Boolean,
    connectionCheckInFlight: Boolean,
    lastConnectedAt: Instant?,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastConnectedText by remember(lastConnectedAt) { mutableStateOf<String?>(null) }

    fun updateLastConnectedText() {
        lastConnectedText = lastConnectedAt?.let {
            "Last connected ${shortTimeAgo(it, nowThresholdSeconds = 1)}."
        }
    }

    LaunchedEffect(lastConnectedAt) {
        updateLastConnectedText()
    }
    LaunchedEffect(intervalOf(10)) {
        updateLastConnectedText()
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Pump disconnected",
                )
                Text(
                    text = "Pump disconnected — reconnecting automatically.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Keep the pump nearby and make sure Bluetooth is on.",
                style = MaterialTheme.typography.bodyMedium,
            )
            lastConnectedText?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (prolongedFailure) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = !connectionCheckInFlight,
                    onClick = onCheckConnection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (connectionCheckInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Checking connection…",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        Text("Check Connection")
                    }
                }
            }
        }
    }
}
