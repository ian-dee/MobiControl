package com.jwoglom.controlx2.widget

import android.graphics.Color
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.theme.AppSkin

/** Host-safe widget equivalents of the app skins; no value here controls pump behavior. */
internal data class WidgetSkinPalette(
    val skin: AppSkin,
    val backgroundDrawable: Int,
    val brandBadgeDrawable: Int,
    val glucoseTileDrawable: Int,
    val iobTileDrawable: Int,
    val statusBadgeDrawable: Int,
    val chartPanelDrawable: Int,
    val surfaceColor: Int,
    val chartSurfaceColor: Int,
    val frameColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val glucoseLabelColor: Int,
    val iobLabelColor: Int,
    val chartTitleColor: Int,
    val chartRangeColor: Int,
    val glucoseTextColor: Int,
    val iobTextColor: Int,
    val staleTextColor: Int,
    val neutralTextColor: Int,
    val liveStatusColor: Int,
    val staleStatusColor: Int,
    val partialStatusColor: Int,
    val noDataStatusColor: Int,
    val outerRadiusDp: Int,
    val innerRadiusDp: Int,
    val chart: WidgetChartPalette,
)

internal data class WidgetChartPalette(
    val labelColor: Int,
    val gridColor: Int,
    val baselineColor: Int,
)

internal fun widgetSkinPalette(
    skin: AppSkin,
    presentation: WidgetPresentation? = null,
): WidgetSkinPalette {
    val palette = baseWidgetSkinPalette(skin)
    if (presentation != WidgetPresentation.COVER_COMPACT &&
        presentation != WidgetPresentation.COVER_WIDE) {
        if (skin != AppSkin.FIELD_KIT) return palette
        return when (presentation) {
            WidgetPresentation.COMPACT -> palette.copy(
                backgroundDrawable = R.drawable.mobicontrol_widget_background_field_kit_light,
                glucoseTileDrawable = R.drawable.mobicontrol_widget_glucose_tile_field_kit_light,
            )
            WidgetPresentation.EXPANDED -> palette.copy(
                glucoseTileDrawable = R.drawable.mobicontrol_widget_glucose_tile_field_kit_light,
                chartPanelDrawable = R.drawable.mobicontrol_widget_chart_panel_field_kit_light,
            )
            else -> palette
        }
    }

    // Samsung's cover host enforces its own outline even on self-clipped widgets.
    // Keep the cover frame within that mask; home/Glance geometry stays skin-specific.
    val coverPalette = palette.copy(
        backgroundDrawable = when (skin) {
            AppSkin.CURRENT -> R.drawable.mobicontrol_cover_widget_background
            AppSkin.INSTRUMENT -> R.drawable.mobicontrol_cover_widget_background_instrument
            AppSkin.FIELD_KIT -> R.drawable.mobicontrol_cover_widget_background_field_kit
        },
        iobTileDrawable = when (skin) {
            AppSkin.CURRENT -> R.drawable.mobicontrol_cover_widget_iob_tile
            AppSkin.INSTRUMENT -> R.drawable.mobicontrol_cover_widget_iob_tile_instrument
            AppSkin.FIELD_KIT -> R.drawable.mobicontrol_cover_widget_iob_tile_field_kit
        },
        chartPanelDrawable = when (skin) {
            AppSkin.CURRENT -> R.drawable.mobicontrol_cover_widget_chart_panel
            AppSkin.INSTRUMENT -> R.drawable.mobicontrol_cover_widget_chart_panel_instrument
            AppSkin.FIELD_KIT -> R.drawable.mobicontrol_cover_widget_chart_panel_field_kit
        },
        outerRadiusDp = 24,
    )
    if (skin == AppSkin.FIELD_KIT && presentation == WidgetPresentation.COVER_WIDE) {
        return coverPalette.copy(
            glucoseTileDrawable = R.drawable.mobicontrol_widget_glucose_tile_field_kit_light,
            chartPanelDrawable = R.drawable.mobicontrol_cover_widget_chart_panel_field_kit_light,
        )
    }
    return coverPalette
}

private fun baseWidgetSkinPalette(skin: AppSkin): WidgetSkinPalette = when (skin) {
    AppSkin.CURRENT -> WidgetSkinPalette(
        skin = skin,
        backgroundDrawable = R.drawable.mobicontrol_widget_background,
        brandBadgeDrawable = R.drawable.mobicontrol_widget_brand_badge,
        glucoseTileDrawable = R.drawable.mobicontrol_widget_glucose_tile,
        iobTileDrawable = R.drawable.mobicontrol_widget_iob_tile,
        statusBadgeDrawable = R.drawable.mobicontrol_widget_status_badge,
        chartPanelDrawable = R.drawable.mobicontrol_widget_chart_panel,
        surfaceColor = Color.rgb(229, 236, 245),
        chartSurfaceColor = Color.WHITE,
        frameColor = Color.TRANSPARENT,
        primaryTextColor = Color.rgb(21, 36, 60),
        secondaryTextColor = Color.rgb(92, 107, 128),
        glucoseLabelColor = Color.rgb(76, 107, 82),
        iobLabelColor = Color.rgb(72, 100, 126),
        chartTitleColor = Color.rgb(53, 66, 80),
        chartRangeColor = Color.rgb(122, 133, 143),
        glucoseTextColor = Color.rgb(46, 125, 50),
        iobTextColor = Color.rgb(25, 118, 210),
        staleTextColor = Color.rgb(117, 117, 117),
        neutralTextColor = Color.rgb(76, 107, 82),
        liveStatusColor = Color.rgb(35, 122, 53),
        staleStatusColor = Color.rgb(173, 94, 0),
        partialStatusColor = Color.rgb(145, 91, 0),
        noDataStatusColor = Color.rgb(102, 113, 124),
        outerRadiusDp = 22,
        innerRadiusDp = 14,
        chart = WidgetChartPalette(
            labelColor = Color.rgb(105, 116, 126),
            gridColor = Color.rgb(224, 231, 237),
            baselineColor = Color.rgb(190, 202, 212),
        ),
    )

    AppSkin.INSTRUMENT -> WidgetSkinPalette(
        skin = skin,
        backgroundDrawable = R.drawable.mobicontrol_widget_background_instrument,
        brandBadgeDrawable = R.drawable.mobicontrol_widget_brand_badge_instrument,
        glucoseTileDrawable = R.drawable.mobicontrol_widget_metric_tile_instrument,
        iobTileDrawable = R.drawable.mobicontrol_widget_metric_tile_instrument,
        statusBadgeDrawable = R.drawable.mobicontrol_widget_status_badge_instrument,
        chartPanelDrawable = R.drawable.mobicontrol_widget_chart_panel_instrument,
        surfaceColor = Color.rgb(231, 229, 221),
        chartSurfaceColor = Color.rgb(250, 249, 243),
        frameColor = Color.TRANSPARENT,
        primaryTextColor = Color.rgb(36, 39, 33),
        secondaryTextColor = Color.rgb(96, 100, 90),
        glucoseLabelColor = Color.rgb(74, 91, 65),
        iobLabelColor = Color.rgb(69, 84, 111),
        chartTitleColor = Color.rgb(36, 39, 33),
        chartRangeColor = Color.rgb(96, 100, 90),
        glucoseTextColor = Color.rgb(46, 125, 50),
        iobTextColor = Color.rgb(43, 89, 195),
        staleTextColor = Color.rgb(116, 123, 107),
        neutralTextColor = Color.rgb(96, 100, 90),
        liveStatusColor = Color.rgb(46, 125, 50),
        staleStatusColor = Color.rgb(169, 64, 28),
        partialStatusColor = Color.rgb(131, 103, 23),
        noDataStatusColor = Color.rgb(96, 100, 90),
        outerRadiusDp = 8,
        innerRadiusDp = 4,
        chart = WidgetChartPalette(
            labelColor = Color.rgb(96, 100, 90),
            gridColor = Color.rgb(200, 201, 191),
            baselineColor = Color.rgb(116, 123, 107),
        ),
    )

    AppSkin.FIELD_KIT -> WidgetSkinPalette(
        skin = skin,
        backgroundDrawable = R.drawable.mobicontrol_widget_background_field_kit,
        brandBadgeDrawable = R.drawable.mobicontrol_widget_brand_badge_field_kit,
        glucoseTileDrawable = R.drawable.mobicontrol_widget_glucose_tile_field_kit,
        iobTileDrawable = R.drawable.mobicontrol_widget_iob_tile_field_kit,
        statusBadgeDrawable = R.drawable.mobicontrol_widget_status_badge_field_kit,
        chartPanelDrawable = R.drawable.mobicontrol_widget_chart_panel_field_kit,
        surfaceColor = Color.rgb(236, 235, 225),
        chartSurfaceColor = Color.rgb(232, 235, 223),
        frameColor = Color.rgb(48, 55, 53),
        primaryTextColor = Color.rgb(40, 50, 44),
        secondaryTextColor = Color.rgb(91, 99, 88),
        glucoseLabelColor = Color.rgb(86, 104, 77),
        iobLabelColor = Color.rgb(60, 86, 97),
        chartTitleColor = Color.rgb(40, 50, 44),
        chartRangeColor = Color.rgb(91, 99, 88),
        glucoseTextColor = Color.rgb(34, 59, 40),
        iobTextColor = Color.rgb(60, 86, 97),
        staleTextColor = Color.rgb(133, 141, 128),
        neutralTextColor = Color.rgb(86, 104, 77),
        liveStatusColor = Color.rgb(211, 222, 197),
        staleStatusColor = Color.rgb(255, 213, 154),
        partialStatusColor = Color.rgb(232, 220, 164),
        noDataStatusColor = Color.rgb(191, 199, 186),
        outerRadiusDp = 14,
        innerRadiusDp = 8,
        chart = WidgetChartPalette(
            labelColor = Color.rgb(79, 87, 81),
            gridColor = Color.rgb(168, 178, 155),
            baselineColor = Color.rgb(59, 72, 65),
        ),
    )
}
