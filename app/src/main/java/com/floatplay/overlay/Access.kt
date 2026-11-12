package com.floatplay.overlay

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

fun Context.overlayAllowed(): Boolean = Settings.canDrawOverlays(this)

fun Context.listenerEnabled(): Boolean {
    val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?: return false
    val flat = ComponentName(this, MediaNotificationListener::class.java).flattenToString()
    return enabled.split(":").any { it.equals(flat, ignoreCase = true) }
}
