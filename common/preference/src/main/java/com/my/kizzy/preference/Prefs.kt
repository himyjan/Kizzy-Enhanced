/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Prefs.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.my.kizzy.domain.model.release.Release
import com.my.kizzy.domain.model.user.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours

object Prefs {

    private var dataStore: DataStore<Preferences>? = null

    /**
     * Synchronous mirror of the stored preferences.
     *
     * DataStore is asynchronous, but the whole app reads preferences through the
     * `Prefs[Key]` operator on the main thread. The mirror keeps that API
     * working without blocking on disk IO: every write updates the mirror
     * first and is persisted to disk in the background.
     */
    @PublishedApi
    internal val _prefsState: MutableStateFlow<Map<String, Any?>> = MutableStateFlow(emptyMap())
    val prefsState: StateFlow<Map<String, Any?>> = _prefsState.asStateFlow()

    /**
     * Completes once the synchronous mirror has been primed from disk.
     *
     * On a cold start the mirror is empty until DataStore has been read. Every
     * reader that decides whether a feature was enabled (for example the RPC
     * auto-start) has to wait for this, otherwise it only sees the defaults
     * and treats an enabled feature as disabled.
     */
    private val _initialized = CompletableDeferred<Unit>()
    val initialized: Deferred<Unit> get() = _initialized

    /** Suspends until [prefsState] contains the values that are stored on disk. */
    suspend fun awaitInitialized() = _initialized.await()

    /**
     * The keys that hold secrets and are therefore written to disk encrypted.
     *
     * The Discord token is the master key of the user's account, so it must not
     * be readable from the preferences file. Everything else is plain text.
     */
    private val SENSITIVE_KEYS = setOf(
        TOKEN,
        DISCORD_OAUTH2_ACCESS_TOKEN,
        DISCORD_OAUTH2_REFRESH_TOKEN,
        LASTFM_API_KEY,
        LASTFM_SECRET,
        LASTFM_USERNAME,
        LASTFM_PASSWORD,
        LASTFM_SESSION_KEY,
        LIBREFM_API_KEY,
        LIBREFM_SECRET,
        LIBREFM_USERNAME,
        LIBREFM_PASSWORD,
        LIBREFM_SESSION_KEY,
        LISTENBRAINZ_USER_TOKEN,
    )

    /**
     * Encrypts the value of a sensitive key before it is written to disk.
     * Returns the value unchanged when the key is not sensitive or when the
     * encryption is not available.
     */
    private fun encodeForStorage(key: String, value: String): String {
        if (key !in SENSITIVE_KEYS) return value
        return SecurePrefs.encrypt(value) ?: value
    }

    /**
     * Decrypts the value of a sensitive key that was read from disk.
     */
    private fun decodeFromStorage(key: String, value: String): String {
        if (key !in SENSITIVE_KEYS) return value
        return SecurePrefs.decrypt(value)
    }

    operator fun set(key: String, value: Any?) = when (value) {
        is String? -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit {
                if (value == null) this.remove(stringPreferencesKey(key))
                else this[stringPreferencesKey(key)] = encodeForStorage(key, value)
            }
            Unit
        }
        is Int -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit {
                this[intPreferencesKey(key)] = value
            }
            Unit
        }
        is Boolean -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit {
                this[booleanPreferencesKey(key)] = value
            }
            Unit
        }
        is Float -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit {
                this[floatPreferencesKey(key)] = value
            }
            Unit
        }
        is Long -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit {
                this[longPreferencesKey(key)] = value
            }
            Unit
        }
        else -> throw UnsupportedOperationException("Not yet implemented")
    }

    inline operator fun <reified T : Any> get(
        key: String,
        defaultValue: T? = null,
    ): T {
        val stored = _prefsState.value[key]
        val result = when (T::class) {
            String::class -> (stored as? String) ?: (defaultValue as? String ?: "")
            Int::class -> (stored as? Int) ?: (defaultValue as? Int ?: -1)
            Boolean::class -> (stored as? Boolean) ?: (defaultValue as? Boolean ?: false)
            Float::class -> (stored as? Float) ?: (defaultValue as? Float ?: -1f)
            Long::class -> (stored as? Long) ?: (defaultValue as? Long ?: -1)
            else -> throw UnsupportedOperationException("Not yet implemented")
        }
        return result as T
    }

    suspend fun remove(key: String) {
        _prefsState.update { it - key }
        // Wait for the DataStore write to complete so that a cold reboot
        // does not resurrect a deleted key (e.g. TOKEN after logout).
        launchDataStoreEdit {
            // The value could have been stored under any of the typed keys, so
            // every one of them is removed.
            this.remove(stringPreferencesKey(key))
            this.remove(intPreferencesKey(key))
            this.remove(booleanPreferencesKey(key))
            this.remove(floatPreferencesKey(key))
            this.remove(longPreferencesKey(key))
        }.await()
    }

    /**
     * Fire-and-forget removal for non-coroutine contexts (e.g. UI click handlers).
     * Updates the in-memory state immediately and launches the DataStore write
     * without waiting for it to complete.
     */
    fun removeAsync(key: String) {
        _prefsState.update { it - key }
        launchDataStoreEdit {
            this.remove(stringPreferencesKey(key))
            this.remove(intPreferencesKey(key))
            this.remove(booleanPreferencesKey(key))
            this.remove(floatPreferencesKey(key))
            this.remove(longPreferencesKey(key))
        }
    }

    fun containsKey(key: String): Boolean {
        return _prefsState.value.containsKey(key)
    }

    /**
     * Runs [block] against the mutable preferences on a background thread.
     *
     * `edit` hands out a `MutablePreferences` receiver, so the block can both
     * write values and remove keys. Returns a deferred that completes when the
     * write has been persisted to disk.
     */
    private fun launchDataStoreEdit(block: suspend MutablePreferences.() -> Unit): Deferred<Unit> {
        return CoroutineScope(Dispatchers.IO).async {
            dataStore?.edit(transform = block)
            return@async Unit
        }
    }

    internal fun initDataStore(context: Context) {
        if (dataStore != null) {
            // A second call must not leave a waiter hanging.
            _initialized.complete(Unit)
            return
        }
        try {
            // Prepare the encryption before the values are read, otherwise a
            // stored token would stay encrypted in the in-memory mirror.
            SecurePrefs.init(context)
            dataStore = context.appPreferencesDataStore
            // Prime the synchronous mirror with the values that are already
            // stored on disk, so the first reads do not return the defaults.
            CoroutineScope(Dispatchers.IO).launch {
                // Prime the synchronous mirror with values already on disk;
                // ignore failures so the UI never waits forever on defaults.
                if (runCatching {
                    dataStore?.data?.first()?.asMap()?.forEach { (key, value) ->
                        val name = key.name
                        val plain = if (value is String) decodeFromStorage(name, value) else value
                        _prefsState.update { it + (name to plain) }
                    }
                }.isFailure) {
                }
                // Signal even when the read failed, so the UI never waits
                // forever on the default (pre-priming) values.
                _initialized.complete(Unit)
            }
        } catch (e: Exception) {
            // Storage could not be opened: keep the defaults, but never leave
            // the waiters (RPC restore, UI) hanging on a deferred that will
            // not complete.
            _initialized.complete(Unit)
        }
    }

    /**
     * The DataStore delegate. `preferencesDataStore` returns a property
     * delegate that creates (and caches) the DataStore for the given context.
     */
    private val Context.appPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "app_preferences")

    fun isAppEnabled(packageName: String?): Boolean {
        val apps = get(ENABLED_APPS, "[]")
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        return enabledPackages.contains(packageName)
    }

    fun saveToPrefs(pkg: String) {
        val apps = get(ENABLED_APPS, "[]")
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        if (enabledPackages.contains(pkg))
            enabledPackages.remove(pkg)
        else
            enabledPackages.add(pkg)
        set(ENABLED_APPS, Json.encodeToString(enabledPackages))
    }

    fun isMediaAppEnabled(packageName: String?): Boolean {
        if (packageName == null) return false
        if (!containsKey(ENABLED_MEDIA_APPS)) return true
        val apps = get(ENABLED_MEDIA_APPS, Json.encodeToString(predefinedMediaApps))
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        return enabledPackages.contains(packageName)
    }

    fun saveMediaAppToPrefs(pkg: String) {
        val apps = get(ENABLED_MEDIA_APPS, Json.encodeToString(predefinedMediaApps))
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        if (enabledPackages.contains(pkg))
            enabledPackages.remove(pkg)
        else
            enabledPackages.add(pkg)
        set(ENABLED_MEDIA_APPS, Json.encodeToString(enabledPackages))
    }

    fun setMediaAppsEnabled(enabledPackages: Collection<String>) {
        set(ENABLED_MEDIA_APPS, Json.encodeToString(ArrayList(enabledPackages)))
    }

    fun getUser(): User? {
        val userJson = get(USER_DATA, "")
        return when {
            userJson.isNotEmpty() -> Json.decodeFromString(userJson)
            else -> null
        }
    }

    fun getSavedLatestRelease(): Release? {
        val json = get(LATEST_RELEASE, "")
        return when {
            json.isNotEmpty() -> Json.decodeFromString(json)
            else -> null
        }
    }

    fun saveLatestRelease(release: Release) {
        set(LATEST_RELEASE, Json.encodeToString(release))
    }

    fun checkAndAutoDeleteSavedImages() {
        val lastDeleted = get(
            key = LAST_DELETED,
            defaultValue = System.currentTimeMillis() - 24.hours.inWholeMilliseconds
        )
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDeleted > 24.hours.inWholeMilliseconds) {
            _prefsState.update { it - SAVED_IMAGES - SAVED_ARTWORK }
            launchDataStoreEdit {
                this.remove(stringPreferencesKey(SAVED_IMAGES))
                this.remove(stringPreferencesKey(SAVED_ARTWORK))
            }
            set(LAST_DELETED, currentTime)
        }
    }

    const val USER_DATA = "user"
    const val TOKEN = "token"
    const val USER_ID = "user-id"
    const val USER_BIO = "user-bio"
    const val USER_NITRO = "user-nitro"
    const val LAST_RUN_CONSOLE_RPC = "last_run_console_rpc"
    const val LAST_RUN_CUSTOM_RPC = "last_run_custom_rpc"
    const val CONSOLE_RPC_AUTO_DETECT = "console_rpc_auto_detect"
    const val CONSOLE_RPC_INCLUDE_SPOOFED = "console_rpc_include_spoofed"
    const val CONSOLE_RPC_PLATFORM = "console_rpc_platform"
    const val CONSOLE_RPC_ACTIVITY_TYPE = "console_rpc_activity_type"
    const val CONSOLE_RPC_STATUS = "console_rpc_status"
    const val CONSOLE_RPC_ENABLE_TIMESTAMPS = "console_rpc_enable_timestamps"
    const val CONSOLE_RPC_SHOW_COVER_ART = "console_rpc_show_cover_art"
    const val CONSOLE_RPC_BUTTON1_TEXT = "console_rpc_button1_text"
    const val CONSOLE_RPC_BUTTON1_URL = "console_rpc_button1_url"
    const val CONSOLE_RPC_BUTTON2_TEXT = "console_rpc_button2_text"
    const val CONSOLE_RPC_BUTTON2_URL = "console_rpc_button2_url"

    /** Id of the Console RPC preset that is currently selected. */
    const val CONSOLE_RPC_PRESET = "console_rpc_preset"

    /** JSON array with the presets the user created in the Console RPC tab. */
    const val CONSOLE_RPC_PRESETS = "console_rpc_presets"
    const val LANGUAGE = "language"
    const val ENABLED_APPS = "enabled_apps"
    const val ENABLED_MEDIA_APPS = "enabled_media_apps"
    const val ENABLED_EXPERIMENTAL_APPS = "enabled_experimental_apps"

    const val MEDIA_RPC_ARTIST_NAME = "media_rpc_artist_name"
    const val MEDIA_RPC_ALBUM_NAME = "media_rpc_album_name"
    const val MEDIA_RPC_APP_ICON = "media_rpc_app_icon"
    const val MEDIA_RPC_SHOW_CHANNEL_ICON = "media_rpc_show_channel_icon"
    const val MEDIA_RPC_SHOW_COVER_ART = "media_rpc_show_cover_art"
    const val MEDIA_RPC_ENABLE_TIMESTAMPS = "enable_timestamps"
    const val MEDIA_RPC_HIDE_ON_PAUSE = "hide_on_pause"
    const val MEDIA_RPC_SHOW_PLAYBACK_STATE = "show_playback_state"
    const val MEDIA_RPC_SHOW_SONG_AS_TITLE = "show_song_as_title"
    const val MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS = "media_rpc_show_app_name_in_details"
    const val MEDIA_RPC_MEDIA_BUTTON = "media_rpc_media_button"
    const val MEDIA_RPC_ARTIST_LINK = "media_rpc_artist_link"
    const val MEDIA_RPC_USE_APPS_RPC = "media_rpc_use_apps"
    const val MEDIA_RPC_ENABLED = "media_rpc_enabled"
    const val LAST_RUN_MEDIA_RPC = "last_run_media_rpc"
    const val LAST_RUN_MANUAL_RPC = "last_run_manual_rpc"

    /**
     * The RPC type the user last enabled, stored as the name of
     * `RpcManager.RpcType`. Restored after a cold start so the UI and the
     * service come back in the same state instead of all being off.
     */
    const val ACTIVE_RPC_TYPE = "active_rpc_type"

    /**
     * All RPC types the user currently has enabled, as a comma-separated list of
     * `RpcManager.RpcType` names. Multiple RPCs run in parallel, so a single
     * [ACTIVE_RPC_TYPE] cannot represent the real state. This is the source of
     * truth for restoring state and for the on/off switches.
     */
    const val ACTIVE_RPC_TYPES = "active_rpc_types"

    /** Whether the running CustomRpcService is in Console RPC mode. */
    const val CUSTOM_RPC_CONSOLE_MODE = "custom_rpc_console_mode"

    // Discord RPC connection method: "gateway" (default) or "oauth"
    const val RPC_CONNECTION_METHOD = "rpc_connection_method"
    const val RPC_CONNECTION_METHOD_GATEWAY = "gateway"
    const val RPC_CONNECTION_METHOD_OAUTH2 = "oauth"

    const val DISCORD_OAUTH2_APPLICATION_CLIENT_ID = "discord_oauth2_client_id"

    // Discord OAuth2 token storage (encrypted via SecurePrefs)
    const val DISCORD_OAUTH2_ACCESS_TOKEN = "discord_oauth_access_token"
    const val DISCORD_OAUTH2_REFRESH_TOKEN = "discord_oauth_refresh_token"
    const val DISCORD_OAUTH2_EXPIRES_AT = "discord_oauth_expires_at"
    const val DISCORD_OAUTH2_USER_ID = "discord_oauth_user_id"
    const val DISCORD_OAUTH2_USERNAME = "discord_oauth_username"

    // Last.fm scrobbling settings
    const val LASTFM_API_KEY = "lastfm_api_key"
    const val LASTFM_SECRET = "lastfm_secret"
    const val LASTFM_USERNAME = "lastfm_username"
    const val LASTFM_PASSWORD = "lastfm_password"
    const val LASTFM_SESSION_KEY = "lastfm_session_key"
    const val LASTFM_ENABLED = "lastfm_enabled"
    const val LASTFM_ENABLE_TIMESTAMPS = "lastfm_enable_timestamps"
    const val LASTFM_SHOW_COVER_ART = "lastfm_show_cover_art"
    const val LASTFM_SHOW_PARTY_CURRENT = "lastfm_show_party_current"
    const val LASTFM_SHOW_PARTY_MAX = "lastfm_show_party_max"

    // Libre.fm scrobbling settings
    const val LIBREFM_API_KEY = "librefm_api_key"
    const val LIBREFM_SECRET = "librefm_secret"
    const val LIBREFM_USERNAME = "librefm_username"
    const val LIBREFM_PASSWORD = "librefm_password"
    const val LIBREFM_SESSION_KEY = "librefm_session_key"
    const val LIBREFM_ENABLED = "librefm_enabled"

    // ListenBrainz scrobbling settings
    const val LISTENBRAINZ_USER_TOKEN = "listenbrainz_user_token"
    const val LISTENBRAINZ_ENABLED = "listenbrainz_enabled"
    const val LISTENBRAINZ_ENABLE_TIMESTAMPS = "listenbrainz_enable_timestamps"
    const val LISTENBRAINZ_SHOW_COVER_ART = "listenbrainz_show_cover_art"
    const val LISTENBRAINZ_SHOW_PARTY_CURRENT = "listenbrainz_show_party_current"
    const val LISTENBRAINZ_SHOW_PARTY_MAX = "listenbrainz_show_party_max"

    const val USE_RPC_BUTTONS = "use_saved_rpc_buttons"
    const val RPC_BUTTONS_DATA = "saved_rpc_buttons_data"
    const val RPC_USE_LOW_RES_ICON = "use_low_res_app_icons"
    const val CONFIGS_DIRECTORY = "configs_directory"
    const val USE_IMGUR = "use_imgur"
    const val IMGUR_CLIENT_ID = "imgur_client_id"
    const val SAVED_IMAGES = "saved_images"
    const val SAVED_ARTWORK = "saved_artwork"

    // Default cover art for all RPC types when notification has no cover art
    const val RPC_DEFAULT_COVER_ART_LARGE = "rpc_default_cover_art_large"
    const val RPC_DEFAULT_COVER_ART_SMALL = "rpc_default_cover_art_small"

    // Global switch to hide cover art images across all RPC types (show text only)
    const val RPC_HIDE_COVER_ART = "rpc_hide_cover_art"

    const val DARK_THEME = "dark_theme_value"
    const val HIGH_CONTRAST = "high_contrast"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val THEME_COLOR = "theme_color"
    // Theme style: 0 = Kizzy (Monet seed/palette), 1 = Discord dark, 2 = Discord light
    const val THEME_STYLE = "theme_style"
    const val CUSTOM_THEME_COLOR = "custom_theme_color"
    const val IS_FIRST_LAUNCHED = "is_first_launched"
    const val CUSTOM_ACTIVITY_TYPE = "custom_activity_type"
    const val SHOW_LOGS_IN_COMPACT_MODE = "logs_compact_mode"
    const val LOGS_AUTO_SCROLL = "logs_auto_scroll"
    const val LOGS_DEVELOPER_MODE = "logs_developer_mode"
    const val PALETTE_STYLE = "palette_style"
    const val APPLY_FIELDS_FROM_LAST_RUN_RPC = "enable_setting_from_last_config"
    const val CUSTOM_ACTIVITY_STATUS = "custom_activity_status"
    const val LATEST_RELEASE = "latest_release"
    const val LAST_DELETED = "last_deleted"
    const val CUSTOM_ACTIVITY_APPLICATION_ID = "custom_activity_application_id_"

    val predefinedMediaApps: List<String> = listOf(
        "com.google.android.apps.youtube.music",
        "com.spotify.music",
        "com.google.android.music",
        "com.amazon.mp3",
        "com.apple.android.music",
        "com.soundcloud.android",
        "deezer.android.app",
        "com.jrtstudio.AnotherMusicPlayer",
        "com.pandora.android",
        "com.rhapsody",
        "com.sonyericsson.music",
        "com.aspiro.tidal",
        "com.sec.android.app.music",
        "com.tbig.playerpro",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.unplugged",
        "com.google.android.apps.youtube.googletv",
        "com.google.android.apps.youtube.tv",
        "com.google.android.youtube",
        "app.morphe.android.youtube",
        "app.morphe.android.apps.youtube.music",
        "anddea.youtube",
        "anddea.youtube.music",
        "com.foobar2000.foobar2000",
        "com.netflix.mediaclient",
        "com.kick.mobile",
        "tv.twitch.android.app",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.google.android.apps.mediashell",
        "com.google.android.videos",
        "org.videolan.vlc"
    )

    const val MANUAL_RPC_USE_APPS_RPC = "manual_rpc_use_apps"
    const val MANUAL_RPC_USE_MEDIA_RPC = "manual_rpc_use_media"
    const val MANUAL_RPC_TEMPLATE_NAME = "manual_rpc_template_name"
    const val MANUAL_RPC_TEMPLATE_DETAILS = "manual_rpc_template_details"
    const val MANUAL_RPC_TEMPLATE_STATE = "manual_rpc_template_state"
    const val MANUAL_RPC_APP_ACTIVITY_TYPES = "manual_rpc_app_activity_types"
    const val APP_ACTIVITY_TYPES = "app_activity_types"
    const val MEDIA_RPC_ACTIVITY_TYPE = "media_rpc_activity_type"
    const val ACTIVITY_TYPE_AUTO = -1
    const val AUTO_ACTIVITY_TYPE = "auto_activity_type"
    const val MANUAL_RPC_SHOW_COVER_ART = "manual_rpc_show_cover_art"
    const val MANUAL_RPC_SHOW_APP_ICON = "manual_rpc_show_app_icon"
    const val MANUAL_RPC_SHOW_CHANNEL_ICON = "manual_rpc_show_channel_icon"
    const val MANUAL_RPC_SHOW_PLAYBACK_STATE = "manual_rpc_show_playback_state"
    const val MANUAL_RPC_SHOW_ARTIST_NAME = "manual_rpc_show_artist_name"
    const val MANUAL_RPC_SHOW_ALBUM_NAME = "manual_rpc_show_album_name"
    const val MANUAL_RPC_ENABLE_TIMESTAMPS = "manual_rpc_enable_timestamps"
    const val MANUAL_RPC_HIDE_ON_PAUSE = "manual_rpc_hide_on_pause"
    const val MANUAL_RPC_PLATFORM = "manual_rpc_platform"
    const val MANUAL_RPC_USE_BUTTONS = "manual_rpc_use_buttons"
    const val MANUAL_RPC_BUTTON1_TEXT = "manual_rpc_button1_text"
    const val MANUAL_RPC_BUTTON1_URL = "manual_rpc_button1_url"
    const val MANUAL_RPC_BUTTON2_TEXT = "manual_rpc_button2_text"
    const val MANUAL_RPC_BUTTON2_URL = "manual_rpc_button2_url"
    const val MANUAL_RPC_USE_CUSTOM_APP_ID = "manual_rpc_use_custom_app_id"
    const val MANUAL_RPC_APPLICATION_ID = "manual_rpc_application_id"
    const val MANUAL_RPC_TIMESTAMP_MODE = "manual_rpc_timestamp_mode"
    const val MANUAL_RPC_TIMESTAMP_CURRENT_START = "manual_rpc_timestamp_current_start"
    const val MANUAL_RPC_TIMESTAMP_CUSTOM_START = "manual_rpc_timestamp_custom_start"
    const val MANUAL_RPC_TIMESTAMP_CUSTOM_END = "manual_rpc_timestamp_custom_end"
    const val MANUAL_RPC_SHOW_SONG_AS_TITLE = "manual_rpc_show_song_as_title"
    const val MANUAL_RPC_SHOW_APP_NAME_IN_DETAILS = "manual_rpc_show_app_name_in_details"
    const val MANUAL_RPC_TITLE_SOURCE = "manual_rpc_title_source"

    // Advanced settings (matching Custom RPC / RpcConfig fields)
    const val MANUAL_RPC_ACTIVITY_TYPE = "manual_rpc_activity_type"
    const val MANUAL_RPC_STATUS = "manual_rpc_status"
    const val MANUAL_RPC_LARGE_IMAGE = "manual_rpc_large_image"
    const val MANUAL_RPC_SMALL_IMAGE = "manual_rpc_small_image"
    const val MANUAL_RPC_LARGE_TEXT = "manual_rpc_large_text"
    const val MANUAL_RPC_SMALL_TEXT = "manual_rpc_small_text"
    const val MANUAL_RPC_STREAM_URL = "manual_rpc_stream_url"
    const val MANUAL_RPC_PARTY_CURRENT_SIZE = "manual_rpc_party_current_size"
    const val MANUAL_RPC_PARTY_MAX_SIZE = "manual_rpc_party_max_size"

    fun saveAppActivityType(packageName: String, activityType: Int) {
        val map = getAppActivityTypes().toMutableMap()
        map[packageName] = activityType
        set(APP_ACTIVITY_TYPES, Json.encodeToString(map))
    }

    fun clearAppActivityType(packageName: String) {
        val map = getAppActivityTypes().toMutableMap()
        if (map.remove(packageName) != null) {
            set(APP_ACTIVITY_TYPES, Json.encodeToString(map))
        }
    }

    fun getAppActivityTypes(): Map<String, Int> {
        val json = get(APP_ACTIVITY_TYPES, "")
        if (json.isNotBlank()) return decodeAppActivityTypes(json)

        val legacy = decodeAppActivityTypes(get(MANUAL_RPC_APP_ACTIVITY_TYPES, "{}"))
        if (legacy.isNotEmpty()) {
            set(APP_ACTIVITY_TYPES, Json.encodeToString(legacy))
        }
        return legacy
    }

    private fun decodeAppActivityTypes(jsonString: String): Map<String, Int> = try {
        Json.decodeFromString<Map<String, Int>>(jsonString)
    } catch (_: Exception) {
        emptyMap()
    }

    fun isExperimentalAppEnabled(packageName: String?): Boolean {
        val apps = get(ENABLED_EXPERIMENTAL_APPS, "[]")
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        return enabledPackages.contains(packageName)
    }

    fun saveExperimentalAppToPrefs(pkg: String) {
        val apps = get(ENABLED_EXPERIMENTAL_APPS, "[]")
        val enabledPackages: ArrayList<String> = Json.decodeFromString(apps)
        if (enabledPackages.contains(pkg))
            enabledPackages.remove(pkg)
        else
            enabledPackages.add(pkg)
        set(ENABLED_EXPERIMENTAL_APPS, Json.encodeToString(enabledPackages))
    }

    fun setExperimentalAppsEnabled(packages: List<String>) {
        set(ENABLED_EXPERIMENTAL_APPS, Json.encodeToString(packages.toMutableList()))
    }
}
