<p align="center">
  <img src="docs/wordmark.png" width="320" alt="portalfin">
</p>

<p align="center">
  A Jellyfin player made for the Facebook Portal.
</p>

<p align="center">
  <a href="https://www.youtube.com/watch?v=1E4ZBgMRJXY" target="_blank" rel="noopener noreferrer">
    <img src="docs/screenshots/03-home.png" width="640" alt="portalfin home screen">
  </a>
</p>

<p align="center">
  ▶︎ <a href="https://www.youtube.com/watch?v=1E4ZBgMRJXY" target="_blank" rel="noopener noreferrer"><strong>See it in action on the Portal</strong></a>
</p>

## What is portalfin?

portalfin is a Jellyfin client for the Facebook Portal. I had an old first-gen Portal
sitting around and wanted to use it as a Jellyfin player, so I forked the official
[Jellyfin Android app](https://github.com/jellyfin/jellyfin-android) and reworked it
to run well on the device.

The Portal is an Android device, so the standard Jellyfin APK installs and runs, but
it just isn't built for the device: it's a phone app on a 1280x800 always-on display,
the system back/home buttons float over the top of the UI, and there's a lot of
chrome you don't need when the device only ever does one thing. The goal was to make
it feel native to the Portal rather than a phone app that happens to run on it. I
kept the Jellyfin web UI that does the heavy lifting and replaced the parts around
it: a native sign-in, a stripped-down kiosk interface, a layout that accounts for the
Portal's quirks, and an ambient slideshow when it's idle.

## Screenshots

| Connect to server | Native sign-in |
|---|---|
| ![](docs/screenshots/01-connect.png) | ![](docs/screenshots/02-login.png) |

| Library | Movie detail |
|---|---|
| ![](docs/screenshots/04-movies.png) | ![](docs/screenshots/05-detail.png) |

| Profile / settings | Video player |
|---|---|
| ![](docs/screenshots/06-profile.png) | ![](docs/screenshots/09-playback.png) |

| Ambient slideshow | Ambient slideshow |
|---|---|
| ![](docs/screenshots/07-ambient.png) | ![](docs/screenshots/08-ambient.png) |

## What I changed

I wanted this to feel native to the Portal, not like the stock app running on the
wrong hardware. Here's what got reworked, and why:

- Native sign-in, instead of being dropped into a web login page inside the WebView.
- A stripped-down kiosk interface. The admin menus, hamburger drawer, and dashboard
  shortcuts are hidden, so what's left is browse, search, cast, and your profile.
- A layout that respects the Portal's screen. Its system back/home buttons float
  over the top of the display, so that space is reserved and the header sits below
  it instead of getting covered.
- A look that matches the device, with a custom header and a color scheme closer to
  the Portal's own rather than Jellyfin's default teal.
- Smoother transitions. Navigating between Home, the library, and a movie page
  crossfades instead of hard-cutting, and the app fades in from the launcher splash.
- An ambient mode. After about a minute idle it shows a full-screen slideshow of
  your cover art with a clock and date, and the background tint drifts with the time
  of day. Tap to wake it.
- A cleaner video player: a top bar with just back, title, and cast, black
  letterboxing, and controls that fade out while you're watching.

### How it works under the hood

The app is a native Android shell around the Jellyfin web UI rather than a
from-scratch rewrite, which keeps it current with upstream Jellyfin for free.
`MainActivity` runs a small state machine over server and user state: no server
configured routes to a native Compose `ConnectFragment`, a server but no user routes
to a native `LoginFragment`, and once you're authenticated it hands off to the
`WebViewFragment` that hosts jellyfin-web.

The native login calls the Jellyfin SDK's `authenticateUserByName` directly, then
seeds the WebView's `localStorage` with the resulting credentials so the web app
loads already signed in on `/home`. A JavaScript bridge (`PortalFinBridge`) runs the
other direction too: it watches for sign-out and clears the native session, and
signals when restyling is done so the WebView can fade in cleanly.

Almost everything visual is done by injecting `portalfin-restyle.js` on every page
load (and re-injecting it on in-app navigation, since jellyfin-web is a single-page
app). That script reserves the 64px top band the Portal overlays its system buttons
on, swaps in the custom `#portalfin-header`, hides the kiosk chrome, applies the
Portal palette and fonts, and drives the ambient slideshow and time-of-day tinting.
The transitions use the browser's View Transitions API, with a shared
`view-transition-name` on the header so it stays put across route changes.

## Supported devices

- **Portal Gen 1** ("aloha") — confirmed working
- Portal Gen 2 / Mini / Plus / Go / TV — likely work but untested. See [meta-quest/portal-samples](https://github.com/meta-quest/portal-samples) for the official supported device list.

Requirements:
- Portal firmware from October 2025 or later
- ADB enabled in **Settings → Debug → ADB Enabled**

## Sideload instructions

The Portal has no app store, so you install over USB with ADB. ADB was locked on
the Portal until October 2025, when a Meta firmware update enabled it, so first make
sure your Portal is up to date. After that it's a one-time setup:

1. Install Android platform-tools: `brew install android-platform-tools` (macOS) or grab from [Google](https://developer.android.com/studio/releases/platform-tools)
2. Plug your Portal in via USB-C
3. Enable ADB on the Portal: Settings → Debug → ADB Enabled (you'll be prompted for the device password)
4. Accept the "Allow USB debugging?" dialog on the Portal screen
5. Verify the connection: `adb devices` should show your Portal
6. Download `portalfin-vX.Y.Z.apk` from the [latest release](https://github.com/luke-hurd/portalfin/releases/latest)
7. Install: `adb install portalfin-vX.Y.Z.apk`

The portalfin tile will appear on the Portal's Apps screen.

## Building from source

Requirements: JDK 17, Android SDK with platform-36 + build-tools 36.0.0.

```bash
git clone https://github.com/luke-hurd/portalfin.git
cd portalfin
./gradlew :app:assembleProprietaryDebug
adb install -r app/build/outputs/apk/proprietary/debug/portalfin-v*-proprietary-debug.apk
```

## Roadmap

Shipped:

- [x] **Native UI rebuild (v2.0)** — native Jetpack Compose home, library, and
  detail screens calling the Jellyfin REST API directly, on Meta's Portal design
  system (Material 3 + Inter + Meta blue). See [v2.0.0 release notes](docs/releases/v2.0.0.md).
- [x] **Native Portal home grid** — My Media + Continue Watching + Next Up + New
  Releases rails, replacing jellyfin-web's React home.
- [x] **Native library pages** — pagination, quick-filter pills (All / Favorites /
  Genres / Collections), grouped genre & collection grids.
- [x] **Native detail page** — fullscreen backdrop, title art, Play/Resume,
  cast & crew, scenes (chapters), more-like-this, RT score, subtitle picker.
- [x] **CSS view transitions** between SPA routes (v1.1)
- [x] **Native splash → home crossfade** (v1.1)
- [x] **Ambient slideshow** — 60s-idle fullscreen backdrop gallery with clock/date (v1.1)
- [x] **Custom video player chrome** — minimal back/title/cast bar, black letterbox (v1.1)

Next up — contributions welcome:

- [ ] **Migrate the remaining screens to M3** — Connect / Login / Downloads /
  Settings are still Material 2.
- [ ] **Weather overlay** on the ambient slideshow (clock + date are done; weather is not)
- [ ] **Transcode-on-download quality picker** (in progress) — pick 1080p/720p and transcode server-side to a phone-sized MP4 instead of the multi-GB remux
- [ ] **Voice control** via the Portal's built-in mic ("portalfin, play Back to the Future")

## Architecture

The interesting bits:

```
app/src/main/java/org/jellyfin/mobile/
├── MainActivity.kt              # Server/User state machine drives Fragment routing:
│                                #   ServerState.Unset       → ConnectFragment
│                                #   ServerState.Available + UserState.Unset → LoginFragment
│                                #   ServerState.Available + UserState.Available → WebViewFragment
├── setup/
│   ├── ConnectFragment.kt       # Native server URL entry (Compose)
│   └── LoginFragment.kt         # Native sign-in (Compose) — NEW IN PORTALFIN
├── ui/screens/
│   ├── connect/ConnectScreen.kt # Compose UI for server entry; defines reusable StyledTextButton
│   └── login/LoginScreen.kt     # Compose UI calling Jellyfin SDK's authenticateUserByName
└── webapp/
    ├── WebViewFragment.kt       # Hosts the WebView. Inner class PortalFinBridge exposes
    │                            #   getCredentials() → seed jellyfin-web localStorage
    │                            #   onSignedOut()    → clear native UserEntity + route to Login
    │                            #   onRestyleApplied() → fadeIn the WebView once styled
    └── JellyfinWebViewClient.kt # In onPageFinished: re-injects portalfin-restyle.js on every nav

app/src/main/assets/native/
├── portalfin-restyle.js         # The big one. Visibility gate, full Portal stylesheet,
│                                # custom #portalfin-header, kiosk chrome hiding, sign-out
│                                # detection, SPA-route re-injection.
└── wordmark.png                 # Served at /native/wordmark.png by AssetsPathHandler

app/src/main/res/
├── drawable/ic_launcher_padded.xml  # Launcher icon (Portal launcher does its own crop;
│                                    # adaptive icon XML files removed — use this instead)
├── values/colors.xml                # Portal palette: #1A1A1A / #2B2B2B / #0866FF
└── values/strings_donottranslate.xml # app_name = "portalfin"
```

## Attribution

Forked from [jellyfin/jellyfin-android](https://github.com/jellyfin/jellyfin-android),
which is licensed [GPL-2.0-only](LICENSE.md). All upstream work belongs to its
authors. The portalfin-specific changes are copyright Luke Hurd, also under
GPL-2.0.

Portal hardware, the Aloha framework, and Meta's Portal SDK belong to Meta
Platforms, Inc. portalfin is not affiliated with or endorsed by Meta.

## License

GPL-2.0-only. See [LICENSE.md](LICENSE.md).
