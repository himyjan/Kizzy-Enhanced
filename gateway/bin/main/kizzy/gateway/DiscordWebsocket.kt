package kizzy.gateway

import kizzy.gateway.entities.presence.Presence
import kotlinx.coroutines.CoroutineScope

sealed interface DiscordWebSocket : CoroutineScope {
    suspend fun connect()
    suspend fun sendActivity(presence: Presence)
    fun isWebSocketConnected(): Boolean
    fun close()

    /** Called when Discord sends SESSIONS_REPLACE (clears presence on their end).
     * Implementations should immediately re-send presence to avoid the activity
     * disappearing for 1-2 seconds. */
    var onSessionsReplace: (() -> Unit)?
}