package com.my.kizzy.feature_rpc_base.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun setTrackInfo(title: String?, artist: String?, album: String?) {
        _currentTrackTitle.value = title
        _currentArtist.value = artist
        _currentAlbum.value = album
    }

    fun setTimestamps(start: Long?, stop: Long?) {
        _currentTimestampStart.value = start
        _currentTimestampStop.value = stop
    }

    fun setImageUrls(large: String?, small: String?) {
        _currentLargeImageUrl.value = large
        _currentSmallImageUrl.value = small
    }

    fun clearTrackInfo() {
        setTrackInfo(null, null, null)
        setTimestamps(null, null)
        setImageUrls(null, null)
    }
}
