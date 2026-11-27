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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns Discord gateway connections used by every RPC instance.
 *
 * Discord Gateway allows only ONE WebSocket connection per user token.
 * Multiple sockets with the same token kill each other with INVALID_SESSION,
 * causing infinite reconnect loops. Therefore [socketCache] keys sockets by
 * their formatted identify token so that:
 *   - A single account token used by Media/Custom/Manual RPC shares ONE socket.
 *   - Different OAuth2 app tokens each get their OWN parallel socket.
 * Each [KizzyRPC] instance owns its own presence data but shares its socket
 * with other instances using the same token.
 */
@Singleton
class MultiRpcManager @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger
) : CoroutineScope {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()

    private data class RpcGroup(
        val slotId: String,
        val instances: MutableMap<String, KizzyRPC> = LinkedHashMap(),
    )

    private val groups = LinkedHashMap<String, RpcGroup>()

    /** Custom RPC instances keep their own KizzyRPC outside slot groups. */
    private val customSessions = LinkedHashMap<String, KizzyRPC>()

    /** Socket cache keyed by formatted identify token. */
    private val socketCache = LinkedHashMap<String, DiscordWebSocketImpl>()

    /** Globally unique presence application ID per instance. */
    private val appIdAssignments = LinkedHashMap<String, String>()

    /** Returns the [KizzyRPC] for a slot, creating its group lazily on first use. */
    fun session(slotId: String): KizzyRPC? {
        val group = groups[slotId] ?: createGroup(slotId) ?: return null
        return group.instances[builtInInstanceId(slotId)]
    }

    /** Returns the slot's KizzyRPC without creating the built-in instance's socket. */
    fun sessionWithoutBuiltIn(slotId: String): KizzyRPC? {
        return groups[slotId]?.instances[builtInInstanceId(slotId)]
    }

    /** Returns a dedicated [KizzyRPC] for one Custom RPC instance. */
    fun customSession(instanceId: String): KizzyRPC? {
        customSessions[instanceId]?.let { return it }
        val instance = Prefs.getHomeRpcInstances().firstOrNull { it.id == instanceId } ?: return null
        val slotId = when(instance.type) {
            "custom_rpc" -> "2"
            else -> return null
        }
        groups[slotId]?.instances[instanceId]?.let { return it }
        val credential = instance.oauthIndex?.takeIf { it > 0 }
            ?.let { Prefs.getOAuthApp(it) }
            ?: credentialForSlot("2")
            ?: return null
        val appId = assignAppId(instanceId)
        val socket = socketForToken(credential)
        val rpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            appId.takeIf { it.isNotBlank() },
        )
        customSessions[instanceId] = rpc
        launch { socket.connect() }
        return rpc
    }

    fun closeCustomSession(instanceId: String) {
        customSessions.remove(instanceId)?.let { rpc ->
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
    }

    fun isSessionActive(slotId: String): Boolean {
        val group = groups[slotId] ?: return false
        return group.instances.values.any { it.isRpcRunning() }
    }

    fun activeSlots(): List<String> =
        groups.filter { it.value.instances.values.any { it.isRpcRunning() } }.map { it.key }

    /** Adds an independent socket for [instanceId] to [slotId]'s group. */
    fun addInstance(slotId: String, instanceId: String, oauthIndex: Int) {
        val group = groups[slotId] ?: createGroup(slotId) ?: return
        val credential = Prefs.getOAuthApp(oauthIndex)
            ?: Prefs.getOAuthApp(slotIndex(slotId))
            ?: OAuthAppCredential(
                index = slotIndex(slotId),
                token = mainToken(),
                appId = defaultAppId(slotIndex(slotId)),
            )
        if (credential.token.isBlank()) return
        if (group.instances.containsKey(instanceId)) return

        val appId = assignAppId(instanceId)
        val socket = socketForToken(credential)
        val rpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            appId.takeIf { it.isNotBlank() },
        )
        group.instances[instanceId] = rpc
        launch { socket.connect() }
    }

    /** Closes and removes only [instanceId]'s socket; other instances keep running. */
    fun removeInstance(slotId: String, instanceId: String) {
        val group = groups[slotId] ?: return
        group.instances.remove(instanceId)?.let { rpc ->
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
    }

    /** Number of live sockets in a slot (including the built-in instance). */
    fun instanceCount(slotId: String): Int = groups[slotId]?.instances?.size ?: 0

    fun hasInstance(slotId: String, instanceId: String): Boolean =
        groups[slotId]?.instances?.containsKey(instanceId) == true

    /** Stops every socket in a slot, closing all of its instance connections. */
    fun stopSession(slotId: String) {
        val group = groups.remove(slotId) ?: return
        group.instances.values.forEach { rpc ->
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
        appIdAssignments.remove(builtInInstanceId(slotId))
        group.instances.keys.forEach { appIdAssignments.remove(it) }
    }

    fun stopAllSessions() {
        groups.keys.toList().forEach { stopSession(it) }
        customSessions.keys.toList().forEach { closeCustomSession(it) }
        socketCache.clear()
    }

    /** Recreates the groups the user had enabled before the process was killed. */
    fun loadSessionsFromPrefs() {
        val activeTypes = Prefs.getActiveRpcInstanceIds()
        listOf("1", "2", "3").forEach { slot ->
            val builtInId = builtInInstanceId(slot)
            if (activeTypes.isEmpty() || builtInId in activeTypes) createGroup(slot)
        }
    }

    private fun createGroup(slotId: String): RpcGroup? {
        val credential = credentialForSlot(slotId)
        if (credential == null || credential.token.isBlank()) return null

        val builtInId = builtInInstanceId(slotId)
        val socket = socketForToken(credential)
        val kizzyRpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            socket,
            logger,
            credential.appId.takeIf { it.isNotBlank() },
        )
        val group = RpcGroup(
            slotId = slotId,
            instances = LinkedHashMap(),
        )
        group.instances[builtInId] = kizzyRpc
        launch { socket.connect() }
        groups[slotId] = group
        return group
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

    private fun slotIndex(slotId: String): Int = when (slotId) {
        "1" -> 1
        "2" -> 2
        "3" -> 3
        else -> 1
    }

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
        ).distinct()

    private fun assignAppId(instanceId: String): String {
        appIdAssignments[instanceId]?.let { return it }
        val used = appIdAssignments.values.toSet()
        val pool = appIdPool()
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

    /**
     * Returns a cached [DiscordWebSocketImpl] for [credential]'s token, or creates a
     * new one if the token has not been seen before.
     *
     * Discord Gateway allows only ONE connection per user token. Caching by token
     * prevents multiple sockets with the same token from killing each other with
     * INVALID_SESSION while preserving true multisocket for different tokens.
     */
    private fun socketForToken(credential: OAuthAppCredential): DiscordWebSocketImpl {
        val token = Prefs.formatIdentifyToken(credential.token)
        return socketCache.getOrPut(token) {
            val device = "${credential.appId}-${token.takeLast(8)}"
            DiscordWebSocketImpl(
                token = token,
                logger = logger,
                identifyBrowser = "Discord Android",
                identifyOs = "Android",
                identifyDevice = device,
            )
        }
    }

    fun onDestroy() {
        stopAllSessions()
    }
}
