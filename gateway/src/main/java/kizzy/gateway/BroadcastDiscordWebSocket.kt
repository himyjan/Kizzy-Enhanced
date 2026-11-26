package kizzy.gateway

import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * A single logical WebSocket that fans every operation out to a set of real,
 * independent [DiscordWebSocketImpl] connections.
 *
 * This lets one [com.my.kizzy.data.rpc.KizzyRPC] (and therefore one service's
 * presence-building logic) drive any number of parallel RPC instances: every
 * instance owns its own Discord gateway socket, sequence, heartbeat, token and
 * application ID, but they all receive the same presence the service builds.
 *
 * The list is mutable at runtime so instances can be added/removed without
 * restarting the service or dropping the other sockets.
 */
class BroadcastDiscordWebSocket(
    /** Forward the group's presence to the central combiner. */
    private val onPresence: suspend (Presence) -> Unit,
) : DiscordWebSocket {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private class Entry(
        val instanceId: String,
        val appId: String,
        val socket: DiscordWebSocketImpl,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    override var onPresenceDropped: (() -> Unit)? = null

    /** Sends an already-assembled presence to every socket, unchanged. */
    suspend fun sendRaw(presence: Presence): Boolean {
        val sockets = snapshot()
        if (sockets.isEmpty()) return false
        val results = sockets.map { entry ->
            async { runCatching { entry.socket.sendActivity(presence) }.getOrDefault(false) }
        }.awaitAll()
        return results.any { it }
    }

    /** Exposes the instances (and their application IDs) attached to this group. */
    fun forEachInstance(action: (instanceId: String, appId: String) -> Unit) {
        snapshot().forEach { action(it.instanceId, it.appId) }
    }


    fun addSocket(instanceId: String, appId: String, socket: DiscordWebSocketImpl) {
        val callback: () -> Unit = { onPresenceDropped?.invoke() }
        synchronized(lock) {
            // Close a stale socket with the same id before replacing it.
            entries.remove(instanceId)?.socket?.close()
            socket.onPresenceDropped = callback
            entries[instanceId] = Entry(instanceId, appId, socket)
        }
    }

    fun removeSocket(instanceId: String) {
        val removed = synchronized(lock) { entries.remove(instanceId) }
        removed?.socket?.close()
    }

    fun socketCount(): Int = synchronized(lock) { entries.size }

    fun hasSocket(instanceId: String): Boolean = synchronized(lock) { entries.containsKey(instanceId) }

    /** True if at least one delegate has an open connection. */
    fun hasConnectedSocket(): Boolean = synchronized(lock) {
        entries.values.any { it.socket.isWebSocketConnected() }
    }

    private fun snapshot(): List<Entry> = synchronized(lock) { entries.values.toList() }

    override suspend fun connect() {
        // Start every socket in parallel; each one performs its own IDENTIFY.
        snapshot().forEach { entry ->
            launch { entry.socket.connect() }
        }
    }

    override suspend fun sendActivity(presence: Presence): Boolean {
        // The central combiner turns every group's activity into one presence
        // with a stable order and sends it to all sockets.
        onPresence(presence)
        return true
    }

    override fun isWebSocketConnected(): Boolean {
        val sockets = snapshot()
        // Do not block presence for the whole group while one instance is
        // still doing IDENTIFY (Discord rate-limits simultaneous connects).
        // Each socket sends independently once it is ready.
        return sockets.any { it.socket.isWebSocketConnected() }
    }

    override fun close() {
        val sockets = synchronized(lock) {
            val all = entries.values.toList()
            entries.clear()
            all
        }
        sockets.forEach { it.socket.close() }
    }
}
