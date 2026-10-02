package com.jwoglom.controlx2.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ProgressIndicatorDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.screens.PumpSetupStage

internal const val PumpPairingStepCount = 8

internal data class PumpPairingProgressPresentation(
    val visible: Boolean,
    val label: String = "",
    val progress: Float = 0f,
)

/**
 * The numbered progress indicator describes pump pairing only. Profile hydration and the
 * optional clock decision happen after the secure pairing has already completed.
 */
internal fun pumpPairingProgressPresentation(
    stage: PumpSetupStage?,
    initialSetup: Boolean,
): PumpPairingProgressPresentation {
    if (stage == null || stage in setOf(
            PumpSetupStage.PUMPX2_READING_PUMP_PROFILES,
            PumpSetupStage.PUMPX2_PUMP_CONNECTED,
            PumpSetupStage.PUMPX2_SET_PUMP_TIME,
            PumpSetupStage.PUMPX2_SYNC_PUMP_DATA,
            PumpSetupStage.PUMPX2_BACKGROUND_CONNECTION,
        )
    ) {
        return PumpPairingProgressPresentation(visible = false)
    }

    if (!initialSetup) {
        return PumpPairingProgressPresentation(visible = false)
    }

    val pairingStep = stage.step.coerceIn(0, PumpPairingStepCount)
    return PumpPairingProgressPresentation(
        visible = true,
        label = "${stage.description} (stage $pairingStep of $PumpPairingStepCount)",
        progress = pairingStep.toFloat() / PumpPairingStepCount.toFloat(),
    )
}

@Composable
fun PumpSetupStageProgress(
    initialSetup: Boolean = false,
) {
    val ds = LocalDataStore.current
    val setupStage = ds.pumpSetupStage.observeAsState()

    val presentation = pumpPairingProgressPresentation(setupStage.value, initialSetup)
    var progress by remember { mutableStateOf(presentation.progress) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )
    LaunchedEffect(presentation.progress) {
        progress = presentation.progress
    }

    if (!presentation.visible) {
        return
    }

    Line(
        presentation.label,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp)
    )
    LinearProgressIndicator(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .padding(10.dp)
            .fillMaxWidth(),
        progress = animatedProgress,
    )
}
