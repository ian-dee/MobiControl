package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticatedPhoneBolusSubmissionTest {
    @Test
    fun roundTripBindsHistoryPlanToExactInitiationId() {
        val request = InitiateBolusRequest(
            1_500,
            7718,
            8,
            0,
            0,
            0,
            155,
            3_000,
        )
        val submission = AuthenticatedPhoneBolusSubmission(
            request,
            PostBolusRecordPlan(
                bolusId = 7718,
                bgMgdl = 155,
                bgRecordType = BolusBgRecordType.AUTOMATIC,
                carbsGrams = 0,
            ),
        )

        val decoded = AuthenticatedPhoneBolusSubmission.decode(submission.encode())

        assertEquals(7718, decoded?.request?.bolusID)
        assertEquals(1_500L, decoded?.request?.totalVolume)
        assertEquals(BolusBgRecordType.AUTOMATIC, decoded?.postBolusRecords?.bgRecordType)
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertNull(AuthenticatedPhoneBolusSubmission.decode("not-a-submission".toByteArray()))
    }
}
