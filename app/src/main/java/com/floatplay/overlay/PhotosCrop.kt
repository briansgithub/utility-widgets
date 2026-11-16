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
     * Smallest rotation the crop slider can represent.
     * A span of 90 or less is the degree dial (-45° to 45°), whose step is a tenth of a degree.
     * Wider ranges are integer progress, so one unit is the step.
     */
    fun minimumRotationStep(min: Float, max: Float): Float {
        val span = max - min
        if (span <= 0f) return 1f
        return if (span <= 90f) 0.1f else 1f
    }

    fun cropSession(sliderIds: List<String>, labels: List<String>, hasRange: Boolean): Boolean {
        if (sliderIds.any { isRotationSlider(it) }) return true
        if (!hasRange) return false
        return labels.any { label ->
            val text = label.trim()
            text.equals("Crop", ignoreCase = true) ||
                text.equals("Straighten", ignoreCase = true) ||
                text.equals("Rotate", ignoreCase = true)
        }
    }
}
