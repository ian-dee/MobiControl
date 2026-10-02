package com.jwoglom.controlx2.widget

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.db.historylog.HistoryLogDatabase
import com.jwoglom.controlx2.db.historylog.HistoryLogRepo
import com.jwoglom.controlx2.shared.util.phoneLocalTimeAsTandemEpochSeconds
import com.jwoglom.controlx2.util.DataClientState
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.DexcomG6CGMHistoryLog
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds emulator-only display data through the same persisted stores consumed by the widget. */
@RunWith(AndroidJUnit4::class)
class WidgetEmulatorSeedTest {
    @Test
    fun seedCurrentMetricsAndFourHourHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = Instant.now()
        DataClientState(context).apply {
            cgmReading = "155" to now
            pumpIOB = "3.00" to now
        }
        delay(500)

        val repo = HistoryLogRepo(HistoryLogDatabase.getDatabase(context).historyLogDao())
        val start = now.minusSeconds(4 * 60 * 60L)
        repeat(49) { index ->
            val timestamp = start.plusSeconds(index * 5 * 60L)
            val glucose = when {
                index < 12 -> 115 + index * 3
                index < 28 -> 151 + (index - 12) * 2
                index < 39 -> 183 - (index - 28) * 6
                else -> 117 + (index - 39) * 4
            }
            repo.insert(
                DexcomG6CGMHistoryLog(
                    phoneLocalTimeAsTandemEpochSeconds(timestamp),
                    DEMO_SEQUENCE_BASE + index,
                    0,
                    1,
                    0,
                    0,
                    0,
                    glucose,
                    phoneLocalTimeAsTandemEpochSeconds(timestamp),
                    0,
                    0,
                ),
                DEMO_PUMP_SID,
            )
        }

        listOf(
            DemoBolus(now.minusSeconds(150 * 60L), 1, 1_250, automatic = false),
            DemoBolus(now.minusSeconds(70 * 60L), 2, 650, automatic = true),
            DemoBolus(now.minusSeconds(20 * 60L), 3, 4_170, automatic = false),
        ).forEach { demo ->
            repo.insert(
                BolusDeliveryHistoryLog(
                    phoneLocalTimeAsTandemEpochSeconds(demo.timestamp),
                    DEMO_SEQUENCE_BASE + 100 + demo.id,
                    demo.id,
                    0,
                    setOf(BolusDeliveryHistoryLog.BolusType.CORRECTION),
                    if (demo.automatic) {
                        BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS
                    } else {
                        BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS
                    },
                    0,
                    demo.milliunits,
                    0,
                    demo.milliunits,
                    0,
                    demo.milliunits,
                ),
                DEMO_PUMP_SID,
            )
        }

        MobiControlWidgetProvider.requestUpdate(context)
        delay(1_000)
    }
}

private data class DemoBolus(
    val timestamp: Instant,
    val id: Int,
    val milliunits: Int,
    val automatic: Boolean,
)

private const val DEMO_PUMP_SID = 990
private const val DEMO_SEQUENCE_BASE = 99_000_000L
