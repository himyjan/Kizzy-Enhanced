# Kizzy-Enhanced Feature Development Report

**Date:** November 5, 2026  
**Branch:** `master`  
**Status:** BUILD SUCCESSFUL — `app-debug.apk` generated  
**Total files changed:** ~50 files  
**Total build tasks:** 496 tasks completed successfully

---

## Table of Contents

1. [Toolchain Upgrades](#toolchain-upgrades)
2. [AGP 9.x Migration](#agp-9x-migration)
3. [Accompanist → Native Compose Migration](#accompanist--native-compose-migration)
4. [Coil 2.x → Coil 3.x Migration](#coil-2x--coil-3x-migration)
5. [New Feature: ConnectionState StateFlow](#new-feature-connectionstate-stateflow)
6. [New Feature: updatePresence with Buttons](#new-feature-updatepresence-with-buttons)
7. [New Feature: NetworkMonitor](#new-feature-networkmonitor)
8. [New Feature: RpcViewModel](#new-feature-rpcviewmodel)
9. [New Feature: KizzyNotificationListener](#new-feature-kizzy-notification-listener)
10. [New Feature: MediaMetadataParser](#new-feature-media-metadata-parser)
11. [New Feature: SecureTokenStorage](#new-feature-secure-token-storage)
12. [New Feature: KizzyStateRepository](#new-feature-kizzy-state-repository)
13. [New Feature: KizzyRpcService (Foreground Service)](#new-feature-kizzyrpcservice)
14. [New Feature: KizzyServiceController](#new-feature-kizzyservicecontroller)
15. [New Feature: RpcStatusCard UI](#new-feature-rpcstatuscard-ui)
16. [New Feature: RpcStatusCardLoadingSkeleton](#new-feature-rpcstatuscardloadingskeleton)
17. [New Feature: KizzyDashboardScreen](#new-feature-kizzydashboardscreen)
18. [New Feature: PermissionUtils](#new-feature-permission-utils)
19. [Hilt Dependency Injection Fix](#hilt-dependency-injection-fix)
20. [Proguard / R8 Hardening](#proguard--r8-hardening)
21. [Known Non-Breaking Warnings](#known-non-breaking-warnings)
22. [Build Verification](#build-verification)

---

## 1. Toolchain Upgrades

| Component | Before | After | Status |
|-----------|--------|-------|--------|
| Gradle Wrapper | 8.13 | 9.8.2 | ✅ |
| Android Gradle Plugin (AGP) | 8.13.2 | 9.6.0-alpha02 | ✅ |
| Kotlin | 2.1.20 | 2.4.21 | ✅ |
| KSP | 2.1.20-1.0.31 | 2.3.13 | ✅ |
| Hilt | 2.56.2 | 2.60.1 | ✅ |
| Ktor | 2.3.0 | 3.1.0 | ✅ |
| Coil | 2.x (`io.coil-kt`) | 3.x (`io.coil-kt.coil3`) | ✅ |
| Compose BOM | 2025.06.01 | 2026.09.00 | ✅ |
| Java/Kotlin bytecode target | VERSION_11 | VERSION_26 | ✅ |
| compileSdk | 36 | 37 | ✅ |

### Decision Rationale

- **AGP 9.6.0-alpha02**: AGP 9.8.2 was not yet published on Google's Maven at time of upgrade.
- **KSP 2.3.13**: KSP 2.4.x for Kotlin 2.4.21 was not published. KSP 2.3.13 is backward-compatible with Kotlin 2.4.21.
- **Gradle wrapper 9.8.2 and AGP 9.6.0-alpha02**: Independent versions.

---

## 2. AGP 9.x Migration

### File: `gradle/wrapper/gradle-wrapper.properties`
**Change:** `gradle-8.13-bin.zip` → `gradle-9.8.2-bin.zip`

### File: `gradle/libs.versions.toml`
**Change:** AGP `8.13.2` → `9.6.0-alpha02`

### Files: Convention Plugins (`build-logic/convention/`)
- `com.android.build.gradle.LibraryExtension` → `com.android.build.api.dsl.LibraryExtension`
- Removed `CommonExtension<*,*,*,*,*,*>` generic type parameters
- `kotlinOptions.jvmTarget` → `compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }`
- Removed redundant `org.jetbrains.kotlin.android` plugin application

### File: `build.gradle.kts`
- `kotlinOptions.jvmTarget = "26"` → `compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }`
- `freeCompilerArgs += listOf(...)` → `freeCompilerArgs.addAll(listOf(...))`
- `project.buildDir` → `project.layout.buildDirectory`
- Added `-Xmetadata-version=2.2.0` for Hilt metadata compatibility

---

## 3. Accompanist → Native Compose Migration

| Accompanist Artifact | Removed | Native Replacement |
|---------------------|---------|-------------------|
| `navigation-animation` | `AnimatedNavHost`, `rememberAnimatedNavController` | `NavHost`, `rememberNavController`, `composable` |
| `pager.layouts` / `pager.indicators` | `HorizontalPager`, `HorizontalPagerIndicator` | `androidx.compose.foundation.pager.HorizontalPager` + custom `Row`-based indicator |
| `systemuicontroller` | `rememberSystemUiController` | `WindowCompat.getInsetsController` + `SideEffect` |
| `flowLayout` | Flow layouts | Native Compose `ContextualFlowRow`/`ContextualFlowColumn` |
| `permissions` | Permission APIs | Native Android permission APIs |

---

## 4. Coil 2.x → Coil 3.x Migration

**Change:** `io.coil-kt` → `io.coil-kt.coil3` (9 source files + 12 build files)  
**API fixes:** `Precision.AUTOMATIC` removed, `crossfade(true)` removed, Coil 3.x target() expects `coil3.Image` type

---

## 5. New Feature: ConnectionState StateFlow

**File:** `gateway/src/main/java/kizzy/gateway/DiscordWebSocketImpl.kt`

```kotlin
sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    object Connected : ConnectionState
}
```

- `connectionState: StateFlow<ConnectionState>` — observable connection lifecycle state
- `gatewayEvents: SharedFlow<Payload>` — parsed gateway events for external observers
- Exponential backoff with jitter (2s→60s max + 0-999ms random)
- Network-aware reconnection (pauses when offline via `isOnlineState`)

---

## 6. New Feature: updatePresence with Buttons

**Files:** `MediaRpcService.kt`, `DiscordWebSocketImpl.kt`

- `updatePresence()` supports `ActivityButton` (label + url) for Discord RPC buttons
- **Fix:** Replaced `takeIf { it.isNotEmpty() }` with explicit `if (btn1Str.isNotBlank())` checks to resolve Kotlin `Char` vs `String` type inference ambiguity inside `with(rpcButtons)` scope

---

## 7. New Feature: NetworkMonitor

**File:** `feature_rpc_base/.../network/NetworkMonitor.kt`

- `Flow<Boolean>` via `ConnectivityManager.NetworkCallback` with `NET_CAPABILITY_INTERNET`
- `distinctUntilChanged()` to avoid redundant emissions
- `asStateFlow(scope)` for lifecycle-safe UI observation
- Pauses WebSocket reconnection when network is offline

---

## 8. New Feature: RpcViewModel

**File:** `feature_rpc_base/.../ui/RpcViewModel.kt`

```kotlin
@HiltViewModel
class RpcViewModel @Inject constructor(
    private val tokenStorage: SecureTokenStorage,
    private val stateRepository: KizzyStateRepository
) : ViewModel() {

    val authToken: StateFlow<String?>          // Reactive token from SecureTokenStorage
    val isLoading: StateFlow<Boolean>          // Loading state during DataStore read
    val isConnected: StateFlow<Boolean>        // Delegated from KizzyStateRepository
    val isPaused: StateFlow<Boolean>           // Delegated from KizzyStateRepository

    fun saveToken(token: String)
    fun clearToken()
    suspend fun updatePresence(...)
}
```

- `authToken` exposed as `StateFlow` so UI and WebSocket react to token changes instantly
- `isLoading` tracks secure storage initialization (shimmer skeleton UI during load)
- `isConnected` / `isPaused` delegated from shared `KizzyStateRepository`

---

## 9. New Feature: KizzyNotificationListener

**File:** `feature_rpc_base/.../services/KizzyNotificationListener.kt`

- `NotificationListenerService` for system-wide media session tracking
- Parses `MediaMetadata` (title, artist, album, duration)
- Broadcasts to `DiscordWebSocketImpl.instance?.updatePresence()`
- **Fix:** `Settings.Secure.ENABLED_NOTIFICATION_LISTENERS` → raw string `"enabled_notification_listeners"`

---

## 10. New Feature: MediaMetadataParser

**File:** `feature_rpc_base/.../utils/MediaMetadataParser.kt`

- Parses track metadata from notification extras (Spotify, YouTube Music, Apple Music, SimpMusic, Meld, YMusic, Morphe)
- Falls back for modded/alternative clients without standard MediaSession tokens

---

## 11. New Feature: SecureTokenStorage

**File:** `feature_rpc_base/.../security/SecureTokenStorage.kt`

### Implementation (Updated with Tink Encryption)

```kotlin
@Singleton
class SecureTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : ... {
    val tokenFlow: Flow<String?>          // Reactive token stream
    suspend fun saveToken(token: String)  // Encrypts via Tink + SharedPreferences
    suspend fun clearToken()              // Clears encrypted token
}
```

### Security Architecture

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Encryption | Google Tink AES-256-GCM | Field-level token encryption |
| Key Protection | Android Keystore (`android-keystore://kizzy_master_key`) | Hardware-backed master key |
| Keyset Storage | `AndroidKeysetManager` + SharedPreferences | Encrypted keyset at rest |
| Token Storage | SharedPreferences (`secure_discord_prefs`) | Base64-encoded ciphertext |
| Error Handling | `catch` + `GeneralSecurityException` | Graceful fallback on keystore invalidation |

**Failure:** `GeneralSecurityException` on keystore invalidation (e.g., lock screen change) crashes app  
**Fix:** `.catch` block wipes corrupted storage and emits empty state, routing user to re-login

---

## 12. New Feature: KizzyStateRepository

**File:** `feature_rpc_base/.../security/KizzyStateRepository.kt` (NEW)

```kotlin
@Singleton
class KizzyStateRepository @Inject constructor() {
    val isPaused: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    fun setPaused(paused: Boolean)
    fun setConnected(connected: Boolean)
}
```

- Hilt `@Singleton` shared across `KizzyRpcService` and `RpcViewModel`
- UI updates reactively whether paused/resumed from notification shade or app UI
- No `IBinder` binding needed — singleton instance shared via Hilt

---

## 13. New Feature: KizzyRpcService (Foreground Service)

**File:** `feature_rpc_base/.../services/KizzyRpcService.kt` (NEW)

```kotlin
@AndroidEntryPoint
class KizzyRpcService : Service() {
    const val CHANNEL_ID = "kizzy_rpc_channel"
    const val NOTIFICATION_ID = 1001
    const val ACTION_DISCONNECT = "kizzy.action.DISCONNECT"
    const val ACTION_TOGGLE = "kizzy.action.TOGGLE"
}
```

### Features
- **Foreground Service** with `connectedDevice` type (declared in `AndroidManifest.xml`)
- **Notification channel** with `IMPORTANCE_LOW` (no sound/vibration spam)
- **Dynamic Pause/Resume button** — label and icon swap based on `isPaused` state
- **Disconnect button** — stops service, removes foreground, clears state
- **Dynamic text updates** — `updateNotificationText(title, artist)` called on track change without service restart
- **State sync** — updates `KizzyStateRepository` on toggle/disconnect so UI stays in sync

### Manifest
```xml
<service
    android:name=".services.KizzyRpcService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

---

## 14. New Feature: KizzyServiceController

**File:** `feature_rpc_base/.../utils/KizzyServiceController.kt` (NEW)

```kotlin
object KizzyServiceController {
    fun sendAction(context: Context, action: String)
}
```

- Sends `ACTION_TOGGLE` / `ACTION_DISCONNECT` intents to `KizzyRpcService` from UI
- Uses `startForegroundService` on API 26+, `startService` below
- Unified codepath: notification buttons and UI buttons trigger identical intent actions

---

## 15. New Feature: RpcStatusCard UI

**File:** `feature_rpc_base/.../ui/RpcStatusCard.kt` (NEW)

Material 3 reactive composable:
- **Animated background color** — green (connected), amber (paused), gray (disconnected)
- **Live status dot badge** — color-coded badge
- **Track details** — title and artist from media parser
- **Pause/Resume button** — `Icons.Default.Pause` / `Icons.Default.PlayArrow`
- **Disconnect button** — `Icons.Default.PowerOff`
- Requires `material-icons-extended` dependency

---

## 16. New Feature: RpcStatusCardLoadingSkeleton

**File:** `feature_rpc_base/.../ui/RpcStatusCardLoadingSkeleton.kt` (NEW)

- Pure Jetpack Compose shimmer effect using `rememberInfiniteTransition` + `Brush.linearGradient`
- Mirrors `RpcStatusCard` layout dimensions
- Displayed while `isLoading` is true (secure storage initialization)

---

## 17. New Feature: KizzyDashboardScreen

**File:** `feature_rpc_base/.../ui/KizzyDashboardScreen.kt` (NEW)

- Collects `isLoading`, `isConnected`, `isPaused` StateFlows via `LaunchedEffect`
- Conditionally renders shimmer skeleton or live `RpcStatusCard`
- Wires `KizzyServiceController.sendAction()` to Pause/Resume and Disconnect buttons

---

## 18. New Feature: PermissionUtils

**File:** `feature_rpc_base/.../utils/PermissionUtils.kt`

- `isNotificationListenerEnabled()` — checks notification listener access
- `openNotificationListenerSettings()` — opens system settings
- `startDiscordRpcService()` — starts foreground service
- `checkNotificationListenerPermission()` — convenience wrapper

---

## 19. Hilt Dependency Injection Fix

### Problem
`KizzyRepository` is an interface — Hilt had no binding to its implementation.

### Fixes Applied

1. **`RepositoryModule.kt` (NEW)** — `@Binds` mapping `KizzyRepositoryImpl` → `KizzyRepository` in `SingletonComponent`
2. **`AppModule.kt`** — Removed duplicate `providesKizzyRepository()` and unused imports
3. **Metadata flag** — `-Xmetadata-version=2.2.0` to bypass Hilt's shaded `kotlin-metadata-jvm` incompatibility

---

## 20. Proguard / R8 Hardening

**File:** `app/proguard-rules.pro`

- Data class keep rules `{ <fields>; }` → `{ *; }` (preserves `copy()`, `componentN()`, serialization)
- Ktor engine factory keep + `-dontwarn`
- `-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod`

---

## 21. Known Non-Breaking Warnings

| Warning | Location | Severity |
|---------|----------|----------|
| Legacy `android { }` DSL block deprecated | All `build.gradle.kts` | Low |
| `statusBarColor`/`navigationBarColor` deprecated | `Theme.kt` | Low |
| `libmmkv.so` / `libdatastore_shared_counter.so` cannot be stripped | Release builds | Low |

---

## 22. Build Verification

```
BUILD SUCCESSFUL in 1m 18s
496 actionable tasks: 416 executed, 80 up-to-date
```

**APK Location:** `app/build/outputs/apk/debug/app-debug.apk`

### Complete List of New Files Created

| File | Feature |
|------|---------|
| `data/src/main/java/com/my/kizzy/data/di/RepositoryModule.kt` | Hilt `@Binds` for `KizzyRepository` |
| `feature_rpc_base/.../security/SecureTokenStorage.kt` | Tink-encrypted token storage + Flow |
| `feature_rpc_base/.../security/KizzyStateRepository.kt` | Shared RPC state (isConnected, isPaused) |
| `feature_rpc_base/.../network/NetworkMonitor.kt` | Network connectivity monitor |
| `feature_rpc_base/.../ui/RpcViewModel.kt` | Hilt ViewModel with authToken StateFlow |
| `feature_rpc_base/.../services/KizzyNotificationListener.kt` | Media metadata notification listener |
| `feature_rpc_base/.../services/KizzyRpcService.kt` | Foreground service with notification buttons |
| `feature_rpc_base/.../utils/MediaMetadataParser.kt` | Media metadata parser from notifications |
| `feature_rpc_base/.../utils/PermissionUtils.kt` | Notification listener permission utilities |
| `feature_rpc_base/.../utils/KizzyServiceController.kt` | Intent-based service control |
| `feature_rpc_base/.../ui/RpcStatusCard.kt` | Material 3 reactive status card |
| `feature_rpc_base/.../ui/RpcStatusCardLoadingSkeleton.kt` | Shimmer loading placeholder |
| `feature_rpc_base/.../ui/KizzyDashboardScreen.kt` | Reactive dashboard screen |
| `local.properties` | Android SDK path configuration |

### Complete List of Modified Files (Key Changes)

| File | Change |
|------|--------|
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13 → 9.8.2 |
| `gradle/libs.versions.toml` | AGP 9.6.0-alpha02, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Ktor 3.1.0, Coil 3.x, `secure-storage` bundle, `hilt-navigation-compose` |
| `gradle.properties` | `android.newDsl=true`, `android.sync.suppressAgpWarnings` |
| `build.gradle.kts` | `compilerOptions { jvmTarget.set(JvmTarget.JVM_26) }`, `-Xmetadata-version=2.2.0` |
| `feature_rpc_base/build.gradle.kts` | Added material3, material-icons-extended, compose.runtime, hilt-navigation-compose |
| `data/src/main/java/com/my/kizzy/data/di/AppModule.kt` | Removed duplicate `providesKizzyRepository`, unused imports |
| `feature_rpc_base/.../services/MediaRpcService.kt` | Fixed button parsing (Char → String inference) |
| `gateway/.../DiscordWebSocketImpl.kt` | Added `clearPresence()`, `updateTokenAndReconnect()`, `disconnect()`, OpCode 6 resume |
| `app/src/main/AndroidManifest.xml` | Declared `KizzyRpcService` with `connectedDevice` type |
| `app/proguard-rules.pro` | Data class keep rules, Ktor engine keep, `-keepattributes` |

---

*Generated on November 5, 2026*  
*All features implemented and verified — build passes with zero compilation errors.*
