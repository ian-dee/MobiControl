package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusDeliveryCalculation
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusDeliveryCalculationRegion
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusCalculationComponent
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusCalculationValueTone
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.BolusReviewDialog
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.bolusCalculationValueTone
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.formatBolusCalculationUnits
import com.jwoglom.controlx2.presentation.screens.sections.components.bolus.toBolusDeliveryCalculation
import com.jwoglom.controlx2.presentation.simulation.DeveloperPreviewProfile
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewBolusSnapshot
import com.jwoglom.controlx2.presentation.simulation.createDeveloperPreviewLastBg
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BolusDeliveryCalculationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun breakdownUsesTheSamePumpCalculatorUnitsAsDelivery() {
        val calculator = buildBolusCalculator(
            createDeveloperPreviewBolusSnapshot(DeveloperPreviewProfile.DEFAULT),
            createDeveloperPreviewLastBg(),
            null,
            30,
            null,
        )

        val calculation = bolusCalcDecision(calculator, null)!!.units
            .toBolusDeliveryCalculation()

        assertEquals(1.0, calculation.correctionUnits, 0.001)
        assertEquals(3.0, calculation.foodUnits, 0.001)
        assertEquals(-1.5, calculation.insulinOnBoardUnits, 0.001)
        assertEquals(3.0, calculation.totalUnits, 0.001)
    }

    @Test
    fun nearZeroDoesNotRenderAsNegativeZero() {
        assertEquals("0.00 u", formatBolusCalculationUnits(-0.001))
    }

    @Test
    fun tandemColorSemanticsMarkOnlyDoseReducingTermsAsSubtractive() {
        assertEquals(
            BolusCalculationValueTone.ADDITIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.CORRECTION,
                units = 1.8,
                correctionEnabled = true,
            ),
        )
        assertEquals(
            BolusCalculationValueTone.SUBTRACTIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.CORRECTION,
                units = -0.3,
                correctionEnabled = true,
            ),
        )
        assertEquals(
            BolusCalculationValueTone.SUBTRACTIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.INSULIN_ON_BOARD,
                units = 0.0,
                correctionEnabled = true,
            ),
        )
        assertEquals(
            BolusCalculationValueTone.ADDITIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.FOOD,
                units = 5.0,
                correctionEnabled = true,
            ),
        )
    }

    @Test
    fun explicitlyDeclinedCorrectionAndIobRemainInactive() {
        assertEquals(
            BolusCalculationValueTone.INACTIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.CORRECTION,
                units = 1.8,
                correctionEnabled = false,
            ),
        )
        assertEquals(
            BolusCalculationValueTone.INACTIVE,
            bolusCalculationValueTone(
                BolusCalculationComponent.INSULIN_ON_BOARD,
                units = -3.0,
                correctionEnabled = false,
            ),
        )
    }

    @Test
    fun calculationRowsDisplayRequestedLabelsAndValues() {
        composeRule.setContent {
            ControlX2Theme {
                BolusDeliveryCalculationRegion(
                    calculation = BolusDeliveryCalculation(
                        correctionUnits = 1.8,
                        foodUnits = 5.0,
                        insulinOnBoardUnits = -3.0,
                        totalUnits = 5.0,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Delivery Calculation:").assertIsDisplayed()
        composeRule.onNodeWithText("Correction").assertIsDisplayed()
        composeRule.onNodeWithText("1.80 u").assertIsDisplayed()
        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithText("Insulin On Board").assertIsDisplayed()
        composeRule.onNodeWithText("-3.00 u").assertIsDisplayed()
        composeRule.onNodeWithText("Total Units to Deliver").assertIsDisplayed()
    }

    @Test
    fun correctionOffUsesZeroAndDoesNotAddResponsiveStatusLabels() {
        composeRule.setContent {
            ControlX2Theme {
                BolusDeliveryCalculationRegion(
                    calculation = BolusDeliveryCalculation(
                        correctionUnits = 1.8,
                        foodUnits = 5.0,
                        insulinOnBoardUnits = -3.0,
                        totalUnits = 5.0,
                    ),
                    correctionEnabled = false,
                )
            }
        }

        composeRule.onNodeWithText("0.00 u").assertIsDisplayed()
        composeRule.onAllNodesWithText("Off").assertCountEquals(0)
        composeRule.onNodeWithText("-3.00 u").assertIsDisplayed()
        composeRule.onAllNodesWithText("Not applied").assertCountEquals(0)
        composeRule.onAllNodesWithText("5.00 u").assertCountEquals(2)
    }

    @Test
    fun manualOverrideMakesCalculatedComponentsInactiveAndKeepsEnteredTotal() {
        composeRule.setContent {
            ControlX2Theme {
                BolusDeliveryCalculationRegion(
                    calculation = BolusDeliveryCalculation(
                        correctionUnits = 1.8,
                        foodUnits = 5.0,
                        insulinOnBoardUnits = -3.0,
                        totalUnits = 2.75,
                    ),
                    correctionEnabled = true,
                    overrideActive = true,
                )
            }
        }

        composeRule.onNodeWithText("Manual override active").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Correction, food, and insulin on board do not change this dose.",
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("0.00 u").assertCountEquals(3)
        composeRule.onNodeWithText("2.75 u").assertIsDisplayed()
    }

    @Test
    fun finalReviewUsesTheSameManualOverridePresentation() {
        composeRule.setContent {
            ControlX2Theme {
                BolusReviewDialog(
                    bolusUnits = 2.75,
                    calculation = BolusDeliveryCalculation(
                        correctionUnits = 0.0,
                        foodUnits = 0.0,
                        insulinOnBoardUnits = 0.0,
                        totalUnits = 2.75,
                    ),
                    correctionEnabled = true,
                    overrideActive = true,
                    onBack = {},
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Review the manually entered override before continuing to deliver 2.75 U.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("2.75 u").assertIsDisplayed()
    }
}
