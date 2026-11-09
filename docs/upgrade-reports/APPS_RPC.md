# Apps RPC / App Detection — Upgrade Report

## Summary

Usage-stats-based Rich Presence that tracks the currently running foreground app and updates Discord with the app's name and icon. Queries `UsageStatsManager` every 2 seconds to determine the most recently used package, then maps enabled apps to a Discord activity. Includes an enabled-apps allowlist and excludes Kizzy and Discord from detection.

## Implementation

- **UI:** `feature_apps_rpc/AppsRpc.kt` — `AppsRPC()` composable
- **Service:** `feature_rpc_base/services/AppDetectionService.kt` — `@AndroidEntryPoint` foreground service
- **State:** `AppsState`
- **Permission:** Requires Usage Access (`Settings.ACTION_USAGE_ACCESS_SETTINGS`)
- **Data:** `data/rpc/CommonRpc.kt`, `data/rpc/RpcImage.ApplicationIcon`

## User-facing options

| Option | Effect |
|---|---|
| Enable Apps RPC | off | Starts/stops `AppDetectionService` |
| Per-app enable checkboxes | — | Only apps in the enabled list trigger presence updates |

## Service behavior (`AppDetectionService`)

1. On start, builds a notification with Exit and Restart actions and starts foreground (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` on Tiramisu+).
2. Launches a coroutine loop (2-second delay) that:
   - Queries `UsageStatsManager.INTERVAL_DAILY` for the last 10 seconds.
   - Picks the package with the latest `lastTimeUsed` (via a `TreeMap` keyed by usage time).
   - Skips `EXCLUDED_APPS` = `[com.my.kizzy, com.discord]`.
3. When the running package changes and is in the enabled list:
   - If RPC already running: `kizzyRPC.updateRPC(CommonRpc(name, largeImage=ApplicationIcon, time=Timestamps, packageName), enableTimestamps=true)`.
   - Otherwise: builds a fresh RPC with app name, start timestamp, status (`dnd` default), app icon, optional RPC buttons, then `build()`.
4. Updates the foreground notification with the current package name and app icon.
5. When no enabled app is running: closes RPC and shows default notification.
6. On destroy: cancels scope, closes RPC.

## Excluded apps

```kotlin
val EXCLUDED_APPS = listOf("com.my.kizzy", "com.discord")
```

## UX notes

- Searchable app list with enable/disable checkboxes (`AppsState.enabledApps`).
- Enabling Apps RPC stops Media, Custom, and Experimental RPC first (mutual exclusion).
- Reflects `AppUtils.appDetectionRunning()` in the toggle.
