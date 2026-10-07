package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.CardBackground
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.Elevation
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme
import com.jwoglom.controlx2.presentation.theme.SkinPanel
import com.jwoglom.controlx2.presentation.theme.SkinPanelVariant
import com.jwoglom.controlx2.shared.PumpHydrationPhase
import com.jwoglom.controlx2.shared.PumpHydrationState
import com.jwoglom.controlx2.shared.util.shortTimeAgo
import java.time.Instant

@Composable
fun PumpStatusBar(middleContent: @Composable () -> Unit = {}) {
    val ds = LocalDataStore.current
    val tokens = MobiControlTheme.tokens

    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.align(Alignment.CenterStart),
            contentAlignment = Alignment.CenterStart,
        ) {
            val batteryPercent = ds.batteryPercent.observeAsState()
            val batteryCharging = ds.batteryCharging.observeAsState()
            HorizBatteryIcon(batteryPercent.value, batteryCharging.value)
        }

        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            middleContent()
        }

        Box(
            modifier = Modifier.align(Alignment.CenterEnd),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val cartridgeRemainingUnits = ds.cartridgeRemainingUnits.observeAsState()
            val cartridgeRemainingEstimate = ds.cartridgeRemainingEstimate.observeAsState()
            val cartridgeLowAlertUnits = ds.cartridgeLowAlertUnits.observeAsState()
            HorizCartridgeIcon(cartridgeRemainingUnits.value,
                cartridgeAmountEstimate = cartridgeRemainingEstimate.value == true,
                lowAlertUnits = cartridgeLowAlertUnits.value ?: CartridgeLowUnits,
            )
        }
        }
    }

    if (tokens.usesTechnicalChrome) {
        SkinPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = Spacing.Small),
            variant = SkinPanelVariant.RAISED,
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                content()
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = Spacing.Small),
        ) {
            content()
        }
    }
}

@Composable
fun PumpStatusCard(
    modifier: Modifier = Modifier
) {
    val ds = LocalDataStore.current
    val batteryPercent = ds.batteryPercent.observeAsState()
    val cartridgeRemainingUnits = ds.cartridgeRemainingUnits.observeAsState()
    val cartridgeRemainingEstimate = ds.cartridgeRemainingEstimate.observeAsState()
    val cartridgeLowAlertUnits = ds.cartridgeLowAlertUnits.observeAsState()
    val pumpConnected = ds.pumpConnected.observeAsState()
    val pumpHydration = ds.pumpHydrationState.observeAsState()
    val pumpLastMessage = ds.pumpLastMessageTimestamp.observeAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card),
        shape = RoundedCornerShape(Spacing.CardCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery
            StatusIndicatorItem(
                icon = {
                    HorizBatteryIcon(batteryPercent.value, showValue = false)
                },
                label = "Battery",
                value = "${batteryPercent.value ?: "--"}%"
            )

            // Connection and live-state freshness.
            StatusIndicatorItem(
                icon = {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Last updated",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = "Pump status",
                value = pumpStatusFreshnessText(
                    pumpConnected = pumpConnected.value == true,
                    hydrationState = pumpHydration.value,
                    lastMessageAt = pumpLastMessage.value,
                ),
            )

            // Insulin
            StatusIndicatorItem(
                icon = {
                    HorizCartridgeIcon(
                        cartridgeRemainingUnits.value,
                        cartridgeAmountEstimate = cartridgeRemainingEstimate.value == true,
                        lowAlertUnits = cartridgeLowAlertUnits.value ?: CartridgeLowUnits,
                        showValue = false,
                    )
                },
                label = "Insulin",
                value = cartridgeLevelText(
                    cartridgeRemainingUnits.value,
                    cartridgeRemainingEstimate.value == true,
                ),
            )
        }
    }
}

internal fun pumpStatusFreshnessText(
    pumpConnected: Boolean,
    hydrationState: PumpHydrationState?,
    lastMessageAt: Instant?,
): String = when {
    !pumpConnected -> "Disconnected"
    hydrationState?.phase == PumpHydrationPhase.LIVE_STATE_LOADING -> "Refreshing"
    hydrationState?.phase == PumpHydrationPhase.LIVE_STATE_PARTIAL -> "Partial"
    lastMessageAt == null -> "Waiting"
    else -> shortTimeAgo(lastMessageAt, nowThresholdSeconds = 1)
}

@Composable
private fun StatusIndicatorItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }

        Spacer(Modifier.height(Spacing.ExtraSmall))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusBarDefaultPreview() {
    ControlX2Theme() {
        Surface(
            color = Color.White,
        ) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.batteryCharging.value = false
            PumpStatusBar(middleContent = {
                PreviewConnectionTimestamp("Updated\nstub")
            })
        }
    }
}


@Preview(showBackground = true)
@Composable
internal fun PumpStatusCardPreview() {
    ControlX2Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceBackground
        ) {
            setUpPreviewState(LocalDataStore.current)
            PumpStatusCard()
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusBarCharging() {
    ControlX2Theme() {
        Surface(
            color = Color.White,
        ) {
            LocalDataStore.current.batteryCharging.value = true
            setUpPreviewState(LocalDataStore.current)
            PumpStatusBar(middleContent = {
                PreviewConnectionTimestamp("Updated\nstub")
            })

        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusBarLowInsulinPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.batteryCharging.value = false
            LocalDataStore.current.batteryPercent.value = 47
            LocalDataStore.current.cartridgeRemainingUnits.value = CartridgeLowUnits
            PumpStatusBar(middleContent = {
                PreviewConnectionTimestamp("Updated\nnow")
            })
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun PumpStatusBarLowBatteryPreview() {
    ControlX2Theme {
        Surface(color = Color.White) {
            setUpPreviewState(LocalDataStore.current)
            LocalDataStore.current.batteryCharging.value = false
            LocalDataStore.current.batteryPercent.value = BatteryLowPercent
            LocalDataStore.current.cartridgeRemainingUnits.value = 100
            PumpStatusBar(middleContent = {
                PreviewConnectionTimestamp("Updated\nnow")
            })
        }
    }
}

@Composable
private fun PreviewConnectionTimestamp(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
