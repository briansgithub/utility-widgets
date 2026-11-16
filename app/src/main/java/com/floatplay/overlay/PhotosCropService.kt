package com.floatplay.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import kotlin.math.roundToInt

class PhotosCropService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: WindowManager? = null
    private var prefs: SharedPreferences? = null
    private var toolPrefs: SharedPreferences? = null
    private var panel: GridLayout? = null
    private var position: WindowManager.LayoutParams? = null
    private var lastWalk = 0L

    private val refresh = Runnable { updateVisibility(force = true) }

    override fun onServiceConnected() {
        manager = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        toolPrefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        toolPrefs?.registerOnSharedPreferenceChangeListener(this)
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
            Action("CW", getString(R.string.photos_rotate_clockwise)) { nudgeRotation(clockwise = true) },
            Action("CCW", getString(R.string.photos_rotate_counterclockwise)) { nudgeRotation(clockwise = false) },
            Action("Up", getString(R.string.photos_translate_up)) { nudge(0, -1) },
            Action("Down", getString(R.string.photos_translate_down)) { nudge(0, 1) },
            Action("Left", getString(R.string.photos_translate_left)) { nudge(-1, 0) },
            Action("Right", getString(R.string.photos_translate_right)) { nudge(1, 0) },
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
                setOnClickListener { action.run() }
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
                    MotionEvent.ACTION_CANCEL -> dragging = false
                }
                return true
            }
        })
    }

    private fun nudgeRotation(clockwise: Boolean) {
        val root = activeApp() ?: return
        val slider = findSlider(root)
        if (slider == null) {
            message(getString(R.string.photos_slider_missing))
            return
        }
        val range = slider.rangeInfo
        if (range != null) {
            val step = PhotosCrop.minimumRotationStep(range.min, range.max)
            val next = (range.current + if (clockwise) step else -step).coerceIn(range.min, range.max)
            if (next == range.current) {
                message(getString(R.string.photos_at_limit))
                return
            }
            val args = Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, next)
            }
            val setProgress = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
            if (slider.performAction(setProgress, args)) return
        }
        val scroll = if (clockwise) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (!slider.performAction(scroll)) message(getString(R.string.photos_slider_missing))
    }

    private fun nudge(xSign: Int, ySign: Int) {
        val root = activeApp() ?: return
        val target = findPanTarget(root)
        if (target == null) {
            message(getString(R.string.photos_pan_missing))
            return
        }
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() < dp(48) || bounds.height() < dp(48)) {
            message(getString(R.string.photos_pan_missing))
            return
        }
        val distance = (ViewConfiguration.get(this).scaledTouchSlop + 1).toFloat()
        val startX = bounds.exactCenterX()
        val startY = bounds.exactCenterY()
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX + xSign * distance, startY + ySign * distance)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(gesture, null, handler)
        if (!accepted) message(getString(R.string.photos_gesture_rejected))
    }

    private fun findSlider(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (match == null && PhotosCrop.isRotationSlider(node.viewIdResourceName)) match = node
        }
        if (match != null) return match
        walk(root) { node ->
            if (match == null && node.rangeInfo != null && node.isVisibleToUser) {
                val label = "${node.contentDescription ?: ""} ${node.text ?: ""}"
                if (label.contains("straighten", ignoreCase = true) || label.contains("rotate", ignoreCase = true)) {
                    match = node
                }
            }
        }
        return match
    }

    private fun findPanTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (match == null && PhotosCrop.isPanTarget(node.viewIdResourceName)) match = node
        }
        return match
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

        fun accessibilityEnabled(context: Context): Boolean {
            val expected = ComponentName(context, PhotosCropService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(-1).any { info ->
                expected == ComponentName.unflattenFromString(info.id)
            }
        }
    }
}
