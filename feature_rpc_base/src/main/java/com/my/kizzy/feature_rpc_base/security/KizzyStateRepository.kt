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

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }
}
