package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.db.historylog.HistoryLogItem
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BasalRateChangeHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertAckHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.AlertActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmBleCalibrationEvtG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationG7HistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateActivatedHistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.TempRateCompletedHistoryLog

class PumpHistoryTimelineTest {
    @Test
    fun `each page extends the history range by exactly 24 hours`() {
        val anchor = 1_000_000L

        assertEquals(anchor - PUMP_HISTORY_PAGE_SECONDS, pumpHistoryOldestInclusive(anchor, 1))
        assertEquals(anchor - 2 * PUMP_HISTORY_PAGE_SECONDS, pumpHistoryOldestInclusive(anchor, 2))
    }

    @Test
    fun `developer preview reveals older samples as the window expands`() {
        val anchor = 1_000_000L
        val firstPage = developerPreviewPumpHistoryEvents(
            anchor,
            pumpHistoryOldestInclusive(anchor, 1),
        )
        val secondPage = developerPreviewPumpHistoryEvents(
            anchor,
            pumpHistoryOldestInclusive(anchor, 2),
        )

        assertTrue(secondPage.size > firstPage.size)
        assertTrue(secondPage.containsAll(firstPage))
    }

    @Test
    fun `events are grouped into ordered calendar days`() {
        val anchor = 1_000_000L
        val events = developerPreviewPumpHistoryEvents(
            anchor,
            pumpHistoryOldestInclusive(anchor, 2),
        )

        val days = groupPumpHistoryEventsByDay(events)

        assertTrue(days.size >= 2)
        assertEquals(events, days.flatMap { it.events })
        assertTrue(days.zipWithNext().all { (newer, older) -> newer.date > older.date })
    }

    @Test
    fun `Control IQ bolus is labeled as an automatic bolus`() {
        val log = bolus(BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS)

        val event = historyLogToPumpHistoryEvent(log, pumpSid = 123, seqId = 10, pumpTimeSec = 500_000L)!!

        assertEquals(PumpHistoryEventKind.AUTOMATIC_BOLUS, event.kind)
        assertEquals("Control-IQ automatic bolus", event.title)
        assertTrue(event.detail.contains("1.20 U"))
        assertTrue(event.detail.contains("Correction"))
    }

    @Test
    fun `remote bolus remains a manual bolus event`() {
        val log = bolus(BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS)

        val event = historyLogToPumpHistoryEvent(log, pumpSid = 123, seqId = 11, pumpTimeSec = 500_001L)!!

        assertEquals(PumpHistoryEventKind.BOLUS, event.kind)
        assertEquals("Bolus delivered", event.title)
        assertTrue(event.detail.contains("Bluetooth Remote Bolus"))
    }

    @Test
    fun `pump-button bolus is labeled as a user initiated Quick Bolus`() {
        val log = bolus(BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS)

        val event = historyLogToPumpHistoryEvent(log, 123, 12, 500_002L)!!

        assertEquals(PumpHistoryEventKind.BOLUS, event.kind)
        assertEquals("Quick Bolus", event.title)
        assertTrue(event.detail.contains("1.20 U"))
        assertFalse(event.detail.contains("Control-IQ"))
    }

    @Test
    fun `alert acknowledgement is visible in pump history`() {
        val event = historyLogToPumpHistoryEvent(
            AlertAckHistoryLog(500_002L, 12L, 17L),
            pumpSid = 123,
            seqId = 12L,
            pumpTimeSec = 500_002L,
        )!!

        assertEquals(PumpHistoryEventKind.ALERT, event.kind)
        assertEquals("Pump alert acknowledged", event.title)
        assertEquals("Alert ID 17", event.detail)
    }

    @Test
    fun `G7 calibration value and accepted result collapse into one history event`() {
        val calibration = CgmCalibrationG7HistoryLog(500_010L, 20L, 172L)
        val accepted = CgmBleCalibrationEvtG7HistoryLog(500_011L, 21L, true)

        val events = historyLogItemsToPumpHistoryEvents(
            listOf(HistoryLogItem(calibration), HistoryLogItem(accepted)),
        )

        assertEquals(1, events.size)
        assertEquals("CGM calibration", events.single().title)
        assertEquals("172 mg/dL • Accepted", events.single().detail)
    }

    @Test
    fun `calibration pump alert uses readable title and support`() {
        val event = historyLogToPumpHistoryEvent(
            AlertActivatedHistoryLog(500_012L, 22L, 27L),
            pumpSid = 123,
            seqId = 22L,
            pumpTimeSec = 500_012L,
        )!!

        assertEquals("Calibration Incomplete", event.title)
        assertEquals("The CGM calibration was incomplete.", event.detail)
    }

    @Test
    fun `bolus lifecycle records collapse to one terminal delivery`() {
        val started = bolus(
            source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            sequenceNum = 100,
            pumpTimeSec = 500_000L,
            bolusId = 7_760,
            statusId = 1,
            deliveredMilliunits = 0,
        )
        val completed = bolus(
            source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            sequenceNum = 101,
            pumpTimeSec = 500_155L,
            bolusId = 7_760,
            statusId = 0,
            deliveredMilliunits = 5_370,
        )

        val events = historyLogItemsToPumpHistoryEvents(
            listOf(HistoryLogItem(started), HistoryLogItem(completed)),
        )

        assertEquals(1, events.size)
        assertEquals("Bolus delivered", events.single().title)
        assertTrue(events.single().detail.contains("5.37 U"))
        assertFalse(events.single().detail.contains("0.00 U"))
        assertEquals(500_155L, events.single().pumpTimeSec)
    }

    @Test
    fun `incomplete bolus lifecycle record is not presented as a delivery`() {
        val started = bolus(
            source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
            sequenceNum = 102,
            pumpTimeSec = 500_200L,
            bolusId = 7_761,
            statusId = 1,
            deliveredMilliunits = 0,
        )

        assertTrue(
            historyLogItemsToPumpHistoryEvents(listOf(HistoryLogItem(started))).isEmpty(),
        )
    }

    @Test
    fun `temporary basal duration and completion time are converted from milliseconds`() {
        val activated = TempRateActivatedHistoryLog().apply {
            parse(hexToBytes("0210C1A3EE1F09A200000000CA4200BA5B4902000F0000000000"))
        }
        val completedEarly = TempRateCompletedHistoryLog().apply {
            parse(hexToBytes("0F1028034F20F9F1000008006100A0BB0D000000000000000000"))
        }
        val completedOnSchedule = TempRateCompletedHistoryLog().apply {
            parse(hexToBytes("0F1117350B23529622002D002801F10E000048008A0048008A00"))
        }

        val activatedEvent = historyLogToPumpHistoryEvent(activated, 123, 103, 500_300L)!!
        val earlyEvent = historyLogToPumpHistoryEvent(completedEarly, 123, 104, 500_400L)!!
        val scheduledEvent = historyLogToPumpHistoryEvent(completedOnSchedule, 123, 105, 500_500L)!!

        assertEquals("101% for 15 min", activatedEvent.detail)
        assertEquals("Ended 15 min early", earlyEvent.detail)
        assertEquals("Scheduled duration completed", scheduledEvent.detail)
    }

    @Test
    fun `invalid Mobi legacy basal parse is rejected and telemetry yields real transitions`() {
        val malformed = BasalRateChangeHistoryLog().apply {
            parse(hexToBytes("03113CC90A23E8952200DD41C47D9FE401000000000000000000"))
        }
        assertNull(historyLogToPumpHistoryEvent(malformed, 123, 106, 587_909_436L))

        val baseline = basalDelivery(
            "1711BBC60A23BA95220003000000F401F401F401FFFF00000000",
        )
        val unchanged = basalDelivery(
            "1711E8C70A23C595220003000000F401F401F401FFFF00000000",
        )
        val changed = basalDelivery(
            "171114C90A23E195220003000000EE02F401EE02FFFF00000000",
        )

        val events = historyLogItemsToPumpHistoryEvents(
            listOf(
                HistoryLogItem(malformed),
                HistoryLogItem(baseline),
                HistoryLogItem(unchanged),
                HistoryLogItem(changed),
            ),
        )

        assertEquals(1, events.size)
        assertEquals("Basal rate changed", events.single().title)
        assertEquals("0.75 U/hr", events.single().detail)
    }

    private fun bolus(
        source: BolusDeliveryHistoryLog.BolusSource,
        sequenceNum: Long = 10L,
        pumpTimeSec: Long = 500_000L,
        bolusId: Int = 42,
        statusId: Int = 0,
        deliveredMilliunits: Int = 1_200,
    ) = BolusDeliveryHistoryLog(
        pumpTimeSec,
        sequenceNum,
        bolusId,
        statusId,
        setOf(BolusDeliveryHistoryLog.BolusType.CORRECTION),
        source,
        0,
        deliveredMilliunits,
        0,
        deliveredMilliunits,
        0,
        deliveredMilliunits,
    )

    private fun hexToBytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun basalDelivery(cargo: String) = BasalDeliveryHistoryLog().apply {
        parse(hexToBytes(cargo))
    }
}
