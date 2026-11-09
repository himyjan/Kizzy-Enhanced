# Experimental RPC — Upgrade Report

## Summary

An advanced, template-driven Rich Presence that combines rich-media detection with app detection. It processes `TemplateKeys` (app name, media title, artist, etc.) through a `TemplateProcessor` using battery level, playback state, and detected app/media info as context. Supports three timestamp modes ("default", "current", "custom"), rich-media cover art / playback-state icons, and per-template name/details/state configuration.

## Implementation

- **Service:** `feature_rpc_base/services/ExperimentalRpc.kt` — `@AndroidEntryPoint` foreground service (extends `Service`)
- **UI:** `feature_experimental_rpc/ExperimentalRpcScreen.kt` + `ExperimentalRpcViewmodel.kt`
- **Core logic:** `TemplateProcessor`, `Timestamps`, `CommonRpc`, `RpcImage`

## Key capabilities

### Dual context (media + app)
- **Media context:** When a media session is active and `useMediaRpc` is enabled, builds presence from `RichMediaMetadata` (title, artist, album, cover art, playback state icon, timestamps).
- **App context:** When an enabled app is in the foreground and `useAppsRpc` is enabled, builds presence from `CommonRpc` app info.
- Falls back to closing the RPC when neither context is active.

### Template processing
`TemplateProcessor` evaluates placeholders such as `{{app_name}}`, `{{media_title}}`, `{{media_artist}}`, `{{battery}}`, `{{playback_state}}`, etc., against the current context. Each of name / details / state has its own configurable template key.

### Timestamp modes
| Mode | Behavior |
|---|---|
| `default` | Uses timestamps from the media/app context |
| `current` | Fixed start anchored to midnight today (elapsed timer keeps counting, no 24h reset) |
| `custom` | User-specified start/end epoch milliseconds |
| `disabled` | `EXPERIMENTAL_RPC_ENABLE_TIMESTAPMS` = false → no timestamps |

### Battery level
Read via `BatteryManager.BATTERY_PROPERTY_CAPACITY` (0–100), exposed as the `{{battery}}` template placeholder.

### Rich media fields
- Cover art (falls back to app icon)
- Playback-state icon
- App icon (optional large/small image)
- Album, playback state icon, timestamps

## User-facing prefs (read directly in the service)

| Preference | Default | Effect |
|---|---|---|
| `EXPERIMENTAL_RPC_USE_APPS_RPC` | true | Enable app-detection context |
| `EXPERIMENTAL_RPC_USE_MEDIA_RPC` | true | Enable media-session context |
| `EXPERIMENTAL_RPC_TEMPLATE_NAME` | `TemplateKeys.APP_NAME` | Template for activity name |
| `EXPERIMENTAL_RPC_TEMPLATE_DETAILS` | `TemplateKeys.MEDIA_TITLE` | Template for details |
| `EXPERIMENTAL_RPC_TEMPLATE_STATE` | `TemplateKeys.MEDIA_ARTIST` | Template for state |
| `EXPERIMENTAL_RPC_ENABLE_TIMESTAMPS` | true | Master toggle for timestamps |
| `EXPERIMENTAL_RPC_TIMESTAMP_MODE` | `default` | `default` / `current` / `custom` |
| `EXPERIMENTAL_RPC_TIMESTAMP_CUSTOM_START/END` | — | Custom epoch ms |
| `EXPERIMENTAL_RPC_HIDE_ON_PAUSE` | false | Hide media presence on pause |
| `EXPERIMENTAL_RPC_SHOW_COVER_ART` | true | Show cover art as large image |
| `EXPERIMENTAL_RPC_SHOW_APP_ICON` | false | Show app icon |
| `EXPERIMENTAL_RPC_SHOW_PLAYBACK_STATE` | true | Show playback-state icon |
| `EXPERIMENTAL_RPC_SHOW_SONG_AS_TITLE` | false | Use song title as name |
| `EXPERIMENTAL_RPC_TITLE_SOURCE` | `title` | `title` or `artist` |
| `EXPERIMENTAL_RPC_USE_BUTTONS` | false | Show RPC buttons |
| `EXPERIMENTAL_RPC_USE_CUSTOM_APP_ID` | false | Use custom Discord application ID |
| `EXPERIMENTAL_RPC_APPLICATION_ID` | — | Custom application ID value |
| `EXPERIMENTAL_RPC_PLATFORM` | — | Spoofed gateway identify platform/os/device |
| `ENABLED_EXPERIMENTAL_APPS` | `[]` | JSON list of app package names to detect |

## Service behavior

1. On start, registers a media-session listener (guarded against `SecurityException` when notification access is missing).
2. Loads all settings from `Prefs`, starts either media or app detection.
3. `updatePresence()` runs the template processor, computes final name/details/state/images/timestamps, and either `updateRPC(...)` or builds a fresh RPC via `KizzyRPC.apply { ... build() }`.
4. Clears buttons optionally, sets custom application ID if enabled.
5. Updates the ongoing notification with the current title/details.
6. On destroy: removes the media listener, cancels scope, closes RPC.

## UX notes

- Dedicated Experimental RPC screen with its own ViewModel and UI state.
- Enabling Experimental RPC stops Media, Custom, and Apps RPC first (mutual exclusion).
