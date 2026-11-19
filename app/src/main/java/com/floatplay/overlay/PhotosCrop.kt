package com.floatplay.overlay

/**
 * How far each crop-pad button moves the photo.
 *
 * rotationRad: straighten change per press, set directly on the Photos slider (its value is in radians).
 * rotationDragPx: fallback drag along the straighten bar when the slider refuses a direct value.
 * panXPx / panYPx: drag length for one horizontal / vertical photo step.
 * pinchPx: how far each finger of the one-time zoom moves when a fitted photo has no room to slide.
 */
internal data class CropSteps(
    val rotationRad: Float,
    val rotationDragPx: Int,
    val panXPx: Int,
    val panYPx: Int,
    val pinchPx: Int,
)

internal object PhotosCrop {
    const val PACKAGE = "com.google.android.apps.photos"

    /** A finger drag on the straighten bar only ever lands on whole 0.01 rad steps, so that is the finest real step. */
    const val ROTATION_STEP_RAD = 0.01f

    private val rotationSliders = setOf(
        "photos_photoeditor_fragments_editor3_crop_slider",
        "photos_photoeditor_fragments_editor3_largescreen_crop_straighten_slider",
    )

    private val panTargets = setOf(
        "photos_photoeditor_crop_window",
    )

    /**
     * Steps measured on real phones (Build.DEVICE), see PHOTOS_CROP_STEPS.md.
     * lynx = Pixel 7a, 1080x2400, 420 dpi, touch slop 21 px.
     * caiman = Pixel 9 Pro, 1280x2856, 480 dpi, touch slop 24 px.
     */
    private val measured = mapOf(
        "lynx" to CropSteps(ROTATION_STEP_RAD, rotationDragPx = 26, panXPx = 23, panYPx = 23, pinchPx = 22),
        "caiman" to CropSteps(ROTATION_STEP_RAD, rotationDragPx = 30, panXPx = 26, panYPx = 26, pinchPx = 25),
    )

    /**
     * Phones nobody has measured yet. Photos ignores any drag up to one pixel past the touch slop and moves by what is
     * left after that, so two pixels past the slop is the smallest move; the straighten bar rounds to its nearest 0.01 rad step about
     * every 2 dp past the slop, so the fallback drag sits 2 dp in.
     */
    fun defaultSteps(touchSlopPx: Int, density: Float): CropSteps {
        val past = Math.round(2 * density)
        return CropSteps(ROTATION_STEP_RAD, touchSlopPx + past, touchSlopPx + 2, touchSlopPx + 2, touchSlopPx + 1)
    }

    fun steps(device: String, touchSlopPx: Int, density: Float): CropSteps =
        measured[device] ?: defaultSteps(touchSlopPx, density)

    /**
     * Drag length for [count] presses in a row. A slow drag moves the photo by what it goes past the dead zone
     * (touch slop + 1 px on the Pixel 7a), so each extra press adds one more step of that size.
     */
    fun panDragPx(stepPx: Int, touchSlopPx: Int, count: Int): Int {
        val deadZone = touchSlopPx + 1
        val unit = (stepPx - deadZone).coerceAtLeast(1)
        return if (count <= 1) stepPx else stepPx + (count - 1) * unit
    }

    /**
     * Where a photo drag stops running fast: 3 px short of the touch slop (never past the drag itself).
     * 1 px short sometimes already counts as past the slop and the photo jumps several pixels.
     */
    fun panApproachPx(touchSlopPx: Int, dragPx: Int): Int = (touchSlopPx - 3).coerceIn(0, dragPx)

    /** The slow last part of a photo drag moves 1 px per 50 ms, slow enough that every frame moves it well under 1 px. */
    fun panFinishMs(remainingPx: Int): Long = (remainingPx.coerceAtLeast(1) * 50L)

    /** Next straighten value one step clockwise or counterclockwise, or null when already at the end of the bar. */
    fun nextAngle(current: Float, min: Float, max: Float, clockwise: Boolean, step: Float): Float? {
        val target = (current + if (clockwise) step else -step).coerceIn(min, max)
        return if (Math.abs(target - current) < step / 100f) null else target
    }

    /**
     * The strip of screen between Photos' top controls and its bottom controls, where the pad can sit without covering
     * a Photos button. [controls] are (top, bottom) of Photos' tappable controls; the ones wholly in the top 40% of
     * the screen bound it from above and the ones wholly in the bottom 40% from below. Null when nothing is left.
     */
    fun freeBand(screenHeight: Int, controls: List<IntArray>): IntArray? {
        var top = 0
        var bottom = screenHeight
        for ((controlTop, controlBottom) in controls.map { it[0] to it[1] }) {
            if (controlBottom <= screenHeight * 2 / 5) top = maxOf(top, controlBottom)
            if (controlTop >= screenHeight * 3 / 5) bottom = minOf(bottom, controlTop)
        }
        return if (bottom > top) intArrayOf(top, bottom) else null
    }

    /** Moves a pad at [y] with [height] up or down just enough to sit inside the free band (top edge first if it can't fit). */
    fun snapY(y: Int, height: Int, bandTop: Int, bandBottom: Int): Int = when {
        height >= bandBottom - bandTop -> bandTop
        y < bandTop -> bandTop
        y + height > bandBottom -> bandBottom - height
        else -> y
    }

    fun leaf(id: String?): String {
        if (id.isNullOrEmpty()) return ""
        return id.substring(id.lastIndexOf('/') + 1)
    }

    fun isRotationSlider(id: String?): Boolean = leaf(id) in rotationSliders

    fun isPanTarget(id: String?): Boolean = leaf(id) in panTargets

    /** Clockwise drags left along the straighten bar (the value rises); counterclockwise drags right. */
    fun rotationDragX(clockwise: Boolean, distancePx: Int): Int = if (clockwise) -distancePx else distancePx

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
