package com.floatplay.overlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BrightnessDialGesturesTest {
    private class Harness(
        touchSlop: Float = 20f,
        doubleTapSlop: Float = 80f,
        tapTimeoutMs: Long = 300L,
        doubleTapTimeoutMs: Long = 300L,
        private val centerX: Float = 250f,
        private val centerY: Float = 250f,
        trackRadius: Float = 156f,
    ) {
        var now = 1000L
        var timeoutAt: Long? = null
        var singleTaps = 0
        var doubleTaps = 0
        var tripleTaps = 0
        var moves = 0
        var moveFinished = 0
        var totalMoveX = 0f
        var totalMoveY = 0f
        var adjustStarts = 0
        var adjustEnds = 0
        val adjustDeltas = ArrayList<Float>()

        val gestures = BrightnessDialGestures(
            touchSlop = touchSlop,
            doubleTapSlop = doubleTapSlop,
            tapTimeoutMs = tapTimeoutMs,
            doubleTapTimeoutMs = doubleTapTimeoutMs,
            trackRadiusProvider = { trackRadius },
            onSingleTap = { singleTaps++ },
            onDoubleTap = { doubleTaps++ },
            onTripleTap = { tripleTaps++ },
            onMove = { dx, dy ->
                moves++
                totalMoveX += dx
                totalMoveY += dy
            },
            onMoveFinished = { moveFinished++ },
            onAdjustStart = { adjustStarts++ },
            onAdjustDelta = { adjustDeltas.add(it) },
            onAdjustEnd = { adjustEnds++ },
            scheduleTimeout = { delay -> timeoutAt = now + delay },
            cancelTimeout = { timeoutAt = null },
        )

        fun advance(ms: Long) {
            now += ms
            val due = timeoutAt
            if (due != null && now >= due) {
                timeoutAt = null
                gestures.onTimeout()
            }
        }

        fun down(x: Float = centerX, y: Float = centerY) {
            gestures.onDown(x, y, x, y, centerX, centerY, now)
        }

        fun move(x: Float, y: Float) {
            gestures.onMove(x, y, x, y, centerX, centerY)
        }

        fun up() {
            gestures.onUp(now)
        }

        fun tap(x: Float = centerX, y: Float = centerY, holdMs: Long = 40L, pauseAfterMs: Long = 50L) {
            down(x, y)
            now += holdMs
            up()
            now += pauseAfterMs
        }

        fun settle() {
            advance(400L)
        }
    }

    @Test fun singleTapFiresSingleTapOnceAfterDoubleTapWindow() {
        val h = Harness()
        h.tap()
        assertEquals(0, h.singleTaps)
        h.settle()
        assertEquals(1, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.tripleTaps)
        assertEquals(0, h.moves)
        assertEquals(0, h.adjustStarts)
    }

    @Test fun doubleTapWithoutDragTogglesExtraDimWithoutSingleTapOrDrag() {
        val h = Harness()
        h.tap()
        h.tap()
        assertEquals(0, h.doubleTaps)
        h.settle()
        assertEquals(1, h.doubleTaps)
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.tripleTaps)
        assertEquals(0, h.moves)
        assertEquals(0, h.adjustStarts)
    }

    @Test fun tripleTapHidesImmediatelyWithoutSingleOrDoubleTap() {
        val h = Harness()
        h.tap()
        h.tap()
        h.tap()
        assertEquals(1, h.tripleTaps)
        h.settle()
        assertEquals(1, h.tripleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.moves)
        assertEquals(0, h.adjustStarts)
    }

    @Test fun longPressDoesNotTriggerSingleOrDoubleTap() {
        val h = Harness()
        // Single long press.
        h.tap(holdMs = 450L)
        h.settle()
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)

        // Tap followed by long press (not a quick double tap).
        h.tap(holdMs = 40L, pauseAfterMs = 60L)
        h.tap(holdMs = 450L, pauseAfterMs = 50L)
        h.settle()
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.tripleTaps)
    }

    @Test fun doubleTapThenDragAdjustsBrightnessWithoutExtraDimOrSingleTap() {
        val h = Harness()
        h.tap(450f, 250f)
        h.down(450f, 250f)
        h.now += 20L
        h.move(250f, 450f)
        h.now += 20L
        h.up()
        h.settle()
        assertEquals(1, h.adjustStarts)
        assertEquals(1, h.adjustEnds)
        assertTrue("Expected positive clockwise delta", h.adjustDeltas.sum() > 0.25f)
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.tripleTaps)
        assertEquals(0, h.moves)
    }

    @Test fun gradualDragAcrossTouchSlopHasNoInitialJump() {
        val h = Harness(touchSlop = 20f, trackRadius = 156f)
        h.tap(450f, 250f)
        // Start on outer track at 3 o'clock: (450, 250) -> rel (200, 0).
        h.down(450f, 250f)
        for (dy in 1..19) {
            h.move(450f, 250f + dy)
        }
        assertEquals(0, h.adjustStarts)
        // Cross touchSlop by just 1px (dy = 21).
        h.move(450f, 271f)
        assertEquals(1, h.adjustStarts)
        assertTrue("Expected tiny initial delta, got ${h.adjustDeltas}",
            h.adjustDeltas.sum() in 0f..0.005f)
        h.up()
    }

    @Test fun rotaryDragIsExactClockwiseAndCounterclockwiseAcrossWrapAround() {
        val h = Harness(touchSlop = 10f, trackRadius = 100f)
        h.tap(50f, 265f)
        // Start at 9 o'clock just below horizontal: rel (-200, +15).
        h.down(50f, 265f)
        // Move clockwise across the -180/+180 seam to rel (-200, -15) -> 30px upward!
        h.move(50f, 250f)
        h.move(50f, 235f)
        val clockwiseDelta = h.adjustDeltas.sum()
        assertTrue("Expected positive clockwise delta across 9 o'clock seam, got $clockwiseDelta",
            clockwiseDelta > 0.01f && clockwiseDelta < 0.1f)

        // Reverse counterclockwise back across the seam.
        h.adjustDeltas.clear()
        h.move(50f, 250f)
        h.move(50f, 265f)
        val ccwDelta = h.adjustDeltas.sum()
        assertTrue("Expected negative ccw delta across 9 o'clock seam, got $ccwDelta",
            ccwDelta < -0.01f && ccwDelta > -0.1f)
        h.up()
    }

    @Test fun draggingNearCenterDoesNotBlowUpAngularDelta() {
        val h = Harness(touchSlop = 5f, trackRadius = 156f)
        h.tap(260f, 250f)
        // Drag 10px tangentially very close to center (radius = 10px instead of 156px).
        h.down(260f, 250f)
        h.move(260f, 256f) // crosses slop
        h.adjustDeltas.clear()
        h.move(260f, 266f) // 10px tangential step at small radius
        val delta = abs(h.adjustDeltas.sum())
        // Should be bounded by arc length / trackRadius / 270 deg (~10/156 rad = 3.7 deg = 0.014),
        // NOT raw atan2 jump (~30 deg = 0.11).
        assertTrue("Center drag blew up: delta=$delta", delta < 0.03f)
        h.up()
    }

    @Test fun singleTouchDragMovesWithoutTappingOrAdjusting() {
        val h = Harness()
        h.down(250f, 250f)
        h.move(320f, 280f)
        h.up()
        h.settle()
        assertEquals(1, h.moves)
        assertEquals(1, h.moveFinished)
        assertEquals(70f, h.totalMoveX, 0.01f)
        assertEquals(30f, h.totalMoveY, 0.01f)
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.adjustStarts)
    }

    @Test fun draggingAfterTwoTapsMovesInsteadOfHidingOrTogglingExtraDim() {
        val h = Harness()
        h.tap()
        h.tap()
        h.down(250f, 250f)
        h.move(350f, 250f)
        h.up()
        h.settle()
        assertEquals(1, h.moves)
        assertEquals(1, h.moveFinished)
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.tripleTaps)
        assertEquals(0, h.adjustStarts)
    }

    @Test fun twoTapsFarApartDoNotTriggerDoubleTap() {
        val h = Harness(doubleTapSlop = 80f)
        h.tap(x = 100f, y = 250f)
        h.tap(x = 300f, y = 250f)
        h.settle()
        assertEquals(0, h.doubleTaps)
        assertEquals(1, h.singleTaps)
    }

    @Test fun lateThirdTapCompletesDoubleTapAndStartsNewSingleTap() {
        val h = Harness(doubleTapTimeoutMs = 300L)
        h.tap()
        h.tap()
        // Advance past timeout without triggering timeout callback manually (tests synthetic clock).
        h.now += 301L
        h.tap()
        h.settle()
        assertEquals(1, h.doubleTaps)
        assertEquals(1, h.singleTaps)
        assertEquals(0, h.tripleTaps)
    }

    @Test fun multiTouchCancelsTapAndDoubleTap() {
        val h = Harness()
        h.tap()
        h.down(250f, 250f)
        h.gestures.onPointerDown()
        h.now += 40L
        h.up()
        h.settle()
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
        assertEquals(0, h.tripleTaps)
    }

    @Test fun cancelEndsActiveAdjustmentAndClearsPendingTap() {
        val h = Harness()
        h.tap(450f, 250f)
        h.down(450f, 250f)
        h.move(250f, 450f)
        assertTrue(h.gestures.isAdjusting)
        h.gestures.onCancel()
        assertFalse(h.gestures.isAdjusting)
        assertEquals(1, h.adjustEnds)
        h.settle()
        assertEquals(0, h.singleTaps)
        assertEquals(0, h.doubleTaps)
    }
}
