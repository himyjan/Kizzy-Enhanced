package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.OAuthAppCredential
import com.my.kizzy.preference.Prefs
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kizzy.gateway.DiscordWebSocketImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the independent Discord gateway connections used by every RPC instance.
 *
 * One group exists per RPC type ("1" media, "2" custom, "3" manual). Each
 * instance in a group owns its own [KizzyRPC] and socket. This way toggling one
 * instance never overwrites another instance's presence on Discord.
 */
@Singleton
class MultiRpcManager @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger
) : CoroutineScope {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()

    private data class RpcGroup(
        val slotId: String,
        /** instanceId -> KizzyRPC (each with its own socket) */
        val instances: MutableMap<String, KizzyRPC> = LinkedHashMap(),
    )

    private val groups = LinkedHashMap<String, RpcGroup>()

    /** Custom RPC instances need different presence from each other, so each
     *  gets its own KizzyRPC outside the slot groups. */
    private val customSessions = LinkedHashMap<String, KizzyRPC>()

    /** Globally unique presence application ID per instance. Discord shows only
     *  one activity per application ID, so every parallel instance needs a
     *  unique ID. Only real, authenticated OAuth2 application IDs are used. */
    private val appIdAssignments = LinkedHashMap<String, String>()

    /** Returns the [KizzyRPC] for a slot, creating its group lazily on first use.
     *  Returns the built-in instance's KizzyRPC. */
    fun session(slotId: String): KizzyRPC? {
        val group = groups[slotId] ?: createGroup(slotId) ?: return null
        return group.instances[builtInInstanceId(slotId)]
    }

    /** Returns the slot's KizzyRPC without creating the built-in instance's
     *  socket. Used when the service is started only for a user-created
     *  instance. Returns null if the built-in socket doesn't exist yet. */
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
        val socket = createSocket(credential, instanceId)
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

    fun activeSlots(): List<String> = groups.filter { it.value.instances.values.any { it.isRpcRunning() } }.map { it.key }

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
        val socket = createSocket(credential, instanceId)
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
        val socket = createSocket(credential, builtInId)
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
        2 -> Prefs.DEFAULT_OAUTH_APP_ID_2
        3 -> Prefs.DEFAULT_OAUTH_APP_ID_3
        else -> Prefs.DEFAULT_OAUTH_APP_ID_1
    }

    private fun appIdPool(): List<String> = (
        Prefs.getConfiguredOAuthApps().map { it.appId } +
            listOf(
                Prefs.DEFAULT_OAUTH_APP_ID_1,
                Prefs.DEFAULT_OAUTH_APP_ID_2,
                Prefs.DEFAULT_OAUTH_APP_ID_3,
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

    private fun createSocket(credential: OAuthAppCredential, instanceId: String): DiscordWebSocketImpl {
        val device = "${credential.appId}-$instanceId"
        return DiscordWebSocketImpl(
            token = Prefs.formatIdentifyToken(credential.token),
            logger = logger,
            identifyBrowser = "Discord Android",
            identifyOs = "Android",
            identifyDevice = device,
        )
    }

    fun onDestroy() {
        stopAllSessions()
    }
}