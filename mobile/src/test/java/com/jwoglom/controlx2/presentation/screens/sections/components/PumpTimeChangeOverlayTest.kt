package com.jwoglom.controlx2.presentation.screens.sections.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PumpTimeChangeOverlayTest {
    @Test
    fun forwardChange_approachesTerminusFromLeft_andArrowPointsRight() {
        val layout = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 120f,
            canvasWidth = 300f,
            gradientWidth = 48f,
            iconSize = 16f,
            iconGap = 2f,
        )

        assertEquals(72f, layout.gradientStartX)
        assertEquals(120f, layout.gradientEndX)
        assertEquals(104f, layout.arrowStartX)
        assertEquals(layout.terminusX, layout.arrowEndX)
        assertEquals(86f, layout.clockStartX)
    }

    @Test
    fun backwardChange_approachesTerminusFromRight_andArrowPointsLeft() {
        val layout = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 120f,
            canvasWidth = 300f,
            gradientWidth = 48f,
            iconSize = 16f,
            iconGap = 2f,
        )

        assertEquals(120f, layout.gradientStartX)
        assertEquals(168f, layout.gradientEndX)
        assertEquals(layout.terminusX, layout.arrowStartX)
        assertEquals(136f, layout.arrowEndX)
        assertEquals(138f, layout.clockStartX)
    }

    @Test
    fun gradientsClampAtChartEdges_withoutMovingTerminus() {
        val forward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 20f,
            canvasWidth = 300f,
            gradientWidth = 48f,
        )
        val backward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 285f,
            canvasWidth = 300f,
            gradientWidth = 48f,
        )

        assertEquals(0f, forward.gradientStartX)
        assertEquals(20f, forward.terminusX)
        assertEquals(285f, backward.terminusX)
        assertEquals(300f, backward.gradientEndX)
    }

    @Test
    fun glyphGroupRemainsRigidBeyondPlotEdges() {
        val forward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 20f,
            canvasWidth = 300f,
            iconSize = 16f,
            iconGap = 2f,
        )
        val backward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 280f,
            canvasWidth = 300f,
            iconSize = 16f,
            iconGap = 2f,
        )

        assertEquals(4f, forward.arrowStartX)
        assertEquals(-14f, forward.clockStartX)
        assertEquals(296f, backward.arrowEndX)
        assertEquals(298f, backward.clockStartX)
    }

    @Test
    fun backwardChangeNearRightEdge_placesLabelBeforeTerminusWithoutOverlap() {
        val layout = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 200f,
            canvasWidth = 270f,
            iconSize = 16f,
            iconGap = 2f,
            labelGap = 4f,
            labelWidth = 68f,
        )

        assertEquals(128f, layout.labelStartX)
        assertEquals(196f, layout.labelStartX + 68f)
        assertEquals(200f, layout.terminusX)
        assertEquals(218f, layout.clockStartX)
    }

    @Test
    fun forwardChange_placesLabelBeforeClockWithoutOverlap() {
        val layout = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 260f,
            canvasWidth = 270f,
            iconSize = 16f,
            iconGap = 2f,
            labelGap = 4f,
            labelWidth = 68f,
        )

        assertEquals(154f, layout.labelStartX)
        assertEquals(222f, layout.labelStartX + 68f)
        assertEquals(226f, layout.clockStartX)
        assertEquals(260f, layout.terminusX)
    }

    @Test
    fun allChanges_keepLabelsLeftOfTerminus() {
        val forward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 120f,
            canvasWidth = 300f,
        )
        val backward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 120f,
            canvasWidth = 300f,
        )

        assertEquals(14f, forward.labelStartX)
        assertEquals(48f, backward.labelStartX)
        assertEquals(82f, forward.labelStartX + 68f)
        assertEquals(116f, backward.labelStartX + 68f)
    }

    @Test
    fun labelsRemainLeftOfTerminusAtLeftEdge_andClipWithTimeline() {
        val forward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.FORWARD,
            terminusX = 20f,
            canvasWidth = 300f,
            iconSize = 16f,
            iconGap = 2f,
            labelGap = 4f,
            labelWidth = 68f,
        )
        val backward = pumpTimeChangeVisualLayout(
            direction = PumpClockChangeDirection.BACKWARD,
            terminusX = 20f,
            canvasWidth = 300f,
            iconSize = 16f,
            iconGap = 2f,
            labelGap = 4f,
            labelWidth = 68f,
        )

        assertEquals(-86f, forward.labelStartX)
        assertEquals(-52f, backward.labelStartX)
        assertEquals(-18f, forward.labelStartX + 68f)
        assertEquals(16f, backward.labelStartX + 68f)
    }
}
