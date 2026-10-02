package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRefreshPresentationTest {
    @Test
    fun `cover dashboard replaces overlay indicator with bounded status`() {
        assertFalse(showDashboardPullRefreshIndicator(compactCover = true))
        assertTrue(
            showCompactDashboardRefreshStatus(
                compactCover = true,
                refreshing = true,
            ),
        )
    }

    @Test
    fun `cover dashboard restores timestamp when refresh completes`() {
        assertFalse(
            showCompactDashboardRefreshStatus(
                compactCover = true,
                refreshing = false,
            ),
        )
    }

    @Test
    fun `full dashboard retains existing pull refresh indicator`() {
        assertTrue(showDashboardPullRefreshIndicator(compactCover = false))
        assertFalse(
            showCompactDashboardRefreshStatus(
                compactCover = false,
                refreshing = true,
            ),
        )
    }
}
