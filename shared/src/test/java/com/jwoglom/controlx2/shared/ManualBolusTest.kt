package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualBolusTest {
    private val identity = ManualBolusIdentity(123456, 700_000L, 42)

    @Test
    fun `identity and result codecs are strict round trips`() {
        assertEquals(identity, ManualBolusIdentity.decode(identity.encode()))

        val result = ManualBolusCancelResult(identity, ManualBolusCancelOutcome.CONFIRMED)
        assertEquals(result, ManualBolusCancelResult.decode(result.encode()))
        val acknowledgement = ManualBolusTerminalAcknowledgement(
            identity,
            ManualBolusTerminalAcknowledgementStage.ALARM,
        )
        assertEquals(
            acknowledgement,
            ManualBolusTerminalAcknowledgement.decode(acknowledgement.encode()),
        )
        assertNull(ManualBolusIdentity.decode("1|123|bad|42".toByteArray()))
    }

    @Test
    fun `only the same active Bluetooth remote bolus matches`() {
        val manual = current(
            status = CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
            bolusId = 42,
            source = BolusDeliveryHistoryLog.BolusSource.BLUETOOTH_REMOTE_BOLUS,
        )
        assertEquals(
            ManualBolusValidation.MATCHED_ACTIVE_MANUAL_BOLUS,
            validateManualBolusForCancellation(identity, 123456, 700_000L, manual),
        )
        assertEquals(
            ManualBolusValidation.MATCHED_ACTIVE_MANUAL_BOLUS,
            validateManualBolusForCancellation(identity, 123456, 700_001L, manual),
        )
        assertEquals(
            ManualBolusValidation.IDENTITY_MISMATCH,
            validateManualBolusForCancellation(identity, 123456, 700_003L, manual),
        )
        assertEquals(
            ManualBolusValidation.IDENTITY_MISMATCH,
            validateManualBolusForCancellation(
                identity,
                123456,
                700_000L,
                current(
                    CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
                    42,
                    BolusDeliveryHistoryLog.BolusSource.CONTROL_IQ_AUTO_BOLUS,
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
            ManualBolusValidation.ALREADY_ENDED,
            validateManualBolusForCancellation(identity, 123456, 700_000L, ended),
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
