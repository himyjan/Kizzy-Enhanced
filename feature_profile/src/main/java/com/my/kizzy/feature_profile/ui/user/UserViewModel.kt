package com.my.kizzy.feature_profile.ui.user

import android.content.Intent
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
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserViewModel @Inject constructor(
    private val getUserUseCase: GetUserUseCase,
    private val stateRepository: KizzyStateRepository
) : ViewModel() {

    private val logger: Logger = LoggerFactory.getLogger("UserViewModel")

    private val _state = MutableStateFlow<UserState>(UserState.Loading)
    val state: StateFlow<UserState> = _state.asStateFlow()

    init {
        getUser()
        observeRpcState()
    }

    /**
     * Logs the user out: clears all preferences, stops RPC services, and resets
     * state to show the Discord Signin button.
     *
     * This must be called from a coroutine scope.
     */
    fun logout(ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            logger.info("Logging out user")

            // Stop all running RPC services
            ctx.stopService(Intent(ctx, MediaRpcService::class.java))
            ctx.stopService(Intent(ctx, CustomRpcService::class.java))

            // Clear all user preferences
            Prefs.remove(Prefs.TOKEN)
            Prefs.remove(Prefs.USER_ID)
            Prefs.remove(Prefs.USER_DATA)
            Prefs.remove(Prefs.USER_BIO)
            Prefs.remove(Prefs.USER_NITRO)
            Prefs.remove(Prefs.DISCORD_OAUTH2_ACCESS_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_REFRESH_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_EXPIRES_AT)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USER_ID)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USERNAME)
            Prefs.remove(Prefs.RPC_CONNECTION_METHOD)
            Prefs.remove(Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID)
            Prefs.remove(Prefs.LASTFM_API_KEY)
            Prefs.remove(Prefs.LASTFM_SECRET)
            Prefs.remove(Prefs.LASTFM_USERNAME)
            Prefs.remove(Prefs.LASTFM_PASSWORD)
            Prefs.remove(Prefs.LASTFM_SESSION_KEY)
            Prefs.remove(Prefs.LASTFM_ENABLED)
            Prefs.remove(Prefs.LASTFM_ENABLE_TIMESTAMPS)
            Prefs.remove(Prefs.LASTFM_SHOW_COVER_ART)
            Prefs.remove(Prefs.LASTFM_SHOW_PARTY_CURRENT)
            Prefs.remove(Prefs.LASTFM_SHOW_PARTY_MAX)
            // Clear ListenBrainz scrobbling credentials so the next login
            // starts fresh with no stored scrobbling session.
            Prefs.remove(Prefs.LISTENBRAINZ_USER_TOKEN)
            Prefs.remove(Prefs.LISTENBRAINZ_ENABLED)
            Prefs.remove(Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS)
            Prefs.remove(Prefs.LISTENBRAINZ_SHOW_COVER_ART)
            Prefs.remove(Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT)
            Prefs.remove(Prefs.LISTENBRAINZ_SHOW_PARTY_MAX)

            // Reset state to show not-logged-in UI immediately
            _state.value = UserState.Error(
                error = "Not logged in",
                user = User(),
                rpcConfig = currentRpcConfig()
            )

            logger.info("User logged out successfully")
        }
    }

    private fun getUser() {
        val userId = Prefs[Prefs.USER_ID, ""]
        val token = Prefs[Prefs.TOKEN, ""]
        if (userId.isBlank() && token.isBlank()) {
            // Not logged in on cold start - show Error state with empty user
            // so UserScreen displays Discord Signin button immediately
            _state.value = UserState.Error(
                error = "Not logged in",
                user = User(),
                rpcConfig = currentRpcConfig()
            )
            return
        }
        // If token exists but userId is blank, still try to load user
        // (happens after Discord login where only token is saved initially)
        if (userId.isBlank() && token.isNotBlank()) {
            _state.value = UserState.Loading
            // Try to load user via GetUserUseCase - it will use token to fetch user info
            getUserUseCase(userId).onEach { result ->
                viewModelScope.launch(Dispatchers.Main) {
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
        } else {
            getUserUseCase(userId).onEach { result ->
                viewModelScope.launch(Dispatchers.Main) {
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
