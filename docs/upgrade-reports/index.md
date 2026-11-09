# Kizzy-Enhanced — RPC Features Upgrade Report

This document summarizes the new Rich Presence (RPC) features implemented across the Kizzy-Enhanced codebase. Each feature is covered in its own report; see the index below.

## Table of Contents

- [Core RPC Service](KIZZY_RPC_SERVICE.md) — background service, foreground notification, pause/resume/disconnect
- [Custom RPC](CUSTOM_RPC.md) — fully configurable custom presence builder with image picker, timestamps, buttons, save/load/share configs
- [Media RPC](MEDIA_RPC.md) — media-session-driven presence for the currently playing track/album
- [Apps RPC / App Detection](APPS_RPC.md) — usage-stats-based detection of the currently running app
- [Experimental RPC](EXPERIMENTAL_RPC.md) — combined rich-media + app-detection presence with template processing
- [Quick Settings Tile](QUICK_SETTINGS_TILE.md) — tile to start/stop any RPC from the quick-settings panel
- [RPC Settings](RPC_SETTINGS.md) — global RPC preferences (buttons, activity type/status, application ID, Imgur, low-res icons)

## Architecture Overview

All RPC features are built on a shared foundation in `feature_rpc_base`:

- **`KizzyRPC`** (`data/rpc/KizzyRPC.kt`) — the Discord RPC client wrapper (`setName`, `setDetails`, `setState`, `setLargeImage`, `setSmallImage`, `setButton1/2`, `setTimestamps`, `build`, `updateRPC`, `closeRPC`).
- **`CommonRpc`** / **`RpcConfig`** — data models describing an RPC payload.
- **`RpcImage`** — supports application icons and custom URLs (Imgur or local file).
- **`SecureTokenStorage`** + **`KizzyStateRepository`** — Hilt-injected token storage and connection/pause state.
- **Hilt** — every RPC service is `@AndroidEntryPoint` and injects `KizzyRPC`, `CoroutineScope`, `Notification.Builder`, and `NotificationManager`.
- **Mutual exclusion** — enabling one RPC (Custom, Media, Apps, Experimental) stops all others before starting the selected one, so only one presence runs at a time.

## Quick Reference

| Feature | Service class | Entry composable | Trigger |
|---|---|---|---|
| Core RPC service | `KizzyRpcService` | — | Background service (notification actions) |
| Custom RPC | `CustomRpcService` | `CustomRPC` | Toggle switch in Custom RPC screen |
| Media RPC | `MediaRpcService` | `MediaRPC` | Toggle switch in Media RPC screen |
| Apps RPC | `AppDetectionService` | `AppsRPC` | Toggle switch in Apps RPC screen |
| Experimental RPC | `ExperimentalRpc` | `ExperimentalRpcScreen` | Toggle switch in Experimental RPC screen |
| Quick Settings Tile | `KizzyTileService` | — | Quick-settings tile tap |

Each individual report details the fields, behaviors, permissions, and user-facing settings for that feature.
