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

class BrightnessBubble(
    private val context: Context,
    private val windows: WindowManager,
    private val onHide: () -> Unit,
) {
    private val prefs = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private var view: BrightnessDialView? = null
    private var layout: WindowManager.LayoutParams? = null
    val isShowing: Boolean get() = view?.isAttachedToWindow == true
    private var observing = false
    private var errorShown = false
    private val maximumBrightness = BrightnessCalibration.maximumSetting().also {
        Log.i("FloatToolsBrightness", "Manual brightness maximum setting: $it")
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh() }
    }

    fun show() {
        val size = dp(ToolSettings.size(context, OverlayService.WIDGET_BRIGHTNESS))
        val current = view
        val existing = layout
        if (current != null && existing != null) {
            existing.width = size
            existing.height = size
            val bounds = windows.currentWindowMetrics.bounds
            existing.x = existing.x.coerceIn(0, (bounds.width() - size).coerceAtLeast(0))
            existing.y = existing.y.coerceIn(0, (bounds.height() - size).coerceAtLeast(0))
            windows.updateViewLayout(current, existing)
            return
        }
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
            onHide = this@BrightnessBubble.onHide
            onMove = { dx, dy ->
                val bounds = windows.currentWindowMetrics.bounds
                params.x = (params.x + dx).coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
                params.y = (params.y + dy).coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
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
            layout = params
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
            observing = true
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
            if (ExtraDim.available(context)) {
                resolver.registerContentObserver(Settings.Secure.getUriFor(ExtraDim.ACTIVATED), false, observer)
                resolver.registerContentObserver(Settings.Secure.getUriFor(ExtraDim.INTENSITY), false, observer)
            }
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
        val output = BrightnessRange.output(percent, ExtraDim.available(context), maximumBrightness)
        updateSettings {
            // Fade Extra Dim out before raising normal brightness. For dimming,
            // lower hardware brightness before activating the software dimmer.
            (output.dim > 0 || ExtraDim.set(context, 0)) &&
                writeSetting(Settings.System.SCREEN_BRIGHTNESS, output.brightness) &&
                writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) &&
                (output.dim == 0 || ExtraDim.set(context, output.dim))
        }
    }

    private fun toggleAutomatic() {
        if (updateSettings {
            val enabling = !automatic()
            (!enabling || ExtraDim.set(context, 0)) && writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (automatic()) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        }) {
            Toast.makeText(context, if (automatic()) R.string.brightness_auto_on else R.string.brightness_auto_off,
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        view?.setState(BrightnessRange.position(brightness(), ExtraDim.intensity(context), ExtraDim.available(context), maximumBrightness), automatic())
    }

    fun remove() {
        if (observing) resolver.unregisterContentObserver(observer)
        observing = false
        view?.let { try { windows.removeView(it) } catch (_: RuntimeException) { } }
        view = null
        layout = null
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
