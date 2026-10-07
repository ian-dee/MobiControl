package com.jwoglom.controlx2.db.historylog

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HistoryLogTimeRangeTest {
    @Test
    fun `most recently active pump partition is selected by pump time then sequence`() {
        val dao = HistoryLogDummyDao(
            mutableListOf(
                item(seqId = 100, pumpSid = 123, typeId = 9, pumpTimeSec = 1_000),
                item(seqId = 500, pumpSid = 999, typeId = 9, pumpTimeSec = 900),
                item(seqId = 101, pumpSid = 123, typeId = 9, pumpTimeSec = 1_100),
            ),
        )

        assertEquals(123, dao.getMostRecentlyActivePumpSid())
    }

    @Test
    fun `most recently active pump partition is absent when history is empty`() {
        assertEquals(null, HistoryLogDummyDao(mutableListOf()).getMostRecentlyActivePumpSid())
    }

    @Test
    fun `time range query filters by pump type and time and returns newest first`() = runBlocking {
        val dao = HistoryLogDummyDao(
            mutableListOf(
                item(seqId = 1, pumpSid = 123, typeId = 9, pumpTimeSec = 100),
                item(seqId = 2, pumpSid = 123, typeId = 9, pumpTimeSec = 200),
                item(seqId = 3, pumpSid = 123, typeId = 9, pumpTimeSec = 300),
                item(seqId = 4, pumpSid = 123, typeId = 8, pumpTimeSec = 250),
                item(seqId = 5, pumpSid = 999, typeId = 9, pumpTimeSec = 275),
            ),
        )

        val result = dao.getItemsForTypesInTimeRange(
            pumpSid = 123,
            typeIds = listOf(9),
            minPumpTimeSec = 150,
            maxPumpTimeSec = 300,
        ).first()

        assertEquals(listOf(3L, 2L), result.map { it.seqId })
    }

    @Suppress("DEPRECATION")
    private fun item(seqId: Long, pumpSid: Int, typeId: Int, pumpTimeSec: Long) = HistoryLogItem(
        seqId = seqId,
        pumpSid = pumpSid,
        typeId = typeId,
        cargo = byteArrayOf(),
        pumpTimeSec = pumpTimeSec,
        pumpTime = LocalDateTime.of(2008, 1, 1, 0, 0),
    )
}
