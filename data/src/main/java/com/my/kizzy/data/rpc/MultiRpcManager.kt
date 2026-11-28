package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.OAuthAppCredential
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.DEFAULT_OAUTH_APP_ID_1
import com.my.kizzy.preference.Prefs.DEFAULT_OAUTH_APP_ID_2
import com.my.kizzy.preference.Prefs.DEFAULT_OAUTH_APP_ID_3
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kizzy.gateway.DiscordWebSocketImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns Discord gateway connections used by every RPC instance.
 *
 * Discord Gateway allows only ONE WebSocket connection per user token.
 * Multiple sockets with the same token kill each other with INVALID_SESSION,
 * causing infinite reconnect loops. Each RPC instance therefore gets its own
 * socket keyed by token plus instance id.
 */
@Singleton
class MultiRpcManager @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger,
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.IO + job

    private data class RpcGroup(
        val slotId: String,
        val instances: MutableMap<String, KizzyRPC> = LinkedHashMap(),
    )

    private val lock = Any()
    private val groups = LinkedHashMap<String, RpcGroup>()
    private val customSessions = LinkedHashMap<String, KizzyRPC>()
    private val appIdAssignments = LinkedHashMap<String, String>()

    fun session(slotId: String): KizzyRPC? {
        val group = synchronized(lock) { groups[slotId] } ?: createGroup(slotId) ?: return null
        return synchronized(lock) { group.instances[builtInInstanceId(slotId)] }
    }

    fun sessionWithoutBuiltIn(slotId: String): KizzyRPC? = synchronized(lock) {
        groups[slotId]?.instances?.get(builtInInstanceId(slotId))
    }

    fun customSession(instanceId: String): KizzyRPC? {
        if (instanceId.isBlank()) return null
        synchronized(lock) {
            customSessions[instanceId]?.let { return it }
        }
        val instance = Prefs.getHomeRpcInstances().firstOrNull { it.id == instanceId } ?: return null
        val slotId = when (instance.type) {
            "custom_rpc" -> "2"
            "manual_rpc" -> "3"
            "media_rpc" -> "1"
            else -> return null
        }
        synchronized(lock) {
            groups[slotId]?.instances?.get(instanceId)?.let { return it }
            customSessions[instanceId]?.let { return it }
        }

        val credential = instance.oauthIndex?.takeIf { it > 0 }
            ?.let { Prefs.getOAuthApp(it) }
            ?: credentialForSlot(slotId)
            ?: return null
        if (credential.token.isBlank()) return null

        val appId = synchronized(lock) { assignAppId(instanceId) }
        val socket = createSocket(credential, instanceId)
        val rpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            appId.takeIf { it.isNotBlank() },
        )
        synchronized(lock) {
            customSessions[instanceId]?.let { return it }
            customSessions[instanceId] = rpc
        }
        launch { connectSocketSafely(socket, instanceId) }
        return rpc
    }

    fun closeCustomSession(instanceId: String) {
        val rpc = synchronized(lock) {
            appIdAssignments.remove(instanceId)
            customSessions.remove(instanceId)
        } ?: return
        launch { closeRpcSafely(rpc) }
    }

    fun isSessionActive(slotId: String): Boolean = synchronized(lock) {
        groups[slotId]?.instances?.values?.any { it.isRpcRunning() } == true
    }

    fun activeSlots(): List<String> = synchronized(lock) {
        groups.filterValues { group -> group.instances.values.any { it.isRpcRunning() } }
            .keys
            .toList()
    }

    fun addInstance(slotId: String, instanceId: String, oauthIndex: Int) {
        if (instanceId.isBlank()) return
        val group = synchronized(lock) { groups[slotId] } ?: createGroup(slotId) ?: return
        val index = slotIndex(slotId)
        val credential = Prefs.getOAuthApp(oauthIndex)
            ?: Prefs.getOAuthApp(index)
            ?: OAuthAppCredential(
                index = index,
                token = mainToken(),
                appId = defaultAppId(index),
            )
        if (credential.token.isBlank()) return

        synchronized(lock) {
            if (group.instances.containsKey(instanceId)) return
        }

        val appId = synchronized(lock) { assignAppId(instanceId) }
        val socket = createSocket(credential, instanceId)
        val rpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            appId.takeIf { it.isNotBlank() },
        )
        synchronized(lock) {
            if (group.instances.containsKey(instanceId)) return
            group.instances[instanceId] = rpc
        }
        launch { connectSocketSafely(socket, instanceId) }
    }

    fun removeInstance(slotId: String, instanceId: String) {
        val rpc = synchronized(lock) {
            appIdAssignments.remove(instanceId)
            groups[slotId]?.instances?.remove(instanceId)
        } ?: return
        launch { closeRpcSafely(rpc) }
    }

    fun instanceCount(slotId: String): Int = synchronized(lock) {
        groups[slotId]?.instances?.size ?: 0
    }

    fun hasInstance(slotId: String, instanceId: String): Boolean = synchronized(lock) {
        groups[slotId]?.instances?.containsKey(instanceId) == true
    }

    fun stopSession(slotId: String) {
        val toClose = synchronized(lock) {
            val group = groups.remove(slotId) ?: return
            appIdAssignments.remove(builtInInstanceId(slotId))
            group.instances.keys.forEach { appIdAssignments.remove(it) }
            group.instances.values.toList()
        }
        toClose.forEach { rpc ->
            launch { closeRpcSafely(rpc) }
        }
    }

    fun stopAllSessions() {
        val slotIds = synchronized(lock) { groups.keys.toList() }
        val customIds = synchronized(lock) { customSessions.keys.toList() }
        slotIds.forEach { stopSession(it) }
        customIds.forEach { closeCustomSession(it) }
    }

    fun loadSessionsFromPrefs() {
        val activeTypes = Prefs.getActiveRpcInstanceIds()
        listOf("1", "2", "3").forEach { slot ->
            val builtInId = builtInInstanceId(slot)
            if (activeTypes.isEmpty() || builtInId in activeTypes) {
                createGroup(slot)
            }
        }
    }

    private suspend fun connectSocketSafely(socket: DiscordWebSocketImpl, instanceId: String) {
        try {
            socket.connect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("MultiRpcManager", "Failed to connect socket for $instanceId: ${e.message}")
        }
    }

    private suspend fun closeRpcSafely(rpc: KizzyRPC) {
        try {
            rpc.clearPresence()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort presence clear before closing socket.
        }
        try {
            rpc.closeRPC()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ignore close errors.
        }
    }

    private fun createGroup(slotId: String): RpcGroup? {
        synchronized(lock) {
            groups[slotId]?.let { return it }
        }
        val credential = credentialForSlot(slotId)
        if (credential == null || credential.token.isBlank()) return null

        val builtInId = builtInInstanceId(slotId)
        val socket = createSocket(credential, builtInId)
        val kizzyRpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            credential.appId.takeIf { it.isNotBlank() },
        )
        val createdGroup = synchronized(lock) {
            groups[slotId]?.let { return it }
            RpcGroup(
                slotId = slotId,
                instances = linkedMapOf(builtInId to kizzyRpc),
            ).also { groups[slotId] = it }
        }
        launch { connectSocketSafely(socket, builtInId) }
        return createdGroup
    }

    private fun credentialForSlot(slotId: String): OAuthAppCredential? {
        val index = slotIndex(slotId)
        return Prefs.getOAuthApp(index)
            ?: Prefs.getConfiguredOAuthApps().firstOrNull()
            ?: OAuthAppCredential(index, mainToken(), defaultAppId(index))
                .takeIf { it.token.isNotBlank() }
    }

    private fun mainToken(): String = Prefs[Prefs.TOKEN, ""]
        .takeIf { it.isNotBlank() }
        ?: Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]

    private fun slotIndex(slotId: String): Int = slotId.toIntOrNull() ?: 1

    private fun defaultAppId(index: Int): String = when (index) {
        2 -> DEFAULT_OAUTH_APP_ID_2
        3 -> DEFAULT_OAUTH_APP_ID_3
        else -> DEFAULT_OAUTH_APP_ID_1
    }

    private fun appIdPool(): List<String> = (
            Prefs.getConfiguredOAuthApps().map { it.appId } +
                    listOf(
                        DEFAULT_OAUTH_APP_ID_1,
                        DEFAULT_OAUTH_APP_ID_2,
                        DEFAULT_OAUTH_APP_ID_3,
                    )
            ).filter { it.isNotBlank() }.distinct()

    private fun assignAppId(instanceId: String): String {
        appIdAssignments[instanceId]?.let { return it }
        val pool = appIdPool()
        if (pool.isEmpty()) return Constants.APPLICATION_ID
        val used = appIdAssignments.values.toSet()
        val appId = pool.firstOrNull { it !in used }
            ?: pool[appIdAssignments.size % pool.size]
        appIdAssignments[instanceId] = appId
        return appId
    }

    private fun builtInInstanceId(slotId: String): String = when (slotId) {
        "1" -> "media_rpc"
        "2" -> "custom_rpc"
        "3" -> "manual_rpc"
        else -> "slot_$slotId"
    }

    private fun createSocket(credential: OAuthAppCredential, instanceId: String): DiscordWebSocketImpl {
        val token = Prefs.formatIdentifyToken(credential.token)
        return DiscordWebSocketImpl(
            token = token,
            logger = logger,
            identifyBrowser = "Discord Android",
            identifyOs = "Android",
            identifyDevice = "${credential.appId}-$instanceId",
        )
    }

    fun onDestroy() {
        stopAllSessions()
        job.cancel()
    }
}