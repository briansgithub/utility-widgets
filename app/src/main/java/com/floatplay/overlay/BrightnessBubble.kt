package com.floatplay.overlay

import android.content.Context
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.roundToInt

class BrightnessBubble(private val context: Context, private val windows: WindowManager) {
    private val prefs = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private var view: BrightnessDialView? = null
    private var observing = false
    private var errorShown = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    fun show() {
        if (view != null) return
        val size = dp(96)
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bounds = windows.currentWindowMetrics.bounds
            x = prefs.getInt("brightness_bubble_x", bounds.width() - size - dp(16))
                .coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
            y = prefs.getInt("brightness_bubble_y", bounds.height() / 2 + dp(108))
                .coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
        }
        val dial = BrightnessDialView(context).apply {
            setOnClickListener { toggleAutomatic() }
            onLevelChanged = { percent -> setLevel(percent) }
            onMove = { dx, dy ->
                val bounds = windows.currentWindowMetrics.bounds
                params.x = (params.x + dx).coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
                params.y = (params.y + dy).coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
                windows.updateViewLayout(this, params)
            }
            onMoveFinished = {
                prefs.edit().putInt("brightness_bubble_x", params.x)
                    .putInt("brightness_bubble_y", params.y).apply()
            }
        }
        try {
            windows.addView(dial, params)
            view = dial
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
            observing = true
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
            refresh()
        } catch (error: RuntimeException) {
            Log.e("FloatPlay", "Could not show brightness control", error)
            remove()
        }
    }

    private fun automatic(): Boolean = Settings.System.getInt(
        resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun brightness(): Int = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    private fun writeSetting(name: String, value: Int): Boolean = Settings.System.putInt(resolver, name, value)

    private fun updateSettings(change: () -> Boolean): Boolean {
        val success = try {
            Settings.System.canWrite(context) && change()
        } catch (error: RuntimeException) {
            Log.w("FloatPlay", "Could not change brightness", error)
            false
        }
        if (!success && !errorShown) {
            Toast.makeText(context, R.string.brightness_failed, Toast.LENGTH_SHORT).show()
        }
        errorShown = !success
        refresh()
        return success
    }

    private fun setLevel(percent: Int) {
        updateSettings {
            // Write the level before disabling automatic mode to avoid an old brightness flashing.
            writeSetting(Settings.System.SCREEN_BRIGHTNESS, (percent.coerceIn(0, 100) * 255f / 100).roundToInt()) &&
                writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        }
    }

    private fun toggleAutomatic() {
        if (updateSettings {
            writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (automatic()) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }) {
            Toast.makeText(context, if (automatic()) R.string.brightness_auto_on else R.string.brightness_auto_off,
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        view?.setState((brightness() * 100f / 255).roundToInt(), automatic())
    }

    fun remove() {
        if (observing) resolver.unregisterContentObserver(observer)
        observing = false
        view?.let { try { windows.removeView(it) } catch (_: RuntimeException) { } }
        view = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
