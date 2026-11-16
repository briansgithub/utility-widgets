package com.floatplay.overlay

internal object PhotosCrop {
    const val PACKAGE = "com.google.android.apps.photos"

    private val rotationSliders = setOf(
        "photos_photoeditor_fragments_editor3_crop_slider",
        "photos_photoeditor_fragments_editor3_largescreen_crop_straighten_slider",
    )

    private val panTargets = setOf(
        "photos_photoeditor_crop_window",
    )

    fun leaf(id: String?): String {
        if (id.isNullOrEmpty()) return ""
        return id.substring(id.lastIndexOf('/') + 1)
    }

    fun isRotationSlider(id: String?): Boolean = leaf(id) in rotationSliders

    fun isPanTarget(id: String?): Boolean = leaf(id) in panTargets

    /**
     * Shortest slider drag that stays a drag.
     * Anything at or below the touch slop is delivered as a tap and jumps the angle.
     * Four pixels past the slop is the first drag, and on the crop bar that drag is one visible step.
     */
    fun rotationDragPx(touchSlopPx: Int): Int = touchSlopPx + 4

    /** Smallest pinch that still changes a fitted crop. A shorter one is delivered as two taps. */
    fun minimumPinchPx(touchSlopPx: Int): Int = touchSlopPx + 1

    /** Clockwise drags left along the straighten bar; counterclockwise drags right. */
    fun rotationDragX(clockwise: Boolean, touchSlopPx: Int): Int {
        val distance = rotationDragPx(touchSlopPx)
        return if (clockwise) -distance else distance
    }

    fun isCropChrome(label: String): Boolean {
        val text = label.trim().lowercase()
        return text.contains("rotate ninety degrees") ||
            text.contains("crop to aspect ratio") ||
            text.contains("perspective crop")
    }

    fun cropSession(sliderIds: List<String>, labels: List<String>, hasRange: Boolean): Boolean {
        if (sliderIds.any { isRotationSlider(it) }) return true
        if (labels.any { isCropChrome(it) }) return true
        val named = labels.map { it.trim() }
        if (named.any { it.equals("Straighten", ignoreCase = true) } &&
            named.any { it.equals("Perspective", ignoreCase = true) || it.equals("Rotate", ignoreCase = true) }
        ) {
            return true
        }
        if (!hasRange) return false
        return named.any { label ->
            label.equals("Crop", ignoreCase = true) ||
                label.equals("Straighten", ignoreCase = true) ||
                label.equals("Rotate", ignoreCase = true)
        }
    }
}
