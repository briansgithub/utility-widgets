package com.floatplay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var bubble: View? = null
    private var iconView: ImageView? = null
    private var rotateBubble: View? = null
    private var brightnessBubble: BrightnessBubble? = null
    private var rotateParams: WindowManager.LayoutParams? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val controllerCallbacks = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private var tapCount = 0
    private val resolveTaps = Runnable {
        val tool = ToolSettings.tools.firstOrNull {
            ToolSettings.taps(this, it) == tapCount
        }
        tapCount = 0
        if (tool == null) Toast.makeText(this, R.string.unassigned_taps, Toast.LENGTH_SHORT).show()
        else {
            val prefs = ToolSettings.prefs(this)
            val key = enabledKey(tool)
            val enabled = !prefs.getBoolean(key, false)
            if (enabled && tool == WIDGET_TABS && !TabControls.accessibilityEnabled(this)) {
                Toast.makeText(this, R.string.tab_accessibility_needed, Toast.LENGTH_LONG).show()
            } else if (enabled && tool == WIDGET_PHOTOS && !PhotosCropService.accessibilityEnabled(this)) {
                Toast.makeText(this, R.string.photos_accessibility_needed, Toast.LENGTH_LONG).show()
            } else if (enabled && tool in listOf(WIDGET_ROTATE, WIDGET_BRIGHTNESS) && !Settings.System.canWrite(this)) {
                Toast.makeText(this, R.string.rotation_permission_needed, Toast.LENGTH_LONG).show()
            } else prefs.edit().putBoolean(key, enabled).apply()
        }
        syncTools()
    }
    // Session callbacks provide immediate updates. Also sample audio output for
    // players controlled by media keys or when notification access is unavailable.
    private val playbackRefresh = object : Runnable {
        override fun run() {
            if (bubble == null) return
            refreshIcon()
            mainHandler.postDelayed(this, 750)
        }
    }
    private var dragging = false
    private var suppressClick = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    private val longPressRunnable = Runnable {
        if (!dragging && (bubble != null || rotateBubble != null || brightnessBubble != null)) {
            suppressClick = true
            Toast.makeText(this, R.string.bubble_hidden, Toast.LENGTH_SHORT).show()
            ToolSettings.prefs(this).edit().putBoolean(KEY_PLAY_ENABLED, false)
                .putBoolean(KEY_ROTATE_ENABLED, false).putBoolean(KEY_BRIGHTNESS_ENABLED, false)
                .putBoolean(KEY_TABS_ENABLED, false).putBoolean(KEY_PHOTOS_ENABLED, false).apply()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WindowManager::class.java)
        startInForeground()
        try {
            NotificationListenerService.requestRebind(MediaToggle.listenerComponent(this))
        } catch (error: RuntimeException) {
            Log.w(TAG, "Notification listener rebind failed", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ToolSettings.prefs(this).edit().putBoolean(KEY_PLAY_ENABLED, false)
                .putBoolean(KEY_ROTATE_ENABLED, false).putBoolean(KEY_BRIGHTNESS_ENABLED, false)
                .putBoolean(KEY_TABS_ENABLED, false).putBoolean(KEY_PHOTOS_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!overlayAllowed()) {
            Log.w(TAG, "Overlay permission missing")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_WIDGET_TAP) {
            tapCount++
            mainHandler.removeCallbacks(resolveTaps)
            mainHandler.postDelayed(resolveTaps, ToolSettings.TAP_WINDOW_MS)
            return START_NOT_STICKY
        }
        syncTools()
        return START_STICKY
    }

    private fun syncTools() {
        if (!overlayAllowed()) {
            stopSelf()
            return
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PLAY_ENABLED, false)) {
            showBubble()
            watchSessions()
        } else {
            removeBubble()
        }
        if (prefs.getBoolean(KEY_ROTATE_ENABLED, false) && Settings.System.canWrite(this)) showRotateBubble()
        else removeRotateBubble()
        if (prefs.getBoolean(KEY_BRIGHTNESS_ENABLED, false) && Settings.System.canWrite(this)) {
            if (brightnessBubble == null) brightnessBubble = BrightnessBubble(this, windowManager)
            brightnessBubble?.show()
        } else {
            brightnessBubble?.remove()
            brightnessBubble = null
        }
        val tabsEnabled = prefs.getBoolean(KEY_TABS_ENABLED, false) && TabControls.accessibilityEnabled(this)
        val photosEnabled = prefs.getBoolean(KEY_PHOTOS_ENABLED, false) && PhotosCropService.accessibilityEnabled(this)
        if (bubble == null && rotateBubble == null && brightnessBubble == null &&
            !tabsEnabled && !photosEnabled && tapCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        sessionsListener?.let { listener ->
            try {
                MediaToggle.sessionManager(this)?.removeOnActiveSessionsChangedListener(listener)
            } catch (_: RuntimeException) {
            }
        }
        sessionsListener = null
        clearControllerCallbacks()
        removeBubble()
        removeRotateBubble()
        brightnessBubble?.remove()
        brightnessBubble = null
        isRunning = false
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val hide = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_media)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(hide)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBubble() {
        if (bubble != null) {
            applyAppearance(bubble!!, params, WIDGET_PLAY)
            refreshIcon()
            return
        }
        val size = dp(ToolSettings.size(this, WIDGET_PLAY))
        params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        placeBubble(size)

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        iconView = view.findViewById(R.id.icon)
        applyAppearance(view, params, WIDGET_PLAY, false)
        view.setOnTouchListener(::onBubbleTouch)
        try {
            windowManager.addView(view, params)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not add the floating button", error)
            stopSelf()
            return
        }
        bubble = view
        mainHandler.removeCallbacks(playbackRefresh)
        mainHandler.post(playbackRefresh)
        refreshIcon()
    }

    private fun showRotateBubble() {
        if (rotateBubble != null) {
            applyAppearance(rotateBubble!!, rotateParams!!, WIDGET_ROTATE)
            return
        }
        val size = dp(ToolSettings.size(this, WIDGET_ROTATE))
        val rotateParams = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bounds = windowManager.currentWindowMetrics.bounds
            x = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_ROTATE_X, bounds.width() - size - dp(16))
                .coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
            y = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_ROTATE_Y, bounds.height() / 2 + dp(40))
                .coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
        }
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        val icon = view.findViewById<ImageView>(R.id.icon)
        icon.setImageResource(R.drawable.ic_rotate)
        applyAppearance(view, rotateParams, WIDGET_ROTATE, false)
        view.contentDescription = getString(R.string.toggle_auto_rotate)
        view.setOnTouchListener { touchedView, event -> onRotateBubbleTouch(touchedView, event) }
        try {
            windowManager.addView(view, rotateParams)
            rotateBubble = view
            this.rotateParams = rotateParams
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not add the auto-rotate button", error)
        }
    }

    private fun applyAppearance(view: View, layout: WindowManager.LayoutParams, tool: String, attached: Boolean = true) {
        val size = dp(ToolSettings.size(this, tool))
        val color = ToolSettings.color(this, tool)
        layout.width = size
        layout.height = size
        val bounds = windowManager.currentWindowMetrics.bounds
        layout.x = layout.x.coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
        layout.y = layout.y.coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        view.findViewById<ImageView>(R.id.icon).apply {
            layoutParams = layoutParams.apply { width = size / 2; height = size / 2 }
            setColorFilter(if (Color.luminance(color) > 0.4f) Color.BLACK else Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (attached) windowManager.updateViewLayout(view, layout)
    }

    private fun onRotateBubbleTouch(view: View, event: MotionEvent): Boolean {
        val params = rotateParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                suppressClick = false
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).roundToInt()
                val dy = (event.rawY - downRawY).roundToInt()
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(KEY_ROTATE_X, params.x).putInt(KEY_ROTATE_Y, params.y).apply()
                } else if (!suppressClick) {
                    pulse(view)
                    if (!Settings.System.canWrite(this)) {
                        Toast.makeText(this, R.string.rotation_permission_needed, Toast.LENGTH_SHORT).show()
                    } else {
                        val enabled = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                        Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 0 else 1)
                        Toast.makeText(this, if (enabled) R.string.auto_rotate_off else R.string.auto_rotate_on, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> mainHandler.removeCallbacks(longPressRunnable)
        }
        return true
    }

    private fun removeRotateBubble() {
        rotateBubble?.let { view ->
            try { windowManager.removeView(view) } catch (_: RuntimeException) { }
        }
        rotateBubble = null
        rotateParams = null
    }

    private fun placeBubble(size: Int) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.contains(KEY_X)) {
            params.x = bounds.width() - size - dp(16)
            params.y = bounds.height() / 2 - size / 2
        } else {
            params.x = prefs.getInt(KEY_X, 0)
            params.y = prefs.getInt(KEY_Y, 0)
        }
        params.x = params.x.coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
    }

    private fun onBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                suppressClick = false
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).roundToInt()
                val dy = (event.rawY - downRawY).roundToInt()
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    savePosition()
                } else if (bubble != null && !suppressClick) {
                    pulse(view)
                    val sent = MediaToggle.toggle(this)
                    if (!sent) {
                        Toast.makeText(this, R.string.nothing_playing, Toast.LENGTH_SHORT).show()
                    }
                    mainHandler.postDelayed({ refreshIcon() }, 350)
                }
            }
            MotionEvent.ACTION_CANCEL -> mainHandler.removeCallbacks(longPressRunnable)
        }
        return true
    }

    private fun watchSessions() {
        val manager = MediaToggle.sessionManager(this) ?: return
        if (sessionsListener == null) {
            val listener = MediaSessionManager.OnActiveSessionsChangedListener {
                bindControllers(it.orEmpty())
            }
            try {
                manager.addOnActiveSessionsChangedListener(
                    listener,
                    MediaToggle.listenerComponent(this),
                    mainHandler,
                )
                sessionsListener = listener
            } catch (error: SecurityException) {
                Log.w(TAG, "Cannot watch media sessions yet", error)
            }
        }
        bindControllers(MediaToggle.activeSessions(this))
    }

    private fun bindControllers(sessions: List<MediaController>) {
        clearControllerCallbacks()
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                refreshIcon()
            }
        }
        sessions.forEach { controller ->
            controller.registerCallback(callback, mainHandler)
            controllerCallbacks.add(controller to callback)
        }
        refreshIcon()
    }

    private fun clearControllerCallbacks() {
        controllerCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: RuntimeException) {
            }
        }
        controllerCallbacks.clear()
    }

    private fun refreshIcon() {
        val playing = MediaToggle.isPlaying(this)
        iconView?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        bubble?.contentDescription = getString(
            if (playing) R.string.pause_media else R.string.play_media,
        )
    }

    private fun savePosition() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_X, params.x)
            .putInt(KEY_Y, params.y)
            .apply()
    }

    private fun removeBubble() {
        mainHandler.removeCallbacks(playbackRefresh)
        bubble?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: RuntimeException) {
            }
        }
        bubble = null
        iconView = null
    }

    private fun pulse(view: View) {
        view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "FloatPlay"
        private const val CHANNEL_ID = "floatplay_overlay"
        private const val NOTIFICATION_ID = 7
        const val PREFS = "floatplay"
        const val WIDGET_PLAY = "play"
        const val WIDGET_ROTATE = "rotate"
        const val WIDGET_BRIGHTNESS = "brightness"
        const val WIDGET_TABS = "tabs"
        const val WIDGET_PHOTOS = "photos"
        const val KEY_PLAY_ENABLED = "play_widget_enabled"
        const val KEY_ROTATE_ENABLED = "rotate_widget_enabled"
        const val KEY_BRIGHTNESS_ENABLED = "brightness_widget_enabled"
        const val KEY_TABS_ENABLED = "tabs_widget_enabled"
        const val KEY_PHOTOS_ENABLED = "photos_widget_enabled"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val KEY_ROTATE_X = "rotate_bubble_x"
        private const val KEY_ROTATE_Y = "rotate_bubble_y"
        const val ACTION_WIDGET_TAP = "com.floatplay.overlay.HOME_WIDGET_TAP"
        const val ACTION_STOP = "com.floatplay.overlay.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun enabledKey(widget: String): String = when (widget) {
            WIDGET_PLAY -> KEY_PLAY_ENABLED
            WIDGET_ROTATE -> KEY_ROTATE_ENABLED
            WIDGET_BRIGHTNESS -> KEY_BRIGHTNESS_ENABLED
            WIDGET_TABS -> KEY_TABS_ENABLED
            WIDGET_PHOTOS -> KEY_PHOTOS_ENABLED
            else -> error("Unknown widget: $widget")
        }

        fun anyWidgetEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PLAY_ENABLED, false) || prefs.getBoolean(KEY_ROTATE_ENABLED, false) ||
                prefs.getBoolean(KEY_BRIGHTNESS_ENABLED, false) || prefs.getBoolean(KEY_TABS_ENABLED, false) ||
                prefs.getBoolean(KEY_PHOTOS_ENABLED, false)
        }
    }
}
