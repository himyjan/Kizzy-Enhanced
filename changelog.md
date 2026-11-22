## 📋 Changelog

### ✨ Features
- **rpc**: multi-application OAuth2 — authenticate up to 3 Discord application IDs independently (slots 1, 2, 3) and open 3 parallel gateway WebSocket sessions simultaneously (each slot shows its own Rich Presence on Discord)
- **rpc_base**: Custom RPC Board — save, switch, and activate custom presence boards with per-board application ID, activity type, details/state, images, buttons, and timestamps; one board active at a time via priority-gated presence
- **settings**: Last.fm API key and secret are now optional editable fields (default to Kizzy's built-in values); user logs in with username + password only
- **settings**: Last.fm / Libre.fm logout button always visible in scrobbling settings popup (clears session key, username, password, and resets API key/secret to defaults)

### 🐛 Bug Fixes
- **rpc**: each RPC slot (Media/Custom/Manual) now uses its own per-slot Application ID when sending Gateway Opcode 3 presence — Discord shows 3 separate activities instead of merging them into one
- **rpc_base**: MultiRpcManager.session() recreates dead WebSocket sessions instead of returning stale closed ones — toggling an RPC switch off then on again reconnects properly
- **rpc_base**: clear Discord presence on RPC service destroy (Media/Custom/Manual/App Detection) so old presence no longer lingers on Discord
- **rpc_base**: CustomRpcBoardScreen refreshes board list state after create/edit/delete/toggle so UI reflects DataStore changes immediately
- **rpc_base**: CustomRpcBoardScreen move up/down buttons to reorder board items
- **rpc_base**: board toggle (on/off) restarts CustomRpcService so presence updates Discord immediately
- **profile**: OAuth2 login skips Discord gateway (OAuth2 tokens can't connect) and goes straight to REST /users/@me — logout button shows immediately after login
- **setup**: setup page recognizes DISCORD_OAUTH2_ACCESS_TOKEN (not just Prefs.TOKEN) for login state
- **workflow**: restore release note details deleted in chore simplify workflow (5bda9d7d)
- **workflow**: make release notes body self-contained and never empty (6000395b)
- **profile**: remove unnecessary safe call on non-null cfg (8e3c2d19)
- **rpc_base**: clean up compiler warnings
- **data**: use ACTIVITY_RESUMED instead of deprecated MOVE_TO_FOREGROUND (033b9be6)

### ⚡ Performance & Refactoring
_None_

### 📦 Other Changes
- **build**: Kotlin downgraded from pre-release 2.5.0-RC to stable 2.4.21
- **build**: removed jvmToolchain(27) from domain/gateway modules (JVM target mismatch fix)
- **ci**: GitHub Actions — actions/cache@v6, --max-workers=2, --no-daemon to fix Maven Central rate limiting
