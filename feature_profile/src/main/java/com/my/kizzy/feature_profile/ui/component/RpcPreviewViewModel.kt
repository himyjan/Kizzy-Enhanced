package com.my.kizzy.feature_profile.ui.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = RpcPreviewViewModel.Factory::class)
class RpcPreviewViewModel @AssistedInject constructor(
    stateRepository: KizzyStateRepository,
    rpcManager: RpcManager,
    @Assisted instanceId: String? = null,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(instanceId: String? = null): RpcPreviewViewModel
    }

    private val passedConfig: RpcConfig? = instanceId?.let { id ->
        rpcManager.presenceConfigs.value[id]
    }

    private val mediaState: Flow<MediaState> = combine(
        stateRepository.currentTrackTitle,
        stateRepository.currentArtist,
        stateRepository.isPaused,
        stateRepository.currentTimestampStart,
        stateRepository.currentTimestampStop,
    ) { title, artist, isPaused, tsStart, tsStop ->
        MediaState(title, artist, isPaused, tsStart, tsStop)
    }

    private val mediaImages: Flow<MediaImages> = combine(
        stateRepository.currentLargeImageUrl,
        stateRepository.currentSmallImageUrl,
        stateRepository.pausedPositionMs,
    ) { large, small, pausedPosition ->
        MediaImages(large, small, pausedPosition)
    }

    val rpcConfig: StateFlow<RpcConfig?> =
        combine(rpcManager.presenceConfigs, mediaState, mediaImages) { configs, media, images ->
            val fromService = instanceId?.let { configs[it] } ?: passedConfig
            val base = fromService
                ?: LastPresence.media()
                ?: LastPresence.experimental()
                ?: passedConfig
            if (base == null) return@combine null

            base.copy(
                details = base.details.copyIfNotEmpty(media.title),
                state = base.state.copyIfNotEmpty(media.artist),
                timestampsStart = media.tsStart?.toString() ?: base.timestampsStart,
                timestampsStop = media.tsStop?.toString() ?: base.timestampsStop,
                largeImg = images.large ?: base.largeImg,
                smallImg = images.small ?: base.smallImg,
                paused = media.isPaused,
                pausedPositionMs = images.pausedPosition,
            )
        }
            .distinctUntilChanged()
            .debounce(100.milliseconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class MediaState(
        val title: String?,
        val artist: String?,
        val isPaused: Boolean,
        val tsStart: Long?,
        val tsStop: Long?,
    )

    private data class MediaImages(
        val large: String?,
        val small: String?,
        val pausedPosition: Long?,
    )

    private fun String?.copyIfNotEmpty(newValue: String?): String {
        if (newValue?.isNotBlank() == true) return newValue
        return this ?: ""
    }
}