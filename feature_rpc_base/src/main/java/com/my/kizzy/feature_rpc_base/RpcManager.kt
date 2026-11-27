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
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RPC_INSTANCE_CUSTOM
import com.my.kizzy.preference.RPC_INSTANCE_MEDIA
import com.my.kizzy.preference.RPC_INSTANCE_MANUAL
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Centralized RPC state manager.
 *
 * All RPC screens observe this instead of maintaining their own local `remember`
 * state, so toggling one RPC on reliably closes all others and the UI reflects
 * the real running state across screens and page re-entries.
 *
 * Multiple RPC types can run simultaneously (parallel). Each service sends its
 * own presence to Discord independently. RpcManager tracks a presence config
 * per [RpcType] so the profile page can show every active RPC preview at once.
 *
 * Usage:
 *   RpcManager.getInstance(context).activeRpc.collect { rpcTypes ->
 *       // update UI switches based on which types are in the set
 *   }
 *   RpcManager.getInstance(context).presenceConfigs.collect { configs ->
 *       // configs is a Map<RpcType, RpcConfig> - one entry per active RPC
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

    /**
     * IDs of the home-screen instances that are currently enabled. This is the
     * fine-grained state: two instances of the same type can both be on, each
     * with its own gateway socket, so a Set<RpcType> is not enough.
     */
    private val _activeInstances = MutableStateFlow<Set<String>>(Prefs.getActiveRpcInstanceIds())
    val activeInstances: StateFlow<Set<String>> = _activeInstances.asStateFlow()

    /** Presence configs per instance ID, keyed by instanceId string.
     * Each clone gets its own entry - they run in parallel and do not
     * overwrite each other.
     */
    private val _presenceConfigs = MutableStateFlow<MutableMap<String, RpcConfig>>(mutableMapOf())
    val presenceConfigs: StateFlow<Map<String, RpcConfig>> = _presenceConfigs.asStateFlow()

    /** Backwards-compatible single-config accessor: the highest-priority non-empty config. */
    private val _singleConfig = _presenceConfigs
        .map { configs -> configs.values.firstOrNull() }
        .distinctUntilChanged()
    val currentPresenceConfig: StateFlow<RpcConfig?> = _singleConfig
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Registers the presence config for [instanceId] so the profile preview can
     * display it. Each instance keeps its own entry - they run in parallel
     * and do not overwrite each other.
     *
     * Called by each RPC service after it assembles its CommonRpc.
     */
    fun setCurrentPresenceConfig(config: RpcConfig, instanceId: String) {
        _presenceConfigs.value = _presenceConfigs.value.toMutableMap().apply {
            this[instanceId] = config
        }
    }

    /**
     * Removes the presence config for [rpcType] when its service stops.
     */
    fun removePresenceConfig(instanceId: String) {
        _presenceConfigs.value = _presenceConfigs.value.toMutableMap().apply {
            this.remove(instanceId)
        }
    }

    /**
     * Clears all presence configs (used on logout / full stop).
     */
    fun clearAllPresenceConfigs() {
        _presenceConfigs.value = mutableMapOf()
    }

    /**
     * Copies an [RpcConfig] to [Prefs.LAST_RUN_CUSTOM_RPC] so it becomes available
     * in the Custom RPC screen, without starting any RPC service.
     */
    fun copyToCustomRpc(config: RpcConfig) {
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = Json.encodeToString(config)
    }

    /** Stable id used in [activeInstances] for a built-in/legacy RPC type. */
    private fun instanceIdFor(type: RpcType): String = when (type) {
        RpcType.MediaRpc -> RPC_INSTANCE_MEDIA
        RpcType.CustomRpc -> RPC_INSTANCE_CUSTOM
        RpcType.ConsoleRpc -> "console_rpc"
        RpcType.ManualRpc -> RPC_INSTANCE_MANUAL
        RpcType.AppDetectionRpc -> "app_detection_rpc"
        RpcType.DiscordOAuth2Rpc -> "discord_oauth2_rpc"
        RpcType.None -> ""
    }

    /** Resolves an instance id back to its RPC type (clones included). */
    private fun typeFor(id: String): RpcType? = when (id) {
        RPC_INSTANCE_MEDIA -> RpcType.MediaRpc
        RPC_INSTANCE_CUSTOM -> RpcType.CustomRpc
        "console_rpc" -> RpcType.ConsoleRpc
        RPC_INSTANCE_MANUAL -> RpcType.ManualRpc
        "app_detection_rpc" -> RpcType.AppDetectionRpc
        "discord_oauth2_rpc" -> RpcType.DiscordOAuth2Rpc
        else -> when (Prefs.getHomeRpcInstances().firstOrNull { it.id == id }?.type) {
            "media_rpc" -> RpcType.MediaRpc
            "custom_rpc" -> RpcType.CustomRpc
            "manual_rpc" -> RpcType.ManualRpc
            else -> null
        }
    }

    /** Updates both the fine-grained instance set and the legacy type set. */
    private fun applyActive(ids: Set<String>) {
        val filtered = ids - ""
        _activeInstances.value = filtered
        _activeRpc.value = filtered.mapNotNull(::typeFor).toSet()
        Prefs.setActiveRpcInstanceIds(filtered)
    }

    init {
        // Seed from the persisted choice first: on a cold start the service is
        // not running yet, but the switch must still show the enabled RPC.
        val persistedIds = Prefs.getActiveRpcInstanceIds()
            .takeIf { it.isNotEmpty() }
            ?: persistedRpcTypes().map(::instanceIdFor).toSet()
        applyActive(persistedIds)
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
        // Right after a switch the services have not settled yet; keep the
        // state the user just selected instead of reading stale service info.
        if (System.currentTimeMillis() < ignoreServiceDetectionUntil) return

        val running = runningRpcTypes()

        // NEVER remove types from _activeRpc.value based on running services.
        // User toggles via switchTo()/stopRpcTypes() are the source of truth.
        // running may still report a just-stopped service due to ActivityManager's
        // 1-second cache, which would resurrect it incorrectly.
        val wasActive = _activeRpc.value

        // Self-heal: re-attach every enabled instance whose service is no longer
        // running. Each instance gets its own socket again after a cold start.
        val toStart = _activeInstances.value.filter { id ->
            val type = typeFor(id)
            type != null && type != RpcType.None && type !in running
        }
        for (id in toStart) {
            Log.d(TAG, "RPC instance $id is enabled but not running, restarting it")
            startInstanceService(id)
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
        val valid = if (tokenEmpty) types - RpcType.DiscordOAuth2Rpc else types
        if (valid != types) persistActiveRpcTypes(valid)

        applyActive(_activeInstances.value + valid.map(::instanceIdFor))
        val running = runningRpcTypes()
        for (id in _activeInstances.value) {
            val type = typeFor(id) ?: continue
            if (type in running) continue
            Log.d(TAG, "Restoring RPC instance $id after a cold start")
            startInstanceService(id)
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
        add: Boolean = true,
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

        val typesToStart = rpcTypes - setOf(RpcType.None)

        // Custom RPC and Console RPC share CustomRpcService, so they are mutually exclusive.
        val previousWithoutMutualExclusions = if (RpcType.ConsoleRpc in typesToStart) {
            previous - RpcType.CustomRpc
        } else if (RpcType.CustomRpc in typesToStart) {
            previous - RpcType.ConsoleRpc
        } else previous

        val finalTypes = if (add) previousWithoutMutualExclusions + typesToStart else typesToStart

        if (RpcType.CustomRpc in typesToStart) {
            Prefs[Prefs.LAST_RUN_CUSTOM_RPC] =
                rpcConfigString ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
        }
        if (RpcType.ConsoleRpc in typesToStart) {
            Prefs[Prefs.LAST_RUN_CONSOLE_RPC] =
                rpcConfigString ?: Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""]
        }
        // Persist the WHOLE set so every screen shows the same on/off state and
        // all enabled RPCs come back after a cold start.
        persistActiveRpcTypes(finalTypes)
        if (RpcType.DiscordOAuth2Rpc in typesToStart) {
            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
        }

        val idsToStart = typesToStart.map(::instanceIdFor).toSet()
        val excludedId = when {
            RpcType.ConsoleRpc in typesToStart -> RPC_INSTANCE_CUSTOM
            RpcType.CustomRpc in typesToStart -> "console_rpc"
            else -> null
        }
        val previousIds = _activeInstances.value
        val baseIds = if (add) previousIds - excludedId.orEmpty() else emptySet()
        applyActive(baseIds + idsToStart)

        for (rpcType in typesToStart) {
            startRpcService(rpcType, rpcConfigString, isConsoleRpc)
        }
        if (typesToStart.isEmpty()) Log.d(TAG, "All RPCs stopped")
    }

    /**
     * Convenience overload for switching a single RPC type.
     */
    fun switchTo(
        rpcType: RpcType,
        add: Boolean = true,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        switchTo(setOf(rpcType), add, rpcConfigString, isConsoleRpc)
    }

    /**
     * Enables a single home-screen RPC instance. The instance gets its own
     * gateway socket; other instances (including other instances of the same
     * type) are left untouched.
     */
    fun switchToInstance(
        instanceId: String,
        rpcType: RpcType,
        oauthIndex: Int = -1,
        config: String? = null,
    ) {
        if (instanceId.isBlank() || rpcType == RpcType.None) return
        // Built-ins use the same per-instance path as clones so that turning one
        // instance off never closes the sockets of the others.
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1500
        applyActive(_activeInstances.value + instanceId)
        persistActiveRpcTypes(_activeInstances.value.mapNotNull(::typeFor).toSet())
        // For Custom RPC clones, read the instance's own config from Prefs first,
        // so each clone keeps its own settings instead of sharing the built-in config.
        val effectiveConfig = if (rpcType == RpcType.CustomRpc) {
            // Read clone-specific config from Prefs first, so each clone keeps
            // its own settings instead of sharing the built-in config.
            Prefs.getCustomRpcConfig(instanceId.takeIf { it != RPC_INSTANCE_CUSTOM })
                .takeIf { it.isNotBlank() }
                ?: config
        } else {
            config
        }
        if (rpcType == RpcType.CustomRpc && effectiveConfig?.isNotBlank() == true) {
            Prefs[Prefs.customRpcConfigKey(instanceId.takeIf { it != RPC_INSTANCE_CUSTOM })] = effectiveConfig
        }
        startInstanceService(instanceId, effectiveConfig, oauthIndex)
    }

    /**
     * Disables a single instance. Its socket is closed without touching the
     * sockets of the other enabled instances.
     */
    fun stopInstance(instanceId: String, rpcType: RpcType) {
        if (instanceId.isBlank()) return
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1500
        val remaining = _activeInstances.value - instanceId
        applyActive(remaining)
        persistActiveRpcTypes(remaining.mapNotNull(::typeFor).toSet())

        // Remove this instance's presence config immediately so the profile
        // preview stops showing an RPC the user just switched off.
        removePresenceConfig(instanceId)

        val serviceClass = serviceClassFor(rpcType) ?: return
        val intent = Intent(context, serviceClass).apply {
            action = Constants.ACTION_REMOVE_INSTANCE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        }
        context.startService(intent)

        // When no instance of this type is left, tear the service down entirely.
        if (remaining.none { typeFor(it) == rpcType }) {
            context.stopService(Intent(context, serviceClass))
        }
    }

    /** Starts (or re-attaches) the service on behalf of one instance. */
    private fun startInstanceService(
        instanceId: String,
        config: String? = null,
        oauthIndex: Int = -1,
    ) {
        val rpcType = typeFor(instanceId) ?: return
        val instance = Prefs.getHomeRpcInstances().firstOrNull { it.id == instanceId }
        val intent = when (rpcType) {
            RpcType.MediaRpc -> Intent(context, MediaRpcService::class.java)
            RpcType.CustomRpc, RpcType.ConsoleRpc -> Intent(context, CustomRpcService::class.java).apply {
                // Clone-specific config takes priority so each clone keeps its
                // own settings instead of sharing the built-in config.
                val rpcData = Prefs.getCustomRpcConfig(instanceId.takeIf { it != "custom_rpc" })
                    ?: config
                    ?: instance?.config?.takeIf { it.isNotBlank() }
                    ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                putExtra("RPC", rpcData)
                putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, rpcType == RpcType.ConsoleRpc)
            }
            RpcType.ManualRpc -> Intent(context, ManualRpc::class.java)
            else -> null
        } ?: return

        val index = oauthIndex.takeIf { it > 0 }
            ?: instance?.oauthIndex?.takeIf { it > 0 }
            ?: defaultOauthIndex(rpcType)
        intent.action = Constants.ACTION_ADD_INSTANCE
        intent.putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        intent.putExtra(Constants.EXTRA_OAUTH_INDEX, index)
        // Every instance needs its own notification ID so multiple instances
        // of the same service type show separate foreground notifications.
        intent.putExtra(
            Constants.EXTRA_NOTIFICATION_ID,
            java.lang.Math.abs(instanceId.hashCode()),
        )
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start instance $instanceId", e)
            context.startService(intent)
        }
    }

    /** Stable notification ID for a built-in instance started via the legacy path. */
    private fun builtInNotificationId(rpcType: RpcType): Int =
        when (rpcType) {
            RpcType.MediaRpc -> Constants.NOTIFICATION_ID_MEDIA_RPC
            RpcType.CustomRpc, RpcType.ConsoleRpc -> Constants.NOTIFICATION_ID_CUSTOM_RPC
            RpcType.ManualRpc -> Constants.NOTIFICATION_ID_MANUAL_RPC
            RpcType.AppDetectionRpc -> Constants.NOTIFICATION_ID_APP_DETECTION
            RpcType.DiscordOAuth2Rpc -> Constants.NOTIFICATION_ID_OAUTH2_RPC
            RpcType.None -> Constants.NOTIFICATION_ID_MEDIA_RPC
        }

    private fun defaultOauthIndex(type: RpcType): Int = when (type) {
        RpcType.CustomRpc -> 2
        RpcType.ManualRpc -> 3
        else -> 1
    }

    private fun serviceClassFor(type: RpcType) = when (type) {
        RpcType.MediaRpc -> MediaRpcService::class.java
        RpcType.CustomRpc, RpcType.ConsoleRpc -> CustomRpcService::class.java
        RpcType.ManualRpc -> ManualRpc::class.java
        RpcType.AppDetectionRpc -> AppDetectionService::class.java
        RpcType.DiscordOAuth2Rpc -> DiscordOAuth2RpcService::class.java
        RpcType.None -> null
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
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ${intent.component?.className}", e)
            // Best-effort fallback start; ignore failures.
            if (runCatching { context.startService(intent) }.isFailure) {
            }
        }
    }

    /**
     * Stops the given RPC services.
     */
    fun stopRpcTypes(rpcTypes: Set<RpcType>) {
        val typesToStop = rpcTypes - setOf(RpcType.None)
        if (typesToStop.isEmpty()) return
        Log.d(TAG, "Stopping RPC services: $typesToStop")

        // Update state FIRST so the UI switch responds instantly. Service teardown
        // continues off the main thread below.
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1500
        val effectiveStops = if (typesToStop.any {
                it == RpcType.CustomRpc || it == RpcType.ConsoleRpc
            }
        ) typesToStop + setOf(RpcType.CustomRpc, RpcType.ConsoleRpc) else typesToStop
        val idsToStop = effectiveStops.map(::instanceIdFor).toSet()
        val remainingIds = _activeInstances.value - idsToStop
        persistActiveRpcTypes(remainingIds.mapNotNull(::typeFor).toSet())
        applyActive(remainingIds)

        // Remove presence configs for stopped types.
        for (rpcType in typesToStop) {
            removePresenceConfig(instanceIdFor(rpcType))
        }

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

        clearAllPresenceConfigs()
        persistActiveRpcTypes(emptySet())
        applyActive(emptySet())
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
    private fun persistedRpcTypes(): Set<RpcType> = activeRpcTypesFromPrefs()

    private fun activeRpcTypesFromPrefs(): Set<RpcType> {
        val raw = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
        val fromSet = raw.split(',')
            .mapNotNull { runCatching { RpcType.valueOf(it.trim()) }.getOrNull() }
            .toSet() - RpcType.None
        if (fromSet.isNotEmpty()) return fromSet
        return legacyRpcTypes()
    }

    private fun legacyRpcTypes(): Set<RpcType> {
        val types = mutableSetOf<RpcType>()
        val stored = Prefs[Prefs.ACTIVE_RPC_TYPE, ""]
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
    private fun restorableRpcTypes(): Set<RpcType> =
        activeRpcTypesFromPrefs().filterTo(mutableSetOf()) { type ->
            when (type) {
                // Custom/Console need a saved config or Discord drops the presence.
                RpcType.CustomRpc -> Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()
                RpcType.ConsoleRpc -> Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()
                else -> true
            }
        }

    /** Persists the complete active set; keeps legacy mirrors in sync. */
    private fun persistActiveRpcTypes(types: Set<RpcType>) {
        val filtered = types - RpcType.None
        Prefs[Prefs.ACTIVE_RPC_TYPES] = filtered.joinToString(",") { it.name }
        Prefs[Prefs.ACTIVE_RPC_TYPE] = filtered.firstOrNull()?.name.orEmpty()
        Prefs[Prefs.MEDIA_RPC_ENABLED] = RpcType.MediaRpc in filtered
        AppUtils.invalidateRunningServicesCache()
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