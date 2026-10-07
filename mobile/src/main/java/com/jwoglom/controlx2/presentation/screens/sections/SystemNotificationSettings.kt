package com.jwoglom.controlx2.presentation.screens.sections

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.SystemNotificationPreferences
import com.jwoglom.controlx2.SystemNotificationPreferencesController
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme

@Composable
fun SystemNotificationSettings(
    innerPadding: PaddingValues = PaddingValues(),
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    var settings by remember { mutableStateOf(prefs.systemNotificationPreferences()) }

    fun update(updated: SystemNotificationPreferences) {
        SystemNotificationPreferencesController.update(context, updated)
        settings = updated
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        ListItem(
            headlineContent = { Text("Back") },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            },
            modifier = Modifier.clickable(onClick = navigateBack),
        )
        HeaderLine("Notifications")
        Divider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("system-notification-settings"),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    text = "These switches control Android notification-shade alerts only. " +
                        "Pump alerts remain available in MobiControl's Notifications tab, and " +
                        "changing a switch never clears an alert on the pump.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { NotificationSettingsGroup("Bolus & Delivery") }
            item {
                NotificationPreferenceRow(
                    title = "Incomplete bolus reminders",
                    description = "Remind me when an opened manual bolus is not completed.",
                    checked = settings.incompleteBolusReminders,
                    testTag = "notification-incomplete-bolus",
                    onCheckedChange = { update(settings.copy(incompleteBolusReminders = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Manual bolus progress",
                    description = "Show preparation, delivery, completion, cancellation, and failure updates.",
                    checked = settings.manualBolusProgress,
                    testTag = "notification-manual-bolus",
                    onCheckedChange = { update(settings.copy(manualBolusProgress = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Control-IQ automatic bolus",
                    description = "Show automatic-bolus activity, completion, and cancellation results.",
                    checked = settings.automaticBolusActivity,
                    testTag = "notification-automatic-bolus",
                    onCheckedChange = { update(settings.copy(automaticBolusActivity = it)) },
                )
            }

            item { NotificationSettingsGroup("CGM") }
            item {
                NotificationPreferenceRow(
                    title = "Glucose level alerts",
                    description = "Show pump-reported fixed-low, low, and high CGM alerts.",
                    checked = settings.cgmGlucoseLevelAlerts,
                    testTag = "notification-cgm-level",
                    onCheckedChange = { update(settings.copy(cgmGlucoseLevelAlerts = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Rise and fall alerts",
                    description = "Show pump-reported rise, rapid-rise, fall, and rapid-fall alerts.",
                    checked = settings.cgmTrendAlerts,
                    testTag = "notification-cgm-trend",
                    onCheckedChange = { update(settings.copy(cgmTrendAlerts = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Sensor and calibration alerts",
                    description = "Show sensor, transmitter, calibration, expiration, and connection alerts.",
                    checked = settings.cgmSensorAndCalibrationAlerts,
                    testTag = "notification-cgm-sensor",
                    onCheckedChange = {
                        update(settings.copy(cgmSensorAndCalibrationAlerts = it))
                    },
                )
            }

            item { NotificationSettingsGroup("Pump & Supplies") }
            item {
                NotificationPreferenceRow(
                    title = "Low pump battery",
                    description = "Show pump-reported low and critically low battery alerts.",
                    checked = settings.lowPumpBattery,
                    testTag = "notification-low-battery",
                    onCheckedChange = { update(settings.copy(lowPumpBattery = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Low cartridge",
                    description = "Show pump-reported low insulin cartridge alerts.",
                    checked = settings.lowCartridge,
                    testTag = "notification-low-cartridge",
                    onCheckedChange = { update(settings.copy(lowCartridge = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Empty cartridge",
                    description = "Show the pump's empty cartridge alarm.",
                    checked = settings.emptyCartridge,
                    testTag = "notification-empty-cartridge",
                    onCheckedChange = { update(settings.copy(emptyCartridge = it)) },
                )
            }
            item {
                NotificationPreferenceRow(
                    title = "Site-change reminders",
                    description = "Show the site-change reminder reported by the pump.",
                    checked = settings.siteChangeReminders,
                    testTag = "notification-site-change",
                    onCheckedChange = { update(settings.copy(siteChangeReminders = it)) },
                )
            }

            item { NotificationSettingsGroup("System") }
            item {
                ListItem(
                    headlineContent = { Text("Pump connection indicator") },
                    supportingContent = {
                        Text(
                            "Always visible while background pump communication is enabled; " +
                                "Android requires this foreground-service indicator.",
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                )
                Divider()
            }
            item {
                val allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
                ListItem(
                    headlineContent = { Text("Android notification settings") },
                    supportingContent = {
                        Text(
                            if (allowed) {
                                "System notifications are currently allowed. Configure individual channels in Android."
                            } else {
                                "Android is currently blocking MobiControl notifications."
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Android, contentDescription = null) },
                    modifier = Modifier
                        .clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                },
                            )
                        }
                        .testTag("open-android-notification-settings"),
                )
                Divider()
            }
        }
    }
}

@Composable
private fun NotificationSettingsGroup(title: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NotificationPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag),
    )
    Divider()
}

@Preview(showBackground = true)
@Composable
internal fun SystemNotificationSettingsPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SystemNotificationSettings(navigateBack = {})
        }
    }
}
