# Float Tools

Android app with independently configurable floating play/pause and auto-rotate buttons, a brightness dial, Chrome/Firefox tab controls, and a Google Photos crop pad.

The integrated Float Tools app is at the repository root. Browser-tab controls are part of this app; the separate Tab Bubble project has been retired.

Enable Brightness in the app to drag to move, double-tap and drag around the dial for 0–100% manual brightness, or tap for automatic brightness. The app provides a 64–160 dp dial size slider. A sun icon indicates manual mode; a sun surrounded by an arrow indicates automatic mode. Brightness requires change-system-settings access.

Add **Float Tools** from your launcher's Widgets menu, or use **Add home-screen widget** in the app. By default:

- One tap toggles the floating play/pause button.
- Two taps toggle the floating auto-rotate button.

Repeat the same gesture to hide that tool. Keep consecutive taps less than 450 ms apart; the action runs 450 ms after the last tap so a double tap does not also toggle play/pause. Three taps toggle the brightness dial. The compact circular home-screen widget fits one launcher cell. All instances control the same tools. Unassigned tap counts do nothing except show a message.

**Four taps toggle Browser tabs** by default. In Float Tools, use the top-right **Permissions** button, then **Open tab-accessibility settings** to enable **Float Tools — browser tabs** once. This Android approval is separate from the old Tab Bubble app; disable that separate service when using the integrated tool to avoid duplicate widgets. The existing Float Tools home-screen widget does not need to be replaced. Existing custom tap assignments are preserved; if another tool already uses four taps, Browser tabs chooses an unused count, which can be changed in its card.

**Five taps toggle Photos crop** by default. Enable **Float Tools — photo crop** once from Permissions. The six-button pad appears only while Google Photos has Crop and rotate open. Clockwise and counterclockwise use the slider’s smallest step. Each direction moves the photo by the shortest drag Android still treats as a drag. If five taps is already assigned, Photos crop chooses an unused count.

Browser-tab controls appear only over regular Chrome or Firefox. Tap the circle to close the active tab; drag the circle to move the whole widget. Swipe up on the rectangle to open the browser’s tabs / tab-groups viewer; the rectangle no longer moves the widget, so reposition it only by touching and dragging the circle. A mostly horizontal rectangle swipe selects **next on left, previous on right** by default. Turn on **Reverse left/right tab swipes** in the Browser tabs card for the opposite mapping. This preference persists and applies to both browsers. Direction locking, immediate circle taps, and the 1 ms injected toolbar gesture are retained. Keep the browser address bar visible. The 1 ms value is the injected stroke duration, not a guarantee about browser animation or reliable recognition in every browser version.

The notification's hide-all action and long-press hide-all gesture also disable Browser tabs and Photos crop. Accessibility permission can expose screen content; the service reads native browser controls on demand and does not save or transmit page content. The app has no internet permission. See [browser-tab validation](BROWSER_TABS.md) for exact checks and remaining device validation.

The dark app screen provides independent tap counts (1–5, unique per tool), 40–96 dp size sliders and color pickers for Media, Rotate, Browser tabs, and Photos crop. Brightness retains its 64–160 dp dial slider. Browser-tab size scales the circle and rectangle together; its selected color applies to both, with contrasting text on light colors. Appearance changes apply to visible controls and persist across app restarts. To swap tap counts, temporarily assign one tool an unused count.

Default colors match the selections captured from the Pixel 9 Pro: Media `#16168D`, Rotate `#FFC926`, and Browser tabs `#919191`. Photos crop defaults to `#188038`. These defaults apply when a tool has no saved color; custom saved colors continue to take precedence.

All access controls are under the **Permissions** button in the fixed top-right header. The page includes overlay access, change-system-settings access, playback-state access, browser-tab accessibility, photo-crop accessibility, notifications, and optional Extra Dim status/help. Status refreshes when returning from Android settings. Permission controls are no longer placed after the tool cards or inside Browser tabs.

Grant display-over-other-apps permission before using the widget. Auto-rotate also needs change-system-settings access. Drag floating buttons to move them; long-press a floating button or tap the ongoing notification to hide all tools. You can also toggle each tool directly in the app.

Package/application ID remains `com.floatplay.overlay` so existing installations and preferences are preserved. Min SDK: 30.

Build: `./gradlew.bat :app:assembleDebug :app:lintDebug`

The brightness dial reserves its bottom 20% for progressively stronger Android Extra Dim. Above that range, Extra Dim turns off and the dial controls normal brightness. The center auto-mode icon is compact. Extra Dim needs Android 12+ and a one-time connected-computer grant: `adb shell pm grant com.floatplay.overlay android.permission.WRITE_SECURE_SETTINGS`. Without this optional grant, the dial retains its normal brightness range. Enabling automatic brightness clears Extra Dim.

The normal-brightness section uses Android’s perceptual brightness curve and the built-in display configuration’s high-brightness transition as its manual maximum. This keeps maximum manual brightness at the end of the dial, rather than saturating midway. If a device does not expose a readable built-in display configuration, the app falls back to the full standard brightness range.
