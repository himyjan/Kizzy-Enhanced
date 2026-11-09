# Quick Settings Tile — Upgrade Report

## Summary

A Android Quick Settings tile that lets users start or stop any RPC directly from the quick-settings panel, without opening the app. Tapping an inactive tile opens a dialog to choose which RPC to launch (App Detection, Media RPC, or Experimental RPC); tapping an active tile stops all RPCs.

## Implementation

- **Service:** `feature_rpc_base/services/KizzyTileService.kt` — extends `TileService`
- **Dialog:** `DialogActivity.kt` (launched when the device is locked)
- **State:** `AppUtils.appDetectionRunning()`, `AppUtils.mediaRpcRunning()`, `AppUtils.experimentalRpcRunning()`

## Tile behavior

| Tile state | Action on tap |
|---|---|
| `STATE_ACTIVE` | Stops App Detection, Media RPC, and Experimental RPC services; shows "stop RPC" toast; sets tile to inactive |
| `STATE_INACTIVE` (unlocked) | Shows an RPC-choosing dialog (App Detection / Media RPC / Experimental RPC); starts the selected foreground service; shows a start toast |
| `STATE_INACTIVE` (locked) | Launches `DialogActivity` so the user can choose an RPC |
| `STATE_UNAVAILABLE` | When `Prefs.TOKEN` is empty; tile cannot be toggled |

## Tile appearance

| State | Icon | Subtitle |
|---|---|---|
| Active | `ic_tile_stop` | Name of the running RPC (App Detection / Media RPC / Experimental RPC) |
| Inactive | `ic_tile_play` | "Off" |
| Unavailable | — | — (token missing) |

## UX notes

- `tileAdded` tracked via `onTileAdded()`.
- `onStartListening()` / `onTileAdded()` call `updateTile()` to keep the icon/state in sync.
- Requires a valid Discord token (`Prefs.TOKEN`) before the tile becomes available.
