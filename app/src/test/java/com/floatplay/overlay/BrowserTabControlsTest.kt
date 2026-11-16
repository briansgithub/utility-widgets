package com.floatplay.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabControlsTest {
    @Test fun defaultLeftSelectsNext() = assertFalse(TabDirection.previous(true, false))
    @Test fun defaultRightSelectsPrevious() = assertTrue(TabDirection.previous(false, false))
    @Test fun reversedLeftSelectsPrevious() = assertTrue(TabDirection.previous(true, true))
    @Test fun reversedRightSelectsNext() = assertFalse(TabDirection.previous(false, true))
    @Test fun defaultTabToolUsesFourTaps() = assertEquals(4, ToolSettings.defaultTabsTapCount(setOf(1, 2, 3)))
    @Test fun existingFourTapAssignmentIsPreserved() = assertEquals(5, ToolSettings.defaultTabsTapCount(setOf(1, 2, 4)))
    @Test fun gestureSafetyRegression() = BubbleGesturesCheck.main(emptyArray())
    @Test fun nativeBrowserMatchingRegression() = BrowserRulesCheck.main(emptyArray())
}
