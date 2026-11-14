package com.my.kizzy.feature_profile.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.domain.use_case.get_user.GetUserUseCase
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalCoroutinesApi::class)
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
            // The ViewModel is created from inside the profile composition. A flow that
            // emits eagerly would otherwise run this code synchronously during that
            // composition. Posting to the main queue moves it to after the frame.
            viewModelScope.launch(Dispatchers.Main) {
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
                _state.value = newState
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
            combine(
                stateRepository.currentLargeImageUrl,
                stateRepository.currentSmallImageUrl,
                stateRepository.pausedPositionMs
            ) { largeImg, smallImg, pausedPositionMs ->
                val currentRpcConfig = currentRpcConfig()

                currentRpcConfig.copy(
                    name = currentRpcConfig.name.copyIfNotEmpty(title ?: currentRpcConfig.name),
                    details = currentRpcConfig.details.copyIfNotEmpty(artist ?: currentRpcConfig.details),
                    state = currentRpcConfig.state.copyIfNotEmpty(artist ?: currentRpcConfig.state),
                    timestampsStart = tsStart?.toString() ?: currentRpcConfig.timestampsStart,
                    timestampsStop = tsStop?.toString() ?: currentRpcConfig.timestampsStop,
                    largeImg = largeImg ?: currentRpcConfig.largeImg,
                    smallImg = smallImg ?: currentRpcConfig.smallImg,
                    paused = isPaused,
                    pausedPositionMs = pausedPositionMs
                )
            }
        }
        mediaFlow.flatMapLatest { it }.onEach { newRpcConfig ->
            // Post the write instead of doing it inside the flow collection. The state
            // is read during composition, so it must not change while the profile is
            // composing.
            viewModelScope.launch(Dispatchers.Main) {
                val current = _state.value
                // The media service can publish the same presence again (for example a
                // duplicate metadata callback). Replacing the state with an equal value
                // would only trigger another recomposition of the whole profile.
                if (current is UserState.LoadingCompleted && current.rpcConfig == newRpcConfig) {
                    return@launch
                }
                _state.value = UserState.LoadingCompleted(
                    when (current) {
                        is UserState.LoadingCompleted -> current.user
                        is UserState.Error -> current.user
                        else -> User()
                    },
                    newRpcConfig
                )
            }
        }.launchIn(viewModelScope)
    }

    /**
     * The presence that is shown on Discord right now.
     *
     * Every service remembers the presence it sent last, so the preview shows
     * the real data - the name, the details, the timestamps and the buttons -
     * instead of a placeholder. When the service is not running, the memory of
     * the last run is used.
     */
    private fun currentRpcConfig(): RpcConfig = try {
        when {
            AppUtils.customRpcRunning() -> {
                val json = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                if (json.isNotBlank()) runCatching { Json.decodeFromString<RpcConfig>(json) }
                    .getOrDefault(kizzyDefault())
                else kizzyDefault()
            }
            AppUtils.mediaRpcRunning() ->
                LastPresence.media() ?: RpcConfig(name = "Media RPC", details = "Playing media", type = "2")
            AppUtils.experimentalRpcRunning() ->
                LastPresence.experimental() ?: RpcConfig(name = "Experimental RPC", details = "Active")
            AppUtils.appDetectionRunning() -> RpcConfig(name = "App Detection", details = "Detecting apps")
            else -> LastPresence.media() ?: LastPresence.experimental() ?: kizzyDefault()
        }
    } catch (e: Exception) { kizzyDefault() }

    private fun kizzyDefault() = RpcConfig(name = "Kizzy", details = "Using Kizzy")

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}
