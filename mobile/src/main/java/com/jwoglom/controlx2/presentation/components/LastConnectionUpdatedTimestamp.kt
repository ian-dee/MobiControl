package com.jwoglom.controlx2.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.shared.presentation.intervalOf
import com.jwoglom.controlx2.shared.util.shortTimeAgo

@Composable
fun LastConnectionUpdatedTimestamp() {
    val ds = LocalDataStore.current

    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpLastConnectionTimestamp = ds.pumpLastConnectionTimestamp.observeAsState()
    val pumpLastMessageTimestamp = ds.pumpLastMessageTimestamp.observeAsState()

    var pumpLastMessageTimestampRelative: String? by remember { mutableStateOf(null) }
    fun updatePumpLastMessageTsRelative() {
        pumpLastMessageTimestampRelative = pumpLastMessageTimestamp.value?.let {
            shortTimeAgo(it, nowThresholdSeconds = 1)
        }
        //Timber.d("set pumpLastMessageTimestampRelative=%s", pumpLastMessageTimestampRelative)
    }

    LaunchedEffect (pumpLastMessageTimestamp.value) {
        updatePumpLastMessageTsRelative()
    }

    LaunchedEffect (intervalOf(10)) {
        updatePumpLastMessageTsRelative()
    }

    val statusText = when {
        pumpConnected.value == false -> when {
            pumpLastMessageTimestampRelative != null ->
                "Last connected\n$pumpLastMessageTimestampRelative"
            pumpLastConnectionTimestamp.value != null ->
                "Device last seen\n${pumpLastConnectionTimestamp.value}"
            else -> null
        }
        pumpLastMessageTimestamp.value == null -> null
        pumpLastMessageTimestampRelative == null -> null
        else -> "Updated\n$pumpLastMessageTimestampRelative"
    }

    statusText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

    }
}
