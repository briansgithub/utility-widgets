# Tab Bubble

Standalone source retained alongside Float Tools. The main app is in the repository root.

An Android app for a Pixel 9 Pro: a floating close-tab circle with a swipe rectangle underneath controls Chrome and Firefox. Android 11 or newer. English browser menus; stable packages `com.android.chrome` and `org.mozilla.firefox`.

## Install and use

1. Copy `dist/TabBubble-0.8.apk` to the phone and open it. Allow installation from the app used to open the file if Android asks.
2. Open Tab Bubble, read the accessibility disclosure, and tap **Open Accessibility settings**.
3. Find **Tab Bubble** under downloaded/installed accessibility apps and enable it.
4. Open Chrome or Firefox and show its address bar. Tap the circle to close the current tab. Drag the circle to move both parts of the widget. Swipe the rectangle mostly horizontally left for the next tab or right for the previous tab. Start a rectangle drag vertically or diagonally to move the whole widget instead. Rectangle taps do nothing. The initial direction chooses the action and stays locked until release; there is no hold or double-tap timer. Turn it off in Tab Bubble or disable its accessibility service.

For a sideloaded app, Android may require **App info → top-right menu → Allow restricted settings** before enabling accessibility. The exact wording varies by Android version.

## Behavior and limits

The service adds a nonfocusable accessibility overlay only over the supported browser packages. A tap long-presses the native tab counter through its accessibility action, waits up to 2.2 seconds for its shortcut menu, then clicks the exact **Close tab** or **Close current tab** item when **New tab** is also present. It never uses a grid position, Back, keyboard injection, root, or a Close all tabs command. A failed or changed interface produces a message and stops. A menu may remain open after a failed attempt.

Swiping the rectangle dispatches a 1 ms horizontal gesture within the detected native address bar, reduced from 60 ms in versions 0.4–0.6 and 260 ms in version 0.3. This is the minimum positive whole-millisecond duration accepted by Android's stroke API. The default maps rectangle left to next tab and rectangle right to previous tab, matching the browser toolbar's physical swipe direction. The toolbar's current on-screen bounds are read each time, supporting top and bottom address bars. The overlay temporarily stops intercepting touches during that gesture, including when positioned over the toolbar. Browser-native animation, input scheduling and menu rendering still take time; there is no added double-tap recognition wait. Stroke duration is not measured total visual transition time. End-of-list and tab-group behavior follows the browser. The new 1 ms swipe is left to the user to validate on-device.

The browser UI can briefly show its shortcut menu. Browser updates can break compatibility. The toolbar must be visible. Tab overview, fullscreen, custom tabs in other apps, other browser editions, and non-English menus are excluded from this first version. Private browsing is not separately validated. Browser updates, private tabs, tab groups, hidden toolbars, rotation, keyboard display and switching apps during a tap need device checks before relying on this app.

Accessibility permission can expose screen contents. The app inspects native browser controls on demand, skips WebView/GeckoView subtrees, stores only visibility and position, and contains no internet permission, analytics, or screen-content logging. Window-change events from other apps are used to hide the button. This is a personal sideload prototype; no Play Store submission is included.

## Build

Open this folder in Android Studio, use its bundled Java runtime, and install Android SDK 36. Run `gradlew.bat :app:assembleDebug :app:lintDebug`. The APK is debug-signed and intended for testing. Keep the same development signing key for updates; production signing and release distribution are not configured.

The standalone matching checks need no dependencies:

```powershell
javac -d app/build/rule-check app/src/main/java/com/tabubble/android/BrowserRules.java app/src/main/java/com/tabubble/android/BubbleGestures.java checks/BrowserRulesCheck.java checks/BubbleGesturesCheck.java
java -cp app/build/rule-check com.tabubble.android.BrowserRulesCheck
java -cp app/build/rule-check com.tabubble.android.BubbleGesturesCheck
```

## Device acceptance checks

Version 0.8 is installed on the Pixel 9 Pro, with the default direction flipped to left-next/right-previous. Build/lint, APK signature verification and all 42 gesture safety checks passed. Float Tools 1.1 (`..`) now integrates this widget as a fourth tool, enabled by four taps on its existing home-screen widget, with a persistent reverse-direction toggle in its Browser tabs card. Float Tools needs its own user-enabled accessibility approval. Disable the standalone Tab Bubble service when using the integrated widget to avoid duplicates; the agent did not change either accessibility grant. See `../BROWSER_TABS.md` for setup and validation.

Phone testing is being performed by the user; no screen interaction was performed after the user requested to test it themselves. Version 0.5's upward rectangle movement was checked on Chrome before that handoff. Version 0.4 previously passed both 60 ms horizontal directions in Chrome and Firefox; the 1 ms duration and newly flipped mapping have not been verified in either browser by the agent. Two disposable Chrome example tabs were left open at the handoff for user testing. See `VALIDATION.md` for the exact completed checks and limitations.

Remaining checks: history within a disposable tab, tab groups, private tabs, list boundaries, rapid repeated gestures, rotation, hidden toolbars, overlapping the toolbar, keyboard display, the visibility switch, and switching apps during an operation. Use the browser's undo/recently closed tabs where available.

## References

- [Android accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Android accessibility overlays](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
- [Firefox tab counter menu source](https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/android-components/components/ui/tabcounter/src/main/java/mozilla/components/ui/tabcounter/TabCounterMenu.kt)
