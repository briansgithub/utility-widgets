package com.floatplay.overlay

import org.junit.Assert.*
import org.junit.Test

class BrightnessRangeTest {
    @Test fun lowestRangeAddsDimWhileKeepingHardwareAtMinimum() {
        assertEquals(BrightnessRange.Output(1, 100), BrightnessRange.output(0, true))
        assertEquals(BrightnessRange.Output(1, 50), BrightnessRange.output(10, true))
        assertEquals(BrightnessRange.Output(1, 0), BrightnessRange.output(20, true))
        assertEquals(BrightnessRange.Output(255, 0), BrightnessRange.output(100, true))
    }

    @Test fun dialBrightensMonotonicallyAcrossDimBoundary() {
        var previous = BrightnessRange.output(0, true)
        for (position in 0..100) {
            val next = BrightnessRange.output(position, true)
            assertTrue(next.brightness >= previous.brightness)
            assertTrue(next.dim <= previous.dim)
            if (position < 100) assertTrue(next.brightness < 255)
            previous = next
        }
    }

    @Test fun optionalPermissionFallbackPreservesNormalRange() {
        assertEquals(BrightnessRange.Output(1, 0), BrightnessRange.output(0, false))
        assertEquals(BrightnessRange.Output(22, 0), BrightnessRange.output(50, false))
        assertEquals(BrightnessRange.Output(255, 0), BrightnessRange.output(100, false))
        assertEquals(50, BrightnessRange.position(22, 0, false))
    }

    @Test fun manualCapUsesWholeDialInsteadOfReachingMaxHalfway() {
        val maximum = 156 // Pixel 9 Pro: normal backlight max 0.61, setting 1 + 254 * 0.61.
        for (extended in listOf(false, true)) {
            for (position in 0 until 100) {
                val output = BrightnessRange.output(position, extended, maximum)
                assertTrue(output.brightness < maximum)
            }
            assertEquals(BrightnessRange.Output(maximum, 0), BrightnessRange.output(100, extended, maximum))
            assertEquals(100, BrightnessRange.position(maximum, 0, extended, maximum))
            assertEquals(100, BrightnessRange.position(255, 0, extended, maximum))
        }
        // Midpoint of the normal-brightness section should match ~50% of the
        // system slider, not half the raw backlight output.
        val midpoint = BrightnessRange.output(60, true, maximum)
        assertTrue(midpoint.brightness in 13..15)
        assertTrue(BrightnessRange.position(midpoint.brightness, 0, true, maximum) in 59..61)
        assertEquals(BrightnessRange.Output(1, 50), BrightnessRange.output(10, true, maximum))
    }

    @Test fun valuesOutsideRangeAreClamped() {
        assertEquals(BrightnessRange.output(0, true, 156), BrightnessRange.output(-20, true, 156))
        assertEquals(BrightnessRange.output(100, true, 156), BrightnessRange.output(120, true, 156))
    }

    @Test fun dimTapersGentlyAtNormalBrightnessBoundary() {
        assertEquals(1, BrightnessRange.output(19, true).dim)
        assertEquals(3, BrightnessRange.output(18, true).dim)
        assertEquals(0, BrightnessRange.output(20, true).dim)
        for (position in 0..19) {
            val output = BrightnessRange.output(position, true)
            assertEquals(position, BrightnessRange.position(output.brightness, output.dim, true))
        }
    }
}
