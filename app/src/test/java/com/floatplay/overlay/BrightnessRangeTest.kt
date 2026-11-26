package com.floatplay.overlay

import org.junit.Assert.*
import org.junit.Test

class BrightnessRangeTest {
    @Test fun endpointsMapToMinimumAndMaximumHardwareBrightness() {
        for (maximum in listOf(2, 100, 156, 255)) {
            assertEquals(1, BrightnessRange.brightness(0f, maximum))
            assertEquals(maximum, BrightnessRange.brightness(1f, maximum))
            assertEquals(0f, BrightnessRange.position(1, maximum), 1e-6f)
            assertEquals(1f, BrightnessRange.position(maximum, maximum), 1e-6f)
        }
    }

    @Test fun curveIsStrictlyMonotonicAndEveryHardwareLevelIsReachable() {
        for (maximum in listOf(156, 255)) {
            var previous = 0
            val seen = HashSet<Int>()
            for (step in 0..10_000) {
                val pos = step / 10_000f
                val value = BrightnessRange.brightness(pos, maximum)
                assertTrue("Decreased at pos=$pos: $value < $previous", value >= previous)
                seen.add(value)
                previous = value
            }
            assertEquals(maximum, seen.size)
        }
    }

    @Test fun everyHardwareLevelRoundTripsExactly() {
        for (maximum in listOf(2, 10, 156, 255)) {
            var previousPos = -1f
            for (level in 1..maximum) {
                val pos = BrightnessRange.position(level, maximum)
                assertTrue("Position did not increase for level=$level", pos > previousPos)
                assertEquals("Round-trip mismatch for level=$level, max=$maximum",
                    level, BrightnessRange.brightness(pos, maximum))
                previousPos = pos
            }
        }
    }

    @Test fun dimmerHalfHasMuchFinerGranularityThanBrighterHalf() {
        val maximum = 156 // Pixel 9 Pro manual HBM transition (0.61 -> 156).
        val quarter = BrightnessRange.brightness(0.25f, maximum)
        val half = BrightnessRange.brightness(0.5f, maximum)
        val threeQuarters = BrightnessRange.brightness(0.75f, maximum)
        assertEquals(4, quarter)
        assertEquals(12, half)
        assertEquals(44, threeQuarters)
        // Bottom half spans only 1..12 (11 steps across 50% of dial); top half spans 12..156.
        assertTrue(half - 1 < (maximum - half) / 10)
        // Each step at the low end changes by at most 1 hardware unit per 1% of dial travel.
        for (p in 0 until 50) {
            val delta = BrightnessRange.brightness((p + 1) / 100f, maximum) -
                BrightnessRange.brightness(p / 100f, maximum)
            assertTrue("Step too large in dimmer half at $p%: delta=$delta", delta in 0..1)
        }
    }

    @Test fun fullRangeDefaultHasExactGeometricQuarters() {
        assertEquals(1, BrightnessRange.brightness(0f))
        assertEquals(4, BrightnessRange.brightness(0.25f))
        assertEquals(16, BrightnessRange.brightness(0.5f))
        assertEquals(64, BrightnessRange.brightness(0.75f))
        assertEquals(255, BrightnessRange.brightness(1f))
    }

    @Test fun outOfRangeAndNanValuesAreClamped() {
        assertEquals(1, BrightnessRange.brightness(-0.5f, 156))
        assertEquals(156, BrightnessRange.brightness(1.5f, 156))
        assertEquals(1, BrightnessRange.brightness(Float.NaN, 156))
        assertEquals(0f, BrightnessRange.position(0, 156), 1e-6f)
        assertEquals(0f, BrightnessRange.position(-5, 156), 1e-6f)
        assertEquals(1f, BrightnessRange.position(255, 156), 1e-6f)
    }
}
