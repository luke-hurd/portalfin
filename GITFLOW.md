# Git workflow

portalfin uses a simplified GitFlow. Two long-lived branches, short feature
branches, tagged releases.

## Branches

| Branch | Purpose | Source | Merges into |
|---|---|---|---|
| `main` | Released, tagged builds. Always installable. | — | — |
| `develop` | Integration branch. Where features land before release. | `main` | `main` (via release) |
| `feature/<name>` | One feature or fix. Short-lived. | `develop` | `develop` |
| `release/<version>` | Stabilization for a release. Optional for small releases. | `develop` | `main` + `develop` |
| `hotfix/<name>` | Urgent fix on top of `main`. | `main` | `main` + `develop` |

## Daily workflow

```bash
# Start a new feature
git checkout develop
git pull
git checkout -b feature/view-transitions

# ... write code, build APK, test on Portal, capture before/after screenshots ...

# Commit incrementally — small commits are easier to revert
git add app/src/main/assets/native/portalfin-restyle.js
git commit -m "feat(restyle): css view-transitions on SPA route changes"

git add docs/screenshots/feature-view-transitions-*.png
git commit -m "docs: capture before/after for view-transitions"

git push -u origin feature/view-transitions
```

## Merging features

Once a feature is verified on the device:

```bash
git checkout develop
git pull
git merge --no-ff feature/view-transitions
git push origin develop
git branch -d feature/view-transitions
```

`--no-ff` keeps the feature branch visible in `git log --graph` so we can see
which commits belonged together — useful for rollback.

## Cutting a release

```bash
git checkout develop
git pull
# Bump versionName in app/build.gradle.kts (or rely on getVersionName())
git tag -a v1.1.0 -m "v1.1.0 — view transitions, splash crossfade, ambient mode"
git checkout main
git merge --no-ff develop
git push origin main --tags
gh release create v1.1.0 \
    --title "v1.1.0" \
    --notes-file docs/releases/v1.1.0.md \
    app/build/outputs/apk/proprietary/debug/portalfin.apk
```

The APK is always named `portalfin.apk` (no version in the filename) so other
projects can link to a stable URL. The version lives inside the APK
(versionName/versionCode) and in the git tag / release title.

Each release ships:
- A signed (debug-signed for sideload) APK attached as a release asset
- Release notes in `docs/releases/v1.1.0.md`
- A git tag

## Rollback

If a feature merge breaks something:

```bash
# Find the merge commit
git log --graph --oneline --first-parent develop | head

# Revert it (creates a new commit that undoes the merge)
git revert -m 1 <merge-sha>
git push origin develop
```

Don't `reset --hard` `develop` once it's been pushed — anyone else who pulled
will see history rewrite. Always revert.

## Commit message format

We loosely follow Conventional Commits:

| Prefix | When |
|---|---|
| `feat:` | New user-facing capability |
| `fix:` | Bug fix |
| `chore:` | Build/CI/dependency change, no behavior change |
| `docs:` | README, screenshots, comments |
| `refactor:` | Code restructure with no behavior change |
| `style:` | CSS/visual tweaks (the portalfin-restyle.js diff territory) |

Optional scope: `feat(restyle):`, `fix(login):`, `style(header):`.

Subject line ≤ 72 chars. If more context is needed, blank line then body
paragraphs.

## Pre-merge testing protocol (mandatory)

Past releases shipped broken features because "verified" meant "the JS console
logged a success message." That is **not** verification. The protocol below
is now the required gate before any feature branch merges into `develop`.

### Step 1 — Build and sideload to the actual Portal

```bash
./gradlew :app:assembleProprietaryDebug
adb install -r app/build/outputs/apk/proprietary/debug/portalfin.apk
adb shell am force-stop org.jellyfin.mobile.portalfin.debug
adb shell monkey -p org.jellyfin.mobile.portalfin.debug -c android.intent.category.LAUNCHER 1
sleep 8   # let the app cold-start through splash
```

### Step 2 — Capture the THREE-VIEW REGRESSION SWEEP

These are non-negotiable. Even if the change "only" touches one screen, capture
all three because layout/CSS bleeds across pages:

```bash
# View 1 — Home
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v1-home.png

# View 2 — Movies library
adb shell input tap 200 250          # Movies tile
sleep 7                               # wait for content render, not just URL change
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v2-movies.png

# View 3 — A movie detail page
adb shell input tap 100 380          # tap a poster
sleep 7
adb shell screencap /sdcard/p.png && adb pull /sdcard/p.png /tmp/v3-detail.png
```

### Step 3 — READ EVERY SCREENSHOT

Open every PNG. Look at it with your eyes / agent's vision tool. Do not rely on:

- The size of the file (a dimmed screen and a rendered page can be the same KB)
- The JS console saying "loaded" (DOM/network success ≠ visible)
- The DOM dump (`getBoundingClientRect`) saying h=44 (a transparent or covered
  element reports the same)

For every visible element you intended to add/change, ask:
1. Is it actually drawn on screen at the position I expect?
2. Is its content visible (not transparent, not covered, not loading)?
3. Does it look the same as my mental model?

If you can't answer yes to all three for all three views — **the change does
not get merged**. Reset and try again.

### Step 4 — Real interaction test (for any feature with click handlers)

Tap each new interactive element. After each tap, sleep at least 5 seconds,
screenshot, READ it. If the resulting page is in a spinner state, broken
layout, or wrong content — the feature is not done.

### Step 4a — End-to-end navigation test (mandatory, no exceptions)

Three disconnected screenshots are not enough. The user-facing flow has to
work as a sequence:

1. Cold launch → see home
2. Tap Movies tile → see Movies library, posters rendered
3. Tap a poster → see detail page with backdrop + Play button
4. Tap Back → see Movies again
5. Tap Home → see home

Every step has to land on the expected screen. **Do not pass GO if a tap
"misses" and the screenshot is the same as the previous step** — that is
NOT a regression-free pass, that is a TEST FAILURE because the test never
ran. Re-tap with corrected coordinates until you see a different page, or
fix the code that's blocking the navigation.

Specifically: a "tap missed, still on home" screenshot is the same evidence
as a "tap was hijacked, redirected back to home" screenshot. You cannot tell
them apart without verifying the tap actually navigated. Always confirm the
post-tap page is different from the pre-tap page before accepting the result.

### Step 5 — Diagnostic logs are an aid, not proof

`console.log("loaded")` confirms code reached a callback. It does not confirm
the user sees anything. Use logs to **debug** when pixels don't match expectation,
not to **substitute** for checking pixels.

## Feature branch checklist

Before `git push -u origin feature/<name>`:

- [ ] Code change committed
- [ ] Build clean (`./gradlew assembleProprietaryDebug`, no errors)
- [ ] Three-view regression sweep captured in `docs/screenshots/feature-<name>-*.png`
- [ ] Each screenshot opened and visually confirmed
- [ ] If feature has interactions: at least one happy-path tap-through captured
- [ ] If a regression slips through into a release: revert the merge commit
      first (`git revert -m 1 <sha>`), DO NOT keep adding patches on top

## Smaller commits for layout-touching changes

When touching `.skinHeader`, `#portalfin-header`, page padding, z-index, or
anything that affects multiple pages: **one structural rule = one commit**.
Bundling 5 changes into one commit means a partial rollback is impossible.
