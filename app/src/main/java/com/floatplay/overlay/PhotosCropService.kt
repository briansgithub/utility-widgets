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
    private var openingStraighten = false
    private var zoomed = false

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
            val opener = findStraightenOpener(root)
            if (!openingStraighten && opener?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                openingStraighten = true
                handler.postDelayed({
                    openingStraighten = false
                    nudgeRotation(clockwise)
                }, 350)
            } else {
                message(getString(R.string.photos_slider_missing))
            }
            return
        }
        openingStraighten = false
        val bounds = Rect()
        slider.getBoundsInScreen(bounds)
        if (bounds.width() < 2 || bounds.height() < 2) {
            message(getString(R.string.photos_slider_missing))
            return
        }
        val distance = chosenDrag(PhotosCrop.KEY_ROTATION_PX, PhotosCrop.rotationChoices)
        val sign = if (clockwise) -1f else 1f
        val y = bounds.exactCenterY()
        val inset = 8f
        val travel = sign * distance
        var startX = bounds.exactCenterX()
        val endX = (startX + travel).coerceIn(bounds.left + inset, bounds.right - inset)
        startX = endX - travel
        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        dispatch(listOf(path), 220L)
    }

    private fun nudge(xSign: Int, ySign: Int) {
        val root = activeApp() ?: return
        if (findSlider(root) == null) {
            zoomed = false
            val opener = findStraightenOpener(root)
            if (!openingStraighten && opener?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                openingStraighten = true
                handler.postDelayed({
                    openingStraighten = false
                    nudge(xSign, ySign)
                }, 500)
            } else if (!openingStraighten) {
                message(getString(R.string.photos_pan_missing))
            }
            return
        }
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
        var startX = bounds.exactCenterX()
        var startY = bounds.exactCenterY()
        panelScreenRect()?.let { panelBounds ->
            if (panelBounds.contains(startX.toInt(), startY.toInt())) {
                startX = (panelBounds.left - dp(80)).toFloat().coerceAtLeast((bounds.left + dp(48)).toFloat())
            }
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val distance = chosenDrag(PhotosCrop.KEY_TRANSLATION_PX, PhotosCrop.translationChoices)
        val pan = {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(startX + xSign * distance, startY + ySign * distance)
            }
            dispatch(listOf(path), 220L)
            Unit
        }
        if (zoomed) {
            pan()
            return
        }
        val half = PhotosCrop.minimumPinchPx(slop).toFloat()
        val left = Path().apply {
            moveTo(startX - dp(24), startY)
            lineTo(startX - dp(24) - half, startY)
        }
        val right = Path().apply {
            moveTo(startX + dp(24), startY)
            lineTo(startX + dp(24) + half, startY)
        }
        zoomed = dispatch(listOf(left, right), 280L, pan)
    }

    private fun chosenDrag(key: String, allowed: IntArray): Float {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val stored = toolPrefs?.getInt(key, 0) ?: 0
        return PhotosCrop.dragDistance(stored, allowed, slop).toFloat()
    }

    private fun dispatch(paths: List<Path>, durationMs: Long, then: (() -> Unit)? = null): Boolean {
        val builder = GestureDescription.Builder()
        for (path in paths) {
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
        }
        val accepted = dispatchGesture(
            builder.build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    then?.let { handler.post(it) }
                }
            },
            handler,
        )
        if (!accepted) message(getString(R.string.photos_gesture_rejected))
        return accepted
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

    private fun findPanTarget(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var labeled: AccessibilityNodeInfo? = null
        var largest: AccessibilityNodeInfo? = null
        var largestArea = 0
        val screen = Rect().also { root.getBoundsInScreen(it) }
        walk(root) { node ->
            if (labeled == null && PhotosCrop.isPanTarget(node.viewIdResourceName)) labeled = node
            val desc = node.contentDescription?.toString().orEmpty()
            if (labeled == null && (desc.contains("image view", ignoreCase = true) ||
                    desc.contains("compare to original", ignoreCase = true))
            ) {
                labeled = node
            }
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width() * bounds.height()
            val photoSized = bounds.width() in screen.width() * 4 / 10..screen.width() * 19 / 20 &&
                bounds.height() in screen.height() / 5..screen.height() * 4 / 5
            if (node.text.isNullOrBlank() && photoSized && area > largestArea) {
                largestArea = area
                largest = node
            }
        }
        return labeled ?: largest
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
