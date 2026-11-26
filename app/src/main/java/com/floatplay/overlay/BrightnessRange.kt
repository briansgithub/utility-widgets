package com.floatplay.overlay

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Perceptual exponential mapping between normalized dial position (0..1) and
 * hardware SCREEN_BRIGHTNESS (1..maximum). Equal angular turns produce equal
 * relative brightness changes, giving fine, smooth control across the dimmer
 * half of the dial with an exact inverse for every hardware level.
 */
object BrightnessRange {
    const val MINIMUM_BRIGHTNESS = 1

    fun brightness(position: Float, maximum: Int = 255): Int {
        val limit = maximum.coerceIn(2, 255)
        val normalized = if (position.isNaN()) 0f else position.coerceIn(0f, 1f)
        val mapped = exp(normalized.toDouble() * ln(limit.toDouble()))
        return mapped.roundToInt().coerceIn(MINIMUM_BRIGHTNESS, limit)
    }

    fun position(brightness: Int, maximum: Int = 255): Float {
        val limit = maximum.coerceIn(2, 255)
        val clamped = brightness.coerceIn(MINIMUM_BRIGHTNESS, limit)
        return (ln(clamped.toDouble()) / ln(limit.toDouble())).toFloat().coerceIn(0f, 1f)
    }
}
