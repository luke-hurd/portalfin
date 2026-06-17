<p align="center">
  <img src="docs/wordmark.png" width="320" alt="portalfin">
</p>

<p align="center">
  Turn an old <strong>Facebook Portal</strong> into a proper Jellyfin player.
</p>

<p align="center">
  <a href="https://www.youtube.com/watch?v=1E4ZBgMRJXY">
    <img src="https://img.youtube.com/vi/1E4ZBgMRJXY/maxresdefault.jpg" width="640" alt="Watch the portalfin walkthrough on YouTube">
  </a>
</p>

<p align="center">
  ▶︎ <a href="https://www.youtube.com/watch?v=1E4ZBgMRJXY"><strong>Watch the Walkthrough</strong></a>
</p>

## What this is

If you've got an old Facebook Portal collecting dust, this turns it into a
genuinely nice Jellyfin player. Your whole library on that always-on touchscreen
— browse posters, tap to play, and when you walk away it drifts into an ambient
slideshow of your cover art with a big clock.

It's a fork of the official [Jellyfin Android app](https://github.com/jellyfin/jellyfin-android),
rebuilt to feel like it belongs on the Portal instead of a phone app squeezed
onto a weird screen: a clean native sign-in, a kiosk-style interface with the
admin clutter stripped out, and a layout that works around the Portal's quirks
(like the system buttons that float over the top of the screen).

## Screenshots

| Connect to server | Native sign-in |
|---|---|
| ![](docs/screenshots/01-connect.png) | ![](docs/screenshots/02-login.png) |

| Home | Library |
|---|---|
| ![](docs/screenshots/03-home.png) | ![](docs/screenshots/04-movies.png) |

| Movie detail | Profile / settings |
|---|---|
| ![](docs/screenshots/05-detail.png) | ![](docs/screenshots/06-profile.png) |

| Video player | |
|---|---|
| ![](docs/screenshots/09-playback.png) | |

| Ambient slideshow | Ambient slideshow |
|---|---|
| ![](docs/screenshots/07-ambient.png) | ![](docs/screenshots/08-ambient.png) |

## What makes it nice

The stock Jellyfin app works on a Portal, but it feels like a phone app running
on the wrong device. portalfin smooths over all of that:

- **A real sign-in screen.** Native login that talks to your server directly,
  instead of dumping you into a web login page inside the app.
- **No clutter.** The admin menus, hamburger drawer, and dashboard shortcuts are
  gone. What's left is browse, search, cast, and your profile — the stuff you
  actually touch from the couch.
- **It fits the screen.** The Portal floats its own back/home buttons over the
  top of the display; portalfin lays everything out so nothing hides behind them.
- **It looks like it belongs.** A custom header and a color scheme that matches
  the Portal's own look, instead of Jellyfin's default teal.
- **Smooth transitions.** Moving between Home, your library, and a movie's page
  crossfades instead of hard-cutting, and the app fades in gracefully from the
  launcher splash.
- **It comes alive when idle.** After a minute of sitting there it turns into an
  ambient slideshow — your cover art full-screen with a big clock and date — and
  the background tint even shifts warmer or cooler with the time of day. Tap to
  wake it.
- **A clean video player.** Just a back button, the title, and a cast button up
  top; proper black bars around the video; controls that fade away while you
  watch.

If you want the exact, line-by-line breakdown of what changed from upstream,
it's all in the [commit history](https://github.com/luke-hurd/portalfin/commits/main)
and the feature branches.

## Supported devices

- **Portal Gen 1** ("aloha") — confirmed working
- Portal Gen 2 / Mini / Plus / Go / TV — likely work but untested. See [meta-quest/portal-samples](https://github.com/meta-quest/portal-samples) for the official supported device list.

Requirements:
- Portal firmware from October 2025 or later
- ADB enabled in **Settings → Debug → ADB Enabled**

## Sideload instructions

The Portal never had an app store, and for years it was locked down tight. That
changed in October 2025, when Meta pushed a firmware update that quietly enabled
ADB — Android's developer/sideloading mode. That update is the only reason any
of this is possible, so step one is making sure your Portal is up to date.

Once it is, you install portalfin over a USB cable using ADB. It's a few
commands, but it's a one-time thing:

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

- [x] **CSS view transitions** between SPA routes — kill the flat web-app feel on every navigation
- [x] **Native splash → home crossfade** — splash logo crossfades into the styled web app
- [x] **Ambient slideshow** — after 60s idle, fullscreen rotating gallery of Jellyfin backdrop art with oversized clock + date + current item title; tap to wake. Fills the screen edge-to-edge (drops the Portal top inset) and keeps the display awake.
- [x] **Custom video player chrome** — minimal back/title/cast top bar, black letterbox, enlarged transport controls, all auto-hiding with the OSD

Next up — contributions welcome:

- [ ] **Weather overlay** on the ambient slideshow (clock + date are done; weather is not)
- [ ] **Transcode-on-download quality picker** (in progress) — pick 1080p/720p and transcode server-side to a phone-sized MP4 instead of the multi-GB remux
- [ ] **Native Portal home grid** — replace jellyfin-web's React home with native Compose tiles calling the Jellyfin REST API directly. Biggest lift, biggest payoff for "feels native."
- [ ] **Haptic accents** on tile taps and detail-page actions
- [ ] **Voice control** via the Portal's built-in mic ("portalfin, play Back to the Future")
- [ ] **Per-user home pages** using the existing Aloha account framework (4 internal user accounts already exist on every Portal)

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
