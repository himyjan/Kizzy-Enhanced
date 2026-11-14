/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcManager.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.DiscordOAuthRpcService
import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Centralized RPC state manager.
 *
 * All RPC screens observe this instead of maintaining their own local `remember`
 * state, so toggling one RPC on reliably closes all others and the UI reflects
 * the real running state across screens and page re-entries.
 *
 * Usage:
 *   RpcManager.getInstance(context).activeRpc.collect { rpc ->
 *       // update UI switch based on rpc
 *   }
 *   RpcManager.getInstance(context).switchTo(RpcType.MediaRpc)
 */
@Singleton
class RpcManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class RpcType {
        None,
        MediaRpc,
        CustomRpc,
        ConsoleRpc,
        ExperimentalRpc,
        AppDetectionRpc,
        DiscordOAuthRpc,
    }

    private val TAG = "RpcManager"

    /** Currently active RPC type. Observed by all RPC screens. */
    private val _activeRpc = MutableStateFlow(RpcType.None)
    val activeRpc: StateFlow<RpcType> = _activeRpc.asStateFlow()

    init {
        // Sync initial state from Android services on creation.
        refreshFromServices()
    }

    /**
     * Re-syncs the internal state from the Android services.
     * Call this when a screen becomes visible so the UI matches reality.
     */
    fun refreshFromServices() {
        val newType = when {
            AppUtils.mediaRpcRunning() -> RpcType.MediaRpc
            AppUtils.customRpcRunning() -> RpcType.CustomRpc
            AppUtils.consoleRpcRunning() -> RpcType.ConsoleRpc
            AppUtils.experimentalRpcRunning() -> RpcType.ExperimentalRpc
            AppUtils.appDetectionRunning() -> RpcType.AppDetectionRpc
            Prefs[Prefs.DISCORD_OAUTH_ACCESS_TOKEN, ""].isNotEmpty() && Prefs[Prefs.RPC_CONNECTION_METHOD, Prefs.RPC_CONNECTION_METHOD_GATEWAY] == Prefs.RPC_CONNECTION_METHOD_OAUTH -> RpcType.DiscordOAuthRpc
            else -> RpcType.None
        }
        if (_activeRpc.value != newType) {
            Log.d(TAG, "Synced RPC state: ${_activeRpc.value} -> $newType")
            _activeRpc.value = newType
        }
    }

    /**
     * Switches to the given RPC type.
     *
     * Stops all other RPC services first, then starts the requested one.
     * Logs the switch to logcat.
     *
     * @param rpcType The RPC type to switch to. Use [RpcType.None] to stop all.
     * @param rpcConfigString Optional serialized RpcConfig for Custom/Console RPC.
     * @param isConsoleRpc Whether the CustomRpcService should run in console mode.
     */
    fun switchTo(
        rpcType: RpcType,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        val previous = _activeRpc.value
        Log.d(TAG, "Switching RPC: $previous -> $rpcType")

        // Stop all RPC services first
        stopAllRpcServices()

        // Update prefs so AppUtils reflects the new state
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = if (rpcType == RpcType.CustomRpc) rpcConfigString else ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = if (rpcType == RpcType.ConsoleRpc) rpcConfigString else ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = if (rpcType == RpcType.MediaRpc) true else false
        // Only set connection method when explicitly switching to DiscordOAuthRpc.
        // Never reset to Gateway when switching away — the user's saved choice
        // (Gateway or OAuth2) must persist across RPC service switches.
        if (rpcType == RpcType.DiscordOAuthRpc) {
            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH
        }

        // Start the requested RPC (if not None)
        when (rpcType) {
            RpcType.None -> {
                Log.d(TAG, "All RPCs stopped")
            }
            RpcType.MediaRpc -> {
                Log.d(TAG, "Starting MediaRpcService")
                context.startService(Intent(context, MediaRpcService::class.java))
            }
            RpcType.CustomRpc -> {
                Log.d(TAG, "Starting CustomRpcService (custom mode)")
                val intent = Intent(context, CustomRpcService::class.java)
                intent.putExtra("RPC", rpcConfigString)
                intent.putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, false)
                context.startService(intent)
            }
            RpcType.ConsoleRpc -> {
                Log.d(TAG, "Starting CustomRpcService (console mode)")
                val intent = Intent(context, CustomRpcService::class.java)
                intent.putExtra("RPC", rpcConfigString)
                intent.putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, true)
                context.startService(intent)
            }
            RpcType.ExperimentalRpc -> {
                Log.d(TAG, "Starting ExperimentalRpc")
                context.startService(Intent(context, ExperimentalRpc::class.java))
            }
            RpcType.AppDetectionRpc -> {
                Log.d(TAG, "Starting AppDetectionService")
                context.startService(Intent(context, AppDetectionService::class.java))
            }
            RpcType.DiscordOAuthRpc -> {
                Log.d(TAG, "Starting DiscordOAuthRpcService")
                context.startService(Intent(context, DiscordOAuthRpcService::class.java))
            }
        }

        _activeRpc.value = rpcType
    }

    /**
     * Stops all RPC services and clears prefs.
     */
    private fun stopAllRpcServices() {
        Log.d(TAG, "Stopping all RPC services")
        context.stopService(Intent(context, MediaRpcService::class.java))
        context.stopService(Intent(context, CustomRpcService::class.java))
        context.stopService(Intent(context, ExperimentalRpc::class.java))
        context.stopService(Intent(context, AppDetectionService::class.java))
        context.stopService(Intent(context, DiscordOAuthRpcService::class.java))

        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = false
    }

    companion object {
        @Volatile
        private var instance: RpcManager? = null

        fun getInstance(context: Context): RpcManager {
            return instance ?: synchronized(this) {
                instance ?: RpcManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
