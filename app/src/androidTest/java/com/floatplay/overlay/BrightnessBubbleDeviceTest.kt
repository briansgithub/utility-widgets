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
    @Test fun extraDimTogglePreservesStrengthAndRestoresDefaultWhenZero() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Extra Dim permission is required for this check", ExtraDim.available(context))
        fun shell(command: String): String = instrumentation.uiAutomation
            .executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().readText().trim()
            }
        fun systemValue(key: String) = shell("settings get secure $key")

        val originalActive = systemValue(ExtraDim.ACTIVATED)
        val originalStrength = systemValue(ExtraDim.INTENSITY)
        try {
            // If strength was previously zeroed, enabling Extra Dim restores a visible default.
            shell("settings put secure ${ExtraDim.ACTIVATED} 0")
            shell("settings put secure ${ExtraDim.INTENSITY} 0")
            instrumentation.runOnMainSync {
                assertEquals(true, ExtraDim.toggle(context))
            }
            assertEquals("1", systemValue(ExtraDim.ACTIVATED))
            assertEquals(ExtraDim.DEFAULT_INTENSITY.toString(), systemValue(ExtraDim.INTENSITY))

            // Turning Extra Dim off preserves the user's configured intensity.
            shell("settings put secure ${ExtraDim.INTENSITY} 35")
            instrumentation.runOnMainSync {
                assertEquals(false, ExtraDim.toggle(context))
            }
            assertEquals("0", systemValue(ExtraDim.ACTIVATED))
            assertEquals("35", systemValue(ExtraDim.INTENSITY))

            // Turning Extra Dim back on keeps the configured intensity intact.
            instrumentation.runOnMainSync {
                assertEquals(true, ExtraDim.toggle(context))
            }
            assertEquals("1", systemValue(ExtraDim.ACTIVATED))
            assertEquals("35", systemValue(ExtraDim.INTENSITY))
        } finally {
            for ((key, value) in listOf(ExtraDim.INTENSITY to originalStrength, ExtraDim.ACTIVATED to originalActive)) {
                val command = if (value == "null") "settings delete secure $key" else "settings put secure $key $value"
                shell(command)
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
                // Reading active state must never throw even if hidden secure keys are restricted.
                ExtraDim.isActive(context)
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
