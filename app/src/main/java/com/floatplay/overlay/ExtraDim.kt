package com.floatplay.overlay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Where Extra Dim state lives: secure settings (reads may throw SecurityException) and app preferences. */
interface ExtraDimStore {
    fun secureInt(key: String, default: Int): Int
    fun putSecureInt(key: String, value: Int): Boolean
    fun prefInt(key: String, default: Int): Int
    fun prefBoolean(key: String, default: Boolean): Boolean
    fun putPref(key: String, value: Int)
    fun putPref(key: String, value: Boolean)
}

/**
 * Extra Dim logic. Only the double tap calls [toggle]; the brightness dial never touches Extra Dim.
 */
class ExtraDimController(private val store: ExtraDimStore) {
    private var readable = true

    fun isActive(): Boolean {
        if (readable) {
            try {
                val active = store.secureInt(ExtraDim.ACTIVATED, 0) == 1
                store.putPref(LAST_ACTIVE, active)
                return active
            } catch (_: SecurityException) {
                // Newer Android versions may forbid reads of hidden secure keys even with
                // WRITE_SECURE_SETTINGS. Fall back to the last applied state.
                readable = false
            }
        }
        return store.prefBoolean(LAST_ACTIVE, false)
    }

    fun toggle(): Boolean? {
        val next = !isActive()
        return if (setActive(next)) next else null
    }

    fun setActive(active: Boolean): Boolean = try {
        if (active) ensureNonZeroIntensity()
        val written = store.putSecureInt(ExtraDim.ACTIVATED, if (active) 1 else 0)
        if (written) store.putPref(LAST_ACTIVE, active)
        written
    } catch (_: SecurityException) {
        false
    }

    private fun currentStrength(): Int {
        if (readable) {
            try {
                val level = store.secureInt(ExtraDim.INTENSITY, 0)
                if (level > 0) store.putPref(LAST_INTENSITY, level)
                return level
            } catch (_: SecurityException) {
                readable = false
            }
        }
        return store.prefInt(LAST_INTENSITY, 0)
    }

    private fun ensureNonZeroIntensity() {
        val current = currentStrength()
        if (current <= 0 && store.putSecureInt(ExtraDim.INTENSITY, ExtraDim.DEFAULT_INTENSITY)) {
            store.putPref(LAST_INTENSITY, ExtraDim.DEFAULT_INTENSITY)
        }
    }

    private companion object {
        const val LAST_ACTIVE = "last_extra_dim_active"
        const val LAST_INTENSITY = "last_extra_dim_intensity"
    }
}

/** Optional access to the system Extra Dim toggle (Android 12+ with WRITE_SECURE_SETTINGS). */
object ExtraDim {
    const val ACTIVATED = "reduce_bright_colors_activated"
    const val INTENSITY = "reduce_bright_colors_level"
    const val DEFAULT_INTENSITY = 50
    const val GRANT_COMMAND = "adb shell pm grant com.floatplay.overlay android.permission.WRITE_SECURE_SETTINGS"

    private var controller: ExtraDimController? = null

    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Null when the one-time WRITE_SECURE_SETTINGS grant is missing. */
    fun controller(context: Context): ExtraDimController? {
        if (!available(context)) return null
        return controller ?: ExtraDimController(SystemStore(context.applicationContext)).also { controller = it }
    }

    fun isActive(context: Context): Boolean = controller(context)?.isActive() ?: false

    fun toggle(context: Context): Boolean? = controller(context)?.toggle()

    private class SystemStore(private val context: Context) : ExtraDimStore {
        private val prefs = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
        override fun secureInt(key: String, default: Int) = Settings.Secure.getInt(context.contentResolver, key, default)
        override fun putSecureInt(key: String, value: Int) = Settings.Secure.putInt(context.contentResolver, key, value)
        override fun prefInt(key: String, default: Int) = prefs.getInt(key, default)
        override fun prefBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
        override fun putPref(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
        override fun putPref(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    }
}
