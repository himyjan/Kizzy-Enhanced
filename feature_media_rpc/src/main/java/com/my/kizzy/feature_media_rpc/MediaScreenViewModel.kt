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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state: MutableStateFlow<MediaAppsState> = MutableStateFlow(MediaAppsState())
    val state = _state

    init {
        getInstalledApps()
    }

    private fun getInstalledApps() {
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
            // Toggle the app, then persist the COMPLETE enabled list. Persisting a single
            // entry would otherwise reset the list to the default package set, which
            // silently disabled every app the user had actually chosen.
            val currentlyEnabled = _state.value.enabledApps[pkg] ?: false
            val newEnabled = !currentlyEnabled

            val updated = _state.value.enabledApps.toMutableMap().apply {
                this[pkg] = newEnabled
            }
            Prefs.setMediaAppsEnabled(updated.filterValues { it }.keys)

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
            // The resolver caches the overrides for a short while.
            ActivityTypeResolver.invalidateCache()

            _state.update { currentState ->
                val updated = currentState.activityTypes.toMutableMap()
                if (activityType == null) {
                    updated.remove(pkg)
                } else {
                    updated[pkg] = activityType
                }
                currentState.copy(activityTypes = updated)
            }
        }
    }
}