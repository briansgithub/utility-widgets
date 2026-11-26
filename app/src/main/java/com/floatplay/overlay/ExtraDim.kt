package com.floatplay.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Optional access to the system Extra Dim toggle (Android 12+ with WRITE_SECURE_SETTINGS). */
object ExtraDim {
    const val ACTIVATED = "reduce_bright_colors_activated"
    const val INTENSITY = "reduce_bright_colors_level"
    const val DEFAULT_INTENSITY = 50
    private const val LAST_ACTIVE = "last_extra_dim_active"
    private const val LAST_INTENSITY = "last_extra_dim_intensity"
    private var readable = true

    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isActive(context: Context): Boolean {
        if (!available(context)) return false
        val prefs = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
        if (readable) {
            try {
                val active = Settings.Secure.getInt(context.contentResolver, ACTIVATED, 0) == 1
                prefs.edit().putBoolean(LAST_ACTIVE, active).apply()
                return active
            } catch (_: SecurityException) {
                // Newer Android versions may forbid reads of hidden secure keys even with
                // WRITE_SECURE_SETTINGS. Fall back to the last applied state.
                readable = false
            }
        }
        return prefs.getBoolean(LAST_ACTIVE, false)
    }

    fun setActive(context: Context, active: Boolean): Boolean {
        if (!available(context)) return false
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
        return try {
            if (active) {
                ensureNonZeroIntensity(resolver, prefs)
            }
            val written = Settings.Secure.putInt(resolver, ACTIVATED, if (active) 1 else 0)
            if (written) prefs.edit().putBoolean(LAST_ACTIVE, active).apply()
            written
        } catch (_: SecurityException) {
            false
        }
    }

    fun toggle(context: Context): Boolean? {
        val next = !isActive(context)
        return if (setActive(context, next)) next else null
    }

    private fun ensureNonZeroIntensity(
        resolver: android.content.ContentResolver,
        prefs: android.content.SharedPreferences,
    ) {
        var needsDefault = false
        if (readable) {
            try {
                val current = Settings.Secure.getInt(resolver, INTENSITY, 0)
                if (current <= 0) {
                    needsDefault = true
                } else {
                    prefs.edit().putInt(LAST_INTENSITY, current).apply()
                }
            } catch (_: SecurityException) {
                readable = false
                needsDefault = prefs.getInt(LAST_INTENSITY, 0) <= 0
            }
        } else {
            needsDefault = prefs.getInt(LAST_INTENSITY, 0) <= 0
        }
        if (needsDefault) {
            if (Settings.Secure.putInt(resolver, INTENSITY, DEFAULT_INTENSITY)) {
                prefs.edit().putInt(LAST_INTENSITY, DEFAULT_INTENSITY).apply()
            }
        }
    }
}
