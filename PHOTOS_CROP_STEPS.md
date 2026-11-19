# Photos crop pad: smallest steps

The Photos crop pad (CW, CCW, Up, Down, Left, Right) moves the photo in Google Photos' Crop > Straighten screen by the
smallest step that still changes the picture. Step sizes depend on the phone, so `PhotosCrop.measured` keeps one entry
per `Build.DEVICE`; any other phone gets `PhotosCrop.defaultSteps`, worked out from its touch slop.

| Phone | Build.DEVICE | Screen | Touch slop | Rotate (each press) | Up / Down / Left / Right drag | First-press pinch |
|---|---|---|---|---|---|---|
| Pixel 7a | `lynx` | 1080x2400, 420 dpi | 21 px | 0.01 rad (0.573°), set directly; fallback drag 26 px | 23 px, about 1 screen px of photo movement | 22 px per finger |
| Pixel 9 Pro | `caiman` | 1280x2856, 480 dpi | 24 px | 0.01 rad, set directly; fallback drag 30 px | 26 px, about 1 screen px of photo movement | 25 px per finger |

## How the buttons work

- **Rotate** sets the Straighten slider's value through accessibility (`ACTION_SET_PROGRESS`), then waits until Photos
  reports the new value. The value is in radians (range ±0.785 = ±45°; the number shown on screen is a −100…100 scale of
  that range, not degrees). A higher value turns the picture clockwise. 0.01 rad is the finest step a finger drag on the bar can make,
  so that is one press. If the slider refuses a direct value, the button drags the bar 26 px instead.
- **Up/Down/Left/Right** drag the photo. Photos ignores a drag until it is 1 px past the touch slop (22 px on the 7a) and
  then moves the photo by the rest, but only if the drag crosses that point slowly: a quick small drag lands in one or two
  frames and Photos drops it or moves it by a varying amount. So the drag runs quickly to 3 px short of the slop, then moves
  1 px per 50 ms to the end.
- **A fitted photo has nowhere to slide.** The first arrow press after Crop opens pinches each finger out 22 px, about a 4–5%
  zoom, then drags.
- **Quick presses.** A tap that lands on the pad while a drag is running never reaches the pad, and it can stop that drag
  from moving the photo (Android still reports the drag as completed). So quick taps on one arrow are gathered, and 250 ms after
  the last one they run as one drag that is one step longer per extra tap. The pad dims while a drag runs. Rotation has no drag, so
  every rotate tap counts.

## How it was measured (Pixel 7a, Photos 7.77, 2026-11-18)

No screenshots or video. Two numeric signals:

- **Rotation:** the Straighten slider's exact value (`AccessibilityNodeInfo.rangeInfo.current`), logged by the debug-only
  probe (`PhotosProbe.kt`) after every press.
- **Movement:** test photos whose pixel colours encode their own position (`tools/photos-crop/mkq.py`: a coarse ramp plus
  two phase-shifted 128 px triangle waves per axis). After each trial the edit is saved as a copy, the copy's centre pixels are
  decoded back to a source position (`decq.py`, about 0.1 source px; one screen px is about 3.9 source px at the pad's zoom),
  and the copy is deleted. Each trial starts from a fresh, unsaved editor.

### Rotation (each is one drag along the bar, 220 ms, or one button press)

| Input | Result |
|---|---|
| drag 22 or 23 px | no change (3/3 each) |
| drag 24 px | +0.01 rad (3/3) |
| drag 25 px (old button) | 0.01 rad, 10/10 CW and 10/10 CCW when tapped slowly; 10 quick taps gave **0** |
| drag 30 / 40 px | 0.02 / 0.04 rad |
| drag 26 px (the fallback) | +0.01 rad 10/10, −0.01 rad 10/10 |
| new button, slow taps | +0.01 rad 10/10 CW, −0.01 rad 10/10 CCW |
| new button, 10 quick taps (0.1 s apart, and back to back) | exactly ±0.10 rad every run, both directions |

### Photo drags (single drags after the 22 px pinch)

| Drag | Moved |
|---|---|
| 20 or 21 px | never (0/32) |
| 22 px over 220 ms | 5–7 of 10 per direction, 2–3 screen px when it moved |
| 24 px over 60 ms | 0/3 |
| 24 px over 220 ms | 3/3 but 2–4 screen px, varying |
| 22 px slow | 0/3 |
| 23 px slow (1 s, 0.6 s, or quick to 18 px then 250 ms) | 11/11, about 1 screen px |
| pause at the slop, then finish | erratic; not used |
| quick to 20 px, then slow | one press jumped 5.6 px; not used |
| **button, 23 px, quick to 18 then slow** | Right 10/10, Left 10/10, Up 10/10, Down 10/10, each about 1 screen px, on the final build; the same 10/10 x4 on an earlier build with the same drag |
| **button, 5 quick taps** (back to back, and 0.1 s apart) | all 5 taps counted, photo moved 5 steps, every direction (8/8 runs) |

Baselines (same pinch, no slide): x 1800.0 ±0.1 source px; y 2406.8–2409.1, because the pinch is not centred exactly on
the photo vertically. Every Up result was above that range and every Down result below it.

The two axes need the same drag on the 7a: the slop is a radius, so Up/Down and Left/Right both start moving at 23 px.
On screen, one step is about 1 px either way.

## Pixel 9 Pro (Photos 7.99, 2026-11-19)

Same method and same editor UI as the 7a. Straighten bar [204,2352][1088,2520]. The 9 Pro's numbers are the 7a's
shifted by the larger touch slop (24 px), so they match `defaultSteps` for that slop and density.

### Rotation

| Input | Result |
|---|---|
| drag 25, 26 or 27 px | no change (3/3 each) |
| drag 28 to 33 px | exactly 0.01 rad (3/3 each) |
| drag 34 / 36 px | 0.02 rad |
| drag 30 px (the fallback) | +0.01 rad 10/10, −0.01 rad 10/10 |
| button, slow taps | +0.01 rad 10/10 CW, −0.01 rad 10/10 CCW |
| button, 10 quick taps (0.1 s apart, and back to back) | exactly ±10 steps, both directions |

### Photo drags (after the 25 px pinch; decoded source px, one screen px ≈ 3.5 source px here)

| Drag | Result |
|---|---|
| baselines (pinch only) | x 1798.8–1799.9; y 2403.9–2405.1 |
| 25 px | Right 3/3 no move; Up 2 of 3 no move, one jumped about 3 screen px |
| **26 px button** | Right 10/10 (1795.8–1797.0), Left 10/10 (1801.9–1803.0), Down 10/10 (2401.4–2402.3), Up 9/10 (2407.9–2409.1; one at 2404.1, inside the baseline) |
| 5 quick taps, Right and Left | moved about 4–5 steps (Right 1783.9 / 1782.9, Left 1815.2) |

Test-rig note: on Android 17 each `uiautomator dump` makes Android reconnect accessibility services, and a reconnect
cancels a drag that is running. The scripts now wait 2 s after the last dump before tapping and log the reconnects.

## Measuring another phone

Needs: the phone on adb, this branch's debug build installed (`adb install -r`), Float Tools' photo-crop accessibility
enabled, the Photos crop tool enabled in Float Tools, `uv` (for Pillow). From WSL with the Windows adb:

```sh
export ADB=/mnt/c/Users/user1/AppData/Local/Android/Sdk/platform-tools/adb.exe SERIAL=<serial from adb devices>
cd tools/photos-crop
./setup.sh                                   # pushes the two test photos, writes their ids
./probe.sh s steps                           # logs slop, density, Build.DEVICE and the steps in use
```

1. **Rotation drag fallback.** `MEDIA=<FTQX id> ./editor.sh`, then `./rot.sh 10 -<px> 220 0` and `./rot.sh 10 <px> 220 0`
   for px from slop+1 upward. Note the smallest px that gives exactly ±0.573° on 10/10, and the largest that still
   does; use the middle.
2. **Rotate buttons.** `./btn.sh 10 $(./pad.sh CW)` and `./burst.sh 10 $(./pad.sh CW) 0` (and CCW): expect ±0.573° per tap.
3. **Photo drags.** Try a step with `./probe.sh s steps --es set "pan_x=<px>,pan_y=<px>"`, then `./panb.sh Right` (and
   Left, Up, Down) 10 times each, plus 3 baselines with `pan_x=15,pan_y=15` (below the slop: pinch only). Start at slop+2 and
   go up 1 px at a time until every press moves the decoded position past the baseline range in the right direction.
   Then `./panburst.sh Right 5 0` should move 5 steps.
4. Add the phone to `PhotosCrop.measured` with its `Build.DEVICE`, clear the overrides
   (`./probe.sh s steps --ez clear true`), rebuild, rerun step 3 once, and fill in the table above.
5. `./cleanup.sh` removes the test photos.

The probe receiver exists only in debuggable builds and requires `android.permission.DUMP`, which only the adb shell has.
