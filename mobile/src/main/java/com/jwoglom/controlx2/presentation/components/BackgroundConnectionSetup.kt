package com.jwoglom.controlx2.presentation.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.BackgroundConnectionAccess

internal data class SamsungPowerGuidance(
    val message: String,
    val showNeverSleepingFallback: Boolean,
)

internal fun samsungPowerGuidance(batteryExempt: Boolean): SamsungPowerGuidance =
    if (batteryExempt) {
        SamsungPowerGuidance(
            message = "Samsung Device Care: MobiControl is already on Android's power " +
                "allowlist, so no additional Never sleeping apps entry is required. It may " +
                "not appear in Samsung's app picker.",
            showNeverSleepingFallback = false,
        )
    } else {
        SamsungPowerGuidance(
            message = "Samsung Device Care: first allow unrestricted battery use above. If " +
                "that option is unavailable, use Never sleeping apps as a fallback.",
            showNeverSleepingFallback = true,
        )
    }

@Composable
fun BackgroundConnectionSetup(
    modifier: Modifier = Modifier,
    onBatteryOptimizationStatusChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val batteryExempt = remember(refreshKey) {
        BackgroundConnectionAccess.isBatteryOptimizationExempt(context)
    }
    val notificationsAllowed = remember(refreshKey) {
        BackgroundConnectionAccess.notificationsAllowed(context)
    }
    val isSamsungDevice = remember { BackgroundConnectionAccess.isSamsungDevice() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshKey += 1
    }

    fun launchWithFallback(primary: Intent, fallback: Intent? = null) {
        when {
            BackgroundConnectionAccess.canOpen(context, primary) -> launcher.launch(primary)
            fallback != null && BackgroundConnectionAccess.canOpen(context, fallback) ->
                launcher.launch(fallback)
        }
    }

    LaunchedEffect(batteryExempt) {
        onBatteryOptimizationStatusChanged(batteryExempt)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (notificationsAllowed) {
                "Notifications: allowed"
            } else {
                "Notifications: not allowed"
            },
            color = if (notificationsAllowed) Color(0xFF287A40) else MaterialTheme.colorScheme.error,
        )
        if (!notificationsAllowed) {
            OutlinedButton(
                onClick = {
                    launchWithFallback(
                        BackgroundConnectionAccess.notificationSettingsIntent(context),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Notification Settings")
            }
        }

        Text(
            if (batteryExempt) {
                "Battery use: unrestricted"
            } else {
                "Battery use: optimized — Android may delay pump reconnection in the background"
            },
            color = if (batteryExempt) Color(0xFF287A40) else MaterialTheme.colorScheme.error,
        )
        if (!batteryExempt) {
            Button(
                onClick = {
                    launchWithFallback(
                        BackgroundConnectionAccess.requestBatteryOptimizationExemptionIntent(context),
                        BackgroundConnectionAccess.batteryOptimizationSettingsIntent(),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow Unrestricted Battery Use")
            }
        }

        if (isSamsungDevice) {
            val guidance = samsungPowerGuidance(batteryExempt)
            Text(
                guidance.message,
                style = MaterialTheme.typography.bodySmall,
            )
            if (guidance.showNeverSleepingFallback) {
                OutlinedButton(
                    onClick = {
                        launchWithFallback(
                            BackgroundConnectionAccess.samsungNeverSleepingAppsIntent(),
                            BackgroundConnectionAccess.batteryOptimizationSettingsIntent(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Never Sleeping Apps")
                }
            }
        }
    }
}
