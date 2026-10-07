package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.db.historylog.HistoryLogDummyDao
import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.db.historylog.HistoryLogViewModel
import com.jwoglom.controlx2.presentation.components.Line
import com.jwoglom.controlx2.presentation.screens.setUpPreviewState
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.SurfaceBackground
import com.jwoglom.controlx2.presentation.theme.Spacing
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMGlucoseAlertSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl2HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataFsl3HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmDataGxHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG7CGMHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import java.time.Instant

val CgmReadingHistoryLogs = listOf(
    DexcomG6CGMHistoryLog::class.java,
    DexcomG7CGMHistoryLog::class.java,
    CgmDataGxHistoryLog::class.java,
    CgmDataFsl2HistoryLog::class.java,
    CgmDataFsl3HistoryLog::class.java
)

private fun createPreviewHistoryLogViewModel(): HistoryLogViewModel {
    val previewLogs = listOf(
        160, 165, 170, 173, 179, 183, 188, 193, 199, 205, 215, 220, 225, 220, 215,
        205, 199, 193, 188, 183, 179, 173, 170, 160, 145, 130, 125, 115, 108, 102,
        97, 90, 85, 79, 73, 70, 68, 70, 75
    ).mapIndexed { index, mgdl -> cgmEntry(index, mgdl) }
        .map { HistoryLogItem(it) }
        .toMutableList()
    return HistoryLogViewModel(HistoryLogRepo(HistoryLogDummyDao(previewLogs)), 0)
}

@Composable
fun DashboardCgmChart(
    historyLogViewModel: HistoryLogViewModel?,
    modifier: Modifier = Modifier,
    compactCover: Boolean = false,
) {
    val context = LocalContext.current
    val developerPreviewEnabled = remember(context) { Prefs(context).developerPreviewEnabled() }
    val importedHistoryCount = historyLogViewModel?.count?.observeAsState()?.value
    val useSyntheticPreview = developerPreviewEnabled && (importedHistoryCount ?: 0L) == 0L
    val developerPreviewData = remember(useSyntheticPreview) {
        if (useSyntheticPreview) createDeveloperPreviewChartData() else null
    }
    VicoCgmChartCard(
        historyLogViewModel = historyLogViewModel,
        modifier = modifier,
        previewData = developerPreviewData,
        compactCover = compactCover,
    )
}

private fun createDeveloperPreviewChartData(
    currentTimeSeconds: Long = Instant.now().epochSecond,
): ChartPreviewData {
    val startTimeSeconds = currentTimeSeconds - 24L * 60L * 60L
    fun atMinute(minutesAfterStart: Int) = startTimeSeconds + minutesAfterStart * 60L
    val fiveMinutePattern = listOf(
        118, 116, 114, 112, 110, 108, 106, 108, 111, 115, 120, 126,
        133, 141, 150, 160, 172, 185, 196, 205, 194, 180, 164, 151,
        138, 122, 108, 94, 82, 72, 66, 60, 64, 75, 91, 106,
    )
    val values = List(289) { index ->
        val base = fiveMinutePattern[index % fiveMinutePattern.size]
        val slowVariation = ((index / fiveMinutePattern.size) % 3 - 1) * 4
        (base + slowVariation).coerceIn(55, 240)
    }.toMutableList().apply {
        this[lastIndex] = 155
    }
    return ChartPreviewData(
        cgmDataPoints = values.mapIndexed { index, glucose ->
            CgmDataPoint(
                timestamp = startTimeSeconds + index * 5L * 60L,
                value = glucose.toFloat(),
            )
        },
        bolusEvents = listOf(
            BolusEvent(atMinute(285), 0.45f, true, "CONTROL_IQ_AUTO_BOLUS"),
            BolusEvent(atMinute(375), 5.0f, false, "MANUAL"),
            BolusEvent(atMinute(630), 0.60f, true, "CONTROL_IQ_AUTO_BOLUS"),
            BolusEvent(atMinute(810), 4.0f, false, "MANUAL"),
            BolusEvent(atMinute(1155), 0.35f, true, "CONTROL_IQ_AUTO_BOLUS"),
            BolusEvent(atMinute(1235), 3.25f, false, "MANUAL"),
            BolusEvent(atMinute(1345), 0.50f, true, "CONTROL_IQ_AUTO_BOLUS"),
        ),
        basalDataPoints = listOf(
            BasalDataPoint(atMinute(0), 0.5f, false, null),
            BasalDataPoint(atMinute(120), 0.35f, true, 90),
            BasalDataPoint(atMinute(210), 0.5f, false, null),
            BasalDataPoint(atMinute(360), 0.75f, true, 60),
            BasalDataPoint(atMinute(420), 0.5f, false, null),
            BasalDataPoint(atMinute(720), 0.85f, true, 60),
            BasalDataPoint(atMinute(780), 0.5f, false, null),
            BasalDataPoint(atMinute(1080), 0.25f, true, 60),
            BasalDataPoint(atMinute(1140), 0.5f, false, null),
            BasalDataPoint(atMinute(1260), 0.65f, true, 45),
            BasalDataPoint(atMinute(1305), 0.5f, false, null),
        ),
        carbEvents = listOf(
            CarbEvent(atMinute(360), 30, "Breakfast"),
            CarbEvent(atMinute(795), 24, "Lunch"),
        ),
        modeEvents = listOf(
            ModeEvent(atMinute(0), TherapyMode.SLEEP, 420),
            ModeEvent(atMinute(1020), TherapyMode.EXERCISE, 90),
        ),
        pumpTimeChanges = listOf(
            PumpTimeChangeMarker(
                timestamp = atMinute(1110),
                direction = PumpClockChangeDirection.FORWARD,
                destinationTimestamp = atMinute(1110),
            ),
            PumpTimeChangeMarker(
                timestamp = atMinute(1300),
                direction = PumpClockChangeDirection.BACKWARD,
                destinationTimestamp = atMinute(1300),
            ),
        ),
        controlIqEnabled = true,
        currentTimeSeconds = currentTimeSeconds,
    )
}

private fun cgmEntry(index: Int, mgdl: Int, baseTimestamp: Long = 1700000000L): HistoryLog {
    return DexcomG6CGMHistoryLog(
            baseTimestamp + index, index.toLong(),
            0, 1, -2, 6, -89,
            mgdl,
            baseTimestamp + index,
            481, 0
        )
}

@Preview(showBackground = true)
@Composable
internal fun DashboardCgmChartDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceBackground,
        ) {
            val dataStore = LocalDataStore.current
            setUpPreviewState(dataStore)
            dataStore.cgmGlucoseAlertSettingsResponse.value = CGMGlucoseAlertSettingsResponse(
                200, 1, 60, 0,
                80, 1, 30, 0,
            )
            VicoCgmChartCard(
                historyLogViewModel = null,
                previewData = remember { createDeveloperPreviewChartData() },
            )
        }
    }
}
