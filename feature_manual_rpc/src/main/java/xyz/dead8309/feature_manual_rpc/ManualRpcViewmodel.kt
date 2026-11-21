/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ManualRpcViewmodel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package xyz.dead8309.feature_manual_rpc

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.utils.getInstalledApps
import com.my.kizzy.domain.use_case.upload_galleryImage.UploadGalleryImageUseCase
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.services.ManualRpc
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class ManualRpcViewmodel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        UiState(
            isAppsLoading = true,
            isAppsRpcPartEnabled = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true],
            isMediaRpcPartEnabled = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true],
            templateName = Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME, ""],
            templateDetails = Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS, ""],
            templateState = Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE, ""],
            showCoverArt = Prefs[Prefs.MANUAL_RPC_SHOW_COVER_ART, false],
            showAppIcon = Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON, false],
            showArtistName = Prefs[Prefs.MANUAL_RPC_SHOW_ARTIST_NAME, true],
            showAlbumName = Prefs[Prefs.MANUAL_RPC_SHOW_ALBUM_NAME, true],
            showPlaybackState = Prefs[Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE, false],
            enableTimestamps = Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, false],
            hideOnPause = Prefs[Prefs.MANUAL_RPC_HIDE_ON_PAUSE, false],
            platform = Prefs[Prefs.MANUAL_RPC_PLATFORM, ""],
            buttonsEnabled = Prefs[Prefs.MANUAL_RPC_USE_BUTTONS, false],
            button1Text = Prefs[Prefs.MANUAL_RPC_BUTTON1_TEXT, ""],
            button1Url = Prefs[Prefs.MANUAL_RPC_BUTTON1_URL, ""],
            button2Text = Prefs[Prefs.MANUAL_RPC_BUTTON2_TEXT, ""],
            button2Url = Prefs[Prefs.MANUAL_RPC_BUTTON2_URL, ""],
            useCustomAppId = Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false],
            applicationId = Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""],
            timestampMode = Prefs[Prefs.MANUAL_RPC_TIMESTAMP_MODE, "default"],
            customTimestampStart = Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_START, ""],
            customTimestampEnd = Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_END, ""],
            showSongAsTitle = Prefs[Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE, false],
            showAppNameInDetails = Prefs[Prefs.MANUAL_RPC_SHOW_APP_NAME_IN_DETAILS, false],
            showChannelIcon = Prefs[Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON, false],
            useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, false],
            titleSource = Prefs[Prefs.MANUAL_RPC_TITLE_SOURCE, "title"],

            // Advanced settings
            activityType = Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE, ""],
            status = Prefs[Prefs.MANUAL_RPC_STATUS, "online"],
            largeImage = Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE, ""],
            smallImage = Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE, ""],
            largeText = Prefs[Prefs.MANUAL_RPC_LARGE_TEXT, ""],
            smallText = Prefs[Prefs.MANUAL_RPC_SMALL_TEXT, ""],
            streamUrl = Prefs[Prefs.MANUAL_RPC_STREAM_URL, ""],
            partyCurrentSize = Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE, ""],
            partyMaxSize = Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE, ""],
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.Default) {
            val appList = getInstalledApps(
                context = context,
                isEnabled = Prefs::isExperimentalAppEnabled
            )
            val enabledApps = appList.associate { it.pkg to it.isChecked }
            // Only the types the user actually picked are kept. Everything else is
            // detected automatically.
            val savedActivityTypes = Prefs.getAppActivityTypes()
            val appActivityTypes = appList
                .mapNotNull { app -> savedActivityTypes[app.pkg]?.let { app.pkg to it } }
                .toMap()
            _uiState.update {
                it.copy(
                    installedApps = appList,
                    enabledApps = enabledApps,
                    appActivityTypes = appActivityTypes,
                    isAppsLoading = false,
                    isAutoActivityTypeEnabled = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true]
                )
            }
        }
    }

    /**
     * Restarts the Manual RPC service (if running) so settings that are only
     * read on service start — the spoofed platform and RPC buttons — apply live
     * without the user having to toggle the service off and on manually.
     */
    private fun restartServiceIfRunning() {
        if (!AppUtils.ManualRpcRunning()) return
        val intent = Intent(context, ManualRpc::class.java).apply {
            action = Constants.ACTION_RESTART_SERVICE
        }
        context.startService(intent)
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.ToggleAppsRpcPart -> {
                    Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC] = event.enabled
                    _uiState.value = _uiState.value.copy(isAppsRpcPartEnabled = event.enabled)
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleAutoActivityType -> {
                    // The switch is shared with the Media RPC, so both features
                    // detect the activity type in the same way.
                    Prefs[Prefs.AUTO_ACTIVITY_TYPE] = event.enabled
                    _uiState.update { it.copy(isAutoActivityTypeEnabled = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleMediaRpcPart -> {
                    Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC] = event.enabled
                    _uiState.value = _uiState.value.copy(isMediaRpcPartEnabled = event.enabled)
                    restartServiceIfRunning()
                }

                is UiEvent.SetTemplateName -> {
                    Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME] = event.value
                    _uiState.update { it.copy(templateName = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetTemplateDetails -> {
                    Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS] = event.value
                    _uiState.update { it.copy(templateDetails = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetTemplateState -> {
                    Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE] = event.value
                    _uiState.update { it.copy(templateState = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleAppEnabled -> {
                    val current = _uiState.value.enabledApps[event.packageName] ?: false
                    Prefs.saveExperimentalAppToPrefs(event.packageName)
                    _uiState.update { state ->
                        state.copy(
                            enabledApps = state.enabledApps.toMutableMap().apply {
                                this[event.packageName] = !current
                            }
                        )
                    }
                    restartServiceIfRunning()
                }

                is UiEvent.SelectAllApps -> {
                    val selectedApps = event.packageNames
                    val currentEnabled = _uiState.value.enabledApps.toMutableMap()
                    selectedApps.forEach { pkg ->
                        currentEnabled[pkg] = true
                    }
                    // Update prefs with all currently enabled apps
                    val enabledList = currentEnabled.filterValues { it }.keys.toList()
                    Prefs.setExperimentalAppsEnabled(enabledList)
                    _uiState.update { state ->
                        state.copy(enabledApps = currentEnabled)
                    }
                    restartServiceIfRunning()
                }

                is UiEvent.DeselectAllApps -> {
                    val deselectedApps = event.packageNames
                    val currentEnabled = _uiState.value.enabledApps.toMutableMap()
                    deselectedApps.forEach { pkg ->
                        currentEnabled[pkg] = false
                    }
                    // Only keep enabled apps in prefs
                    val enabledList = currentEnabled.filterValues { it }.keys.toList()
                    Prefs.setExperimentalAppsEnabled(enabledList)
                    _uiState.update { state ->
                        state.copy(enabledApps = currentEnabled)
                    }
                    restartServiceIfRunning()
                }

                is UiEvent.SetAppActivityType -> {
                    val typeInt = event.activityType
                    if (typeInt == null) {
                        Prefs.clearAppActivityType(event.packageName)
                    } else {
                        Prefs.saveAppActivityType(event.packageName, typeInt)
                    }
                    ActivityTypeResolver.invalidateCache()
                    _uiState.update { state ->
                        state.copy(
                            appActivityTypes = state.appActivityTypes.toMutableMap().apply {
                                if (typeInt == null) {
                                    remove(event.packageName)
                                } else {
                                    this[event.packageName] = typeInt
                                }
                            }
                        )
                    }
                    restartServiceIfRunning()
                }
                is UiEvent.ToggleShowCoverArt -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_COVER_ART] = event.enabled
                    _uiState.update { it.copy(showCoverArt = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowAppIcon -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON] = event.enabled
                    _uiState.update {
                        it.copy(showAppIcon = event.enabled)
                    }
                    if (event.enabled) {
                        Prefs[Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE] = false
                        _uiState.update { it.copy(showPlaybackState = false) }
                    }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowArtistName -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_ARTIST_NAME] = event.enabled
                    _uiState.update { it.copy(showArtistName = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowAlbumName -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_ALBUM_NAME] = event.enabled
                    _uiState.update { it.copy(showAlbumName = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowPlaybackState -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE] = event.enabled
                    _uiState.update {
                        it.copy(showPlaybackState = event.enabled)
                    }
                    if (event.enabled) {
                        Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON] = false
                        _uiState.update { it.copy(showAppIcon = false) }
                    }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleEnableTimestamps -> {
                    Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS] = event.enabled
                    _uiState.update { it.copy(enableTimestamps = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleHideOnPause -> {
                    Prefs[Prefs.MANUAL_RPC_HIDE_ON_PAUSE] = event.enabled
                    _uiState.update { it.copy(hideOnPause = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetPlatform -> {
                    Prefs[Prefs.MANUAL_RPC_PLATFORM] = event.value
                    _uiState.update { it.copy(platform = event.value, platformIsExpanded = false) }
                    // Platform is applied via the gateway IDENTIFY on service start,
                    // so restart the running service to apply the new platform live.
                    restartServiceIfRunning()
                }

                is UiEvent.TriggerPlatformDropDownMenu -> {
                    _uiState.update { it.copy(platformIsExpanded = !it.platformIsExpanded) }
                }

                is UiEvent.ToggleButtons -> {
                    Prefs[Prefs.MANUAL_RPC_USE_BUTTONS] = event.enabled
                    _uiState.update { it.copy(buttonsEnabled = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetButton1Text -> {
                    Prefs[Prefs.MANUAL_RPC_BUTTON1_TEXT] = event.value
                    _uiState.update { it.copy(button1Text = event.value) }
                }

                is UiEvent.SetButton1Url -> {
                    Prefs[Prefs.MANUAL_RPC_BUTTON1_URL] = event.value
                    _uiState.update { it.copy(button1Url = event.value) }
                }

                is UiEvent.SetButton2Text -> {
                    Prefs[Prefs.MANUAL_RPC_BUTTON2_TEXT] = event.value
                    _uiState.update { it.copy(button2Text = event.value) }
                }

                is UiEvent.SetButton2Url -> {
                    Prefs[Prefs.MANUAL_RPC_BUTTON2_URL] = event.value
                    _uiState.update { it.copy(button2Url = event.value) }
                }

                is UiEvent.ToggleUseCustomAppId -> {
                    Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID] = event.enabled
                    _uiState.update { it.copy(useCustomAppId = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetApplicationId -> {
                    Prefs[Prefs.MANUAL_RPC_APPLICATION_ID] = event.value
                    _uiState.update { it.copy(applicationId = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetTimestampMode -> {
                    Prefs[Prefs.MANUAL_RPC_TIMESTAMP_MODE] = event.value
                    _uiState.update { it.copy(timestampMode = event.value, timestampModeExpanded = false) }
                    restartServiceIfRunning()
                }

                is UiEvent.TriggerTimestampModeDropDownMenu -> {
                    _uiState.update { it.copy(timestampModeExpanded = !it.timestampModeExpanded) }
                }

                is UiEvent.SetCustomTimestampStart -> {
                    Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_START] = event.value
                    _uiState.update { it.copy(customTimestampStart = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetCustomTimestampEnd -> {
                    Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_END] = event.value
                    _uiState.update { it.copy(customTimestampEnd = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowSongAsTitle -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE] = event.enabled
                    _uiState.update { it.copy(showSongAsTitle = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowAppNameInDetails -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_APP_NAME_IN_DETAILS] = event.enabled
                    _uiState.update { it.copy(showAppNameInDetails = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleShowChannelIcon -> {
                    Prefs[Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON] = event.enabled
                    _uiState.update { it.copy(showChannelIcon = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.ToggleUseAppsRpc -> {
                    Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC] = event.enabled
                    _uiState.update { it.copy(useAppsRpc = event.enabled) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetTitleSource -> {
                    Prefs[Prefs.MANUAL_RPC_TITLE_SOURCE] = event.value
                    _uiState.update { it.copy(titleSource = event.value, titleSourceExpanded = false) }
                    restartServiceIfRunning()
                }

                is UiEvent.TriggerTitleSourceDropDown -> {
                    _uiState.update { it.copy(titleSourceExpanded = !it.titleSourceExpanded) }
                }

                // Advanced settings handlers
                is UiEvent.SetActivityType -> {
                    Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE] = event.value
                    _uiState.update { it.copy(activityType = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.TriggerActivityTypeDropDownMenu -> {
                    _uiState.update { it.copy(activityTypeIsExpanded = !it.activityTypeIsExpanded) }
                }

                is UiEvent.SetStatus -> {
                    Prefs[Prefs.MANUAL_RPC_STATUS] = event.value
                    _uiState.update { it.copy(status = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.TriggerStatusDropDownMenu -> {
                    _uiState.update { it.copy(statusIsExpanded = !it.statusIsExpanded) }
                }

                is UiEvent.SetLargeImage -> {
                    Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE] = event.value
                    _uiState.update { it.copy(largeImage = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetSmallImage -> {
                    Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE] = event.value
                    _uiState.update { it.copy(smallImage = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.UploadImage -> {
                    viewModelScope.launch {
                        val result = uploadGalleryImageUseCase(event.file)
                        result?.let { uploadedUrl ->
                            _uiState.update { it.copy(largeImage = uploadedUrl) }
                            event.callback(uploadedUrl)
                        }
                    }
                }

                is UiEvent.SetLargeText -> {
                    Prefs[Prefs.MANUAL_RPC_LARGE_TEXT] = event.value
                    _uiState.update { it.copy(largeText = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetSmallText -> {
                    Prefs[Prefs.MANUAL_RPC_SMALL_TEXT] = event.value
                    _uiState.update { it.copy(smallText = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetStreamUrl -> {
                    Prefs[Prefs.MANUAL_RPC_STREAM_URL] = event.value
                    _uiState.update { it.copy(streamUrl = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetPartyCurrentSize -> {
                    Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE] = event.value
                    _uiState.update { it.copy(partyCurrentSize = event.value) }
                    restartServiceIfRunning()
                }

                is UiEvent.SetPartyMaxSize -> {
                    Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE] = event.value
                    _uiState.update { it.copy(partyMaxSize = event.value) }
                    restartServiceIfRunning()
                }
            }
        }
    }
}
