# Float Tools

Android app with independently configurable floating play/pause and auto-rotate buttons, plus a brightness dial.

Enable Brightness in the app to turn the dial for 0–100% manual brightness, tap for automatic brightness, or double-tap and hold then drag to move. Brightness requires change-system-settings access.

Add **Float Tools** from your launcher's Widgets menu, or use **Add home-screen widget** in the app. By default:

- One tap toggles the floating play/pause button.
- Two taps toggle the floating auto-rotate button.

Repeat the same gesture to hide that tool. Keep consecutive taps less than 450 ms apart; the action runs 450 ms after the last tap so a double tap does not also toggle play/pause. All instances of the home-screen widget control the same tools. Unassigned tap counts do nothing except show a message.

The dark app screen provides independent tap counts (1–5, unique per tool), button sizes (40–96 dp), and RGB colors for both tools. Appearance changes apply immediately to visible buttons and are saved across app restarts. To swap tap counts, temporarily assign one tool an unused count.

Grant display-over-other-apps permission before using the widget. Auto-rotate also needs change-system-settings access. Drag floating buttons to move them; long-press a floating button or tap the ongoing notification to hide all tools. You can also toggle each tool directly in the app.

Package/application ID remains `com.floatplay.overlay` so existing installations and preferences are preserved. Min SDK: 30.

Build: `./gradlew.bat :app:assembleDebug :app:lintDebug`
