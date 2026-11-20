/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRPC.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import android.media.session.PlaybackState
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.CUSTOM_ACTIVITY_TYPE
import jakarta.inject.Inject
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.presence.*
import kizzy.gateway.entities.presence.Timestamps
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

class KizzyRPC @Inject constructor(
    private var token: String,
    private val kizzyRepository: KizzyRepository,
    private val discordWebSocket: DiscordWebSocket,
    private val logger: Logger
) {
    private val rpcScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    // Re-apply the presence when Discord reports that our activity is no longer
    // part of the session list (it was cleared or replaced by another client).
    init {
        if (discordWebSocket is DiscordWebSocketImpl) {
            discordWebSocket.onPresenceDropped = {
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
        /**
         * Discord allows 5 presence updates per 20 seconds per session. 1500 ms
         * allowed ~13, so a busy media session could exceed the limit and have
         * updates silently dropped (the presence then stopped updating until the
         * next track). 4000 ms matches the documented budget.
         */
        private const val PRESENCE_MIN_INTERVAL_MS = 4000L
        private const val POST_CONNECT_DELAY_MS = 2000L
        // Max consecutive connection failures before backing off
        private const val MAX_CONNECTION_FAILURES = 5

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
         *
         * Kept low (1s) because the app may rapidly switch RPC types (MediaRpc ->
         * None -> MediaRpc), tearing down and recreating the WebSocket. A 10s cooldown
         * would prevent the presence from ever being re-applied after a service restart.
         */
        private const val REAPPLY_COOLDOWN_MS = 1000L

        /**
         * Sanitizes a button url so it can never break the whole presence.
         *
         * Discord Gateway OP 3 rejects the ENTIRE activity when a button url is
         * empty, has no http(s) scheme, contains leading/trailing whitespace, or
         * exceeds 512 characters. Returning null makes the caller omit the button.
         */
        private fun sanitizeButtonUrl(raw: String?): String? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            if (trimmed.length > 512) return null
            return trimmed
        }

        /** Button labels must be 1..32 characters. */
        private fun sanitizeButtonLabel(raw: String?): String? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            return trimmed.take(32)
        }

        /**
         * The top-level activity `url` is ONLY valid for type == 1 (Streaming) and
         * must be a Twitch or YouTube url. Any other type with a non-null url causes
         * Discord to silently drop the whole activity.
         */
        private fun sanitizeStreamUrl(raw: String?, type: Int?): String? {
            if (type != 1) return null
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            val lower = trimmed.lowercase()
            val valid = lower.contains("twitch.tv") || lower.contains("youtube.com") || lower.contains("youtu.be")
            if (!valid) return null
            if (trimmed.length > 512) return null
            return trimmed
        }

        /**
         * Sanitizes a rich-presence field URL (details_url / state_url / large_url /
         * small_url). Unlike [String.sanitize] — which truncates display TEXT to 128
         * chars — these URLs must be kept whole: truncating a YouTube/Spotify link to
         * 128 chars corrupts it and Discord silently makes it unclickable. Discord
         * accepts http(s) URLs up to 512 chars here; anything else is dropped.
         */
        private fun sanitizeFieldUrl(raw: String?): String? {
            val trimmed = raw?.trim() ?: return null
            if (trimmed.isEmpty()) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            if (trimmed.length > 512) return null
            return trimmed
        }

        /** Discord allows at most 2 buttons. Invalid entries are dropped. */
        private fun sanitizeButtons(buttons: List<ActivityButton>?): List<ActivityButton>? {
            if (buttons.isNullOrEmpty()) return null
            val clean = buttons.mapNotNull { b ->
                val label = sanitizeButtonLabel(b.label) ?: return@mapNotNull null
                val url = sanitizeButtonUrl(b.url) ?: return@mapNotNull null
                ActivityButton(label = label, url = url)
            }.take(2)
            return clean.takeIf { it.isNotEmpty() }
        }
    }
    /**
     * Serializes every presence that is actually sent.
     *
     * Media detection triggers many update calls at once (metadata + playback state +
     * timeline ticker). Without this lock two calls can both compute "content changed",
     * both pass the rate limit and both send - and the OLDER one may reach the gateway
     * last, leaving Discord stuck on the previous song. Holding the lock across the
     * dedup check, the rate-limit wait and the send makes the last call win.
     */
    private val presenceSendMutex = Mutex()
    private var lastPresenceTimeMs: Long = 0L
    private var lastPresenceHash: Int = -1
    // Timestamp of the last presence re-apply, used to break the SESSIONS_REPLACE loop.
    private var lastReapplyTimeMs: Long = 0L
    // Connection state tracking for reliable RPC
    @Volatile
    private var isConnected = false
    // Connection failure count for exponential backoff
    private var connectionFailures = 0
    // Image urls resolved for the presence that was sent last. The RPC preview uses
    // them so it can show the same cover art as Discord.
    @Volatile
    private var lastResolvedLargeImage: String? = null
    @Volatile
    private var lastResolvedSmallImage: String? = null
    // Start/end timestamps of the last presence that was actually sent. Used to detect seeks.
    private var lastPresenceStart: Long? = null
    private var lastPresenceEnd: Long? = null
    // Tracks when the WebSocket last connected/resumed; delays the first presence
    // update to avoid Discord 4003 (Not authenticated) right after RESUME.
    private var lastConnectedTimeMs: Long = 0L
    // Remember the last presence so we can re-apply after reconnect (fresh IDENTIFY
    // creates a brand-new, empty session; the previously-set activity is gone).
    private var lastPresence: Presence? = null

    /**
     * Called by the connect flow right after [connect] has been requested.
     *
     * The gateway itself invokes [DiscordWebSocketImpl.onPresenceDropped] once
     * READY arrives (a fresh IDENTIFY session starts empty), so this only has to
     * cover the reconnect case where the socket is already READY.
     */
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
        // Discord's Gateway IDENTIFY only accepts an ACCOUNT token
        // (user_id.timestamp.hmac). An OAuth2 bearer token
        // (Authorization: Bearer ...) is rejected with close code 4004
        // "Authentication failed". The "OAuth2" connection method therefore
        // still needs the account token captured by the WebView login - the
        // bearer token is only used for REST API calls, not for the gateway.
        //
        // Prefer the account token; only fall back to the OAuth2 value when it
        // is itself an account token (the settings dialog may store one there).
        val accountToken = Prefs[Prefs.TOKEN, ""]
        val oauthToken = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        val connectionMethod = Prefs[Prefs.RPC_CONNECTION_METHOD, Prefs.RPC_CONNECTION_METHOD_GATEWAY]

        val isAccountToken: (String) -> Boolean = { value ->
            // Account tokens are "<base64-ish id>.<timestamp>.<hmac>";
            // OAuth2 access tokens (JWT-like / opaque) have no such structure.
            val parts = value.split(".")
            parts.size >= 3 && parts[0].isNotBlank() &&
                    parts[1].toLongOrNull() != null || value.startsWith("mfa.")
        }
        val newToken = when {
            accountToken.isNotBlank() -> accountToken
            isAccountToken(oauthToken) -> {
                // Keep the gateway and the account-token pref in sync.
                Prefs[Prefs.TOKEN] = oauthToken
                oauthToken
            }
            else -> {
                if (oauthToken.isNotBlank()) {
                    logger.w(
                        "KizzyRPC",
                        "connectionMethod=$connectionMethod but only an OAuth2 bearer token is available. " +
                                "The gateway will reject it with 4004 - open Discord Login to capture the account token."
                    )
                }
                ""
            }
        }
        token = newToken

        logger.d("KizzyRPC", "refreshFromPrefs: connectionMethod=$connectionMethod tokenLen=${token.length}")

        // Never mutate/delete the stored token here. refreshFromPrefs() runs on every
        // service start and settings change; deleting a freshly obtained OAuth2 token
        // caused an endless "authorize -> token cleared" loop. Discord is the only
        // authority on whether a token is valid, so let IDENTIFY fail naturally.
        logger.d(
            "KizzyRPC",
            "token shape: parts=${newToken.count { it == '.' } + if (newToken.isEmpty()) 0 else 1} len=${newToken.length}"
        )

        // If the token changed (e.g. OAuth2 login after bot-token session, or logout),
        // reconnect the WebSocket with the new token. Discord authenticates based on
        // the IDENTIFY handshake token, so simply updating the in-memory token is not
        // enough on an already-open socket. updateTokenAndReconnect() closes the old
        // socket and establishes a new one with the new token.
        val tokenChanged = newToken != token
        token = newToken

        if (tokenChanged && discordWebSocket.isWebSocketConnected()) {
            logger.d("KizzyRPC", "Token changed, reconnecting WebSocket with new token")
            (discordWebSocket as DiscordWebSocketImpl).updateTokenAndReconnect(token)
        }

        // Blank stored value (old default "") should identify as Android/mobile,
        // not fall through to the desktop identify.
        val platform = Prefs[Prefs.MANUAL_RPC_PLATFORM, "android"]
            .takeIf { it.isNotBlank() } ?: "android"
        val (browser, os, device) = Constants.spoofIdentifyProperties(platform)
        if (discordWebSocket is DiscordWebSocketImpl) {
            discordWebSocket.refreshIdentifyProperties(token, browser, os, device)
        }
    }

    /**
     * Re-apply the last presence after Discord dropped it (SESSIONS_REPLACE without our
     * activity, or a fresh IDENTIFY session). Runs on the gateway's own scope so the
     * owning service cancelling its update coroutine cannot kill it.
     *
     * The gateway only calls this when the activity is genuinely missing, so there is no
     * echo feedback loop. The cooldown is a safety net against a misbehaving server that
     * keeps sending a session list without the activity we just sent.
     */
    private suspend fun launchReapplyPresence() {
        if (lastPresence == null) return
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (now - lastReapplyTimeMs < REAPPLY_COOLDOWN_MS) {
                logger.d(
                    "KizzyRPC",
                    "Skipping presence re-apply (within ${REAPPLY_COOLDOWN_MS}ms cooldown)"
                )
                return@withContext
            }
            // Claim the slot before the first delay so two callers (the connect
            // flow and the gateway's READY callback) cannot both start a re-apply.
            lastReapplyTimeMs = now
            var attempt = 0
            val maxAttempts = 3
            var attemptedSend = false
            while (attempt < maxAttempts) {
                attempt++
                delay(if (attempt == 1) 500L else 2000L)
                if (!discordWebSocket.isWebSocketConnected()) {
                    continue
                }
                attemptedSend = true
                try {
                    // Read the presence INSIDE the lock: a track change may have been
                    // sent while this re-apply was waiting, and re-sending the older
                    // presence afterwards would revert Discord to the previous song.
                    val sent = presenceSendMutex.withLock {
                        val latest = lastPresence ?: return@withLock false
                        discordWebSocket.sendActivity(latest)
                    }
                    if (sent) {
                        logger.i("KizzyRPC", "Presence re-applied (attempt $attempt)")
                        return@withContext
                    }
                } catch (e: Exception) {
                    logger.e("KizzyRPC", "Presence re-apply attempt $attempt failed: ${e.message ?: ""}")
                }
            }
            if (!attemptedSend) {
                // The gateway never reached READY in time. Release the cooldown so
                // the READY callback can re-apply the presence as soon as it arrives.
                lastReapplyTimeMs = 0L
                logger.d("KizzyRPC", "Presence re-apply postponed: gateway not ready yet")
            } else {
                logger.e("KizzyRPC", "Presence re-apply gave up after $maxAttempts attempts")
            }
        }
    }

    private fun presenceHash(commonRpc: CommonRpc, buttons: List<ActivityButton>?): Int {
        var result = 1
        result = 31 * result + commonRpc.name.hashCode()
        result = 31 * result + (commonRpc.details?.hashCode() ?: 0)
        result = 31 * result + (commonRpc.state?.hashCode() ?: 0)
        result = 31 * result + normalizedPlaybackState(commonRpc.playbackState)
        // The activity type and the platform change the way Discord renders the
        // presence, so they belong to the fingerprint. Without them a changed
        // activity type would be dropped as "content unchanged" and would
        // never reach Discord.
        result = 31 * result + (commonRpc.type ?: 0)
        result = 31 * result + (commonRpc.platform?.hashCode() ?: 0)
        // The images belong to the fingerprint as well: switching the cover art
        // switch off has to drop the large image from the profile immediately,
        // not only when the next track starts.
        result = 31 * result + imageFingerprint(commonRpc.largeImage)
        result = 31 * result + imageFingerprint(commonRpc.smallImage)
        // Whether a timeline is shown is part of the presence. A paused track has no
        // timestamps, and the first presence of a just-started track often has a
        // position of 0 (so no timeline) - without these the later presence that
        // carries the real timeline would be dropped as "content unchanged".
        result = 31 * result + (commonRpc.time?.start != null).hashCode()
        result = 31 * result + (commonRpc.time?.end != null).hashCode()
        result = 31 * result + (buttons?.hashCode() ?: 0)
        return result
    }

    /**
     * Buffering and connecting are transient states of the *same* playback.
     *
     * Discord renders the same activity card for them, so counting them as a
     * content change made every track change send three presences (metadata,
     * buffering, playing). The rate limit then delayed the one with the real
     * timeline. Only a genuine play/pause transition may cause a new send.
     */
    private fun normalizedPlaybackState(state: Int?): Int = when (state) {
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> PlaybackState.STATE_PLAYING
        else -> state ?: 0
    }

    /**
     * A stable identity of an image that survives across recompositions of the
     * same artwork: `RpcImage` has no `equals()`, and the bitmap of a
     * `BitmapImage` changes instance on every media update, so hashing the
     * object itself would make every update look like new content.
     */
    private fun imageFingerprint(image: RpcImage?): Int = when (image) {
        null -> 0
        is RpcImage.DiscordImage -> image.image.hashCode()
        is RpcImage.ExternalImage -> image.image.hashCode()
        is RpcImage.ApplicationIcon -> image.packageName.hashCode()
        is RpcImage.BitmapImage -> image.title.hashCode()
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
        // Keep lastPresence in memory so the READY re-apply path can restore it
        // when the service is restarted (e.g. after an RPC type switch). Clearing
        // it here would mean the presence never comes back after a service restart.
    }

    /** Clears Discord presence by sending an empty activity.
     * The WebSocket connection is kept alive so presence can resume when media starts again.
     * Must be called from a coroutine context. */
    suspend fun clearPresence() {
        presenceSendMutex.withLock {
            // Remove the activity but keep the account online. The old code sent
            // status="invisible" here, which made the user appear offline to every
            // other client and hid the presence everywhere until the next update.
            discordWebSocket.sendActivity(
                Presence(
                    activities = emptyList(),
                    afk = false,
                    since = null,
                    status = effectiveStatus(null)
                )
            )
            // Forget the cached presence so the next update is not treated as unchanged.
            lastPresence = null
            lastPresenceHash = -1
            lastPresenceStart = null
            lastPresenceEnd = null
            lastResolvedLargeImage = null
            lastResolvedSmallImage = null
        }
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
     * Checks if the OAuth2 access token is available for OAuth2-based RPC.
     */
    fun isOAuth2TokenAvailable(): Boolean {
        return Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].isNotEmpty()
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
     * The large/small image urls that were resolved for the presence sent last.
     *
     * The RPC preview needs them to show the same cover art as Discord. A caller
     * that builds its own preview config (Manual RPC) would otherwise have
     * to upload the artwork a second time.
     */
    fun currentImageUrls(): Pair<String?, String?> =
        lastResolvedLargeImage to lastResolvedSmallImage

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
     * @param url Button URL (must be a valid http(s) url, empty/null urls are ignored)
     * @return
     */
    fun setButton1(label: String?, url: String?): KizzyRPC {
        val cleanLabel = sanitizeButtonLabel(label)
        val cleanUrl = sanitizeButtonUrl(url)
        if (cleanLabel != null && cleanUrl != null) {
            buttonsLock.lock()
            try {
                buttons.add(ActivityButton(label = cleanLabel, url = cleanUrl))
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
     * @param url Button URL (must be a valid http(s) url, empty/null urls are ignored)
     * @return
     */
    fun setButton2(button2Text: String?, url: String? = null): KizzyRPC {
        val cleanLabel = sanitizeButtonLabel(button2Text)
        val cleanUrl = sanitizeButtonUrl(url)
        if (cleanLabel != null && cleanUrl != null) {
            buttonsLock.lock()
            try {
                buttons.add(ActivityButton(label = cleanLabel, url = cleanUrl))
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

    suspend fun build(forceRefresh: Boolean = false) {
        val cleanButtons = sanitizeButtons(buttons.takeIf { buttons.isNotEmpty() })

        presence = Presence(
            activities = listOf(
                Activity(
                    name = activityName?.sanitize().orEmpty(),
                    state = state?.sanitize(),
                    details = details?.sanitize(),
                    // For manual RPC, use url field as details_url if set.
                    // Links do not depend on whether cover art is shown.
                    detailsUrl = if (type != 1) sanitizeFieldUrl(url) else null,
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
                        smallText = smallText?.sanitize(),
                        // For manual RPC, use url as large_url if set.
                        largeUrl = if (type != 1) sanitizeFieldUrl(url) else null
                    ).takeIf { largeImage != null || smallImage != null },
                    // Client-presence format: labels in buttons, urls in metadata.
                    buttons = cleanButtons?.map { it.label },
                    metadata = cleanButtons?.let { Metadata(buttonUrls = it.map { b -> b.url }) },
                    applicationId = applicationIdNumber.takeIf { it.isNotEmpty() } ?: Constants.APPLICATION_ID,
                    // Top-level url is only valid for Streaming (type 1).
                    url = sanitizeStreamUrl(url, type)
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
        connectToWebSocket(forceRefresh = forceRefresh)
    }

    /**
     * Returns whether the Discord WebSocket is currently connected.
     * Used by MediaRpcService to skip presence updates when disconnected.
     */
    suspend fun ensureConnected(timeoutMs: Long = 10_000L): Boolean {
        if (discordWebSocket.isWebSocketConnected()) {
            isConnected = true
            connectionFailures = 0
            return true
        }
        // Wait for connection with timeout
        val startTime = System.currentTimeMillis()
        while (!discordWebSocket.isWebSocketConnected() && System.currentTimeMillis() - startTime < timeoutMs) {
            delay(300)
        }
        if (discordWebSocket.isWebSocketConnected()) {
            isConnected = true
            connectionFailures = 0
            return true
        }
        isConnected = false
        connectionFailures++
        logger.w("KizzyRPC", "WebSocket not connected after ${timeoutMs}ms (failures: $connectionFailures)")
        return false
    }
    private suspend fun connectToWebSocket(forceRefresh: Boolean = false) {
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
        // that cause Discord 4003 Not authenticated errors. When forceRefresh is true
        // (e.g. notification restart), we still send the presence on the existing connection.
        if (discordWebSocket.isWebSocketConnected() && !forceRefresh) {
            logger.d("KizzyRPC", "WebSocket already connected, skipping connect")
            return
        }
        if (!discordWebSocket.isWebSocketConnected()) {
            discordWebSocket.connect()
            markConnected()
            // Wait for WebSocket to connect before sending presence
            val start = System.currentTimeMillis()
            while (!discordWebSocket.isWebSocketConnected() && System.currentTimeMillis() - start < 15000) {
                delay(500)
            }
        }
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
        //
        // Refresh the token and identify properties from prefs before connecting,
        // so a login that happened after DI takes effect even when build() was
        // never called (e.g. Media RPC running without Manual RPC).
        refreshFromPrefs()

        if (!isUserTokenValid()) {
            logger.e(
                tag = "KizzyRPC",
                event = "Token Seems to be invalid, Please Login if you haven't"
            )
            return
        }
        if (!discordWebSocket.isWebSocketConnected()) {
            discordWebSocket.connect()
            markConnected()
        }

        val wasConnected = discordWebSocket.isWebSocketConnected()

        // Wait for WebSocket to actually connect before sending presence.
        // connect() returns immediately after launching the connection coroutine;
        // we poll isWebSocketConnected() until the socket is ready or timeout.
        if (!wasConnected) {
            val connectStart = System.currentTimeMillis()
            val connectTimeoutMs = 15_000L
            while (!discordWebSocket.isWebSocketConnected() && System.currentTimeMillis() - connectStart < connectTimeoutMs) {
                delay(500)
            }
            if (!discordWebSocket.isWebSocketConnected()) {
                logger.w("KizzyRPC", "WebSocket did not connect within ${connectTimeoutMs}ms; presence may not be sent")
            } else {
                logger.d("KizzyRPC", "WebSocket connected after ${System.currentTimeMillis() - connectStart}ms")
            }
            // Delay first presence update after connect/resume to avoid Discord 4003 (Not authenticated)
            // when presence is sent immediately after RESUME/READY.
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

        // Everything that decides whether a presence is sent, the rate-limit wait and
        // the send itself is serialized. Media detection calls updateRPC() from several
        // coroutines at once; without this an older presence could be sent after a newer
        // one and leave Discord showing the previous song.
        presenceSendMutex.withLock {
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
                return@withLock
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

            // Global switch: hide cover art images entirely (show text only)
            val hideCoverArt = Prefs[Prefs.RPC_HIDE_COVER_ART, false]
            val finalLargeImage = if (hideCoverArt) null else resolvedLargeImage
            val finalSmallImage = if (hideCoverArt) null else resolvedSmallImage

            // Apply default cover art when notification has no image (but only if cover art is enabled)
            val defaultLargeImage = Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""]
            val defaultSmallImage = Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""]
            val finalLargeImageWithDefault = if (finalLargeImage != null || hideCoverArt) finalLargeImage else defaultLargeImage.takeIf { it.isNotEmpty() }
            val finalSmallImageWithDefault = if (finalSmallImage != null || hideCoverArt) finalSmallImage else defaultSmallImage.takeIf { it.isNotEmpty() }

            // Discord only accepts a top-level activity `url` for type 1 (Streaming).
            // Media RPC uses type 2/3 (Listening/Watching), so its mediaUrl must NOT
            // be placed here - a non-null url with a non-streaming type makes Discord
            // silently drop the whole activity. The mediaUrl goes into the buttons.
            val activityType = commonRpc.type ?: Prefs[CUSTOM_ACTIVITY_TYPE, 0]
            val cleanButtons = sanitizeButtons(buttonsSnapshot)

            val presence = Presence(
                activities = listOf(
                    Activity(
                        name = commonRpc.name.sanitize(),
                        details = commonRpc.details?.takeIf { it.isNotEmpty() }?.sanitize(),
                        state = commonRpc.state?.takeIf { it.isNotEmpty() }?.sanitize(),
                        detailsUrl = sanitizeFieldUrl(commonRpc.detailsUrl),
                        stateUrl = sanitizeFieldUrl(commonRpc.stateUrl),
                        type = activityType,
                        platform = commonRpc.platform?.sanitize(),
                        timestamps = time,
                        url = sanitizeStreamUrl(commonRpc.mediaUrl, activityType),
                        assets = Assets(
                            largeImage = finalLargeImageWithDefault,
                            smallImage = finalSmallImageWithDefault,
                            largeText = commonRpc.largeText?.sanitize(),
                            smallText = commonRpc.smallText?.sanitize(),
                            largeUrl = sanitizeFieldUrl(commonRpc.largeUrl),
                            smallUrl = sanitizeFieldUrl(commonRpc.smallUrl)
                        ).takeIf { finalLargeImageWithDefault != null || finalSmallImageWithDefault != null },
                        party = party.takeIf { party != null },
                        // Client-presence format: labels in buttons, urls in metadata.
                        buttons = cleanButtons?.map { it.label },
                        metadata = cleanButtons?.let { Metadata(buttonUrls = it.map { b -> b.url }) },

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
            val sent = discordWebSocket.sendActivity(presence)
            if (!sent) {
                // The socket was not ready. Do NOT touch the cache, otherwise the next
                // update is dropped as "content unchanged" and Discord keeps showing the
                // previous song until the track changes again.
                logger.w("KizzyRPC", "Presence not sent (socket not ready); will retry on the next update")
                return@withLock
            }
            // Remember the presence so we can re-apply after SESSIONS_REPLACE reconnect
            lastPresence = presence
            lastPresenceHash = hash
            // The send may have waited for an artwork upload, so record the actual
            // completion time - the rate limiter and the echo window depend on it.
            lastPresenceTimeMs = System.currentTimeMillis()
            // Track the timeline that was sent so the next update can tell whether the user
            // seeked (timeline jump) or the track simply kept playing.
            lastPresenceStart = time?.start
            lastPresenceEnd = time?.end
            lastResolvedLargeImage = finalLargeImageWithDefault
            lastResolvedSmallImage = finalSmallImageWithDefault

            // Remember the presence for the RPC preview, so it shows what Discord
            // actually displays even when the service is not running anymore.
            rememberLastPresence(
                commonRpc = commonRpc,
                buttonsSnapshot = buttonsSnapshot,
                time = time,
                largeImage = finalLargeImageWithDefault,
                smallImage = finalSmallImageWithDefault,
            )
        }
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
            detailsUrl = commonRpc.detailsUrl.orEmpty(),
            stateUrl = commonRpc.stateUrl.orEmpty(),
            largeUrl = commonRpc.largeUrl.orEmpty(),
            smallUrl = commonRpc.smallUrl.orEmpty(),
            button1 = button1?.label.orEmpty(),
            button1link = button1?.url.orEmpty(),
            button2 = button2?.label.orEmpty(),
            button2link = button2?.url.orEmpty(),
        )
        LastPresence.rememberMedia(config)
    }
}