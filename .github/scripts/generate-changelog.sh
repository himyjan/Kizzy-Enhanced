#!/bin/bash
set -e

echo "🔍 Fetching tags..."
git fetch --tags --quiet 2>/dev/null || true

LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -n "$LAST_TAG" ]; then
  RANGE="${LAST_TAG}..HEAD"
  echo "📋 Using tag range: ${LAST_TAG}..HEAD"
else
  RANGE="-n 100"
  echo "📋 No tags found, using last 100 commits"
fi

echo "📦 Extracting commits..."
RAW_COMMITS=$(git log $RANGE --pretty=format:"%s (%h)" --no-merges 2>/dev/null || true)
RAW_COMMITS_COUNT=$(printf '%s\n' "$RAW_COMMITS" | grep -c . 2>/dev/null || echo "0")
echo "📊 Found $RAW_COMMITS_COUNT commits"

# Categorize commits (grep -E is available everywhere; ripgrep is not on ubuntu-latest)
FEATS=$(printf '%s\n' "$RAW_COMMITS" | grep -Ei '^feat(\(.+\))?:' | sed -E 's/^feat(\(([^)]+)\))?:\s*/- **\2**: /I; s/^\- \*\*\*\*: /- /' || true)
FIXES=$(printf '%s\n' "$RAW_COMMITS" | grep -Ei '^fix(\(.+\))?:' | sed -E 's/^fix(\(([^)]+)\))?:\s*/- **\2**: /I; s/^\- \*\*\*\*: /- /' || true)
PERFS=$(printf '%s\n' "$RAW_COMMITS" | grep -Ei '^(perf|refactor)(\(.+\))?:' | sed -E 's/^(perf|refactor)(\(([^)]+)\))?:\s*/- **\3**: /I; s/^\- \*\*\*\*: /- /' || true)
OTHERS=$(printf '%s\n' "$RAW_COMMITS" | grep -Evi '^(feat|fix|perf|refactor)(\(.+\))?:' | sed 's/^/- /' || true)

{
  echo "## 📋 Changelog"
  echo ""
  echo "### ✨ Features"
  [ -n "$FEATS" ] && printf '%s\n\n' "$FEATS" || echo "_None_"
  echo ""
  echo "### 🐛 Bug Fixes"
  [ -n "$FIXES" ] && printf '%s\n\n' "$FIXES" || echo "_None_"
  echo ""
  echo "### ⚡ Performance & Refactoring"
  [ -n "$PERFS" ] && printf '%s\n\n' "$PERFS" || echo "_None_"
  echo ""
  echo "### 📦 Other Changes"
  [ -n "$OTHERS" ] && printf '%s\n\n' "$OTHERS" || echo "_None_"
  echo ""
  [ -z "$RAW_COMMITS" ] && echo "_No code changes recorded since last tag._"
} > changelog.md

echo "✅ Changelog written to changelog.md"
cat changelog.md
