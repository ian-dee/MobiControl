package com.jwoglom.controlx2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.presentation.theme.AppSkin
import com.jwoglom.controlx2.shared.enums.GlucoseUnit
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

internal val CAR_WIDGET_COMPACT_SIZE = DpSize(160.dp, 112.dp)
internal val CAR_WIDGET_WIDE_SIZE = DpSize(280.dp, 72.dp)
internal val CAR_WIDGET_LARGE_SIZE = DpSize(280.dp, 168.dp)

internal enum class CarWidgetPresentation {
    COMPACT,
    WIDE,
    LARGE,
}

internal fun carWidgetPresentation(size: DpSize): CarWidgetPresentation = when {
    size.width >= 260.dp && size.height >= 140.dp -> CarWidgetPresentation.LARGE
    size.width >= 240.dp -> CarWidgetPresentation.WIDE
    else -> CarWidgetPresentation.COMPACT
}

/**
 * A passive, responsive Glance surface. The widget reads the same persisted snapshot as the
 * established home/FlexWindow providers and deliberately contains no click or pump action.
 */
internal class MobiControlCarGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(CAR_WIDGET_COMPACT_SIZE, CAR_WIDGET_WIDE_SIZE, CAR_WIDGET_LARGE_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val now = Instant.now()
        val snapshot = runCatching { MobiControlWidgetState.load(context) }
            .onFailure { Timber.e(it, "Unable to load MobiControl Car Glance state") }
            .getOrElse { emptyCarWidgetSnapshot(now) }
        val appSkin = Prefs(context.applicationContext).appSkin()
        MobiControlCarWidgetFreshness.schedule(context, snapshot.nextFreshnessBoundary(now))

        provideContent {
            MobiControlCarWidgetContent(snapshot = snapshot, now = now, appSkin = appSkin)
        }
    }
}

class MobiControlCarGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MobiControlCarGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MobiControlWidgetProvider.ACTION_REFRESH) {
            val pendingResult = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    glanceAppWidget.updateAll(context.applicationContext)
                } catch (t: Throwable) {
                    Timber.e(t, "Unable to refresh MobiControl Car Glance widget")
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }
}

@Composable
internal fun MobiControlCarWidgetContent(
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    appSkin: AppSkin = AppSkin.CURRENT,
) {
    MobiControlCarWidgetContentForPresentation(
        presentation = carWidgetPresentation(LocalSize.current),
        snapshot = snapshot,
        now = now,
        appSkin = appSkin,
    )
}

@Composable
internal fun MobiControlCarWidgetContentForPresentation(
    presentation: CarWidgetPresentation,
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    appSkin: AppSkin = AppSkin.CURRENT,
) {
    val palette = widgetSkinPalette(appSkin)
    when (presentation) {
        CarWidgetPresentation.COMPACT -> CompactCarWidget(snapshot, now, palette)
        CarWidgetPresentation.WIDE -> WideCarWidget(snapshot, now, palette)
        CarWidgetPresentation.LARGE -> LargeCarWidget(snapshot, now, palette)
    }
}

@Composable
private fun CompactCarWidget(
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    palette: WidgetSkinPalette,
) {
    WidgetFrame(testTag = CAR_COMPACT_TEST_TAG, palette = palette) {
        Header(
            status = snapshot.dataStatus(now),
            mode = snapshot.displayedControlIqMode(),
            compact = true,
            palette = palette,
        )
        Spacer(GlanceModifier.height(5.dp))
        GlucoseLine(snapshot, now, valueSizeSp = 29, trendSizeSp = 17, palette = palette)
        Spacer(GlanceModifier.height(3.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${snapshot.iobDisplay()} u IOB",
                style = metricStyle(
                    color = palette.color(
                        if (snapshot.iob.isStale(now)) palette.staleTextColor else palette.iobTextColor,
                    ),
                    sizeSp = 17,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = snapshot.freshnessText(now),
                style = labelStyle(palette.color(palette.secondaryTextColor), 7),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun WideCarWidget(
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    palette: WidgetSkinPalette,
) {
    HorizontalWidgetFrame(testTag = CAR_WIDE_TEST_TAG, palette = palette) {
        Column(
            modifier = GlanceModifier.width(72.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MOBICONTROL", style = labelStyle(palette.color(palette.primaryTextColor), 9), maxLines = 1)
            Spacer(GlanceModifier.height(3.dp))
            StatusText(snapshot.dataStatus(now), palette)
            snapshot.displayedControlIqMode()?.let { mode ->
                Spacer(GlanceModifier.height(2.dp))
                ModeIndicator(mode, sizeSp = 7)
            }
        }
        Spacer(GlanceModifier.width(9.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlucoseLine(snapshot, now, valueSizeSp = 27, trendSizeSp = 16, palette = palette)
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(
            modifier = GlanceModifier.width(72.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${snapshot.iobDisplay()} u",
                style = metricStyle(
                    color = palette.color(
                        if (snapshot.iob.isStale(now)) palette.staleTextColor else palette.iobTextColor,
                    ),
                    sizeSp = 20,
                ),
                maxLines = 1,
            )
            Text("IOB", style = labelStyle(palette.color(palette.secondaryTextColor), 8), maxLines = 1)
            Text(
                snapshot.freshnessText(now),
                style = labelStyle(palette.color(palette.secondaryTextColor), 7),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LargeCarWidget(
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    palette: WidgetSkinPalette,
) {
    WidgetFrame(testTag = CAR_LARGE_TEST_TAG, palette = palette) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.width(76.dp)) {
                Text("MOBICONTROL", style = labelStyle(palette.color(palette.primaryTextColor), 9), maxLines = 1)
                Spacer(GlanceModifier.height(3.dp))
                StatusText(snapshot.dataStatus(now), palette)
                snapshot.displayedControlIqMode()?.let { mode ->
                    Spacer(GlanceModifier.height(2.dp))
                    ModeIndicator(mode, sizeSp = 7)
                }
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                GlucoseLine(snapshot, now, valueSizeSp = 25, trendSizeSp = 15, palette = palette)
            }
            Column(
                modifier = GlanceModifier.width(72.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "${snapshot.iobDisplay()} u",
                    style = metricStyle(
                        color = palette.color(
                            if (snapshot.iob.isStale(now)) palette.staleTextColor else palette.iobTextColor,
                        ),
                        sizeSp = 18,
                    ),
                    maxLines = 1,
                )
                Text("IOB", style = labelStyle(palette.color(palette.secondaryTextColor), 8), maxLines = 1)
            }
        }
        Text(
            snapshot.freshnessText(now),
            style = labelStyle(palette.color(palette.secondaryTextColor), 7),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        val chart = remember(snapshot, palette) {
            WidgetChartRenderer.render(snapshot, widthPx = 560, heightPx = 190, palette = palette.chart)
        }
        val chartContent = @Composable {
            Image(
                provider = ImageProvider(chart),
                contentDescription = "Four-hour glucose and bolus history",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .semantics { testTag = CAR_CHART_TEST_TAG },
                contentScale = ContentScale.FillBounds,
            )
        }
        if (palette.skin == AppSkin.FIELD_KIT) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(ImageProvider(palette.chartPanelDrawable))
                    .cornerRadius(palette.innerRadiusDp.dp)
                    .padding(3.dp),
            ) {
                chartContent()
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(
                        ColorProvider(
                            day = palette.color(palette.chartSurfaceColor),
                            night = palette.color(palette.chartSurfaceColor),
                        ),
                    )
                    .cornerRadius(palette.innerRadiusDp.dp)
                    .padding(3.dp),
            ) { chartContent() }
        }
    }
}

@Composable
private fun WidgetFrame(
    testTag: String,
    palette: WidgetSkinPalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (palette.skin == AppSkin.FIELD_KIT) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(palette.backgroundDrawable))
                .appWidgetBackground()
                .cornerRadius(palette.outerRadiusDp.dp)
                .padding(8.dp)
                .semantics { this.testTag = testTag },
            content = content,
        )
        return
    }
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(
            ColorProvider(
                day = palette.color(palette.surfaceColor),
                night = palette.color(palette.surfaceColor),
            ),
        )
        .appWidgetBackground()
        .cornerRadius(palette.outerRadiusDp.dp)
        .padding(8.dp)
        .semantics { this.testTag = testTag }
    Column(modifier = modifier, content = content)
}

@Composable
private fun HorizontalWidgetFrame(
    testTag: String,
    palette: WidgetSkinPalette,
    content: @Composable RowScope.() -> Unit,
) {
    if (palette.skin == AppSkin.FIELD_KIT) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(palette.backgroundDrawable))
                .appWidgetBackground()
                .cornerRadius(palette.outerRadiusDp.dp)
                .padding(8.dp)
                .semantics { this.testTag = testTag },
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                ColorProvider(
                    day = palette.color(palette.surfaceColor),
                    night = palette.color(palette.surfaceColor),
                ),
            )
            .appWidgetBackground()
            .cornerRadius(palette.outerRadiusDp.dp)
            .padding(8.dp)
            .semantics { this.testTag = testTag },
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Header(
    status: WidgetDataStatus,
    mode: WidgetControlIqMode?,
    compact: Boolean,
    palette: WidgetSkinPalette,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MOBICONTROL",
            style = labelStyle(palette.color(palette.primaryTextColor), if (compact) 9 else 10),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        mode?.let {
            ModeIndicator(it, sizeSp = 7)
            Spacer(GlanceModifier.width(6.dp))
        }
        StatusText(status, palette)
    }
}

@Composable
private fun ModeIndicator(mode: WidgetControlIqMode, sizeSp: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(mode.iconResource),
            contentDescription = "${mode.label} Control-IQ mode",
            modifier = GlanceModifier.width(10.dp).height(10.dp),
        )
        Spacer(GlanceModifier.width(2.dp))
        Text(
            text = mode.label,
            style = labelStyle(Color(mode.color), sizeSp),
            maxLines = 1,
        )
    }
}

@Composable
private fun GlucoseLine(
    snapshot: MobiControlWidgetSnapshot,
    now: Instant,
    valueSizeSp: Int,
    trendSizeSp: Int,
    palette: WidgetSkinPalette,
) {
    val glucoseColor = when {
        !snapshot.displaysCurrentGlucose() -> palette.color(palette.neutralTextColor)
        snapshot.glucose.isStale(now) -> palette.color(palette.staleTextColor)
        else -> palette.color(palette.glucoseTextColor)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = snapshot.glucoseDisplay(),
            style = metricStyle(glucoseColor, valueSizeSp),
            maxLines = 1,
        )
        snapshot.trendArrowDisplay(now)?.let { arrow ->
            Spacer(GlanceModifier.width(4.dp))
            Text(arrow, style = metricStyle(glucoseColor, trendSizeSp), maxLines = 1)
        }
        Spacer(GlanceModifier.width(5.dp))
        Text(
            snapshot.glucoseLabel(),
            style = labelStyle(palette.color(palette.secondaryTextColor), 8),
            maxLines = 2,
        )
    }
}

@Composable
private fun StatusText(status: WidgetDataStatus, palette: WidgetSkinPalette) {
    Text(
        text = when (status) {
            WidgetDataStatus.LIVE -> "LIVE"
            WidgetDataStatus.STALE -> "STALE"
            WidgetDataStatus.PARTIAL -> "PARTIAL"
            WidgetDataStatus.NO_DATA -> "NO DATA"
        },
        style = labelStyle(
            color = when (status) {
                WidgetDataStatus.LIVE -> palette.color(palette.liveStatusColor)
                WidgetDataStatus.STALE -> palette.color(palette.staleStatusColor)
                WidgetDataStatus.PARTIAL -> palette.color(palette.partialStatusColor)
                WidgetDataStatus.NO_DATA -> palette.color(palette.noDataStatusColor)
            },
            sizeSp = 8,
        ),
        maxLines = 1,
    )
}

private fun metricStyle(color: Color, sizeSp: Int) = TextStyle(
    color = ColorProvider(day = color, night = color),
    fontSize = sizeSp.sp,
    fontWeight = FontWeight.Medium,
)

private fun labelStyle(color: Color, sizeSp: Int) = TextStyle(
    color = ColorProvider(day = color, night = color),
    fontSize = sizeSp.sp,
    fontWeight = FontWeight.Medium,
)

private fun emptyCarWidgetSnapshot(now: Instant) = MobiControlWidgetSnapshot(
    glucose = WidgetMetric(null, null),
    iob = WidgetMetric(null, null),
    glucoseUnit = GlucoseUnit.MGDL,
    chartEndTimestamp = now.epochSecond,
    cgmPoints = emptyList(),
    boluses = emptyList(),
    lowerControlLimitMgdl = 70,
    upperControlLimitMgdl = 180,
    colorLowDots = true,
    colorHighDots = true,
)

private object MobiControlCarWidgetFreshness {
    fun schedule(context: Context, boundary: Instant?) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MobiControlCarGlanceWidgetReceiver::class.java)
            .setAction(MobiControlWidgetProvider.ACTION_REFRESH)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            MobiControlCarGlanceWidgetReceiver::class.java.name.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent)
        boundary?.let {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, it.toEpochMilli(), pendingIntent)
        }
    }
}

private fun WidgetSkinPalette.color(argb: Int): Color = Color(argb)

internal const val CAR_COMPACT_TEST_TAG = "car_widget_compact"
internal const val CAR_WIDE_TEST_TAG = "car_widget_wide"
internal const val CAR_LARGE_TEST_TAG = "car_widget_large"
internal const val CAR_CHART_TEST_TAG = "car_widget_chart"
