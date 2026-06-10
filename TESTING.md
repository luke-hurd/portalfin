# Testing portalfin on the Portal (aloha) device

This is the **authoritative, repeatable** procedure for testing portalfin on the
physical Portal Gen 1. Stop re-deriving tap coordinates by screenshot every time —
they are recorded here. Update this file whenever a coordinate or step changes.

## Device facts

- Codename: `aloha` (Portal Gen 1)
- Screen: **1280 × 800** physical, but the WebView viewport is **1280 × 740**
  (top ~60px reserved for Portal's system overlay; portalfin header sits at
  the top of the WebView area below that).
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
| Detail | Back arrow | 40, 30-ish header | 5s |

> Coordinates assume the restyled layout. If the app shows **raw Jellyfin**
> (teal logo, "Jellyfin" wordmark), the restyle did NOT inject — fix that first;
> tap targets will be wrong.

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
