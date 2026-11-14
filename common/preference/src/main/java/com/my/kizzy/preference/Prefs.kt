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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.my.kizzy.domain.model.release.Release
import com.my.kizzy.domain.model.user.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
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
    private val _prefsState = MutableStateFlow(emptyMap<String, Any?>())
    val prefsState: Flow<Map<String, Any?>> = _prefsState.asStateFlow()

    operator fun set(key: String, value: Any?) = when (value) {
        is String? -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
        is Int -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
        is Boolean -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
        is Float -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }
        is Long -> {
            _prefsState.update { it + (key to value) }
            launchDataStoreEdit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
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

    fun remove(key: String) {
        _prefsState.update { it - key }
        launchDataStoreEdit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }

    fun containsKey(key: String): Boolean {
        return _prefsState.value.containsKey(key)
    }

    /**
     * Runs [block] against the mutable preferences on a background thread.
     *
     * `edit` hands out a `MutablePreferences` receiver, so the block can both
     * write values and remove keys.
     */
    private fun launchDataStoreEdit(block: suspend MutablePreferences.() -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            dataStore?.edit { block() }
        }
    }

    internal fun initDataStore(context: Context) {
        if (dataStore == null) {
            dataStore = preferencesDataStore(context = context, name = "app_preferences")
            // Prime the synchronous mirror with the values that are already
            // stored on disk, so the first reads do not return the defaults.
            CoroutineScope(Dispatchers.IO).launch {
                dataStore?.data?.first()?.asMap()?.forEach { (key, value) ->
                    _prefsState.update { it + (key.name to value) }
                }
            }
        }
    }

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
            remove(SAVED_IMAGES)
            remove(SAVED_ARTWORK)
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
    const val LANGUAGE = "language"
    const val ENABLED_APPS = "enabled_apps"
    const val ENABLED_MEDIA_APPS = "enabled_media_apps"
    const val ENABLED_EXPERIMENTAL_APPS = "enabled_experimental_apps"

    const val MEDIA_RPC_ARTIST_NAME = "media_rpc_artist_name"
    const val MEDIA_RPC_ALBUM_NAME = "media_rpc_album_name"
    const val MEDIA_RPC_APP_ICON = "media_rpc_app_icon"
    const val MEDIA_RPC_SHOW_COVER_ART = "media_rpc_show_cover_art"
    const val MEDIA_RPC_ENABLE_TIMESTAMPS = "enable_timestamps"
    const val MEDIA_RPC_HIDE_ON_PAUSE = "hide_on_pause"
    const val MEDIA_RPC_SHOW_PLAYBACK_STATE = "show_playback_state"
    const val MEDIA_RPC_SHOW_SONG_AS_TITLE = "show_song_as_title"
    const val MEDIA_RPC_MEDIA_BUTTON = "media_rpc_media_button"
    const val MEDIA_RPC_ARTIST_LINK = "media_rpc_artist_link"
    const val MEDIA_RPC_ENABLED = "media_rpc_enabled"
    const val LAST_RUN_MEDIA_RPC = "last_run_media_rpc"
    const val LAST_RUN_EXPERIMENTAL_RPC = "last_run_experimental_rpc"

    const val USE_RPC_BUTTONS = "use_saved_rpc_buttons"
    const val RPC_BUTTONS_DATA = "saved_rpc_buttons_data"
    const val RPC_USE_LOW_RES_ICON = "use_low_res_app_icons"
    const val CONFIGS_DIRECTORY = "configs_directory"
    const val USE_IMGUR = "use_imgur"
    const val IMGUR_CLIENT_ID = "imgur_client_id"
    const val SAVED_IMAGES = "saved_images"
    const val SAVED_ARTWORK = "saved_artwork"

    const val DARK_THEME = "dark_theme_value"
    const val HIGH_CONTRAST = "high_contrast"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val THEME_COLOR = "theme_color"
    const val CUSTOM_THEME_COLOR = "custom_theme_color"
    const val IS_FIRST_LAUNCHED = "is_first_launched"
    const val CUSTOM_ACTIVITY_TYPE = "custom_activity_type"
    const val SHOW_LOGS_IN_COMPACT_MODE = "logs_compact_mode"
    const val LOGS_AUTO_SCROLL = "logs_auto_scroll"
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
        "com.netflix.mediaclient",
        "com.kick.mobile",
        "tv.twitch.android.app",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "com.google.android.apps.mediashell",
        "com.google.android.videos",
        "org.videolan.vlc"
    )

    const val EXPERIMENTAL_RPC_USE_APPS_RPC = "experimental_rpc_use_apps"
    const val EXPERIMENTAL_RPC_USE_MEDIA_RPC = "experimental_rpc_use_media"
    const val EXPERIMENTAL_RPC_TEMPLATE_NAME = "experimental_rpc_template_name"
    const val EXPERIMENTAL_RPC_TEMPLATE_DETAILS = "experimental_rpc_template_details"
    const val EXPERIMENTAL_RPC_TEMPLATE_STATE = "experimental_rpc_template_state"
    const val EXPERIMENTAL_RPC_APP_ACTIVITY_TYPES = "experimental_rpc_app_activity_types"
    const val APP_ACTIVITY_TYPES = "app_activity_types"
    const val MEDIA_RPC_ACTIVITY_TYPE = "media_rpc_activity_type"
    const val ACTIVITY_TYPE_AUTO = -1
    const val AUTO_ACTIVITY_TYPE = "auto_activity_type"
    const val EXPERIMENTAL_RPC_SHOW_COVER_ART = "experimental_rpc_show_cover_art"
    const val EXPERIMENTAL_RPC_SHOW_APP_ICON = "experimental_rpc_show_app_icon"
    const val EXPERIMENTAL_RPC_SHOW_PLAYBACK_STATE = "experimental_rpc_show_playback_state"
    const val EXPERIMENTAL_RPC_ENABLE_TIMESTAMPS = "experimental_rpc_enable_timestamps"
    const val EXPERIMENTAL_RPC_HIDE_ON_PAUSE = "experimental_rpc_hide_on_pause"
    const val EXPERIMENTAL_RPC_PLATFORM = "experimental_rpc_platform"
    const val EXPERIMENTAL_RPC_USE_BUTTONS = "experimental_rpc_use_buttons"
    const val EXPERIMENTAL_RPC_BUTTON1_TEXT = "experimental_rpc_button1_text"
    const val EXPERIMENTAL_RPC_BUTTON1_URL = "experimental_rpc_button1_url"
    const val EXPERIMENTAL_RPC_BUTTON2_TEXT = "experimental_rpc_button2_text"
    const val EXPERIMENTAL_RPC_BUTTON2_URL = "experimental_rpc_button2_url"
    const val EXPERIMENTAL_RPC_USE_CUSTOM_APP_ID = "experimental_rpc_use_custom_app_id"
    const val EXPERIMENTAL_RPC_APPLICATION_ID = "experimental_rpc_application_id"
    const val EXPERIMENTAL_RPC_TIMESTAMP_MODE = "experimental_rpc_timestamp_mode"
    const val EXPERIMENTAL_RPC_TIMESTAMP_CURRENT_START = "experimental_rpc_timestamp_current_start"
    const val EXPERIMENTAL_RPC_TIMESTAMP_CUSTOM_START = "experimental_rpc_timestamp_custom_start"
    const val EXPERIMENTAL_RPC_TIMESTAMP_CUSTOM_END = "experimental_rpc_timestamp_custom_end"
    const val EXPERIMENTAL_RPC_SHOW_SONG_AS_TITLE = "experimental_rpc_show_song_as_title"
    const val EXPERIMENTAL_RPC_TITLE_SOURCE = "experimental_rpc_title_source"

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

        val legacy = decodeAppActivityTypes(get(EXPERIMENTAL_RPC_APP_ACTIVITY_TYPES, "{}"))
        if (legacy.isNotEmpty()) {
            set(APP_ACTIVITY_TYPES, Json.encodeToString(legacy))
        }
        return legacy
    }

    private fun decodeAppActivityTypes(json: String): Map<String, Int> = try {
        Json.decodeFromString<Map<String, Int>>(json)
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
}
