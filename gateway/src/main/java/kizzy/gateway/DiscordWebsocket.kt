package kizzy.gateway

import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope

sealed interface DiscordWebSocket : CoroutineScope {
    suspend fun connect()

    /**
     * Sends a presence update.
     *
     * @return true when the payload was handed to the socket, false when the
     *         connection was not ready (the caller must keep its cache and retry).
     */
    suspend fun sendActivity(presence: Presence): Boolean
    fun isWebSocketConnected(): Boolean
    fun close()

    /** Called when Discord sends SESSIONS_REPLACE (clears presence on their end).
     * Implementations should immediately re-send presence to avoid the activity
     * disappearing for 1-2 seconds. */
    var onSessionsReplace: (() -> Unit)?
}