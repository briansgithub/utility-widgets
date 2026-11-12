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
    private var rotateParams: WindowManager.LayoutParams? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val controllerCallbacks = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private var dragging = false
    private var suppressClick = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    private val longPressRunnable = Runnable {
        if (!dragging && bubble != null) {
            suppressClick = true
            Toast.makeText(this, R.string.bubble_hidden, Toast.LENGTH_SHORT).show()
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
            stopSelf()
            return START_NOT_STICKY
        }
        if (!overlayAllowed()) {
            Log.w(TAG, "Overlay permission missing")
            stopSelf()
            return START_NOT_STICKY
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
        if (!anyWidgetEnabled(this)) stopSelf()
        return START_STICKY
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
            refreshIcon()
            return
        }
        val size = dp(BUBBLE_DP)
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
        view.setOnTouchListener(::onBubbleTouch)
        try {
            windowManager.addView(view, params)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not add the floating button", error)
            stopSelf()
            return
        }
        bubble = view
        refreshIcon()
    }

    private fun showRotateBubble() {
        if (rotateBubble != null) return
        val size = dp(BUBBLE_DP)
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
        const val KEY_PLAY_ENABLED = "play_widget_enabled"
        const val KEY_ROTATE_ENABLED = "rotate_widget_enabled"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val KEY_ROTATE_X = "rotate_bubble_x"
        private const val KEY_ROTATE_Y = "rotate_bubble_y"
        private const val BUBBLE_DP = 56
        const val ACTION_STOP = "com.floatplay.overlay.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }

        fun enabledKey(widget: String): String = if (widget == WIDGET_PLAY) KEY_PLAY_ENABLED else KEY_ROTATE_ENABLED

        fun anyWidgetEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PLAY_ENABLED, false) || prefs.getBoolean(KEY_ROTATE_ENABLED, false)
        }
    }
}
