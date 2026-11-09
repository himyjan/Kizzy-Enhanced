# Core Kizzy RPC Service — Upgrade Report

## Summary

The foundational background RPC service that maintains a persistent Discord connection. Provides a foreground notification with Pause / Resume / Disconnect actions, Hilt-injected secure token storage, and a connection/pause state repository. All feature RPCs (Custom, Media, Apps, Experimental) delegate presence-building to the shared `KizzyRPC` client.

## Implementation

- **Service:** `feature_rpc_base/services/KizzyRpcService.kt` — `@AndroidEntryPoint` foreground service
- **DI:** `feature_rpc_base/di/RpcBaseModule.kt`
- **Dependencies:** `SecureTokenStorage`, `KizzyStateRepository` (Hilt-injected)
- **Notification channel:** `kizzy_rpc_channel` ("Kizzy Rich Presence Service"), low priority, ongoing

## Injected dependencies

| Dependency | Role |
|---|---|
| `SecureTokenStorage` | Encrypted storage of the Discord token |
| `KizzyStateRepository` | Connection (`connected`) and pause (`paused`) state flags |

## Service behavior

1. `onCreate()` — sets repository connected state to `true`.
2. `onStartCommand()`:
   - `ACTION_DISCONNECT` — sets connected/paused false, removes foreground, stops self.
   - `ACTION_TOGGLE` — flips pause state, updates notification text ("RPC Paused" or resumes last title/artist).
   - Default — starts foreground with "Kizzy RPC Active / Waiting for media..." notification.
3. Notification actions:
   - **Tap body** — opens the app launch intent.
   - **Pause / Resume** — toggles pause; button label/icon swaps dynamically.
   - **Disconnect** — stops the service.
4. `updateNotificationText(title, content)` — refreshes the ongoing notification without restarting the service (no-ops when paused).
5. `onDestroy()` — sets connected/paused false.

## UX notes

- Persistent foreground notification keeps the RPC connection alive in the background.
- Pause/Resume lets users temporarily stop presence syncing without disconnecting.
