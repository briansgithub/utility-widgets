package com.floatplay.overlay

import kotlin.math.abs
import kotlin.math.pow

/**
 * What the dial wants: a hardware level and a software dim factor (1.0 = none, down to
 * [DimCurve.MIN_FACTOR]) that fills the gaps between hardware steps at the dim end.
 */
data class DimSetting(val level: Int, val factor: Double = 1.0)

/**
 * Luminance model and dial mapping.
 *
 * Android only lets an app write the integer SCREEN_BRIGHTNESS (1..255); the float setting is ignored
 * (verified on a Pixel 9 Pro, Android 17). On that panel level n is linear in backlight, about
 * 2 nits + 8.06 nits per step: level 1 -> 2 is 2 -> 10 nits (5x) and 3 -> 4 is still +44%, so a
 * hardware step at the dim end is a large perceptual jump however the dial is mapped. Extra Dim cannot
 * bridge it either (its strength only scales output between 0.76 and 0.52).
 *
 * The dial therefore moves along a perceptual axis (lightness = nits^0.4). Up to [BLEND_LEVELS] the
 * hardware level is rounded up and a black overlay (factor) trims it down to the exact target, so the
 * output is continuous across the whole dim end and continues below the dimmest hardware level.
 * The dial changes brightness only; it never touches Extra Dim.
 */
class DimCurve(
    val maximum: Int = 255,
    private val minNits: Double = MIN_NITS,
    private val nitsPerLevel: Double = NITS_PER_LEVEL,
    private val softwareDim: Boolean = true,
) {
    private val limit = maximum.coerceIn(2, 255)

    fun nits(level: Int): Double = minNits + (level.coerceIn(MINIMUM_LEVEL, limit) - 1) * nitsPerLevel

    fun output(level: Int, factor: Double): Double = nits(level) * factor

    private val lowest get() = output(MINIMUM_LEVEL, if (softwareDim) MIN_FACTOR else 1.0)
    private val highest get() = nits(limit)

    private fun lightness(output: Double) = output.coerceAtLeast(0.0).pow(GAMMA)

    /** Dial position (0 dimmest .. 1 brightest) of what the phone shows now. */
    fun position(level: Int, factor: Double): Float {
        val low = lightness(lowest)
        val high = lightness(highest)
        return ((lightness(output(level, factor)) - low) / (high - low)).toFloat().coerceIn(0f, 1f)
    }

    fun setting(position: Float): DimSetting {
        val normalized = if (position.isNaN()) 0f else position.coerceIn(0f, 1f)
        val low = lightness(lowest)
        val high = lightness(highest)
        val target = (low + normalized * (high - low)).pow(1.0 / GAMMA)
        if (softwareDim) {
            var level = MINIMUM_LEVEL
            while (level < limit && nits(level) < target) level++
            if (level <= BLEND_LEVELS) {
                return DimSetting(level, (target / nits(level)).coerceIn(MIN_FACTOR, 1.0))
            }
        }
        return DimSetting(nearestLevel(target))
    }

    private fun nearestLevel(target: Double): Int {
        var best = MINIMUM_LEVEL
        var bestError = Double.MAX_VALUE
        val goal = lightness(target)
        for (level in MINIMUM_LEVEL..limit) {
            val error = abs(lightness(nits(level)) - goal)
            if (error < bestError) {
                best = level
                bestError = error
            }
        }
        return best
    }

    companion object {
        const val MINIMUM_LEVEL = 1
        /** Pixel 9 Pro: 2 nits at level 1, 1250 nits at level 156 (backlight 0.61). */
        const val MIN_NITS = 2.0
        const val NITS_PER_LEVEL = 1248.0 / 155.0
        /** Stevens' power law for perceived brightness. */
        const val GAMMA = 0.4
        /** Above this level one hardware step is under ~4% of the output, so no blending is needed. */
        const val BLEND_LEVELS = 24
        /** The dimmest software trim: 20% of the lowest hardware level. */
        const val MIN_FACTOR = 0.2
        /** Display gamma, to turn an output factor into an overlay alpha. */
        const val DISPLAY_GAMMA = 2.2

        fun overlayAlpha(factor: Double): Float =
            (1.0 - factor.coerceIn(MIN_FACTOR, 1.0).pow(1.0 / DISPLAY_GAMMA)).toFloat()

        fun factor(alpha: Float): Double = (1.0 - alpha.toDouble().coerceIn(0.0, 1.0)).pow(DISPLAY_GAMMA)
    }
}
