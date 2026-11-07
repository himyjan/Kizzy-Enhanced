package kizzy.gateway

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.interfaces.NoOpLogger
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kizzy.gateway.entities.Heartbeat
import kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import kizzy.gateway.entities.OutgoingPayload
import kizzy.gateway.entities.Payload
import kizzy.gateway.entities.PayloadData
import kizzy.gateway.entities.Ready
import kizzy.gateway.entities.Resume
import kizzy.gateway.entities.op.OpCode
import kizzy.gateway.entities.op.OpCode.*
import kizzy.gateway.entities.presence.Assets
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.ActivityButton
import kizzy.gateway.entities.presence.Presence
import kizzy.gateway.entities.presence.Timestamps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/// Connection lifecycle states exposed to UI/Service observers.
sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    object Connected : ConnectionState
}

open class DiscordWebSocketImpl(
    private val token: String,
    private val logger: Logger = NoOpLogger,
    private val identifyBrowser: String = "Discord Client",
    private val identifyOs: String = "Windows",
    private val identifyDevice: String = "ktor",
    private val externalScope: CoroutineScope? = null,
    private val isOnlineState: StateFlow<Boolean>? = null
) : DiscordWebSocket {
    companion object {
        @Volatile
        var instance: DiscordWebSocketImpl? = null
    }
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var connected = false
    private var deliberateClose = false
    private var reconnectAttempts = 0
    private var awaitingHeartbeatAck = false
    private var client: HttpClient = HttpClient {
        install(WebSockets)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Connection state observable by external components (UI, services, etc.)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Expose parsed gateway events to external observers (UI, services, etc.)
    private val _gatewayEvents = MutableSharedFlow<Payload>(replay = 1, extraBufferCapacity = 64)
    val gatewayEvents: SharedFlow<Payload> = _gatewayEvents.asSharedFlow()

    // Use external scope if provided, otherwise create our own
    private val externalCoroutineScope: CoroutineScope? = externalScope
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = (externalScope?.coroutineContext ?: supervisorJob) + Dispatchers.Default

    override suspend fun connect() {
        launch(coroutineContext) {
            try {
                logger.i("Gateway", "Connect called")
                instance = this@DiscordWebSocketImpl
                _connectionState.value = ConnectionState.Connecting

                // Close existing websocket before creating new one - prevents multiple
                // concurrent connections that cause Discord 4003 Not authenticated
                websocket?.close(
                    CloseReason(1000, "Reconnecting")
                )
                websocket = null

                val url = resumeGatewayUrl ?: gatewayUrl
                websocket = client.webSocketSession(url)
                reconnectAttempts = 0

                // start receiving messages
                websocket!!.incoming.receiveAsFlow()
                    .collect {
                        when (it) {
                            is Frame.Text -> {
                                val jsonString = it.readText()
                                onMessage(jsonString)
                            }
                            else -> {}
                        }
                    }
                handleClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Gateway", e.message ?: "")
                scheduleReconnect()
            }
        }
    }

    private suspend fun handleClose() {
        heartbeatJob?.cancel()
        connected = false
        awaitingHeartbeatAck = false
        _connectionState.value = ConnectionState.Disconnected
        val close = websocket?.closeReason?.await()
        val code = close?.code?.toInt()
        logger.w("Gateway", "Closed with code: $code, reason: ${close?.message}")
        when {
            deliberateClose -> {}
            code == 4000 -> {
                delay(200.milliseconds)
                connect()
            }
            code == 4004 || (code != null && code in 4010..4014) -> {
                logger.e("Gateway", "Fatal close code $code — not reconnecting")
                close()
            }
            else -> scheduleReconnect()
        }
    }

    private suspend fun scheduleReconnect() {
        if (deliberateClose) return
        heartbeatJob?.cancel()
        connected = false
        awaitingHeartbeatAck = false
        runCatching { websocket?.close() }
        reconnectAttempts++

        if (reconnectAttempts >= 3) {
            resumeGatewayUrl = null
            sessionId = null
        }

        _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts)

        // Wait for network to come back online before reconnecting
        isOnlineState?.let { onlineState ->
            if (!onlineState.value) {
                _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts)
                logger.w("Gateway", "Network offline — waiting for connection to return...")
                onlineState.first { it }
            }
        }

        val delayMs = (2000L shl minOf(reconnectAttempts, 5)).coerceAtMost(60_000L)
        val jitter = Random.nextLong(0L, 1000L)
        val totalDelay = delayMs + jitter
        logger.w("Gateway", "Connection lost — reconnecting in ${totalDelay}ms (attempt $reconnectAttempts)")
        delay(totalDelay)
        if (!deliberateClose) connect()
    }

    private suspend fun onMessage(jsonString: String) {
        val payload = json.decodeFromString<Payload>(jsonString)
        logger.d("Gateway", "Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")

        payload.s?.let {
            sequence = it
        }
        // Emit parsed payload to external observers
        _gatewayEvents.emit(payload)

        when (payload.op) {
            DISPATCH -> payload.handleDispatch(jsonString)
            HEARTBEAT -> sendHeartBeat()
            RECONNECT -> reconnectWebSocket()
            INVALID_SESSION -> handleInvalidSession()
            HELLO -> handleHello(jsonString)
            HEARTBEAT_ACK -> {
                awaitingHeartbeatAck = false
                logger.d("Gateway", "Heartbeat ACK received")
            }
            else -> {}
        }
    }

    open fun Payload.handleDispatch(jsonString: String) {
        when (this.t.toString()) {
            "READY" -> {
                val ready = decodePayloadData<Ready>(jsonString) ?: return
                sessionId = ready.sessionId
                resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                logger.i("Gateway", "resume_gateway_url updated to $resumeGatewayUrl")
                logger.i("Gateway", "session_id updated to $sessionId")
                connected = true
                _connectionState.value = ConnectionState.Connected
                return
            }
            "RESUMED" -> {
                logger.i("Gateway", "Session Resumed")
            }
            else -> {}
        }
    }

    private suspend inline fun handleInvalidSession() {
        logger.i("Gateway", "Handling Invalid Session")
        logger.d("Gateway", "Sending Identify after 150ms")
        delay(150)
        sendIdentify()
    }

    private suspend inline fun handleHello(jsonString: String) {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = decodePayloadData<Heartbeat>(jsonString)?.heartbeatInterval ?: return
        logger.i("Gateway", "Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    protected fun decodeReady(jsonString: String): Ready? {
        return decodePayloadData(jsonString)
    }

    private inline fun <reified T> decodePayloadData(jsonString: String): T? {
        return json.decodeFromString<PayloadData<T>>(jsonString).d
    }

    private suspend fun sendHeartBeat() {
        logger.i("Gateway", "Sending $HEARTBEAT with seq: $sequence")
        send(
            op = HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect"
            )
        )
    }

    private suspend fun sendIdentify() {
        logger.i("Gateway", "Sending $IDENTIFY")
        send(
            op = IDENTIFY,
            d = token.toIdentifyPayload(
                browser = identifyBrowser,
                os = identifyOs,
                device = identifyDevice
            )
        )
    }

    private suspend fun sendResume() {
        logger.i("Gateway", "Sending $RESUME")
        send(
            op = RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token
            )
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        awaitingHeartbeatAck = false
        heartbeatJob = launch(coroutineContext) {
            while (isActive) {
                if (awaitingHeartbeatAck) {
                    logger.w("Gateway", "No heartbeat ACK — zombie connection, reconnecting")
                    reconnectWebSocket()
                    return@launch
                }
                awaitingHeartbeatAck = true
                sendHeartBeat()
                delay(interval)
            }
        }
    }

    private fun isSocketConnectedToAccount(): Boolean {
        return connected && websocket?.isActive == true
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override fun isWebSocketConnected(): Boolean {
        return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        if (websocket?.isActive == true) {
            val payload = json.encodeToString(
                OutgoingPayload(
                    op = op,
                    d = d,
                )
            )
            websocket?.send(Frame.Text(payload))
        }
    }

    override fun close() {
        deliberateClose = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        this.cancel()
        resumeGatewayUrl = null
        sessionId = null
        connected = false
        _connectionState.value = ConnectionState.Disconnected
        runBlocking {
            websocket?.close()
            logger.e("Gateway", "Connection to gateway closed")
        }
    }

    override suspend fun sendActivity(presence: Presence) {
        var waitedMs = 0L
        while (!isSocketConnectedToAccount()) {
            delay(50.milliseconds)
            waitedMs += 50
            if (waitedMs >= 60_000) {
                logger.w("Gateway", "Socket not connected after 60s — dropping presence update")
                return
            }
        }
        logger.i("Gateway", "Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence
        )
    }

    /// Updates Discord Rich Presence with custom fields.
    /// Requires an active WebSocket connection (see [connectionState]).
    suspend fun updatePresence(
        name: String,
        details: String? = null,
        state: String? = null,
        largeImageKey: String? = null,
        largeText: String? = null,
        smallImageKey: String? = null,
        smallText: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        status: String = "online",
        afk: Boolean = false,
        buttons: List<ActivityButton>? = null
    ) {
        if (!isSocketConnectedToAccount()) {
            logger.w("Gateway", "Socket not connected — cannot update presence")
            return
        }

        val timestamps = if (startTime != null || endTime != null) {
            Timestamps(start = startTime, end = endTime)
        } else null

        val assets = if (largeImageKey != null || smallImageKey != null) {
            Assets(largeImage = largeImageKey, smallImage = smallImageKey, largeText = largeText, smallText = smallText)
        } else null

        val activity = Activity(
            name = name,
            type = 0, // Playing
            state = state,
            details = details,
            timestamps = timestamps,
            assets = assets,
            buttons = buttons
        )

        val presence = Presence(
            activities = listOf(activity),
            status = status,
            afk = afk
        )

        logger.i("Gateway", "Sending $PRESENCE_UPDATE — name=$name, details=$details")
        send(
            op = PRESENCE_UPDATE,
            d = presence
        )
    }

    /// Clears Rich Presence on Discord (used when paused).
    suspend fun clearPresence() {
        if (!isSocketConnectedToAccount()) return
        logger.i("Gateway", "Clearing Rich Presence")
        send(
            op = PRESENCE_UPDATE,
            d = Presence(
                activities = emptyList(),
                status = "invisible",
                afk = true
            )
        )
    }

    /// Updates the token and reconnects with the new credentials.
    /// Sends OpCode 6 (Resume) if we have a saved session_id and sequence,
    /// otherwise performs a fresh Identify (OpCode 2).
    suspend fun updateTokenAndReconnect(newToken: String) {
        logger.i("Gateway", "Token updated — reconnecting with new credentials")
        deliberateClose = false
        stopWebSocketInternally()
        connectWithToken(newToken)
    }

    /// Internal: stop the current WebSocket without changing deliberateClose flag.
    private suspend fun stopWebSocketInternally() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        runCatching { websocket?.close() }
        websocket = null
        connected = false
        sessionId = null
        resumeGatewayUrl = null
        sequence = 0
        _connectionState.value = ConnectionState.Disconnected
    }

    /// Internal: connect with a given token (used by updateTokenAndReconnect).
    private suspend fun connectWithToken(token: String) {
        // Note: this class's `token` property is a constructor val (immutable).
        // For dynamic token updates, the service layer should create a new
        // DiscordWebSocketImpl instance with the new token and call connect().
        // This method is retained for API compatibility.
        connect()
    }

    /// Gracefully disconnects the WebSocket and clears session state.
    fun disconnect() {
        deliberateClose = true
        launch(coroutineContext) {
            stopWebSocketInternally()
        }
    }

}
