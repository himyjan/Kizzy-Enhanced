/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MediaRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_media_rpc

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PlaylistAddCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.utils.AppsInfo
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ALBUM_NAME
import com.my.kizzy.preference.Prefs.MEDIA_RPC_APP_ICON
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_NAME
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_HIDE_ON_PAUSE
import com.my.kizzy.preference.Prefs.MEDIA_RPC_SHOW_COVER_ART
import com.my.kizzy.preference.Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE
import com.my.kizzy.preference.Prefs.MEDIA_RPC_USE_APPS_RPC
import com.my.kizzy.preference.RPC_INSTANCE_MEDIA
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.ActivityTypeChip
import com.my.kizzy.ui.components.ActivityTypePreference
import com.my.kizzy.ui.components.AppsItem
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.SwitchBar
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import com.my.kizzy.ui.components.preference.PreferencesHint
import com.my.kizzy.ui.theme.KizzyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRPC(
    onBackPressed: () -> Unit,
    state: MediaAppsState,
    hasNotificationAccess: Boolean,
    updateMediaAppEnabled: (String) -> Unit,
    selectAllMediaApps: () -> Unit,
    deselectAllMediaApps: () -> Unit,
    updateAppActivityType: (String, Int?) -> Unit = { _, _ -> },
    instanceId: String? = null,
) {
    val targetInstanceId = instanceId ?: RPC_INSTANCE_MEDIA
    fun key(prefKey: String) = Prefs.scopedKey(prefKey, instanceId)
    fun readBool(prefKey: String, default: Boolean): Boolean =
        if (instanceId == null) Prefs[prefKey, default] else Prefs[key(prefKey), default]

    fun writeBool(prefKey: String, value: Boolean) {
        if (instanceId == null) {
            Prefs[prefKey] = value
        } else {
            Prefs[key(prefKey)] = value
        }
    }

    fun readInt(prefKey: String, default: Int): Int =
        if (instanceId == null) Prefs[prefKey, default] else Prefs[key(prefKey), default]

    fun writeInt(prefKey: String, value: Int) {
        if (instanceId == null) {
            Prefs[prefKey] = value
        } else {
            Prefs[key(prefKey)] = value
        }
    }

    val context = LocalContext.current
    val rpcManager = RpcManager.getInstance(context)
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetInstanceId) {
        rpcManager.refreshFromServices()
    }

    var mediaRpcRunning by remember(targetInstanceId) {
        mutableStateOf(RpcType.MediaRpc in rpcManager.activeRpc.value)
    }
    LaunchedEffect(rpcManager.activeRpc, targetInstanceId) {
        mediaRpcRunning = RpcType.MediaRpc in rpcManager.activeRpc.value
    }

    var isArtistEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_ARTIST_NAME, true))
    }
    var isAlbumEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_ALBUM_NAME, true))
    }
    var isCoverArtEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_SHOW_COVER_ART, true))
    }
    var isAppIconEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_APP_ICON, false))
    }
    var isChannelIconEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(Prefs.MEDIA_RPC_SHOW_CHANNEL_ICON, false))
    }
    var useAppsRpc by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_USE_APPS_RPC, false))
    }
    var isTimestampsEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_ENABLE_TIMESTAMPS, true))
    }
    var hideOnPause by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_HIDE_ON_PAUSE, false))
    }
    var isShowPlaybackState by remember(targetInstanceId) {
        mutableStateOf(readBool(MEDIA_RPC_SHOW_PLAYBACK_STATE, false))
    }
    var showSongAsTitle by remember(targetInstanceId) {
        mutableStateOf(readBool(Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true))
    }
    var showAppNameInDetails by remember(targetInstanceId) {
        mutableStateOf(readBool(Prefs.MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS, true))
    }
    var isAutoActivityTypeEnabled by remember(targetInstanceId) {
        mutableStateOf(readBool(Prefs.AUTO_ACTIVITY_TYPE, true))
    }
    var activityType by remember(targetInstanceId) {
        mutableStateOf(readInt(Prefs.MEDIA_RPC_ACTIVITY_TYPE, Prefs.ACTIVITY_TYPE_AUTO))
    }

    fun notifyInstanceConfigChanged() {
        if (mediaRpcRunning) {
            try {
                val intent = Intent(context, MediaRpcService::class.java).apply {
                    action = Constants.ACTION_RESTART_SERVICE
                    instanceId?.let { putExtra(Constants.EXTRA_INSTANCE_ID, it) }
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    MediaRPCContent(
        onBackPressed = onBackPressed,
        state = state,
        hasNotificationAccess = hasNotificationAccess,
        updateMediaAppEnabled = updateMediaAppEnabled,
        selectAllMediaApps = selectAllMediaApps,
        deselectAllMediaApps = deselectAllMediaApps,
        mediaRpcRunning = mediaRpcRunning,
        onMediaRpcRunningChanged = {
            val newValue = !mediaRpcRunning
            mediaRpcRunning = newValue
            scope.launch {
                if (newValue) {
                    rpcManager.switchToInstance(
                        instanceId = targetInstanceId,
                        rpcType = RpcType.MediaRpc,
                        oauthIndex = 1,
                    )
                } else {
                    rpcManager.stopInstance(targetInstanceId, RpcType.MediaRpc)
                }
            }
        },
        isArtistEnabled = isArtistEnabled,
        onArtistEnabledChanged = {
            isArtistEnabled = !isArtistEnabled
            writeBool(MEDIA_RPC_ARTIST_NAME, isArtistEnabled)
            notifyInstanceConfigChanged()
        },
        isAlbumEnabled = isAlbumEnabled,
        onAlbumEnabledChanged = {
            isAlbumEnabled = !isAlbumEnabled
            writeBool(MEDIA_RPC_ALBUM_NAME, isAlbumEnabled)
            notifyInstanceConfigChanged()
        },
        isCoverArtEnabled = isCoverArtEnabled,
        onCoverArtEnabledChanged = {
            isCoverArtEnabled = !isCoverArtEnabled
            writeBool(MEDIA_RPC_SHOW_COVER_ART, isCoverArtEnabled)
            notifyInstanceConfigChanged()
        },
        isAppIconEnabled = isAppIconEnabled,
        onAppIconEnabledChanged = {
            isAppIconEnabled = !isAppIconEnabled
            writeBool(MEDIA_RPC_APP_ICON, isAppIconEnabled)
            if (isAppIconEnabled) {
                isShowPlaybackState = false
                writeBool(MEDIA_RPC_SHOW_PLAYBACK_STATE, false)
                isChannelIconEnabled = false
                writeBool(Prefs.MEDIA_RPC_SHOW_CHANNEL_ICON, false)
            }
            notifyInstanceConfigChanged()
        },
        isChannelIconEnabled = isChannelIconEnabled,
        onChannelIconEnabledChanged = {
            isChannelIconEnabled = !isChannelIconEnabled
            writeBool(Prefs.MEDIA_RPC_SHOW_CHANNEL_ICON, isChannelIconEnabled)
            if (isChannelIconEnabled) {
                isAppIconEnabled = false
                writeBool(MEDIA_RPC_APP_ICON, false)
                isShowPlaybackState = false
                writeBool(MEDIA_RPC_SHOW_PLAYBACK_STATE, false)
            }
            notifyInstanceConfigChanged()
        },
        useAppsRpc = useAppsRpc,
        onUseAppsRpcChanged = {
            useAppsRpc = !useAppsRpc
            writeBool(MEDIA_RPC_USE_APPS_RPC, useAppsRpc)
            notifyInstanceConfigChanged()
        },
        isTimestampsEnabled = isTimestampsEnabled,
        onTimestampsEnabledChanged = {
            isTimestampsEnabled = !isTimestampsEnabled
            writeBool(MEDIA_RPC_ENABLE_TIMESTAMPS, isTimestampsEnabled)
            notifyInstanceConfigChanged()
        },
        isShowPlaybackState = isShowPlaybackState,
        onShowPlaybackStateChanged = {
            isShowPlaybackState = !isShowPlaybackState
            writeBool(MEDIA_RPC_SHOW_PLAYBACK_STATE, isShowPlaybackState)
            if (isShowPlaybackState) {
                isAppIconEnabled = false
                writeBool(MEDIA_RPC_APP_ICON, false)
                isChannelIconEnabled = false
                writeBool(Prefs.MEDIA_RPC_SHOW_CHANNEL_ICON, false)
            }
            notifyInstanceConfigChanged()
        },
        hideOnPause = hideOnPause,
        onHideOnPauseChanged = {
            hideOnPause = !hideOnPause
            writeBool(MEDIA_RPC_HIDE_ON_PAUSE, hideOnPause)
            notifyInstanceConfigChanged()
        },
        showSongAsTitle = showSongAsTitle,
        onShowSongAsTitleChanged = {
            showSongAsTitle = !showSongAsTitle
            writeBool(Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, showSongAsTitle)
            notifyInstanceConfigChanged()
        },
        showAppNameInDetails = showAppNameInDetails,
        onShowAppNameInDetailsChanged = {
            showAppNameInDetails = !showAppNameInDetails
            writeBool(Prefs.MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS, showAppNameInDetails)
            notifyInstanceConfigChanged()
        },
        isAutoActivityTypeEnabled = isAutoActivityTypeEnabled,
        onAutoActivityTypeEnabledChanged = {
            isAutoActivityTypeEnabled = !isAutoActivityTypeEnabled
            writeBool(Prefs.AUTO_ACTIVITY_TYPE, isAutoActivityTypeEnabled)
            notifyInstanceConfigChanged()
        },
        activityType = activityType,
        onActivityTypeChanged = {
            activityType = it
            writeInt(Prefs.MEDIA_RPC_ACTIVITY_TYPE, it)
            notifyInstanceConfigChanged()
        },
        onAppActivityTypeChanged = updateAppActivityType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaRPCContent(
    onBackPressed: () -> Unit,
    state: MediaAppsState,
    hasNotificationAccess: Boolean,
    updateMediaAppEnabled: (String) -> Unit,
    selectAllMediaApps: () -> Unit,
    deselectAllMediaApps: () -> Unit,
    mediaRpcRunning: Boolean,
    onMediaRpcRunningChanged: () -> Unit,
    isArtistEnabled: Boolean,
    onArtistEnabledChanged: () -> Unit,
    isAlbumEnabled: Boolean,
    onAlbumEnabledChanged: () -> Unit,
    isCoverArtEnabled: Boolean,
    onCoverArtEnabledChanged: () -> Unit,
    isAppIconEnabled: Boolean,
    onAppIconEnabledChanged: () -> Unit,
    isChannelIconEnabled: Boolean,
    onChannelIconEnabledChanged: () -> Unit,
    isTimestampsEnabled: Boolean,
    onTimestampsEnabledChanged: () -> Unit,
    isShowPlaybackState: Boolean,
    onShowPlaybackStateChanged: () -> Unit,
    hideOnPause: Boolean,
    onHideOnPauseChanged: () -> Unit,
    showSongAsTitle: Boolean,
    onShowSongAsTitleChanged: () -> Unit,
    showAppNameInDetails: Boolean,
    onShowAppNameInDetailsChanged: () -> Unit,
    useAppsRpc: Boolean,
    onUseAppsRpcChanged: () -> Unit,
    isAutoActivityTypeEnabled: Boolean,
    onAutoActivityTypeEnabledChanged: () -> Unit,
    activityType: Int,
    onActivityTypeChanged: (Int) -> Unit,
    onAppActivityTypeChanged: (String, Int?) -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )
    var searchText by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }

    val rpcManager = RpcManager.getInstance(context)
    var copyConfig by remember { mutableStateOf<RpcConfig?>(null) }
    LaunchedEffect(rpcManager.currentPresenceConfig) {
        copyConfig = rpcManager.currentPresenceConfig.value
    }

    val filteredApps = remember(state.apps, searchText) {
        if (searchText.isEmpty()) {
            state.apps
        } else {
            state.apps.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                        it.pkg.contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.main_mediaRpc),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    navigationIcon = { BackButton { onBackPressed() } },
                    actions = {
                        IconButton(onClick = { isSearchBarVisible = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(id = R.string.search)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(visible = isSearchBarVisible) {
                    SearchBar(
                        text = searchText,
                        onTextChanged = { searchText = it },
                        onClose = {
                            isSearchBarVisible = false
                            searchText = ""
                        },
                        placeholder = stringResource(id = R.string.search_placeholder),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            AnimatedVisibility(visible = !hasNotificationAccess) {
                PreferencesHint(
                    title = stringResource(id = R.string.permission_required),
                    description = stringResource(id = R.string.request_for_notification_access),
                    icon = Icons.Outlined.Warning,
                ) {
                    if (!hasNotificationAccess) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
            SwitchBar(
                title = stringResource(id = R.string.enable_mediaRpc),
                isChecked = mediaRpcRunning,
                enabled = hasNotificationAccess && !AppUtils.ManualRpcRunning(),
            ) { onMediaRpcRunningChanged() }

            val currentConfig = copyConfig
            if (currentConfig != null) {
                val copyToast = stringResource(id = R.string.copied_to_custom_rpc)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TextButton(
                        onClick = {
                            rpcManager.copyToCustomRpc(currentConfig)
                            Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.copy_to_custom_rpc),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            LazyColumn {
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_artist_name),
                        icon = Icons.Outlined.Audiotrack,
                        isChecked = isArtistEnabled,
                    ) { onArtistEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_album_name),
                        icon = Icons.Outlined.Album,
                        isChecked = isAlbumEnabled
                    ) { onAlbumEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_cover_art),
                        description = stringResource(id = R.string.show_cover_art_desc),
                        icon = Icons.Outlined.Image,
                        isChecked = isCoverArtEnabled,
                    ) { onCoverArtEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_app_icon),
                        icon = Icons.Outlined.Apps,
                        isChecked = isAppIconEnabled,
                    ) { onAppIconEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_channel_icon),
                        description = stringResource(id = R.string.show_channel_icon_desc),
                        icon = Icons.Outlined.AccountCircle,
                        isChecked = isChannelIconEnabled,
                    ) { onChannelIconEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.media_rpc_use_apps_rpc),
                        description = stringResource(id = R.string.media_rpc_use_apps_rpc_desc),
                        icon = Icons.Outlined.Gamepad,
                        isChecked = useAppsRpc,
                    ) { onUseAppsRpcChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.enable_timestamps),
                        icon = Icons.Outlined.Timer,
                        isChecked = isTimestampsEnabled,
                    ) { onTimestampsEnabledChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_playback_state),
                        icon = Icons.Outlined.PlayCircle,
                        isChecked = isShowPlaybackState,
                    ) { onShowPlaybackStateChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.hide_on_pause),
                        icon = Icons.Outlined.PauseCircle,
                        isChecked = hideOnPause,
                    ) { onHideOnPauseChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_song_as_title),
                        icon = Icons.Outlined.PlaylistAddCircle,
                        isChecked = showSongAsTitle,
                    ) { onShowSongAsTitleChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.show_app_name_in_details),
                        description = stringResource(id = R.string.show_app_name_in_details_desc),
                        isChecked = showAppNameInDetails,
                    ) { onShowAppNameInDetailsChanged() }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(id = R.string.auto_detect_activity_type),
                        icon = Icons.Outlined.AutoAwesome,
                        isChecked = isAutoActivityTypeEnabled,
                    ) { onAutoActivityTypeEnabledChanged() }
                }
                item {
                    ActivityTypePreference(
                        title = stringResource(id = R.string.activity_type),
                        description = stringResource(id = R.string.activity_type_fallback_desc),
                        selectedTypeId = activityType,
                        onSelect = { onActivityTypeChanged(it) },
                    )
                }
                item {
                    Subtitle(
                        text = "Apps",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp, 8.dp)
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp, 8.dp)
                    ) {
                        TextButton(
                            onClick = { selectAllMediaApps() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(id = R.string.turn_on_all_apps))
                        }
                        TextButton(
                            onClick = { deselectAllMediaApps() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(id = R.string.turn_off_all_apps))
                        }
                    }
                }
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(
                        count = filteredApps.size,
                        key = { index -> filteredApps[index].pkg }
                    ) { index ->
                        val app = filteredApps[index]
                        AppsItem(
                            name = app.name,
                            pkg = app.pkg,
                            isChecked = state.enabledApps[app.pkg] ?: false,
                            onClick = { updateMediaAppEnabled(app.pkg) }
                        ) {
                            ActivityTypeChip(
                                selectedTypeId = state.activityTypes[app.pkg],
                                detectedTypeId = ActivityTypeResolver.knownType(app.pkg)
                                    ?: activityType,
                                onSelect = { onAppActivityTypeChanged(app.pkg, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "MediaRPC Light")
@Composable
fun MediaRPCLightPreview() {
    KizzyTheme(
        darkTheme = false,
        isHighContrastModeEnabled = false,
        isDynamicColorEnabled = false,
    ) {
        MediaRPCContent(
            onBackPressed = {},
            state = MediaAppsState(
                apps = listOf(
                    AppsInfo(name = "Spotify", pkg = "com.spotify.music", isChecked = true),
                    AppsInfo(name = "YouTube Music", pkg = "com.google.android.apps.youtube.music", isChecked = false),
                    AppsInfo(name = "Apple Music", pkg = "com.apple.android.music", isChecked = true),
                ),
                enabledApps = mapOf(
                    "com.spotify.music" to true,
                    "com.google.android.apps.youtube.music" to false,
                    "com.apple.android.music" to true,
                ),
                isLoading = false,
            ),
            hasNotificationAccess = true,
            updateMediaAppEnabled = {},
            selectAllMediaApps = {},
            deselectAllMediaApps = {},
            mediaRpcRunning = true,
            onMediaRpcRunningChanged = {},
            isArtistEnabled = true,
            onArtistEnabledChanged = {},
            isAlbumEnabled = true,
            onAlbumEnabledChanged = {},
            isCoverArtEnabled = true,
            onCoverArtEnabledChanged = {},
            isAppIconEnabled = false,
            onAppIconEnabledChanged = {},
            isChannelIconEnabled = false,
            onChannelIconEnabledChanged = {},
            isTimestampsEnabled = true,
            onTimestampsEnabledChanged = {},
            isShowPlaybackState = false,
            onShowPlaybackStateChanged = {},
            hideOnPause = false,
            onHideOnPauseChanged = {},
            showSongAsTitle = false,
            onShowSongAsTitleChanged = {},
            showAppNameInDetails = true,
            onShowAppNameInDetailsChanged = {},
            useAppsRpc = false,
            onUseAppsRpcChanged = {},
            isAutoActivityTypeEnabled = true,
            onAutoActivityTypeEnabledChanged = {},
            activityType = 3,
            onActivityTypeChanged = {},
            onAppActivityTypeChanged = { _, _ -> },
        )
    }
}

@Preview(name = "MediaRPC Dark")
@Composable
fun MediaRPCDarkPreview() {
    KizzyTheme(
        darkTheme = true,
        isHighContrastModeEnabled = false,
        isDynamicColorEnabled = false,
    ) {
        MediaRPCContent(
            onBackPressed = {},
            state = MediaAppsState(
                apps = listOf(
                    AppsInfo(name = "Spotify", pkg = "com.spotify.music", isChecked = true),
                    AppsInfo(name = "YouTube Music", pkg = "com.google.android.apps.youtube.music", isChecked = false),
                    AppsInfo(name = "Apple Music", pkg = "com.apple.android.music", isChecked = true),
                ),
                enabledApps = mapOf(
                    "com.spotify.music" to true,
                    "com.google.android.apps.youtube.music" to false,
                    "com.apple.android.music" to true,
                ),
                isLoading = false,
            ),
            hasNotificationAccess = true,
            updateMediaAppEnabled = {},
            selectAllMediaApps = {},
            deselectAllMediaApps = {},
            mediaRpcRunning = true,
            onMediaRpcRunningChanged = {},
            isArtistEnabled = true,
            onArtistEnabledChanged = {},
            isAlbumEnabled = true,
            onAlbumEnabledChanged = {},
            isCoverArtEnabled = true,
            onCoverArtEnabledChanged = {},
            isAppIconEnabled = false,
            onAppIconEnabledChanged = {},
            isChannelIconEnabled = false,
            onChannelIconEnabledChanged = {},
            isTimestampsEnabled = true,
            onTimestampsEnabledChanged = {},
            isShowPlaybackState = false,
            onShowPlaybackStateChanged = {},
            hideOnPause = false,
            onHideOnPauseChanged = {},
            showSongAsTitle = false,
            onShowSongAsTitleChanged = {},
            showAppNameInDetails = true,
            onShowAppNameInDetailsChanged = {},
            useAppsRpc = false,
            onUseAppsRpcChanged = {},
            isAutoActivityTypeEnabled = true,
            onAutoActivityTypeEnabledChanged = {},
            activityType = 3,
            onActivityTypeChanged = {},
            onAppActivityTypeChanged = { _, _ -> },
        )
    }
}