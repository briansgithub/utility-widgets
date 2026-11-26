package com.floatplay.overlay

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class BrightnessRangeTest {
    private val curve = DimCurve(156) // Pixel 9 Pro manual maximum (HBM transition 0.61 -> level 156).

    private fun output(position: Float): Double {
        val setting = curve.setting(position)
        return curve.output(setting.level, DimCurve.factor(DimCurve.overlayAlpha(setting.factor)))
    }

    private fun lightness(output: Double) = output.pow(DimCurve.GAMMA)

    @Test fun modelMatchesTheNitsReadFromThePixel() {
        assertEquals(2.0, curve.nits(1), 0.01)
        assertEquals(1250.0, curve.nits(156), 0.5)
        assertEquals(26.2, curve.nits(4), 0.5) // Level 4 measured about 26 nits.
    }

    @Test fun dimEndSampledEveryHalfPercentHasNoLargeStep() {
        // Log of the dial 0..30% against the output it produces (nits) and the perceptual step.
        var previous = lightness(output(0f))
        var worst = 0.0
        val log = StringBuilder()
        for (i in 1..60) {
            val p = i / 200f
            val setting = curve.setting(p)
            val out = output(p)
            val current = lightness(out)
            worst = maxOf(worst, current - previous)
            log.append("dial=%.3f level=%d factor=%.3f nits=%.2f%n".format(p, setting.level, setting.factor, out))
            previous = current
        }
        println(log)
        val span = lightness(curve.nits(156)) - lightness(output(0f))
        // Evenly spaced would be span/200 per half percent; allow 3x for rounding of the overlay alpha.
        assertTrue("Largest step $worst of span $span", worst <= 3 * span / 200)
    }

    @Test fun hardwareOnlyWouldHaveJumped() {
        val hardware = DimCurve(156, softwareDim = false)
        var worst = 0.0
        var previous = hardware.nits(hardware.setting(0f).level).pow(DimCurve.GAMMA)
        for (i in 1..200) {
            val level = hardware.setting(i / 200f).level
            val now = hardware.nits(level).pow(DimCurve.GAMMA)
            worst = maxOf(worst, now - previous)
            previous = now
        }
        val span = hardware.nits(156).pow(DimCurve.GAMMA) - hardware.nits(1).pow(DimCurve.GAMMA)
        assertTrue("Level 1 -> 2 should be a big step: $worst of $span", worst > 8 * span / 200)
    }

    @Test fun curveIsMonotonicAcrossTheWholeDial() {
        var previous = -1.0
        for (i in 0..10_000) {
            val out = output(i / 10_000f)
            assertTrue("Output decreased at $i: $out < $previous", out >= previous - 1e-9)
            previous = out
        }
    }

    @Test fun bottomIsDimmerThanTheDimmestHardwareLevel() {
        val bottom = curve.setting(0f)
        assertEquals(1, bottom.level)
        assertEquals(DimCurve.MIN_FACTOR, bottom.factor, 1e-9)
        assertTrue(output(0f) < curve.nits(1))
        assertEquals(156, curve.setting(1f).level)
        assertEquals(1.0, curve.setting(1f).factor, 0.0)
    }

    @Test fun softwareTrimIsOnlyUsedAtTheDimEnd() {
        for (i in 0..1000) {
            val setting = curve.setting(i / 1000f)
            if (setting.level > DimCurve.BLEND_LEVELS) assertEquals(1.0, setting.factor, 0.0)
            assertTrue(setting.factor in DimCurve.MIN_FACTOR..1.0)
        }
    }

    @Test fun positionRoundTripsThroughTheSetting() {
        for (i in 0..200) {
            val p = i / 200f
            val setting = curve.setting(p)
            val factor = DimCurve.factor(DimCurve.overlayAlpha(setting.factor))
            assertEquals(p, curve.position(setting.level, factor), 0.02f)
        }
    }

    @Test fun withoutSoftwareDimTheDialUsesHardwareLevelsOnly() {
        val hardware = DimCurve(156, softwareDim = false)
        for (i in 0..200) assertEquals(1.0, hardware.setting(i / 200f).factor, 0.0)
        assertEquals(1, hardware.setting(0f).level)
        assertEquals(156, hardware.setting(1f).level)
    }

    @Test fun outOfRangeAndNanAreClamped() {
        assertEquals(curve.setting(0f), curve.setting(-0.5f))
        assertEquals(curve.setting(1f), curve.setting(1.5f))
        assertEquals(curve.setting(0f), curve.setting(Float.NaN))
        assertEquals(0f, curve.position(1, DimCurve.MIN_FACTOR), 1e-6f)
        assertEquals(1f, curve.position(156, 1.0), 1e-6f)
        assertEquals(1f, curve.position(255, 1.0), 1e-6f)
    }

    @Test fun overlayAlphaIsInvertible() {
        for (f in listOf(0.2, 0.35, 0.5, 0.8, 1.0)) {
            assertEquals(f, DimCurve.factor(DimCurve.overlayAlpha(f)), 1e-4)
        }
        assertTrue(DimCurve.overlayAlpha(DimCurve.MIN_FACTOR) < 0.8f) // Android's overlay opacity cap.
        assertTrue(abs(DimCurve.overlayAlpha(1.0)) < 1e-6f)
    }
}
