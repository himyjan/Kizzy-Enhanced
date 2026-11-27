package com.my.kizzy.feature_profile.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

/**
 * Live state for the RPC preview.
 *
 * The preview receives its [RpcConfig] through the navigation route, so the route
 * only carries a snapshot taken when the page was opened. The media service keeps
 * publishing after that: the next song, a seek and the pause state all change
 * without touching the route. For a running Media RPC the fields are rebuilt from
 * the state repository so the preview follows the current track (and freezes when
 * the music is paused). Every other RPC type keeps using the route snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = RpcPreviewViewModel.Factory::class)
class RpcPreviewViewModel @AssistedInject constructor(
    private val stateRepository: KizzyStateRepository,
    @Assisted rawConfig: String,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(rawConfig: String): RpcPreviewViewModel
    }

    private val passedConfig: RpcConfig? = rawConfig.takeIf { it.isNotBlank() }
        ?.let { value ->
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<RpcConfig>(value)
            }.getOrNull()
        }

    val rpcConfig: StateFlow<RpcConfig?> = if (AppUtils.mediaRpcRunning()) {
        liveMediaConfig(passedConfig)
            .stateIn(viewModelScope, SharingStarted.Eagerly, passedConfig)
    } else {
        MutableStateFlow(passedConfig)
    }

    private fun liveMediaConfig(
        base: RpcConfig?,
    ): Flow<RpcConfig> =
        combine(
            stateRepository.currentTrackTitle,
            stateRepository.currentArtist,
            stateRepository.isPaused,
            stateRepository.currentTimestampStart,
            stateRepository.currentTimestampStop,
        ) { title, artist, isPaused, tsStart, tsStop ->
            combine(
                stateRepository.currentLargeImageUrl,
                stateRepository.currentSmallImageUrl,
                stateRepository.pausedPositionMs,
            ) { largeImage, smallImage, pausedPositionMs ->
                val resolvedBase = base
                    ?: LastPresence.media()
                    ?: RpcConfig(name = "Media RPC", details = "Playing media", type = "2")
                resolvedBase.copy(
                    name = resolvedBase.name.copyIfNotEmpty(title ?: resolvedBase.name),
                    details = resolvedBase.details.copyIfNotEmpty(artist ?: resolvedBase.details),
                    state = resolvedBase.state.copyIfNotEmpty(artist ?: resolvedBase.state),
                    timestampsStart = tsStart?.toString() ?: resolvedBase.timestampsStart,
                    timestampsStop = tsStop?.toString() ?: resolvedBase.timestampsStop,
                    largeImg = largeImage ?: resolvedBase.largeImg,
                    smallImg = smallImage ?: resolvedBase.smallImg,
                    paused = isPaused,
                    pausedPositionMs = pausedPositionMs,
                )
            }
        }.flatMapLatest { it }

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}
