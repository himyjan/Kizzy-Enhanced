# Kizzy-Enhanced → Original Kizzy Sync Workflow

This file documents the manual workflow for pulling bug fixes from the original [dead8309/Kizzy](https://github.com/dead8309/Kizzy) project into Kizzy-Enhanced (`himyjan/Kizzy-Enhanced`), and pushing your enhancements back to your fork.

## Remote setup (one time)

From the Kizzy-Enhanced repo root:

```bash
# Your fork — push your enhancements here
git remote add origin https://github.com/himyjan/Kizzy-Enhanced.git

# Original Kizzy — pull bug fixes from here
git remote add upstream https://github.com/dead8309/Kizzy.git

# Verify
git remote -v
```

Expected output:

```
origin	https://github.com/himyjan/Kizzy-Enhanced.git (fetch)
origin	https://github.com/himyjan/Kizzy-Enhanced.git (push)
upstream	https://github.com/dead8309/Kizzy.git (fetch)
upstream	https://github.com/dead8309/Kizzy.git (push)
```

## Regular sync workflow

### 1. Fetch latest from both remotes

```bash
git fetch origin
git fetch upstream
```

### 2. Review what changed

See what original Kizzy has that your fork doesn't:

```bash
git log --oneline origin/master..upstream/master
```

See what your fork has that original Kizzy doesn't:

```bash
git log --oneline upstream/master..origin/master
```

### 3. Rebase your work on top of original Kizzy

```bash
git rebase upstream/master
```

This replays all your commits on top of `upstream/master` with **no merge commit** — linear history.

### 4. Resolve conflicts (if any)

Original Kizzy and Kizzy-Enhanced have diverged, so conflicts are common.

```bash
# See which files conflicted
git status

# Open each conflicted file, fix the conflicts manually, then:
git add <fixed-file>

# Continue the rebase
git rebase --continue

# If you want to give up and start over:
git rebase --abort
```

**Tips:**
- Do **not** use `git rebase -X theirs` unless you understand it discards upstream changes in favor of yours.
- Resolve conflicts manually so you keep the best of both versions.
- After `git rebase --continue`, repeat until the rebase finishes.

### 5. Push to your fork

Rebasing rewrites commit hashes, so you must force-push:

```bash
git push --force-with-lease origin master
```

> **Never** use plain `git push --force` — `--force-with-lease` is safer because it won't overwrite work you don't know about.

---

## Cherry-pick specific fixes (alternative to full rebase)

If you only want specific upstream fixes instead of rebasing everything:

```bash
git fetch upstream

# Pick just one commit
git cherry-pick <commit-hash>

# Example: logout fix from original Kizzy
git cherry-pick 583c4eca

# Resolve any conflict, then:
git push origin master
```

Useful upstream commits to consider:

| Commit | Fix |
|--------|-----|
| `583c4eca` | fix: logout clearing the whole app data via `pm clear` |
| `a054ae9b` | fix: experimental rpc not forwarding type |
| `e9bbfaa3` | fix: timezone offset issue in custom RPC timestamp picker |
| `78c613c9` | fix: comply with fgs type on versions above `33` |
| `67503ca4` | fix: JSON parsing memory issue |
| `faf7c85d` | fix: hide placeholders regex issue |

---

## Complete example session

```bash
cd /Users/user/Downloads/Kizzy-Enhanced

# 1. Fetch
git fetch upstream

# 2. Check what's new
git log --oneline origin/master..upstream/master

# 3. Rebase
git rebase upstream/master

# 4. If conflicts: edit files, then:
#    git add <file>
#    git rebase --continue

# 5. Push
git push --force-with-lease origin master

# 6. Verify
git log --oneline -5
```

---

## Troubleshooting

### "The following untracked working tree files would be overwritten"

Build artifacts (e.g. `build-logic/convention/bin/main/*.kt`) are untracked and block `git rebase --abort` or `git reset`. Move them out of the way:

```bash
mv build-logic/convention/bin/main/AndroidApplicationPlugin.kt /tmp/
mv build-logic/convention/bin/main/AndroidHiltPlugin.kt /tmp/
# ... etc.
git rebase --abort
```

### "Cannot push — rejected (non-fast-forward)"

Your local history was rewritten by rebase. Use `--force-with-lease`:

```bash
git push --force-with-lease origin master
```

### "Cannot rebase — you have unstaged changes"

Commit or stash your changes first:

```bash
git stash
git rebase upstream/master
git stash pop
```

---

## Safety rules

1. **Always `git fetch upstream` before rebasing** — you need the latest original Kizzy commits.
2. **Always force-push with `--force-with-lease`** — plain `--force` is destructive.
3. **Test after every rebase** — build and install the APK to confirm nothing broke.
4. **Communicate before force-pushing** if others clone your fork — they'll need to re-fetch.
