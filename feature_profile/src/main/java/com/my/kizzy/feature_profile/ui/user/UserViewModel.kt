package com.my.kizzy.feature_profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.domain.use_case.get_user.GetUserUseCase
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val getUserUseCase: GetUserUseCase,
    private val stateRepository: KizzyStateRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UserState>(UserState.Loading)
    val state: StateFlow<UserState> = _state.asStateFlow()

    init {
        getUser()
        observeRpcState()
    }

    private fun getUser() {
        getUserUseCase(Prefs[Prefs.USER_ID, ""]).onEach { result ->
            viewModelScope.launch {
                // Compute new state (off Main thread via coroutine dispatcher)
                val newState = when (result) {
                    is Resource.Success -> {
                        val user = result.data?.copy(
                            bio = Prefs[Prefs.USER_BIO],
                            nitro = Prefs[Prefs.USER_NITRO]
                        )
                        Prefs[Prefs.USER_DATA] = Json.encodeToString(result.data)
                        UserState.LoadingCompleted(
                            user,
                            currentRpcConfig()
                        )
                    }
                    is Resource.Error -> {
                        val user = Json.decodeFromString<User>(Prefs[Prefs.USER_DATA, "{}"])
                        UserState.Error(
                            error = result.message ?: "An unexpected error occurred",
                            user = user,
                            rpcConfig = currentRpcConfig()
                        )
                    }
                    is Resource.Loading -> UserState.Loading
                }
                // Write on Main thread AFTER current composition frame completes
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = newState
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun observeRpcState() {
        val mediaFlow = combine(
            stateRepository.currentTrackTitle,
            stateRepository.currentArtist,
            stateRepository.isPaused,
            stateRepository.currentTimestampStart,
            stateRepository.currentTimestampStop
        ) { title, artist, isPaused, tsStart, tsStop ->
            stateRepository.currentLargeImageUrl.combine(stateRepository.currentSmallImageUrl) { largeImg, smallImg ->
                val currentRpcConfig = try {
                    when {
                        AppUtils.customRpcRunning() -> {
                            val json = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                            if (json.isNotBlank()) runCatching { Json.decodeFromString<RpcConfig>(json) }
                                .getOrDefault(kizzyDefault())
                            else kizzyDefault()
                        }
                        AppUtils.mediaRpcRunning() -> RpcConfig(name = "Media RPC", details = "Playing media", type = "2")
                        AppUtils.experimentalRpcRunning() -> RpcConfig(name = "Experimental RPC", details = "Active")
                        AppUtils.appDetectionRunning() -> RpcConfig(name = "App Detection", details = "Detecting apps")
                        else -> kizzyDefault()
                    }
                } catch (e: Exception) { kizzyDefault() }

                currentRpcConfig.copy(
                    name = currentRpcConfig.name.copyIfNotEmpty(title ?: currentRpcConfig.name),
                    details = currentRpcConfig.details.copyIfNotEmpty(artist ?: currentRpcConfig.details),
                    state = currentRpcConfig.state.copyIfNotEmpty(artist ?: currentRpcConfig.state),
                    timestampsStart = tsStart?.toString() ?: "",
                    timestampsStop = tsStop?.toString() ?: "",
                    largeImg = largeImg ?: "",
                    smallImg = smallImg ?: ""
                )
            }
        }
        mediaFlow.flatMapLatest { it }.onEach { newRpcConfig ->
            viewModelScope.launch {
                // Compute new state
                val newState = UserState.LoadingCompleted(
                    when {
                        _state.value is UserState.LoadingCompleted -> (_state.value as UserState.LoadingCompleted).user
                        else -> User()
                    },
                    newRpcConfig
                )
                // Write on Main thread AFTER current composition frame completes
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = newState
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun currentRpcConfig(): RpcConfig = try {
        when {
            AppUtils.customRpcRunning() -> {
                val json = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                if (json.isNotBlank()) runCatching { Json.decodeFromString<RpcConfig>(json) }
                    .getOrDefault(kizzyDefault())
                else kizzyDefault()
            }
            AppUtils.mediaRpcRunning() -> RpcConfig(name = "Media RPC", details = "Playing media", type = "2")
            AppUtils.experimentalRpcRunning() -> RpcConfig(name = "Experimental RPC", details = "Active")
            AppUtils.appDetectionRunning() -> RpcConfig(name = "App Detection", details = "Detecting apps")
            else -> kizzyDefault()
        }
    } catch (e: Exception) { kizzyDefault() }

    private fun kizzyDefault() = RpcConfig(name = "Kizzy", details = "Using Kizzy")

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}
