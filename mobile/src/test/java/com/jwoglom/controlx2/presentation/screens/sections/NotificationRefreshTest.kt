package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import java.math.BigInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRefreshTest {
    private val attempt = NotificationRefreshAttempt(id = 1L, acceptEventsAfterSequence = 20L)

    @Test
    fun `stale reminder response cannot complete a refresh`() {
        assertFalse(isFreshReminderStatus(attempt, 20L, ReminderStatusResponse(BigInteger.ZERO)))
    }

    @Test
    fun `another notification response cannot complete a refresh`() {
        assertFalse(isFreshReminderStatus(attempt, 21L, AlertStatusResponse(BigInteger.ZERO)))
    }

    @Test
    fun `fresh reminder response completes a refresh`() {
        assertTrue(isFreshReminderStatus(attempt, 21L, ReminderStatusResponse(BigInteger.ZERO)))
    }

    @Test
    fun `native pull indicator hides while skinned loading state is visible`() {
        assertFalse(showNotificationPullRefreshIndicator(refreshing = true))
    }

    @Test
    fun `native pull indicator remains available as pull gesture feedback`() {
        assertTrue(showNotificationPullRefreshIndicator(refreshing = false))
    }
}
