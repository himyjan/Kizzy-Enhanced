package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kizzy.gateway.DiscordWebSocketImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manages up to 3 independent Discord gateway connections, each with its own
 * OAuth2 application ID + token. Each session connects to Discord's gateway
 * separately and sends its own presence (OP 3), so Discord shows one activity
 * per application ID simultaneously.
 *
 * Slots:
 *   "1" - default token (Prefs.TOKEN / DISCORD_OAUTH2_ACCESS_TOKEN)
 *   "2" - DISCORD_OAUTH2_ACCESS_TOKEN_2 + DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2
 *   "3" - DISCORD_OAUTH2_ACCESS_TOKEN_3 + DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3
 *
 * RPC type to slot mapping:
 *   Media RPC    -> slot "1"
 *   Custom RPC   -> slot "2"
 *   Manual RPC   -> slot "3"
 *
 * A session is created lazily on first use and connects to Discord's gateway.
 * Each session owns its own WebSocket, heartbeat, sequence, and session ID, so
 * they never interfere with each other.
 */
@Singleton
class MultiRpcManager @Inject constructor(
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger
) : CoroutineScope {

    override val coroutineContext = Dispatchers.IO + SupervisorJob()

    private val TAG = "MultiRpcManager"

    private data class RpcSession(
        val slotId: String,
        val appId: String,
        val kizzyRpc: KizzyRPC,
        val discordWebSocket: DiscordWebSocketImpl,
        var enabled: Boolean = false
    )

    private val sessions = mutableMapOf<String, RpcSession>()

    /** Returns the [KizzyRPC] for [slotId], creating it lazily on first use.
     *
     * If the existing session's WebSocket is no longer connected (e.g. after
     * an RPC service was destroyed), the old session is discarded and a fresh
     * one is created so that toggling a switch off then on again actually
     * reconnects instead of silently returning a dead session.
     */
    fun session(slotId: String): KizzyRPC? {
        val existing = sessions[slotId]
        if (existing != null && existing.kizzyRpc.isRpcRunning()) {
            return existing.kizzyRpc
        }
        // Session is missing, disabled, or WebSocket is dead — remove it and recreate.
        sessions.remove(slotId)
        return createSession(slotId)?.kizzyRpc
    }

    /** Returns true if [slotId] is enabled and its WebSocket is connected. */
    fun isSessionActive(slotId: String): Boolean {
        val s = sessions[slotId]
        return s != null && s.enabled && s.kizzyRpc.isRpcRunning()
    }

    /** Returns all currently enabled slot IDs. */
    fun activeSlots(): List<String> {
        return sessions.filter { it.value.enabled }.map { it.key }
    }

    /** Enable/disable a session without destroying its WebSocket. */
    fun setSessionEnabled(slotId: String, enabled: Boolean) {
        sessions[slotId]?.enabled = enabled
    }

    /** Stops and removes a session, closing its WebSocket. */
    fun stopSession(slotId: String) {
        sessions[slotId]?.let {
            try {
                it.kizzyRpc.closeRPC()
            } catch (_: Exception) {
            }
            try {
                it.discordWebSocket.close()
            } catch (_: Exception) {
            }
            sessions.remove(slotId)
        }
    }

    /** Stops every session and clears the map. */
    fun stopAllSessions() {
        sessions.values.forEach {
            try {
                it.kizzyRpc.closeRPC()
            } catch (_: Exception) {
            }
            try {
                it.discordWebSocket.close()
            } catch (_: Exception) {
            }
        }
        sessions.clear()
    }

    /** Read persisted session configuration from Preferences and create sessions. */
    fun loadSessionsFromPrefs() {
        // Slot 1 is always "available" if there's a default token
        val token1 = Prefs[Prefs.TOKEN, ""]
            .takeIf { it.isNotBlank() }
            ?: Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        if (token1.isNotBlank()) {
            sessions["1"] = RpcSession(
                slotId = "1",
                appId = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""],
                kizzyRpc = createKizzyRpc("1", Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""], token1),
                discordWebSocket = createDiscordWebSocket("1", Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""], token1),
                enabled = true
            )
        }
        // Slot 2
        val token2 = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_2, ""]
        val appId2 = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2, ""]
        if (token2.isNotBlank() && Prefs[Prefs.RPC_SESSION_2_ENABLED, false]) {
            sessions["2"] = RpcSession(
                slotId = "2",
                appId = appId2,
                kizzyRpc = createKizzyRpc("2", appId2, token2),
                discordWebSocket = createDiscordWebSocket("2", appId2, token2),
                enabled = true
            )
        }
        // Slot 3
        val token3 = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_3, ""]
        val appId3 = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3, ""]
        if (token3.isNotBlank() && Prefs[Prefs.RPC_SESSION_3_ENABLED, false]) {
            sessions["3"] = RpcSession(
                slotId = "3",
                appId = appId3,
                kizzyRpc = createKizzyRpc("3", appId3, token3),
                discordWebSocket = createDiscordWebSocket("3", appId3, token3),
                enabled = true
            )
        }
    }

    private fun createSession(slotId: String): RpcSession? {
        val mainToken = Prefs[Prefs.TOKEN, ""]
            .takeIf { it.isNotBlank() }
            ?: Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]

        val token = when (slotId) {
            "1" -> mainToken
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_2, ""].takeIf { it.isNotBlank() } ?: mainToken
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_3, ""].takeIf { it.isNotBlank() } ?: mainToken
            "4" -> mainToken
            "5" -> mainToken
            else -> return null
        }
        val appId = when (slotId) {
            "1" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""].takeIf { it.isNotBlank() } ?: "1571063796418744412"
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2, ""].takeIf { it.isNotBlank() } ?: "1573725984845201438"
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3, ""].takeIf { it.isNotBlank() } ?: "1573728208854065293"
            "4" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""].takeIf { it.isNotBlank() } ?: "1571063796418744412"
            "5" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""].takeIf { it.isNotBlank() } ?: "1573730101810331718"
            else -> ""
        }
        if (token.isBlank()) return null

        val discordWebSocket = createDiscordWebSocket(slotId, appId, token)
        val kizzyRpc = KizzyRPC(token, kizzyRepository, discordWebSocket, logger, appId.takeIf { it.isNotEmpty() })
        sessions[slotId] = RpcSession(slotId, appId, kizzyRpc, discordWebSocket, true)
        return sessions[slotId]
    }

    private fun createKizzyRpc(slotId: String, appId: String, token: String): KizzyRPC {
        val ws = createDiscordWebSocket(slotId, appId, token)
        return KizzyRPC(token, kizzyRepository, ws, logger, appId.takeIf { it.isNotEmpty() })
    }

    private fun createDiscordWebSocket(slotId: String, appId: String, token: String): DiscordWebSocketImpl {
        // Use the app ID as the identify device so Discord distinguishes sessions
        val device = if (appId.isNotBlank()) appId else "kizzy-$slotId"
        return DiscordWebSocketImpl(
            token = token,
            logger = logger,
            identifyBrowser = "Discord Android",
            identifyOs = "Android",
            identifyDevice = device
        )
    }

    fun onDestroy() {
        stopAllSessions()
    }
}
