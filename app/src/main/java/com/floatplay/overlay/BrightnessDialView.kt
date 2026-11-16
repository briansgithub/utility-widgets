package com.floatplay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/** Turn to adjust brightness; double-tap and drag to move the overlay. */
class BrightnessDialView(context: Context) : View(context) {
    var onLevelChanged: (Int) -> Unit = {}
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    var onMoveFinished: () -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 50
    private var auto = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var movingWidget = false
    private var lastAngle: Double? = null
    private var turningLevel = 50f
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            performClick()
            return true
        }
        override fun onDoubleTap(event: MotionEvent): Boolean = true
        override fun onDoubleTapEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) movingWidget = true
            return true
        }
    }).apply { setIsLongpressEnabled(false) }

    init { isClickable = true; isFocusable = true }

    fun setState(percent: Int, automatic: Boolean) {
        level = percent.coerceIn(0, 100)
        auto = automatic
        contentDescription = context.getString(R.string.brightness_dial_state, level,
            context.getString(if (auto) R.string.brightness_auto_on else R.string.brightness_auto_off))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f
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
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(17)
        paint.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.brightness_percent, level), cx, cy + dp(2), paint)
        paint.isFakeBoldText = false
        paint.textSize = dp(8)
        canvas.drawText(context.getString(if (auto) R.string.brightness_auto_on else R.string.brightness_auto_off),
            cx, cy + dp(14), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x - width / 2f
        val y = event.y - height / 2f
        val distance = hypot(x, y)
        val angle = if (distance >= dp(12)) atan2(y.toDouble(), x.toDouble()) else null
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                moved = false
                movingWidget = false
                lastAngle = angle
                turningLevel = level.toFloat()
                isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (hypot(dx, dy) > ViewConfiguration.get(context).scaledTouchSlop) moved = true
                if (moved && movingWidget) {
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
                if (moved && movingWidget) onMoveFinished()
                lastAngle = null
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                if (moved && movingWidget) onMoveFinished()
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
