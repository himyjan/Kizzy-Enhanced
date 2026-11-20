package kizzy.gateway

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.interfaces.NoOpLogger
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
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
import kizzy.gateway.entities.SessionReplaceEntry
import kizzy.gateway.entities.op.OpCode
import kizzy.gateway.entities.op.OpCode.*
import kizzy.gateway.entities.presence.Assets
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.ActivityButton
import kizzy.gateway.entities.presence.Metadata
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
    private var token: String,
    private val logger: Logger = NoOpLogger,
    private var identifyBrowser: String = "Discord Client",
    private var identifyOs: String = "Windows",
    private var identifyDevice: String = "ktor",
    private val externalScope: CoroutineScope? = null,
    private val isOnlineState: StateFlow<Boolean>? = null
) : DiscordWebSocket {
    companion object {
        @Volatile
        var instance: DiscordWebSocketImpl? = null
        private const val STUCK_CONNECT_MS = 45_000L
        private const val CONNECT_WAIT_TIMEOUT_MS = 15_000L
        private const val CLOSE_AUTH_FAILED = 4004
        private const val CUSTOM_STATUS_TYPE = 4
        private val NON_RESUMABLE_CLOSE_CODES = setOf(4004, 4010, 4011, 4012, 4013, 4014)
    }
    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    /** Callback invoked when a SESSIONS_REPLACE no longer contains our activity. */
    override var onPresenceDropped: (() -> Unit)? = null
    // Identity of the activity from the last successfully sent presence. Used to
    // decide whether an incoming SESSIONS_REPLACE still contains our presence.
    @Volatile
    internal var lastSentActivityApplicationId: String? = null
    @Volatile
    internal var lastSentActivityName: String? = null
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    @Volatile
    private var connected = false
    /** True when the current handshake used RESUME, false when it used IDENTIFY. */
    private var handshakeUsedResume = false
    private var deliberateClose = false
    private var reconnectAttempts = 0
    private var awaitingHeartbeatAck = false
    private var client: HttpClient = HttpClient(OkHttp) {
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
        // Early return if already connected and the WebSocket is active.
        // updateRPC() calls connect() on every presence update; without this
        // guard each call launches a coroutine on the supervisor job even when
        // the gateway is already open, which contributes to socket churn.
        if (websocket?.isActive == true && connected) {
            logger.d("Gateway", "Connect called while already connected; keeping it")
            return
        }

        launch(coroutineContext) {
            try {
                logger.i("Gateway", "Connect called")
                instance = this@DiscordWebSocketImpl
                // A previous close() must not prevent this explicit connect from working.
                deliberateClose = false
                _connectionState.value = ConnectionState.Connecting

                // Everything is decided and started while holding the mutex. Doing
                // connectInternal() outside the lock left a small window in which a
                // second connect() also saw "no live job" and opened a second socket
                // (the first one was then cancelled by the generation check).
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
                            val stale = websocket
                            websocket = null
                            // Best-effort close; ignore failures (e.g. socket already closed).
                            if (runCatching { stale?.close() }.isFailure) {
                            }
                            connectInternal()
                        }
                    } else {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        reconnectScheduled = false
                        reconnectAttempts = 0
                        connectInternal()
                    }
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
                val wsUrl = resumeGatewayUrl ?: gatewayUrl
                logger.i("Gateway", "Opening gateway connection: $wsUrl")
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
                    // Best-effort close; ignore failures (e.g. session already closed).
                    if (runCatching { session.close() }.isFailure) {
                    }
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
                            is Frame.Close -> {
                                val reason = it.readReason()
                                logger.w("Gateway", "WebSocket close frame: code=${reason?.code}, reason=${reason?.message ?: "none"}")
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
                if (payload.t == "SESSIONS_REPLACE") {
                    if (isOurActivityPresent(jsonString)) {
                        // This is the echo of our own PRESENCE_UPDATE (or another
                        // client's update that kept our activity). Re-sending would
                        // be a duplicate and burn the presence rate limit.
                        logger.d("Gateway", "SESSIONS_REPLACE still contains our activity")
                    } else {
                        logger.i("Gateway", "SESSIONS_REPLACE no longer contains our activity — re-applying presence")
                        onPresenceDropped?.invoke()
                    }
                }
            }
            HEARTBEAT -> sendHeartBeat()
            RECONNECT -> reconnectWebSocket()
            INVALID_SESSION -> handleInvalidSession(jsonString)
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
                // resume_gateway_url is optional in the READY payload. Appending to
                // a null used to produce the invalid url "null/?v=10&encoding=json".
                resumeGatewayUrl = ready.resumeGatewayUrl?.let { "$it/?v=10&encoding=json" }
                logger.i("Gateway", "resume_gateway_url updated to $resumeGatewayUrl")
                logger.i("Gateway", "session_id updated to $sessionId")
                connected = true
                // A successful handshake ends the current reconnect cycle.
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected
                if (!handshakeUsedResume) {
                    // A fresh IDENTIFY creates an empty session; Discord does not
                    // carry the previous activity over. Ask the owner to re-apply it.
                    onPresenceDropped?.invoke()
                }
                return
            }
            "RESUMED" -> {
                logger.i("Gateway", "Session Resumed")
                connected = true
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected
            }
            else -> {}
        }
    }

    private suspend fun handleInvalidSession(jsonString: String) {
        // Discord's INVALID_SESSION `d` is a boolean: true means the session can
        // still be resumed, false means it is gone and a fresh IDENTIFY is needed.
        val resumable = runCatching {
            json.decodeFromString<PayloadData<Boolean>>(jsonString).d
        }.getOrNull() ?: false

        if (resumable && sequence > 0 && !sessionId.isNullOrBlank()) {
            logger.i("Gateway", "Invalid session (resumable) — resuming")
            // Discord asks clients to wait 1-5 seconds before resuming.
            delay(Random.nextLong(1_000L, 5_000L))
            sendResume()
        } else {
            logger.i("Gateway", "Invalid session (not resumable) — identifying again")
            sessionId = null
            sequence = 0
            resumeGatewayUrl = null
            delay(150)
            sendIdentify()
        }
    }

    private suspend fun handleHello(jsonString: String) {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        heartbeatInterval = decodePayloadData<Heartbeat>(jsonString)?.heartbeatInterval ?: return
        logger.i("Gateway", "Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    /**
     * Whether an incoming SESSIONS_REPLACE still contains the activity we last
     * sent. This replaces the old time-window heuristic, which re-applied the
     * presence when it was still there and skipped it when Discord had actually
     * cleared it (the exact "RPC stops working" case).
     */
    internal fun isOurActivityPresent(jsonString: String): Boolean {
        val appId = lastSentActivityApplicationId
        val name = lastSentActivityName
        if (appId == null && name == null) return false
        val sessions = runCatching {
            json.decodeFromString<PayloadData<List<SessionReplaceEntry>>>(jsonString).d
        }.getOrNull() ?: return false
        return sessions.any { session ->
            session.activities.any { activity ->
                // type 4 is a Custom Status, never our Rich Presence.
                activity.type != CUSTOM_STATUS_TYPE &&
                    ((appId != null && activity.applicationId == appId) ||
                        (name != null && activity.name == name))
            }
        }
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

    private suspend fun handleClose(session: DefaultWebSocketSession) {
        heartbeatJob?.cancel()
        connected = false
        val closeReason = session.closeReason.await()
        val code = closeReason?.code ?: -1
        if (!deliberateClose) {
            logger.w(
                "Gateway",
                "WebSocket closed: code=$code, reason=${closeReason?.message ?: "none"}"
            )
            // 4004 = Authentication failed (bad token, or an OAuth2 bearer
            // token used for IDENTIFY). Retrying with the same token creates an
            // endless reconnect loop and floods the log. Stop and require a
            // new login / token refresh instead.
            if (code.toInt() in NON_RESUMABLE_CLOSE_CODES) {
                logger.e("Gateway", "Non-resumable close ($code): authentication failed, not reconnecting")
                sessionId = null
                sequence = 0
                resumeGatewayUrl = null
                deliberateClose = true
                emitEvent(RpcEvent.ERROR, "Authentication failed ($code). Please log in again.")
                return
            }
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
        handshakeUsedResume = false
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
        handshakeUsedResume = true
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
        // "connected" only becomes true after READY/RESUMED. Checking just the
        // socket object reported a connection that had not authenticated yet
        // (and a dead one during the reconnect backoff), so callers skipped the
        // reconnect and the presence was silently dropped.
        return connected && websocket?.isActive == true &&
            websocket?.outgoing?.isClosedForSend == false
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?): Boolean {
        val socket = websocket
        if (socket?.isActive == true) {
            val payload = json.encodeToString(
                OutgoingPayload(
                    op = op,
                    d = d,
                )
            )
            logger.d("Gateway", "Outgoing op:$op: $payload")
            socket.send(Frame.Text(payload))
            return true
        }
        return false
    }

    override fun close() {
        deliberateClose = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        // Tear down the CURRENT connection, but keep the coroutine scope alive.
        //
        // Cancelling the scope (this.cancel()) used to permanently kill the gateway: every
        // later connect() launched on an already-cancelled job and therefore never ran, so
        // the RPC could never come back once it had been closed. That is what made switching
        // to another app in the apps list leave Discord showing the previous app forever.
        connectionJob?.cancel()
        connectionJob = null
        // Best-effort cancel; ignore failures (e.g. already cancelled).
        if (runCatching { websocket?.cancel() }.isFailure) {
        }
        websocket = null
        resumeGatewayUrl = null
        sessionId = null
        // A deliberate close starts a brand-new session on the next connect, so
        // the old sequence must not be used for a heartbeat before READY.
        sequence = 0
        awaitingHeartbeatAck = false
        handshakeUsedResume = false
        connected = false
        _connectionState.value = ConnectionState.Disconnected
        logger.i("Gateway", "Connection to gateway closed")
    }

    override suspend fun sendActivity(presence: Presence): Boolean {
        val connectedInTime = withTimeoutOrNull(CONNECT_WAIT_TIMEOUT_MS) {
            while (!isSocketConnectedToAccount()) {
                delay(250.milliseconds)
            }
            true
        }
        if (connectedInTime != true) {
            logger.w("Gateway", "Socket not connected within timeout — dropping presence update")
            return false
        }
        logger.i("Gateway", "Sending $PRESENCE_UPDATE")
        val sent = runCatching {
            send(
                op = PRESENCE_UPDATE,
                d = presence
            )
        }.getOrElse { e ->
            logger.e("Gateway", "Failed to send presence: ${e.message ?: ""}")
            false
        }
        if (sent) {
            // Remember what Discord should now show, so SESSIONS_REPLACE can be
            // checked against the real content instead of a time window.
            val primary = presence.activities?.firstOrNull()
            lastSentActivityApplicationId = primary?.applicationId
            lastSentActivityName = primary?.name
        }
        return sent
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

        // Client-presence format: labels in buttons, urls in metadata.
        val btnLabels = buttons?.map { it.label }
        val btnMetadata = buttons?.let { Metadata(buttonUrls = it.map { it.url }) }

        val activity = Activity(
            name = name,
            type = 0,
            state = state,
            details = details,
            timestamps = timestamps,
            assets = assets,
            buttons = btnLabels,
            metadata = btnMetadata
        )

        val presence = Presence(
            activities = listOf(activity),
            status = status,
            afk = afk
        )

        logger.i("Gateway", "Sending $PRESENCE_UPDATE — name=$name, details=$details")
        if (send(op = PRESENCE_UPDATE, d = presence)) {
            lastSentActivityApplicationId = activity.applicationId
            lastSentActivityName = activity.name
        }
    }

    suspend fun clearPresence() {
        if (!isSocketConnectedToAccount()) return
        logger.i("Gateway", "Clearing Rich Presence")
        // Only the activity is removed. Setting status="invisible" here (as the
        // old code did) also made the whole account appear offline to everyone,
        // which is not what "clear the activity" means.
        if (send(
                op = PRESENCE_UPDATE,
                d = Presence(
                    activities = emptyList(),
                    status = "online",
                    afk = false
                )
            )
        ) {
            lastSentActivityApplicationId = null
            lastSentActivityName = null
        }
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
        // Best-effort close; ignore failures (e.g. already closed).
        if (runCatching { websocket?.close() }.isFailure) {
        }
        websocket = null
        connected = false
        sessionId = null
        resumeGatewayUrl = null
        sequence = 0
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun connectWithToken(token: String) {
        refreshIdentifyProperties(token, identifyBrowser, identifyOs, identifyDevice)
        connect()
    }

    /**
     * Refreshes the token and platform-indicator properties before (re)connecting.
     *
     * These are stored as constructor parameters and were immutable, so a token or
     * platform change after DI never took effect. If the Media RPC service auto-starts
     * before the user logs in, the token is empty and Discord rejects the IDENTIFY,
     * so the presence is never sent.
     */
    public fun refreshIdentifyProperties(
        token: String,
        browser: String,
        os: String,
        device: String,
    ) {
        this.token = token
        this.identifyBrowser = browser
        this.identifyOs = os
        this.identifyDevice = device
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
