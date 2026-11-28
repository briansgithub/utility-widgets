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
    @Test fun cropToolChromeShowsThePad() = assertTrue(PhotosCrop.cropSession(
        emptyList(),
        listOf("Crop", "Straighten", "Rotate", "Perspective", "Rotate ninety degrees counter-clockwise"),
        false,
    ))
    @Test fun photoLibraryDoesNotShowThePad() = assertFalse(PhotosCrop.cropSession(emptyList(), listOf("Crop"), false))
    @Test fun defaultPhotoToolUsesFiveTaps() = assertEquals(5, ToolSettings.defaultPhotosTapCount(setOf(1, 2, 3, 4)))
    @Test fun photoToolAvoidsAssignedFiveTaps() = assertEquals(4, ToolSettings.defaultPhotosTapCount(setOf(1, 2, 3, 5)))
    @Test fun rotationDragClearsTouchSlop() {
        assertEquals(25, PhotosCrop.rotationDragPx(21))
        assertEquals(-25, PhotosCrop.rotationDragX(clockwise = true, touchSlopPx = 21))
        assertEquals(28, PhotosCrop.rotationDragX(clockwise = false, touchSlopPx = 24))
        assertEquals(22, PhotosCrop.minimumPinchPx(21))
        assertEquals(25, PhotosCrop.dragDistance(0, PhotosCrop.rotationChoices, 21))
        assertEquals(1, PhotosCrop.dragDistance(1, PhotosCrop.rotationChoices, 21))
        assertEquals(28, PhotosCrop.dragDistance(28, PhotosCrop.rotationChoices, 24))
        assertEquals(25, PhotosCrop.dragDistance(99, PhotosCrop.rotationChoices, 21))
        assertEquals(40, PhotosCrop.dragDistance(40, PhotosCrop.translationChoices, 21))
    }
    @Test fun gestureSafetyRegression() = BubbleGesturesCheck.main(emptyArray())
    @Test fun nativeBrowserMatchingRegression() = BrowserRulesCheck.main(emptyArray())
    @Test fun tabToolUsesSameSizeRangeAsMedia() {
        assertEquals(ToolSettings.minSize(OverlayService.WIDGET_PLAY), ToolSettings.minSize(OverlayService.WIDGET_TABS))
        assertEquals(ToolSettings.maxSize(OverlayService.WIDGET_PLAY), ToolSettings.maxSize(OverlayService.WIDGET_TABS))
    }
}
