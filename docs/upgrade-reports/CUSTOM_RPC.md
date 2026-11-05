# Custom RPC — Upgrade Report

## Summary

A fully configurable, user-defined Rich Presence builder. Users compose their own activity payload field-by-field and launch it as a background service. Supports text fields, party size, timestamps, platform, status, two action buttons, large/small images (with gallery picker), activity type, stream URL, and application ID. Configs can be saved, loaded, shared, previewed, and deleted.

## Implementation

- **UI:** `feature_custom_rpc/CustomRpc.kt` — `CustomRPC()` composable + `CustomRpcScreen()`
- **Service:** `feature_rpc_base/services/CustomRpcService.kt` — `@AndroidEntryPoint` foreground service
- **ViewModel/State:** `CustomScreenViewModel.kt`, `UiState.kt`
- **Components:** `RpcField`, `ImagePicker`, `DateTimePickerDialog`, bottom sheets for save/load/share/delete, preview dialog

## User-facing fields

| Field | Notes |
|---|---|
| Activity name | Required-ish; displayed as the activity title |
| Details | Secondary line of text |
| State | Tertiary line of text |
| Party current / max size | Numeric party counters with validation |
| Timestamps (start / stop) | Date/time picker dialogs; epoch milliseconds |
| Platform | Dropdown from `Constants.ACTIVITY_PLATFORMS` |
| Status | Dropdown from `Constants.ACTIVITY_STATUS` (online / idle / dnd / invisible) |
| Button 1 / Button 2 | Text + URL each; max length enforced |
| Large image / Large text | Gallery picker; supports local file or Imgur URL |
| Small image / Small text | Gallery picker |
| Activity type | Numeric type (0–5); reveals stream URL when type == 1 |
| Stream URL | Shown only for streaming type |

## Service behavior (`CustomRpcService`)

1. Receives the serialized `RpcConfig` via intent extra `"RPC"`.
2. Starts a foreground service with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` and a partial wake lock (`kizzy:CustomRPC`).
3. Builds the RPC via `KizzyRPC.apply { ... build() }`, mapping every field (name, details, state, party size, status, type, platform, timestamps, buttons, large/small images, stream URL).
4. Updates the notification large icon asynchronously from the selected image.
5. On destroy: cancels the coroutine scope, closes the RPC, releases the wake lock.

## UX notes

- Enabling Custom RPC stops Media, Apps, and Experimental RPC services first (mutual exclusion).
- The toggle reflects `AppUtils.customRpcRunning()`.
- Last-run config is persisted to `Prefs.LAST_RUN_CUSTOM_RPC` and can be reapplied in RPC Settings (`APPLY_FIELDS_FROM_LAST_RUN_RPC`).
- Config persistence directory is configurable in RPC Settings (`APP_DIRECTORY` or `DOWNLOADS_DIRECTORY`).
