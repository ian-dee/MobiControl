package com.jwoglom.controlx2

import com.jwoglom.controlx2.presentation.components.historySyncPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncPresentationTest {
    @Test
    fun zeroRecordsCommunicatesBackgroundStartupInsteadOfLookingStuck() {
        val presentation = historySyncPresentation(0, 5_000)

        assertEquals("Starting…", presentation.status)
        assertEquals("Requesting recent pump history in the background", presentation.detail)
        assertFalse(presentation.isSynced)
    }

    @Test
    fun activeAndCompletedHistorySyncRemainQuantified() {
        val active = historySyncPresentation(1_250, 5_000)
        assertEquals("1250 / 5000", active.status)
        assertEquals("25% complete (3750 logs remaining)", active.detail)
        assertFalse(active.isSynced)

        val complete = historySyncPresentation(5_000, 5_000)
        assertEquals("Synced", complete.status)
        assertNull(complete.detail)
        assertTrue(complete.isSynced)
    }

    @Test
    fun allPumpAvailableRecordsCompleteAWindowSmallerThanFiveThousand() {
        val complete = historySyncPresentation(2_001, 2_001)

        assertEquals("Synced", complete.status)
        assertTrue(complete.isSynced)
    }
}
