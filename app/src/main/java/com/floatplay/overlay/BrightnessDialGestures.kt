package com.floatplay.overlay

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Pure gesture classifier and rotary drag tracker for the floating brightness dial.
 *
 * Gestures:
 * - Single quick tap: toggle automatic brightness
 * - Double quick tap (no drag, not a long press): toggle Extra Dim
 * - Triple quick tap: hide the dial
 * - Single-touch drag: move the floating dial
 * - Double-tap then drag: smoothly turn the dial to adjust brightness
 */
class BrightnessDialGestures(
    private val touchSlop: Float,
    private val doubleTapSlop: Float,
    private val tapTimeoutMs: Long,
    private val doubleTapTimeoutMs: Long,
    private val trackRadiusProvider: () -> Float,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onTripleTap: () -> Unit,
    private val onMove: (dx: Float, dy: Float) -> Unit,
    private val onMoveFinished: () -> Unit,
    private val onAdjustStart: () -> Unit,
    private val onAdjustDelta: (deltaFraction: Float) -> Unit,
    private val onAdjustEnd: () -> Unit,
    private val scheduleTimeout: (delayMs: Long) -> Unit,
    private val cancelTimeout: () -> Unit,
) {
    private enum class DragMode { NONE, MOVING, ADJUSTING }

    private var active = false
    private var dragMode = DragMode.NONE
    private var multiTouch = false
    private var downTimeMs = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var downRelX = 0f
    private var downRelY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastRelX = 0f
    private var lastRelY = 0f

    private var activeTapIndex = 0
    private var pendingTapCount = 0
    private var lastTapUpTimeMs = 0L
    private var lastTapRawX = 0f
    private var lastTapRawY = 0f

    val isAdjusting: Boolean get() = dragMode == DragMode.ADJUSTING

    fun onDown(
        x: Float,
        y: Float,
        rawX: Float,
        rawY: Float,
        centerX: Float,
        centerY: Float,
        timeMs: Long,
    ) {
        cancelTimeout()
        val elapsedSinceLastTap = timeMs - lastTapUpTimeMs
        if (pendingTapCount in 1..2 && elapsedSinceLastTap > doubleTapTimeoutMs) {
            firePendingTap()
        }

        val withinTime = elapsedSinceLastTap in 0L..doubleTapTimeoutMs
        val withinSlop = hypot(rawX - lastTapRawX, rawY - lastTapRawY) <= doubleTapSlop
        activeTapIndex = if (pendingTapCount in 1..2 && withinTime && withinSlop) {
            pendingTapCount + 1
        } else {
            1
        }
        pendingTapCount = 0

        active = true
        dragMode = DragMode.NONE
        multiTouch = false
        downTimeMs = timeMs
        downRawX = rawX
        downRawY = rawY
        downRelX = x - centerX
        downRelY = y - centerY
        lastRawX = rawX
        lastRawY = rawY
        lastRelX = downRelX
        lastRelY = downRelY
    }

    fun onPointerDown() {
        multiTouch = true
        if (dragMode == DragMode.NONE) {
            cancelTimeout()
            pendingTapCount = 0
        }
    }

    fun onMove(
        x: Float,
        y: Float,
        rawX: Float,
        rawY: Float,
        centerX: Float,
        centerY: Float,
    ) {
        if (!active) return
        val relX = x - centerX
        val relY = y - centerY

        when (dragMode) {
            DragMode.NONE -> {
                val dist = hypot(rawX - downRawX, rawY - downRawY)
                if (dist > touchSlop) {
                    if (activeTapIndex == 2 && !multiTouch) {
                        val excessFraction = ((dist - touchSlop) / dist).coerceIn(0f, 1f)
                        val anchorX = relX - (relX - downRelX) * excessFraction
                        val anchorY = relY - (relY - downRelY) * excessFraction
                        dragMode = DragMode.ADJUSTING
                        lastRelX = anchorX
                        lastRelY = anchorY
                        onAdjustStart()
                        val delta = rotaryDeltaFraction(anchorX, anchorY, relX, relY)
                        lastRelX = relX
                        lastRelY = relY
                        if (delta != 0f) onAdjustDelta(delta)
                    } else {
                        dragMode = DragMode.MOVING
                        val dx = rawX - lastRawX
                        val dy = rawY - lastRawY
                        lastRawX = rawX
                        lastRawY = rawY
                        if (dx != 0f || dy != 0f) onMove(dx, dy)
                    }
                }
            }
            DragMode.MOVING -> {
                val dx = rawX - lastRawX
                val dy = rawY - lastRawY
                lastRawX = rawX
                lastRawY = rawY
                if (dx != 0f || dy != 0f) onMove(dx, dy)
            }
            DragMode.ADJUSTING -> {
                val delta = rotaryDeltaFraction(lastRelX, lastRelY, relX, relY)
                lastRelX = relX
                lastRelY = relY
                if (delta != 0f) onAdjustDelta(delta)
            }
        }
    }

    fun onUp(timeMs: Long) {
        if (!active) return
        active = false
        val finishedMode = dragMode
        dragMode = DragMode.NONE

        when (finishedMode) {
            DragMode.MOVING -> {
                pendingTapCount = 0
                onMoveFinished()
            }
            DragMode.ADJUSTING -> {
                pendingTapCount = 0
                onAdjustEnd()
            }
            DragMode.NONE -> {
                val duration = timeMs - downTimeMs
                if (!multiTouch && duration in 0L..tapTimeoutMs) {
                    when (activeTapIndex) {
                        1, 2 -> {
                            pendingTapCount = activeTapIndex
                            lastTapUpTimeMs = timeMs
                            lastTapRawX = downRawX
                            lastTapRawY = downRawY
                            scheduleTimeout(doubleTapTimeoutMs)
                        }
                        else -> {
                            pendingTapCount = 0
                            onTripleTap()
                        }
                    }
                } else {
                    pendingTapCount = 0
                }
            }
        }
    }

    fun onCancel() {
        cancelTimeout()
        pendingTapCount = 0
        if (!active) return
        active = false
        val canceledMode = dragMode
        dragMode = DragMode.NONE
        when (canceledMode) {
            DragMode.MOVING -> onMoveFinished()
            DragMode.ADJUSTING -> onAdjustEnd()
            DragMode.NONE -> Unit
        }
    }

    fun onTimeout() {
        firePendingTap()
    }

    private fun firePendingTap() {
        val count = pendingTapCount
        pendingTapCount = 0
        when (count) {
            1 -> onSingleTap()
            2 -> onDoubleTap()
        }
    }

    private fun rotaryDeltaFraction(px: Float, py: Float, x: Float, y: Float): Float {
        val prevDist = hypot(px, py)
        val curDist = hypot(x, y)
        val minDist = minOf(prevDist, curDist)
        if (minDist < 1e-3f) return 0f
        val cross = (px * y - py * x).toDouble()
        val dot = (px * x + py * y).toDouble()
        val deltaDegrees = Math.toDegrees(atan2(cross, dot)).toFloat()
        val trackRadius = trackRadiusProvider().coerceAtLeast(1f)
        val radiusScale = (minDist / trackRadius).coerceIn(0f, 1f)
        return (deltaDegrees * radiusScale) / DIAL_SWEEP_DEGREES
    }

    companion object {
        const val DIAL_SWEEP_DEGREES = 270f
    }
}
