package com.floatplay.overlay

import android.provider.Settings
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrightnessBubbleDeviceTest {
    @Test fun extraDimFadesBeforeSwitchingOffAndReversesWithoutDelayedOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun systemValue(key: String): String = instrumentation.uiAutomation
            .executeShellCommand("settings get secure $key").use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().readText().trim()
            }
        val originalActive = systemValue(ExtraDim.ACTIVATED)
        val originalStrength = systemValue(ExtraDim.INTENSITY)
        fun set(value: Int) = instrumentation.runOnMainSync { assertTrue(ExtraDim.set(context, value)) }
        try {
            set(40)
            android.os.SystemClock.sleep(300)
            assertEquals("1", systemValue(ExtraDim.ACTIVATED))
            assertEquals("40", systemValue(ExtraDim.INTENSITY))
            set(0)
            android.os.SystemClock.sleep(80)
            assertEquals("1", systemValue(ExtraDim.ACTIVATED))
            assertTrue(systemValue(ExtraDim.INTENSITY).toInt() in 0..39)
            android.os.SystemClock.sleep(350)
            assertEquals("0", systemValue(ExtraDim.ACTIVATED))
            assertEquals("0", systemValue(ExtraDim.INTENSITY))
            set(40)
            android.os.SystemClock.sleep(300)
            set(0)
            android.os.SystemClock.sleep(80)
            set(35)
            android.os.SystemClock.sleep(350)
            assertEquals("1", systemValue(ExtraDim.ACTIVATED))
            assertEquals("35", systemValue(ExtraDim.INTENSITY))
        } finally {
            set(if (originalActive == "1") originalStrength.toIntOrNull() ?: 0 else 0)
            android.os.SystemClock.sleep(400)
            // Restore the user's stored system preference as well as activation.
            for ((key, value) in listOf(ExtraDim.INTENSITY to originalStrength, ExtraDim.ACTIVATED to originalActive)) {
                val command = if (value == "null") "settings delete secure $key" else "settings put secure $key $value"
                instrumentation.uiAutomation.executeShellCommand(command).close()
            }
        }
    }

    @Test fun restrictedExtraDimReadDoesNotRemoveDialOrCrashObserver() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Extra Dim permission is required for this regression check", ExtraDim.available(context))
        assertTrue("Overlay permission is required", context.overlayAllowed())
        val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        lateinit var bubble: BrightnessBubble
        var created = false
        try {
            instrumentation.runOnMainSync {
                assertTrue(ExtraDim.intensity(context) in 0..100)
                bubble = BrightnessBubble(context, context.getSystemService(WindowManager::class.java)) { }
                created = true
                bubble.show()
                // Exercise the observer refresh path without changing the user's brightness.
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            }
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(100)
            instrumentation.runOnMainSync {
                assertTrue("Dial must survive the brightness observer callback", bubble.isShowing)
                bubble.show()
                assertTrue("Repeated show must retain the dial", bubble.isShowing)
            }
        } finally {
            instrumentation.runOnMainSync { if (created) bubble.remove() }
        }
    }
}
