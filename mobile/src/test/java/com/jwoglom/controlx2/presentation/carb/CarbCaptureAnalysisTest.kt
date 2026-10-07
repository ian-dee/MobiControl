package com.jwoglom.controlx2.presentation.carb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarbCaptureAnalysisTest {
    @Test
    fun `parses a valid structured estimate`() {
        val estimate = parseCarbCaptureEstimate(
            """
                {
                  "description": "Turkey sandwich and apple",
                  "estimated_carbs_grams": 58,
                  "range_low_grams": 46,
                  "range_high_grams": 72,
                  "confidence": "medium",
                  "assumptions": ["Two slices of bread", "One small apple"]
                }
            """.trimIndent(),
        )

        assertEquals("Turkey sandwich and apple", estimate.description)
        assertEquals(58, estimate.estimatedCarbsGrams)
        assertEquals(46, estimate.rangeLowGrams)
        assertEquals(72, estimate.rangeHighGrams)
        assertEquals(CarbCaptureConfidence.MEDIUM, estimate.confidence)
        assertEquals(2, estimate.assumptions.size)
    }

    @Test
    fun `rejects an inverted range`() {
        val error = runCatching {
            parseCarbCaptureEstimate(
                """
                    {
                      "description": "Meal",
                      "estimated_carbs_grams": 20,
                      "range_low_grams": 30,
                      "range_high_grams": 10,
                      "confidence": "low",
                      "assumptions": []
                    }
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `rejects unknown confidence`() {
        val error = runCatching {
            parseCarbCaptureEstimate(
                """
                    {
                      "description": "Meal",
                      "estimated_carbs_grams": 20,
                      "range_low_grams": 10,
                      "range_high_grams": 30,
                      "confidence": "certain",
                      "assumptions": []
                    }
                """.trimIndent(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
