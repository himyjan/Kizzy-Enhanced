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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ManualRpcViewmodel.Factory::class)
class ManualRpcViewmodel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
    @Assisted private val instanceId: String?,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(instanceId: String?): ManualRpcViewmodel
    }

    private fun key(prefKey: String) = Prefs.scopedKey(prefKey, instanceId)

    private val _uiState = MutableStateFlow(
        UiState(
            isAppsLoading = true,
            isAppsRpcPartEnabled = Prefs[key(Prefs.MANUAL_RPC_USE_APPS_RPC), true],
            isMediaRpcPartEnabled = Prefs[key(Prefs.MANUAL_RPC_USE_MEDIA_RPC), true],
            templateName = Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_NAME), ""],
            templateDetails = Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_DETAILS), ""],
            templateState = Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_STATE), ""],
            showCoverArt = Prefs[key(Prefs.MANUAL_RPC_SHOW_COVER_ART), false],
            showAppIcon = Prefs[key(Prefs.MANUAL_RPC_SHOW_APP_ICON), false],
            showArtistName = Prefs[key(Prefs.MANUAL_RPC_SHOW_ARTIST_NAME), true],
            showAlbumName = Prefs[key(Prefs.MANUAL_RPC_SHOW_ALBUM_NAME), true],
            showPlaybackState = Prefs[key(Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE), false],
            enableTimestamps = Prefs[key(Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS), false],
            hideOnPause = Prefs[key(Prefs.MANUAL_RPC_HIDE_ON_PAUSE), false],
            platform = Prefs[key(Prefs.MANUAL_RPC_PLATFORM), ""],
            buttonsEnabled = Prefs[key(Prefs.MANUAL_RPC_USE_BUTTONS), false],
            button1Text = Prefs[key(Prefs.MANUAL_RPC_BUTTON1_TEXT), ""],
            button1Url = Prefs[key(Prefs.MANUAL_RPC_BUTTON1_URL), ""],
            button2Text = Prefs[key(Prefs.MANUAL_RPC_BUTTON2_TEXT), ""],
            button2Url = Prefs[key(Prefs.MANUAL_RPC_BUTTON2_URL), ""],
            useCustomAppId = Prefs[key(Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID), false],
            applicationId = Prefs[key(Prefs.MANUAL_RPC_APPLICATION_ID), ""],
            timestampMode = Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_MODE), "default"],
            customTimestampStart = Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_START), ""],
            customTimestampEnd = Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_END), ""],
            showSongAsTitle = Prefs[key(Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE), false],
            showAppNameInDetails = Prefs[key(Prefs.MANUAL_RPC_SHOW_APP_NAME_IN_DETAILS), false],
            showChannelIcon = Prefs[key(Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON), false],
            titleSource = Prefs[key(Prefs.MANUAL_RPC_TITLE_SOURCE), "title"],

            // Advanced settings
            activityType = Prefs[key(Prefs.MANUAL_RPC_ACTIVITY_TYPE), ""],
            status = Prefs[key(Prefs.MANUAL_RPC_STATUS), "online"],
            largeImage = Prefs[key(Prefs.MANUAL_RPC_LARGE_IMAGE), ""],
            smallImage = Prefs[key(Prefs.MANUAL_RPC_SMALL_IMAGE), ""],
            largeText = Prefs[key(Prefs.MANUAL_RPC_LARGE_TEXT), ""],
            smallText = Prefs[key(Prefs.MANUAL_RPC_SMALL_TEXT), ""],
            streamUrl = Prefs[key(Prefs.MANUAL_RPC_STREAM_URL), ""],
            partyCurrentSize = Prefs[key(Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE), ""],
            partyMaxSize = Prefs[key(Prefs.MANUAL_RPC_PARTY_MAX_SIZE), ""],
            detailsUrl = Prefs[key(Prefs.MANUAL_RPC_DETAILS_URL), ""],
            stateUrl = Prefs[key(Prefs.MANUAL_RPC_STATE_URL), ""],
            largeUrl = Prefs[key(Prefs.MANUAL_RPC_LARGE_URL), ""],
            smallUrl = Prefs[key(Prefs.MANUAL_RPC_SMALL_URL), ""],
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
                    isAutoActivityTypeEnabled = Prefs[key(Prefs.AUTO_ACTIVITY_TYPE), true]
                )
            }
        }
    }

    private fun restartServiceIfRunning() {
        if (!AppUtils.ManualRpcRunning()) return
        val intent = Intent(context, ManualRpc::class.java).apply {
            action = Constants.ACTION_RESTART_SERVICE
        }
        context.startService(intent)
    }

    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ToggleAppsRpcPart -> {
                Prefs[key(Prefs.MANUAL_RPC_USE_APPS_RPC)] = event.enabled
                _uiState.update { it.copy(isAppsRpcPartEnabled = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleAutoActivityType -> {
                Prefs[key(Prefs.AUTO_ACTIVITY_TYPE)] = event.enabled
                _uiState.update { it.copy(isAutoActivityTypeEnabled = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleMediaRpcPart -> {
                Prefs[key(Prefs.MANUAL_RPC_USE_MEDIA_RPC)] = event.enabled
                _uiState.update { it.copy(isMediaRpcPartEnabled = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.SetTemplateName -> {
                Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_NAME)] = event.value
                _uiState.update { it.copy(templateName = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetTemplateDetails -> {
                Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_DETAILS)] = event.value
                _uiState.update { it.copy(templateDetails = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetTemplateState -> {
                Prefs[key(Prefs.MANUAL_RPC_TEMPLATE_STATE)] = event.value
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
                val currentEnabled = _uiState.value.enabledApps.toMutableMap()
                event.packageNames.forEach { pkg -> currentEnabled[pkg] = true }
                Prefs.setExperimentalAppsEnabled(currentEnabled.filterValues { it }.keys.toList())
                _uiState.update { it.copy(enabledApps = currentEnabled) }
                restartServiceIfRunning()
            }

            is UiEvent.DeselectAllApps -> {
                val currentEnabled = _uiState.value.enabledApps.toMutableMap()
                event.packageNames.forEach { pkg -> currentEnabled[pkg] = false }
                Prefs.setExperimentalAppsEnabled(currentEnabled.filterValues { it }.keys.toList())
                _uiState.update { it.copy(enabledApps = currentEnabled) }
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
                            if (typeInt == null) remove(event.packageName) else this[event.packageName] = typeInt
                        }
                    )
                }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowCoverArt -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_COVER_ART)] = event.enabled
                _uiState.update { it.copy(showCoverArt = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowAppIcon -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_APP_ICON)] = event.enabled
                if (event.enabled) {
                    Prefs[key(Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE)] = false
                }
                _uiState.update {
                    it.copy(
                        showAppIcon = event.enabled,
                        showPlaybackState = if (event.enabled) false else it.showPlaybackState,
                    )
                }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowArtistName -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_ARTIST_NAME)] = event.enabled
                _uiState.update { it.copy(showArtistName = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowAlbumName -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_ALBUM_NAME)] = event.enabled
                _uiState.update { it.copy(showAlbumName = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowPlaybackState -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE)] = event.enabled
                if (event.enabled) {
                    Prefs[key(Prefs.MANUAL_RPC_SHOW_APP_ICON)] = false
                }
                _uiState.update {
                    it.copy(
                        showPlaybackState = event.enabled,
                        showAppIcon = if (event.enabled) false else it.showAppIcon,
                    )
                }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleEnableTimestamps -> {
                Prefs[key(Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS)] = event.enabled
                _uiState.update { it.copy(enableTimestamps = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleHideOnPause -> {
                Prefs[key(Prefs.MANUAL_RPC_HIDE_ON_PAUSE)] = event.enabled
                _uiState.update { it.copy(hideOnPause = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.SetPlatform -> {
                Prefs[key(Prefs.MANUAL_RPC_PLATFORM)] = event.value
                _uiState.update { it.copy(platform = event.value, platformIsExpanded = false) }
                restartServiceIfRunning()
            }

            UiEvent.TriggerPlatformDropDownMenu -> {
                _uiState.update { it.copy(platformIsExpanded = !it.platformIsExpanded) }
            }

            is UiEvent.ToggleButtons -> {
                Prefs[key(Prefs.MANUAL_RPC_USE_BUTTONS)] = event.enabled
                _uiState.update { it.copy(buttonsEnabled = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.SetButton1Text -> {
                Prefs[key(Prefs.MANUAL_RPC_BUTTON1_TEXT)] = event.value
                _uiState.update { it.copy(button1Text = event.value) }
            }

            is UiEvent.SetButton1Url -> {
                Prefs[key(Prefs.MANUAL_RPC_BUTTON1_URL)] = event.value
                _uiState.update { it.copy(button1Url = event.value) }
            }

            is UiEvent.SetButton2Text -> {
                Prefs[key(Prefs.MANUAL_RPC_BUTTON2_TEXT)] = event.value
                _uiState.update { it.copy(button2Text = event.value) }
            }

            is UiEvent.SetButton2Url -> {
                Prefs[key(Prefs.MANUAL_RPC_BUTTON2_URL)] = event.value
                _uiState.update { it.copy(button2Url = event.value) }
            }

            is UiEvent.ToggleUseCustomAppId -> {
                Prefs[key(Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID)] = event.enabled
                _uiState.update { it.copy(useCustomAppId = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.SetApplicationId -> {
                Prefs[key(Prefs.MANUAL_RPC_APPLICATION_ID)] = event.value
                _uiState.update { it.copy(applicationId = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetTimestampMode -> {
                Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_MODE)] = event.value
                _uiState.update { it.copy(timestampMode = event.value, timestampModeExpanded = false) }
                restartServiceIfRunning()
            }

            UiEvent.TriggerTimestampModeDropDownMenu -> {
                _uiState.update { it.copy(timestampModeExpanded = !it.timestampModeExpanded) }
            }

            is UiEvent.SetCustomTimestampStart -> {
                Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_START)] = event.value
                _uiState.update { it.copy(customTimestampStart = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetCustomTimestampEnd -> {
                Prefs[key(Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_END)] = event.value
                _uiState.update { it.copy(customTimestampEnd = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowSongAsTitle -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE)] = event.enabled
                _uiState.update { it.copy(showSongAsTitle = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowAppNameInDetails -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_APP_NAME_IN_DETAILS)] = event.enabled
                _uiState.update { it.copy(showAppNameInDetails = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.ToggleShowChannelIcon -> {
                Prefs[key(Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON)] = event.enabled
                _uiState.update { it.copy(showChannelIcon = event.enabled) }
                restartServiceIfRunning()
            }

            is UiEvent.SetTitleSource -> {
                Prefs[key(Prefs.MANUAL_RPC_TITLE_SOURCE)] = event.value
                _uiState.update { it.copy(titleSource = event.value, titleSourceExpanded = false) }
                restartServiceIfRunning()
            }

            UiEvent.TriggerTitleSourceDropDown -> {
                _uiState.update { it.copy(titleSourceExpanded = !it.titleSourceExpanded) }
            }

            is UiEvent.SetActivityType -> {
                Prefs[key(Prefs.MANUAL_RPC_ACTIVITY_TYPE)] = event.value
                _uiState.update { it.copy(activityType = event.value, activityTypeIsExpanded = false) }
                restartServiceIfRunning()
            }

            UiEvent.TriggerActivityTypeDropDownMenu -> {
                _uiState.update { it.copy(activityTypeIsExpanded = !it.activityTypeIsExpanded) }
            }

            is UiEvent.SetStatus -> {
                Prefs[key(Prefs.MANUAL_RPC_STATUS)] = event.value
                _uiState.update { it.copy(status = event.value, statusIsExpanded = false) }
                restartServiceIfRunning()
            }

            UiEvent.TriggerStatusDropDownMenu -> {
                _uiState.update { it.copy(statusIsExpanded = !it.statusIsExpanded) }
            }

            is UiEvent.SetLargeImage -> {
                Prefs[key(Prefs.MANUAL_RPC_LARGE_IMAGE)] = event.value
                _uiState.update { it.copy(largeImage = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetSmallImage -> {
                Prefs[key(Prefs.MANUAL_RPC_SMALL_IMAGE)] = event.value
                _uiState.update { it.copy(smallImage = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.UploadImage -> {
                viewModelScope.launch {
                    val result = uploadGalleryImageUseCase(event.file)
                    event.callback(result)
                }
            }

            is UiEvent.SetLargeText -> {
                Prefs[key(Prefs.MANUAL_RPC_LARGE_TEXT)] = event.value
                _uiState.update { it.copy(largeText = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetSmallText -> {
                Prefs[key(Prefs.MANUAL_RPC_SMALL_TEXT)] = event.value
                _uiState.update { it.copy(smallText = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetStreamUrl -> {
                Prefs[key(Prefs.MANUAL_RPC_STREAM_URL)] = event.value
                _uiState.update { it.copy(streamUrl = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetPartyCurrentSize -> {
                Prefs[key(Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE)] = event.value
                _uiState.update { it.copy(partyCurrentSize = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetPartyMaxSize -> {
                Prefs[key(Prefs.MANUAL_RPC_PARTY_MAX_SIZE)] = event.value
                _uiState.update { it.copy(partyMaxSize = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetDetailsUrl -> {
                Prefs[key(Prefs.MANUAL_RPC_DETAILS_URL)] = event.value
                _uiState.update { it.copy(detailsUrl = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetStateUrl -> {
                Prefs[key(Prefs.MANUAL_RPC_STATE_URL)] = event.value
                _uiState.update { it.copy(stateUrl = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetLargeUrl -> {
                Prefs[key(Prefs.MANUAL_RPC_LARGE_URL)] = event.value
                _uiState.update { it.copy(largeUrl = event.value) }
                restartServiceIfRunning()
            }

            is UiEvent.SetSmallUrl -> {
                Prefs[key(Prefs.MANUAL_RPC_SMALL_URL)] = event.value
                _uiState.update { it.copy(smallUrl = event.value) }
                restartServiceIfRunning()
            }
        }
    }
}