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
 * Multiple RPC types can be active at the same time - each service sends its
 * own presence to Discord independently. The last presence sent wins on
 * Discord's side (Discord only shows one presence per user).
 *
 * Usage:
 *   RpcManager.getInstance(context).activeRpc.collect { rpcTypes ->
 *       // update UI switches based on which types are in the set
 *   }
 *   RpcManager.getInstance(context).switchTo(RpcType.MediaRpc, add = true)
 *   RpcManager.getInstance(context).switchTo(RpcType.None) // stop all
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

    /** Currently active RPC types. Observed by all RPC screens. */
    private val _activeRpc = MutableStateFlow<Set<RpcType>>(emptySet())
    val activeRpc: StateFlow<Set<RpcType>> = _activeRpc.asStateFlow()

    init {
        // Seed from the persisted choice first: on a cold start the service is
        // not running yet, but the switch must still show the enabled RPC.
        _activeRpc.value = persistedRpcTypes()
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
     */
    fun refreshFromServices() {
        val running = if (System.currentTimeMillis() < ignoreServiceDetectionUntil) {
            emptySet<RpcType>()
        } else {
            runningRpcTypes()
        }
        val persisted = persistedRpcTypes()
        val newTypes = if (running.isNotEmpty()) running else persisted
        if (_activeRpc.value != newTypes) {
            Log.d(TAG, "Synced RPC state: ${_activeRpc.value} -> $newTypes")
            _activeRpc.value = newTypes
        }
        // Keep the persisted value in step with services that were started
        // outside this manager (tile, notification listener, apps screen).
        for (rpcType in running) {
            if (rpcType != RpcType.None && rpcType !in persisted) {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = rpcType.name
            }
        }
        // Self-heal: RPCs are enabled but their services are not running.
        val restorable = restorableRpcTypes()
        val toStart = restorable.filter { it !in _activeRpc.value &&
            System.currentTimeMillis() >= ignoreServiceDetectionUntil }
        for (rpcType in toStart) {
            Log.d(TAG, "RPC $rpcType is enabled but not running, starting it")
            startRpcService(rpcType, rpcConfigString = null, isConsoleRpc = rpcType == RpcType.ConsoleRpc)
        }
    }

    /**
     * Starts the RPCs that were enabled before the process was killed.
     *
     * Called from the Application after the preferences have been primed.
     */
    fun restoreActiveRpc() {
        val types = restorableRpcTypes()
        val tokenEmpty = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].isEmpty()
        for (type in types) {
            if (type == RpcType.None) continue
            if (type == RpcType.DiscordOAuth2Rpc && tokenEmpty) {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
                continue
            }
            if (type in runningRpcTypes()) {
                _activeRpc.value = _activeRpc.value + type
                continue
            }
            Log.d(TAG, "Restoring $type after a cold start")
            startRpcService(type, rpcConfigString = null, isConsoleRpc = type == RpcType.ConsoleRpc)
            _activeRpc.value = _activeRpc.value + type
        }
    }

    /**
     * Switches the given RPC types.
     *
     * When [add] is true, the given types are added to the currently active set
     * (multiple RPCs can run simultaneously). When [add] is false, all other
     * RPCs are stopped first and only the given types are started.
     *
     * @param rpcTypes The RPC types to switch to.
     * @param add When true, adds to the current set instead of replacing it.
     * @param rpcConfigString Optional serialized RpcConfig for Custom/Console RPC.
     * @param isConsoleRpc Whether the CustomRpcService should run in console mode.
     */
    fun switchTo(
        rpcTypes: Set<RpcType>,
        add: Boolean = false,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        val previous = _activeRpc.value
        Log.d(TAG, "Switching RPC: $previous -> $rpcTypes (add=$add)")

        if (!add) {
            // Stop all RPC services first
            stopAllRpcServices()
            _activeRpc.value = emptySet()
        } else {
            // The services need a moment to start; do not let their old
            // entries overwrite the state the user just selected.
            ignoreServiceDetectionUntil = System.currentTimeMillis() + 1500
        }

        // Update prefs so AppUtils reflects the new state
        val customRpc = rpcTypes.filter { it == RpcType.CustomRpc || it == RpcType.ConsoleRpc }
        val mediaRpc = RpcType.MediaRpc in rpcTypes
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = if (customRpc.any { it == RpcType.CustomRpc }) rpcConfigString else ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = if (customRpc.any { it == RpcType.ConsoleRpc }) rpcConfigString else ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = mediaRpc
        Prefs[Prefs.ACTIVE_RPC_TYPE] = if (rpcTypes.isEmpty() || rpcTypes == setOf(RpcType.None)) ""
        else rpcTypes.first { it != RpcType.None }.name
        // Only set connection method when explicitly switching to DiscordOAuth2Rpc.
        if (RpcType.DiscordOAuth2Rpc in rpcTypes) {
            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
        }

        // Start the requested RPCs (if not None)
        val typesToStart = rpcTypes - setOf(RpcType.None)
        if (typesToStart.isNotEmpty()) {
            for (rpcType in typesToStart) {
                startRpcService(rpcType, rpcConfigString, isConsoleRpc)
            }
        } else {
            Log.d(TAG, "All RPCs stopped")
        }

        _activeRpc.value = rpcTypes
    }

    /**
     * Convenience overload for switching a single RPC type.
     */
    fun switchTo(
        rpcType: RpcType,
        add: Boolean = false,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        switchTo(setOf(rpcType), add, rpcConfigString, isConsoleRpc)
    }

    /**
     * Starts the Android service that belongs to [rpcType].
     */
    private fun startRpcService(
        rpcType: RpcType,
        rpcConfigString: String?,
        isConsoleRpc: Boolean,
    ) {
        val intent = when (rpcType) {
            RpcType.MediaRpc -> {
                Log.d(TAG, "Starting MediaRpcService")
                Intent(context, MediaRpcService::class.java)
            }
            RpcType.CustomRpc -> {
                Log.d(TAG, "Starting CustomRpcService (custom mode)")
                Intent(context, CustomRpcService::class.java).apply {
                    putExtra(
                        "RPC",
                        rpcConfigString ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                    )
                    putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, false)
                }
            }
            RpcType.ConsoleRpc -> {
                Log.d(TAG, "Starting CustomRpcService (console mode)")
                Intent(context, CustomRpcService::class.java).apply {
                    putExtra(
                        "RPC",
                        rpcConfigString ?: Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""]
                    )
                    putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, true)
                }
            }
            RpcType.ManualRpc -> {
                Log.d(TAG, "Starting ManualRpc")
                Intent(context, ManualRpc::class.java)
            }
            RpcType.AppDetectionRpc -> {
                Log.d(TAG, "Starting AppDetectionService")
                Intent(context, AppDetectionService::class.java)
            }
            RpcType.DiscordOAuth2Rpc -> {
                Log.d(TAG, "Starting DiscordOAuth2RpcService")
                Intent(context, DiscordOAuth2RpcService::class.java)
            }
            RpcType.None -> return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ${intent.component?.className}", e)
            runCatching { context.startService(intent) }
        }
    }

    /**
     * Stops the given RPC services.
     */
    fun stopRpcTypes(rpcTypes: Set<RpcType>) {
        val typesToStop = rpcTypes - setOf(RpcType.None)
        if (typesToStop.isEmpty()) return
        Log.d(TAG, "Stopping RPC services: $typesToStop")
        for (rpcType in typesToStop.toList()) {
            when (rpcType) {
                RpcType.MediaRpc -> context.stopService(Intent(context, MediaRpcService::class.java))
                RpcType.CustomRpc, RpcType.ConsoleRpc ->
                    context.stopService(Intent(context, CustomRpcService::class.java))
                RpcType.ManualRpc -> context.stopService(Intent(context, ManualRpc::class.java))
                RpcType.AppDetectionRpc ->
                    context.stopService(Intent(context, AppDetectionService::class.java))
                RpcType.DiscordOAuth2Rpc ->
                    context.stopService(Intent(context, DiscordOAuth2RpcService::class.java))
                RpcType.None -> Unit
            }
        }
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = ""
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
        Prefs[Prefs.MEDIA_RPC_ENABLED] = false
        Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
        AppUtils.invalidateRunningServicesCache()
        _activeRpc.value = _activeRpc.value - typesToStop
    }

    /**
     * Stops all RPC services and clears prefs.
     */
    fun stopAllRpcServices() {
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
        _activeRpc.value = emptySet()
    }

    /** The RPC types whose Android services are running right now. */
    private fun runningRpcTypes(): Set<RpcType> = buildSet {
        if (AppUtils.mediaRpcRunning()) add(RpcType.MediaRpc)
        if (AppUtils.customRpcRunning()) add(RpcType.CustomRpc)
        if (AppUtils.consoleRpcRunning()) add(RpcType.ConsoleRpc)
        if (AppUtils.ManualRpcRunning()) add(RpcType.ManualRpc)
        if (AppUtils.appDetectionRunning()) add(RpcType.AppDetectionRpc)
    }

    /**
     * The RPC types the user enabled last, read from the preferences.
     */
    private fun persistedRpcTypes(): Set<RpcType> {
        val stored = Prefs[Prefs.ACTIVE_RPC_TYPE, ""]
        val types = mutableSetOf<RpcType>()
        if (stored.isNotEmpty()) {
            runCatching { RpcType.valueOf(stored) }.getOrNull()?.let { types.add(it) }
        }
        // Users that update from a version without ACTIVE_RPC_TYPE:
        if (Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()) types.add(RpcType.ConsoleRpc)
        if (Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()) types.add(RpcType.CustomRpc)
        if (Prefs[Prefs.MEDIA_RPC_ENABLED, false]) types.add(RpcType.MediaRpc)
        return types
    }

    /**
     * The RPC types the user really enabled and that may be started again.
     */
    private fun restorableRpcTypes(): Set<RpcType> {
        val types = mutableSetOf<RpcType>()
        val stored = Prefs[Prefs.ACTIVE_RPC_TYPE, ""]
        if (stored.isNotEmpty()) {
            runCatching { RpcType.valueOf(stored) }.getOrNull()?.let { types.add(it) }
        }
        if (Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()) types.add(RpcType.ConsoleRpc)
        if (Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()) types.add(RpcType.CustomRpc)
        if (Prefs[Prefs.MEDIA_RPC_ENABLED, false]) types.add(RpcType.MediaRpc)
        return types
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
