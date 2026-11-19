/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcManager.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.DiscordOAuth2RpcService
import com.my.kizzy.feature_rpc_base.services.ManualRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * The enabled RPC is persisted in [Prefs.ACTIVE_RPC_TYPE]. After a cold start
 * the state is restored from it before the service is up, so the switches show
 * the RPC the user had enabled instead of all being closed until the service
 * finishes starting.
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
        ManualRpc,
        AppDetectionRpc,
        DiscordOAuth2Rpc,
    }

    private val TAG = "RpcManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Until this timestamp the service list is not trusted for the active
     * state. `stopService` is asynchronous, so right after a switch the old
     * service can still be listed and would flip the switch back on.
     */
    @Volatile
    private var ignoreServiceDetectionUntil = 0L

    /** Currently active RPC type. Observed by all RPC screens. */
    private val _activeRpc = MutableStateFlow(RpcType.None)
    val activeRpc: StateFlow<RpcType> = _activeRpc.asStateFlow()

    init {
        // Seed from the persisted choice first: on a cold start the service is
        // not running yet, but the switch must still show the enabled RPC.
        _activeRpc.value = persistedRpcType()
        // Sync initial state from Android services on creation.
        refreshFromServices()
        // The preference mirror is primed asynchronously, so the first read may
        // have returned the defaults. Re-sync once the stored values are ready.
        scope.launch {
            Prefs.awaitInitialized()
            refreshFromServices()
        }
    }

    /**
     * Re-syncs the internal state from the Android services.
     * Call this when a screen becomes visible so the UI matches reality.
     *
     * When no service is running the persisted choice is kept instead of
     * reporting "all off": the service may still be starting after a cold
     * boot, and the switch has to stay where the user left it.
     */
    fun refreshFromServices() {
        val running = if (System.currentTimeMillis() < ignoreServiceDetectionUntil) {
            RpcType.None
        } else {
            runningRpcType()
        }
        val persisted = persistedRpcType()
        val newType = if (running != RpcType.None) running else persisted
        if (_activeRpc.value != newType) {
            Log.d(TAG, "Synced RPC state: ${_activeRpc.value} -> $newType")
            _activeRpc.value = newType
        }
        // Keep the persisted value in step with a service that was started
        // outside this manager (tile, notification listener, apps screen).
        if (running != RpcType.None && running != persisted) {
            Prefs[Prefs.ACTIVE_RPC_TYPE] = running.name
        }
        // Self-heal: the RPC is enabled but its service is not running. This
        // happens when the process was started in the background (where the
        // foreground service could not be launched) or after a service crash.
        // Screens call this while they are visible, so the start is allowed.
        val restorable = restorableRpcType()
        if (running == RpcType.None &&
            restorable != RpcType.None &&
            System.currentTimeMillis() >= ignoreServiceDetectionUntil
        ) {
            Log.d(TAG, "RPC $restorable is enabled but not running, starting it")
            startRpcService(restorable, rpcConfigString = null, isConsoleRpc = restorable == RpcType.ConsoleRpc)
        }
    }

    /**
     * Starts the RPC that was enabled before the process was killed.
     *
     * Called from the Application after the preferences have been primed. If
     * the service is already running this only restores the in-memory state.
     */
    fun restoreActiveRpc() {
        // Only restore an RPC the user really enabled. The OAuth2 fallback in
        // persistedRpcType() describes the connection method, not a toggle, so
        // it must not start a service on its own.
        val type = restorableRpcType()
        if (type == RpcType.None) return

        if (type == RpcType.DiscordOAuth2Rpc &&
            Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].isEmpty()
        ) {
            Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
            _activeRpc.value = RpcType.None
            return
        }

        if (runningRpcType() == type) {
            _activeRpc.value = type
            return
        }

        Log.d(TAG, "Restoring $type after a cold start")
        startRpcService(type, rpcConfigString = null, isConsoleRpc = type == RpcType.ConsoleRpc)
        _activeRpc.value = type
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
        // The services need a moment to start/stop; do not let their old
        // entries overwrite the state the user just selected.
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1500

        // Stop all RPC services first
        stopAllRpcServices()

        // Update prefs so AppUtils reflects the new state
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = if (rpcType == RpcType.CustomRpc) rpcConfigString else ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = if (rpcType == RpcType.ConsoleRpc) rpcConfigString else ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = rpcType == RpcType.MediaRpc
        Prefs[Prefs.ACTIVE_RPC_TYPE] = if (rpcType == RpcType.None) "" else rpcType.name
        // Only set connection method when explicitly switching to DiscordOAuth2Rpc.
        // Never reset to Gateway when switching away — the user's saved choice
        // (Gateway or OAuth2) must persist across RPC service switches.
        if (rpcType == RpcType.DiscordOAuth2Rpc) {
            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
        }

        // Start the requested RPC (if not None)
        if (rpcType == RpcType.None) {
            Log.d(TAG, "All RPCs stopped")
        } else {
            startRpcService(rpcType, rpcConfigString, isConsoleRpc)
        }

        _activeRpc.value = rpcType
    }

    /**
     * Starts the Android service that belongs to [rpcType].
     *
     * The foreground services have to be started with
     * `startForegroundService` on Android 8+, otherwise the system kills the
     * process when the service calls `startForeground`.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun startRpcService(
        rpcType: RpcType,
        rpcConfigString: String?,
        isConsoleRpc: Boolean,
    ) {
        when (rpcType) {
            RpcType.None -> Unit

            RpcType.MediaRpc -> {
                Log.d(TAG, "Starting MediaRpcService")
                startService(Intent(context, MediaRpcService::class.java), foreground = true)
            }

            RpcType.CustomRpc -> {
                Log.d(TAG, "Starting CustomRpcService (custom mode)")
                val intent = Intent(context, CustomRpcService::class.java)
                intent.putExtra(
                    "RPC",
                    rpcConfigString ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                )
                intent.putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, false)
                startService(intent, foreground = true)
            }

            RpcType.ConsoleRpc -> {
                Log.d(TAG, "Starting CustomRpcService (console mode)")
                val intent = Intent(context, CustomRpcService::class.java)
                intent.putExtra(
                    "RPC",
                    rpcConfigString ?: Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""]
                )
                intent.putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, true)
                startService(intent, foreground = true)
            }

            RpcType.ManualRpc -> {
                Log.d(TAG, "Starting ManualRpc")
                startService(Intent(context, ManualRpc::class.java), foreground = true)
            }

            RpcType.AppDetectionRpc -> {
                Log.d(TAG, "Starting AppDetectionService")
                startService(Intent(context, AppDetectionService::class.java), foreground = true)
            }

            RpcType.DiscordOAuth2Rpc -> {
                Log.d(TAG, "Starting DiscordOAuth2RpcService")
                // This service does not post a foreground notification, so it
                // must be started as a normal service.
                startService(Intent(context, DiscordOAuth2RpcService::class.java), foreground = false)
            }
        }
    }

    private fun startService(intent: Intent, foreground: Boolean) {
        try {
            if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ${intent.component?.className}", e)
            // A plain start still works while the app is in the foreground.
            runCatching { context.startService(intent) }
        }
    }

    /**
     * Stops all RPC services and clears prefs.
     */
    private fun stopAllRpcServices() {
        Log.d(TAG, "Stopping all RPC services")
        context.stopService(Intent(context, MediaRpcService::class.java))
        context.stopService(Intent(context, CustomRpcService::class.java))
        context.stopService(Intent(context, ManualRpc::class.java))
        context.stopService(Intent(context, AppDetectionService::class.java))
        context.stopService(Intent(context, DiscordOAuth2RpcService::class.java))

        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = false
        Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
        AppUtils.invalidateRunningServicesCache()
    }

    /** The RPC type whose Android service is running right now. */
    private fun runningRpcType(): RpcType = when {
        AppUtils.mediaRpcRunning() -> RpcType.MediaRpc
        AppUtils.customRpcRunning() -> RpcType.CustomRpc
        AppUtils.consoleRpcRunning() -> RpcType.ConsoleRpc
        AppUtils.ManualRpcRunning() -> RpcType.ManualRpc
        AppUtils.appDetectionRunning() -> RpcType.AppDetectionRpc
        else -> RpcType.None
    }

    /**
     * The RPC type the user enabled last, read from the preferences.
     *
     * Falls back to the older signals for users that updated from a version
     * without [Prefs.ACTIVE_RPC_TYPE].
     */
    private fun persistedRpcType(): RpcType {
        val restorable = restorableRpcType()
        if (restorable != RpcType.None) return restorable
        // OAuth2 is a connection method, not a toggle. It is still reported so
        // the screens know that a session exists.
        if (Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].isNotEmpty() &&
            Prefs[Prefs.RPC_CONNECTION_METHOD, Prefs.RPC_CONNECTION_METHOD_GATEWAY] ==
            Prefs.RPC_CONNECTION_METHOD_OAUTH2
        ) {
            return RpcType.DiscordOAuth2Rpc
        }
        return RpcType.None
    }

    /**
     * The RPC the user really enabled and that may be started again.
     *
     * Older versions only persisted [Prefs.MEDIA_RPC_ENABLED] and the
     * custom/console configs, so those are accepted as fallbacks.
     */
    private fun restorableRpcType(): RpcType {
        val stored = Prefs[Prefs.ACTIVE_RPC_TYPE, ""]
        if (stored.isNotEmpty()) {
            runCatching { RpcType.valueOf(stored) }.getOrNull()?.let { return it }
        }
        // The custom/console config is only kept while that RPC is active.
        if (Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()) return RpcType.ConsoleRpc
        if (Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()) return RpcType.CustomRpc
        if (Prefs[Prefs.MEDIA_RPC_ENABLED, false]) return RpcType.MediaRpc
        return RpcType.None
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
