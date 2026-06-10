# portalfin — working rules for Claude

These are hard rules, not suggestions. They exist because each one maps to a
real mistake that wasted the user's time. Follow them every session.

## 1. Regression? `git diff` the last-good commit FIRST.

If something that worked recently is now broken, do NOT start a fresh
investigation. The cause is almost always in a recent diff of mine.

```bash
git log --oneline -10
git diff <last-good-sha>..HEAD -- <relevant-file>
```

Find the line I changed, understand why the old value was load-bearing, and
revert/repair that specifically. Only investigate from scratch if the diff
genuinely shows nothing relevant.

Concrete example that cost an hour: the detail glow-up added
`.itemBackdrop { top: 0 !important }` to "fix cropped heads," overriding
jellyfin-web's inline `top: -179px`. That -179px was load-bearing — it keeps
the backdrop inside the detail page's `padding-top: 70px` reserve that clears
Portal's system-overlay icons + the portalfin header. The override pushed the
title under the Portal back/home icons. A `git diff` would have shown this in
seconds.

## 2. `node --check` before every build. (Now enforced by a pre-commit hook.)

`app/src/main/assets/native/portalfin-restyle.js` is ONE giant JS template
literal — the entire stylesheet is inside backticks. A stray backtick in a CSS
comment closes the literal early and silently breaks ALL styling app-wide (the
app falls back to raw Jellyfin chrome). This has happened more than once.

```bash
node --check app/src/main/assets/native/portalfin-restyle.js
```

A pre-commit hook (`.git/hooks/pre-commit`, installed via
`tools/install-git-hooks.sh`) blocks commits when this fails. Never write a
backtick inside a CSS comment in that file — use single quotes.

## 3. The top inset is sacred. Don't fight it.

Portal renders fixed system back/home icons in a ~64px band at the top of the
screen that **adb screencap does NOT capture**. So a screenshot can look fine
while the real device shows content jammed under those icons — trust the user's
eyes over the screenshot here.

- Native side: `webView.applyWindowInsetsAsMargins()` pushes the WebView down.
- CSS side: `PORTAL_TOP_INSET = 64`; pages carry `padding-top: 70px` (header +
  breathing). `#portalfin-header` is `position: fixed; top: 0` *within* the
  already-offset WebView.
- Do not add `top:`/`margin-top:` overrides to `.itemBackdrop`,
  `.skinHeader`, `.itemDetailPage`, body, or page containers without checking
  this chain first.

## 4. Testing on the device — see TESTING.md.

Server `192.168.1.112:8096`, login in memory (`jellyfin-test-creds`). NEVER
`pm clear` or `adb uninstall` the app (wipes login). `adb install -r` keeps the
session. Recorded tap coordinates + the login/sweep flow live in `TESTING.md`.

## 5. Don't hammer the user's Jellyfin server to test my own code.

Verify URL/format by reading the SDK or code, not by curling the server for
real media. The user can manually test on-device flows when asked.

## 6. GitFlow (see GITFLOW.md).

Feature/fix branch → `--no-ff` merge into `develop` → push. Commit messages end
with the Co-Authored-By line. One structural change per commit so partial
rollback stays possible.
