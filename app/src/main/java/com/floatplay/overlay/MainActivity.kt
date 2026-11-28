package com.floatplay.overlay

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.*
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var playSwitch: Switch
    private lateinit var rotateSwitch: Switch
    private lateinit var brightnessSwitch: Switch
    private lateinit var tabsSwitch: Switch
    private lateinit var photosSwitch: Switch
    private val brightnessPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == OverlayService.KEY_BRIGHTNESS_ENABLED) refreshControls()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onStart() {
        super.onStart()
        ToolSettings.prefs(this).registerOnSharedPreferenceChangeListener(brightnessPreferenceListener)
    }

    override fun onStop() {
        ToolSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(brightnessPreferenceListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::playSwitch.isInitialized) {
            refreshControls()
            if (overlayAllowed() && OverlayService.anyWidgetEnabled(this)) OverlayService.start(this)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
            setBackgroundColor(0xFF10151C.toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.widget_screen_description)
            textSize = 15f
            setTextColor(0xFFB6C2CF.toInt())
            setPadding(0, 0, 0, dp(24))
        })

        playSwitch = addWidgetRow(root, R.string.play_widget_title, R.string.play_widget_description)
        rotateSwitch = addWidgetRow(root, R.string.rotate_widget_title, R.string.rotate_widget_description)
        brightnessSwitch = addWidgetRow(root, R.string.brightness_widget_title, R.string.brightness_widget_description)
        tabsSwitch = addWidgetRow(root, R.string.tabs_widget_title, R.string.tabs_widget_description)
        photosSwitch = addWidgetRow(root, R.string.photos_widget_title, R.string.photos_widget_description)
        root.addView(Button(this).apply {
            text = getString(R.string.add_home_widget)
            setOnClickListener {
                val manager = getSystemService(AppWidgetManager::class.java)
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(ComponentName(this@MainActivity, ToolsWidget::class.java), null, null)
                } else Toast.makeText(this@MainActivity, R.string.add_widget_manually, Toast.LENGTH_LONG).show()
            }
        })
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF10151C.toInt())
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setTextColor(0xFFF2F5FA.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            text = getString(R.string.permissions_title)
            textSize = 14f
            setOnClickListener { startActivity(Intent(this@MainActivity, PermissionsActivity::class.java)) }
        })
        screen.addView(header)
        screen.addView(ScrollView(this).apply { addView(root) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screen)
        refreshControls()
        playSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PLAY, enabled) }
        rotateSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_ROTATE, enabled) }
        brightnessSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_BRIGHTNESS, enabled) }
        tabsSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_TABS, enabled) }
        photosSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PHOTOS, enabled) }
    }

    private fun addWidgetRow(root: LinearLayout, title: Int, description: Int): Switch {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF202B35.toInt())
        }
        val label = TextView(this).apply {
            text = getString(title)
            textSize = 18f
            setTextColor(0xFFF2F5FA.toInt())
        }
        card.addView(label)
        card.addView(TextView(this).apply {
            text = getString(description)
            textSize = 14f
            setTextColor(0xFFB6C2CF.toInt())
            setPadding(0, dp(4), 0, dp(8))
        })
        val toggle = Switch(this).apply { text = getString(R.string.widget_enabled) }
        card.addView(toggle)
        val tool = when (title) {
            R.string.play_widget_title -> OverlayService.WIDGET_PLAY
            R.string.rotate_widget_title -> OverlayService.WIDGET_ROTATE
            R.string.brightness_widget_title -> OverlayService.WIDGET_BRIGHTNESS
            R.string.photos_widget_title -> OverlayService.WIDGET_PHOTOS
            else -> OverlayService.WIDGET_TABS
        }
        addSettings(card, tool)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return toggle
    }

    private fun addSettings(card: LinearLayout, tool: String) {
        val prefs = ToolSettings.prefs(this)
        fun label(value: String) = TextView(this).apply {
            text = value
            setTextColor(0xFFB6C2CF.toInt())
            setPadding(0, dp(12), 0, dp(4))
            card.addView(this)
        }
        label(getString(R.string.tap_count))
        val taps = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                (1..ToolSettings.MAX_TAPS).map { getString(R.string.tap_count_value, it) })
            setSelection(ToolSettings.taps(this@MainActivity, tool) - 1)
            contentDescription = getString(R.string.tap_count)
        }
        card.addView(taps)
        taps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val count = position + 1
                if (count == ToolSettings.taps(this@MainActivity, tool)) return
                if (ToolSettings.tools.any { it != tool && count == ToolSettings.taps(this@MainActivity, it) }) {
                    Toast.makeText(this@MainActivity, R.string.tap_count_conflict, Toast.LENGTH_LONG).show()
                    taps.setSelection(ToolSettings.taps(this@MainActivity, tool) - 1)
                } else prefs.edit().putInt("${tool}_taps", count).apply()
            }
        }
        if (tool == OverlayService.WIDGET_PHOTOS) {
            addDragChoice(card, ::label, PhotosCrop.KEY_ROTATION_PX, R.string.photos_rotation_increment, listOf(
                0 to R.string.photos_step_smallest,
                1 to R.string.photos_step_1_scrub,
                24 to R.string.photos_step_24_rotate,
                25 to R.string.photos_step_25_rotate,
                27 to R.string.photos_step_27,
                28 to R.string.photos_step_28,
                30 to R.string.photos_step_30,
                32 to R.string.photos_step_32,
                36 to R.string.photos_step_36,
                40 to R.string.photos_step_40_rotate,
                48 to R.string.photos_step_48,
            ))
            addDragChoice(card, ::label, PhotosCrop.KEY_TRANSLATION_PX, R.string.photos_translation_increment, listOf(
                0 to R.string.photos_step_smallest,
                1 to R.string.photos_step_1_move,
                22 to R.string.photos_step_22,
                24 to R.string.photos_step_24_move,
                25 to R.string.photos_step_25_move,
                40 to R.string.photos_step_40_move,
            ))
        }
        if (tool == OverlayService.WIDGET_TABS) {
            val direction = label(getString(if (prefs.getBoolean(TabControls.REVERSE_SWIPE, false))
                R.string.tab_direction_reversed else R.string.tab_direction_default))
            card.addView(Switch(this).apply {
                text = getString(R.string.tab_reverse_swipe)
                isChecked = prefs.getBoolean(TabControls.REVERSE_SWIPE, false)
                setOnCheckedChangeListener { _, reversed ->
                    prefs.edit().putBoolean(TabControls.REVERSE_SWIPE, reversed).apply()
                    direction.text = getString(if (reversed) R.string.tab_direction_reversed else R.string.tab_direction_default)
                }
            })
        }
        val minimumSize = ToolSettings.minSize(tool)
        val maximumSize = ToolSettings.maxSize(tool)
        val sizeLabel = label(getString(R.string.button_size, ToolSettings.size(this, tool)))
        card.addView(SeekBar(this).apply {
            max = maximumSize - minimumSize
            progress = ToolSettings.size(this@MainActivity, tool) - minimumSize
            contentDescription = getString(R.string.size_description, minimumSize, maximumSize)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val size = progress + minimumSize
                    sizeLabel.text = getString(R.string.button_size, size)
                    prefs.edit().putInt("${tool}_size", size).apply()
                    updateAppearance()
                }
            })
        })
        if (tool == OverlayService.WIDGET_BRIGHTNESS) return
        card.addView(FloatingColorPicker(this, tool) { color ->
            prefs.edit().putInt("${tool}_color", color).apply()
            updateAppearance()
        })
    }

    private fun addDragChoice(
        card: LinearLayout,
        label: (String) -> TextView,
        key: String,
        title: Int,
        choices: List<Pair<Int, Int>>,
    ) {
        label(getString(title))
        val prefs = ToolSettings.prefs(this)
        val stored = prefs.getInt(key, 0)
        val selected = choices.indexOfFirst { it.first == stored }.coerceAtLeast(0)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                choices.map { getString(it.second) },
            )
            setSelection(selected)
            contentDescription = getString(title)
        }
        card.addView(spinner)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val px = choices[position].first
                if (px == prefs.getInt(key, 0)) return
                prefs.edit().putInt(key, px).apply()
            }
        }
    }

    private fun updateAppearance() {
        if (OverlayService.anyWidgetEnabled(this)) OverlayService.start(this)
    }

    private fun refreshControls() {
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        val overlayReady = overlayAllowed()
        val rotateReady = Settings.System.canWrite(this)
        playSwitch.setOnCheckedChangeListener(null)
        rotateSwitch.setOnCheckedChangeListener(null)
        brightnessSwitch.setOnCheckedChangeListener(null)
        tabsSwitch.setOnCheckedChangeListener(null)
        photosSwitch.setOnCheckedChangeListener(null)
        playSwitch.isChecked = overlayReady && prefs.getBoolean(OverlayService.KEY_PLAY_ENABLED, false)
        rotateSwitch.isChecked = overlayReady && rotateReady && prefs.getBoolean(OverlayService.KEY_ROTATE_ENABLED, false)
        playSwitch.isEnabled = overlayReady
        rotateSwitch.isEnabled = overlayReady && rotateReady
        brightnessSwitch.isChecked = overlayReady && rotateReady && prefs.getBoolean(OverlayService.KEY_BRIGHTNESS_ENABLED, false)
        brightnessSwitch.isEnabled = overlayReady && rotateReady
        tabsSwitch.isChecked = prefs.getBoolean(OverlayService.KEY_TABS_ENABLED, false)
        tabsSwitch.isEnabled = overlayReady && TabControls.accessibilityEnabled(this)
        photosSwitch.isChecked = prefs.getBoolean(OverlayService.KEY_PHOTOS_ENABLED, false)
        photosSwitch.isEnabled = overlayReady && PhotosCropService.accessibilityEnabled(this)
        playSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PLAY, enabled) }
        rotateSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_ROTATE, enabled) }
        brightnessSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_BRIGHTNESS, enabled) }
        tabsSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_TABS, enabled) }
        photosSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PHOTOS, enabled) }
    }

    private fun toggleWidget(widget: String, enabled: Boolean) {
        val needsWriteAccess = widget in listOf(OverlayService.WIDGET_ROTATE, OverlayService.WIDGET_BRIGHTNESS) && !Settings.System.canWrite(this)
        if (enabled && !overlayAllowed()) {
            Toast.makeText(this, R.string.overlay_permission_needed, Toast.LENGTH_SHORT).show()
            refreshControls()
            return
        }
        if (enabled && needsWriteAccess) {
            Toast.makeText(this, R.string.rotation_permission_needed, Toast.LENGTH_SHORT).show()
            refreshControls()
            return
        }
        if (enabled && widget == OverlayService.WIDGET_TABS && !TabControls.accessibilityEnabled(this)) {
            Toast.makeText(this, R.string.tab_accessibility_needed, Toast.LENGTH_LONG).show()
            refreshControls()
            return
        }
        if (enabled && widget == OverlayService.WIDGET_PHOTOS && !PhotosCropService.accessibilityEnabled(this)) {
            Toast.makeText(this, R.string.photos_accessibility_needed, Toast.LENGTH_LONG).show()
            refreshControls()
            return
        }
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(OverlayService.enabledKey(widget), enabled)
            .apply()
        if (OverlayService.anyWidgetEnabled(this)) OverlayService.start(this) else OverlayService.stop(this)
        refreshControls()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
