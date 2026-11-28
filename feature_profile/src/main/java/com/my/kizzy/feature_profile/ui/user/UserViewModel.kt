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
import com.my.kizzy.feature_profile.persistDiscordUser
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserViewModel @Inject constructor(
    private val getUserUseCase: GetUserUseCase,
    private val stateRepository: KizzyStateRepository,
    private val rpcManager: RpcManager
) : ViewModel() {

    private val logger: Logger = LoggerFactory.getLogger("UserViewModel")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _state = MutableStateFlow<UserState>(UserState.Loading)
    val state: StateFlow<UserState> = _state.asStateFlow()

    private var identityJob: Job? = null

    @Volatile
    private var lastIdentityKey: String? = null

    private companion object {
        const val IDENTITY_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    init {
        observeLoginState()
        observeRpcState()

        val currentConfigs = currentRpcConfigs()
        when (val currentState = _state.value) {
            is UserState.Error -> _state.value = UserState.Error(
                error = currentState.error,
                user = currentState.user,
                rpcConfigs = currentConfigs
            )
            is UserState.LoadingCompleted -> _state.value = UserState.LoadingCompleted(
                user = currentState.user,
                rpcConfigs = currentConfigs
            )
            UserState.Loading -> Unit
        }
    }

    private fun observeLoginState() {
        Prefs.prefsState
            .map { state ->
                val token = (state[Prefs.TOKEN] as? String ?: "").ifBlank {
                    state[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String ?: ""
                }
                val userId = state[Prefs.USER_ID] as? String ?: ""
                val cached = state[Prefs.USER_DATA] as? String ?: ""
                Triple(token, userId, cached)
            }
            .distinctUntilChanged()
            .onEach { (token, userId, cachedJson) ->
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

    private fun refreshBioInBackground(token: String, baseUser: User) {
        viewModelScope.launch {
            val bio = fetchDiscordUserProfile(token)?.takeIf { it.isNotBlank() } ?: return@launch
            val current = _state.value
            val currentUser = when (current) {
                is UserState.LoadingCompleted -> current.user
                is UserState.Error -> current.user
                UserState.Loading -> baseUser
            }
            if (currentUser?.bio == bio) return@launch
            val merged = (currentUser ?: baseUser).copy(bio = bio)
            persistDiscordUser(merged)
            _state.value = UserState.LoadingCompleted(merged, currentRpcConfigs())
        }
    }

    private fun shouldRefreshIdentity(): Boolean {
        val last = Prefs[Prefs.USER_PROFILE_SYNCED_AT, 0L]
        return System.currentTimeMillis() - last >= IDENTITY_REFRESH_INTERVAL_MS
    }

    private fun refreshIdentityInBackground(token: String, baseUser: User) {
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

            val merged = fresh.copy(
                bio = fresh.bio?.takeIf { it.isNotBlank() } ?: currentUser.bio,
                nitro = fresh.nitro || currentUser.nitro,
            )
            if (merged == currentUser) return@launch
            persistDiscordUser(merged)
            _state.value = UserState.LoadingCompleted(merged, currentRpcConfigs())
        }
    }

    private suspend fun loadIdentity(token: String, userId: String, cachedJson: String) {
        val cachedUser = runCatching {
            if (cachedJson.isNotBlank()) json.decodeFromString<User>(cachedJson) else null
        }.getOrNull()

        if (token.isBlank()) {
            _state.value = UserState.Error(
                error = "Not logged in",
                user = User(),
                rpcConfigs = currentRpcConfigs()
            )
            return
        }

        if (cachedUser != null && !cachedUser.id.isNullOrBlank()) {
            _state.value = UserState.LoadingCompleted(cachedUser, currentRpcConfigs())
            if (cachedUser.bio.isNullOrBlank()) {
                refreshBioInBackground(token, cachedUser)
            }
            if (shouldRefreshIdentity()) {
                refreshIdentityInBackground(token, cachedUser)
            }
            return
        }

        val isAccountToken = token.count { it == '.' } >= 2 || token.startsWith("mfa.")
        if (!isAccountToken) {
            val fresh = fetchDiscordUser(token)
            if (fresh != null) {
                val withBio = fetchDiscordUserProfile(token)?.takeIf { it.isNotBlank() }?.let { profileBio ->
                    fresh.copy(bio = profileBio)
                } ?: fresh
                persistDiscordUser(withBio)
                _state.value = UserState.LoadingCompleted(withBio, currentRpcConfigs())
                return
            }
        } else {
            val fresh = fetchUserInfo(token)
                ?: fetchDiscordUser(token)
                ?: cachedUser?.takeIf { !it.id.isNullOrBlank() }
            if (fresh != null) {
                val withBio = fetchDiscordUserProfile(token)?.takeIf { it.isNotBlank() }?.let { profileBio ->
                    fresh.copy(bio = profileBio)
                } ?: fresh
                persistDiscordUser(withBio)
                _state.value = UserState.LoadingCompleted(withBio, currentRpcConfigs())
                return
            }
        }

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

    fun logout(ctx: Context, scope: CoroutineScope = viewModelScope) {
        scope.launch {
            logger.info("Logging out user")

            runCatching { rpcManager.switchTo(RpcManager.RpcType.None) }
            listOf(
                MediaRpcService::class.java,
                CustomRpcService::class.java,
                ManualRpc::class.java,
                AppDetectionService::class.java,
                DiscordOAuth2RpcService::class.java,
            ).forEach { service ->
                runCatching { ctx.stopService(Intent(ctx, service)) }
            }

            Prefs.remove(Prefs.TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_ACCESS_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_REFRESH_TOKEN)
            Prefs.remove(Prefs.DISCORD_OAUTH2_EXPIRES_AT)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USER_ID)
            Prefs.remove(Prefs.DISCORD_OAUTH2_USERNAME)
            Prefs.remove(Prefs.RPC_CONNECTION_METHOD)

            Prefs.remove(Prefs.USER_ID)
            Prefs.remove(Prefs.USER_DATA)
            Prefs.remove(Prefs.USER_BIO)
            Prefs.remove(Prefs.USER_NITRO)

            Prefs.remove(Prefs.LASTFM_API_KEY)
            Prefs.remove(Prefs.LASTFM_SECRET)
            Prefs.remove(Prefs.LASTFM_USERNAME)
            Prefs.remove(Prefs.LASTFM_PASSWORD)
            Prefs.remove(Prefs.LASTFM_SESSION_KEY)
            Prefs.remove(Prefs.LISTENBRAINZ_USER_TOKEN)

            lastIdentityKey = null
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
        val mediaStateFlow: Flow<MediaLiveState> = combine(
            stateRepository.currentTrackTitle,
            stateRepository.currentArtist,
            stateRepository.isPaused,
            stateRepository.currentTimestampStart,
            stateRepository.currentTimestampStop
        ) { title, artist, isPaused, tsStart, tsStop ->
            MediaLiveState(title, artist, isPaused, tsStart, tsStop)
        }
        val mediaImagesFlow: Flow<MediaImagesLiveState> = combine(
            stateRepository.currentLargeImageUrl,
            stateRepository.currentSmallImageUrl,
            stateRepository.pausedPositionMs
        ) { largeImg, smallImg, pausedPosition ->
            MediaImagesLiveState(largeImg, smallImg, pausedPosition)
        }

        combine(rpcManager.presenceConfigs, mediaStateFlow, mediaImagesFlow) { configs, media, images ->
            val liveConfigs = configs.values.map { cfg ->
                cfg.copy(instanceId = cfg.instanceId.ifBlank { cfg.name })
            }.toMutableList()

            val mediaIdx = liveConfigs.indexOfFirst { it.type == "2" || it.type == "3" }
            if (mediaIdx >= 0) {
                val mediaEntry = liveConfigs[mediaIdx]
                liveConfigs[mediaIdx] = mediaEntry.copy(
                    name = mediaEntry.name.copyIfNotEmpty(media.title),
                    details = mediaEntry.details.copyIfNotEmpty(media.artist ?: mediaEntry.details),
                    state = mediaEntry.state.copyIfNotEmpty(media.artist ?: mediaEntry.state),
                    timestampsStart = media.tsStart?.toString() ?: mediaEntry.timestampsStart,
                    timestampsStop = media.tsStop?.toString() ?: mediaEntry.timestampsStop,
                    largeImg = images.large ?: mediaEntry.largeImg,
                    smallImg = images.small ?: mediaEntry.smallImg,
                    paused = media.isPaused,
                    pausedPositionMs = images.pausedPosition
                )
            }

            if (liveConfigs.isEmpty()) currentRpcConfigs() else liveConfigs
        }.onEach { newConfigs ->
            viewModelScope.launch(Dispatchers.Main) {
                val current = _state.value
                if (current is UserState.LoadingCompleted && current.rpcConfigs != newConfigs) {
                    _state.value = UserState.LoadingCompleted(current.user, newConfigs)
                }
            }
        }.launchIn(viewModelScope)
    }

    private data class MediaLiveState(
        val title: String?,
        val artist: String?,
        val isPaused: Boolean,
        val tsStart: Long?,
        val tsStop: Long?,
    )

    private data class MediaImagesLiveState(
        val large: String?,
        val small: String?,
        val pausedPosition: Long?,
    )

    private fun currentRpcConfigs(): List<RpcConfig> = try {
        val configs = mutableListOf<RpcConfig>()

        val instances = Prefs.getHomeRpcInstances()
        for (instance in instances) {
            if (instance.type != "custom_rpc") continue
            if (!AppUtils.customRpcRunning()) continue
            val rawJson = Prefs.getCustomRpcConfig(instance.id.takeIf { it != "custom_rpc" })
            if (rawJson.isNotBlank()) {
                runCatching { json.decodeFromString<RpcConfig>(rawJson) }
                    .getOrDefault(kizzyDefault(instance.label))
                    .let { configs.add(it) }
            } else {
                configs.add(kizzyDefault(instance.label))
            }
        }

        if (AppUtils.mediaRpcRunning()) {
            LastPresence.media()?.let { configs.add(it) }
                ?: configs.add(RpcConfig(name = "Media RPC", details = "Playing media", type = "2"))
        }
        if (AppUtils.ManualRpcRunning()) {
            LastPresence.experimental()?.let { configs.add(it) }
                ?: configs.add(RpcConfig(name = "Manual RPC", details = "Active"))
        }
        if (AppUtils.appDetectionRunning()) {
            configs.add(RpcConfig(name = "App Detection", details = "Detecting apps"))
        }

        if (configs.isNotEmpty()) configs
        else listOf(LastPresence.media() ?: LastPresence.experimental() ?: kizzyDefault())
    } catch (_: Exception) {
        listOf(kizzyDefault())
    }

    private fun kizzyDefault(name: String = "Kizzy") = RpcConfig(name = name, details = "Using $name")

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}