# Browser tabs integration

Float Tools 1.3 (`com.floatplay.overlay`, version code 4) includes the standalone tab widget as a fourth tool. The existing home-screen widget's tap resolver includes Browser tabs, defaulting to four taps with the normal 450 ms multi-tap counting window. There is no additional timer on the floating controls. Existing custom tap assignments are preserved; a conflict with four taps falls back to an unused count.

Default rectangle direction is **left → next, right → previous**. The Browser tabs card includes **Reverse left/right tab swipes**, stored in `floatplay.xml` as `tabs_reverse_swipe` (default false). The selected direction is read for every horizontal action. Widget visibility is stored as `tabs_widget_enabled` alongside the other tools; position is stored separately in `browser_tabs.xml`. Notification/long-press hide-all clears the tab flag too. Changing direction refreshes the widget's accessibility description and cancels any pending operation. Disabling the tab tool cancels pending operations and hides it immediately.

The copied accessibility implementation retains the circle/rectangle layout, vertical and diagonal movement, direction locking, exact native browser-control matching, popup handling and 1 ms toolbar stroke. Its Android accessibility approval is specific to Float Tools and cannot be inherited from Tab Bubble. The disclosure and link to Android Accessibility settings are now on the Permissions page. The service is protected by `BIND_ACCESSIBILITY_SERVICE`; no internet permission was added.

The standalone Tab Bubble project has been retired after its controls were folded into Float Tools. Its source copies and local project artifacts were removed with user authorization. The integrated implementation and direction toggle remain in Float Tools. An older standalone installation on a phone can be disabled or uninstalled separately.

The checkout contained existing uncommitted brightness, media, appearance and settings changes. Those were retained and committed in logical groups. Temporary comparison snapshots were removed during the authorized standalone-project cleanup. Existing mixed Windows/UTF-8 characters in the string resources and README were normalized to valid UTF-8 while preserving their text; no existing resource text was changed except the introduction of the fourth tool in the widget instructions.

## Validation

From the Float Tools project, using Android Studio's bundled Java runtime, SDK 36 and the existing Gradle cache:

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Unit coverage includes all four combinations of swipe direction/preference, four taps by default, preserving an existing four-tap assignment, 42 gesture safety checks and 31 native-control matching checks. The six existing brightness range tests are also included. Native phone testing is left to the user, following their earlier instruction to test personally. Do not imply four real home-screen taps, service enablement or the direction toggle have been exercised on-device by the agent.

The initial and final test/build/lint runs passed: 14 JUnit tests total (6 existing brightness tests and 8 tab-control tests), zero failures/errors. The two tab regression methods execute 42 gesture and 31 browser matching assertions. The final run includes the disable-handler and text-encoding adjustments. `git diff --check` passed for the touched pre-existing files after correcting README line endings.

Both packaged APKs passed `apksigner.bat verify --verbose` with APK signature scheme v2. Installation on the identified Pixel 9 Pro returned **Success** for both commands:

```powershell
adb -t 52 install -r dist/FloatTools-1.1.apk
adb -t 52 install -r C:/Users/user1/Desktop/Projects/tab-bubble/dist/TabBubble-0.8.apk
```

No screen inspection, UI input, accessibility grant, setting toggle or browser interaction was performed on the phone for this integration. Float Tools' new accessibility service remains for the user to enable; the separate Tab Bubble service was not disabled automatically. The installed standalone update already has the flipped default while that setup is pending. No app was uninstalled or existing preferences cleared.

Standalone validation:

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:lintDebug --console=plain
javac -d app/build/rule-check app/src/main/java/com/tabubble/android/BubbleGestures.java checks/BubbleGesturesCheck.java
java -cp app/build/rule-check com.tabubble.android.BubbleGesturesCheck
```

Result: standalone build/lint passed and all 42 gesture safety checks passed after renaming the raw gesture results LEFT/RIGHT and updating the default mapping.

## User setup and device checks

1. In Float Tools → **Permissions** (top right), open tab-accessibility settings and enable **Float Tools — browser tabs**.
2. Disable the standalone **Tab Bubble** accessibility service to avoid duplicate overlays.
3. Tap the existing Float Tools home-screen widget four times, with less than 450 ms between taps; open Chrome or Firefox to see the controls. Repeat four taps to hide them.
4. Verify the new default mapping and switch **Reverse left/right tab swipes** on and off in Float Tools. Verify it persists across reopening the app and works in both browsers.
5. Verify close, circle drag, vertical/diagonal rectangle drag, disable, and notification hide-all using disposable tabs. The 1 ms gesture has not been validated on-device by the agent.

## Appearance and Permissions page, version 1.2

Browser tabs now uses the same 40–96 dp size slider and color picker as Media and Rotate. `tabs_size` and `tabs_color` use the existing shared preferences and clamp/default logic. The tab service scales the circle, rectangle, gap, text and decoration together, with a minimum 32 dp rectangle height. The selected fill color applies to both controls and light fills use dark text. Appearance changes cancel pending actions and recreate/clamp the widget using its current saved position; they do not modify swipe direction or timing. The color picker's preview shows the close-circle icon.

The main activity has a fixed header with a top-right **Permissions** button, with tool settings scrolling below it. The new non-exported `PermissionsActivity` contains all access links/status and the tab accessibility disclosure: overlay, system settings, playback state, tab accessibility, notifications, and optional Extra Dim help. The page refreshes status on resume. No permission is granted automatically; the main screen's Add home-screen widget button remains because it is not a permission control.

Validation command:

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

Result: **BUILD SUCCESSFUL**, 15 JUnit tests, zero failures/errors (6 brightness tests and 9 browser-tab tests). The added check verifies the tab tool shares the media tool's size range; existing direction, gesture, browser matching and brightness checks passed. The final `./gradlew.bat --offline :app:assembleDebug :app:lintDebug --console=plain` resource/compile/lint check also passed after the tab-specific color preview was added. UI appearance, live resizing/recoloring, permission-page navigation and persistence on the phone are left for user testing. No automated screen interaction was performed.

`git diff --check` passed for the touched tracked files. `apksigner.bat verify --verbose dist/FloatTools-1.2.apk` returned **Verifies** (signature scheme v2). `adb -t 52 install -r dist/FloatTools-1.2.apk` returned **Success** on the Pixel 9 Pro. Permission grants and saved tool settings were not changed by the agent.

## Current colors as defaults, version 1.3

Read the Pixel 9 Pro's `com.floatplay.overlay` preferences with `adb -t 52 shell run-as com.floatplay.overlay cat shared_prefs/floatplay.xml`, reporting only color entries. Captured values: Play/Pause `#16168D` (-15329651), Rotate `#FFC926` (-14042), Browser tabs `#919191` (-7237231). `ToolSettings.defaultColor(tool)` now supplies those values when there is no saved `${tool}_color`. Existing saved colors continue to override defaults. Brightness has no configurable saved color in the current app and its rendering is unaffected.

`./gradlew.bat --offline :app:assembleDebug :app:lintDebug --console=plain` passed. No new tests were added for this constant/default-only change, and the existing unit suite was not rerun. `git diff --check` passed for the touched tracked files, `apksigner.bat verify --verbose dist/FloatTools-1.3.apk` returned **Verifies** (scheme v2), and `adb -t 52 install -r dist/FloatTools-1.3.apk` returned **Success**. No phone screen interaction or preference writes were performed.

## Source publication checks

Float Tools changes were grouped into brightness/customization, media/home-screen widget, browser tabs, appearance/permissions, and default-color commits. A standalone source copy was initially published before the user's destination reply arrived, then removed in a normal cleanup commit after the user requested keeping it out of the current repository and subsequently authorized deleting the obsolete local project. Earlier commits retain that historical copy; published history was not rewritten. Local settings, generated builds/APKs, logs, private phone captures and temporary task helpers were never added to Git.

Before publication, the following checks passed on the current final source:

```powershell
# Repository root: 15 JUnit tests, zero failures/errors, app build and lint passed.
.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain

# Repository root: device-test APK compiled; no device tests were executed.
.\gradlew.bat --offline :app:assembleDebugAndroidTest --console=plain

# Historical standalone check before its retirement: build/lint passed without local.properties.
.\gradlew.bat --offline :app:assembleDebug :app:lintDebug --console=plain
```

No phone interaction was performed during publication. The existing device-test sources were compiled only. The standalone build command above records its successful validation before the obsolete project was removed; Float Tools is now the maintained app.

## Retired standalone project cleanup

Removed the tracked `standalone-tab-bubble/` copy, its ignored local build files, and the original `C:\Users\user1\Desktop\Projects\tab-bubble` folder, including temporary captures, APKs and task helpers, after explicit user authorization. Cleanup targets were resolved and checked to remain inside `C:\Users\user1\Desktop\Projects` before recursive removal. Float Tools' source and packaged builds were retained. Only standalone files and documentation changed, so application tests were not rerun; the existing passing Float Tools build/test results above still apply. The Pixel was disconnected, so no phone apps or settings were changed during cleanup.
