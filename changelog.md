## 📋 Changelog

### ✨ Features
- **rpc**: multi-application OAuth2 — authenticate up to 3 Discord application IDs independently (slots 1, 2, 3) and open 3 parallel gateway WebSocket sessions simultaneously (each slot shows its own Rich Presence on Discord)
- **rpc_base**: Custom RPC Board — save, switch, and activate custom presence boards with per-board application ID, activity type, details/state, images, buttons, and timestamps; one board active at a time via priority-gated presence

### 🐛 Bug Fixes
- **workflow**:  restore release note details deleted in chore simplify workflow (5bda9d7d)
- **workflow**:  make release notes body self-contained and never empty (6000395b)
- **profile**:  remove unnecessary safe call on non-null cfg (8e3c2d19)
- **rpc_base**:  clean up compiler warnings (437239bc)
- **rpc_base**:  clear Discord presence on RPC service destroy (Media/Custom/Manual/App Detection) so old presence no longer lingers on Discord
- **data**:  use ACTIVITY_RESUMED instead of deprecated MOVE_TO_FOREGROUND (033b9be6)
- **feature_settings**: Last.fm logout now clears session key, password, username, API key, and secret


### ⚡ Performance & Refactoring
_None_

### 📦 Other Changes
_None_

