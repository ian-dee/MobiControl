@file:OptIn(ExperimentalMaterial3Api::class)

package com.jwoglom.controlx2.presentation.screens.sections

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.sync.gdh.GdhPackageInspector
import com.jwoglom.controlx2.sync.gdh.GdhPackageStatus
import com.jwoglom.controlx2.sync.gdh.GdhSyncConfig

internal fun gdhSetupInstructions(): List<String> = listOf(
    "Open GlucoDataHandler and open its menu.",
    "Open Sources, then find Local applications.",
    "Open Juggluco and enable the Juggluco source.",
    "Leave Juggluco Webserver off; MobiControl uses GDH's local broadcast receiver.",
    "Return to MobiControl and enable Send data to GlucoDataHandler.",
    "After the direct values are updating, disable GDH's Notification Reader source to avoid competing duplicate readings.",
)

@Composable
fun GdhSettings(innerPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    val prefs = Prefs(context).prefs()
    var config by remember { mutableStateOf(GdhSyncConfig.load(prefs)) }
    var packageStatus by remember { mutableStateOf(GdhPackageInspector(context).inspect()) }

    fun saveConfig(updated: GdhSyncConfig, toast: String? = null) {
        config = updated
        GdhSyncConfig.save(prefs, updated)
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LazyColumn(
        contentPadding = innerPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            HeaderLine("GlucoDataHandler")
            HorizontalDivider()
        }

        item {
            GdhInstallationStatus(
                status = packageStatus,
                onRefresh = { packageStatus = GdhPackageInspector(context).inspect() },
            )
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("Send data to GlucoDataHandler") },
                supportingContent = {
                    Text(
                        if (config.enabled) {
                            "Sends new pump CGM readings to GDH without restarting MobiControl."
                        } else {
                            "Off by default. Enable after the Juggluco source is enabled in GDH."
                        },
                    )
                },
                leadingContent = { Icon(Icons.Filled.Sync, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = {
                            saveConfig(
                                config.copy(enabled = it),
                                if (it) "GlucoDataHandler export enabled" else "GlucoDataHandler export disabled",
                            )
                        },
                    )
                },
                modifier = Modifier.clickable {
                    val enabled = !config.enabled
                    saveConfig(
                        config.copy(enabled = enabled),
                        if (enabled) "GlucoDataHandler export enabled" else "GlucoDataHandler export disabled",
                    )
                },
            )
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("Include pump IOB") },
                supportingContent = {
                    Text("Sends fresh pump-displayed insulin on board with the current glucose reading.")
                },
                trailingContent = {
                    Switch(
                        checked = config.sendPumpIob,
                        onCheckedChange = { saveConfig(config.copy(sendPumpIob = it)) },
                    )
                },
                modifier = Modifier.clickable {
                    saveConfig(config.copy(sendPumpIob = !config.sendPumpIob))
                },
            )
            HorizontalDivider()
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Setup in GlucoDataHandler", fontWeight = FontWeight.Bold)
                Text(
                    "GDH names this compatible receiver “Juggluco.” The Juggluco app itself is not required, and GDH does not need to be modified.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                gdhSetupInstructions().forEachIndexed { index, instruction ->
                    Text("${index + 1}. $instruction", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Data and limits", fontWeight = FontWeight.Bold)
                Text(
                    "MobiControl sends live BG, trend, sensor-session timing, and—when enabled—fresh pump IOB. It does not send COB or override GDH's alert thresholds.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "This is a live feed, not a 24-hour history import. “Sent” means Android handed the broadcast to an installed GDH package; GDH does not return a display acknowledgement.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GdhInstallationStatus(
    status: GdhPackageStatus,
    onRefresh: () -> Unit,
) {
    val installedNames = status.installedPackages.joinToString { installed ->
        installed.versionName?.let { "v$it" } ?: installed.packageName
    }
    ListItem(
        headlineContent = {
            Text(if (status.installed) "GlucoDataHandler detected" else "GlucoDataHandler not detected")
        },
        supportingContent = {
            Text(
                if (status.installed) {
                    "Installed: $installedNames"
                } else {
                    "Install or update GDH, then tap here to check again."
                },
            )
        },
        leadingContent = {
            Icon(
                if (status.installed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
            )
        },
        trailingContent = { Icon(Icons.Filled.Refresh, contentDescription = "Check again") },
        modifier = Modifier.clickable(onClick = onRefresh),
    )
}
