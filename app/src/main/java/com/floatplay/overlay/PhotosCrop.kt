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

    /** One pixel of movement for a rotate creep or a photo drag. */
    const val STEP_PX = 1

    /** Clockwise creeps left along the straighten bar; counterclockwise creeps right. */
    fun rotationCreepX(clockwise: Boolean): Int = if (clockwise) -STEP_PX else STEP_PX

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
