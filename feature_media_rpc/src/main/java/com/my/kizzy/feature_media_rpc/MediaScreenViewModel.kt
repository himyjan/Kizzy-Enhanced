/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MediaScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_media_rpc

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.utils.getInstalledApps
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MediaScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state: MutableStateFlow<MediaAppsState> = MutableStateFlow(MediaAppsState())
    val state: StateFlow<MediaAppsState> = _state.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(context = Dispatchers.Default) {
            val appList = getInstalledApps(
                context = context,
                isEnabled = Prefs::isMediaAppEnabled
            ).sortedBy { !it.isChecked }
            val enabledApps = appList.associate { it.pkg to it.isChecked }
            _state.update {
                MediaAppsState(
                    apps = appList,
                    isLoading = false,
                    enabledApps = enabledApps,
                    activityTypes = Prefs.getAppActivityTypes()
                )
            }
        }
    }

    fun updateMediaAppEnabled(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentMap = _state.value.enabledApps
            val newEnabled = !(currentMap[pkg] ?: false)
            val updated = currentMap.toMutableMap().apply {
                this[pkg] = newEnabled
            }
            Prefs.setMediaAppsEnabled(updated.filterValues { it }.keys)
            _state.update { it.copy(enabledApps = updated) }
        }
    }

    fun selectAllMediaApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val allAppPkgs = state.value.apps.map { it.pkg }
            Prefs.setMediaAppsEnabled(allAppPkgs)
            val updated = allAppPkgs.associate { it to true }
            _state.update { currentState ->
                currentState.copy(enabledApps = updated)
            }
        }
    }

    fun deselectAllMediaApps() {
        viewModelScope.launch(Dispatchers.IO) {
            Prefs.setMediaAppsEnabled(emptyList())
            val updated = state.value.apps.associate { it.pkg to false }
            _state.update { currentState ->
                currentState.copy(enabledApps = updated)
            }
        }
    }

    /**
     * Stores the activity type of a single app. A null type removes the choice, so
     * the automatic detection takes over again.
     */
    fun updateAppActivityType(pkg: String, activityType: Int?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (activityType == null) {
                Prefs.clearAppActivityType(pkg)
            } else {
                Prefs.saveAppActivityType(pkg, activityType)
            }
            ActivityTypeResolver.invalidateCache()

            _state.update { currentState ->
                val updated = currentState.activityTypes.toMutableMap().apply {
                    if (activityType == null) remove(pkg) else this[pkg] = activityType
                }
                currentState.copy(activityTypes = updated)
            }
        }
    }
}