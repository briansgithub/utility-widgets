package com.floatplay.overlay

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

object TabControls {
    const val REVERSE_SWIPE = "tabs_reverse_swipe"

    @JvmStatic
    fun size(context: Context): Int = ToolSettings.size(context, OverlayService.WIDGET_TABS)

    @JvmStatic
    fun color(context: Context): Int = ToolSettings.color(context, OverlayService.WIDGET_TABS)

    fun accessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TabAccessibilityService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { expected == ComponentName.unflattenFromString(it.id) }
    }

    @JvmStatic
    fun previousTab(context: Context, swipedLeft: Boolean): Boolean = TabDirection.previous(
        swipedLeft, ToolSettings.prefs(context).getBoolean(REVERSE_SWIPE, false),
    )
}
