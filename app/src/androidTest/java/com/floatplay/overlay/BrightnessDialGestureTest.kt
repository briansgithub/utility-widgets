package com.floatplay.overlay

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrightnessDialGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun threeTapsHideWithoutTogglingAutomaticBrightnessOrExtraDim() = checkGesture(
        actions = { tap(); tap(); tap() },
        verify = {
            assertEquals(1, hides)
            assertEquals(0, clicks)
            assertEquals(0, extraDimToggles)
            assertEquals(0, adjustments)
            assertEquals(0, moves)
        },
    )

    @Test fun oneTapStillClicks() = checkGesture(
        actions = { tap() },
        verify = {
            assertEquals(1, clicks)
            assertEquals(0, extraDimToggles)
            assertEquals(0, hides)
        },
    )

    @Test fun doubleTapWithoutDragTogglesExtraDimOnly() = checkGesture(
        actions = { tap(); tap() },
        verify = {
            assertEquals(1, extraDimToggles)
            assertEquals(0, clicks)
            assertEquals(0, hides)
            assertEquals(0, adjustments)
            assertEquals(0, moves)
        },
    )

    @Test fun doubleTapThenTurnStillAdjustsAndCannotStartATripleTap() = checkGesture(
        actions = {
            tap()
            send(MotionEvent.ACTION_DOWN, 450f, 250f)
            send(MotionEvent.ACTION_MOVE, 250f, 450f)
            send(MotionEvent.ACTION_UP, 250f, 450f)
            tap()
        },
        verify = {
            assertEquals(1, adjustments)
            assertEquals(0, extraDimToggles)
            assertEquals(0, moves)
            assertEquals(0, hides)
            assertEquals(1, clicks)
        },
    )

    @Test fun draggingAfterTwoTapsStillMoves() = checkGesture(
        actions = {
            tap()
            tap()
            send(MotionEvent.ACTION_DOWN, 450f, 250f)
            send(MotionEvent.ACTION_MOVE, 250f, 450f)
            send(MotionEvent.ACTION_UP, 250f, 450f)
        },
        verify = {
            assertEquals(1, moves)
            assertEquals(1, finishedMoves)
            assertEquals(0, hides)
            assertEquals(0, extraDimToggles)
            assertEquals(0, clicks)
            assertEquals(0, adjustments)
        },
    )

    @Test fun lateThirdTapCompletesDoubleTapAndStartsAnotherGesture() = checkGesture(
        actions = {
            tap()
            tap()
            advance(ViewConfiguration.getDoubleTapTimeout() + 1)
            tap()
        },
        verify = {
            assertEquals(0, hides)
            assertEquals(1, extraDimToggles)
            assertEquals(1, clicks)
        },
    )

    private fun checkGesture(actions: DialEvents.() -> Unit, verify: DialEvents.() -> Unit) {
        lateinit var events: DialEvents
        instrumentation.runOnMainSync {
            events = DialEvents(BrightnessDialView(instrumentation.targetContext))
            events.actions()
        }
        // Give the gesture timer time to confirm any pending single or double tap.
        SystemClock.sleep(ViewConfiguration.getDoubleTapTimeout() + 80L)
        instrumentation.waitForIdleSync()
        events.verify()
    }

    private class DialEvents(private val dial: BrightnessDialView) {
        var hides = 0
        var clicks = 0
        var extraDimToggles = 0
        var adjustments = 0
        var moves = 0
        var finishedMoves = 0
        private var time = SystemClock.uptimeMillis()
        private var downTime = time

        init {
            dial.layout(0, 0, 500, 500)
            dial.setOnClickListener { clicks++ }
            dial.onToggleExtraDim = { extraDimToggles++ }
            dial.onHide = { hides++ }
            dial.onLevelChanged = { adjustments++ }
            dial.onMove = { _, _ -> moves++ }
            dial.onMoveFinished = { finishedMoves++ }
        }

        fun tap() {
            send(MotionEvent.ACTION_DOWN, 450f, 250f)
            send(MotionEvent.ACTION_UP, 450f, 250f)
        }

        fun advance(milliseconds: Int) {
            time += milliseconds
        }

        fun send(action: Int, x: Float, y: Float) {
            if (action == MotionEvent.ACTION_DOWN) downTime = time
            val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
            try {
                assertTrue(dial.onTouchEvent(event))
            } finally {
                event.recycle()
            }
            advance(50)
        }
    }
}
