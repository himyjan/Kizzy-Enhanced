package kizzy.gateway

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.interfaces.NoOpLogger
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

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
        private const val STUCK_CONNECT_MS = 45_000L
        private const val CONNECT_WAIT_TIMEOUT_MS = 15_000L
        private const val CLOSE_AUTH_FAILED = 4004
        private val NON_RESUMABLE_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
    }
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    /** Callback invoked when Discord sends SESSIONS_REPLACE (presence cleared on their end). */
    override var onSessionsReplace: (() -> Unit)? = null
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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _gatewayEvents = MutableSharedFlow<Payload>(replay = 1, extraBufferCapacity = 64)
    val gatewayEvents: SharedFlow<Payload> = _gatewayEvents.asSharedFlow()

    private val externalCoroutineScope: CoroutineScope? = externalScope
    private val supervisorJob = SupervisorJob()
    override val coroutineContext: CoroutineContext = (externalScope?.coroutineContext ?: supervisorJob) + Dispatchers.Default

    // Connection management - Mutex instead of synchronized for coroutine safety
    private val connectionMutex = Mutex()
    @Volatile
    private var connectionJob: Job? = null
    @Volatile
    private var generation = 0
    @Volatile
    private var connectStartedAtNanos = 0L

    override suspend fun connect() {
        launch(coroutineContext) {
            try {
                logger.i("Gateway", "Connect called")
                instance = this@DiscordWebSocketImpl
                _connectionState.value = ConnectionState.Connecting

                var shouldConnect = false
                var staleWebsocket: DefaultClientWebSocketSession? = null
                connectionMutex.withLock {
                    val live = connectionJob
                    if (live != null && live.isActive) {
                        val ageMs = (System.nanoTime() - connectStartedAtNanos) / 1_000_000L
                        if (connected || ageMs < STUCK_CONNECT_MS) {
                            logger.i("Gateway", "Connect called while a connection is already open or opening (${ageMs}ms); keeping it")
                        } else {
                            logger.w("Gateway", "The connection has not come up after ${ageMs}ms; replacing it")
                            generation++
                            live.cancel()
                            staleWebsocket = websocket
                            websocket = null
                            shouldConnect = true
                        }
                    } else {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        reconnectScheduled = false
                        reconnectAttempts = 0
                        shouldConnect = true
                    }
                }
                runCatching { staleWebsocket?.close() }
                if (shouldConnect) {
                    connectInternal()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("Gateway", e.message ?: "")
                scheduleReconnect()
            }
        }
    }

    private var reconnectJob: Job? = null
    private var reconnectScheduled = false

    private suspend fun connectInternal() {
        val gen = ++generation
        connectStartedAtNanos = System.nanoTime()
        connectionJob = launch {
            try {
                logger.i("Gateway","Connect called")
                val wsUrl = resumeGatewayUrl ?: gatewayUrl
                val session = withTimeout(30_000L) {
                    client.webSocketSession(wsUrl) {
                        headers {
                            append(
                                "X-Super-Properties",
                                "eyJvcyI6IldpbmRvd3MiLCJicm93c2VyIjoiS2l6enkiLCJkZXZpY2UiOiJrdXppeSIsInN5c3RlbV92ZXJzaW9uIjoiMTAuMC4xOTA0MSIsImJyb3dzZXJfdXNlcl9hZ2VudCI6Iktpenp5L0wxMC4wIChXaW5kb3dzKSIsImJyb3dzZXJfdXNlcl9hZ2VudF92ZXJzaW9uIjoiMTAuMC4xOTA0MSIsIm9zX3VzZXJfYWdlbnRfdmVyc2lvbiI6IjEwLjAuMTkwNDEiLCJjbGllbnRfYnVpbGRfbnVtYmVyIjoxNjM4OCwiY2xpZW50X2J1aWxkX3ZlcnNpb24iOiIxNi4zLjgiLCJvcGVyYXRpbmdfc3lzdGVtIjoid2luZG93cyIsImJyb3dzZXJfYXV0b21hdGljX3RyYWNrZXIiOmZhbHNlLCJkZXZlbG9wZXJfbXVkZXIiOnRydWV9"
                            )
                            append(
                                HttpHeaders.UserAgent,
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Kizzy/1.0.0"
                            )
                        }
                    }
                }
                if (gen != generation) {
                    runCatching { session.close() }
                    return@launch
                }
                websocket = session

                session.incoming.receiveAsFlow()
                    .collect {
                        when (it) {
                            is Frame.Text -> {
                                val jsonString = it.readText()
                                if (gen == generation) onMessage(jsonString)
                            }
                            else -> {}
                        }
                    }
                if (gen == generation) {
                    handleClose(session)
                } else {
                    logger.i("Gateway", "A replaced connection finished")
                }
            } catch (e: CancellationException) {
                if (deliberateClose || !supervisorJob.isActive || gen != generation) throw e
                logger.e("Gateway", "Receive loop cancelled unexpectedly: ${e.message ?: ""}")
                scheduleReconnect()
            } catch (e: Exception) {
                if (gen != generation) return@launch
                logger.e("Gateway", e.message ?: "")
                emitEvent(RpcEvent.ERROR, e.message ?: "Gateway connection error")
                scheduleReconnect()
            }
        }
    }

    private suspend fun scheduleReconnect() {
        if (deliberateClose || reconnectScheduled) return
        reconnectScheduled = true
        heartbeatJob?.cancel()
        connected = false
        reconnectJob = launch {
            val attempt = reconnectAttempts++
            val backoff = (2000L shl minOf(attempt, 5)).coerceAtMost(60_000L)
            val jitter = Random.nextLong(0L, 1000L)
            val totalDelay = backoff + jitter
            logger.w("Gateway", "Reconnecting in ${totalDelay}ms (attempt $reconnectAttempts)")
            emitEvent(RpcEvent.RECONNECTING, "Reconnecting to gateway")
            delay(totalDelay)
            connectionMutex.withLock {
                reconnectScheduled = false
                if (deliberateClose) return@launch
                _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts)
                if (connectionJob?.isActive == true) {
                    logger.i("Gateway", "Reconnect skipped: a connection is already open or opening")
                    return@launch
                }
                connectInternal()
            }
        }
    }

    private suspend fun onMessage(jsonString: String) {
        val payload = json.decodeFromString<Payload>(jsonString)
        logger.d("Gateway", "Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")
        logger.d("Gateway", "Incoming: $jsonString")

        payload.s?.let {
            sequence = it
        }
        _gatewayEvents.emit(payload)

        when (payload.op) {
            DISPATCH -> {
                payload.handleDispatch(jsonString)
                if (payload.t.toString() == "SESSIONS_REPLACE") {
                    logger.i("Gateway", "SESSIONS_REPLACE received — re-applying presence")
                    onSessionsReplace?.invoke()
                }
            }
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
                connected = true
                _connectionState.value = ConnectionState.Connected
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

    private suspend fun handleClose(session: WebSocketSession) {
        heartbeatJob?.cancel()
        connected = false
        if (!deliberateClose) {
            logger.w("Gateway", "WebSocket closed")
            scheduleReconnect()
        }
    }

    private suspend inline fun reconnectWebSocket() {
        try {
            websocket?.close(
                CloseReason(
                    code = 4000,
                    message = "Attempting to reconnect"
                )
            )
        } catch (e: Exception) {
            logger.e("Gateway", "Error closing socket for reconnect: ${e.message ?: ""}")
            scheduleReconnect()
        }
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
        heartbeatJob = launch((externalScope?.coroutineContext ?: supervisorJob) + Dispatchers.IO) {
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
            logger.d("Gateway", "Outgoing op:$op: $payload")
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
        val connectedInTime = withTimeoutOrNull(CONNECT_WAIT_TIMEOUT_MS) {
            while (!isSocketConnectedToAccount()) {
                delay(250.milliseconds)
            }
            true
        }
        if (connectedInTime != true) {
            logger.w("Gateway", "Socket not connected within timeout — dropping presence update")
            return
        }
        logger.i("Gateway", "Sending $PRESENCE_UPDATE")
        send(
            op = PRESENCE_UPDATE,
            d = presence
        )
    }

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
            type = 0,
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

    suspend fun updateTokenAndReconnect(newToken: String) {
        logger.i("Gateway", "Token updated — reconnecting with new credentials")
        deliberateClose = false
        stopWebSocketInternally()
        connectWithToken(newToken)
    }

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

    private suspend fun connectWithToken(token: String) {
        connect()
    }

    fun disconnect() {
        deliberateClose = true
        launch(coroutineContext) {
            stopWebSocketInternally()
        }
    }

    enum class RpcEvent {
        CONNECTED, DISCONNECTED, RECONNECTING, ERROR, LOGIN_REJECTED
    }

    private fun emitEvent(event: RpcEvent, message: String) {
        logger.i("Gateway", "$event: $message")
    }
}
