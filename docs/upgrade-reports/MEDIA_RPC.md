# Media RPC — Upgrade Report

## Summary

Media-session-driven Rich Presence that reflects the currently playing track/album on the device. Reads active media sessions via `MediaSessionManager` and the `NotificationListener`, then maps playback metadata (title, artist, album, artwork, playback state icon) into a Discord activity. Includes per-app enable/disable, timestamps, hide-on-pause, and "show song as title" options.

## Implementation

- **UI:** `feature_media_rpc/MediaRpc.kt` — `MediaRPC()` composable
- **Service:** `feature_rpc_base/services/MediaRpcService.kt` — `@AndroidEntryPoint` foreground service + `MediaSessionManager.OnActiveSessionsChangedListener`
- **State:** `MediaAppsState`
- **Permission:** Requires Notification Listener access (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`)

## User-facing options

| Option | Default | Effect |
|---|---|---|
| Enable Media RPC | off | Starts/stops `MediaRpcService` |
| Enable artist name | off | Includes artist in the activity |
| Enable album name | off | Includes album in the activity |
| Show app icon | off | Uses the media app's icon as the large image (mutually exclusive with playback state) |
| Enable timestamps | off | Shows start/stop timestamps |
| Show playback state | off | Shows the playback-state icon (mutually exclusive with app icon) |
| Hide on pause | off | Clears/hides presence when media pauses |
| Show song as title | off | Uses the song title as the activity name |

## Service behavior (`MediaRpcService`)

1. On start, registers an `OnActiveSessionsChangedListener` with `MediaSessionManager`.
2. When sessions change, fetches the active media controller and calls `updatePresence()`.
3. `updatePresence()` reads the playing media via `GetCurrentPlayingMedia` / `GetCurrentPlayingMediaAll`, builds a `CommonRpc` (name, details, state, large/small image, timestamps), and pushes it through `KizzyRPC.updateRPC(...)`.
4. If no media is playing or data is empty, closes the RPC and shows an idling notification.
5. Notification channel `Constants.NOTIFICATION_CHANNEL_ID` ("Rich Presence Active"), ongoing, low priority.

## Permissions

- Notification Listener access is required to observe media sessions. The UI shows a `PreferencesHint` warning and launches `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` when access is missing.

## UX notes

- Searchable list of media apps with individual enable/disable checkboxes (`MediaAppsState`).
- Enabling Media RPC stops Custom, Apps, and Experimental RPC first (mutual exclusion).
- Reflects `AppUtils.mediaRpcRunning()` in the toggle.
