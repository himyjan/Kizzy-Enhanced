package com.my.kizzy.feature_rpc_base.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.security.SecureTokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RpcViewModel @Inject constructor(
    private val tokenStorage: SecureTokenStorage,
    private val stateRepository: KizzyStateRepository
) : ViewModel() {

    /// Expose token reactively as a StateFlow so the UI and WebSocket can observe changes
    val authToken: StateFlow<String?> = tokenStorage.tokenFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    /// Loading state while reading from secure storage
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    /// Connection state (delegated to shared repository)
    val isConnected: StateFlow<Boolean> = stateRepository.isConnected

    /// Pause state (delegated to shared repository)
    val isPaused: StateFlow<Boolean> = stateRepository.isPaused

    /// Current track title (delegated to shared repository)
    val currentTrackTitle: StateFlow<String?> = stateRepository.currentTrackTitle

    /// Current track artist (delegated to shared repository)
    val currentArtist: StateFlow<String?> = stateRepository.currentArtist

    /// Current track album (delegated to shared repository)
    val currentAlbum: StateFlow<String?> = stateRepository.currentAlbum

    init {
        viewModelScope.launch {
            // Wait for initial token flow emission to determine loading state
            tokenStorage.tokenFlow.first()
            _isLoading.value = false
        }
    }

    /// Save Discord auth token securely
    fun saveToken(token: String) {
        viewModelScope.launch {
            tokenStorage.saveToken(token)
        }
    }

    /// Clear stored token (e.g., on logout)
    fun clearToken() {
        viewModelScope.launch {
            tokenStorage.clearToken()
        }
    }

    /// Update Discord Rich Presence
    suspend fun updatePresence(
        name: String,
        details: String? = null,
        state: String? = null,
        largeImageKey: String? = null,
        largeText: String? = null,
        smallImageKey: String? = null,
        smallText: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        status: String = "online",
        afk: Boolean = false
    ) {
        // WebSocket client not injected; implement externally via stateRepository
    }
}
