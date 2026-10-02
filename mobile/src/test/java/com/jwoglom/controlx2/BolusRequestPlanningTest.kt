package com.jwoglom.controlx2

import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.shared.BolusBgRecordType
import com.jwoglom.pumpx2.pump.messages.response.historyLog.BolusDeliveryHistoryLog
import com.jwoglom.pumpx2.shared.Hex
import org.junit.Assert.assertEquals
import org.junit.Test

class BolusRequestPlanningTest {
    @Test
    fun officialMobiSixGramCarbBolusMatchesCapturedRequestExactly() {
        val request = createInitiateBolusRequest(
            totalUnits = 1.0,
            bolusId = 7809,
            foodUnits = 1.0,
            correctionVolumeMilliunits = 0,
            carbsGrams = 6,
            bgMgdl = 129,
            iobMilliunits = 0,
        )

        assertEquals(1000L, request.totalVolume)
        assertEquals(7809, request.bolusID)
        assertEquals(1, request.bolusTypeBitmask)
        assertEquals(setOf(BolusDeliveryHistoryLog.BolusType.FOOD1), request.bolusTypes)
        assertEquals(1000L, request.foodVolume)
        assertEquals(0L, request.correctionVolume)
        assertEquals(6, request.bolusCarbs)
        assertEquals(129, request.bolusBG)
        assertEquals(0L, request.bolusIOB)
        assertEquals(
            "e8030000811e000001e8030000000000000600810000000000000000000000000000000000",
            Hex.encodeHexString(request.cargo),
        )
    }

    @Test
    fun bolusTypeMappingUsesOnlyDocumentedComponentBits() {
        assertEquals(1, plannedBolusTypeBitmask(carbsGrams = 6, correctionVolumeMilliunits = 0))
        assertEquals(2, plannedBolusTypeBitmask(carbsGrams = 0, correctionVolumeMilliunits = 500))
        assertEquals(3, plannedBolusTypeBitmask(carbsGrams = 6, correctionVolumeMilliunits = 500))
        assertEquals(8, plannedBolusTypeBitmask(carbsGrams = 0, correctionVolumeMilliunits = 0))
    }

    @Test
    fun auditedCorrectionOnlyVectorUsesThousandthsAndTypeTwo() {
        val request = createInitiateBolusRequest(
            totalUnits = 0.15,
            bolusId = 7810,
            foodUnits = 0.0,
            correctionVolumeMilliunits = 150,
            carbsGrams = 0,
            bgMgdl = 155,
            iobMilliunits = 0,
        )

        assertEquals(150L, request.totalVolume)
        assertEquals(2, request.bolusTypeBitmask)
        assertEquals(150L, request.correctionVolume)
        assertEquals(0L, request.foodVolume)
    }

    @Test
    fun auditedCombinedVectorUsesTypeThreeWithoutChangingAmounts() {
        val request = createInitiateBolusRequest(
            totalUnits = 1.15,
            bolusId = 7811,
            foodUnits = 1.0,
            correctionVolumeMilliunits = 150,
            carbsGrams = 6,
            bgMgdl = 155,
            iobMilliunits = 0,
        )

        assertEquals(1_150L, request.totalVolume)
        assertEquals(3, request.bolusTypeBitmask)
        assertEquals(1_000L, request.foodVolume)
        assertEquals(150L, request.correctionVolume)
    }

    @Test
    fun auditedUnitOnlyVectorUsesTypeEight() {
        val request = createInitiateBolusRequest(
            totalUnits = 1.5,
            bolusId = 7812,
            foodUnits = 0.0,
            correctionVolumeMilliunits = 0,
            carbsGrams = 0,
            bgMgdl = 0,
            iobMilliunits = 0,
        )

        assertEquals(1_500L, request.totalVolume)
        assertEquals(8, request.bolusTypeBitmask)
        assertEquals(0L, request.foodVolume)
        assertEquals(0L, request.correctionVolume)
    }

    @Test
    fun automaticBgRemainsMetadataWhenCorrectionIsNotIncluded() {
        val request = createInitiateBolusRequest(
            totalUnits = 1.0,
            bolusId = 7809,
            foodUnits = 1.0,
            correctionVolumeMilliunits = 0,
            carbsGrams = 6,
            bgMgdl = 129,
            iobMilliunits = 0,
        )

        assertEquals(1, request.bolusTypeBitmask)
        assertEquals(0L, request.correctionVolume)
        assertEquals(129, request.bolusBG)
    }

    @Test
    fun manuallyEnteredBgRemainsManualEvenWhenItMatchesAutomaticCgm() {
        val snapshot = createDeveloperPreviewBolusSnapshot()

        val plan = createPostBolusRecordPlan(
            bgValue = snapshot.correctionFactor,
            carbsGrams = 0,
            bolusId = 42,
            dataSnapshot = snapshot,
            glucoseWasEnteredManually = true,
        )

        assertEquals(snapshot.correctionFactor, plan.bgMgdl)
        assertEquals(BolusBgRecordType.MANUAL, plan.bgRecordType)
    }
}
