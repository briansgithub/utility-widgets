package com.floatplay.overlay

import android.content.Context

object ToolSettings {
    const val MAX_TAPS = 5
    const val TAP_WINDOW_MS = 450L
    const val DEFAULT_COLOR = 0xFF167D8D.toInt()

    fun taps(context: Context, tool: String): Int = prefs(context)
        .getInt("${tool}_taps", if (tool == OverlayService.WIDGET_PLAY) 1 else 2)
        .coerceIn(1, MAX_TAPS)

    fun size(context: Context, tool: String): Int = prefs(context)
        .getInt("${tool}_size", 56).coerceIn(40, 96)

    fun color(context: Context, tool: String): Int = prefs(context)
        .getInt("${tool}_color", DEFAULT_COLOR)

    fun prefs(context: Context) = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
}
