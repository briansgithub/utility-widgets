package com.floatplay.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast
import android.util.Log
import kotlin.math.roundToInt

class PhotosCropService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: WindowManager? = null
    private var prefs: SharedPreferences? = null
    private var toolPrefs: SharedPreferences? = null
    private var panel: GridLayout? = null
    private var position: WindowManager.LayoutParams? = null
    private var lastWalk = 0L
    private var zoomed = false

    private val refresh = Runnable { updateVisibility(force = true) }
    private val probe = PhotosProbe(this, handler)

    override fun onServiceConnected() {
        manager = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        toolPrefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        toolPrefs?.registerOnSharedPreferenceChangeListener(this)
        probe.register()
        updateVisibility(force = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (toolPrefs == null) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastWalk < 180L) {
            handler.removeCallbacks(refresh)
            handler.postDelayed(refresh, 180L)
            return
        }
        updateVisibility(force = false)
    }

    override fun onInterrupt() {
        hidePanel()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        probe.unregister()
        hidePanel()
        toolPrefs?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == OverlayService.KEY_PHOTOS_ENABLED) updateVisibility(force = true)
    }

    private fun updateVisibility(force: Boolean) {
        lastWalk = android.os.SystemClock.uptimeMillis()
        val enabled = toolPrefs?.getBoolean(OverlayService.KEY_PHOTOS_ENABLED, false) == true
        if (!enabled) {
            hidePanel()
            return
        }
        val root = activeApp()
        if (root == null) return
        val show = PhotosCrop.PACKAGE.contentEquals(root.packageName) && cropActive(root)
        if (show) showPanel() else hidePanel()
        if (!force) return
    }

    private fun activeApp(): AccessibilityNodeInfo? {
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && window.isActive) {
                return window.root
            }
        }
        return rootInActiveWindow
    }

    private fun cropActive(root: AccessibilityNodeInfo): Boolean {
        val sliderIds = ArrayList<String>()
        val labels = ArrayList<String>()
        var hasRange = false
        walk(root) { node ->
            val id = node.viewIdResourceName
            if (id != null) sliderIds.add(id)
            node.text?.let { labels.add(it.toString()) }
            node.contentDescription?.let { labels.add(it.toString()) }
            if (node.rangeInfo != null) hasRange = true
        }
        return PhotosCrop.cropSession(sliderIds, labels, hasRange)
    }

    private fun showPanel() {
        if (panel != null) return
        val windowManager = manager ?: return
        val stored = prefs ?: return
        val color = ToolSettings.color(this, OverlayService.WIDGET_PHOTOS)
        val textColor = if (Color.luminance(color) > 0.55f) 0xFF102018.toInt() else Color.WHITE
        val size = dp(52)
        val gap = dp(6)
        val width = size * 2 + gap
        val height = size * 3 + gap * 2
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        val screen = windowManager.currentWindowMetrics.bounds
        params.x = stored.getInt("x", screen.width() - width - dp(12))
        params.y = stored.getInt("y", screen.height() / 3)
        position = params
        clamp()
        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 3
        }
        val actions = listOf(
            Action("CW", getString(R.string.photos_rotate_clockwise)) { press(Step.Rotate(clockwise = true)) },
            Action("CCW", getString(R.string.photos_rotate_counterclockwise)) { press(Step.Rotate(clockwise = false)) },
            Action("Up", getString(R.string.photos_translate_up)) { press(Step.Pan(0, -1)) },
            Action("Down", getString(R.string.photos_translate_down)) { press(Step.Pan(0, 1)) },
            Action("Left", getString(R.string.photos_translate_left)) { press(Step.Pan(-1, 0)) },
            Action("Right", getString(R.string.photos_translate_right)) { press(Step.Pan(1, 0)) },
        )
        actions.forEachIndexed { index, action ->
            val button = Button(this).apply {
                text = action.label
                contentDescription = action.description
                setTextColor(textColor)
                textSize = 13f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minHeight = 0
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener {
                    Log.i(PhotosProbe.TAG, "pad press ${action.label}")
                    action.run()
                }
            }
            attachDrag(button)
            val cell = GridLayout.LayoutParams(GridLayout.spec(index / 2), GridLayout.spec(index % 2))
            cell.width = size
            cell.height = size
            if (index % 2 == 1) cell.leftMargin = gap
            if (index >= 2) cell.topMargin = gap
            grid.addView(button, cell)
        }
        panel = grid
        windowManager.addView(grid, params)
    }

    private fun attachDrag(button: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var dragging = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = position ?: return false
                val windowManager = manager ?: return false
                val host = panel ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (gestureInFlight) interrupted = true
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (!dragging && (dx * dx + dy * dy) > slop * slop) dragging = true
                        if (dragging) {
                            params.x = startX + dx.roundToInt()
                            params.y = startY + dy.roundToInt()
                            clamp()
                            windowManager.updateViewLayout(host, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) {
                            prefs?.edit()?.putInt("x", params.x)?.putInt("y", params.y)?.apply()
                        } else {
                            view.performClick()
                        }
                        dragging = false
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // A drag still running in Photos cancels this touch; the tap was real, so count it.
                        if (gestureInFlight) interrupted = true
                        val short = event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()
                        if (!dragging && short && panel != null) view.performClick()
                        dragging = false
                    }
                }
                return true
            }
        })
    }

    /** One button press. Presses run one at a time, so a quick second tap never cuts the first drag short. */
    private sealed class Step {
        data class Rotate(val clockwise: Boolean) : Step()
        data class Pan(val xSign: Int, val ySign: Int, val count: Int = 1) : Step()
    }

    private val queue = ArrayDeque<Step>()
    private var running: Step? = null
    private var gestureInFlight = false
    private var interrupted = false
    private var retries = 0
    private var openAttempts = 0

    /**
     * Quick taps on one arrow are gathered and sent as one drag. A tap that lands while a drag is running is dropped
     * by Android and can void that drag, so nothing runs until the taps pause. Photos moves the photo by what a slow
     * drag goes past its dead zone, so n taps become one drag that is n steps longer than the dead zone.
     */
    private var pendingPan: Step.Pan? = null
    private val flushPan = Runnable { flushPending() }

    private fun press(step: Step) {
        if (step is Step.Pan) {
            val pending = pendingPan
            pendingPan = if (pending != null && pending.xSign == step.xSign && pending.ySign == step.ySign) {
                pending.copy(count = pending.count + 1)
            } else {
                flushPending()
                step
            }
            handler.removeCallbacks(flushPan)
            if ((pendingPan?.count ?: 0) >= MAX_BATCH) flushPending() else handler.postDelayed(flushPan, BATCH_MS)
            return
        }
        flushPending()
        enqueue(step)
    }

    private fun flushPending() {
        handler.removeCallbacks(flushPan)
        val pending = pendingPan ?: return
        pendingPan = null
        enqueue(pending)
    }

    private fun enqueue(step: Step) {
        if (queue.size >= MAX_QUEUED) return
        queue.addLast(step)
        pump()
    }

    private fun pump() {
        if (running != null) return
        val next = queue.removeFirstOrNull() ?: return
        running = next
        retries = 0
        openAttempts = 0
        perform(next)
    }

    private fun finish(delayMs: Long = SETTLE_MS) {
        handler.postDelayed({
            running = null
            pump()
        }, delayMs)
    }

    private fun clearQueue() {
        handler.removeCallbacks(flushPan)
        pendingPan = null
        queue.clear()
    }

    private fun steps(): CropSteps {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val base = PhotosCrop.steps(android.os.Build.DEVICE, slop, resources.displayMetrics.density)
        val stored = prefs ?: return base
        return CropSteps(
            rotationRad = stored.getFloat(KEY_ROTATION_RAD, base.rotationRad),
            rotationDragPx = stored.getInt(KEY_ROTATION_DRAG, base.rotationDragPx),
            panXPx = stored.getInt(KEY_PAN_X, base.panXPx),
            panYPx = stored.getInt(KEY_PAN_Y, base.panYPx),
            pinchPx = stored.getInt(KEY_PINCH, base.pinchPx),
        )
    }

    private fun perform(step: Step) {
        val root = activeApp()
        if (root == null) {
            finish(0)
            return
        }
        val slider = findSlider(root)
        if (slider == null) {
            zoomed = false
            val opener = findStraightenOpener(root)
            if (openAttempts < 2 && opener?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                openAttempts++
                handler.postDelayed({ perform(step) }, OPEN_STRAIGHTEN_MS)
            } else {
                message(getString(if (step is Step.Rotate) R.string.photos_slider_missing else R.string.photos_pan_missing))
                clearQueue()
                finish(0)
            }
            return
        }
        when (step) {
            is Step.Rotate -> rotate(slider, step.clockwise)
            is Step.Pan -> pan(slider, step)
        }
    }

    /** Sets the straighten value directly; no drag means nothing for a quick tap to interrupt. */
    private fun rotate(slider: AccessibilityNodeInfo, clockwise: Boolean) {
        val steps = steps()
        slider.refresh()
        val range = slider.rangeInfo
        if (range != null) {
            val target = PhotosCrop.nextAngle(range.current, range.min, range.max, clockwise, steps.rotationRad)
            if (target == null) {
                message(getString(R.string.photos_at_limit))
                finish(0)
                return
            }
            val args = android.os.Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, target)
            }
            if (slider.performAction(android.R.id.accessibilityActionSetProgress, args)) {
                confirmAngle(slider, range.current, target, clockwise, 0)
                return
            }
        }
        rotateByDrag(slider, clockwise)
    }

    /** Waits until Photos reports the new angle, so the next queued press starts from it; drags if it never does. */
    private fun confirmAngle(slider: AccessibilityNodeInfo, from: Float, target: Float, clockwise: Boolean, tries: Int) {
        slider.refresh()
        val now = slider.rangeInfo?.current
        if (now != null && Math.abs(now - target) < 1e-4f) {
            Log.i(PhotosProbe.TAG, "pad rotate $from -> $now")
            finish()
            return
        }
        if (tries >= CONFIRM_TRIES) {
            Log.i(PhotosProbe.TAG, "pad rotate unconfirmed $from -> $now, dragging")
            if (now == null || Math.abs(now - from) < 1e-4f) rotateByDrag(slider, clockwise) else finish()
            return
        }
        handler.postDelayed({ confirmAngle(slider, from, target, clockwise, tries + 1) }, CONFIRM_MS)
    }

    private fun rotateByDrag(slider: AccessibilityNodeInfo, clockwise: Boolean) {
        val steps = steps()
        val bounds = Rect().also { slider.getBoundsInScreen(it) }
        if (bounds.width() < 2 || bounds.height() < 2) {
            message(getString(R.string.photos_slider_missing))
            finish(0)
            return
        }
        val travel = PhotosCrop.rotationDragX(clockwise, steps.rotationDragPx).toFloat()
        val y = bounds.exactCenterY()
        val inset = 8f
        val endX = (bounds.exactCenterX() + travel).coerceIn(bounds.left + inset, bounds.right - inset)
        val startX = endX - travel
        gesture(single(listOf(floatArrayOf(startX, y, endX, y)), DRAG_MS)) { finish() }
    }

    private fun pan(slider: AccessibilityNodeInfo, step: Step.Pan) {
        val steps = steps()
        val area = photoArea(slider)
        if (area == null) {
            message(getString(R.string.photos_pan_missing))
            clearQueue()
            finish(0)
            return
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val dx = step.xSign * PhotosCrop.panDragPx(steps.panXPx, slop, step.count).toFloat()
        val dy = step.ySign * PhotosCrop.panDragPx(steps.panYPx, slop, step.count).toFloat()
        var startX = area.exactCenterX() - dx / 2
        val startY = area.exactCenterY() - dy / 2
        panelScreenRect()?.let { pad ->
            val reach = Rect(
                minOf(startX, startX + dx).toInt() - dp(16),
                minOf(startY, startY + dy).toInt() - dp(16),
                maxOf(startX, startX + dx).toInt() + dp(16),
                maxOf(startY, startY + dy).toInt() + dp(16),
            )
            if (Rect.intersects(reach, pad)) {
                startX = if (pad.centerX() > area.centerX()) {
                    (pad.left - dp(48)).toFloat().coerceAtLeast(area.left + dp(24).toFloat())
                } else {
                    (pad.right + dp(48)).toFloat().coerceAtMost(area.right - dp(24).toFloat())
                }
            }
        }
        Log.i(PhotosProbe.TAG, "pad pan from $startX,$startY by $dx,$dy area=$area pad=${panelScreenRect()} zoomed=$zoomed")
        val drag = {
            gesture(panPhases(startX, startY, dx, dy)) { finish() }
        }
        if (zoomed) {
            drag()
            return
        }
        // A photo that exactly fills the crop has nowhere to slide; the smallest pinch gives it room.
        val half = steps.pinchPx.toFloat()
        val cx = area.exactCenterX()
        val cy = area.exactCenterY()
        val spread = dp(24).toFloat()
        gesture(
            single(
                listOf(
                    floatArrayOf(cx - spread, cy, cx - spread - half, cy),
                    floatArrayOf(cx + spread, cy, cx + spread + half, cy),
                ),
                PINCH_MS,
            ),
        ) {
            zoomed = true
            handler.postDelayed({ drag() }, SETTLE_MS)
        }
    }

    /**
     * Runs one gesture and calls [then] once Photos has had it in full.
     * If the pad is touched while it runs, Android cancels the injected drag, so it is run again.
     */
    private fun gesture(phases: List<List<Triple<FloatArray, FloatArray, Long>>>, then: () -> Unit) {
        interrupted = false
        gestureInFlight = true
        panel?.alpha = BUSY_ALPHA
        dispatchPhases(phases, handler) { result ->
            gestureInFlight = false
            panel?.alpha = 1f
            Log.i(PhotosProbe.TAG, "pad gesture $result interrupted=$interrupted")
            when {
                result == "rejected" -> {
                    message(getString(R.string.photos_gesture_rejected))
                    clearQueue()
                    finish(0)
                }
                (interrupted || result == "cancelled") && retries < MAX_RETRIES -> {
                    retries++
                    handler.postDelayed({ gesture(phases, then) }, SETTLE_MS)
                }
                else -> then()
            }
        }
    }

    private fun single(strokes: List<FloatArray>, durationMs: Long) =
        listOf(strokes.map { Triple(floatArrayOf(it[0], it[1]), floatArrayOf(it[2], it[3]), durationMs) })

    /**
     * Photos drops a quick small drag, or moves it by a varying amount, because a whole quick drag lands in one or two
     * frames. So the finger runs quickly to just short of the touch slop, then crosses it slowly: the photo then moves
     * by exactly what the drag goes past the slop.
     */
    private fun panPhases(x: Float, y: Float, dx: Float, dy: Float): List<List<Triple<FloatArray, FloatArray, Long>>> {
        val length = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val approach = PhotosCrop.panApproachPx(slop, length.roundToInt()).toFloat()
        val f = if (length > 0f) approach / length else 0f
        val mid = floatArrayOf(x + dx * f, y + dy * f)
        val end = floatArrayOf(x + dx, y + dy)
        return listOf(
            listOf(Triple(floatArrayOf(x, y), mid, APPROACH_MS)),
            listOf(Triple(mid, end, PhotosCrop.panFinishMs(length.roundToInt() - approach.roundToInt()))),
        )
    }

    /** The photo sits between the crop tool row at the top and the straighten bar. */
    private fun photoArea(slider: AccessibilityNodeInfo): Rect? {
        val root = activeApp() ?: return null
        val screen = Rect().also { root.getBoundsInScreen(it) }
        val bar = Rect().also { slider.getBoundsInScreen(it) }
        var top = screen.top + screen.height() / 6
        walk(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty()
            if (desc.equals("Crop to aspect ratio", ignoreCase = true) || PhotosCrop.isCropChrome(desc)) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (bounds.bottom < bar.top) top = maxOf(top, bounds.bottom)
            }
        }
        val area = Rect(screen.left, top, screen.right, bar.top)
        area.inset(dp(32), dp(32))
        return if (area.width() < dp(96) || area.height() < dp(96)) null else area
    }

    private fun findSlider(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (match != null) return@walk
            val label = "${node.contentDescription ?: ""} ${node.text ?: ""}"
            val seekBar = node.className?.contains("SeekBar", ignoreCase = true) == true
            if (PhotosCrop.isRotationSlider(node.viewIdResourceName) ||
                (seekBar && label.contains("straighten", ignoreCase = true)) ||
                (node.rangeInfo != null && (label.contains("straighten", ignoreCase = true) || label.contains("rotate", ignoreCase = true)))
            ) {
                match = node
            }
        }
        return match
    }

    private fun findStraightenOpener(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var described: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (described != null) return@walk
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            val seekBar = node.className?.contains("SeekBar", ignoreCase = true) == true
            if (!seekBar && node.rangeInfo == null &&
                (desc.equals("Straighten", ignoreCase = true) || text.equals("Straighten", ignoreCase = true))
            ) {
                described = node
            }
        }
        val target = described ?: return null
        if (target.isClickable) return target
        val marker = Rect().also { target.getBoundsInScreen(it) }
        var button: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (button == null && node.isClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.contains(marker.centerX(), marker.centerY())) button = node
            }
        }
        return button ?: target
    }

    internal fun probeRoot(): AccessibilityNodeInfo? = activeApp()

    internal fun probeSlider(root: AccessibilityNodeInfo): AccessibilityNodeInfo? = findSlider(root)

    internal fun runPhases(phases: List<List<Triple<FloatArray, FloatArray, Long>>>, done: (String) -> Unit) =
        dispatchPhases(phases, handler, done)

    internal fun probeButton(name: String, done: (String) -> Unit) {
        when (name) {
            "CW" -> press(Step.Rotate(clockwise = true))
            "CCW" -> press(Step.Rotate(clockwise = false))
            "Up" -> press(Step.Pan(0, -1))
            "Down" -> press(Step.Pan(0, 1))
            "Left" -> press(Step.Pan(-1, 0))
            "Right" -> press(Step.Pan(1, 0))
            else -> return done("unknown")
        }
        done("queued")
    }

    internal fun probeSteps(): CropSteps = steps()

    internal fun probeStepPrefs(): SharedPreferences? = prefs

    internal fun probeResetZoom() {
        zoomed = false
    }

    private fun panelScreenRect(): Rect? {
        val host = panel ?: return null
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + host.width, location[1] + host.height)
    }

    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        var index = 0
        while (index < queue.size && index < 800) {
            val node = queue[index++]
            if (node.isVisibleToUser) visit(node)
            val childCount = node.childCount
            var child = 0
            while (child < childCount && queue.size < 800) {
                node.getChild(child)?.let { queue.add(it) }
                child++
            }
        }
    }

    private fun clamp() {
        val params = position ?: return
        val windowManager = manager ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        params.x = params.x.coerceIn(dp(8), (bounds.width() - params.width - dp(8)).coerceAtLeast(dp(8)))
        params.y = params.y.coerceIn(dp(32), (bounds.height() - params.height - dp(56)).coerceAtLeast(dp(32)))
    }

    private fun hidePanel() {
        zoomed = false
        clearQueue()
        val host = panel ?: return
        try {
            manager?.removeView(host)
        } catch (_: RuntimeException) {
        }
        panel = null
    }

    private fun message(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private class Action(val label: String, val description: String, val run: () -> Unit)

    companion object {
        private const val PREFS = "photos_crop"
        const val KEY_ROTATION_RAD = "step_rotation_rad"
        const val KEY_ROTATION_DRAG = "step_rotation_drag_px"
        const val KEY_PAN_X = "step_pan_x_px"
        const val KEY_PAN_Y = "step_pan_y_px"
        const val KEY_PINCH = "step_pinch_px"
        private const val DRAG_MS = 220L
        private const val APPROACH_MS = 50L
        private const val BUSY_ALPHA = 0.55f
        private const val PINCH_MS = 280L
        private const val SETTLE_MS = 60L
        private const val OPEN_STRAIGHTEN_MS = 450L
        private const val MAX_RETRIES = 2
        private const val MAX_QUEUED = 12
        private const val MAX_BATCH = 20
        private const val BATCH_MS = 250L
        private const val CONFIRM_MS = 30L
        private const val CONFIRM_TRIES = 12

        fun accessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, PhotosCropService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(-1).any { info ->
                expected == ComponentName.unflattenFromString(info.id)
            }
        }
    }
}
