package com.floatplay.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator

/** Optional access to the system Extra Dim controls. */
object ExtraDim {
    const val ACTIVATED = "reduce_bright_colors_activated"
    const val INTENSITY = "reduce_bright_colors_level"
    private const val LAST_INTENSITY = "last_applied_extra_dim"
    private val handler = Handler(Looper.getMainLooper())
    private var animator: ValueAnimator? = null
    private var target = -1
    private var readable = true
    private var activated: Boolean? = null
    private var pendingOff: Runnable? = null

    fun available(context: Context) = Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun intensity(context: Context): Int {
        if (!available(context)) return 0
        val prefs = ToolSettings.prefs(context)
        if (!readable) return prefs.getInt(LAST_INTENSITY, 0).coerceIn(0, 100)
        return try {
            val value = if (Settings.Secure.getInt(context.contentResolver, ACTIVATED, 0) == 1)
                Settings.Secure.getInt(context.contentResolver, INTENSITY, 0).coerceIn(0, 100) else 0
            prefs.edit().putInt(LAST_INTENSITY, value).apply()
            value
        } catch (_: SecurityException) {
            // Newer Android versions forbid reads of these hidden keys even with
            // WRITE_SECURE_SETTINGS. Keep the last successfully applied value.
            readable = false
            prefs.getInt(LAST_INTENSITY, 0).coerceIn(0, 100)
        }
    }

    fun set(context: Context, intensity: Int): Boolean {
        if (!available(context)) return intensity == 0
        val applied = intensity.coerceIn(0, 100)
        if (target == applied) return true
        val app = context.applicationContext
        val from = intensity(app)
        if (activated == null) activated = from > 0
        target = applied
        pendingOff?.let { handler.removeCallbacks(it) }
        pendingOff = null
        animator?.cancel()
        animator = null
        try {
            if (applied > 0 && activated != true) {
                // Clear the old stored level before activation; otherwise Android
                // can briefly restore the previous, much stronger tint.
                if (!writeIntensity(app, 0, true) || !Settings.Secure.putInt(app.contentResolver, ACTIVATED, 1)) {
                    target = -1
                    return false
                }
                activated = true
            }
            if (from == applied) {
                if (applied == 0) scheduleOff(app)
                return true
            }
            animator = ValueAnimator.ofInt(from, applied).apply {
                duration = 220L
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val value = it.animatedValue as Int
                    try {
                        if (!writeIntensity(app, value)) {
                            cancel()
                            target = -1
                        } else if (value == 0 && target == 0) scheduleOff(app)
                    } catch (_: SecurityException) {
                        cancel()
                        target = -1
                    }
                }
                start()
            }
            return true
        } catch (_: SecurityException) {
            target = -1
            return false
        }
    }

    private fun writeIntensity(context: Context, value: Int, force: Boolean = false): Boolean {
        val prefs = ToolSettings.prefs(context)
        if (!force && prefs.getInt(LAST_INTENSITY, -1) == value) return true
        val success = Settings.Secure.putInt(context.contentResolver, INTENSITY, value)
        if (success) prefs.edit().putInt(LAST_INTENSITY, value).apply()
        return success
    }

    private fun scheduleOff(context: Context) {
        pendingOff?.let { handler.removeCallbacks(it) }
        pendingOff = Runnable {
            if (target == 0) {
                try {
                    if (Settings.Secure.putInt(context.contentResolver, ACTIVATED, 0)) activated = false
                    else target = -1
                } catch (_: SecurityException) {
                    target = -1
                }
            }
            pendingOff = null
        }.also { handler.postDelayed(it, 100L) }
    }
}
