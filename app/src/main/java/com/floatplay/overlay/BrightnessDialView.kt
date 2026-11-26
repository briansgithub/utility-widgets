package com.floatplay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Floating rotary brightness dial.
 * - Drag: move the floating dial
 * - Single tap: toggle automatic brightness
 * - Double tap (no drag): toggle Extra Dim
 * - Double tap then drag: smoothly turn the dial to adjust brightness
 * - Triple tap: hide the dial
 */
class BrightnessDialView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val configuration = ViewConfiguration.get(context)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val trackRect = RectF()
    private val autoRect = RectF()
    private val arrowPath = Path()
    private val moonPath = Path()
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private var level = 0.5f
    private var turningLevel = 0.5f
    private var auto = false
    private var extraDim = false

    var onAdjustStart: () -> Unit = {}
    var onLevelChanged: (Float) -> Unit = {}
    var onAdjustEnd: () -> Unit = {}
    var onToggleExtraDim: () -> Unit = {}
    var onHide: () -> Unit = {}
    var onMove: (Float, Float) -> Unit = { _, _ -> }
    var onMoveFinished: () -> Unit = {}

    private val timeoutRunnable: Runnable = Runnable { gestures.onTimeout() }

    private val gestures: BrightnessDialGestures = BrightnessDialGestures(
        touchSlop = configuration.scaledTouchSlop.toFloat(),
        doubleTapSlop = configuration.scaledDoubleTapSlop.toFloat(),
        tapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
        trackRadiusProvider = {
            val size = minOf(width, height).toFloat().coerceAtLeast(dp(64))
            size * (30f / 96f)
        },
        onSingleTap = { performClick() },
        onDoubleTap = { onToggleExtraDim() },
        onTripleTap = { onHide() },
        onMove = { dx, dy -> onMove(dx, dy) },
        onMoveFinished = { onMoveFinished() },
        onAdjustStart = {
            turningLevel = level
            onAdjustStart()
        },
        onAdjustDelta = { deltaFraction ->
            val next = (turningLevel + deltaFraction).coerceIn(0f, 1f)
            if (next != turningLevel) {
                turningLevel = next
                level = next
                updateDescription()
                invalidate()
                onLevelChanged(next)
            }
        },
        onAdjustEnd = { onAdjustEnd() },
        scheduleTimeout = { delayMs ->
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.postDelayed(timeoutRunnable, delayMs)
        },
        cancelTimeout = { timeoutHandler.removeCallbacks(timeoutRunnable) },
    )

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        buildMoonPath()
        updateDescription()
    }

    private fun buildMoonPath() {
        val cx = dp(48)
        val cy = dp(76)
        val outer = Path().apply { addCircle(cx, cy, dp(5.2f), Path.Direction.CW) }
        val cutout = Path().apply {
            addCircle(cx + dp(2.7f), cy - dp(1.3f), dp(4.4f), Path.Direction.CW)
        }
        moonPath.reset()
        moonPath.op(outer, cutout, Path.Op.DIFFERENCE)
    }

    fun setState(position: Float, automatic: Boolean, extraDimActive: Boolean) {
        auto = automatic
        extraDim = extraDimActive
        if (!gestures.isAdjusting) {
            level = position.coerceIn(0f, 1f)
        }
        updateDescription()
        invalidate()
    }

    fun startAdjustingAt(position: Float) {
        val clamped = position.coerceIn(0f, 1f)
        level = clamped
        turningLevel = clamped
        auto = false
        updateDescription()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gestures.onCancel()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val scale = size / dp(96)
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(scale, scale)

        val cx = dp(48)
        val cy = dp(48)
        fillPaint.color = if (extraDim) Color.rgb(30, 36, 66) else Color.rgb(22, 57, 61)
        canvas.drawCircle(cx, cy, dp(48), fillPaint)

        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeWidth = dp(1.1f)
        strokePaint.color = if (extraDim) Color.rgb(155, 146, 209) else Color.rgb(119, 156, 157)
        for (i in 0 until 40) {
            val angle = Math.toRadians(i * 9.0)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            canvas.drawLine(
                cx + cosA * dp(41),
                cy + sinA * dp(41),
                cx + cosA * dp(44),
                cy + sinA * dp(44),
                strokePaint,
            )
        }

        val trackRadius = dp(30)
        trackRect.set(cx - trackRadius, cy - trackRadius, cx + trackRadius, cy + trackRadius)
        strokePaint.strokeWidth = dp(7)
        strokePaint.color = if (extraDim) Color.rgb(66, 70, 110) else Color.rgb(62, 92, 95)
        canvas.drawArc(trackRect, 135f, BrightnessDialGestures.DIAL_SWEEP_DEGREES, false, strokePaint)

        val accent = if (auto) Color.rgb(113, 212, 237) else Color.rgb(255, 211, 113)
        strokePaint.color = accent
        canvas.drawArc(
            trackRect,
            135f,
            level * BrightnessDialGestures.DIAL_SWEEP_DEGREES,
            false,
            strokePaint,
        )

        val thumbAngle = Math.toRadians(135.0 + level * BrightnessDialGestures.DIAL_SWEEP_DEGREES)
        fillPaint.color = accent
        canvas.drawCircle(
            cx + cos(thumbAngle).toFloat() * trackRadius,
            cy + sin(thumbAngle).toFloat() * trackRadius,
            dp(5.5f),
            fillPaint,
        )

        val iconScale = if (auto) 0.65f else 1f
        canvas.save()
        canvas.scale(iconScale, iconScale, cx, cy)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, dp(5), fillPaint)
        strokePaint.color = Color.WHITE
        strokePaint.strokeWidth = dp(1.8f)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            canvas.drawLine(
                cx + cosA * dp(8),
                cy + sinA * dp(8),
                cx + cosA * dp(11),
                cy + sinA * dp(11),
                strokePaint,
            )
        }

        if (auto) {
            val autoRadius = dp(17)
            autoRect.set(cx - autoRadius, cy - autoRadius, cx + autoRadius, cy + autoRadius)
            strokePaint.color = accent
            strokePaint.strokeWidth = dp(2.3f)
            canvas.drawArc(autoRect, 165f, 300f, false, strokePaint)
            arrowPath.reset()
            arrowPath.moveTo(cx - dp(16.5f), cy + dp(7.5f))
            arrowPath.lineTo(cx - dp(21f), cy + dp(1f))
            arrowPath.lineTo(cx - dp(12.5f), cy + dp(2.2f))
            arrowPath.close()
            fillPaint.color = accent
            canvas.drawPath(arrowPath, fillPaint)
        }
        canvas.restore()

        if (extraDim) {
            fillPaint.color = Color.rgb(196, 181, 253)
            canvas.drawPath(moonPath, fillPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val centerX = width / 2f
        val centerY = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                gestures.onDown(event.x, event.y, event.rawX, event.rawY, centerX, centerY, event.eventTime)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                gestures.onPointerDown()
            }
            MotionEvent.ACTION_MOVE -> {
                gestures.onMove(event.x, event.y, event.rawX, event.rawY, centerX, centerY)
            }
            MotionEvent.ACTION_UP -> {
                isPressed = false
                gestures.onUp(event.eventTime)
            }
            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                gestures.onCancel()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
            0f,
            100f,
            (level * 100f).roundToInt().toFloat(),
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                context.getString(R.string.brightness_toggle_auto),
            ),
        )
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        val next = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> (level + 0.05f).coerceAtMost(1f)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> (level - 0.05f).coerceAtLeast(0f)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                val value = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, -1f)
                    ?: -1f
                if (value < 0f) return false
                (value / 100f).coerceIn(0f, 1f)
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        level = next
        updateDescription()
        invalidate()
        onLevelChanged(next)
        return true
    }

    private fun updateDescription() {
        val percent = (level * 100f).roundToInt().coerceIn(0, 100)
        val autoMode = context.getString(if (auto) R.string.brightness_auto_on else R.string.brightness_auto_off)
        val dimMode = context.getString(if (extraDim) R.string.extra_dim_on else R.string.extra_dim_off)
        contentDescription = context.getString(R.string.brightness_dial_state, percent, autoMode, dimMode)
    }

    private fun dp(value: Number) = value.toFloat() * density
}
