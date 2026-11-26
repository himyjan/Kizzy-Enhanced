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
class BroadcastDiscordWebSocket : DiscordWebSocket {

    override val coroutineContext = SupervisorJob() + Dispatchers.IO

    private class Entry(
        val instanceId: String,
        val appId: String,
        val socket: DiscordWebSocketImpl,
    )

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    /** Replaces the presence's application ID with the one assigned to this socket. */
    private fun Presence.forApplication(appId: String): Presence {
        if (appId.isBlank()) return this
        return copy(
            activities = activities?.map { activity -> activity?.copy(applicationId = appId) }
        )
    }

    override var onPresenceDropped: (() -> Unit)? = null

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
        val sockets = snapshot()
        if (sockets.isEmpty()) return false
        val results = sockets.map { entry ->
            async {
                runCatching { entry.socket.sendActivity(presence.forApplication(entry.appId)) }
                    .getOrDefault(false)
            }
        }.awaitAll()
        return results.any { it }
    }

    override fun isWebSocketConnected(): Boolean {
        val sockets = snapshot()
        return sockets.isNotEmpty() && sockets.all { it.socket.isWebSocketConnected() }
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
