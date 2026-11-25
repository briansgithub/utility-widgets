package com.floatplay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Drag to move, double-tap then turn to adjust brightness, or triple-tap to hide. */
class BrightnessDialView(context: Context) : View(context) {
    var onLevelChanged: (Int) -> Unit = {}
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var onMoveFinished: () -> Unit = {}
    var onHide: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 50
    private var auto = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var adjustingBrightness = false
    private var lastAngle: Double? = null
    private var turningLevel = 50f
    private val configuration = ViewConfiguration.get(context)
    private val tapTimeout = ViewConfiguration.getDoubleTapTimeout()
    private var secondTapUpTime: Long? = null
    private var thirdTap = false
    private var multiTouch = false
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (!moved) performClick()
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean = true
        override fun onDoubleTapEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) adjustingBrightness = true
            return true
        }
    }).apply { setIsLongpressEnabled(false) }

    init { isClickable = true; isFocusable = true }

    fun setState(percent: Int, automatic: Boolean) {
        level = if (isPressed && adjustingBrightness) turningLevel.roundToInt().coerceIn(0, 100)
            else percent.coerceIn(0, 100)
        auto = automatic
        contentDescription = context.getString(R.string.brightness_dial_state, level,
            context.getString(if (auto) R.string.brightness_auto_on else R.string.brightness_auto_off))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        // Draw in a fixed logical space so every detail scales with the overlay.
        canvas.scale(width / dp(96), height / dp(96))
        val cx = dp(48)
        val cy = dp(48)
        val radius = dp(48)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(22, 57, 61)
        canvas.drawCircle(cx, cy, radius - dp(1), paint)
        paint.color = Color.rgb(119, 156, 157)
        paint.strokeWidth = dp(2)
        // Small ticks reinforce the rotary control without a separate drag handle.
        for (tick in 0 until 40) {
            val angle = Math.toRadians(tick * 9.0)
            canvas.drawLine(cx + cos(angle).toFloat() * (radius - dp(7)),
                cy + sin(angle).toFloat() * (radius - dp(7)),
                cx + cos(angle).toFloat() * (radius - dp(4)),
                cy + sin(angle).toFloat() * (radius - dp(4)), paint)
        }
        val trackRadius = radius - dp(18)
        val track = RectF(cx - trackRadius, cy - trackRadius, cx + trackRadius, cy + trackRadius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(5)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(62, 92, 95)
        canvas.drawArc(track, 135f, 270f, false, paint)
        paint.color = if (auto) Color.rgb(113, 212, 237) else Color.rgb(255, 211, 113)
        if (level > 0) canvas.drawArc(track, 135f, level * 2.7f, false, paint)
        val angle = Math.toRadians(135.0 + level * 2.7)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx + cos(angle).toFloat() * trackRadius,
            cy + sin(angle).toFloat() * trackRadius, dp(4), paint)
        paint.color = Color.WHITE
        canvas.save()
        canvas.scale(0.65f, 0.65f, cx, cy)
        // Sun = manual; sun inside a circular arrow = automatic. No visible text.
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, dp(5), paint)
        paint.strokeWidth = dp(2)
        for (ray in 0 until 8) {
            val a = Math.toRadians(ray * 45.0)
            canvas.drawLine(cx + cos(a).toFloat() * dp(8), cy + sin(a).toFloat() * dp(8),
                cx + cos(a).toFloat() * dp(11), cy + sin(a).toFloat() * dp(11), paint)
        }
        if (auto) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.rgb(113, 212, 237)
            canvas.drawArc(RectF(cx - dp(17), cy - dp(17), cx + dp(17), cy + dp(17)),
                -60f, 300f, false, paint)
            paint.style = Paint.Style.FILL
            canvas.drawPath(Path().apply {
                moveTo(cx - (8.5f * resources.displayMetrics.density), cy - (14.7f * resources.displayMetrics.density))
                lineTo(cx - dp(14), cy - dp(14))
                lineTo(cx - dp(10), cy - dp(9))
                close()
            }, paint)
        }
        canvas.restore()
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x - width / 2f
        val y = event.y - height / 2f
        val distance = hypot(x, y)
        val angle = if (distance >= dp(12)) atan2(y.toDouble(), x.toDouble()) else null
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                thirdTap = secondTapUpTime?.let { secondUp ->
                    event.eventTime - secondUp in 0L..tapTimeout.toLong() &&
                        hypot(event.rawX - downX, event.rawY - downY) < configuration.scaledDoubleTapSlop
                } == true
                secondTapUpTime = null
                multiTouch = false
                downX = event.rawX
                downY = event.rawY
                moved = false
                adjustingBrightness = false
                lastAngle = angle
                turningLevel = level.toFloat()
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (hypot(dx, dy) > configuration.scaledTouchSlop) {
                    moved = true
                }
                if (moved && !adjustingBrightness) {
                    onMove(dx.roundToInt(), dy.roundToInt())
                    // Keep offsets relative to the last move because the window itself moved.
                    downX = event.rawX
                    downY = event.rawY
                } else if (moved) {
                    val previous = lastAngle
                    if (angle != null && previous != null) {
                        var delta = Math.toDegrees(angle - previous).toFloat()
                        if (delta > 180f) delta -= 360f
                        if (delta < -180f) delta += 360f
                        turningLevel = (turningLevel + delta / 2.7f).coerceIn(0f, 100f)
                        val next = turningLevel.roundToInt()
                        if (next != level || auto) onLevelChanged(next)
                    }
                    lastAngle = angle
                }
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                if (moved && !adjustingBrightness) onMoveFinished()
                lastAngle = null
                if (!moved && !multiTouch && event.eventTime - event.downTime <= tapTimeout) {
                    if (thirdTap) {
                        // This down began a new single tap in GestureDetector. Cancel its confirmation.
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        gestures.onTouchEvent(cancel)
                        cancel.recycle()
                        onHide()
                        return true
                    }
                    if (adjustingBrightness) secondTapUpTime = event.eventTime
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> multiTouch = true
            MotionEvent.ACTION_CANCEL -> {
                secondTapUpTime = null
                isPressed = false
                if (moved && !adjustingBrightness) onMoveFinished()
                lastAngle = null
            }
            else -> return false
        }
        gestures.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    @Suppress("DEPRECATION") // RangeInfo.obtain supports the app's older Android versions.
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, level.toFloat())
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.brightness_toggle_auto)))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val next = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> level + 5
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> level - 5
            android.R.id.accessibilityActionSetProgress -> {
                if (arguments?.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) != true) return false
                arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).roundToInt()
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        onLevelChanged(next.coerceIn(0, 100))
        return true
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}
