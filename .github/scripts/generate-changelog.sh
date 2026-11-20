#!/bin/bash
set -e

LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -n "$LAST_TAG" ]; then
  RANGE="${LAST_TAG}..HEAD"
else
  RANGE="-n 100"
fi

RAW_COMMITS=$(git log $RANGE --pretty=format:"%s (%h)" --no-merges | rg -iv '^(chore|ci|style|build|test)(\(.+\))?:' || true)

FEATS=$(printf '%s\n' "$RAW_COMMITS" | rg -i '^feat(\(.+\))?:' | sed -E 's/^feat(\(([^)]+)\))?:\s*/- **\2**: /I; s/^\- \*\*\*\*: /- /' || true)
FIXES=$(printf '%s\n' "$RAW_COMMITS" | rg -i '^fix(\(.+\))?:' | sed -E 's/^fix(\(([^)]+)\))?:\s*/- **\2**: /I; s/^\- \*\*\*\*: /- /' || true)
PERFS=$(printf '%s\n' "$RAW_COMMITS" | rg -i '^(perf|refactor)(\(.+\))?:' | sed -E 's/^(perf|refactor)(\(([^)]+)\))?:\s*/- **\3**: /I; s/^\- \*\*\*\*: /- /' || true)
OTHERS=$(printf '%s\n' "$RAW_COMMITS" | rg -iv '^(feat|fix|perf|refactor)(\(.+\))?:' | sed 's/^/- /' || true)

{
  echo "### ✨ Features"
  [ -n "$FEATS" ] && printf '%s\n\n' "$FEATS"
  echo "### 🐛 Bug Fixes"
  [ -n "$FIXES" ] && printf '%s\n\n' "$FIXES"
  echo "### ⚡ Performance & Refactoring"
  [ -n "$PERFS" ] && printf '%s\n\n' "$PERFS"
  echo "### 📦 Other Changes"
  [ -n "$OTHERS" ] && printf '%s\n\n' "$OTHERS"
  [ -z "$RAW_COMMITS" ] && echo "_No code changes recorded since last tag._"
} > changelog.md
