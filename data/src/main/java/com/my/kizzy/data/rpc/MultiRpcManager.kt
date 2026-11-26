package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.OAuthAppCredential
import com.my.kizzy.preference.Prefs
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kizzy.gateway.BroadcastDiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the independent Discord gateway connections used by every RPC instance.
 *
 * One group exists per RPC type ("1" media, "2" custom, "3" manual). A group has
 * a single [KizzyRPC] - so the existing service logic stays unchanged - whose
 * socket is a [BroadcastDiscordWebSocket]. That broadcaster fans each presence
 * out to one real [DiscordWebSocketImpl] per enabled instance. Adding or
 * removing an instance therefore adds/removes a completely separate socket
 * (own token, application ID, sequence and heartbeat) without restarting the
 * service or disturbing the other instances.
 */
@Singleton
class MultiRpcManager @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger
) : CoroutineScope {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()

    private data class RpcGroup(
        val slotId: String,
        val broadcast: BroadcastDiscordWebSocket,
        val kizzyRpc: KizzyRPC,
        /** instanceId -> OAuth credentials used for that instance's socket. */
        val instances: MutableMap<String, OAuthAppCredential> = LinkedHashMap(),
    )

    private val groups = LinkedHashMap<String, RpcGroup>()

    /** Globally unique presence application ID per instance. */
    private val appIdAssignments = LinkedHashMap<String, String>()

    /** Last activity produced by each group, used to build one combined presence. */
    private val lastGroupPresence = LinkedHashMap<String, Presence>()
    private val throttler = PresenceThrottler()
    private var lastCombined: Presence? = null
    private var flushJob: Job? = null

    /**
     * Combines the latest activity of every group into one ordered presence and
     * sends that same payload to all sockets. This keeps every enabled instance
     * visible and in a stable order instead of letting Discord drop/reorder
     * activities sent independently by each socket.
     */
    private fun onGroupPresence(slotId: String, presence: Presence) {
        lastGroupPresence[slotId] = presence
        val combined = buildCombined()
        lastCombined = combined

        val now = System.currentTimeMillis()
        val waitMs = throttler.delayFor(now, combined)
        flushJob?.cancel()
        if (waitMs <= 0L) {
            flushJob = null
            sendCombined(combined, now)
        } else {
            flushJob = launch {
                delay(waitMs)
                lastCombined?.let { sendCombined(it, System.currentTimeMillis()) }
                flushJob = null
            }
        }
    }

    private fun buildCombined(): Presence {
        val activities = mutableListOf<Activity>()
        groups.values.forEach { group ->
            val base = lastGroupPresence[group.slotId]?.activities?.firstOrNull()
                ?: return@forEach
            group.broadcast.forEachInstance { _, appId ->
                activities += base.copy(applicationId = appId)
            }
        }
        return Presence(activities = activities, afk = false, status = "online")
    }

    private fun sendCombined(presence: Presence, now: Long) {
        groups.values.forEach { group ->
            launch { group.broadcast.sendRaw(presence) }
        }
        throttler.markSent(now, presence)
    }

    private fun assignAppId(instanceId: String): String {
        appIdAssignments[instanceId]?.let { return it }
        val used = appIdAssignments.values.toSet()
        val pool = appIdPool()
        val appId = pool.firstOrNull { it !in used }
            ?: pool[appIdAssignments.size % pool.size]
        appIdAssignments[instanceId] = appId
        return appId
    }

    /** Returns the [KizzyRPC] for a slot, creating its group lazily on first use. */
    fun session(slotId: String): KizzyRPC? {
        val existing = groups[slotId]
        // A group with sockets is live even before its first socket finishes
        // IDENTIFY. Discarding it here loses instances that were just attached.
        if (existing != null && existing.broadcast.socketCount() > 0) return existing.kizzyRpc
        if (existing != null) stopSession(slotId)
        return createGroup(slotId)?.kizzyRpc
    }

    /**
     * Returns the slot's KizzyRPC without adding the built-in instance's socket.
     * Used when the service is started only for a user-created instance.
     */
    fun sessionWithoutBuiltIn(slotId: String): KizzyRPC? {
        val existing = groups[slotId]
        if (existing != null && existing.broadcast.socketCount() > 0) return existing.kizzyRpc
        if (existing != null) stopSession(slotId)
        return createGroup(slotId, includeBuiltIn = false)?.kizzyRpc
    }

    fun isSessionActive(slotId: String): Boolean {
        val group = groups[slotId] ?: return false
        return group.broadcast.hasConnectedSocket()
    }

    fun activeSlots(): List<String> = groups.filter { it.value.broadcast.hasConnectedSocket() }.map { it.key }

    /** Adds an independent socket for [instanceId] to [slotId]'s group. */
    fun addInstance(slotId: String, instanceId: String, oauthIndex: Int) {
        val group = groups[slotId] ?: createGroup(slotId) ?: return
        if (group.broadcast.hasSocket(instanceId)) return

        val credential = Prefs.getOAuthApp(oauthIndex)
            ?: Prefs.getOAuthApp(slotIndex(slotId))
            ?: OAuthAppCredential(
                index = slotIndex(slotId),
                token = mainToken(),
                appId = defaultAppId(slotIndex(slotId)),
            )
        if (credential.token.isBlank()) return

        // Discord shows only one activity per application ID. Give every socket
        // a globally distinct application ID so all parallel instances are
        // visible at the same time.
        val appId = assignAppId(instanceId)
        val socket = createSocket(credential, instanceId)
        group.broadcast.addSocket(instanceId, appId, socket)
        group.instances[instanceId] = credential
        // Discord rate-limits IDENTIFYs from the same client; stagger them so
        // every parallel socket actually reaches READY.
        val delayMs = 750L * group.instances.size
        launch {
            kotlinx.coroutines.delay(delayMs)
            socket.connect()
        }
    }

    /** Closes and removes only [instanceId]'s socket; other instances keep running. */
    fun removeInstance(slotId: String, instanceId: String) {
        val group = groups[slotId] ?: return
        group.broadcast.removeSocket(instanceId)
        group.instances.remove(instanceId)
        appIdAssignments.remove(instanceId)
    }

    /** Number of live sockets in a slot (including the built-in instance). */
    fun instanceCount(slotId: String): Int = groups[slotId]?.broadcast?.socketCount() ?: 0

    fun hasInstance(slotId: String, instanceId: String): Boolean =
        groups[slotId]?.broadcast?.hasSocket(instanceId) == true

    /** Stops every socket in a slot, closing all of its instance connections. */
    fun stopSession(slotId: String) {
        val group = groups.remove(slotId) ?: return
        group.broadcast.close()
        group.instances.keys.forEach { appIdAssignments.remove(it) }
        appIdAssignments.remove(builtInInstanceId(slotId))
        group.instances.clear()
    }

    fun stopAllSessions() {
        flushJob?.cancel()
        flushJob = null
        lastCombined = null
        groups.keys.toList().forEach { stopSession(it) }
    }

    /** Recreates the groups the user had enabled before the process was killed. */
    fun loadSessionsFromPrefs() {
        val activeTypes = Prefs.getActiveRpcInstanceIds()
        listOf("1", "2", "3").forEach { slot ->
            val builtInId = builtInInstanceId(slot)
            if (activeTypes.isEmpty() || builtInId in activeTypes) createGroup(slot)
        }
    }

    private fun createGroup(slotId: String, includeBuiltIn: Boolean = true): RpcGroup? {
        val credential = credentialForSlot(slotId)
        if (credential == null || credential.token.isBlank()) return null

        val broadcast = BroadcastDiscordWebSocket { presence ->
            onGroupPresence(slotId, presence)
        }
        val builtInId = builtInInstanceId(slotId)
        if (includeBuiltIn) {
            broadcast.addSocket(
                instanceId = builtInId,
                appId = assignAppId(builtInId),
                socket = createSocket(credential, builtInId),
            )
        }
        val kizzyRpc = KizzyRPC(
            credential.token,
            kizzyRepository,
            broadcast,
            logger,
            credential.appId.takeIf { it.isNotBlank() },
        )
        val group = RpcGroup(
            slotId = slotId,
            broadcast = broadcast,
            kizzyRpc = kizzyRpc,
            instances = LinkedHashMap(),
        )
        groups[slotId] = group
        return group
    }

    private fun credentialForSlot(slotId: String): OAuthAppCredential? {
        val index = slotIndex(slotId)
        // Keep the old behaviour: a slot whose own OAuth app is not configured
        // falls back to the main token so built-in types still work when the
        // user has only one application authenticated.
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

    /**
     * Application IDs used for presence on parallel sockets. Configured apps
     * come first; built-in IDs fill the rest so more instances than configured
     * apps still get distinct application IDs on Discord.
     */
    private fun appIdPool(): List<String> = (
        Prefs.getConfiguredOAuthApps().map { it.appId } +
            listOf(
                Prefs.DEFAULT_OAUTH_APP_ID_1,
                Prefs.DEFAULT_OAUTH_APP_ID_2,
                Prefs.DEFAULT_OAUTH_APP_ID_3,
                "1573730101810331718",
            )
        ).distinct()

    private fun builtInInstanceId(slotId: String): String = when (slotId) {
        "1" -> "media_rpc"
        "2" -> "custom_rpc"
        "3" -> "manual_rpc"
        else -> "slot_$slotId"
    }

    private fun createSocket(credential: OAuthAppCredential, instanceId: String): DiscordWebSocketImpl {
        // Distinct identify device per instance so Discord treats them as
        // separate clients instead of reconnecting the same session.
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
