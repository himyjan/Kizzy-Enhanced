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

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.use_case.get_games.GetGamesUseCase
import com.my.kizzy.domain.use_case.upload_galleryImage.UploadGalleryImageUseCase
import com.my.kizzy.feature_custom_rpc.components.sheet.stringToData
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.preference.Prefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

@HiltViewModel(assistedFactory = CustomScreenViewModel.Factory::class)
class CustomScreenViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val uploadGalleryImageUseCase: UploadGalleryImageUseCase,
    private val getGamesUseCase: GetGamesUseCase,
    /** Null for the built-in Custom RPC; a clone passes its own instance id. */
    @Assisted private val instanceId: String?,
) : ViewModel() {
    private val configKey: String = Prefs.customRpcConfigKey(instanceId)

    @AssistedFactory
    interface Factory {
        fun create(instanceId: String?): CustomScreenViewModel
    }

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState(instanceId = instanceId))
    val uiState: StateFlow<UiState> = _uiState
    private val uiEventChannel = Channel<UiEvent>(capacity = Channel.UNLIMITED)

    init {
        // Always load the last saved custom RPC config so the screen remembers
        // what the user last modified, even if they haven't started the RPC yet.
        val lastConfig = Prefs[configKey, ""]
        if (lastConfig.isNotBlank()) {
            _uiState.update { state ->
                state.copy(rpcConfig = lastConfig.stringToData())
            }
        }

        // Load console games so the game list shows in Custom RPC tab
        viewModelScope.launch {
            getGamesUseCase().collect { result ->
                if (result is Resource.Success) {
                    val freshGames = result.data.orEmpty()
                    ConsoleGameCatalog.update(freshGames)
                    _uiState.update { state ->
                        state.copy(consoleGames = freshGames)
                    }
                }
            }
        }

        viewModelScope.launch {
            uiEventChannel.consumeAsFlow().collect { event ->
                processEvent(event)
            }
        }
    }

    private suspend fun uploadImage(file: File, result: (String) -> Unit) {
        uploadGalleryImageUseCase(file)?.let { uploadedUrl ->
            withContext(Dispatchers.Main) {
                result(uploadedUrl.drop(3))
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
                _uiState.update { state ->
                    state.copy(rpcConfig = event.config)
                }
                // Persist immediately so the RPC config survives navigation away
                // and re-open of the screen.
                Prefs[configKey] = Json.encodeToString(event.config)

                // Restart running Custom RPC service so field changes apply live
                // without the user having to close and reopen the app.
                if (AppUtils.customRpcRunning()) {
                    val intent = Intent(context, CustomRpcService::class.java).apply {
                        action = Constants.ACTION_RESTART_SERVICE
                        putExtra("RPC", Json.encodeToString(event.config))
                        putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, false)
                        // Target ONLY this instance. Without this a clone's edit
                        // restarted the built-in Custom RPC with the clone's config.
                        instanceId?.let { putExtra(Constants.EXTRA_INSTANCE_ID, it) }
                    }
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {
                        // Ignore background start restrictions if service is already running or restricted
                    }
                }
            }

            UiEvent.TriggerBottomSheet -> {
                _uiState.update { it.copy(showBottomSheet = !it.showBottomSheet) }
            }

            UiEvent.TriggerActivityTypeDropDownMenu -> {
                _uiState.update { it.copy(activityTypeIsExpanded = !it.activityTypeIsExpanded) }
            }

            UiEvent.TriggerStatusDropDownMenu -> {
                _uiState.update { it.copy(statusIsExpanded = !it.statusIsExpanded) }
            }

            UiEvent.TriggerPlatformDropDownMenu -> {
                _uiState.update { it.copy(platformIsExpanded = !it.platformIsExpanded) }
            }

            UiEvent.TriggerStartTimeStampsDialog -> {
                _uiState.update {
                    it.copy(showStartTimeStampsPickerDialog = !it.showStartTimeStampsPickerDialog)
                }
            }

            UiEvent.TriggerStopTimeStampsDialog -> {
                _uiState.update {
                    it.copy(showStopTimeStampsPickerDialog = !it.showStopTimeStampsPickerDialog)
                }
            }

            is UiEvent.UploadImage -> {
                uploadImage(event.file, event.callback)
            }

            // Sheet Events
            UiEvent.SheetEvent.TriggerStoragePermissionRequest -> {
                _uiState.update {
                    it.copy(
                        showStoragePermissionRequestDialog = !it.showStoragePermissionRequestDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.TriggerDeleteDialog -> {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = !it.showDeleteDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.TriggerLoadDialog -> {
                _uiState.update {
                    it.copy(
                        showLoadDialog = !it.showLoadDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.TriggerShareDialog -> {
                _uiState.update {
                    it.copy(
                        showShareDialog = !it.showShareDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.TriggerPreviewDialog -> {
                _uiState.update {
                    it.copy(
                        showPreviewDialog = !it.showPreviewDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.TriggerSaveDialog -> {
                _uiState.update {
                    it.copy(
                        showSaveDialog = !it.showSaveDialog,
                        showBottomSheet = false
                    )
                }
            }

            UiEvent.SheetEvent.ClearAllFields -> {
                _uiState.update {
                    it.copy(
                        rpcConfig = RpcConfig(),
                        showBottomSheet = false
                    )
                }
                // Persist the clearing so the next time the screen opens it starts empty
                Prefs[configKey] = ""
            }
        }
    }
}