package com.floatplay.overlay

import android.content.Context

object ToolSettings {
    const val MAX_TAPS = 5
    const val TAP_WINDOW_MS = 450L
    const val DEFAULT_COLOR = 0xFF167D8D.toInt()

    val tools = listOf(OverlayService.WIDGET_PLAY, OverlayService.WIDGET_ROTATE, OverlayService.WIDGET_BRIGHTNESS, OverlayService.WIDGET_TABS)

    fun taps(context: Context, tool: String): Int {
        val prefs = prefs(context)
        val default = when (tool) {
            OverlayService.WIDGET_PLAY -> 1
            OverlayService.WIDGET_ROTATE -> 2
            OverlayService.WIDGET_BRIGHTNESS -> listOf(3, 4, 5, 1, 2).first {
                it != prefs.getInt("play_taps", 1) && it != prefs.getInt("rotate_taps", 2)
            }
            else -> defaultTabsTapCount(setOf(
                taps(context, OverlayService.WIDGET_PLAY), taps(context, OverlayService.WIDGET_ROTATE),
                taps(context, OverlayService.WIDGET_BRIGHTNESS),
            ))
        }
        return prefs.getInt("${tool}_taps", default).coerceIn(1, MAX_TAPS)
    }

    internal fun defaultTabsTapCount(used: Set<Int>): Int = listOf(4, 5, 3, 2, 1).first { it !in used }

    fun minSize(tool: String) = if (tool == OverlayService.WIDGET_BRIGHTNESS) 64 else 40
    fun maxSize(tool: String) = if (tool == OverlayService.WIDGET_BRIGHTNESS) 160 else 96

    fun size(context: Context, tool: String): Int = prefs(context)
        .getInt("${tool}_size", if (tool == OverlayService.WIDGET_BRIGHTNESS) 96 else 56)
        .coerceIn(minSize(tool), maxSize(tool))

    fun color(context: Context, tool: String): Int = prefs(context)
        .getInt("${tool}_color", defaultColor(tool))

    fun defaultColor(tool: String): Int = when (tool) {
        OverlayService.WIDGET_PLAY -> 0xFF16168D.toInt()
        OverlayService.WIDGET_ROTATE -> 0xFFFFC926.toInt()
        OverlayService.WIDGET_TABS -> 0xFF919191.toInt()
        else -> DEFAULT_COLOR
    }

    fun prefs(context: Context) = context.getSharedPreferences(OverlayService.PREFS, Context.MODE_PRIVATE)
}
