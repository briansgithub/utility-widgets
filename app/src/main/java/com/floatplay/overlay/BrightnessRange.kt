package com.floatplay.overlay

import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Bottom fifth fades Extra Dim out at minimum hardware brightness. */
object BrightnessRange {
    data class Output(val brightness: Int, val dim: Int)

    fun output(position: Int, extended: Boolean, maximum: Int = 255): Output {
        val value = position.coerceIn(0, 100)
        if (extended && value < 20) {
            val fraction = (20 - value) / 20f
            // Ease the last part of the tint away instead of a fixed five-point
            // intensity jump at the handoff to normal brightness.
            val eased = fraction * fraction * (3 - 2 * fraction)
            return Output(1, (eased * 100).roundToInt().coerceAtLeast(1))
        }
        val minimum = if (extended) 20 else 0
        val fraction = (value - minimum).toFloat() / (100 - minimum)
        val brightness = 1 + gammaToLinear(fraction) * (maximum.coerceIn(2, 255) - 1)
        return Output(brightness.roundToInt(), 0)
    }

    fun position(brightness: Int, dim: Int, extended: Boolean, maximum: Int = 255): Int {
        if (extended && dim > 0) {
            // Invert the eased low range with a bounded search.
            return (0..19).minBy { kotlin.math.abs(output(it, true, maximum).dim - dim) }
        }
        val minimum = if (extended) 20 else 0
        val limit = maximum.coerceIn(2, 255)
        val fraction = (brightness.coerceIn(1, limit) - 1).toFloat() / (limit - 1)
        return (minimum + linearToGamma(fraction) * (100 - minimum)).roundToInt().coerceIn(minimum, 100)
    }

    // Android's HLG perceptual curve gives useful control throughout the dial,
    // matching the system brightness slider instead of spending most of it near max.
    private fun gammaToLinear(value: Float): Float {
        if (value <= 0f) return 0f
        if (value >= 1f) return 1f
        val result = if (value <= 0.5f) (value / 0.5f) * (value / 0.5f)
            else exp((value - 0.55991073f) / 0.17883277f) + 0.28466892f
        return (result / 12f).coerceIn(0f, 1f)
    }

    private fun linearToGamma(value: Float): Float {
        val normalized = value * 12f
        return if (normalized <= 1f) sqrt(normalized) * 0.5f
            else 0.17883277f * ln(normalized - 0.28466892f) + 0.55991073f
    }
}
