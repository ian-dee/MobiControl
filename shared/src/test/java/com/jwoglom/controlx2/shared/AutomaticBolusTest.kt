package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.TimeSinceResetResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomaticBolusTest {
    private val identity = AutomaticBolusIdentity(123456, 700_000L, 42)

    @Test
    fun `identity and result codecs are strict round trips`() {
        assertEquals(identity, AutomaticBolusIdentity.decode(identity.encode()))

        val result = AutomaticBolusCancelResult(identity, AutomaticBolusCancelOutcome.CONFIRMED)
        assertEquals(result, AutomaticBolusCancelResult.decode(result.encode()))
        assertNull(AutomaticBolusIdentity.decode("1|123|bad|42".toByteArray()))
    }

    @Test
    fun `pump session id is stable boot epoch`() {
        val first = TimeSinceResetResponse(1_000_000L, 300_000L)
        val later = TimeSinceResetResponse(1_000_100L, 300_100L)
        assertEquals(700_000L, first.pumpSessionId())
        assertEquals(first.pumpSessionId(), later.pumpSessionId())
    }

    @Test
    fun `only the same active Control IQ bolus matches`() {
        val automatic = current(
            status = CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
            bolusId = 42,
            source = BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
        )
        assertEquals(
            AutomaticBolusValidation.MATCHED_ACTIVE_AUTOMATIC_BOLUS,
            validateAutomaticBolusForCancellation(identity, 123456, 700_000L, automatic),
        )

        assertEquals(
            AutomaticBolusValidation.MATCHED_ACTIVE_AUTOMATIC_BOLUS,
            validateAutomaticBolusForCancellation(identity, 123456, 700_001L, automatic),
        )
        assertEquals(
            AutomaticBolusValidation.IDENTITY_MISMATCH,
            validateAutomaticBolusForCancellation(identity, 123456, 700_003L, automatic),
        )
        assertEquals(
            AutomaticBolusValidation.IDENTITY_MISMATCH,
            validateAutomaticBolusForCancellation(
                identity,
                123456,
                700_000L,
                current(
                    CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
                    43,
                    BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
                ),
            ),
        )
        assertEquals(
            AutomaticBolusValidation.IDENTITY_MISMATCH,
            validateAutomaticBolusForCancellation(
                identity,
                123456,
                700_000L,
                current(
                    CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
                    42,
                    BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
                ),
            ),
        )
    }

    @Test
    fun `terminal current status reports already ended`() {
        val ended = current(
            status = CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
            bolusId = 0,
            source = BolusDeliveryHistoryLog.BolusSource.QUICK_BOLUS,
            timestamp = 0L,
        )
        assertEquals(
            AutomaticBolusValidation.ALREADY_ENDED,
            validateAutomaticBolusForCancellation(identity, 123456, 700_000L, ended),
        )
    }

    private fun current(
        status: CurrentBolusStatusResponse.CurrentBolusStatus,
        bolusId: Int,
        source: BolusDeliveryHistoryLog.BolusSource,
        timestamp: Long = 100L,
    ) = CurrentBolusStatusResponse(
        status.id,
        bolusId,
        timestamp,
        1_250L,
        source.id(),
        1,
    )
}
