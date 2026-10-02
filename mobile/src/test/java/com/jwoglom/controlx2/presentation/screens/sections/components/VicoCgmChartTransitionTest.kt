package com.jwoglom.controlx2.presentation.screens.sections.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.LocalDataStore
import com.jwoglom.controlx2.presentation.DataStore
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h840dp-mdpi")
class VicoCgmChartTransitionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun populatedChartSurvivesRepeatedFullCoverAndRangeTransitions() {
        val base = 1_700_000_000L
        val compact = mutableStateOf(false)
        val volatile = mutableStateOf(false)
        val stableData = List(49) { index ->
            CgmDataPoint(base + index * 300L, (145 + index % 20).toFloat())
        }
        val volatileData = List(49) { index ->
            CgmDataPoint(base + index * 300L, if (index % 2 == 0) 40f else 400f)
        }
        val boluses = listOf(
            BolusEvent(base + 3_000L, 1.25f, false, "MANUAL"),
            BolusEvent(base + 8_700L, 0.35f, true, "AUTO"),
        )
        val basal = listOf(
            BasalDataPoint(base, 0.5f, false, null),
            BasalDataPoint(base + 7_200L, 0.2f, true, null),
        )

        compose.setContent {
            val dataStore = androidx.compose.runtime.remember { DataStore() }
            val preview = ChartPreviewData(
                cgmDataPoints = if (volatile.value) volatileData else stableData,
                bolusEvents = boluses,
                basalDataPoints = basal,
                currentTimeSeconds = base + 14_400L,
            )
            CompositionLocalProvider(LocalDataStore provides dataStore) {
                ControlX2Theme(appSkin = AppSkin.FIELD_KIT, dynamicColor = false) {
                    Surface {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(if (compact.value) 300.dp else 460.dp),
                        ) {
                            VicoCgmChartCard(
                                historyLogViewModel = null,
                                previewData = preview,
                                compactCover = compact.value,
                            )
                        }
                    }
                }
            }
        }

        repeat(4) {
            compose.runOnIdle { compact.value = true }
            compose.waitForIdle()
            compose.runOnIdle { volatile.value = true }
            compose.waitForIdle()
            compose.runOnIdle {
                compact.value = false
                volatile.value = false
            }
            compose.waitForIdle()
        }

        compose.onRoot().assertExists()
    }
}
