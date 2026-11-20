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

    /**
     * Called when a `SESSIONS_REPLACE` dispatch arrives **and our activity is no
     * longer part of it** (Discord cleared it, or another client replaced it).
     *
     * It is deliberately not called for the `SESSIONS_REPLACE` that Discord
     * echoes after our own `PRESENCE_UPDATE`, because that one already contains
     * the activity we just sent.
     *
     * Implementations should re-send the last presence so the activity does not
     * stay missing.
     */
    var onPresenceDropped: (() -> Unit)?
}