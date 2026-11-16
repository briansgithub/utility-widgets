package com.tabubble.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class TabAccessibilityService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private WindowManager.LayoutParams position;
    private Button bubble;
    private LinearLayout widget;
    private SharedPreferences prefs;
    private String pendingPackage;
    private long deadline;
    private final Runnable retry = this::tryClose;
    private BubbleGestures gestures;
    private boolean switching;
    private int switchGeneration;
    private Runnable swipeStart;

    @Override protected void onServiceConnected() {
        manager = getSystemService(WindowManager.class);
        prefs = getSharedPreferences("bubble", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
        ViewConfiguration config = ViewConfiguration.get(this);
        gestures = new BubbleGestures(config.getScaledTouchSlop(), dp(40));
        updateVisibility();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null) return;
        updateVisibility();
        if (pendingPackage != null) { handler.removeCallbacks(retry); handler.post(retry); }
    }
    private AccessibilityNodeInfo activeApp() {
        // The overlay is never focusable, so the active application stays the browser.
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isActive()) return window.getRoot();
        }
        return getRootInActiveWindow();
    }
    private void updateVisibility() {
        AccessibilityNodeInfo root = activeApp();
        // Browser popup transitions can temporarily have no accessible active root.
        if (root == null && (pendingPackage != null || switching)) return;
        boolean visible = root != null && BrowserRules.supported(root.getPackageName()) && prefs.getBoolean("visible", true);
        if (visible) showBubble();
        else { cancel(); hideBubble(); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void showBubble() {
        if (bubble != null) return;
        int size = dp(56);
        position = new WindowManager.LayoutParams(dp(112), dp(104), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        position.gravity = Gravity.TOP | Gravity.LEFT;
        position.x = prefs.getInt("x", screen().width() - position.width - dp(16));
        position.y = prefs.getInt("y", screen().height() * 2 / 3);
        if (prefs.contains("x") && prefs.getInt("layout", 1) < 2) position.x -= dp(28);
        clamp();
        prefs.edit().putInt("layout", 2).putInt("x", position.x).putInt("y", position.y).apply();
        widget = new LinearLayout(this);
        widget.setOrientation(LinearLayout.VERTICAL);
        widget.setGravity(Gravity.CENTER_HORIZONTAL);
        widget.setClipChildren(false);
        bubble = new Button(this);
        bubble.setText("×"); bubble.setTextSize(30); bubble.setTextColor(0xffffffff);
        bubble.setContentDescription("Close current browser tab. Drag this circle to move the whole widget.");
        bubble.setPadding(0, 0, 0, 0); bubble.setMinWidth(0); bubble.setMinHeight(0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff275d5a); background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(2), 0xffe4f4ee); bubble.setBackground(background);
        bubble.setElevation(dp(6));
        bubble.setOnClickListener(v -> beginClose());
        widget.addView(bubble, new LinearLayout.LayoutParams(size, size));
        attachTouch(bubble, BubbleGestures.Zone.CIRCLE);
        TextView strip = new TextView(this);
        strip.setText("‹  Swipe tabs  ›"); strip.setTextSize(14); strip.setTextColor(0xffffffff);
        strip.setGravity(Gravity.CENTER);
        strip.setContentDescription("Swipe horizontally left for next tab, right for previous tab. Start dragging vertically or diagonally to move the whole widget.");
        GradientDrawable stripBackground = new GradientDrawable();
        stripBackground.setColor(0xff275d5a); stripBackground.setCornerRadius(dp(8));
        stripBackground.setStroke(dp(2), 0xffe4f4ee); strip.setBackground(stripBackground);
        LinearLayout.LayoutParams stripLayout = new LinearLayout.LayoutParams(dp(112), dp(44));
        stripLayout.topMargin = dp(4);
        widget.addView(strip, stripLayout);
        attachTouch(strip, BubbleGestures.Zone.STRIP);
        manager.addView(widget, position);
    }
    private void attachTouch(View target, BubbleGestures.Zone zone) {
        target.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (pendingPackage != null || switching) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX(); downY = event.getRawY();
                        startX = position.x; startY = position.y;
                        gestures.down(zone, downX, downY);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                        gestures.move(event.getRawX(), event.getRawY());
                        if (gestures.isDragging()) {
                            position.x = startX + Math.round(dx); position.y = startY + Math.round(dy);
                            clamp(); manager.updateViewLayout(widget, position);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        BubbleGestures.Result result = gestures.up(event.getRawX(), event.getRawY());
                        switch (result) {
                            case DRAGGED:
                                position.x = startX + Math.round(event.getRawX() - downX);
                                position.y = startY + Math.round(event.getRawY() - downY);
                                clamp(); manager.updateViewLayout(widget, position);
                                prefs.edit().putInt("x", position.x).putInt("y", position.y).apply(); break;
                            case CLOSE: view.performClick(); break;
                            case LEFT: switchTab(false); break;
                            case RIGHT: switchTab(true); break;
                            default: break;
                        }
                        return true;
                    case MotionEvent.ACTION_POINTER_DOWN:
                    case MotionEvent.ACTION_CANCEL:
                        gestures.cancel(); return true;
                    default: return true;
                }
            }
        });
    }
    private Rect screen() { return manager.getCurrentWindowMetrics().getBounds(); }
    private void clamp() {
        Rect bounds = screen();
        position.x = Math.max(dp(8), Math.min(position.x, bounds.width() - position.width - dp(8)));
        position.y = Math.max(dp(32), Math.min(position.y, bounds.height() - position.height - dp(56)));
    }
    private void hideBubble() {
        if (gestures != null) gestures.cancel();
        if (widget != null) { manager.removeView(widget); widget = null; bubble = null; }
    }
    private void beginClose() {
        if (pendingPackage != null || switching) return;
        AccessibilityNodeInfo root = activeApp();
        if (root == null || !BrowserRules.supported(root.getPackageName())) return;
        for (AccessibilityNodeInfo node : controls(root)) {
            if (BrowserRules.tabButton(node.getViewIdResourceName(), node.getContentDescription())) {
                pendingPackage = root.getPackageName().toString();
                Log.d("TabBubble", "Close requested");
                deadline = SystemClock.uptimeMillis() + 2200;
                if (act(node, AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    widget.setAlpha(0.5f); handler.post(retry); return;
                }
                cancel();
            }
        }
        message("Couldn’t find the tab shortcut. Show the browser toolbar and try again.");
    }
    private void switchTab(boolean previous) {
        if (pendingPackage != null || switching) return;
        AccessibilityNodeInfo root = activeApp();
        Rect bar = addressBarBounds(root);
        if (bar == null) {
            message("Show the browser address bar and finish editing before switching tabs.");
            return;
        }
        String pkg = root.getPackageName().toString();
        switching = true;
        int generation = ++switchGeneration;
        widget.setAlpha(0.5f);
        // Allow the native toolbar to receive the gesture even if the bubble sits over it.
        position.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        manager.updateViewLayout(widget, position);
        swipeStart = () -> {
            AccessibilityNodeInfo current = activeApp();
            Rect bounds = addressBarBounds(current);
            if (current == null || !pkg.contentEquals(String.valueOf(current.getPackageName())) || bounds == null) {
                finishSwitch(generation); return;
            }
            // Native toolbar motion is right for previous, left for next.
            float left = bounds.left + bounds.width() * 0.125f;
            float right = bounds.right - bounds.width() * 0.125f;
            Path path = new Path();
            path.moveTo(previous ? left : right, bounds.exactCenterY());
            path.lineTo(previous ? right : left, bounds.exactCenterY());
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 1)).build();
            boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) { finishSwitch(generation); }
                @Override public void onCancelled(GestureDescription description) { finishSwitch(generation); }
            }, handler);
            if (!accepted) {
                finishSwitch(generation);
                message("Android did not accept the tab-switch gesture. Re-enable Tab Bubble in Accessibility settings.");
            }
        };
        // Begin only after the finger that swiped the bubble has lifted.
        handler.post(swipeStart);
    }
    private Rect addressBarBounds(AccessibilityNodeInfo root) {
        if (root == null || !BrowserRules.supported(root.getPackageName())) return null;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus != null && focus.isEditable()) return null;
        for (AccessibilityNodeInfo node : controls(root)) {
            if (BrowserRules.addressBar(root.getPackageName(), node.getViewIdResourceName())) {
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                if (bounds.intersect(screen()) && bounds.width() >= dp(96) && bounds.height() >= dp(24)) return bounds;
            }
        }
        return null;
    }
    private void finishSwitch(int generation) {
        if (generation != switchGeneration) return;
        switching = false;
        swipeStart = null;
        if (widget != null) {
            if ((position.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                position.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                manager.updateViewLayout(widget, position);
            }
            widget.setAlpha(1f);
        }
    }
    private void tryClose() {
        if (pendingPackage == null) return;
        AccessibilityNodeInfo root = activeApp();
        if (root == null) {
            if (SystemClock.uptimeMillis() < deadline) handler.postDelayed(retry, 60);
            else { Log.d("TabBubble", "Close timed out while waiting for active window"); cancel(); }
            return;
        }
        if (!pendingPackage.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            Log.d("TabBubble", "Close cancelled after application changed");
            cancel(); return;
        }
        List<AccessibilityNodeInfo> nodes = controls(root);
        // Only act inside the tab shortcut menu, never a tab grid or web page.
        boolean shortcutMenu = false;
        for (AccessibilityNodeInfo node : nodes) {
            if (BrowserRules.newTabLabel(node.getText()) || BrowserRules.newTabLabel(node.getContentDescription())) shortcutMenu = true;
        }
        if (shortcutMenu) for (AccessibilityNodeInfo node : nodes) {
            if (BrowserRules.closeLabel(node.getText()) || BrowserRules.closeLabel(node.getContentDescription())) {
                // Stop retries before the click: a tap must never close two tabs.
                cancel();
                boolean clicked = act(node, AccessibilityNodeInfo.ACTION_CLICK);
                Log.d("TabBubble", "Close menu action accepted: " + clicked);
                if (!clicked) message("The browser did not accept Close tab.");
                return;
            }
        }
        if (SystemClock.uptimeMillis() >= deadline) {
            Log.d("TabBubble", "Close timed out while waiting for menu controls");
            cancel(); message("Close tab wasn’t available. This browser version may need an update to Tab Bubble.");
        } else handler.postDelayed(retry, 60);
    }
    private List<AccessibilityNodeInfo> controls(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result = new ArrayList<>(), queue = new ArrayList<>();
        queue.add(root);
        for (int i = 0; i < queue.size() && i < 800; i++) {
            AccessibilityNodeInfo node = queue.get(i);
            if (node == null || !node.isVisibleToUser()) continue;
            String name = String.valueOf(node.getClassName());
            if (name.contains("WebView") || name.contains("GeckoView") || node.isEditable()) continue;
            result.add(node);
            for (int child = 0; child < node.getChildCount() && queue.size() < 800; child++) queue.add(node.getChild(child));
        }
        return result;
    }
    private boolean act(AccessibilityNodeInfo node, int action) {
        for (int depth = 0; node != null && depth < 4; depth++, node = node.getParent()) {
            if (node.isEnabled() && node.performAction(action)) return true;
        }
        return false;
    }
    private void cancel() {
        if (gestures != null) gestures.cancel();
        if (swipeStart != null) handler.removeCallbacks(swipeStart);
        finishSwitch(switchGeneration);
        switchGeneration++;
        handler.removeCallbacks(retry); pendingPackage = null;
        if (widget != null) widget.setAlpha(1f);
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if ("visible".equals(key)) updateVisibility();
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (widget != null) { clamp(); manager.updateViewLayout(widget, position); }
    }
    @Override public void onInterrupt() { cancel(); hideBubble(); }
    @Override public void onDestroy() {
        cancel(); hideBubble();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
