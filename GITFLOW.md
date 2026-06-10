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
    app/build/outputs/apk/proprietary/debug/portalfin-v1.1.0-proprietary-debug.apk
```

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

## What goes in a feature branch (the rule for portalfin)

1. The code change.
2. **Visual proof** — at least one screenshot in `docs/screenshots/feature-<name>-*.png`
   captured on the actual device after building + sideloading.
3. A short note in the README's Roadmap section moving the item from "todo"
   to released.

No feature gets merged to `develop` until step 2 passes. We've been burned by
"the dump says it works" without checking pixels — visual proof is the gate.
