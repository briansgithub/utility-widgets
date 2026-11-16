package com.floatplay.overlay

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

/** A preview button opens a picker; only Use color commits the draft. */
class FloatingColorPicker(
    context: Context,
    private val tool: String,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    private var selected = ToolSettings.color(context, tool)
    private val icon = ImageView(context)
    private val choose = Button(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, 0)
        addView(icon, LayoutParams(dp(56), dp(56)))
        addView(choose, LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        paintIcon(icon, selected)
        updateButton()
        choose.setOnClickListener { openPicker() }
    }

    private fun updateButton() {
        choose.text = "${context.getString(R.string.choose_color)}\n${hex(selected)}"
    }

    private fun openPicker() {
        val hsv = FloatArray(3)
        Color.colorToHSV(selected, hsv)
        var draft = selected
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        val preview = ImageView(context)
        body.addView(preview, LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val code = TextView(context).apply { gravity = Gravity.CENTER; textSize = 16f }
        body.addView(code)
        body.addView(TextView(context).apply {
            setText(R.string.color_presets)
            setPadding(0, dp(12), 0, dp(4))
        })
        val names = listOf(R.string.color_teal, R.string.color_blue, R.string.color_purple,
            R.string.color_pink, R.string.color_red, R.string.color_orange,
            R.string.color_green, R.string.color_white)
        val colors = listOf(0xFF167D8D, 0xFF2979FF, 0xFF9C6CFF, 0xFFF06292,
            0xFFEF5350, 0xFFFFA726, 0xFF43A047, 0xFFFFFFFF).map { it.toInt() }
        val swatches = mutableListOf<TextView>()
        val sliders = mutableListOf<SeekBar>()
        val labels = mutableListOf<TextView>()
        val labelIds = listOf(R.string.color_hue, R.string.color_saturation, R.string.color_lightness)
        val maxValues = listOf(360, 100, 100)
        val exact = EditText(context).apply {
            setSingleLine(true)
            setText(hex(draft))
            hint = context.getString(R.string.color_hex)
            contentDescription = context.getString(R.string.color_hex)
        }
        var syncingHex = false
        fun refresh() {
            paintIcon(preview, draft)
            code.text = hex(draft)
            if (!syncingHex && exact.text.toString() != hex(draft)) {
                syncingHex = true
                exact.setText(hex(draft))
                syncingHex = false
            }
            colors.forEachIndexed { index, color ->
                swatches[index].apply {
                    text = if (color == draft) "✓" else ""
                    setTextColor(contrast(color))
                    isSelected = color == draft
                    contentDescription = "${context.getString(names[index])}, ${hex(color)}" +
                        if (isSelected) ", ${context.getString(R.string.color_selected)}" else ""
                }
            }
            sliders.forEachIndexed { index, slider ->
                val value = (if (index == 0) hsv[0] else hsv[index] * 100).toInt()
                slider.progress = value
                labels[index].text = context.getString(labelIds[index], value)
                slider.progressTintList = ColorStateList.valueOf(draft)
            }
        }
        repeat(2) { rowIndex ->
            val row = LinearLayout(context)
            body.addView(row)
            repeat(4) { column ->
                val index = rowIndex * 4 + column
                val color = colors[index]
                val swatch = TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 24f
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        setStroke(dp(2), 0xFFB6C2CF.toInt())
                    }
                    setOnClickListener {
                        draft = color
                        Color.colorToHSV(color, hsv)
                        refresh()
                    }
                }
                swatches.add(swatch)
                val touchTarget = FrameLayout(context).apply {
                    addView(swatch, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
                }
                row.addView(touchTarget, LayoutParams(0, dp(60), 1f))
            }
        }
        repeat(3) { index ->
            val label = TextView(context)
            labels.add(label)
            body.addView(label)
            val slider = SeekBar(context).apply {
                max = maxValues[index]
                minimumHeight = dp(48)
                contentDescription = context.getString(labelIds[index], 0).substringBefore(':')
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        hsv[index] = if (index == 0) value.toFloat() else value / 100f
                        draft = Color.HSVToColor(hsv)
                        refresh()
                    }
                })
            }
            sliders.add(slider)
            body.addView(slider)
        }
        exact.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingHex || !Regex("#[0-9a-fA-F]{6}").matches(s.toString().trim())) return
                draft = Color.parseColor(s.toString().trim())
                Color.colorToHSV(draft, hsv)
                syncingHex = true
                refresh()
                syncingHex = false
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        // Keep exact entry available without requiring a keyboard for ordinary use.
        body.addView(Button(context).apply {
            text = context.getString(R.string.color_hex)
            setOnClickListener {
                exact.visibility = if (exact.visibility == View.GONE) View.VISIBLE else View.GONE
                exact.setText(hex(draft))
            }
        })
        exact.visibility = View.GONE
        body.addView(exact)
        refresh()
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.choose_color)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(R.string.color_cancel, null)
            .setPositiveButton(R.string.color_apply, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (exact.visibility == View.VISIBLE) {
                    val value = exact.text.toString().trim()
                    if (!Regex("#[0-9a-fA-F]{6}").matches(value)) {
                        exact.error = context.getString(R.string.invalid_color)
                        return@setOnClickListener
                    }
                    draft = Color.parseColor(value)
                }
                selected = draft
                paintIcon(icon, selected)
                updateButton()
                onSelected(selected)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun paintIcon(view: ImageView, color: Int) {
        view.setImageResource(when (tool) {
            OverlayService.WIDGET_PLAY -> R.drawable.ic_play
            OverlayService.WIDGET_TABS -> R.drawable.ic_close_tab
            else -> R.drawable.ic_rotate
        })
        view.setPadding(dp(14), dp(14), dp(14), dp(14))
        view.setColorFilter(contrast(color))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        view.contentDescription = "${context.getString(R.string.color_preview)}, ${hex(color)}"
    }

    private fun contrast(color: Int) = if (Color.luminance(color) > 0.4f) Color.BLACK else Color.WHITE
    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
