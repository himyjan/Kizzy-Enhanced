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
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.DiscordOAuth2RpcService
import com.my.kizzy.feature_rpc_base.services.ManualRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RPC_INSTANCE_CUSTOM
import com.my.kizzy.preference.RPC_INSTANCE_MANUAL
import com.my.kizzy.preference.RPC_INSTANCE_MEDIA
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Centralized RPC state coordinator for built-in and home-screen clone RPC instances.
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Until this timestamp the service list is not trusted for the active
     * state. `stopService` is asynchronous, so right after a switch the old
     * service can still be listed and would flip the switch back on.
     */
    @Volatile
    private var ignoreServiceDetectionUntil = 0L

    private val _activeRpc = MutableStateFlow<Set<RpcType>>(emptySet())
    val activeRpc: StateFlow<Set<RpcType>> = _activeRpc.asStateFlow()

    private val _activeInstances = MutableStateFlow<Set<String>>(Prefs.getActiveRpcInstanceIds())
    val activeInstances: StateFlow<Set<String>> = _activeInstances.asStateFlow()

    private val _presenceConfigs = MutableStateFlow(emptyMap<String, RpcConfig>())
    val presenceConfigs = _presenceConfigs.asStateFlow()

    private val _singleConfig = _presenceConfigs
        .map { configs -> configs.values.firstOrNull() }
        .distinctUntilChanged()
    val currentPresenceConfig: StateFlow<RpcConfig?> = _singleConfig
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun setCurrentPresenceConfig(config: RpcConfig, instanceId: String) {
        if (instanceId.isBlank()) return
        _presenceConfigs.update { current -> current + (instanceId to config) }
    }

    fun removePresenceConfig(instanceId: String) {
        if (instanceId.isBlank()) return
        _presenceConfigs.update { current -> current - instanceId }
    }

    fun clearAllPresenceConfigs() {
        _presenceConfigs.value = emptyMap()
    }

    fun copyToCustomRpc(config: RpcConfig) {
        Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = json.encodeToString(config)
    }

    private fun instanceIdFor(type: RpcType): String = when (type) {
        RpcType.MediaRpc -> RPC_INSTANCE_MEDIA
        RpcType.CustomRpc -> RPC_INSTANCE_CUSTOM
        RpcType.ConsoleRpc -> "console_rpc"
        RpcType.ManualRpc -> RPC_INSTANCE_MANUAL
        RpcType.AppDetectionRpc -> "app_detection_rpc"
        RpcType.DiscordOAuth2Rpc -> "discord_oauth2_rpc"
        RpcType.None -> ""
    }

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

    private fun applyActive(ids: Set<String>) {
        val filtered = ids - ""
        _activeInstances.value = filtered
        _activeRpc.value = filtered.mapNotNull(::typeFor).toSet()
        Prefs.setActiveRpcInstanceIds(filtered)
    }

    init {
        val persistedIds = Prefs.getActiveRpcInstanceIds()
            .takeIf { it.isNotEmpty() }
            ?: persistedRpcTypes().map(::instanceIdFor).toSet()
        applyActive(persistedIds)
        refreshFromServices()
        scope.launch {
            try {
                Prefs.awaitInitialized()
                refreshFromServices()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore preference priming failures on startup.
            }
        }
    }

    fun refreshFromServices() {
        if (System.currentTimeMillis() < ignoreServiceDetectionUntil) return

        val running = runningRpcTypes()
        val toStart = _activeInstances.value.filter { id ->
            val type = typeFor(id)
            type != null && type != RpcType.None && type !in running
        }
        for (id in toStart) {
            Log.d(TAG, "RPC instance $id is enabled but not running, restarting it")
            startInstanceService(id)
        }
    }

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

    fun switchTo(
        rpcTypes: Set<RpcType>,
        add: Boolean = true,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        val previous = _activeRpc.value
        Log.d(TAG, "Switching RPC: $previous -> $rpcTypes (add=$add)")

        if (!add) {
            stopAllRpcServices()
            _activeRpc.value = emptySet()
        } else {
            ignoreServiceDetectionUntil = System.currentTimeMillis() + 1_500L
        }

        val typesToStart = rpcTypes - setOf(RpcType.None)

        val previousWithoutMutualExclusions = when {
            RpcType.ConsoleRpc in typesToStart -> previous - RpcType.CustomRpc
            RpcType.CustomRpc in typesToStart -> previous - RpcType.ConsoleRpc
            else -> previous
        }

        val finalTypes = if (add) previousWithoutMutualExclusions + typesToStart else typesToStart

        if (RpcType.CustomRpc in typesToStart) {
            Prefs[Prefs.LAST_RUN_CUSTOM_RPC] =
                rpcConfigString ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
        }
        if (RpcType.ConsoleRpc in typesToStart) {
            Prefs[Prefs.LAST_RUN_CONSOLE_RPC] =
                rpcConfigString ?: Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""]
        }

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

    fun switchTo(
        rpcType: RpcType,
        add: Boolean = true,
        rpcConfigString: String? = null,
        isConsoleRpc: Boolean = false
    ) {
        switchTo(setOf(rpcType), add, rpcConfigString, isConsoleRpc)
    }

    fun switchToInstance(
        instanceId: String,
        rpcType: RpcType,
        oauthIndex: Int = -1,
        config: String? = null,
    ) {
        if (instanceId.isBlank() || rpcType == RpcType.None) return
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1_500L
        applyActive(_activeInstances.value + instanceId)
        persistActiveRpcTypes(_activeInstances.value.mapNotNull(::typeFor).toSet())

        val effectiveConfig = if (rpcType == RpcType.CustomRpc) {
            Prefs.getCustomRpcConfig(instanceId.takeIf { it != RPC_INSTANCE_CUSTOM })
                .takeIf { it.isNotBlank() }
                ?: config
        } else {
            config
        }
        if (rpcType == RpcType.CustomRpc && !effectiveConfig.isNullOrBlank()) {
            Prefs[Prefs.customRpcConfigKey(instanceId.takeIf { it != RPC_INSTANCE_CUSTOM })] = effectiveConfig
        }
        startInstanceService(instanceId, effectiveConfig, oauthIndex)
    }

    fun stopInstance(instanceId: String, rpcType: RpcType) {
        if (instanceId.isBlank()) return
        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1_500L
        val remaining = _activeInstances.value - instanceId
        applyActive(remaining)
        persistActiveRpcTypes(remaining.mapNotNull(::typeFor).toSet())

        removePresenceConfig(instanceId)

        val serviceClass = serviceClassFor(rpcType) ?: return
        val intent = Intent(context, serviceClass).apply {
            action = Constants.ACTION_REMOVE_INSTANCE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch remove-instance intent for $instanceId", e)
        }

        if (remaining.none { typeFor(it) == rpcType }) {
            context.stopService(Intent(context, serviceClass))
        }
    }

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
        intent.putExtra(
            Constants.EXTRA_NOTIFICATION_ID,
            abs(instanceId.hashCode()),
        )
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground instance $instanceId", e)
            runCatching { context.startService(intent) }
        }
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
            runCatching { context.startService(intent) }
        }
    }

    fun stopRpcTypes(rpcTypes: Set<RpcType>) {
        val typesToStop = rpcTypes - setOf(RpcType.None)
        if (typesToStop.isEmpty()) return
        Log.d(TAG, "Stopping RPC services: $typesToStop")

        ignoreServiceDetectionUntil = System.currentTimeMillis() + 1_500L
        val effectiveStops = if (typesToStop.any {
                it == RpcType.CustomRpc || it == RpcType.ConsoleRpc
            }
        ) typesToStop + setOf(RpcType.CustomRpc, RpcType.ConsoleRpc) else typesToStop
        val idsToStop = effectiveStops.map(::instanceIdFor).toSet()
        val remainingIds = _activeInstances.value - idsToStop
        persistActiveRpcTypes(remainingIds.mapNotNull(::typeFor).toSet())
        applyActive(remainingIds)

        for (rpcType in typesToStop) {
            removePresenceConfig(instanceIdFor(rpcType))
        }

        if (typesToStop.any { it == RpcType.CustomRpc || it == RpcType.ConsoleRpc }) {
            Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = ""
            Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
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

    private fun runningRpcTypes(): Set<RpcType> = buildSet {
        if (AppUtils.mediaRpcRunning()) add(RpcType.MediaRpc)
        if (AppUtils.customRpcRunning()) add(RpcType.CustomRpc)
        if (AppUtils.consoleRpcRunning()) add(RpcType.ConsoleRpc)
        if (AppUtils.ManualRpcRunning()) add(RpcType.ManualRpc)
        if (AppUtils.appDetectionRunning()) add(RpcType.AppDetectionRpc)
    }

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
        if (Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()) types.add(RpcType.ConsoleRpc)
        if (Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()) types.add(RpcType.CustomRpc)
        if (Prefs[Prefs.MEDIA_RPC_ENABLED, false]) types.add(RpcType.MediaRpc)
        return types
    }

    private fun restorableRpcTypes(): Set<RpcType> =
        activeRpcTypesFromPrefs().filterTo(mutableSetOf()) { type ->
            when (type) {
                RpcType.CustomRpc -> Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty()
                RpcType.ConsoleRpc -> Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty()
                else -> true
            }
        }

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