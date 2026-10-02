package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.controlx2.db.historylog.itemLruCache
import com.jwoglom.controlx2.presentation.PumpMessageEvent
import com.jwoglom.controlx2.presentation.PumpStateEvent
import com.jwoglom.controlx2.shared.enums.BasalStatus
import com.jwoglom.pumpx2.pump.messages.response.control.FillCannulaResponse
import com.jwoglom.pumpx2.pump.messages.response.control.SetSiteChangeReminderResponse
import com.jwoglom.pumpx2.pump.messages.response.controlStream.FillCannulaStateStreamResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.LoadStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.RemindersResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CannulaFilledHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import java.time.Instant
import java.time.ZoneId

class CannulaFillWorkflowTest {
    @Before
    fun clearHistoryLogCache() {
        itemLruCache.evictAll()
    }

    @Test
    fun readinessRequiresSuspensionFreshStateAndCompatibleLoadState() {
        val compatible = loadEvent(11, LoadStatusResponse.LoadState.PRIME_NUDGE)

        assertEquals(
            CannulaFillReadinessKind.NEEDS_SUSPEND,
            evaluateCannulaFillReadiness(basalEvent(11, BasalStatus.PUMP_RESUMED), compatible, 10).kind,
        )
        assertEquals(
            CannulaFillReadinessKind.WAITING_FOR_FRESH_STATE,
            evaluateCannulaFillReadiness(basalEvent(11, BasalStatus.PUMP_SUSPENDED), compatible, 11).kind,
        )
        assertEquals(
            CannulaFillReadinessKind.BLOCKED,
            evaluateCannulaFillReadiness(
                basalEvent(12, BasalStatus.PUMP_SUSPENDED),
                loadEvent(12, LoadStatusResponse.LoadState.PRIME_TUBING),
                11,
            ).kind,
        )
        assertEquals(
            CannulaFillReadinessKind.READY,
            evaluateCannulaFillReadiness(basalEvent(11, BasalStatus.PUMP_SUSPENDED), compatible, 10).kind,
        )
        assertEquals(
            CannulaFillReadinessKind.NEEDS_SUSPEND,
            evaluateCannulaFillReadiness(
                basalEvent(11, BasalStatus.BASALIQ_SUSPENDED),
                compatible,
                10,
            ).kind,
        )
        assertEquals(
            CannulaFillReadinessKind.BLOCKED,
            evaluateCannulaFillReadiness(
                basalEvent(11, BasalStatus.PUMP_SUSPENDED),
                loadEvent(11, LoadStatusResponse.LoadState.PRIME_NUDGE, active = true),
                10,
            ).kind,
        )
    }

    @Test
    fun completionRequiresAcceptedResponseFromTheCurrentAttempt() {
        val ready = CannulaFillAttemptState.begin(1, 100).copy(phase = CannulaFillPhase.READY)
        val requesting = markCannulaFillRequested(ready, 100)

        val completionBeforeResponse = applyFillCannulaCompletion(
            requesting,
            fillStateEvent(101, 1_000),
        )
        assertEquals(CannulaFillPhase.REQUESTING_FILL, completionBeforeResponse.phase)

        val accepted = applyFillCannulaResponse(requesting, fillResponseEvent(101, 0))
        assertEquals(CannulaFillPhase.WAITING_FOR_COMPLETION, accepted.phase)

        val staleCompletion = applyFillCannulaCompletion(accepted, fillStateEvent(100, 1_000))
        assertEquals(CannulaFillPhase.WAITING_FOR_COMPLETION, staleCompletion.phase)
        assertNull(staleCompletion.completedAtEpochMillis)

        val completed = applyFillCannulaCompletion(accepted, fillStateEvent(102, 1_234))
        assertEquals(CannulaFillPhase.FILL_CONFIRMED, completed.phase)
        assertEquals(1_234L, completed.completedAtEpochMillis)
    }

    @Test
    fun secondAttemptDoesNotReuseFirstAttemptsCompletion() {
        val first = CannulaFillAttemptState.begin(1, 50)
            .copy(phase = CannulaFillPhase.WAITING_FOR_COMPLETION, acceptEventsAfterSequence = 51)
        val firstCompleted = applyFillCannulaCompletion(first, fillStateEvent(52, 1_000))
        assertEquals(CannulaFillPhase.FILL_CONFIRMED, firstCompleted.phase)

        val second = CannulaFillAttemptState.begin(2, 52)
        val secondRequest = markCannulaFillRequested(
            second.copy(phase = CannulaFillPhase.READY),
            52,
        )
        val secondAccepted = applyFillCannulaResponse(secondRequest, fillResponseEvent(53, 0))
        val staleFirstCompletion = applyFillCannulaCompletion(secondAccepted, fillStateEvent(52, 2_000))

        assertEquals(CannulaFillPhase.WAITING_FOR_COMPLETION, staleFirstCompletion.phase)
        assertNull(staleFirstCompletion.completedAtEpochMillis)
    }

    @Test
    fun currentCompletionIsAcceptedEvenIfItsStreamEventArrivesBeforeTheResponse() {
        val requesting = markCannulaFillRequested(
            CannulaFillAttemptState.begin(1, 100).copy(phase = CannulaFillPhase.READY),
            100,
        )
        val completionEvent = fillStateEvent(102, 1_234)

        assertEquals(
            CannulaFillPhase.REQUESTING_FILL,
            applyFillCannulaCompletion(requesting, completionEvent).phase,
        )

        val accepted = applyFillCannulaResponse(requesting, fillResponseEvent(101, 0))
        val completed = applyFillCannulaCompletion(accepted, completionEvent)

        assertEquals(CannulaFillPhase.FILL_CONFIRMED, completed.phase)
        assertEquals(1_234L, completed.completedAtEpochMillis)
    }

    @Test
    fun nonzeroFillResponseFailsWithoutRecordingCompletion() {
        val requesting = markCannulaFillRequested(
            CannulaFillAttemptState.begin(1, 10).copy(phase = CannulaFillPhase.READY),
            10,
        )

        val failed = applyFillCannulaResponse(requesting, fillResponseEvent(11, 4))

        assertEquals(CannulaFillPhase.FILL_FAILED, failed.phase)
        assertTrue(failed.error!!.contains("status 4"))
        assertNull(failed.completedAtEpochMillis)
    }

    @Test
    fun reminderPlanPreservesConfiguredDaysAndTimeAndEnablesAllFields() {
        val planning = planSiteChangeReminder(remindersResponse(days = 3, timeMinutes = 9 * 60, enabled = 0))
        val plan = planning.plan!!
        val request = createSiteChangeReminderRequest(plan)

        assertEquals(3, plan.dayCount)
        assertEquals(540L, plan.timeOfDayMinutes)
        assertEquals(0x07, plan.changeBitmask)
        assertTrue(request.isEnable)
        assertEquals(3, request.dayCount)
        assertEquals(540L, request.timeOfDayMinutes)
        assertEquals(0x07, request.bitmask)
        assertArrayEquals(
            byteArrayOf(0x01, 0x03, 0x1c, 0x02, 0x00, 0x00, 0x07),
            request.cargo,
        )
    }

    @Test
    fun reminderMustBeAcceptedAndMatchReadbackBeforeWorkflowCompletes() {
        val plan = SiteChangeReminderPlan(3, 540)
        val setting = markSettingSiteChangeReminder(
            CannulaFillAttemptState.begin(1, 10).copy(
                phase = CannulaFillPhase.FILL_CONFIRMED,
                completedAtEpochMillis = 1_000,
            ),
            plan,
            20,
        )
        val accepted = applySiteChangeReminderResponse(
            setting,
            PumpMessageEvent(21, Instant.EPOCH, SetSiteChangeReminderResponse(0)),
        )
        assertEquals(CannulaFillPhase.REMINDER_RESPONSE_ACCEPTED, accepted.phase)

        val verifying = markVerifyingSiteChangeReminder(accepted, 21)
        val complete = applySiteChangeReminderReadback(
            verifying,
            PumpMessageEvent(22, Instant.EPOCH, remindersResponse(3, 540, 1)),
        )
        assertEquals(CannulaFillPhase.COMPLETE, complete.phase)

        val mismatch = applySiteChangeReminderReadback(
            verifying,
            PumpMessageEvent(23, Instant.EPOCH, remindersResponse(2, 540, 1)),
        )
        assertEquals(CannulaFillPhase.COMPLETE_REMINDER_FAILED, mismatch.phase)
        assertTrue(mismatch.error!!.contains("did not match"))
    }

    @Test
    fun realMobiSiteReminderBitmaskAndFrequencyFieldVerifySuccessfully() {
        val plan = SiteChangeReminderPlan(3, 435)
        val setting = markSettingSiteChangeReminder(
            CannulaFillAttemptState.begin(1, 10).copy(
                phase = CannulaFillPhase.FILL_CONFIRMED,
                completedAtEpochMillis = 1_000,
            ),
            plan,
            20,
        )
        val accepted = applySiteChangeReminderResponse(
            setting,
            PumpMessageEvent(21, Instant.EPOCH, SetSiteChangeReminderResponse(0)),
        )
        val verifying = markVerifyingSiteChangeReminder(accepted, 21)

        val complete = applySiteChangeReminderReadback(
            verifying,
            PumpMessageEvent(22, Instant.EPOCH, remindersResponse(3, 435, 1)),
        )

        assertEquals(CannulaFillPhase.COMPLETE, complete.phase)
        assertNull(complete.error)
    }

    @Test
    fun existingMobiReminderPlanReadsTimeFromFrequencyField() {
        val planning = planSiteChangeReminder(remindersResponse(3, 435, 1))

        assertEquals(SiteChangeReminderPlan(3, 435), planning.plan)
        assertNull(planning.error)
    }

    @Test
    fun invalidReminderConfigurationIsNotTurnedIntoACommand() {
        val zeroDays = planSiteChangeReminder(remindersResponse(0, 540, 1))
        val invalidTime = planSiteChangeReminder(remindersResponse(3, 1500, 1))

        assertNull(zeroDays.plan)
        assertNull(invalidTime.plan)
        assertFalse(zeroDays.error.isNullOrBlank())
        assertFalse(invalidTime.error.isNullOrBlank())
    }

    @Test
    fun nextSiteChangeUsesPumpConfirmedFillAndCurrentPumpReminder() {
        val zone = ZoneId.of("America/Los_Angeles")
        val lastFill = Instant.parse("2026-08-16T21:30:00Z").toEpochMilli()

        val nextChange = nextSiteChangeReminderEpochMillis(
            lastFill,
            remindersResponse(days = 3, timeMinutes = 9 * 60, enabled = 1),
            zone,
        )

        assertEquals(Instant.parse("2026-08-19T16:00:00Z").toEpochMilli(), nextChange)
    }

    @Test
    fun nextSiteChangeIsNotInventedForDisabledOrUnavailableReminder() {
        val lastFill = Instant.parse("2026-08-16T21:30:00Z").toEpochMilli()

        assertNull(nextSiteChangeReminderEpochMillis(lastFill, null))
        assertNull(nextSiteChangeReminderEpochMillis(lastFill, remindersResponse(3, 540, 0)))
        assertNull(nextSiteChangeReminderEpochMillis(lastFill, remindersResponse(0, 540, 1)))
    }

    @Test
    fun latestCompletedFillIgnoresNewerAbortedAndFailedAttempts() {
        val olderCompleted = cannulaHistoryItem(10, 1_000, 0.7f, 3)
        val newestCompleted = cannulaHistoryItem(15, 2_000, 0.7f, 3)
        val userAborted = cannulaHistoryItem(20, 3_000, 0.0f, 0)
        val alarmTerminated = cannulaHistoryItem(21, 4_000, 0.0f, 1)
        val malfunctionTerminated = cannulaHistoryItem(22, 5_000, 0.0f, 2)

        val selected = latestCompletedCannulaFill(
            listOf(userAborted, olderCompleted, malfunctionTerminated, newestCompleted, alarmTerminated),
        )

        assertEquals(15L, selected?.seqId)
        assertEquals(2_000L, selected?.pumpTimeSec)
    }

    @Test
    fun noCompletedFillDoesNotProduceATimestamp() {
        assertNull(
            latestCompletedCannulaFill(
                listOf(
                    cannulaHistoryItem(20, 3_000, 0.0f, 0),
                    cannulaHistoryItem(21, 4_000, 0.0f, 1),
                    cannulaHistoryItem(22, 5_000, 0.0f, 2),
                ),
            ),
        )
    }

    @Test
    fun siteAgeReportsWholeElapsedDaysAndHours() {
        assertEquals("0 days, 0 hours", formatCannulaFillAge(3_599))
        assertEquals("0 days, 1 hour", formatCannulaFillAge(3_600))
        assertEquals("1 day, 0 hours", formatCannulaFillAge(86_400))
        assertEquals("2 days, 5 hours", formatCannulaFillAge((2 * 24 + 5) * 3_600L + 3_599))
        assertNull(formatCannulaFillAge(-1))
    }

    private fun loadEvent(
        sequence: Long,
        state: LoadStatusResponse.LoadState,
        active: Boolean = false,
    ) = PumpMessageEvent(
        sequence,
        Instant.EPOCH,
        LoadStatusResponse(active, state, 1),
    )

    private fun basalEvent(sequence: Long, status: BasalStatus) = PumpStateEvent(
        sequence,
        Instant.EPOCH,
        status,
    )

    private fun fillResponseEvent(sequence: Long, status: Int) = PumpMessageEvent(
        sequence,
        Instant.EPOCH,
        FillCannulaResponse(status),
    )

    private fun fillStateEvent(sequence: Long, receivedAtEpochMillis: Long) = PumpMessageEvent(
        sequence,
        Instant.ofEpochMilli(receivedAtEpochMillis),
        FillCannulaStateStreamResponse(2),
    )

    private fun remindersResponse(days: Int, timeMinutes: Int, enabled: Int): RemindersResponse {
        val empty = RemindersResponse.Reminder(0, 0, 0, 0, 0, 0)
        val site = RemindersResponse.Reminder(timeMinutes.toLong(), 0, 0, 0, enabled, 3)
        return RemindersResponse(
            empty,
            empty,
            site,
            empty,
            empty,
            empty,
            empty,
            empty,
            empty,
            70,
            180,
            days,
            4,
        )
    }

    private fun cannulaHistoryItem(
        sequence: Long,
        pumpTimeSec: Long,
        primeSize: Float,
        completionStatus: Long,
    ) = HistoryLogItem(
        CannulaFilledHistoryLog(pumpTimeSec, sequence, primeSize, completionStatus),
    )
}
