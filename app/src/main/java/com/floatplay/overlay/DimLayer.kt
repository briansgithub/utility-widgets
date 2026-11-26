package com.floatplay.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager

/**
 * Full-screen black layer that trims the output between hardware brightness steps. It cannot be
 * touched, so it never blocks input, and it lives only as long as the brightness dial does.
 */
class DimLayer(private val context: Context, private val windows: WindowManager) {
    private var view: View? = null

    /** Current output factor (1.0 when the layer is not showing). */
    var factor = 1.0
        private set

    fun apply(factor: Double): Boolean {
        if (factor >= 1.0 - 1e-3) {
            clear()
            return true
        }
        val alpha = DimCurve.overlayAlpha(factor)
        val color = Color.argb((alpha * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
        val existing = view
        if (existing != null) {
            existing.setBackgroundColor(color)
        } else {
            val layer = View(context).apply { setBackgroundColor(color) }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = "FloatToolsDimLayer"
            }
            try {
                windows.addView(layer, params)
            } catch (_: RuntimeException) {
                return false
            }
            view = layer
        }
        this.factor = DimCurve.factor(alpha)
        return true
    }

    fun clear() {
        view?.let { try { windows.removeView(it) } catch (_: RuntimeException) { } }
        view = null
        factor = 1.0
    }
}
