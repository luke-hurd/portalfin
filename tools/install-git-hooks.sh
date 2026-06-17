#!/bin/sh
# Install portalfin's git hooks. Run once after cloning:
#   sh tools/install-git-hooks.sh
#
# .git/hooks is not version-controlled, so the hook source lives here and gets
# copied into place. See CLAUDE.md rule #2 for why the restyle syntax check
# matters.

set -e
HOOK_DIR="$(git rev-parse --git-dir)/hooks"
mkdir -p "$HOOK_DIR"
cp tools/hooks/pre-commit "$HOOK_DIR/pre-commit"
chmod +x "$HOOK_DIR/pre-commit"
echo "✓ installed pre-commit hook → $HOOK_DIR/pre-commit"
