package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ReminderStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpNotificationsTest {
    @Test
    fun `canonical notification reads contain exactly one reminder request and finish with it`() {
        val requests = pumpNotificationStatusRequests()

        assertEquals(1, requests.count { it is ReminderStatusRequest })
        assertTrue(requests.last() is ReminderStatusRequest)
    }

    @Test
    fun `site reminder uses user readable official terminology`() {
        assertEquals(
            PumpReminderPresentation(
                headline = "Site Reminder",
                supportingText = "It's time to change your infusion set.",
            ),
            pumpReminderPresentation(ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER),
        )
    }

    @Test
    fun `unknown reminder preserves its protocol id in supporting text`() {
        val reminder = ReminderStatusResponse.ReminderType.DEFAULT_REMINDER_17

        assertEquals("Pump Reminder", pumpReminderPresentation(reminder).headline)
        assertTrue(pumpReminderPresentation(reminder).supportingText.contains("17"))
    }
}
