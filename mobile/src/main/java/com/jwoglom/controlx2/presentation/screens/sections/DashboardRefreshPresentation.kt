package com.jwoglom.controlx2.presentation.screens.sections

internal fun showDashboardPullRefreshIndicator(compactCover: Boolean): Boolean = !compactCover

internal fun showCompactDashboardRefreshStatus(
    compactCover: Boolean,
    refreshing: Boolean,
): Boolean = compactCover && refreshing
