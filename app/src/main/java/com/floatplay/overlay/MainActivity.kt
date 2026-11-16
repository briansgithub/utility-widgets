package com.floatplay.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var playSwitch: Switch
    private lateinit var rotateSwitch: Switch
    private lateinit var brightnessSwitch: Switch
    private lateinit var accessStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::playSwitch.isInitialized) refreshControls()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(36), dp(24), dp(24))
            setBackgroundColor(0xFFF5F5F5.toInt())
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 26f
            setTextColor(0xFF171717.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.widget_screen_description)
            textSize = 15f
            setTextColor(0xFF555555.toInt())
            setPadding(0, 0, 0, dp(24))
        })

        playSwitch = addWidgetRow(root, R.string.play_widget_title, R.string.play_widget_description)
        rotateSwitch = addWidgetRow(root, R.string.rotate_widget_title, R.string.rotate_widget_description)
        brightnessSwitch = addWidgetRow(root, R.string.brightness_widget_title, R.string.brightness_widget_description)
        accessStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF555555.toInt())
            setPadding(0, dp(14), 0, dp(10))
        }
        root.addView(accessStatus)
        root.addView(android.widget.Button(this).apply {
            text = getString(R.string.overlay_permission_button)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        root.addView(android.widget.Button(this).apply {
            text = getString(R.string.rotation_permission_button)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        val scroll = ScrollView(this).apply { addView(root) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        refreshControls()
        playSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PLAY, enabled) }
        rotateSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_ROTATE, enabled) }
    }

    private fun addWidgetRow(root: LinearLayout, title: Int, description: Int): Switch {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val label = TextView(this).apply {
            text = getString(title)
            textSize = 18f
            setTextColor(0xFF171717.toInt())
        }
        card.addView(label)
        card.addView(TextView(this).apply {
            text = getString(description)
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, dp(4), 0, dp(8))
        })
        val toggle = Switch(this).apply { text = getString(R.string.widget_enabled) }
        card.addView(toggle)
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return toggle
    }

    private fun refreshControls() {
        val prefs = getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE)
        val overlayReady = overlayAllowed()
        val rotateReady = Settings.System.canWrite(this)
        playSwitch.setOnCheckedChangeListener(null)
        rotateSwitch.setOnCheckedChangeListener(null)
        brightnessSwitch.setOnCheckedChangeListener(null)
        playSwitch.isChecked = overlayReady && prefs.getBoolean(OverlayService.KEY_PLAY_ENABLED, false)
        rotateSwitch.isChecked = overlayReady && rotateReady && prefs.getBoolean(OverlayService.KEY_ROTATE_ENABLED, false)
        brightnessSwitch.isChecked = overlayReady && rotateReady && prefs.getBoolean(OverlayService.KEY_BRIGHTNESS_ENABLED, false)
        playSwitch.isEnabled = overlayReady
        rotateSwitch.isEnabled = overlayReady && rotateReady
        brightnessSwitch.isEnabled = overlayReady && rotateReady
        accessStatus.text = when {
            !overlayReady -> getString(R.string.overlay_permission_needed)
            !rotateReady -> getString(R.string.rotation_permission_needed)
            else -> getString(R.string.all_permissions_ready)
        }
        playSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_PLAY, enabled) }
        rotateSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_ROTATE, enabled) }
        brightnessSwitch.setOnCheckedChangeListener { _, enabled -> toggleWidget(OverlayService.WIDGET_BRIGHTNESS, enabled) }
    }

    private fun toggleWidget(widget: String, enabled: Boolean) {
        val needsWriteAccess = widget != OverlayService.WIDGET_PLAY && !Settings.System.canWrite(this)
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
        getSharedPreferences(OverlayService.PREFS, MODE_PRIVATE).edit()
            .putBoolean(OverlayService.enabledKey(widget), enabled)
            .apply()
        if (OverlayService.anyWidgetEnabled(this)) OverlayService.start(this) else OverlayService.stop(this)
        refreshControls()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
