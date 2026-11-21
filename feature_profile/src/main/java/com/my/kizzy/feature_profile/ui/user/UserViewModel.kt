package com.my.kizzy.feature_profile.ui.user

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.domain.use_case.get_user.GetUserUseCase
import com.my.kizzy.feature_profile.fetchDiscordUser
import com.my.kizzy.feature_profile.fetchUserInfo
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.DiscordOAuth2RpcService
import com.my.kizzy.feature_rpc_base.services.ManualRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
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

    /** Cancels an in-flight identity fetch when the account logs out. */
    private var identityJob: kotlinx.coroutines.Job? = null
    /** Last identity key we resolved, so prefs emissions don't re-fetch forever. */
    @Volatile
    private var lastIdentityKey: String? = null

    init {
        observeLoginState()
        observeRpcState()
        
        // Immediately load the current RPC config on init so the profile shows
        // the real state after a cold boot, not empty/null values
        val currentConfig = currentRpcConfig()
        val currentState = _state.value
        when (currentState) {
            is UserState.Error -> _state.value = UserState.Error(
                error = currentState.error,
                user = currentState.user,
                rpcConfig = currentConfig
            )
            is UserState.LoadingCompleted -> _state.value = UserState.LoadingCompleted(
                user = currentState.user,
                rpcConfig = currentConfig
            )
            UserState.Loading -> Unit // Keep as Loading, observeRpcState will update
        }
    }

    /**
     * Reacts to credential changes in [Prefs.prefsState].
     *
     * The profile ViewModel lives in the navigation backstack and used to fetch
     * only once in init, so after a Discord re-login (or logout) it kept showing
     * the stale "Login with Discord" state. Every token/USER_ID write is now a
     * trigger.
     */
    private fun observeLoginState() {
        Prefs.prefsState
            .map { state ->
                val token = state[Prefs.TOKEN] as? String ?: ""
                val userId = state[Prefs.USER_ID] as? String ?: ""
                val cached = state[Prefs.USER_DATA] as? String ?: ""
                Triple(token, userId, cached)
            }
            .distinctUntilChanged()
            .onEach { (token, userId, cachedJson) ->
                val key = "$token|$userId|$cachedJson"
                if (key == lastIdentityKey) return@onEach
                lastIdentityKey = key

                identityJob?.cancel()
                identityJob = viewModelScope.launch {
                    loadIdentity(token, userId, cachedJson)
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun loadIdentity(token: String, userId: String, cachedJson: String) {
        val cachedUser = runCatching {
            if (cachedJson.isNotBlank()) Json.decodeFromString<User>(cachedJson) else null
        }.getOrNull()

        if (token.isBlank()) {
            // Logged out: no account data at all.
            _state.value = UserState.Error(
                error = "Not logged in",
                user = User(),
                rpcConfig = currentRpcConfig()
            )
            return
        }

        // Prefer the cached identity (instant, no flicker). Otherwise fetch the
        // full user via REST /users/@me - unlike the gateway READY payload this
        // works with OAuth2 bearer tokens and saves USER_DATA (avatar/bio/banner).
        val fresh = cachedUser?.takeIf { !it.id.isNullOrBlank() }
            ?: fetchDiscordUser(token)
            ?: fetchUserInfo(token)
        if (fresh != null) {
            _state.value = UserState.LoadingCompleted(fresh, currentRpcConfig())
            return
        }

        // Everything failed: fall back to the profile API, but NEVER report
        // "not logged in" while a token is present - that would wrongly show
        // the login button.
        val fallback = runCatching {
            var result: User? = null
            getUserUseCase(userId).collect { resource ->
                if (resource is Resource.Success) result = resource.data
            }
            result
        }.getOrNull()

        _state.value = if (fallback != null) {
            UserState.LoadingCompleted(fallback, currentRpcConfig())
        } else {
            UserState.Error(
                error = "Could not load profile",
                user = User(),
                rpcConfig = currentRpcConfig()
            )
        }
    }

    /**
     * Logs the user out: stops every RPC service, clears all credentials and
     * account data, and resets state to show the Discord Signin button.
     */
    fun logout(ctx: Context, scope: CoroutineScope) {
        viewModelScope.launch {
            logger.info("Logging out user")

            // Stop EVERY RPC service, not just Media/Custom. Manual RPC,
            // App Detection and the OAuth2 service also hold a gateway socket
            // with the old token; leaving them running keeps a stale session
            // alive and can resurrect the presence after logout.
            // Best-effort stop of all RPC services; ignore failures.
            if (runCatching { RpcManager.getInstance(ctx).switchTo(RpcManager.RpcType.None) }.isFailure) {
            }
            listOf(
                MediaRpcService::class.java,
                CustomRpcService::class.java,
                ManualRpc::class.java,
                AppDetectionService::class.java,
                DiscordOAuth2RpcService::class.java,
            ).forEach { service -> ctx.stopService(Intent(ctx, service)) }

            // Credentials - both gateway (user token) and OAuth2 (bearer +
            // refresh). Both are encrypted on disk via SecurePrefs.
            Prefs.remove(Prefs.TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_ACCESS_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_REFRESH_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_EXPIRES_AT)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USER_ID)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USERNAME)
            Prefs.remove(Prefs.RPC_CONNECTION_METHOD)
            // Do NOT clear the OAuth2 application client id - it is a
            // configuration value, not a credential.

            // Account identity
            Prefs.remove(Prefs.USER_ID)
            Prefs.remove(Prefs.USER_DATA)
            Prefs.remove(Prefs.USER_BIO)
            Prefs.remove(Prefs.USER_NITRO)

            // Third-party credentials / scrobbling sessions
            Prefs.remove(Prefs.LASTFM_API_KEY)
            Prefs.remove(Prefs.LASTFM_SECRET)
            Prefs.remove(Prefs.LASTFM_USERNAME)
            Prefs.remove(Prefs.LASTFM_PASSWORD)
            Prefs.remove(Prefs.LASTFM_SESSION_KEY)
            Prefs.remove(Prefs.LISTENBRAINZ_USER_TOKEN)

            // Reset state immediately; observeLoginState() will also react.
            identityJob?.cancel()
            _state.value = UserState.Error(
                error = "Not logged in",
                user = User(),
                rpcConfig = currentRpcConfig()
            )
            logger.info("User logged out successfully")
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
            viewModelScope.launch(Dispatchers.Main) {
                val current = _state.value
                // While still loading, ignore media updates: rendering the
                // profile with an EMPTY user caused a visible jump on open.
                if (current is UserState.Loading) return@launch
                if (current is UserState.LoadingCompleted && current.rpcConfig == newRpcConfig) {
                    return@launch
                }
                _state.value = UserState.LoadingCompleted(
                    when (current) {
                        is UserState.LoadingCompleted -> current.user
                        is UserState.Error -> current.user
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
            AppUtils.ManualRpcRunning() ->
                LastPresence.experimental() ?: RpcConfig(name = "Manual RPC", details = "Active")
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
