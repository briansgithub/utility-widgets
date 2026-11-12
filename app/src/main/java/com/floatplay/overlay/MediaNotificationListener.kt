package com.floatplay.overlay

import android.service.notification.NotificationListenerService

/**
 * Enabled so this app can see active media sessions.
 * Notification contents are not read or stored.
 */
class MediaNotificationListener : NotificationListenerService()
