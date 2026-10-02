package com.jwoglom.controlx2.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostBolusRecordPlanTest {
    private val staged = PostBolusRecordPlan(
        bolusId = 7809,
        bgMgdl = 129,
        bgRecordType = BolusBgRecordType.AUTOMATIC,
        carbsGrams = 6,
    )

    @Test
    fun successfulOfficialSequenceRetainsIdentityAndTimestampUntilBothAcks() {
        val bgPending = staged.onInitiationAccepted(pumpTime = 349_960L)
        assertTrue(bgPending.handled)
        assertEquals(PostBolusRecordCommand.SEND_BG, bgPending.nextCommand)
        assertEquals(PostBolusRecordPhase.BG_RESPONSE_PENDING, bgPending.persistedPlan?.phase)
        assertEquals(7809, bgPending.persistedPlan?.bolusId)
        assertEquals(349_960L, bgPending.persistedPlan?.pumpTimeSecondsSinceReset)

        val carbPending = bgPending.persistedPlan!!.onBgResponse(success = true)
        assertTrue(carbPending.handled)
        assertEquals(PostBolusRecordCommand.SEND_CARBS, carbPending.nextCommand)
        assertEquals(PostBolusRecordPhase.CARB_RESPONSE_PENDING, carbPending.persistedPlan?.phase)
        assertEquals(7809, carbPending.persistedPlan?.bolusId)
        assertEquals(349_960L, carbPending.persistedPlan?.pumpTimeSecondsSinceReset)

        val complete = carbPending.persistedPlan!!.onCarbResponse(success = true)
        assertTrue(complete.handled)
        assertEquals(PostBolusRecordCommand.NONE, complete.nextCommand)
        assertNull(complete.persistedPlan)
    }

    @Test
    fun negativeBgAckIsRetainedAndDoesNotAdvanceToCarbs() {
        val bgPending = staged.onInitiationAccepted(349_960L).persistedPlan!!

        val rejected = bgPending.onBgResponse(success = false)

        assertTrue(rejected.handled)
        assertEquals(PostBolusRecordCommand.NONE, rejected.nextCommand)
        assertEquals(PostBolusRecordPhase.BG_REJECTED, rejected.persistedPlan?.phase)
        assertEquals(6, rejected.persistedPlan?.carbsGrams)
    }

    @Test
    fun negativeCarbAckIsRetainedInsteadOfBeingReportedComplete() {
        val carbPending = staged.onInitiationAccepted(349_960L)
            .persistedPlan!!
            .onBgResponse(success = true)
            .persistedPlan!!

        val rejected = carbPending.onCarbResponse(success = false)

        assertTrue(rejected.handled)
        assertEquals(PostBolusRecordPhase.CARB_REJECTED, rejected.persistedPlan?.phase)
        assertEquals(PostBolusRecordCommand.NONE, rejected.nextCommand)
    }

    @Test
    fun duplicateOrOutOfOrderResponsesAreIgnoredWithoutAResend() {
        val bgPending = staged.onInitiationAccepted(349_960L).persistedPlan!!

        val outOfOrderCarb = bgPending.onCarbResponse(success = true)

        assertFalse(outOfOrderCarb.handled)
        assertEquals(bgPending, outOfOrderCarb.persistedPlan)
        assertEquals(PostBolusRecordCommand.NONE, outOfOrderCarb.nextCommand)
    }

    @Test
    fun versionTwoRoundTripAndLegacyVersionOneDecodeRemainSupported() {
        val pending = staged.onInitiationAccepted(349_960L).persistedPlan!!
        assertEquals(pending, PostBolusRecordPlan.decode(pending.encode()))

        val legacy = "1|7809|129|AUTOMATIC|6".toByteArray()
        assertEquals(staged, PostBolusRecordPlan.decode(legacy))
    }

    @Test
    fun noOptionalHistoryRecordsCompletesWithoutSendingACommand() {
        val empty = PostBolusRecordPlan(
            bolusId = 7809,
            bgMgdl = null,
            bgRecordType = BolusBgRecordType.NONE,
            carbsGrams = 0,
        )

        val complete = empty.onInitiationAccepted(349_960L)

        assertTrue(complete.handled)
        assertNull(complete.persistedPlan)
        assertEquals(PostBolusRecordCommand.NONE, complete.nextCommand)
    }
}
