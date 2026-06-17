# Testing portalfin on the Portal (aloha) device

This is the **authoritative, repeatable** procedure for testing portalfin on the
physical Portal Gen 1. Stop re-deriving tap coordinates by screenshot every time —
they are recorded here. Update this file whenever a coordinate or step changes.

## Device facts

- Codename: `aloha` (Portal Gen 1)
- Screen: **1280 × 800** physical, but the WebView viewport is **1280 × 740**
  (top ~60px reserved for Portal's system overlay; portalfin header sits at
  the top of the WebView area below that).
- ⚠️ **TAP/INPUT COORDINATE SPACE — read before you "fix" taps.** `adb shell
  wm size` reports `800x1280` and `dumpsys display` shows `mCurrentOrientation=3`.
  This is a TRAP: it looks like the panel is rotated and tempts you into applying
  a coordinate transform. **Do not.** `adb shell input tap/swipe` uses the SAME
  **1280×800 landscape** space as `adb screencap` — read X,Y straight off the
  screenshot, no transform. (Verified 2026-06-16: the recorded (200,320) poster
  tap lands; transformed coords like (190,1205)/(610,75) do nothing.)
  - If a tap "does nothing", the cause is almost always **wrong Y** (you hit the
    toolbar/filter row at y≈140-190) or the page hadn't finished loading — NOT
    rotation. Sanity-check input is alive with a vertical `swipe 640 500 640 150`
    on a scrollable list; the screen must change (compare md5 of two screencaps).
- Package (debug): `org.jellyfin.mobile.portalfin.debug`
- Main activity: `org.jellyfin.mobile.MainActivity`
- Server: `192.168.1.112:8096`
- Credentials: see memory `jellyfin-test-creds` (NOT stored in the repo).

## Environment (every shell)

```bash
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=$HOME/Library/Android/sdk
```

## Build + install (does NOT wipe login)

```bash
cd /Users/luke/Projects/Portal/portalfin
./gradlew :app:assembleProprietaryDebug
adb install -r app/build/outputs/apk/proprietary/debug/portalfin-v*-proprietary-debug.apk
adb shell am force-stop org.jellyfin.mobile.portalfin.debug
adb shell monkey -p org.jellyfin.mobile.portalfin.debug -c android.intent.category.LAUNCHER 1
sleep 10   # cold start through splash
```

### ⚠️ NEVER run `pm clear` or `adb uninstall` unless you intend to wipe login.

`pm clear` purges SharedPreferences → server URL + auth token gone → you land
back on the Connect screen and have to sign in again. `adb install -r` upgrades
in place and **keeps** the session. Only wipe when explicitly testing the
first-run / login flow.

## ⚠️ VIDEO PLAYBACK IS NOT SCREENSHOT-CAPTURABLE (FLAG_SECURE)

The Portal display runs with `FLAG_SECURE`, so **`adb screencap` cannot capture
the live hardware video surface during playback**. Symptom that wastes hours if
you don't know this: every screencap taken while the player is up comes back
**byte-for-byte identical** (e.g. exactly 1370861 bytes), even across fresh
launches and real state changes — it's a frozen/stale composite, NOT the live
screen. Do NOT diagnose player visuals (black video, letterbox color, centering,
"did exit work") from screenshots — they lie even harder here than the top-inset
case. Two reliable channels instead:

1. **CDP DOM inspection** (trustworthy): forward devtools and read computed
   styles / classes / element rects. Setup:
   ```bash
   PID=$(adb shell pidof org.jellyfin.mobile.portalfin.debug | tr -d '\r')
   adb forward tcp:9222 localabstract:webview_devtools_remote_$PID
   curl -s http://127.0.0.1:9222/json   # find the page target's webSocketDebuggerUrl
   ```
   Drive it over the WebSocket (Runtime.evaluate for DOM/style facts;
   Input.dispatchMouseEvent for REAL clicks — synthetic JS `dispatchEvent`
   does NOT reach jellyfin-web's key/click handlers).
   - ⚠️ Do NOT call `history.back()` / `NavigationHelper.goBack()` over CDP to
     "test exit" — it desyncs jellyfin's router from the player overlay and
     corrupts state for the rest of the session (force-stop to recover). The
     player closes via its own OSD back button / hardware BACK, not raw history.
2. **The user's eyes.** For "does the video look right / can I exit", ask the
   user — the device shows the real thing; your screenshot does not.

Player-active signal (verified): `body.classList.contains('pf-video-page')`,
which the restyle sets from `.videoPlayerContainer-onTop` (NOT bare
`.videoPlayerContainer`, which persists after exit). Letterbox-is-black check:
`getComputedStyle(document.body).backgroundColor === 'rgb(0, 0, 0)'`.

## Waking the screen

The Portal sleeps/dims. If screenshots come back tiny (~25-50 KB) the screen is
off or in ambient mode. Wake it first:

```bash
adb shell input keyevent KEYCODE_WAKEUP
sleep 1
adb shell input tap 640 400   # dismiss ambient slideshow (tap anywhere)
```

A real rendered page screenshot is **~500 KB – 1 MB**. A blank/dimmed/ambient
screen is **~25–55 KB**. Use file size as a first sanity check before reading.

## Screenshot helper

```bash
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/<name>.png
```

Then READ the PNG with the vision tool. File size alone is not verification
(see GITFLOW.md Step 3).

## Login flow (when session was wiped)

The native Compose Connect/Login screens hand off to jellyfin-web's own login.
Recorded tap coordinates (1280×800, keyboard dismissed):

| Screen | Element | Tap (x, y) |
|---|---|---|
| Connect | Host field | 640, 258 |
| Connect | "Connect" button | 640, 333 |
| Connect | "Choose server" | 640, 397 |
| Native Login (if shown) | Username field | 640, 286 |
| Native Login | Password field | 640, 358 |
| Native Login | "Sign In" | 640, 433 |
| jellyfin-web login | User field | 640, 283 |
| jellyfin-web login | Password field | 640, 369 |
| jellyfin-web login | "Sign In" | 640, 497 |

Type into a focused field with `adb shell input text "value"`. **The IME keyboard
covers the lower buttons.** Submit the web login form by pressing
`adb shell input keyevent KEYCODE_ENTER` while the password field is focused —
do NOT dismiss the keyboard with BACK first, because BACK can clear the field.

Full scripted login (password re-entered right before ENTER to avoid clears):

```bash
adb shell input tap 640 283; sleep 1; adb shell input text "luke"
adb shell input tap 640 369; sleep 1; adb shell input text "<password>"
adb shell input keyevent KEYCODE_ENTER
sleep 10
```

## Navigation map (home → movies → detail)

Recorded against the portalfin-restyled home. Re-confirm after any header/layout
change.

| From | Action | Tap (x, y) | Wait |
|---|---|---|---|
| Home | "Movies" library tile | 200, 250 | 7s |
| Movies | first poster (top-left) | 200, 320 | 9s (let backdrop + content render) |
| Detail | **Resume / Play button** (blue pill) | **415, 465** | 9s (HLS startup) |
| Detail | Back arrow | 40, 30-ish header | 5s |
| Player | reveal OSD (auto-hides) | tap center 640, 400 | 2s |

> Coordinates assume the restyled layout. If the app shows **raw Jellyfin**
> (teal logo, "Jellyfin" wordmark), the restyle did NOT inject — fix that first;
> tap targets will be wrong.

### Scripted: home → play first movie (no human needed)

The full path that reaches actual video playback, verified 2026-06-16. Run from
home (force-stop + relaunch first if unsure of state):

```bash
adb shell input tap 200 250; sleep 7   # Movies library tile
adb shell input tap 200 320; sleep 9   # first poster -> detail page
adb shell input tap 415 465; sleep 9   # Resume/Play pill -> HLS playback
# verify: screencap should be ~1.3 MB (real frame), NOT ~14 KB (black)
```

> The Resume pill is ~58% down the detail page, NOT at the vertical center.
> y=205 (my first guess) hits the backdrop and does nothing; y=465 is correct.

### Capturing a tap coordinate from the device (when one isn't recorded)

Don't make the user tap for you. If you need a coordinate you don't have, record
their tap instead of trial-and-error:

```bash
adb shell getevent -lt /dev/input/event<N>   # find the touchscreen event node via `getevent -lp`
# user taps once; read ABS_MT_POSITION_X / _Y values (raw panel units).
```

Raw getevent units may be panel-native (800×1280) and need scaling to the
1280×800 input space — but for normal nav just reuse the table above.

## Three-view regression sweep (mandatory, per GITFLOW.md)

```bash
# Home
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v1-home.png
adb shell input tap 200 250; sleep 7
# Movies
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v2-movies.png
adb shell input tap 200 320; sleep 9
# Detail
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v3-detail.png
```

READ all three. Confirm each is a *different* page (a "tap missed, same screen"
is a TEST FAILURE, not a pass — GITFLOW.md Step 4a).

## DOM probing inside the WebView

To inspect computed styles / element rects live, add a temporary block at the top
of `applyAll()` in `portalfin-restyle.js` gated on the route, log with a unique
prefix, then read it back:

```bash
adb logcat -c   # clear first
# ... trigger the page ...
adb logcat -d 2>&1 | grep "pf-<tag>" | head
```

WebView `console.log` shows up in logcat as `I WebView : [pf-<tag>] ...`.
Remove probe blocks before shipping.

## Reference: known jellyfin-web inline-style gotchas on the detail page

- `.itemBackdrop` gets inline `top: -179.391px` (bleeds under header) → override
  `top: 0` or heads get scrolled off the top.
- `.detailLogo` gets inline `top: 333px` (positions title art) → override to
  re-anchor it onto the backdrop.
- Video/audio/subtitle "metadata" are `<select class="selectVideo …">` dropdowns,
  not text spans.
- `.btnPlay` carries class `detailButton`, so a generic `.detailButton{}` circle
  rule will hit it unless excluded with `:not(.btnPlay):not(.btnReplay)`.
