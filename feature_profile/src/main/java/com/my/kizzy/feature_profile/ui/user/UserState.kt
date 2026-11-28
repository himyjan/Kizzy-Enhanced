package com.my.kizzy.feature_profile.ui.user

import androidx.compose.runtime.Immutable
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User

@Immutable
sealed interface UserState {
    data object Loading : UserState
    data class Error(val error: String, val user: User?, val rpcConfigs: List<RpcConfig>) : UserState
    data class LoadingCompleted(val user: User?, val rpcConfigs: List<RpcConfig>) : UserState
}