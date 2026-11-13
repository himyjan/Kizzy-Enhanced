package com.my.kizzy.feature_profile.ui.component

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
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
@HiltViewModel
class RpcPreviewViewModel @Inject constructor(
    stateRepository: KizzyStateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val passedConfig: RpcConfig? = savedStateHandle.get<String>("rpcConfig")
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<RpcConfig>(raw)
            }.getOrNull()
        }

    val rpcConfig: StateFlow<RpcConfig?> = if (AppUtils.mediaRpcRunning()) {
        liveMediaConfig(stateRepository)
            .stateIn(viewModelScope, SharingStarted.Eagerly, passedConfig)
    } else {
        MutableStateFlow(passedConfig)
    }

    private fun liveMediaConfig(
        stateRepository: KizzyStateRepository,
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
                val base = passedConfig
                    ?: LastPresence.media()
                    ?: RpcConfig(name = "Media RPC", details = "Playing media", type = "2")
                base.copy(
                    name = base.name.copyIfNotEmpty(title ?: base.name),
                    details = base.details.copyIfNotEmpty(artist ?: base.details),
                    state = base.state.copyIfNotEmpty(artist ?: base.state),
                    timestampsStart = tsStart?.toString() ?: base.timestampsStart,
                    timestampsStop = tsStop?.toString() ?: base.timestampsStop,
                    largeImg = largeImage ?: base.largeImg,
                    smallImg = smallImage ?: base.smallImg,
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
