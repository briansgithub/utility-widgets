package com.floatplay.overlay

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExtraDimControllerTest {
    private class FakeStore(var readable: Boolean = true) : ExtraDimStore {
        val secure = mutableMapOf<String, Int>()
        val prefs = mutableMapOf<String, Any>()
        val secureWrites = mutableListOf<Pair<String, Int>>()
        override fun secureInt(key: String, default: Int): Int {
            if (!readable) throw SecurityException("restricted")
            return secure[key] ?: default
        }
        override fun putSecureInt(key: String, value: Int): Boolean {
            secure[key] = value
            secureWrites.add(key to value)
            return true
        }
        override fun prefInt(key: String, default: Int) = prefs[key] as? Int ?: default
        override fun prefBoolean(key: String, default: Boolean) = prefs[key] as? Boolean ?: default
        override fun putPref(key: String, value: Int) { prefs[key] = value }
        override fun putPref(key: String, value: Boolean) { prefs[key] = value }
    }

    @Test fun toggleSwitchesOnAndOffAndKeepsTheUsersStrength() {
        val store = FakeStore().apply { secure[ExtraDim.INTENSITY] = 35 }
        val extra = ExtraDimController(store)
        assertEquals(true, extra.toggle())
        assertEquals(1, store.secure[ExtraDim.ACTIVATED])
        assertEquals(35, store.secure[ExtraDim.INTENSITY])
        assertEquals(false, extra.toggle())
        assertEquals(0, store.secure[ExtraDim.ACTIVATED])
        assertEquals(35, store.secure[ExtraDim.INTENSITY])
    }

    @Test fun zeroStrengthGetsTheDefaultWhenSwitchedOn() {
        val store = FakeStore()
        assertEquals(true, ExtraDimController(store).toggle())
        assertEquals(ExtraDim.DEFAULT_INTENSITY, store.secure[ExtraDim.INTENSITY])
    }

    @Test fun readingStateNeverWritesAnything() {
        val store = FakeStore()
        val extra = ExtraDimController(store)
        repeat(5) { extra.isActive() }
        assertTrue(store.secureWrites.isEmpty())
    }

    @Test fun restrictedReadsFallBackToTheLastState() {
        val store = FakeStore()
        val extra = ExtraDimController(store)
        extra.toggle()
        store.readable = false
        assertTrue(extra.isActive())
    }

    private fun source(name: String): String {
        val file = listOf("src/main/java/com/floatplay/overlay/$name", "app/src/main/java/com/floatplay/overlay/$name")
            .map(::File).first { it.isFile }
        return file.readText()
    }

    @Test fun onlyTheDoubleTapHandlerTogglesExtraDim() {
        val bubble = source("BrightnessBubble.kt")
        // Only toggleExtraDim() may switch it; the observer registration merely watches the key.
        val toggles = Regex("""ExtraDim\.toggle\(""").findAll(bubble).toList()
        assertEquals(1, toggles.size)
        val body = bubble.substringAfter("private fun toggleExtraDim()").substringBefore("private fun refresh()")
        assertTrue(body.contains("ExtraDim.toggle("))
        assertEquals(0, Regex("""\.setActive\(""").findAll(bubble).count())
        // The dial path (setLevel/applySetting/DimLayer) must not touch ExtraDim at all.
        val dial = bubble.substringAfter("private fun setLevel(").substringBefore("private fun toggleAutomatic()")
        assertFalse(dial.contains("ExtraDim"))
        assertFalse(source("DimLayer.kt").contains("ExtraDim"))
        assertFalse(source("BrightnessRange.kt").contains("ExtraDim"))
    }

    @Test fun onlyTheDoubleTapGestureCallsTheToggleCallback() {
        val dial = source("BrightnessDialView.kt")
        assertEquals(1, Regex("""onToggleExtraDim\(\)""").findAll(dial).count())
        assertTrue(dial.contains("onDoubleTap = { onToggleExtraDim() }"))
        assertFalse(dial.contains("ExtraDim."))
    }
}
