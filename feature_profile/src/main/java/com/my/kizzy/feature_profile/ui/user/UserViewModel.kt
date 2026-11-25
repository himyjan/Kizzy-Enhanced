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
import com.my.kizzy.feature_profile.fetchDiscordUserProfile
import com.my.kizzy.feature_profile.fetchUserInfo
import com.my.kizzy.feature_rpc_base.AppUtils
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

    private companion object {
        /** How often the gateway identity may be re-read to refresh the custom status. */
        const val IDENTITY_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    init {
        observeLoginState()
        observeRpcState()

        // Immediately load the current RPC configs on init so the profile shows
        // the real state after a cold boot, not empty/null values
        val currentConfigs = currentRpcConfigs()
        val currentState = _state.value
        when (currentState) {
            is UserState.Error -> _state.value = UserState.Error(
                error = currentState.error,
                user = currentState.user,
                rpcConfigs = currentConfigs
            )
            is UserState.LoadingCompleted -> _state.value = UserState.LoadingCompleted(
                user = currentState.user,
                rpcConfigs = currentConfigs
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
                // Either credential means the user is logged in: the gateway
                // account token or the OAuth2 bearer token (gateway rejects the
                // latter, but REST /users/@me accepts it for the profile).
                val token = (state[Prefs.TOKEN] as? String ?: "").ifBlank {
                    state[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String ?: ""
                }
                val userId = state[Prefs.USER_ID] as? String ?: ""
                val cached = state[Prefs.USER_DATA] as? String ?: ""
                Triple(token, userId, cached)
            }
            .distinctUntilChanged()
            .onEach { (token, userId, cachedJson) ->
                // Do not include cachedJson in the key: our own profile writes
                // would otherwise retrigger and cancel the in-flight bio fetch.
                val key = "$token|$userId"
                if (key == lastIdentityKey) return@onEach
                lastIdentityKey = key

                identityJob?.cancel()
                identityJob = viewModelScope.launch {
                    loadIdentity(token, userId, cachedJson)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Best-effort background refresh of the bio for OAuth2 sessions.
     * Faster cached UI is shown first; if the profile endpoint returns a bio,
     * persist and emit the merged user.
     */
    private fun refreshBioInBackground(token: String, baseUser: User) {
        viewModelScope.launch {
            val bio = fetchDiscordUserProfile(token) ?: return@launch
            if (bio.isBlank()) return@launch
            val current = _state.value
            val currentUser = when (current) {
                is UserState.LoadingCompleted -> current.user
                is UserState.Error -> current.user
                UserState.Loading -> baseUser
            }
            if (currentUser?.bio == bio) return@launch
            val merged = (currentUser ?: baseUser).copy(bio = bio)
            Prefs[Prefs.USER_DATA] = Json.encodeToString(merged)
            _state.value = UserState.LoadingCompleted(merged, currentRpcConfigs())
        }
    }

    /**
     * The custom status is only exposed in the gateway READY payload. A profile
     * written by the REST token login therefore has no custom status, and the
     * cached branch below would never fetch it again. Refresh the identity from
     * the gateway for account tokens, at most once per
     * [IDENTITY_REFRESH_INTERVAL_MS], so the Bio section does not stay stuck on
     * the "No bio yet" placeholder.
     */
    private fun shouldRefreshIdentity(): Boolean {
        val last = Prefs[Prefs.USER_PROFILE_SYNCED_AT, 0L]
        return System.currentTimeMillis() - last >= IDENTITY_REFRESH_INTERVAL_MS
    }

    private fun refreshIdentityInBackground(token: String, baseUser: User) {
        // OAuth2 bearer tokens are rejected by the gateway; only account tokens
        // (id.timestamp.hmac, two dots) can read the READY payload.
        val isAccountToken = token.count { it == '.' } >= 2 || token.startsWith("mfa.")
        if (!isAccountToken) return

        viewModelScope.launch {
            val fresh = fetchUserInfo(token) ?: return@launch
            Prefs[Prefs.USER_PROFILE_SYNCED_AT] = System.currentTimeMillis()

            val current = _state.value
            val currentUser = when (current) {
                is UserState.LoadingCompleted -> current.user
                is UserState.Error -> current.user
                UserState.Loading -> baseUser
            } ?: baseUser

            // READY is authoritative for the identity and the custom status,
            // but keep a locally fetched bio when the payload does not carry one.
            val merged = fresh.copy(
                bio = fresh.bio?.takeIf { it.isNotBlank() } ?: currentUser.bio,
            )
            if (merged == currentUser) return@launch
            Prefs[Prefs.USER_DATA] = Json.encodeToString(merged)
            _state.value = UserState.LoadingCompleted(merged, currentRpcConfigs())
        }
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
                rpcConfigs = currentRpcConfigs()
            )
            return
        }

        // If we already have persisted user data, show it immediately.
        // If bio is missing, refresh it in the background so OAuth2 users
        // still get the bio from the profile endpoint.
        if (cachedUser != null && cachedUser.id?.isNotBlank() == true) {
            _state.value = UserState.LoadingCompleted(cachedUser, currentRpcConfigs())
            if (cachedUser.bio.isNullOrBlank()) {
                refreshBioInBackground(token, cachedUser)
            }
            if (shouldRefreshIdentity()) {
                refreshIdentityInBackground(token, cachedUser)
            }
            return
        }

        // OAuth2 bearer tokens cannot open a Discord gateway connection, so the
        // READY payload never arrives and fetchUserInfo always times out.
        // Account tokens contain two dots; OAuth2 tokens do not.
        val isAccountToken = token.count { it == '.' } >= 2 || token.startsWith("mfa.")
        if (!isAccountToken) {
            // OAuth2: /users/@me gives the profile quickly, then best-effort
            // fetch /users/@me/profile for the bio and merge it.
            val fresh = fetchDiscordUser(token)
            if (fresh != null) {
                val withBio = fetchDiscordUserProfile(token)?.let { profileBio ->
                    fresh.copy(bio = profileBio)
                } ?: fresh
                Prefs[Prefs.USER_DATA] = Json.encodeToString(withBio)
                _state.value = UserState.LoadingCompleted(withBio, currentRpcConfigs())
                return
            }
        } else {
            // Account token: gateway READY includes bio. Fall back to REST.
            val fresh = fetchUserInfo(token)
                ?: fetchDiscordUser(token)
                ?: cachedUser?.takeIf { !it.id.isNullOrBlank() }
            if (fresh != null) {
                _state.value = UserState.LoadingCompleted(fresh, currentRpcConfigs())
                return
            }
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
            UserState.LoadingCompleted(fallback, currentRpcConfigs())
        } else {
            UserState.Error(
                error = "Could not load profile",
                user = User(),
                rpcConfigs = currentRpcConfigs()
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
            if (runCatching { com.my.kizzy.feature_rpc_base.RpcManager.getInstance(ctx).switchTo(com.my.kizzy.feature_rpc_base.RpcManager.RpcType.None) }.isFailure) {
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
                rpcConfigs = currentRpcConfigs()
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
                // Start from the full set of active RPC configs and only
                // update the MediaRpc entry with live media state.
                val configs = currentRpcConfigs().toMutableList()
                val mediaIdx = configs.indexOfFirst { it.type == "2" }

                val mediaEntry = configs.getOrNull(mediaIdx)
                val updatedMedia = mediaEntry?.copy(
                    name = mediaEntry.name.copyIfNotEmpty(title),
                    details = mediaEntry.details.copyIfNotEmpty(artist ?: mediaEntry.details),
                    state = mediaEntry.state.copyIfNotEmpty(artist ?: mediaEntry.state),
                    timestampsStart = tsStart?.toString() ?: mediaEntry.timestampsStart,
                    timestampsStop = tsStop?.toString() ?: mediaEntry.timestampsStop,
                    largeImg = largeImg ?: mediaEntry.largeImg,
                    smallImg = smallImg ?: mediaEntry.smallImg,
                    paused = isPaused,
                    pausedPositionMs = pausedPositionMs
                )

                if (mediaIdx >= 0 && updatedMedia != null) {
                    configs[mediaIdx] = updatedMedia
                } else if (updatedMedia != null) {
                    configs.add(updatedMedia)
                }
                configs
            }
        }
        mediaFlow.flatMapLatest { it }.onEach { newConfigs ->
            viewModelScope.launch(Dispatchers.Main) {
                val current = _state.value
                // While still loading, ignore media updates.
                if (current is UserState.Loading) return@launch
                // NEVER overwrite a logged-out state (UserState.Error) with
                // an empty user - that would wrongly show Logout instead of
                // Login.
                if (current !is UserState.LoadingCompleted) return@launch
                if (current.rpcConfigs == newConfigs) {
                    return@launch
                }
                _state.value = UserState.LoadingCompleted(
                    current.user,
                    newConfigs
                )
            }
        }.launchIn(viewModelScope)
    }

    /**
     * The presence configs that are shown on Discord right now.
     *
     * Every service remembers the presence it sent last, so the preview shows
     * the real data - the name, the details, the timestamps and the buttons -
     * instead of a placeholder. When the service is not running, the memory of
     * the last run is used.
     *
     * Multiple RPCs can be active at the same time, so this returns all of
     * them.
     */
    private fun currentRpcConfigs(): List<RpcConfig> = try {
        val configs = mutableListOf<RpcConfig>()

        if (AppUtils.customRpcRunning()) {
            val json = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
            if (json.isNotBlank()) {
                runCatching { Json.decodeFromString<RpcConfig>(json) }
                    .getOrDefault(kizzyDefault("Custom RPC"))
                    .let { configs.add(it) }
            } else {
                configs.add(kizzyDefault("Custom RPC"))
            }
        }
        if (AppUtils.mediaRpcRunning()) {
            LastPresence.media()?.let { configs.add(it) } ?: configs.add(RpcConfig(name = "Media RPC", details = "Playing media", type = "2"))
        }
        if (AppUtils.ManualRpcRunning()) {
            LastPresence.experimental()?.let { configs.add(it) } ?: configs.add(RpcConfig(name = "Manual RPC", details = "Active"))
        }
        if (AppUtils.appDetectionRunning()) {
            configs.add(RpcConfig(name = "App Detection", details = "Detecting apps"))
        }

        return if (configs.isNotEmpty()) configs else listOf(LastPresence.media() ?: LastPresence.experimental() ?: kizzyDefault())
    } catch (e: Exception) { listOf(kizzyDefault()) }

    private fun kizzyDefault(name: String = "Kizzy") = RpcConfig(name = name, details = "Using $name")

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}
