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

/** Floating rotary dial that adjusts screen brightness, auto brightness, and Extra Dim. */
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
    private var adjusting = false
    private var lastAppliedBrightness = -1
    private var activeToast: Toast? = null
    private val maximumBrightness = BrightnessCalibration.maximumSetting().also {
        Log.i("FloatToolsBrightness", "Manual brightness maximum setting: $it")
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!adjusting) refresh()
        }
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
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
            onToggleExtraDim = { toggleExtraDim() }
            onAdjustStart = { beginAdjustment() }
            onLevelChanged = { position -> setLevel(position) }
            onAdjustEnd = { endAdjustment() }
            onHide = this@BrightnessBubble.onHide
            onMove = { dx, dy ->
                val bounds = windows.currentWindowMetrics.bounds
                params.x = (params.x + dx.roundToInt()).coerceIn(0, (bounds.width() - params.width).coerceAtLeast(0))
                params.y = (params.y + dy.roundToInt()).coerceIn(0, (bounds.height() - params.height).coerceAtLeast(0))
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
            }
            refresh()
        } catch (error: RuntimeException) {
            Log.e("FloatPlay", "Could not show brightness control", error)
            remove()
        }
    }

    private fun beginAdjustment() {
        adjusting = true
        val actual = brightness()
        lastAppliedBrightness = actual
        view?.startAdjustingAt(BrightnessRange.position(actual, maximumBrightness))
    }

    private fun endAdjustment() {
        adjusting = false
        lastAppliedBrightness = -1
        refresh()
    }

    private fun setLevel(position: Float) {
        val target = BrightnessRange.brightness(position, maximumBrightness)
        val wasAuto = automatic()
        if (adjusting && !wasAuto && target == lastAppliedBrightness) return

        val success = try {
            Settings.System.canWrite(context) &&
                (!wasAuto || writeSetting(
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )) &&
                writeSetting(Settings.System.SCREEN_BRIGHTNESS, target)
        } catch (error: RuntimeException) {
            Log.w("FloatPlay", "Could not change brightness", error)
            false
        }
        if (success) {
            errorShown = false
            lastAppliedBrightness = target
        } else {
            showErrorOnce()
        }
        if (!adjusting) refresh()
    }

    private fun toggleAutomatic() {
        val nextMode = if (automatic()) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        val success = try {
            Settings.System.canWrite(context) && writeSetting(Settings.System.SCREEN_BRIGHTNESS_MODE, nextMode)
        } catch (error: RuntimeException) {
            Log.w("FloatPlay", "Could not toggle automatic brightness", error)
            false
        }
        refresh()
        if (success) {
            errorShown = false
            showToast(if (automatic()) R.string.brightness_auto_on else R.string.brightness_auto_off)
        } else {
            showErrorOnce()
        }
    }

    private fun toggleExtraDim() {
        if (!ExtraDim.available(context)) {
            showToast(R.string.extra_dim_access_needed)
            return
        }
        val active = ExtraDim.toggle(context)
        refresh()
        if (active != null) {
            showToast(if (active) R.string.extra_dim_on else R.string.extra_dim_off)
        } else {
            showToast(R.string.extra_dim_failed)
        }
    }

    private fun refresh() {
        val actual = brightness()
        view?.setState(
            BrightnessRange.position(actual, maximumBrightness),
            automatic(),
            ExtraDim.isActive(context),
        )
    }

    fun remove() {
        adjusting = false
        lastAppliedBrightness = -1
        activeToast?.cancel()
        activeToast = null
        if (observing) resolver.unregisterContentObserver(observer)
        observing = false
        view?.let { try { windows.removeView(it) } catch (_: RuntimeException) { } }
        view = null
        layout = null
    }

    private fun showToast(messageRes: Int) {
        activeToast?.cancel()
        activeToast = Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun showErrorOnce() {
        if (!errorShown) {
            showToast(R.string.brightness_failed)
            errorShown = true
        }
    }

    private fun automatic(): Boolean = Settings.System.getInt(
        resolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    private fun brightness(): Int = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        .coerceIn(BrightnessRange.MINIMUM_BRIGHTNESS, maximumBrightness)

    private fun writeSetting(name: String, value: Int): Boolean = Settings.System.putInt(resolver, name, value)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
