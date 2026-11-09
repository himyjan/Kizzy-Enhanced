# RPC Settings — Upgrade Report

## Summary

Global Rich Presence preferences that apply across all RPC features. Covers custom buttons, activity type/status, application ID, last-run config reuse, Imgur integration, and low-res icons. Accessed from the Settings screen (`RpcSettings()` composable).

## Implementation

- **UI:** `feature_settings/rpc_settings/RpcSettings.kt` — `RpcSettings()` composable (Scaffold + LazyColumn + modal dialogs)
- **Prefs:** `com.my.kizzy.preference.Prefs`
- **Data model:** `data/model/rpc/RpcButtons.kt`

## Settings sections

### General settings
| Setting | Key | Type | Description |
|---|---|---|---|
| Configs directory | `Prefs.CONFIGS_DIRECTORY` | choice | `APP_DIRECTORY` or `DOWNLOADS_DIRECTORY` (Android < Tiramisu only) |
| Use custom buttons | `Prefs.USE_RPC_BUTTONS` | switch | Enables RPC button configs |
| Custom activity type | `Prefs.CUSTOM_ACTIVITY_TYPE` | dialog (0–5) | Numeric activity type for custom presence |
| Custom activity status | `Prefs.CUSTOM_ACTIVITY_STATUS` | dialog | online / idle / dnd / invisible |
| Set previous run config in Custom RPC | `Prefs.APPLY_FIELDS_FROM_LAST_RUN_RPC` | switch | Reapply last Custom RPC fields on launch |
| Custom application ID | `Prefs.CUSTOM_ACTIVITY_APPLICATION_ID` | dialog (digits) | Custom Discord application ID |

### Advance settings
| Setting | Key | Type | Description |
|---|---|---|---|
| Use Imgur | `Prefs.USE_IMGUR` | switch | Upload images to Imgur |
| Imgur client ID | `Prefs.IMGUR_CLIENT_ID` | dialog | Imgur API client ID (defaults to `Constants.IMGUR_CLIENT_ID`) |
| Use low-res icon | `Prefs.RPC_USE_LOW_RES_ICON` | switch | Use low-resolution icons |
| Delete saved icon URLs | — | action | Clears `SAVED_IMAGES` and `SAVED_ARTWORK` |

### RPC Buttons config dialog (when "Use custom buttons" is on)
| Field | Key | Validation |
|---|---|---|
| Button 1 text | `rpcButtons.button1` | Max length (`Constants.MAX_ALLOWED_CHARACTER_LENGTH`) |
| Button 1 URL | `rpcButtons.button1Url` | — |
| Button 2 text | `rpcButtons.button2` | Max length |
| Button 2 URL | `rpcButtons.button2Url` | — |

Persisted as JSON in `Prefs.RPC_BUTTONS_DATA`.

## Related constants (`data/rpc/Constants.kt`)
- `IMGUR_CLIENT_ID` — default Imgur client ID
- `MAX_ALLOWED_CHARACTER_LENGTH` — button text length cap
- `MAX_APPLICATION_ID_LENGTH_RANGE` — valid application ID digit range
- `APP_DIRECTORY` / `DOWNLOADS_DIRECTORY` — config directory choices
