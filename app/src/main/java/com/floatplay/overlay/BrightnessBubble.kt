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
    private var lastApplied: DimSetting? = null
    private val dimLayer = DimLayer(context, windows)
    private var dimmedLevel = -1
    private var activeToast: Toast? = null
    private val curve = DimCurve(BrightnessCalibration.maximumSetting().also {
        Log.i("FloatToolsBrightness", "Manual brightness maximum setting: $it")
    })
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
        lastApplied = currentSetting()
        view?.startAdjustingAt(currentPosition())
    }

    private fun endAdjustment() {
        adjusting = false
        lastApplied = null
        refresh()
    }

    /** The dial changes brightness only: a hardware level plus a software trim below the hardware steps. */
    private fun setLevel(position: Float) {
        val target = curve.setting(position)
        val wasAuto = automatic()
        if (adjusting && !wasAuto && target == lastApplied) return

        val success = try {
            Settings.System.canWrite(context) &&
                (!wasAuto || writeSetting(
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )) &&
                applySetting(target)
        } catch (error: RuntimeException) {
            Log.w("FloatPlay", "Could not change brightness", error)
            false
        }
        if (success) {
            errorShown = false
            lastApplied = target
        } else {
            showErrorOnce()
        }
        if (!adjusting) refresh()
    }

    private fun applySetting(target: DimSetting): Boolean {
        val before = dimLayer.factor
        // Order the two writes so the output moves monotonically instead of overshooting between them.
        val lighter = target.factor >= before
        if (lighter && !dimLayer.apply(target.factor)) return false
        if (!writeSetting(Settings.System.SCREEN_BRIGHTNESS, target.level)) return false
        dimmedLevel = target.level
        if (!lighter && !dimLayer.apply(target.factor)) return false
        return true
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
            activeToast?.cancel()
            activeToast = Toast.makeText(
                context, context.getString(R.string.extra_dim_access_needed, ExtraDim.GRANT_COMMAND), Toast.LENGTH_LONG,
            ).also { it.show() }
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
        view?.setState(currentPosition(), automatic(), ExtraDim.isActive(context))
    }

    /** The software trim only counts while the hardware level and manual mode are still ours. */
    private fun currentSetting(): DimSetting {
        val level = brightness()
        if (dimLayer.factor < 1.0 && (automatic() || level != dimmedLevel)) dimLayer.clear()
        return DimSetting(level, dimLayer.factor)
    }

    private fun currentPosition(): Float {
        val setting = currentSetting()
        return curve.position(setting.level, setting.factor)
    }

    fun remove() {
        adjusting = false
        lastApplied = null
        activeToast?.cancel()
        activeToast = null
        dimLayer.clear()
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
        .coerceIn(DimCurve.MINIMUM_LEVEL, curve.maximum)

    private fun writeSetting(name: String, value: Int): Boolean = Settings.System.putInt(resolver, name, value)

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
}
