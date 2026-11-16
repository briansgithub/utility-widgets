package com.tabubble.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 48, 28, 32);
        content.setBackgroundColor(Color.rgb(245, 249, 248));
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(28 + bars.left, 28 + bars.top, 28 + bars.right, 28 + bars.bottom);
            return insets;
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        text(content, "Tab Bubble", 32);
        text(content, "Close tabs. Swipe between them.", 19);
        status = text(content, "", 17);
        text(content, "1. Enable Tab Bubble in Accessibility settings.\n2. Open Chrome or Firefox and show the address bar.\n3. Tap the × circle to close the current tab.\n4. Drag the circle to move the whole widget.\n5. Swipe the rectangle horizontally left for the next tab, right for the previous tab.\n6. Start dragging the rectangle vertically or diagonally to move the whole widget. The initial drag direction chooses movement or tab switching, with no hold or double-tap wait.", 17);
        text(content, "Accessibility access", 22);
        text(content, getString(R.string.service_description), 16);
        Button enable = new Button(this);
        enable.setText("Open Accessibility settings");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(enable);
        Switch visible = new Switch(this);
        visible.setText("Show floating button");
        visible.setChecked(getSharedPreferences("bubble", MODE_PRIVATE).getBoolean("visible", true));
        visible.setPadding(0, 24, 0, 24);
        visible.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("bubble", MODE_PRIVATE).edit().putBoolean("visible", checked).apply());
        content.addView(visible);
        text(content, "Prototype · English browser menus\nWorks with the regular Chrome and Firefox Android apps. Open the browser toolbar before tapping if it is hidden. Tab overview, custom tabs inside other apps, and fullscreen browsing are not supported. Browser updates may require adjustments.\n\nIf Android blocks enabling the service after installation, open Tab Bubble’s App info, use the top-right menu and allow restricted settings, then return here.", 15);
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(26, 49, 48));
        view.setPadding(0, 12, 0, 12); parent.addView(view); return view;
    }
    @Override protected void onResume() {
        super.onResume();
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        ComponentName expected = new ComponentName(this, TabAccessibilityService.class);
        boolean enabled = false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (expected.equals(ComponentName.unflattenFromString(info.getId()))) enabled = true;
        }
        status.setText(enabled ? "● Enabled — open Chrome or Firefox" : "○ Setup needed — enable accessibility below");
    }
}
