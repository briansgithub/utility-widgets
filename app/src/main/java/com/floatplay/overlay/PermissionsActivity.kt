package com.floatplay.overlay

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** All access controls live here; returning from Android settings refreshes the status. */
class PermissionsActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            setText(R.string.permissions_title)
            textSize = 24f
            setTextColor(0xFFF2F5FA.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply {
            setText(R.string.permissions_done)
            setOnClickListener { finish() }
        })
        screen.addView(header)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(24))
        }
        status = text(content, "")
        button(content, R.string.overlay_permission_button) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        }
        button(content, R.string.rotation_permission_button) {
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        }
        text(content, getString(R.string.media_access_needed))
        button(content, R.string.media_access_button) { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
        text(content, getString(R.string.tab_service_description))
        button(content, R.string.tab_accessibility_button) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        text(content, getString(R.string.photos_service_description))
        button(content, R.string.photos_accessibility_button) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        button(content, R.string.notifications_permission_button) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        if (Build.VERSION.SDK_INT >= 31 && !ExtraDim.available(this)) text(content, getString(R.string.extra_dim_access_needed, ExtraDim.GRANT_COMMAND)).setTextIsSelectable(true)
        screen.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        fun line(label: Int, allowed: Boolean) = getString(R.string.permission_status_line,
            getString(label), getString(if (allowed) R.string.permission_ready else R.string.permission_not_enabled))
        val lines = mutableListOf(
            line(R.string.permission_overlay, overlayAllowed()),
            line(R.string.permission_system_settings, Settings.System.canWrite(this)),
            line(R.string.permission_playback, listenerEnabled()),
            line(R.string.permission_browser_tabs, TabControls.accessibilityEnabled(this)),
            line(R.string.permission_photo_crop, PhotosCropService.accessibilityEnabled(this)),
            line(R.string.permission_notifications, getSystemService(NotificationManager::class.java).areNotificationsEnabled()),
        )
        if (Build.VERSION.SDK_INT >= 31) lines.add(line(R.string.permission_extra_dim, ExtraDim.available(this)))
        status.text = lines.joinToString("\n")
    }

    private fun text(parent: LinearLayout, value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFB6C2CF.toInt())
        setPadding(0, dp(12), 0, dp(12))
        parent.addView(this)
    }

    private fun button(parent: LinearLayout, label: Int, intent: () -> Intent) {
        parent.addView(Button(this).apply {
            setText(label)
            setOnClickListener { startActivity(intent()) }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
