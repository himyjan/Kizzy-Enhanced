package com.my.kizzy.feature_rpc_base.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.feature_rpc_base.security.SecureTokenStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RpcViewModel @Inject constructor(
    private val tokenStorage: SecureTokenStorage
) : ViewModel() {

    /// Save Discord auth token securely
    fun saveToken(token: String) {
        tokenStorage.saveToken(token)
    }

    /// Retrieve stored Discord auth token
    fun getToken(): String? {
        return tokenStorage.getToken()
    }

    /// Clear stored token (e.g., on logout)
    fun clearToken() {
        tokenStorage.clearToken()
    }

    /// Check current connection state
    val isConnected: Boolean
        get() = false // WebSocket client not injected; set externally

    /// Observe connection state changes
    val connectionState = androidx.lifecycle.MutableLiveData<kizzy.gateway.ConnectionState>(kizzy.gateway.ConnectionState.Disconnected)

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
        // WebSocket client not injected; implement externally
    }
}
