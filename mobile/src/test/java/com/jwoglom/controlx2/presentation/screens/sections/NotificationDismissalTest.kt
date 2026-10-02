package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.pumpx2.pump.messages.request.control.DismissNotificationRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CGMAlertStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.AlarmStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDismissalTest {
    @Test
    fun repeatedCallbacksOnlyBeginOneDismissal() {
        val gate = NotificationDismissalGate()
        val target = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            1L,
        )

        assertTrue(gate.begin(target))
        assertFalse(gate.begin(target))
        assertFalse(gate.begin(target))
        assertEquals(target, gate.active())
    }

    @Test
    fun responseCompletionAllowsTheNextDismissal() {
        val gate = NotificationDismissalGate()
        val first = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALERT,
            51L,
        )
        val second = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            1L,
        )

        assertTrue(gate.begin(first))
        assertFalse(gate.begin(second))
        assertTrue(gate.finish(first))
        assertTrue(gate.begin(second))
    }

    @Test
    fun unrelatedCompletionCannotReleaseTheActiveDismissal() {
        val gate = NotificationDismissalGate()
        val active = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALERT,
            51L,
        )
        val unrelated = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            1L,
        )

        assertTrue(gate.begin(active))
        assertFalse(gate.finish(unrelated))
        assertEquals(active, gate.active())
    }

    @Test
    fun abandonedAttemptExpiresSoAnotherScreenCanDismiss() {
        var nowMillis = 100L
        val gate = NotificationDismissalGate(
            elapsedMillis = { nowMillis },
            staleAfterMillis = 1_000L,
        )
        val abandoned = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALERT,
            51L,
        )
        val replacement = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.CGM_ALERT,
            1L,
        )

        assertTrue(gate.begin(abandoned))
        nowMillis += 999L
        assertFalse(gate.begin(replacement))
        nowMillis += 1L
        assertTrue(gate.begin(replacement))
        assertEquals(replacement, gate.active())
    }

    @Test
    fun cgmAlertMapsToItsProtocolTypeAndId() {
        val notification = CGMAlertStatusResponse.CGMAlert.FIXED_LOW_CGM_ALERT

        assertEquals(
            NotificationDismissalTarget(
                DismissNotificationRequest.NotificationType.CGM_ALERT,
                notification.id().toLong(),
            ),
            notificationDismissalTarget(notification),
        )
    }

    @Test
    fun pumpAlertMapsToItsProtocolTypeAndBitmask() {
        val notification = AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT

        assertEquals(
            NotificationDismissalTarget(
                DismissNotificationRequest.NotificationType.ALERT,
                notification.bitmask().toLong(),
            ),
            notificationDismissalTarget(notification),
        )
    }

    @Test
    fun siteReminderMapsToItsExactProtocolTypeAndId() {
        val notification = ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER

        assertEquals(
            NotificationDismissalTarget(
                DismissNotificationRequest.NotificationType.REMINDER,
                2L,
            ),
            notificationDismissalTarget(notification),
        )
    }

    @Test
    fun reminderDismissalVerificationUsesFreshReminderStatus() {
        val notification = ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER
        val target = notificationDismissalTarget(notification)!!

        assertEquals(
            true,
            notificationStatusContainsTarget(
                ReminderStatusResponse(BigInteger.ZERO.setBit(notification.id())),
                target,
            ),
        )
        assertEquals(
            false,
            notificationStatusContainsTarget(ReminderStatusResponse(BigInteger.ZERO), target),
        )
        assertEquals(
            null,
            notificationStatusContainsTarget(AlertStatusResponse(BigInteger.ZERO), target),
        )
    }

    @Test
    fun verificationWaitsForTheMatchingFreshStatusType() {
        val target = NotificationDismissalTarget(
            DismissNotificationRequest.NotificationType.ALERT,
            AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask().toLong(),
        )

        assertEquals(
            null,
            notificationStatusContainsTarget(AlarmStatusResponse(BigInteger.ZERO), target),
        )
        assertEquals(
            true,
            notificationStatusContainsTarget(
                AlertStatusResponse(
                    BigInteger.ZERO.setBit(
                        AlertStatusResponse.AlertResponseType.LOW_INSULIN_ALERT2.bitmask(),
                    ),
                ),
                target,
            ),
        )
        assertEquals(
            false,
            notificationStatusContainsTarget(AlertStatusResponse(BigInteger.ZERO), target),
        )
    }
}
