package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.GraphDisplayPreferences
import com.jwoglom.controlx2.MAX_GRAPH_DISPLAY_THRESHOLD_MGDL
import com.jwoglom.controlx2.MIN_GRAPH_DISPLAY_THRESHOLD_MGDL
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.components.HeaderLine
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.GraphGlucoseDotColors

internal data class GraphDisplayThresholdValidation(
    val lowerThresholdMgdl: Int?,
    val upperThresholdMgdl: Int?,
    val message: String?,
) {
    val isValid: Boolean
        get() = lowerThresholdMgdl != null && upperThresholdMgdl != null && message == null
}

internal fun validateGraphDisplayThresholds(
    lowerRaw: String,
    upperRaw: String,
): GraphDisplayThresholdValidation {
    val lower = lowerRaw.toIntOrNull()
    val upper = upperRaw.toIntOrNull()
    if (lower == null || upper == null) {
        return GraphDisplayThresholdValidation(
            lower,
            upper,
            "Enter both control limits as whole numbers.",
        )
    }
    if (lower !in MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL ||
        upper !in MIN_GRAPH_DISPLAY_THRESHOLD_MGDL..MAX_GRAPH_DISPLAY_THRESHOLD_MGDL
    ) {
        return GraphDisplayThresholdValidation(
            lower,
            upper,
            "Control limits must be between 40 and 400 mg/dL.",
        )
    }
    if (lower >= upper) {
        return GraphDisplayThresholdValidation(
            lower,
            upper,
            "The lower control limit must be below the upper control limit.",
        )
    }
    return GraphDisplayThresholdValidation(lower, upper, null)
}

@Composable
fun GraphDisplaySettings(
    innerPadding: PaddingValues = PaddingValues(),
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    val saved = remember { prefs.graphDisplayPreferences() }

    var lowerRaw by remember { mutableStateOf(saved.lowerThresholdMgdl.toString()) }
    var upperRaw by remember { mutableStateOf(saved.upperThresholdMgdl.toString()) }
    var showLowerLine by remember { mutableStateOf(saved.showLowerLimitLine) }
    var showUpperLine by remember { mutableStateOf(saved.showUpperLimitLine) }
    var colorLowDots by remember { mutableStateOf(saved.colorDotsBelowLowerLimit) }
    var colorHighDots by remember { mutableStateOf(saved.colorDotsAboveUpperLimit) }

    val validation = validateGraphDisplayThresholds(lowerRaw, upperRaw)

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
        HeaderLine("Graph Display")
        Divider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("graph-display-settings"),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Text(
                    text = "Control limits change only the dashboard graph and dot colors. They do not change CGM High or Low Alerts on the pump.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                GraphLimitSection(
                    title = "Upper Control Limit",
                    thresholdRaw = upperRaw,
                    thresholdTag = "graph-upper-limit-input",
                    onThresholdChange = { raw ->
                        if (raw.length <= 3 && raw.all(Char::isDigit)) upperRaw = raw
                    },
                    showLine = showUpperLine,
                    onShowLineChange = { showUpperLine = it },
                    showLineLabel = "Show upper control limit line",
                    colorDots = colorHighDots,
                    onColorDotsChange = { colorHighDots = it },
                    colorDotsLabel = "Color dots above the upper control limit",
                )
            }

            item {
                GraphLimitSection(
                    title = "Lower Control Limit",
                    thresholdRaw = lowerRaw,
                    thresholdTag = "graph-lower-limit-input",
                    onThresholdChange = { raw ->
                        if (raw.length <= 3 && raw.all(Char::isDigit)) lowerRaw = raw
                    },
                    showLine = showLowerLine,
                    onShowLineChange = { showLowerLine = it },
                    showLineLabel = "Show lower control limit line",
                    colorDots = colorLowDots,
                    onColorDotsChange = { colorLowDots = it },
                    colorDotsLabel = "Color dots below the lower control limit",
                )
            }

            item {
                GraphDotLegend()
            }

            if (validation.message != null) {
                item {
                    Text(
                        text = validation.message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = navigateBack) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        modifier = Modifier.testTag("save-graph-display-settings"),
                        enabled = validation.isValid,
                        onClick = {
                            prefs.setGraphDisplayPreferences(
                                GraphDisplayPreferences(
                                    lowerThresholdMgdl = validation.lowerThresholdMgdl!!,
                                    upperThresholdMgdl = validation.upperThresholdMgdl!!,
                                    showLowerLimitLine = showLowerLine,
                                    showUpperLimitLine = showUpperLine,
                                    colorDotsBelowLowerLimit = colorLowDots,
                                    colorDotsAboveUpperLimit = colorHighDots,
                                ),
                            )
                            navigateBack()
                        },
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphLimitSection(
    title: String,
    thresholdRaw: String,
    thresholdTag: String,
    onThresholdChange: (String) -> Unit,
    showLine: Boolean,
    onShowLineChange: (Boolean) -> Unit,
    showLineLabel: String,
    colorDots: Boolean,
    onColorDotsChange: (Boolean) -> Unit,
    colorDotsLabel: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(thresholdTag),
                value = thresholdRaw,
                onValueChange = onThresholdChange,
                label = { Text("$title value") },
                suffix = { Text("mg/dL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            GraphDisplaySwitchRow(showLineLabel, showLine, onShowLineChange)
            GraphDisplaySwitchRow(colorDotsLabel, colorDots, onColorDotsChange)
        }
    }
}

@Composable
private fun GraphDisplaySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GraphDotLegend() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Dot Colors", fontWeight = FontWeight.Bold)
            DotLegendItem("Between control limits", GraphGlucoseDotColors.InRangeFill, GraphGlucoseDotColors.InRangeOutline)
            DotLegendItem("Above upper control limit", GraphGlucoseDotColors.HighFill, GraphGlucoseDotColors.HighOutline)
            DotLegendItem("Below lower control limit", GraphGlucoseDotColors.LowFill, GraphGlucoseDotColors.LowOutline)
        }
    }
}

@Composable
private fun DotLegendItem(label: String, fill: Color, outline: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(fill, CircleShape)
                .border(1.5.dp, outline, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
internal fun GraphDisplaySettingsPreview() {
    ControlX2Theme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            GraphDisplaySettings(navigateBack = {})
        }
    }
}
