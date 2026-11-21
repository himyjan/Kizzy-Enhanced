# Kizzy Enhanced — Full Feature History

This document lists the complete set of features, bug fixes, and dependency changes
that distinguish this fork from the base Kizzy project. It is referenced from every
GitHub Release so that release notes stay focused on **this release** rather than
re-listing everything every time.

---

## 🔧 Build & Dependency Changes

| Component | Base Commit | This Fork | Change Type |
| :--- | :--- | :--- | :--- |
| Gradle | 8.13.2 | 9.9.1 | Major upgrade |
| AGP | 8.13.2 | 9.6.0-alpha03 | Major upgrade |
| Kotlin | 2.1.20 | 2.4.21 | Major upgrade |
| KSP | 2.1.20-1.0.31 | 2.3.13 | Major upgrade |
| Compose BOM | 2025.06.01 | 2026.09.00 | ~1.5 years newer |
| Ktor | 2.3.0 | 3.6.1 | Major upgrade |
| Coil | 2.1.0 | 3.6.3 (Coil3) | Major rewrite |
| Hilt | 2.56.2 | 2.60.1 | Minor upgrade |
| Material | 1.12.0 | 1.14.0 | Minor upgrade |

**Removed:** MMKV, Accompanist, BlankJ UtilCodex, CrashX, AndroidSVG, javax.inject  
**Added:** Jetpack DataStore 1.3.0, Tink Android 1.24.0, Jetpack BaselineProfile 1.6.0-alpha01, Jetpack MacroBenchmark 1.6.0-alpha01, Coil 3.6.3, WorkManager 2.12.0, Hilt Work 1.4.0, Material3 Adaptive 1.4.0-alpha03  
**Critical:** Coil 3 splits the HTTP fetcher — `coil-network-ktor3` was added; without it, remote images fail to load.  
**HTTP Client:** CIO client replaced with OkHttp client via `ktor-client-okhttp` across all RPC services for better mobile reliability (connection pooling, HTTP/2 multiplexing, Wi-Fi/cellular handoff, robust SSL/TLS).

---

## 🎮 Console RPC → Custom RPC Merge

**Before:** Separate Console RPC screen, home entry, and navigation route  
**After:** Single Custom RPC screen with two tabs

**Console RPC Tab:**
- Platform: Desktop, Web, Android, iOS, Xbox, PlayStation, VR
- Activity Type: Playing, Streaming, Listening, Watching, Competing
- Status: Online, Idle, DND, Offline, Invisible
- Enable Timestamps / Show Cover Art toggles
- Button 1/2: Text + URL
- Preset Games: Xbox, PlayStation, Nintendo, Desktop quick-select chips

**Features:** Console presets with custom preset creation and deletion support

---

## 🔐 DataStore Migration (MMKV → Jetpack DataStore)

**Before:** MMKV (C++ based, synchronous, no coroutine integration)  
**After:** Jetpack DataStore Preferences 1.3.0

**Architecture:** `MutableStateFlow<Map<String, Any?>>` mirror for synchronous reads; async writes on `Dispatchers.IO`.  
**Typed Keys:** `stringPreferencesKey`, `intPreferencesKey`, `booleanPreferencesKey`, `floatPreferencesKey`, `longPreferencesKey`  
**Remove:** Deletes key under all five typed keys  
**Edit:** `MutablePreferences` receiver (`Preferences.() -> Unit`)

---

## 🔒 Tink AES-256-GCM Token Encryption

**Sensitive keys encrypted:** `TOKEN`, `DISCORD_OAUTH_ACCESS_TOKEN`, `DISCORD_OAUTH_REFRESH_TOKEN`  
**Key storage:** Android Keystore via `AndroidKeysetManager`  
**Ciphertext:** `enc:` + Base64(NO_WRAP) — pre-existing plaintext tokens remain readable  
**Fallback:** If keystore unavailable, falls back to plain text (user never locked out)

---

## 🔐 Discord OAuth2 Integration (New Feature)

**Files added:** `DiscordAuth.kt`, `DiscordOAuthActivity.kt`, `DiscordUserLoginDialog.kt`, `DiscordOAuthRpcService.kt`  
**OAuth2 flow:** User authorizes via Discord → app exchanges code for token → stored encrypted via Tink  
**Per-user OAuth:** Users can use their own Discord account instead of bot token

---

## 📱 Media RPC Activity Type Fix

**Bug:** Global activity type setting did not take effect for known apps (YouTube Music, Spotify, etc.) because built-in catalog was checked before global fallback.  
**Fix:** When user explicitly sets a global activity type (not "Auto"), built-in catalog and metadata detection are skipped; per-app overrides still win.

---

## 🎨 Cover Art Settings (New Feature)

**Global Toggle:** Hide Cover Art Images — when enabled, hides cover art from ALL RPC types (Media, Custom, Console, Experimental); Discord shows text only.  
**Default Cover Art URLs:** Set default large and small cover art image URLs. When a notification has no cover art, the default image is used as fallback instead of Discord's default app icon.  
**Applied to:** Media RPC, Custom RPC (Console tab), Manual RPC.

---

## 🔗 YouTube Music Link Fix

**Bug:** YouTube Music activity link pointed to a search page URL (e.g. `youtube.com/results?search_query=...`) instead of the actual video.  
**Fix:** `getMediaUrl()` now returns `null` when `extractYouTubeUrl()` returns null for YouTube apps, instead of falling back to `mediaSearchUrl()` which returns another search page. Discord now shows the direct `youtu.be/VIDEO_ID` link instead of a broken search page.

---

## 🎮 Manual RPC: Select All / Deselect All

Two buttons added above the app list for bulk enabling/disabling all apps at once.

---

## 🌍 i18n — All 87 Locale Files

**0 English leftovers** across all locales  
**0 cross-script contamination**  
All 88 strings.xml files valid XML; `:app:mergeDebugResources` verified BUILD SUCCESSFUL  
Brand names (Xbox, PlayStation, Nintendo, iOS, VR) intentionally identical across locales

---

## 🎮 Console RPC Game List with Search (New Feature)

**Added to Custom RPC tab:** Console RPC now includes a preset game list with search functionality for quick console presence setup.

- **Game List:** Displays games from `ConsoleGameCatalog` with cover art and platform info
- **Search:** Real-time filtering by game title (case-insensitive)
- **Selection:** Tap a game to select it; check mark indicates selected game
- **Quick Apply:** Selecting a game auto-applies Console RPC settings (platform, activity type, status, timestamps, cover art)
- **Integration:** Works alongside existing Console Presets (built-in + custom) chips
- **Files:** `ConsoleGameItem` composable + `applyConsoleGame()` in `CustomRpc.kt`, `games()` getter in `ConsoleGameCatalog`

---

## 🔧 Fix: MetadataResolver Class Name Mismatch

**Bug:** `MediaResolver` class name did not match file name `MetadataResolver.kt`, causing build errors.  
**Fix:** Renamed class `MediaResolver` → `MetadataResolver` in `MetadataResolver.kt`. Updated all references in `GetCurrentlyPlayingMedia.kt` and `GetCurrentPlayingMediaAll.kt` to use `metadataResolver: MetadataResolver`.  
**Impact:** `:app:assembleDebug` compiles successfully.

---

## 📋 Fix: Last.fm Settings Relocation

**Before:** Last.fm Scrobbling settings were on the Custom RPC screen.  
**After:** Moved to Settings page (`RpcSettings.kt`) with a popup dialog for API credentials (API Key, API Secret) and enable/disable toggle.

**Details:**
- Last.fm settings now live in the main Settings page instead of Custom RPC
- Popup dialog for entering API Key and API Secret credentials
- Enable/Disable Last.fm Scrobbling toggle
- Added 10 Last.fm string resources across all 87 locale files

---

## 🎵 ListenBrainz Scrobbling (New Feature)

**Added alternative scrobbling backend** for users who prefer ListenBrainz over Last.fm.

**Features:**
- User token authentication via ListenBrainz API (https://api.listenbrainz.org/)
- Scrobble tracks with artist, track, album, timestamp, duration, and song ID
- Update Now Playing status for currently playing tracks
- Token validation on save
- Toggle for timestamps, cover art, and party size display
- Token stored encrypted via Tink + Android Keystore

**Files:** `ListenBrainz.kt` (new service), `RpcSettings.kt` (settings UI), `Prefs.kt` (preference keys), `strings.xml` (string resources)

---

## 🎨 Fix: "Hide Cover Art Images" Reactivity

**Bug:** "Hide Cover Art Images" toggle did not react to DataStore preference changes.  
**Fix:** Updated to use `collectAsState` pattern with `prefsMap` key for proper DataStore reactivity. The setting now correctly updates UI when the DataStore value changes.

---

## 🔧 Fix: ConsolePresetEditor Icon Type Mismatch

**Bug:** `ConsolePresetEditor.kt` referenced `Icons.Filled.Image` which doesn't exist in the material icons extended dependency, causing build failure in `ConsolePresetEditorDialog`.  
**Fix:** Changed `Icons.Filled.Image` → `Icons.Outlined.Image` for cover art picker icons and added proper import for `androidx.compose.material.icons.outlined.Image`.

---

## 🔧 Fix: Custom RPC Console Tab State References

**Bug:** `CustomRpc.kt` `RpcTextFieldsColumn` referenced `state.consoleGames` instead of `uiState.consoleGames` for the Console RPC game list, causing unresolved reference compile error. `CustomScreenViewModel.kt` used `_uiState.update { ... }` which is not available on `MutableStateFlow` in this Kotlin version.  
**Fix:** Changed `state.consoleGames` → `uiState.consoleGames` in `CustomRpc.kt`. Changed `_uiState.update { ... }` → `_uiState.value = _uiState.value.copy(consoleGames = freshGames)` in `CustomScreenViewModel.kt`.

---

## 📏 APK Size Reduction

**Release:** 35 MB → 8.2 MB (77% smaller)  
**DEX files:** 29 → 1  
**R8:** 769 classes removed, 14 app classes, 171 obfuscated, 0 kept names  
**ABI Filters:** `arm64-v8a`, `x86_64` (32-bit devices excluded)

---

## ⚡ Performance Tuning

**Compose Compiler Reports** — Enabled across all modules. Reports output to `build/compose_compiler/` in each module. Use these to identify:
- Unstable composables causing unnecessary recomposition
- Recomposition hot spots
- Skippable vs non-skippable composables

**Compose Runtime Tracing** — `androidx.compose.runtime:runtime-tracing` added. Captures Perfetto traces for frame timing, recomposition, and layout measurement. Open traces in Android Studio Profiler or Perfetto UI.

**Compose Stack Traces** — Compiler generates readable stack traces for recomposition issues. Check `build/compose_compiler/` metrics for unstable lambda captures.

**R8 Optimization** — `isMinifyEnabled = true`, `isShrinkResources = true`, `optimization.enable = true`. R8 mapping, usage, and configuration analysis reports generated for each release build (`r8-config-analyzer-release.pb`).

**R8 Keep Rules Cleanup** — Removed 5 redundant `-keep` rules from `app/proguard-rules.pro` that were subsumed by broader existing rules (e.g. `io.ktor.client.plugins.**` covered by `io.ktor.client.**`, `kotlinx.serialization.json.**` covered by `kotlinx.serialization.**`, duplicate Hilt entry points, duplicate serialization field keeps).

**Baseline Profiles** — `StartupBaselineProfileTest` traces app startup, Console RPC, Media RPC, and Custom RPC navigation. Run `./gradlew :app:baselineProfile` on a connected device to generate `src/main/baseline-prof.txt` for faster startup.

**Problems Report** — `build/reports/problems/problems-report.html` generated per release, showing potential API compatibility and performance issues.

---

## 🎮 Console RPC Preset Loading Fix

**Bug:** Loading a Console RPC preset (built-in or custom) saved settings to DataStore but did not start Console RPC if it wasn't already running. The preset had no effect on Discord until the user manually toggled Console RPC on.  
**Fix:** `applyConsolePreset()` and `applyConsoleGame()` now start Console RPC automatically when `rpcManager.activeRpc.value != RpcType.ConsoleRpc`. The preset takes effect immediately on Discord without requiring a manual toggle.  
**Files:** `CustomRpc.kt` — `applyConsolePreset()`, `applyConsoleGame()`.

---

## 🎮 Custom RPC Console Tab Switch Fix

**Bug:** The Enable/Disable switch on the Custom RPC screen always started Console RPC regardless of which tab was active. The Custom RPC tab had no way to start Custom RPC from the switch.  
**Fix:** SwitchBar now starts the correct RPC type based on the selected tab:
- Custom RPC tab → starts `RpcType.CustomRpc`
- Console RPC tab → starts `RpcType.ConsoleRpc`

Added `isConsoleRpcEnabled` state variable synced with `rpcManager.activeRpc`. Switch label now shows "Enable Custom RPC" or "Enable Console RPC" based on active tab.  
**Files:** `CustomRpc.kt` — SwitchBar, `isConsoleRpcEnabled`.

---

## 🔐 Discord Signin Button on User Profile After Logout

**Bug:** After Discord logout, the User Profile page showed no way to sign back in. Users had to navigate away from the profile page to access the Login screen.  
**Fix:** UserScreen now always renders regardless of login state. When logged out (`UserState.Error`), the page shows ProfileCard with fallback user data plus a Discord Signin button (`DiscordLoginButton`). Added `onNavigateToLogin` callback to navigate to the Login screen. Added `LOGIN` route constant and route registration in `Kizzy.kt` navigation graph.  
**Files:** `UserScreen.kt`, `Kizzy.kt`, `Routes.kt` (added `LOGIN` constant).

---

## 🚪 Discord Logout: Clear All Auth State & Stop RPC Services

**Bug:** Logout did not clear all authentication credentials and background RPC services continued running after logout, sending stale presence to Discord.  
**Fix:** Logout handler now:
- Stops `MediaRpcService`, `CustomRpcService`, and `ManualRpc` services so no presence is sent after logout
- Clears Discord OAuth2 tokens: `ACCESS_TOKEN`, `REFRESH_TOKEN`, `EXPIRES_AT`, `USER_ID`, `USERNAME`, `APPLICATION_CLIENT_ID`
- Clears Last.fm scrobbling credentials: `API_KEY`, `SECRET`, `USERNAME`, `PASSWORD`, `SESSION_KEY`, `ENABLED`, `ENABLE_TIMESTAMPS`, `SHOW_COVER_ART`, `SHOW_PARTY_CURRENT`, `SHOW_PARTY_MAX`
- Clears ListenBrainz scrobbling credentials: `USER_TOKEN`, `ENABLE_TIMESTAMPS`, `SHOW_COVER_ART`, `SHOW_PARTY_CURRENT`, `SHOW_PARTY_MAX`
- Clears user profile data: `TOKEN`, `USER_ID`, `USER_DATA`, `USER_BIO`, `USER_NITRO`
- Resets `RPC_CONNECTION_METHOD` to Gateway default
- Sets `loggedIn = false` to show LoginScreen on next profile visit  
**Files:** `Kizzy.kt` (logout handler), `UserScreen.kt` (onLogout callback with Context).

---

## 🔗 YouTube Music Video URL Link Fix (Enhanced)

**Bug:** YouTube Music activity link pointed to wrong video ID or search page URL instead of the actual video. `extractYouTubeUrl()` returned raw YouTube URLs (e.g., `youtube.com/watch?v=ID&list=PLAYLIST`) instead of extracting just the video ID. `normalizeMediaUrl()` returned URLs with `list=` parameter as-is, causing Discord to open the wrong video in a playlist.  
**Fix:**
- `extractYouTubeUrl()` now extracts just the 11-character video ID from all URL forms (`youtube.com/watch?v=ID`, `youtu.be/ID`, `yt:video:ID`, `t:ID`)
- `normalizeMediaUrl()` ignores `list=` parameter and extracts video ID
- Returns `https://youtu.be/ID` instead of full YouTube URLs
- Skips search page URLs (`/results?search_query`) to avoid broken links  
**Files:** `MetadataResolver.kt` — `extractYouTubeUrl()`, `normalizeMediaUrl()`.

---

## 🔄 Multiple RPCs Running Simultaneously

**Bug:** Every RPC type shared a single active-state slot, so turning one RPC on stopped all others. Opening Manual RPC closed Media RPC, opening Custom RPC closed App Detection, etc.  
**Fix:** `RpcManager.activeRpc` changed from a single `RpcType` to `Set<RpcType>`. Each RPC type now starts and stops independently:
- Media RPC on/off → only Media RPC starts or stops
- Manual RPC on/off → only Manual RPC starts or stops
- Custom RPC on/off → only Custom RPC starts or stops
- Console RPC on/off → only Console RPC starts or stops
- App Detection RPC on/off → only App Detection starts or stops

Discord still shows only one presence at a time (last sent wins), but all RPC services can run simultaneously.  
**Files:** `RpcManager.kt` (Set-based state, add=true/stopRpcTypes), `MediaRpc.kt`, `ManualRpcScreen.kt`, `CustomRpc.kt`, `AppsRpc.kt`.

---

## 🔗 Clickable HTTP Links in Profile Bio

**Bug:** URLs in the user's Discord profile bio (e.g. GitHub, YouTube, website) were shown as plain text with no way to click them.  
**Fix:** Bio URLs are now detected via regex and annotated with `addStringAnnotation`. Tapping a link opens it in the system browser via `LocalUriHandler`. Links are styled in Discord's blue link color.  
**Files:** `ProfileCard.kt` — `buildAnnotatedBio()`, `onTextLinkClick`.

---

## 🔐 Last.fm / Libre.fm / ListenBrainz Login & Logout

**Bug:** Scrobbling services had no explicit login/logout. Users had to rely on auto-authentication during scrobbling with no visibility into session status, and no way to log out except clearing all credentials.  
**Fix:** Each scrobbling service now has:
- **Login button** — authenticates with username/password via the service API and saves the session key
- **Logout button** — clears the session key and credentials
- **Authentication status indicator** — shows "Authenticated" or "Not authenticated" in the settings list item
- **Password visibility toggle** — show/hide password characters in password fields  
**Files:** `RpcSettings.kt` (login/logout UI, password visibility), `Prefs.kt` (`ACTIVE_RPC_TYPE`), `strings.xml`.

---

## ❄️ Cold Start RPC Restoration

**Bug:** After the app process was killed (cold start), every RPC switch came up closed. The state was derived only from running Android services, which weren't up yet, and the preference mirror was read before being primed from disk.  
**Fix:**
- `Prefs.awaitInitialized()` — completes once the DataStore mirror is primed from disk
- `Prefs.ACTIVE_RPC_TYPE` — persists which RPC type(s) the user enabled
- `RpcManager.restoreActiveRpc()` — starts the previously enabled RPC(s) after cold start
- Self-healing in `refreshFromServices()` — starts any RPC that's enabled but not running  
**Files:** `RpcManager.kt`, `Prefs.kt`, `App.kt`, all RPC service classes.

---

## 🧹 Custom RPC Clear Settings Button

**Bug:** The Custom RPC screen had no quick way to clear all fields — users had to open the bottom sheet menu and select Clear All Fields.  
**Fix:** Added a **Clear All** button directly in the Custom RPC top bar next to the menu button. Tapping it clears all fields and persists the empty state so the screen starts empty on next open.  
**Files:** `CustomRpc.kt` (top bar button), `CustomScreenViewModel.kt` (persist clear state).

---

## 🎮 Console RPC Preset Cover Art Fixes

**Bug:** Console RPC presets (built-in and custom) had `largeCoverArtUrl` and `smallCoverArtUrl` fields but they were never used — `buildConsoleConfig()` hardcoded empty image URLs. New presets were saved but never applied to Discord.  
**Fix:**
- `buildConsoleConfig()` now reads `largeImg`/`smallImg` from UI state when cover art is enabled
- New presets are automatically applied after saving (always call `applyConsolePreset()`)
- Deleting the currently selected preset clears the selection state  
**Files:** `CustomRpc.kt`.

---

## 🔗 Manual RPC Links Match Media RPC

**Bug:** Experimental/Manual RPC had weaker links than Media RPC — details URL dropped to null when no direct URL existed, state URL had no fallback, and media URL didn't try `getDirectMediaUrl` first.  
**Fix:** Manual RPC now matches Media RPC exactly:
- Details URL: direct URL → media search URL → browser search URL
- State URL: session artist URL → service artist URL (package + artist name)
- Media URL: tries `getDirectMediaUrl` first (YouTube family), then `getMediaUrl`  
**Files:** `ManualRpc.kt`.

---

## 🎨 Non-YouTube Client Cover Art Links

**Bug:** Non-YouTube clients like Morphe (`app.morphe.android.youtube`) and AndDea Revanced Patches (`anddea.youtube`) publish their project page (e.g. `github.com/morphe-patches`) instead of a YouTube video link. `toRpcMediaUrl()` dropped these because `YouTubeResolver.toRpcUrl()` returns null for non-YouTube URLs, so `mediaUrl` was null and `largeUrl` was null — cover art showed but clicking did nothing.  
**Fix:** Non-YouTube http(s) URLs are now kept as-is in `toRpcMediaUrl()`, so the cover art links to the app's project page. Morphe/AndDea/YMusic also got their correct patch repo download URLs in `appDownloadUrl()`.  
**Files:** `MetadataResolver.kt` (`toRpcMediaUrl`), `MediaServiceResolver.kt` (`appDownloadUrl`).

---

## 📱 More Media Player Download URLs

Added download URLs for additional media players so the small app icon links to each player's own page:

**Open-source (GitHub releases):**
| App | Link |
| --- | --- |
| mpv-android | github.com/mpv-android/mpv-android/releases |
| RetroMusic | github.com/RetroMusicPlayer/RetroMusicPlayer/releases |
| Player (brouken) | github.com/moneytoo/Player/releases |
| Auxio | github.com/OxygenCobalt/Auxio/releases |
| Gramophone | github.com/FoedusProgramme/Gramophone/releases |
| NextPlayer | github.com/anilbeesetti/nextplayer/releases |

**Closed-source / official:**
| App | Link |
| --- | --- |
| foobar2000 | foobar2000.org/android |
| VLC | videolan.org/vlc/download-android.html |
| Poweramp | forum.powerampapp.com (v3 releases) |
| Musicolet | krosbits.in/musicolet/download |
| AIMP | aimp.ru (Android download) |
| MX Player | mx.j2inter.com/download |
| Kodi | kodi.tv/download/android |

**Files:** `MediaServiceResolver.kt` (`appDownloadUrl`).

---

## 🖼️ Large URL Fallback to App Download Page

**Bug:** Cover art click target (`large_url`) was null when no YouTube/album/playlist URL was resolved — e.g. Morphe, AndDea Revanced Patches, and other YouTube wrapper clients that don't publish a YouTube video URL. The cover art showed but clicking did nothing.  
**Fix:** `largeUrl` now falls back to `smallUrl` (app download page / GitHub releases / store page) when no media URL is available. Applied to both `MediaRpcService` and `ManualRpc`.  
**Files:** `MediaRpcService.kt`, `ManualRpc.kt`.
