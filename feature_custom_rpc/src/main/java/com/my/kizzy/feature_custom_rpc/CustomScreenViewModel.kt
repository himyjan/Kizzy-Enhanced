/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CustomScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_custom_rpc

import android.annotation.SuppressLint
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.Game
import com.my.kizzy.domain.use_case.get_games.GetGamesUseCase
import com.my.kizzy.domain.use_case.upload_galleryImage.UploadGalleryImageUseCase
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.feature_custom_rpc.components.sheet.stringToData
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import android.content.Context

@SuppressLint("StaticFieldLeak")
@HiltViewModel(assistedFactory = CustomScreenViewModel.Factory::class)
class CustomScreenViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
    private val getGamesUseCase: GetGamesUseCase,
    /** Null for the built-in Custom RPC; a clone passes its own instance id. */
    @Assisted instanceId: String?,
) : ViewModel() {
    private val instanceIdValue: String? = instanceId
    private val configKey: String = Prefs.customRpcConfigKey(instanceId)

    @AssistedFactory
    interface Factory {
        fun create(instanceId: String?): CustomScreenViewModel
    }

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private val uiEventChannel = Channel<UiEvent>(capacity = Channel.UNLIMITED)

    init {
        // Tag the state with this instance so the UI's switch / restart / preset
        // actions only touch THIS instance, never the built-in or other clones.
        _uiState.value = _uiState.value.copy(instanceId = instanceId)

        // Always load the last saved custom RPC config so the screen remembers
        // what the user last modified, even if they haven't started the RPC yet.
        val lastConfig = Prefs[configKey, ""]
        if (lastConfig.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                rpcConfig = lastConfig.stringToData()
            )
        }
        // Load console games so the game list shows in Custom RPC tab
        viewModelScope.launch {
            getGamesUseCase().collect { result ->
                if (result is com.my.kizzy.domain.model.Resource.Success) {
                    val freshGames = result.data ?: emptyList()
                    ConsoleGameCatalog.update(freshGames)
                    _uiState.value = _uiState.value.copy(consoleGames = freshGames)
                }
            }
        }
        viewModelScope.launch {
            uiEventChannel.consumeAsFlow()
                .collect { event ->
                    processEvent(event)
                }
        }
    }

    private suspend fun uploadImage(file: File, result: (String) -> Unit) {
        uploadGalleryImageUseCase(file)?.let {
            withContext(Dispatchers.Main) {
                result(it.drop(3))
            }
        }
    }

    fun onEvent(event: UiEvent) {
        viewModelScope.launch {
            uiEventChannel.send(event)
        }
    }

    private suspend fun processEvent(event: UiEvent) {
        when (event) {
            is UiEvent.SetFieldsFromConfig -> {
                _uiState.value = _uiState.value.copy(rpcConfig = event.config)
                // Persist immediately so the RPC config survives navigation away
                // and re-open of the screen.
                Prefs[configKey] = Json.encodeToString(event.config)
                // Restart running Custom RPC service so field changes apply live
                // without the user having to close and reopen the app.
                viewModelScope.launch {
                    if (AppUtils.customRpcRunning()) {
                        val intent = Intent(context, CustomRpcService::class.java).apply {
                            action = Constants.ACTION_RESTART_SERVICE
                            putExtra("RPC", Json.encodeToString(event.config))
                            putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, false)
                            // Target ONLY this instance. Without this a clone's edit
                            // restarted the built-in Custom RPC with the clone's config.
                            instanceIdValue?.let { putExtra(Constants.EXTRA_INSTANCE_ID, it) }
                        }
                        context.startService(intent)
                    }
                }
            }

            UiEvent.TriggerBottomSheet -> {
                _uiState.value =
                    _uiState.value.copy(showBottomSheet = !_uiState.value.showBottomSheet)
            }

            UiEvent.TriggerActivityTypeDropDownMenu -> {
                _uiState.value =
                    _uiState.value.copy(activityTypeIsExpanded = !_uiState.value.activityTypeIsExpanded)
            }

            UiEvent.TriggerStatusDropDownMenu -> {
                _uiState.value =
                    _uiState.value.copy(statusIsExpanded = !_uiState.value.statusIsExpanded)
            }

            UiEvent.TriggerPlatformDropDownMenu -> {
                _uiState.value =
                    _uiState.value.copy(platformIsExpanded = !_uiState.value.platformIsExpanded)
            }

            UiEvent.TriggerStartTimeStampsDialog -> {
                _uiState.value =
                    _uiState.value.copy(showStartTimeStampsPickerDialog = !_uiState.value.showStartTimeStampsPickerDialog)
            }

            UiEvent.TriggerStopTimeStampsDialog -> {
                _uiState.value =
                    _uiState.value.copy(showStopTimeStampsPickerDialog = !_uiState.value.showStopTimeStampsPickerDialog)
            }

            is UiEvent.UploadImage -> {
                uploadImage(event.file) {
                    event.callback(it)
                }
            }

            // Sheet Events
            UiEvent.SheetEvent.TriggerStoragePermissionRequest -> {
                _uiState.value = _uiState.value.copy(
                    showStoragePermissionRequestDialog = !_uiState.value.showStoragePermissionRequestDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.TriggerDeleteDialog -> {
                _uiState.value = _uiState.value.copy(
                    showDeleteDialog = !_uiState.value.showDeleteDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.TriggerLoadDialog -> {
                _uiState.value = _uiState.value.copy(
                    showLoadDialog = !_uiState.value.showLoadDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.TriggerShareDialog -> {
                _uiState.value = _uiState.value.copy(
                    showShareDialog = !_uiState.value.showShareDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.TriggerPreviewDialog -> {
                _uiState.value = _uiState.value.copy(
                    showPreviewDialog = !_uiState.value.showPreviewDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.TriggerSaveDialog -> {
                _uiState.value = _uiState.value.copy(
                    showSaveDialog = !_uiState.value.showSaveDialog,
                    showBottomSheet = false
                )
            }

            UiEvent.SheetEvent.ClearAllFields -> {
                _uiState.value = _uiState.value.copy(
                    rpcConfig = RpcConfig(),
                    showBottomSheet = false
                )
                // Persist the clearing so the next time the screen opens it starts empty
                Prefs[configKey] = ""
            }
        }
    }
}