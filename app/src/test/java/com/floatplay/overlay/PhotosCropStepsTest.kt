package com.floatplay.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotosCropStepsTest {
    @Test fun clockwiseRaisesTheStraightenValueByOneStep() =
        assertEquals(0.11f, PhotosCrop.nextAngle(0.1f, -0.785f, 0.785f, clockwise = true, step = 0.01f)!!, 1e-6f)

    @Test fun counterclockwiseLowersIt() =
        assertEquals(-0.01f, PhotosCrop.nextAngle(0f, -0.785f, 0.785f, clockwise = false, step = 0.01f)!!, 1e-6f)

    @Test fun lastStepStopsAtTheEndOfTheBar() =
        assertEquals(0.785f, PhotosCrop.nextAngle(0.78f, -0.785f, 0.785f, clockwise = true, step = 0.01f)!!, 1e-6f)

    @Test fun atTheEndThereIsNoStep() =
        assertNull(PhotosCrop.nextAngle(0.785f, -0.785f, 0.785f, clockwise = true, step = 0.01f))

    @Test fun clockwiseDragsLeftAlongTheBar() {
        assertEquals(-26, PhotosCrop.rotationDragX(clockwise = true, distancePx = 26))
        assertEquals(26, PhotosCrop.rotationDragX(clockwise = false, distancePx = 26))
    }

    @Test fun pixel7aUsesItsMeasuredSteps() {
        val steps = PhotosCrop.steps("lynx", touchSlopPx = 21, density = 2.625f)
        assertEquals(26, steps.rotationDragPx)
        assertEquals(PhotosCrop.ROTATION_STEP_RAD, steps.rotationRad, 0f)
    }

    @Test fun pixel9ProUsesItsMeasuredSteps() {
        val steps = PhotosCrop.steps("caiman", touchSlopPx = 24, density = 3f)
        assertEquals(30, steps.rotationDragPx)
        assertEquals(26, steps.panXPx)
        assertEquals(26, steps.panYPx)
        assertEquals(25, steps.pinchPx)
    }

    @Test fun photoDragsRunFastOnlyUpToJustShortOfTheSlop() {
        assertEquals(18, PhotosCrop.panApproachPx(21, 23))
        assertEquals(5, PhotosCrop.panApproachPx(8, 5))
        assertEquals(250L, PhotosCrop.panFinishMs(5))
    }

    @Test fun quickPressesBecomeOneLongerDrag() {
        assertEquals(23, PhotosCrop.panDragPx(23, 21, 1))
        assertEquals(25, PhotosCrop.panDragPx(23, 21, 3))
        assertEquals(34, PhotosCrop.panDragPx(26, 21, 3))
    }

    @Test fun unmeasuredPhonesStartJustPastTheTouchSlop() {
        val steps = PhotosCrop.steps("unknown", touchSlopPx = 24, density = 3f)
        assertEquals(26, steps.panXPx)
        assertEquals(26, steps.panYPx)
        assertEquals(30, steps.rotationDragPx)
    }

    @Test fun freeBandSitsBetweenTheTopAndBottomControls() {
        // Pixel 7a crop screen: tool row 362..488, photo 591..1770, tool tabs 1852..2105, bottom bar 2174..2300
        val controls = listOf(intArrayOf(151, 277), intArrayOf(362, 488), intArrayOf(591, 1770), intArrayOf(1852, 2105), intArrayOf(2174, 2300))
        val band = PhotosCrop.freeBand(2400, controls)!!
        assertEquals(488, band[0])
        assertEquals(1852, band[1])
    }

    @Test fun padBelowTheBandIsLiftedAboveTheBottomControls() =
        assertEquals(1852 - 368, PhotosCrop.snapY(1700, 368, 488, 1852))

    @Test fun padAboveTheBandIsLoweredBelowTheTopControls() =
        assertEquals(488, PhotosCrop.snapY(300, 368, 488, 1852))

    @Test fun padInsideTheBandStays() =
        assertEquals(900, PhotosCrop.snapY(900, 368, 488, 1852))
}
