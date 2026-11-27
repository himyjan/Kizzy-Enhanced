package com.my.kizzy.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination keys for Kizzy.
 * Every key implements [NavKey] and is [@Serializable] so `rememberNavBackStack`
 * can save and restore the back stack across configuration changes and process death.
 */
@Serializable
sealed interface AppRoute : NavKey {

    // Setup / onboarding destination
    @Serializable
    data object Setup : AppRoute

    // Primary Home screen destination
    @Serializable
    data object Home : AppRoute

    // Media RPC screen; pass a non-null [instanceId] when opening a cloned instance
    @Serializable
    data class MediaRpc(val instanceId: String? = null) : AppRoute

    @Serializable
    data class MediaRpcInstance(val instanceId: String) : AppRoute

    // Manual RPC screen; pass a non-null [instanceId] when opening a cloned instance
    @Serializable
    data class ManualRpc(val instanceId: String? = null) : AppRoute

    @Serializable
    data class ManualRpcInstance(val instanceId: String) : AppRoute

    // Custom RPC screen; pass a non-null [instanceId] when opening a cloned instance
    @Serializable
    data class CustomRpc(val instanceId: String? = null) : AppRoute

    @Serializable
    data class CustomRpcInstance(val instanceId: String) : AppRoute

    // Settings drawer and top-bar destinations
    @Serializable
    data object Profile : AppRoute

    @Serializable
    data object StyleAndAppearance : AppRoute

    @Serializable
    data object Languages : AppRoute

    @Serializable
    data object About : AppRoute

    @Serializable
    data object RpcSettings : AppRoute

    @Serializable
    data object LogsScreen : AppRoute

    @Serializable
    data object AppsDetection : AppRoute

    @Serializable
    data object DarkTheme : AppRoute

    @Serializable
    data object CustomRpcBoard : AppRoute

    @Serializable
    data object Leaderboard : AppRoute

    @Serializable
    data object Credits : AppRoute

    @Serializable
    data object ManualRpcApps : AppRoute

    @Serializable
    data class RpcPreview(
        val userId: String,
        val avatarUrl: String,
        val rpcConfig: String,
    ) : AppRoute
}