package com.my.kizzy.feature_rpc_base.security

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class KizzyStateRepository @Inject constructor() {

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentTrackTitle = MutableStateFlow<String?>(null)
    val currentTrackTitle: StateFlow<String?> = _currentTrackTitle.asStateFlow()

    private val _currentArtist = MutableStateFlow<String?>(null)
    val currentArtist: StateFlow<String?> = _currentArtist.asStateFlow()

    private val _currentAlbum = MutableStateFlow<String?>(null)
    val currentAlbum: StateFlow<String?> = _currentAlbum.asStateFlow()

    private val _currentTimestampStart = MutableStateFlow<Long?>(null)
    val currentTimestampStart: StateFlow<Long?> = _currentTimestampStart.asStateFlow()

    private val _currentTimestampStop = MutableStateFlow<Long?>(null)
    val currentTimestampStop: StateFlow<Long?> = _currentTimestampStop.asStateFlow()

    private val _currentLargeImageUrl = MutableStateFlow<String?>(null)
    val currentLargeImageUrl: StateFlow<String?> = _currentLargeImageUrl.asStateFlow()

    private val _currentSmallImageUrl = MutableStateFlow<String?>(null)
    val currentSmallImageUrl: StateFlow<String?> = _currentSmallImageUrl.asStateFlow()

    /**
     * The position (in milliseconds) at which playback was paused.
     *
     * The preview used to derive the elapsed time from the wall clock, so a
     * paused track still showed a running timeline. Publishing the frozen
     * position lets the UI stop the timer exactly where the track stopped.
     */
    private val _pausedPositionMs = MutableStateFlow<Long?>(null)
    val pausedPositionMs: StateFlow<Long?> = _pausedPositionMs.asStateFlow()

    fun setPaused(paused: Boolean) {
        _isPaused.update { paused }
    }

    fun setConnected(connected: Boolean) {
        _isConnected.update { connected }
    }

    fun setTrackInfo(title: String?, artist: String?, album: String?) {
        _currentTrackTitle.update { title }
        _currentArtist.update { artist }
        _currentAlbum.update { album }
    }

    fun setTimestamps(start: Long?, stop: Long?) {
        _currentTimestampStart.update { start }
        _currentTimestampStop.update { stop }
    }

    fun setImageUrls(large: String?, small: String?) {
        _currentLargeImageUrl.update { large }
        _currentSmallImageUrl.update { small }
    }

    fun setPausedPosition(positionMs: Long?) {
        _pausedPositionMs.update { positionMs }
    }

    fun clearTrackInfo() {
        setTrackInfo(null, null, null)
        setTimestamps(null, null)
        setImageUrls(null, null)
        setPausedPosition(null)
    }
}