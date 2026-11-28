/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * HomeScreenViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */

package com.my.kizzy.feature_home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.release.Release
import com.my.kizzy.domain.use_case.check_for_update.CheckForUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel backing update checks and release state on the Home screen.
 */
@HiltViewModel
class HomeScreenViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase
) : ViewModel() {

    private val _homeScreenState: MutableStateFlow<HomeScreenState> = MutableStateFlow(
        HomeScreenState.Loading
    )
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState

    fun getLatestUpdate() {
        checkForUpdateUseCase().onEach { result ->
            when (result) {
                is Resource.Success -> {
                    _homeScreenState.value =
                        HomeScreenState.LoadingCompleted(result.data ?: Release())
                }

                is Resource.Error -> {
                    _homeScreenState.value =
                        HomeScreenState.Error(result.message ?: "An unexpected error occurred")
                }

                is Resource.Loading -> {
                    _homeScreenState.value = HomeScreenState.Loading
                }
            }
        }.launchIn(viewModelScope)
    }

    fun setReleaseFromPrefs(release: Release) {
        _homeScreenState.value = HomeScreenState.LoadingCompleted(release)
    }
}

/**
 * UI state holder for release update checks on the Home screen.
 */
@Stable
sealed interface HomeScreenState {
    @Stable
    object Loading : HomeScreenState

    @Stable
    class Error(val error: String?) : HomeScreenState

    @Stable
    class LoadingCompleted(val release: Release) : HomeScreenState
}