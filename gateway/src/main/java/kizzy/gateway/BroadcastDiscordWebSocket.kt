package kizzy.gateway

import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One logical WebSocket that fans every operation out to real, independent
 * [DiscordWebSocketImpl] connections - one per parallel RPC instance.
 *
 * Each socket carries its own token, application ID, sequence and heartbeat.
 * Presence is throttled dynamically: real changes and timeline jumps go out
 * quickly, identical updates are held until the maximum interval.
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
    private val throttler = PresenceThrottler()
    private var pendingPresence: Presence? = null
    private var flushJob: Job? = null

    override var onPresenceDropped: (() -> Unit)? = null

    fun addSocket(instanceId: String, appId: String, socket: DiscordWebSocketImpl) {
        val callback: () -> Unit = { onPresenceDropped?.invoke() }
        synchronized(lock) {
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

    fun hasConnectedSocket(): Boolean = synchronized(lock) {
        entries.values.any { it.socket.isWebSocketConnected() }
    }

    private fun snapshot(): List<Entry> = synchronized(lock) { entries.values.toList() }

    override suspend fun connect() {
        snapshot().forEach { entry ->
            launch { entry.socket.connect() }
        }
    }

    override suspend fun sendActivity(presence: Presence): Boolean {
        val now = System.currentTimeMillis()
        val waitMs = throttler.delayFor(now, presence)
        pendingPresence = presence
        flushJob?.cancel()
        if (waitMs <= 0L) {
            flushJob = null
            return sendNow(presence, now)
        }
        flushJob = launch {
            delay(waitMs)
            pendingPresence?.let { sendNow(it, System.currentTimeMillis()) }
            flushJob = null
        }
        return true
    }

    private suspend fun sendNow(presence: Presence, now: Long): Boolean {
        val sockets = snapshot()
        if (sockets.isEmpty()) return false
        val results = sockets.map { entry ->
            async {
                runCatching {
                    entry.socket.sendActivity(
                        if (entry.appId.isBlank()) presence
                        else presence.copy(
                            activities = presence.activities?.map { activity ->
                                activity?.copy(applicationId = entry.appId)
                            },
                        ),
                    )
                }.getOrDefault(false)
            }
        }.awaitAll()
        throttler.markSent(now, presence)
        return results.any { it }
    }

    override fun isWebSocketConnected(): Boolean = hasConnectedSocket()

    override fun close() {
        flushJob?.cancel()
        flushJob = null
        val sockets = synchronized(lock) {
            val all = entries.values.toList()
            entries.clear()
            all
        }
        sockets.forEach { it.socket.close() }
    }
}
