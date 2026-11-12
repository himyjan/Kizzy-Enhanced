package com.my.kizzy.data.rpc

import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.CUSTOM_ACTIVITY_TYPE
import com.my.kizzy.data.rpc.Constants
import javax.inject.Inject
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
import kotlin.math.abs

class KizzyRPC @Inject constructor(
    private var token: String,
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

        /**
         * How far the start/end timestamps have to move before it counts as a seek.
         * Players report their position with coarse granularity (often whole seconds), so a
         * smaller tolerance would report a jump on every single update.
         */
        private const val TIMELINE_JUMP_TOLERANCE_MS = 3000L

        /**
         * Minimum time between two presence re-applies. Discord answers every
         * PRESENCE_UPDATE with SESSIONS_REPLACE, so re-applying on every one of those
         * events would loop forever and flood the gateway.
         */
        private const val REAPPLY_COOLDOWN_MS = 10000L
    }
    private var lastPresenceTimeMs: Long = 0L
    private var lastPresenceHash: Int = -1
    // Timestamp of the last presence re-apply, used to break the SESSIONS_REPLACE loop.
    private var lastReapplyTimeMs: Long = 0L
    // Start/end timestamps of the last presence that was actually sent. Used to detect seeks.
    private var lastPresenceStart: Long? = null
    private var lastPresenceEnd: Long? = null
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
     * Refreshes the Discord gateway token and platform-indicator properties from
     * preferences before (re)connecting.
     *
     * Both [token] and the identify properties on [discordWebSocket] are immutable
     * constructor parameters. If the Media RPC service auto-starts before the user
     * logs in (e.g. from Application.onCreate), the token is empty and Discord rejects
     * the IDENTIFY, so the presence is never sent. Refreshing before every connect
     * ensures a late login still works without restarting the service.
     */
    suspend fun refreshFromPrefs() {
        token = Prefs[Prefs.TOKEN, ""]
        val (browser, os, device) = Constants.spoofIdentifyProperties(
            Prefs[Prefs.EXPERIMENTAL_RPC_PLATFORM, ""]
        )
        if (discordWebSocket is DiscordWebSocketImpl) {
            discordWebSocket.refreshIdentifyProperties(token, browser, os, device)
        }
    }

    /**
     * Re-apply the last presence after a reconnect. Runs on the gateway's own scope so the
     * owning service cancelling its update coroutine cannot kill it.
     *
     * Discord answers every PRESENCE_UPDATE with a SESSIONS_REPLACE event, so re-applying
     * the presence on every such event creates a feedback loop (re-apply -> SESSIONS_REPLACE
     * -> re-apply -> ...) that floods the gateway. A cooldown breaks that loop while still
     * restoring the activity after a genuine session replacement.
     */
    private suspend fun launchReapplyPresence() {
        val presence = lastPresence ?: return
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (now - lastReapplyTimeMs < REAPPLY_COOLDOWN_MS) {
                logger.d(
                    "KizzyRPC",
                    "Skipping presence re-apply (within ${REAPPLY_COOLDOWN_MS}ms cooldown)"
                )
                return@withContext
            }
            lastReapplyTimeMs = now
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

    private fun presenceHash(commonRpc: CommonRpc, buttons: List<ActivityButton>?): Int {
        var result = 1
        result = 31 * result + (commonRpc.name?.hashCode() ?: 0)
        result = 31 * result + (commonRpc.details?.hashCode() ?: 0)
        result = 31 * result + (commonRpc.state?.hashCode() ?: 0)
        result = 31 * result + (commonRpc.playbackState?.hashCode() ?: 0)
        // The activity type and the platform change the way Discord renders the
        // presence, so they belong to the fingerprint. Without them a changed
        // activity type would be dropped as "content unchanged" and would
        // never reach Discord.
        result = 31 * result + (commonRpc.type ?: 0)
        result = 31 * result + (commonRpc.platform?.hashCode() ?: 0)
        result = 31 * result + (buttons?.hashCode() ?: 0)
        return result
    }

    /**
     * Detects a "timeline jump" — the user seeking to a different position in the track.
     *
     * The start/end timestamps are computed as "now - position", so while a track plays
     * normally both the clock and the position advance at the same rate and the start
     * timestamp stays (almost) constant. Seeking shifts it by the seek distance, which is
     * what has to be pushed to Discord for the progress bar to jump.
     *
     * Comparing the raw values is not enough because players report their position with
     * coarse granularity, so a tolerance is applied to ignore that jitter.
     */
    private fun isTimelineJump(timestamps: Timestamps?): Boolean {
        if (timestamps == null) return false
        val newStart = timestamps.start
        val newEnd = timestamps.end
        if (newStart != null && lastPresenceStart != null &&
            abs(newStart - lastPresenceStart!!) > TIMELINE_JUMP_TOLERANCE_MS
        ) return true
        if (newEnd != null && lastPresenceEnd != null &&
            abs(newEnd - lastPresenceEnd!!) > TIMELINE_JUMP_TOLERANCE_MS
        ) return true
        return false
    }

    /** Closes the WebSocket connection without blocking. Safe to call from any thread. */
    fun closeRPC() {
        discordWebSocket.close()
    }

    /** Clears Discord presence by sending an empty activity.
     * The WebSocket connection is kept alive so presence can resume when media starts again.
     * Must be called from a coroutine context. */
    suspend fun clearPresence() {
        // Send empty presence to clear Discord's activity card.
        // The status must be a valid value ("invisible" hides the activity entirely).
        discordWebSocket.sendActivity(
            Presence(
                activities = emptyList(),
                afk = true,
                since = null,
                status = "invisible"
            )
        )
        // Forget the cached presence so the next update is not treated as unchanged.
        lastPresence = null
        lastPresenceHash = -1
        lastPresenceStart = null
        lastPresenceEnd = null
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
     * The buttons of the presence that was sent last.
     *
     * The RPC preview shows them, so the preview shows the same buttons as
     * Discord.
     */
    fun currentButtons(): List<ActivityButton>? {
        buttonsLock.lock()
        return try {
            buttons.toList().takeIf { it.isNotEmpty() }
        } finally {
            buttonsLock.unlock()
        }
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

    /**
     * Discord's gateway expects `status` to be one of online/idle/dnd/offline/invisible.
     * Sending an explicit `null` makes Discord ignore the whole PRESENCE_UPDATE, so the
     * activity never shows up on the profile even though the payload looked correct.
     */
    private fun effectiveStatus(explicit: String?): String =
        explicit?.takeIf { it.isNotBlank() } ?: status?.takeIf { it.isNotBlank() } ?: "online"

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
            // The user is actively playing something, so they are not AFK. `afk = true`
            // together with a `since` timestamp made Discord render the profile as idle.
            afk = false,
            since = null,
            status = effectiveStatus(null)
        )
        // Remember the presence so we can re-apply after reconnect
        lastPresence = presence
        connectToWebSocket()
    }
    private suspend fun connectToWebSocket() {
        // Refresh token and identify properties from prefs before connecting, so a
        // login that happened after DI (e.g. service auto-started before login) takes effect.
        refreshFromPrefs()

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
            lastPresenceStart = null
            lastPresenceEnd = null
        }

        // Deduplication.
        //
        // A presence is only re-sent when something actually changed: a different
        // track/app, a different play state, or the user seeking (timeline jump).
        // Re-sending an identical presence every few seconds is pure gateway traffic.
        //
        // IMPORTANT: a changed presence must never be DROPPED. The old code skipped
        // whenever the minimum interval had not elapsed, which silently swallowed the
        // update - the RPC then kept showing the previous app until the next pause/play.
        // Instead we wait out the remainder of the interval and then send, so the new
        // state always reaches Discord (just not faster than the rate limit allows).
        val now = System.currentTimeMillis()

        // The buttons are part of the presence, so they have to be part of the
        // fingerprint as well. Otherwise switching a button off would be dropped
        // as "content unchanged" and the old button would stay on the profile.
        buttonsLock.lock()
        val buttonsSnapshot = try {
            buttons.toList().takeIf { it.isNotEmpty() }
        } finally {
            buttonsLock.unlock()
        }

        val hash = presenceHash(commonRpc, buttonsSnapshot)
        val contentChanged = hash != lastPresenceHash
        val timelineJumped = isTimelineJump(time)
        // Always send on first call (lastPresenceTimeMs == 0) so Discord receives
        // the initial presence even if WebSocket was still connecting.
        val isFirstRun = lastPresenceTimeMs == 0L

        if (!forceRefresh && !isFirstRun && !contentChanged && !timelineJumped) {
            logger.d("KizzyRPC", "Skipping presence update (content unchanged)")
            return
        }

        if (!forceRefresh && !isFirstRun) {
            val elapsed = now - lastPresenceTimeMs
            if (elapsed < PRESENCE_MIN_INTERVAL_MS) {
                logger.d(
                    "KizzyRPC",
                    "Presence changed - delaying ${PRESENCE_MIN_INTERVAL_MS - elapsed}ms to respect the rate limit"
                )
                delay(PRESENCE_MIN_INTERVAL_MS - elapsed)
            }
        }

        // The images are resolved once, so the presence and the memory of the
        // presence show the same artwork.
        val resolvedLargeImage = commonRpc.largeImage?.resolveImage(kizzyRepository)
        val resolvedSmallImage = commonRpc.smallImage?.resolveImage(kizzyRepository)

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
                        largeImage = resolvedLargeImage,
                        smallImage = resolvedSmallImage,
                        largeText = commonRpc.largeText?.sanitize(),
                        smallText = commonRpc.smallText?.sanitize()
                    ).takeIf { commonRpc.largeImage != null || commonRpc.smallImage != null },
                    party = party.takeIf { party != null },
                    buttons = buttonsSnapshot,

                    applicationId = applicationIdNumber.takeIf { it.isNotEmpty() }
                        ?: Constants.APPLICATION_ID

                )
            ),
            // The user is actively doing something, so they are not AFK. Sending
            // `afk = true` made Discord render the profile as idle.
            afk = false,
            since = null,
            status = effectiveStatus(status)
        )
        discordWebSocket.sendActivity(presence)
        // Remember the presence so we can re-apply after SESSIONS_REPLACE reconnect
        lastPresence = presence
        // Only update cache AFTER successful upload - if sendActivity() timed out
        // (socket not connected within 15s), we want to retry next time
        lastPresenceHash = hash
        lastPresenceTimeMs = now
        // Track the timeline that was sent so the next update can tell whether the user
        // seeked (timeline jump) or the track simply kept playing.
        lastPresenceStart = time?.start
        lastPresenceEnd = time?.end

        // Remember the presence for the RPC preview, so it shows what Discord
        // actually displays even when the service is not running anymore.
        rememberLastPresence(
            commonRpc = commonRpc,
            buttonsSnapshot = buttonsSnapshot,
            time = time,
            largeImage = resolvedLargeImage,
            smallImage = resolvedSmallImage,
        )
    }

    /**
     * Remembers the presence that was sent last.
     */
    private fun rememberLastPresence(
        commonRpc: CommonRpc,
        buttonsSnapshot: List<ActivityButton>?,
        time: kizzy.gateway.entities.presence.Timestamps?,
        largeImage: String?,
        smallImage: String?,
    ) {
        val button1 = buttonsSnapshot?.getOrNull(0)
        val button2 = buttonsSnapshot?.getOrNull(1)
        val config = com.my.kizzy.domain.model.rpc.RpcConfig(
            name = commonRpc.name,
            details = commonRpc.details.orEmpty(),
            state = commonRpc.state.orEmpty(),
            type = (commonRpc.type ?: 0).toString(),
            timestampsStart = time?.start?.toString().orEmpty(),
            timestampsStop = time?.end?.toString().orEmpty(),
            largeImg = largeImage.orEmpty(),
            smallImg = smallImage.orEmpty(),
            largeText = commonRpc.largeText.orEmpty(),
            smallText = commonRpc.smallText.orEmpty(),
            button1 = button1?.label.orEmpty(),
            button1link = button1?.url.orEmpty(),
            button2 = button2?.label.orEmpty(),
            button2link = button2?.url.orEmpty(),
        )
        LastPresence.rememberMedia(config)
    }
}