package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import javax.inject.Inject
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.CUSTOM_ACTIVITY_TYPE
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.ActivityButton
import kizzy.gateway.entities.presence.Assets
import kizzy.gateway.entities.presence.Party
import kizzy.gateway.entities.presence.Presence
import kizzy.gateway.entities.presence.Timestamps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.locks.ReentrantLock

class KizzyRPC @Inject constructor(
    private val token: String,
    private val kizzyRepository: KizzyRepository,
    private val discordWebSocket: DiscordWebSocket,
    private val logger: Logger
) {
    private val rpcScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    // Wire up SESSIONS_REPLACE callback to immediately re-apply presence
    // when Discord clears it (prevents activity disappearing for 1-2 seconds)
    init {
        if (discordWebSocket is DiscordWebSocketImpl) {
            discordWebSocket.onSessionsReplace = {
                rpcScope.launch {
                    launchReapplyPresence()
                }
            }
        }
    }
    private var presence: Presence = Presence(
        activities = emptyList(),
        afk = true,
        status = null
    )
    private var activityName: String? = null
    private var applicationIdNumber = Prefs[Prefs.CUSTOM_ACTIVITY_APPLICATION_ID, Constants.APPLICATION_ID]
    private var details: String? = null
    private var state: String? = null
    private var party: Party? = null
    private var largeImage: RpcImage? = null
    private var smallImage: RpcImage? = null
    private var largeText: String? = null
    private var smallText: String? = null
    private var status: String? = null
    private var startTimestamps: Long? = null
    private var stopTimestamps: Long? = null
    private var type: Int = 0
    private var platform: String? = null
    private var buttons = ArrayList<ActivityButton>()
    // Protects concurrent access to buttons from UI thread (setButton1/setButton2/clearButtons)
    // and Dispatchers.IO (updateRPC's buttons.toList() snapshot)
    private val buttonsLock = ReentrantLock()
    private var url: String? = null

    // Presence update deduplication + throttling to prevent flooding Discord gateway
    // (rapid SESSIONS_REPLACE events used to cause connection closures / INVALID_SESSION)
    companion object {
        private const val PRESENCE_MIN_INTERVAL_MS = 1500L
        private const val POST_CONNECT_DELAY_MS = 1000L
    }
    private var lastPresenceTimeMs: Long = 0L
    private var lastPresenceHash: Int = -1
    // Tracks when the WebSocket last connected/resumed; delays the first presence
    // update to avoid Discord 4003 (Not authenticated) right after RESUME.
    private var lastConnectedTimeMs: Long = 0L
    // Remember the last presence so we can re-apply after reconnect (fresh IDENTIFY
    // creates a brand-new, empty session; the previously-set activity is gone).
    private var lastPresence: Presence? = null

    /** Called by DiscordWebSocketImpl after a successful RESUME or READY. */
    suspend fun markConnected() {
        lastConnectedTimeMs = System.currentTimeMillis()
        // Re-apply the last presence after reconnect so Discord doesn't show an empty card
        launchReapplyPresence()
    }

    /**
     * Re-apply the last presence after a reconnect. Runs on the gateway's own scope so the
     * owning service cancelling its update coroutine cannot kill it.
     */
    private suspend fun launchReapplyPresence() {
        val presence = lastPresence ?: return
        withContext(Dispatchers.IO) {
            var attempt = 0
            val maxAttempts = 3
            while (attempt < maxAttempts) {
                attempt++
                delay(if (attempt == 1) 500L else 2000L)
                if (!discordWebSocket.isWebSocketConnected()) {
                    continue
                }
                try {
                    discordWebSocket.sendActivity(presence)
                    logger.i("KizzyRPC", "Presence re-applied after reconnect (attempt $attempt)")
                    return@withContext
                } catch (e: Exception) {
                    logger.e("KizzyRPC", "Presence re-apply attempt $attempt failed: ${e.message ?: ""}")
                }
            }
            logger.e("KizzyRPC", "Presence re-apply gave up after $maxAttempts attempts")
        }
    }

    private fun presenceHash(name: String?, details: String?, state: String?, timestamps: kizzy.gateway.entities.presence.Timestamps?, playbackState: Int?): Int {
        var result = 1
        // Only hash name/details/state/playbackState — NOT timestamps.
        // Timestamps change every millisecond during playback, which would change the hash
        // constantly and cause presence to be re-sent on every position update, flooding
        // Discord's gateway with duplicate PRESENCE_UPDATEs and causing the activity
        // to flash on/off rapidly.
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (details?.hashCode() ?: 0)
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + (playbackState?.hashCode() ?: 0)
        return result
    }

    /** Closes the WebSocket connection without blocking. Safe to call from any thread. */
    fun closeRPC() {
        discordWebSocket.close()
    }

    /** Clears Discord presence by sending an empty activity.
     * The WebSocket connection is kept alive so presence can resume when media starts again.
     * Must be called from a coroutine context. */
    suspend fun clearPresence() {
        // Send empty presence to clear Discord's activity card
        discordWebSocket.sendActivity(
            Presence(
                activities = emptyList(),
                afk = true,
                since = null,
                status = null
            )
        )
    }

    fun isRpcRunning(): Boolean {
        return discordWebSocket.isWebSocketConnected()
    }

    /**
     * Use Regex to check if token are valid
     * @return true if token is valid else false
     *
     * source: [#token-structure](https://gist.github.com/aydynx/5d29e903417354fd25641b98efc9d437#token-structure)
     */
    private fun isUserTokenValid(): Boolean {
        /*val regex = Regex(
            "[a-z\\d]{24}\\.[a-z\\d]{6}\\.([\\w-]{107}|[\\w-]{38}|[\\w-]{27})|mfa\\.[a-z\\d]{8,}",
            RegexOption.IGNORE_CASE
        )
        return regex.matches(token)*/
        return token.isNotBlank()
    }

    /**
     * Activity Name of Rpc
     *
     * @param activityName Activity name
     * @return
     */
    fun setName(activityName: String?): KizzyRPC {
        this.activityName = activityName
        return this
    }

    /**
     * Details of Rpc
     *
     * @param details
     * @return
     */
    fun setDetails(details: String?): KizzyRPC {
        this.details = details
        return this
    }

    /**
     * Rpc State
     *
     * @param state
     * @return
     */
    fun setState(state: String?): KizzyRPC {
        this.state = state
        return this
    }

    /**
     * Party of Rpc
     *
     * @param current Current party size
     * @param max Max party size
     * @return
     */
    fun setPartySize(current: Int?, max: Int?): KizzyRPC {
        if (current != null && max != null) {
            this.party = Party(
                id = "kizzy",
                size = arrayOf(current, max)
            )
        }
        return this
    }

    /**
     * Large image on rpc
     *
     * How to get Image ?
     * Upload image to any discord chat and copy its media link it should look like "https://media.discordapp.net/attachments/90202992002/xyz.png" now just use the image link from attachments part
     * so it would look like: .setLargeImage("attachments/90202992002/xyz.png")
     * @param largeImage
     * @return
     */
    fun setLargeImage(largeImage: RpcImage?, largeText: String? = null): KizzyRPC {
        this.largeText = largeText.takeIf { !it.isNullOrEmpty() }
        this.largeImage = largeImage
        return this
    }

    /**
     * Small image on Rpc
     *
     * @param smallImage
     * @return
     */
    fun setSmallImage(smallImage: RpcImage?, smallText: String? = null): KizzyRPC {
        this.smallText = smallText.takeIf { !it.isNullOrEmpty() }
        this.smallImage = smallImage
        return this
    }

    /**
     * Start timestamps
     *
     * @param startTimestamps
     * @return
     */
    fun setStartTimestamps(startTimestamps: Long?): KizzyRPC {
        this.startTimestamps = startTimestamps
        return this
    }

    /**
     * Stop timestamps
     *
     * @param stopTimestamps
     * @return
     */
    fun setStopTimestamps(stopTimestamps: Long?): KizzyRPC {
        this.stopTimestamps = stopTimestamps
        return this
    }

    /**
     * Activity Types
     *
     * 0: Playing
     * 1: Streaming
     * 2: Listening
     * 3: Watching
     * 5: Competing
     *
     * @param type
     * @return
     */

    fun setType(type: Int): KizzyRPC {
        if (type in 0..5)
            this.type = type
        else this.type = 0
        return this
    }

    /** Status type for profile online,idle,dnd
     *
     * @param status
     * @return
     */
    fun setStatus(status: String?): KizzyRPC {
        this.status = status
        return this
    }

    /**
     * Platform type for profile xbox,playstation,pc,ios,android etc.
     *
     * @param platform
     * @return
     */
    fun setPlatform(platform: String?): KizzyRPC {
        this.platform = platform
        return this
    }

    /**
     * Clears any previously set buttons and their urls.
     * Useful before re-applying buttons on a reconnect so they don't accumulate.
     */
    fun clearButtons(): KizzyRPC {
        buttonsLock.lock()
        try {
            buttons.clear()
        } finally {
            buttonsLock.unlock()
        }
        return this
    }

    /**
     * Button1 text and url
     *
     * @param label Button label text
     * @param url Button URL
     * @return
     */
    fun setButton1(label: String?, url: String?): KizzyRPC {
        label?.let { lbl ->
            buttonsLock.lock()
            try {
                buttons.add(ActivityButton(label = lbl, url = url.orEmpty()))
            } finally {
                buttonsLock.unlock()
            }
        }
        return this
    }

    /**
     * Button2 text and url
     *
     * @param button2Text Button label text
     * @param url Button URL
     * @return
     */
    fun setButton2(button2Text: String?, url: String? = null): KizzyRPC {
        button2Text?.let { txt ->
            buttonsLock.lock()
            try {
                buttons.add(ActivityButton(label = txt, url = url.orEmpty()))
            } finally {
                buttonsLock.unlock()
            }
        }
        return this
    }

    /**
     * Button1 url
     *
     * @param url
     * @return
     */
    fun setButton1URL(url: String?): KizzyRPC {
        // Deprecated - use setButton1(label, url) instead
        return this
    }

    /**
     * Button2 url
     *
     * @param url
     * @return
     */
    fun setButton2URL(url: String?): KizzyRPC {
        // Deprecated - use setButton2(text, url) instead
        return this
    }
    /**
     * Overrides the Discord Application ID used for the activity.
     * The application's icon and name are what Discord shows on the presence,
     * so a custom id lets the user swap the default (Kizzy) icon for their own.
     * Passing null/blank falls back to the default application id.
     */
    fun setApplicationId(applicationId: String?): KizzyRPC {
        this.applicationIdNumber = applicationId?.takeIf { it.isNotEmpty() } ?: Constants.APPLICATION_ID
        return this
    }

    /**
     * Streaming Url
     *
     * @param url The streaming type currently only supports Twitch and YouTube.
     * Only https://twitch.tv/ and https://youtube.com/ urls will work
     */
    fun setStreamUrl(url: String?): KizzyRPC {
        this.url = url
        return this
    }

    private fun String.sanitize(): String {
        return if (this.length > 128) {
            this.substring(0, 128)
        } else {
            this
        }
    }

    suspend fun build() {
        presence = Presence(
            activities = listOf(
                Activity(
                    name = activityName,
                    state = state?.sanitize(),
                    details = details?.sanitize(),
                    party = party.takeIf { party != null },
                    type = type,
                    platform = platform?.sanitize(),
                    timestamps = Timestamps(
                        start = startTimestamps,
                        end = stopTimestamps
                    ).takeIf { startTimestamps != null || stopTimestamps != null },
                    assets = Assets(
                        largeImage = largeImage?.resolveImage(kizzyRepository),
                        smallImage = smallImage?.resolveImage(kizzyRepository),
                        largeText = largeText?.sanitize(),
                        smallText = smallText?.sanitize()
                    ).takeIf { largeImage != null || smallImage != null },
                    buttons = buttons.takeIf { buttons.isNotEmpty() },
                    applicationId = applicationIdNumber.takeIf { it.isNotEmpty() } ?: Constants.APPLICATION_ID,
                    url = url
                )
            ),
            afk = true,
            since = startTimestamps,
            status = status
        )
        // Remember the presence so we can re-apply after reconnect
        lastPresence = presence
        connectToWebSocket()
    }
    private suspend fun connectToWebSocket() {
        if (!isUserTokenValid()) {
            logger.e(
                tag = "KizzyRPC",
                event = "Token Seems to be invalid, Please Login if you haven't"
            )
            return
        }
        // Don't reconnect if already connected - prevents multiple concurrent WebSocket connections
        // that cause Discord 4003 Not authenticated errors
        if (discordWebSocket.isWebSocketConnected()) {
            logger.d("KizzyRPC", "WebSocket already connected, skipping connect")
            return
        }
        discordWebSocket.connect()
        markConnected()
        discordWebSocket.sendActivity(presence)
    }

    suspend fun updateRPC(commonRpc: CommonRpc, enableTimestamps: Boolean? = true, status: String? = null, forceRefresh: Boolean = false) {
        // Don't check isRpcRunning() here - sendActivity() will wait for the socket to connect
        // (up to CONNECT_WAIT_TIMEOUT_MS) before sending. This prevents media detection from
        // being lost when the gateway hasn't finished connecting yet.
        // Convert com.my.kizzy.data.rpc.Timestamps -> kizzy.gateway.entities.presence.Timestamps
        val time = commonRpc.time?.let { t ->
            kizzy.gateway.entities.presence.Timestamps(start = t.start, end = t.end)
        }.takeIf { enableTimestamps == true }
        if (commonRpc.partyCurrentSize != null && commonRpc.partyMaxSize != null)
            Party(
                id = "kizzy",
                size = arrayOf(commonRpc.partyCurrentSize, commonRpc.partyMaxSize)
            ).also { party = it }
        val effectiveStatus = status ?: this.status

        // Ensure the Discord WebSocket is connected before sending presence.
        // updateRPC() is called from media detection without going through build()
        // which used to call connectToWebSocket(). Without this, sendActivity()
        // waits 15s for a socket that never connects and presence is silently dropped.
        val wasConnected = discordWebSocket.isWebSocketConnected()
        discordWebSocket.connect()

        // Delay first presence update after connect/resume to avoid Discord 4003 (Not authenticated)
        // when presence is sent immediately after RESUME/READY.
        if (!wasConnected) {
            lastConnectedTimeMs = System.currentTimeMillis()
            val timeSinceConnect = System.currentTimeMillis() - lastConnectedTimeMs
            if (timeSinceConnect > 0L && timeSinceConnect < POST_CONNECT_DELAY_MS) {
                logger.d(
                    "KizzyRPC",
                    "Delaying presence update ${POST_CONNECT_DELAY_MS - timeSinceConnect}ms after connect"
                )
                delay(POST_CONNECT_DELAY_MS - timeSinceConnect)
            }
            // Reset presence hash so presence is re-sent after WebSocket reconnect
            lastPresenceHash = -1
            lastPresenceTimeMs = 0
        }

        // Deduplication: skip if within minimum interval OR presence data unchanged.
        // Always enforce minimum interval to prevent flooding Discord's gateway with
        // rapid PRESENCE_UPDATEs when SESSIONS_REPLACE events fire quickly (e.g.,
        // YouTube/Spotify media sessions changing position or state rapidly).
        val now = System.currentTimeMillis()
        val hash = presenceHash(commonRpc.name, commonRpc.details, commonRpc.state, time, commonRpc.playbackState)
        val intervalElapsed = now - lastPresenceTimeMs >= PRESENCE_MIN_INTERVAL_MS
        // Always send on first call (lastPresenceTimeMs == 0) so Discord receives
        // the initial presence even if WebSocket was still connecting.
        val isFirstRun = lastPresenceTimeMs == 0L
        if (!forceRefresh && !isFirstRun && (!intervalElapsed || hash == lastPresenceHash)) {
            logger.d(
                "KizzyRPC",
                "Skipping presence update (intervalElapsed=$intervalElapsed, hashChanged=${hash != lastPresenceHash})"
            )
            return
        }

        buttonsLock.lock()
        val buttonsSnapshot = try {
            buttons.toList().takeIf { it.isNotEmpty() }
        } finally {
            buttonsLock.unlock()
        }
        val presence = Presence(
            activities = listOf(
                Activity(
                    name = commonRpc.name,
                    details = commonRpc.details?.takeIf { it.isNotEmpty() }?.sanitize(),
                    state = commonRpc.state?.takeIf { it.isNotEmpty() }?.sanitize(),
                    type = commonRpc.type ?: Prefs[CUSTOM_ACTIVITY_TYPE, 0],
                    platform = commonRpc.platform?.sanitize(),
                    timestamps = time,
                    assets = Assets(
                        largeImage = commonRpc.largeImage?.resolveImage(kizzyRepository),
                        smallImage = commonRpc.smallImage?.resolveImage(kizzyRepository),
                        largeText = commonRpc.largeText?.sanitize(),
                        smallText = commonRpc.smallText?.sanitize()
                    ).takeIf { commonRpc.largeImage != null || commonRpc.smallImage != null },
                    party = party.takeIf { party != null },
                    buttons = buttonsSnapshot,

                    applicationId = applicationIdNumber.takeIf { it.isNotEmpty() }
                        ?: Constants.APPLICATION_ID

                )
            ),
            afk = true,
            since = time?.start,
            status = effectiveStatus
        )
        discordWebSocket.sendActivity(presence)
        // Remember the presence so we can re-apply after SESSIONS_REPLACE reconnect
        lastPresence = presence
        // Only update cache AFTER successful upload - if sendActivity() timed out
        // (socket not connected within 15s), we want to retry next time
        lastPresenceHash = hash
        lastPresenceTimeMs = now
    }
}