# Prototype validation

Run from `C:\Users\user1\Desktop\Projects\tab-bubble` with Android Studio's bundled Java runtime and the existing Gradle cache.

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:lintDebug --console=plain
```

Result for version 0.8: **BUILD SUCCESSFUL**. Lint: zero errors, eight warnings (SDK 36 versus 37, the API 31 service attribute on minimum API 30, backup rules recommendation, three English-only text literals, and absolute drag-coordinate gravity). Circle taps invoke `performClick()` on release with no double-tap timer. Circle drags and rectangle gestures never invoke the close click.

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe' -d app/build/rule-check app/src/main/java/com/tabubble/android/BrowserRules.java app/src/main/java/com/tabubble/android/BubbleGestures.java checks/BrowserRulesCheck.java checks/BubbleGesturesCheck.java
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp app/build/rule-check com.tabubble.android.BrowserRulesCheck
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp app/build/rule-check com.tabubble.android.BubbleGesturesCheck
```

Last run in version 0.3: **31 browser control matching checks and 21 gesture safety checks passed**. These cover supported package filtering, native address-bar identifiers, tab-counter recognition, exact close labels, rejection of close-all/close-other commands, immediate circle taps, circle drags suppressing closes, rectangle direction, rectangle taps/vertical movement doing nothing, and cancelled touches. These classes are unchanged in version 0.4, so the passing checks were not rerun. The private tab description is covered by a matching check, not a private-browsing device test.

```powershell
& 'C:\Users\user1\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat' verify --verbose 'dist\TabBubble-0.8.apk'
```

Result: **Verifies**, APK signature scheme v2, one signer. This is a debug-signed testing build.

## Firefox device validation, version 0.2

The Pixel 9 Pro was identified as ADB transport 52; Firefox reported version 159.0.1. Its current Compose toolbar exposes `ADDRESSBAR_TABS_COUNTER`, a long-clickable node, with a child `TabCounterTestTags.tabCounter` described as **Non-private Tabs Open: 3. Tap to switch tabs.** The original prototype recognized neither identifier nor this label. These exact identifiers and a narrowly matched regular/private description were added; Chrome matching was retained.

Using the installed Android SDK's `adb.exe`, the relevant commands were:

```powershell
adb -t 52 shell am start -a android.intent.action.VIEW -d 'https://example.com/?tab-bubble-test=1' -p org.mozilla.firefox
adb -t 52 shell input swipe 1064 300 1064 300 700
adb -t 52 shell input keyevent 4
adb -t 52 install -r dist/TabBubble-0.2.apk
adb -t 52 shell input tap 936 1868
adb -t 52 shell uiautomator dump /data/local/tmp/tabbubble-ui.xml
```

Result: opening the disposable tab increased the count from three to four. Inspecting its shortcut menu confirmed native **New tab**, **New private tab**, and **Close tab** buttons. The menu was dismissed, installation returned **Success**, and tapping the updated app's floating × closed the disposable tab. Firefox displayed **Tab closed**, the counter returned to three, and the original active tab returned. The existing three tabs were preserved. Accessibility access was already enabled by the user and was not changed.

Local UI dumps and screenshots are in the ignored `.debug` directory and may contain personal browser content; do not commit or distribute them. The preceding section records the version 0.2 Firefox fix; the current version 0.3 results follow.

## Circle and rectangle device validation, version 0.3

Version 0.3 was installed successfully with `adb -t 52 install -r dist/TabBubble-0.3.apk`. The final layout has a 56 dp close circle centered above a 112 × 44 dp swipe rectangle, in one overlay window. Circle drags move the window. Rectangle gestures switch tabs only. The earlier double-tap design was replaced before completion.

Two disposable tabs were opened per browser using `adb -t 52 shell am start -a android.intent.action.VIEW -d <test-url> -p <browser-package>`. Firefox had 2 original tabs and Chrome had 33. Test A used example.com; test B used example.org, with task-specific query strings.

The rectangle's bounds were visually checked on-device. These commands exercised its two directions:

```powershell
adb -t 52 shell input swipe 845 2234 600 2234 300
adb -t 52 shell input swipe 845 2234 1090 2234 300
```

Chrome selected B → A for left and A → B for right, retaining 35 tabs. Firefox selected A → B for right and B → A for left, retaining 4 tabs; its rectangle y coordinate was 2235. Stored widget coordinates stayed unchanged. Chrome's toolbar was at the bottom and Firefox's at the top, verifying that navigation uses the detected toolbar bounds.

`adb -t 52 shell input swipe 845 2074 700 1800 600` dragged the circle: both parts moved together, with Chrome test B still selected and 35 tabs retained. The position was restored using a circle drag back near the original location. `adb -t 52 shell input tap 845 2235` on Firefox's rectangle left test B selected and retained 4 tabs.

Circle taps were tested using `adb -t 52 shell input tap 845 2074` (and `701 1801` while the widget was temporarily moved). Each tap closed exactly one disposable tab. Chrome returned to its original count of 33, and Firefox to its original count of 2. Both originals were preserved and the phone was returned to Firefox with `adb -t 52 shell am start -n org.mozilla.firefox/org.mozilla.fenix.HomeActivity`.

One Firefox close attempt during validation left its shortcut menu open. The service was hardened to tolerate a temporarily missing active accessibility root during popup transitions, and to avoid updating overlay window parameters when no touchability restoration is needed. The final build then closed Firefox test B and A successfully, and passed a new Chrome disposable-tab close check. Logs showed close-menu actions accepted after 197 ms and 178 ms in Firefox and 262 ms in Chrome; these measure request-to-native-action time, not final visual completion. Diagnostic logs contain control-operation status only, never page contents.

UI hierarchy snapshots used `adb -t 52 shell uiautomator dump /data/local/tmp/tabbubble-ui.xml` followed by `adb -t 52 pull`. Final close observations allowed 800 ms before starting the UI inspector, so the inspector would not interrupt an in-flight accessibility operation. This wait is in the test procedure, not the app.

Not validated: private browsing, tab groups, list boundaries, rapid repeated gestures, rotation, hidden toolbars, overlapping the toolbar, keyboard display, toggling visibility, or switching apps during an operation. The earlier passing gesture checks were not rerun after the final service-only popup fix because the gesture model and matching rules were unchanged.

## Faster switching device validation, version 0.4

The only behavior change is the injected native toolbar stroke duration: **260 ms → 60 ms**, about **77% shorter**. Gesture recognition, path length, direction, close handling and widget positioning are unchanged. This does not measure or override the browsers' own settling animation or Android-wide animation settings.

Build/lint passed with the command above. `adb -t 52 install -r dist/TabBubble-0.4.apk` returned **Success**. The Pixel 9 Pro initially had 31 Chrome tabs and 2 Firefox tabs; the stored widget position was x=831, y=1886. Two disposable tabs per browser were opened using `adb -t 52 shell am start -a android.intent.action.VIEW -d <test-url> -p <browser-package>`, with `https://example.com/?tab-bubble-fast=A` and `https://example.org/?tab-bubble-fast=B`.

In both browsers, these physical rectangle gestures exercised the app's new 60 ms native swipe:

```powershell
adb -t 52 shell input swipe 999 2334 754 2334 300
adb -t 52 shell input swipe 999 2334 1244 2334 300
```

Result: left selected test A from B, right selected test B from A. Counts stayed at 33 in Chrome and 4 in Firefox. The 300 ms argument is the test's finger movement across the rectangle; the app's subsequent toolbar stroke is 60 ms. Hierarchy observations began after a 500 ms test-only wait to avoid interrupting the service. No before/after end-to-end visual timing benchmark was performed.

Circle taps with `adb -t 52 shell input tap 999 2174` closed the temporary B and A tabs individually, with an expected-test-tab assertion before each destructive action. Final counts were restored to 31 Chrome tabs and 2 Firefox tabs. Stored widget coordinates remained x=831, y=1886. The phone was returned to Chrome using `adb -t 52 shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.android.chrome`.

## Vertical and diagonal rectangle movement, versions 0.5–0.6

The rectangle now chooses one action when movement crosses the existing Android touch-slop distance. If initial horizontal displacement is greater than 1.5 times vertical displacement, it locks to tab switching. Other initial directions, including all four 45-degree diagonals, lock to moving the widget. That decision remains fixed until release, so a horizontal switch cannot turn into repositioning mid-gesture and a movement drag cannot switch tabs. No hold, timer, extra swipe distance, or change to the 60 ms native toolbar stroke was introduced. Drag release now applies the final finger coordinates before persisting the position.

Version 0.5 was installed and tested with `adb -t 52 shell input swipe 765 2401 765 2121 300` on Chrome's rectangle. The whole widget moved up 280 px, from stored y=1953 to y=1673, with x=597 unchanged. Test B remained active and the count stayed at 33 (31 original tabs plus 2 disposable test tabs). The user then requested to test the phone themselves, so further automated UI tests and cleanup were stopped. The temporary example.com/example.org tabs with `tab-bubble-vertical=A/B` were intentionally left open at that handoff.

Version 0.6 added diagonal movement and was built with the Gradle/lint command above. The updated gesture checks were run with:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe' -d app/build/rule-check app/src/main/java/com/tabubble/android/BubbleGestures.java checks/BubbleGesturesCheck.java
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp app/build/rule-check com.tabubble.android.BubbleGesturesCheck
```

Result: **42 gesture safety checks passed**. Coverage includes all four diagonal movement directions, upward/downward movement, horizontal switches with vertical jitter, direction locking after a turn, taps, circle dragging, and cancellation. Browser matching is unchanged; its 31 previously passing checks were not rerun. The earlier paragraph about 21 gesture checks records the historical version 0.3 run and is superseded for gesture handling by this result.

APK verification returned **Verifies** with signature scheme v2. `adb -t 52 install -r dist/TabBubble-0.6.apk` returned **Success**. No phone screen inspection, input, tab closing, or other automated UI testing was performed for version 0.6, honoring the user's request to test personally. Device validation of the new downward/diagonal gestures and horizontal coexistence is left to the user.

## Minimum stroke duration, version 0.7

Changed the native toolbar stroke duration from 60 ms to **1 ms**, the minimum positive integer accepted by [Android's StrokeDescription API](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.StrokeDescription). The path, gesture recognition, direction lock and widget movement are unchanged. Android's [MotionEventInjector implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java) emits a movement event before the release event when the endpoint changes, including when no intermediate sample falls before the endpoint. Browser recognition and visual completion are still unverified for this extremely short duration; no claim of a 1 ms visible tab change is made.

The Gradle build/lint command at the top passed; APK verification returned **Verifies**, signature scheme v2. `adb -t 52 install -r dist/TabBubble-0.7.apk` returned **Success** on the Pixel 9 Pro. The 42 previously passing gesture checks were not rerun because that model is unchanged. No screen inspection, UI input, tab closing, or phone animation setting change was performed. The user is testing on-device; compatibility of the 1 ms stroke in Chrome and Firefox remains to be confirmed.

## Flipped default and Float Tools integration, version 0.8

Default direction is now left-next/right-previous. Raw gesture enum values are named LEFT/RIGHT so direction preferences are separate from movement recognition. The standalone build/lint command passed, the gesture-only javac/java commands recorded in the version 0.6 section passed all 42 checks after that rename, and APK signature verification passed. `adb -t 52 install -r dist/TabBubble-0.8.apk` returned **Success**.

The same controls were integrated into Float Tools 1.1, with four-tap home-screen activation, an enable switch, and a persistent reverse-direction switch. See `../utility-widgets/BROWSER_TABS.md` for exact integration tests and manual setup. No accessibility permission, enabled flag or UI setting was changed on-device by the agent. The user will enable the new Float Tools service and disable the standalone Tab Bubble service when ready; no app was uninstalled. Phone UI testing remains with the user.
