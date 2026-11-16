package com.floatplay.overlay

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast

class ToolsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.tools_widget)
            val tap = PendingIntent.getBroadcast(context, 0,
                Intent(context, ToolsWidget::class.java).setAction(ACTION_TAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_button, tap)
            manager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return
        if (!context.overlayAllowed()) {
            Toast.makeText(context, R.string.overlay_permission_needed, Toast.LENGTH_LONG).show()
            return
        }
        context.startForegroundService(Intent(context, OverlayService::class.java)
            .setAction(OverlayService.ACTION_WIDGET_TAP))
    }

    companion object {
        const val ACTION_TAP = "com.floatplay.overlay.WIDGET_TAP"
    }
}
