package com.jwoglom.controlx2.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.jwoglom.controlx2.MainActivity
import com.jwoglom.controlx2.Prefs
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin
import java.time.Instant
import timber.log.Timber

internal enum class WidgetPresentation {
    COMPACT,
    EXPANDED,
    COVER_COMPACT,
    COVER_WIDE,
}

internal fun widgetPresentation(widthDp: Int, heightDp: Int): WidgetPresentation =
    if (widthDp >= 260 && heightDp >= 150) WidgetPresentation.EXPANDED
    else WidgetPresentation.COMPACT

internal fun resolvedWidgetPresentation(
    widthDp: Int,
    heightDp: Int,
    override: WidgetPresentation?,
): WidgetPresentation = override ?: widgetPresentation(widthDp, heightDp)

internal object MobiControlWidgetRenderer {
    fun render(
        context: Context,
        appWidgetId: Int,
        options: Bundle,
        snapshot: MobiControlWidgetSnapshot,
        now: Instant = Instant.now(),
        presentationOverride: WidgetPresentation? = null,
        appSkin: AppSkin = Prefs(context.applicationContext).appSkin(),
    ): RemoteViews {
        // Launchers report portrait/landscape bounds together: portrait uses the narrower width
        // and taller height; landscape uses the wider width and shallower height.
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 168)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 84)
        val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidthDp)
        val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeightDp)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthDp = if (landscape) maxWidthDp else minWidthDp
        val heightDp = if (landscape) minHeightDp else maxHeightDp
        val presentation = resolvedWidgetPresentation(widthDp, heightDp, presentationOverride)
        Timber.d(
            "Rendering MobiControl widget %d at %dx%ddp as %s",
            appWidgetId,
            widthDp,
            heightDp,
            presentation,
        )
        val expanded = presentation == WidgetPresentation.EXPANDED
        val palette = widgetSkinPalette(appSkin, presentation)
        val views = RemoteViews(
            context.packageName,
            when (presentation) {
                WidgetPresentation.COMPACT -> if (appSkin == AppSkin.FIELD_KIT) {
                    R.layout.mobicontrol_widget_compact_field_kit
                } else R.layout.mobicontrol_widget_compact
                WidgetPresentation.EXPANDED -> if (appSkin == AppSkin.FIELD_KIT) {
                    R.layout.mobicontrol_widget_field_kit
                } else R.layout.mobicontrol_widget
                WidgetPresentation.COVER_COMPACT -> if (appSkin == AppSkin.FIELD_KIT) {
                    R.layout.mobicontrol_cover_widget_compact_field_kit
                } else R.layout.mobicontrol_cover_widget_compact
                WidgetPresentation.COVER_WIDE -> if (appSkin == AppSkin.FIELD_KIT) {
                    R.layout.mobicontrol_cover_widget_wide_field_kit
                } else R.layout.mobicontrol_cover_widget_wide
            },
        )
        applyWidgetSkin(views, presentation, palette)

        views.setTextViewText(R.id.widget_bg_value, snapshot.glucoseDisplay())
        val trendArrow = snapshot.trendArrowDisplay(now)
        views.setTextViewText(R.id.widget_bg_trend, trendArrow.orEmpty())
        views.setViewVisibility(
            R.id.widget_bg_trend,
            if (trendArrow == null) View.GONE else View.VISIBLE,
        )
        views.setTextViewText(R.id.widget_bg_label, snapshot.glucoseLabel())
        views.setTextViewText(R.id.widget_iob_value, snapshot.iobDisplay())
        views.setTextViewText(R.id.widget_freshness, snapshot.freshnessText(now))
        val mode = snapshot.displayedControlIqMode()
        views.setViewVisibility(
            R.id.widget_mode_chip,
            if (mode == null) View.GONE else View.VISIBLE,
        )
        mode?.let {
            views.setImageViewResource(R.id.widget_mode_icon, it.iconResource)
            views.setTextViewText(R.id.widget_mode_label, context.getString(it.labelResource))
            views.setTextColor(R.id.widget_mode_label, it.color)
        }
        val status = snapshot.dataStatus(now)
        views.setTextViewText(
            R.id.widget_status,
            context.getString(status.labelResource),
        )
        views.setTextColor(R.id.widget_status, status.textColor(palette))
        val glucoseTextColor = if (!snapshot.displaysCurrentGlucose()) palette.neutralTextColor
        else if (snapshot.glucose.isStale(now)) palette.staleTextColor else palette.glucoseTextColor
        views.setTextColor(R.id.widget_bg_value, glucoseTextColor)
        views.setTextColor(R.id.widget_bg_trend, glucoseTextColor)
        views.setTextColor(
            R.id.widget_iob_value,
            if (snapshot.iob.isStale(now)) palette.staleTextColor else palette.iobTextColor,
        )

        views.setTextViewTextSize(
            R.id.widget_bg_value,
            TypedValue.COMPLEX_UNIT_SP,
            when (presentation) {
                WidgetPresentation.COMPACT -> if (snapshot.displaysCurrentGlucose()) 24f else 14f
                WidgetPresentation.EXPANDED -> if (snapshot.displaysCurrentGlucose()) 26f else 16f
                WidgetPresentation.COVER_COMPACT -> if (snapshot.displaysCurrentGlucose()) 32f else 18f
                WidgetPresentation.COVER_WIDE -> if (snapshot.displaysCurrentGlucose()) 28f else 16f
            },
        )
        views.setTextViewTextSize(
            R.id.widget_iob_value,
            TypedValue.COMPLEX_UNIT_SP,
            when (presentation) {
                WidgetPresentation.COMPACT -> 22f
                WidgetPresentation.EXPANDED -> 24f
                WidgetPresentation.COVER_COMPACT -> 19f
                WidgetPresentation.COVER_WIDE -> 20f
            },
        )
        views.setTextViewTextSize(
            R.id.widget_bg_trend,
            TypedValue.COMPLEX_UNIT_SP,
            when (presentation) {
                WidgetPresentation.COMPACT -> 13f
                WidgetPresentation.EXPANDED -> 16f
                WidgetPresentation.COVER_COMPACT -> 18f
                WidgetPresentation.COVER_WIDE -> 16f
            },
        )

        if (expanded) {
            views.setViewVisibility(R.id.widget_chart_header, View.VISIBLE)
            views.setViewVisibility(R.id.widget_chart_panel, View.VISIBLE)
            views.setViewVisibility(R.id.widget_chart_legend, View.VISIBLE)
            val density = context.resources.displayMetrics.density
            val bitmapWidth = (widthDp * density).toInt().coerceIn(320, 640)
            val bitmapHeight = ((heightDp - 128) * density).toInt().coerceIn(120, 260)
            views.setImageViewBitmap(
                R.id.widget_chart,
                WidgetChartRenderer.render(snapshot, bitmapWidth, bitmapHeight, palette.chart),
            )
        } else if (presentation == WidgetPresentation.COVER_WIDE) {
            val density = context.resources.displayMetrics.density
            val bitmapWidth = ((widthDp - 112) * density).toInt().coerceIn(300, 720)
            val bitmapHeight = ((heightDp - 43) * density).toInt().coerceIn(140, 320)
            views.setImageViewBitmap(
                R.id.widget_chart,
                WidgetChartRenderer.render(
                    snapshot,
                    bitmapWidth,
                    bitmapHeight,
                    palette.chart,
                    WidgetBolusPresentation.LARGE_COVER,
                ),
            )
        }

        val openDashboard = PendingIntent.getActivity(
            context,
            appWidgetId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(android.R.id.background, openDashboard)
        return views
    }
}

private fun applyWidgetSkin(
    views: RemoteViews,
    presentation: WidgetPresentation,
    palette: WidgetSkinPalette,
) {
    views.setInt(android.R.id.background, "setBackgroundResource", palette.backgroundDrawable)
    views.setInt(R.id.widget_brand, "setBackgroundResource", palette.brandBadgeDrawable)
    views.setInt(R.id.widget_bg_tile, "setBackgroundResource", palette.glucoseTileDrawable)
    views.setInt(R.id.widget_iob_tile, "setBackgroundResource", palette.iobTileDrawable)
    views.setInt(R.id.widget_status, "setBackgroundResource", palette.statusBadgeDrawable)

    views.setTextColor(R.id.widget_title, palette.primaryTextColor)
    views.setTextColor(R.id.widget_freshness, palette.secondaryTextColor)
    views.setTextColor(R.id.widget_bg_label, palette.glucoseLabelColor)
    views.setTextColor(R.id.widget_iob_label, palette.iobLabelColor)

    if (presentation == WidgetPresentation.EXPANDED) {
        views.setInt(R.id.widget_chart_panel, "setBackgroundResource", palette.chartPanelDrawable)
        views.setTextColor(R.id.widget_chart_title, palette.chartTitleColor)
        views.setTextColor(R.id.widget_chart_range, palette.chartRangeColor)
    } else if (presentation == WidgetPresentation.COVER_WIDE) {
        views.setInt(R.id.widget_chart_panel, "setBackgroundResource", palette.chartPanelDrawable)
    }

}

private val WidgetDataStatus.labelResource: Int
    get() = when (this) {
        WidgetDataStatus.LIVE -> R.string.mobicontrol_widget_status_live
        WidgetDataStatus.STALE -> R.string.mobicontrol_widget_status_stale
        WidgetDataStatus.PARTIAL -> R.string.mobicontrol_widget_status_partial
        WidgetDataStatus.NO_DATA -> R.string.mobicontrol_widget_status_no_data
    }

private fun WidgetDataStatus.textColor(palette: WidgetSkinPalette): Int = when (this) {
    WidgetDataStatus.LIVE -> palette.liveStatusColor
    WidgetDataStatus.STALE -> palette.staleStatusColor
    WidgetDataStatus.PARTIAL -> palette.partialStatusColor
    WidgetDataStatus.NO_DATA -> palette.noDataStatusColor
}

internal val WidgetControlIqMode.iconResource: Int
    get() = when (this) {
        WidgetControlIqMode.STANDARD -> R.drawable.ic_widget_mode_standard
        WidgetControlIqMode.EXERCISE -> R.drawable.ic_widget_mode_exercise
        WidgetControlIqMode.SLEEP -> R.drawable.ic_widget_mode_sleep
    }

internal val WidgetControlIqMode.labelResource: Int
    get() = when (this) {
        WidgetControlIqMode.STANDARD -> R.string.mobicontrol_widget_mode_standard
        WidgetControlIqMode.EXERCISE -> R.string.mobicontrol_widget_mode_exercise
        WidgetControlIqMode.SLEEP -> R.string.mobicontrol_widget_mode_sleep
    }

internal val WidgetControlIqMode.color: Int
    get() = when (this) {
        WidgetControlIqMode.STANDARD -> android.graphics.Color.rgb(25, 118, 210)
        WidgetControlIqMode.EXERCISE -> android.graphics.Color.rgb(255, 152, 0)
        WidgetControlIqMode.SLEEP -> android.graphics.Color.rgb(63, 81, 181)
    }
