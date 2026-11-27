package com.my.kizzy.feature_rpc_base.services

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.Trace
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.rpc.*
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.data.work.RpcRecoveryHiltWorker
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.BuildConfig
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.services.lastfm.*
import com.my.kizzy.feature_rpc_base.services.listenbrainz.ListenBrainz
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_LINK
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_MEDIA_BUTTON
import com.my.kizzy.preference.Prefs.TOKEN
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import com.my.kizzy.feature_rpc_base.R as FeatureRpcR

private const val MEDIA_RPC = "MediaRPC"
private val rpcButtonsJson = Json { ignoreUnknownKeys = true }

@AndroidEntryPoint
class MediaRpcService : Service() {

    private var kizzyRPC: KizzyRPC? = null


    /** The home-screen instance ID this service is currently sending presence for.
     *  Null for the built-in Media RPC; a clone's id for clones. Used to read
     *  per-instance scoped preferences (cover art, channel icon, etc.). */
    private var currentInstanceId: String? = null

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var getCurrentPlayingMedia: GetCurrentPlayingMedia

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var stateRepository: KizzyStateRepository

    @Inject
    lateinit var MetadataResolver: MetadataResolver

    @Inject
    lateinit var kizzyRepository: com.my.kizzy.domain.repository.KizzyRepository

    @Inject
    lateinit var getCurrentlyRunningApp: GetCurrentlyRunningApp

    @Inject
    lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine

    @Inject
    lateinit var rpcManager: RpcManager

    private var wakeLock: WakeLock? = null

    private var activeScrobbleTrack: String? = null
    private var lastFmScrobbledIdentity: String? = null
    private var listenBrainzScrobbledIdentity: String? = null
    private var lastFmNowPlayingIdentity: String? = null
    private var listenBrainzNowPlayingIdentity: String? = null
    private var lastFmScrobbleInFlight: String? = null
    private var listenBrainzScrobbleInFlight: String? = null
    private var lastFmTrackStartTimestamp: Long = 0
    private var lastFmScrobbleRetryAfter: Long = 0
    private var listenBrainzScrobbleRetryAfter: Long = 0
    private var lastFmNowPlayingInFlight: String? = null
    private var listenBrainzNowPlayingInFlight: String? = null
    private var lastFmNowPlayingRetryAfter: Long = 0
    private var listenBrainzNowPlayingRetryAfter: Long = 0
    private var listenBrainzMissingTokenLogged = false
    private val lastFmAuth = LastFmAuthState(
        name = "LastFM",
        client = LastFM,
        enabledPreference = Prefs.LASTFM_ENABLED,
        apiKeyPreference = Prefs.LASTFM_API_KEY,
        secretPreference = Prefs.LASTFM_SECRET,
        usernamePreference = Prefs.LASTFM_USERNAME,
        passwordPreference = Prefs.LASTFM_PASSWORD,
        sessionKeyPreference = Prefs.LASTFM_SESSION_KEY
    )
    private val libreFmAuth = LastFmAuthState(
        name = "LibreFM",
        client = LibreFM,
        enabledPreference = Prefs.LIBREFM_ENABLED,
        apiKeyPreference = Prefs.LIBREFM_API_KEY,
        secretPreference = Prefs.LIBREFM_SECRET,
        usernamePreference = Prefs.LIBREFM_USERNAME,
        passwordPreference = Prefs.LIBREFM_PASSWORD,
        sessionKeyPreference = Prefs.LIBREFM_SESSION_KEY
    )
    private var libreFmScrobbledIdentity: String? = null
    private var libreFmNowPlayingIdentity: String? = null
    private var libreFmScrobbleInFlight: String? = null
    private var libreFmNowPlayingInFlight: String? = null
    private var libreFmScrobbleRetryAfter: Long = 0
    private var libreFmNowPlayingRetryAfter: Long = 0

    private data class LastFmAuthState(
        val name: String,
        val client: LastFmApiClient,
        val enabledPreference: String,
        val apiKeyPreference: String = "",
        val secretPreference: String = "",
        val usernamePreference: String,
        val passwordPreference: String,
        val sessionKeyPreference: String,
        var retryAfter: Long = 0,
        var missingConfigLogged: Boolean = false,
        val mutex: Mutex = Mutex()
    )

    private var isPaused = false

    /** Holds the wake lock only while actively updating presence, then releases it. */
    private suspend fun withWakeLock(block: suspend () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PARTIAL_WAKE_LOCK is needed on API 31+ to keep CPU alive during work
            wakeLock?.acquire(10_000)
        } else {
            wakeLock?.acquire()
        }
        try {
            block()
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private var currentPackageName: String? = null
    private var lastKnownTrackTitle: String? = null
    private var lastKnownTrackArtist: String? = null

    private var lastSentName: String? = null
    private var lastSentDetails: String? = null
    private var lastSentState: String? = null
    private var lastSentStartTimestamps: Long? = null
    private var lastSentStopTimestamps: Long? = null
    private var lastSentLargeImageUrl: String? = null
    private var lastSentPackageName: String? = null
    private var lastPresenceUpdateTime: Long = 0L
    private val PRESENCE_UPDATE_MIN_INTERVAL_MS = 1500L

    // Notification throttling: only update notification at most once per second
    // while media is playing, to avoid constant UI redraws and battery drain.
    private var lastNotificationUpdateTime: Long = 0L
    private val NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 1000L

    /**
     * The last playback state and metadata that were reported to Discord.
     *
     * Media players fire their callbacks very often - a buffering state change or a
     * repeated metadata event with identical values does not mean the song changed.
     * Remembering the values keeps the update pipeline from being woken for noise
     * (and from queueing behind the rate limit), which is what made the next song
     * appear late.
     */
    private var lastPlaybackStateCode: Int? = null
    private var lastMetadataSignature: String? = null

    /**
     * How far the start timestamp has to move before it is treated as a seek. Players
     * report their position with coarse granularity, so a smaller value would report a
     * jump on every update.
     */
    private val TIMELINE_JUMP_TOLERANCE_MS = 3000L

    private val presenceUpdateMutex = Mutex()

    private val timelineJob = MutableStateFlow<Job?>(null)
    private val heartbeatJob = MutableStateFlow<Job?>(null)

    @Inject
    lateinit var notificationManager: NotificationManager

    private lateinit var mediaSessionManager: MediaSessionManager

    /**
     * Single listener instance shared by the add and remove calls.
     *
     * A bound method reference to activeSessionsListener allocates a new
     * wrapper on every evaluation, so the remove call never matches the
     * listener registered by the add call. The framework then retains the
     * binder stub — and this destroyed service through it — leaking the
     * whole service instance after onDestroy().
     */
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            activeSessionsListener(sessions)
        }

    private var currentMediaController: MediaController? = null
    private var currentMediaTitle: String? = null
    private var currentMediaArtist: String? = null
    private var currentMediaCoverArt: Bitmap? = null
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0
    private var lastSentProgressSec: Int = -1

    private var appDetectionJob: Job? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Kizzy Media RPC",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Media Rich Presence notification channel"
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
        createNotificationChannel()
        val basicNotification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle("Rich Presence Active")
            .setContentText("Starting...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, basicNotification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, basicNotification)
        }

        val token = Prefs[TOKEN, ""]
        if (token.isBlank()) {
            logger.d(MEDIA_RPC, "onCreate: token currently empty, waiting for refreshFromPrefs()")
        }
        // Don't stop the service when the token is empty - the user may log in
        // later. refreshFromPrefs() in updateRPC() will pick up the token when
        // it becomes available. MEDIA_RPC_ENABLED is set to true so the watchdog
        // knows the service should be running.
        Prefs[Prefs.MEDIA_RPC_ENABLED] = true
        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.MediaRpc.name
        setupWakeLock()
        logger.d(MEDIA_RPC, "onCreate: starting app detection coroutine, useAppsRpc=${Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]}")
        startAppDetectionCoroutine()

        val stopIntent = Intent(this, MediaRpcService::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(this, MediaRpcService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        val restartPendingIntent = PendingIntent.getService(
            this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle("Rich Presence Active")
            .setContentText("Broadcasting media status")
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.restart),
                    restartPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.exit),
                    stopPendingIntent
                ).build()
            )
            .build()

        notificationManager.notify(Constants.NOTIFICATION_ID_MEDIA_RPC, notification)

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.addOnActiveSessionsChangedListener(
            activeSessionsChangedListener,
            ComponentName(this, NotificationListener::class.java)
        )

        val sessions: List<MediaController> = try {
            mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
        activeSessionsListener(sessions, false)

        startHeartbeat()
    }

    private fun startTimelineUpdates() {
        stopTimelineUpdates()
        timelineJob.value = scope.launch {
            while (isActive) {
                // 3-second tick instead of 1 second: the CPU wakes 3x less often
                // while media is playing, saving battery. Discord's progress bar
                // does not need 1-second precision, and the presence update is
                // deduplicated anyway - only a real seek is sent to the gateway.
                delay(3000)
                if (!isPaused && kizzyRPC?.isRpcRunning() == true) {
                    withWakeLock {
                        updatePresence()
                    }
                }
            }
        }
    }

    private fun stopTimelineUpdates() {
        timelineJob.value?.cancel()
        timelineJob.value = null
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob.value = scope.launch {
            while (isActive) {
                delay(30000)
                withWakeLock {
                    updatePresence(forceRefresh = true)
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob.value?.cancel()
        heartbeatJob.value = null
    }

    private fun startAppDetectionCoroutine() {
        if (appDetectionJob?.isActive == true) {
            logger.d(MEDIA_RPC, "App detection coroutine already running")
            return
        }
        logger.d(MEDIA_RPC, "Starting app detection coroutine")

        appDetectionJob = scope.launch {
            var currentPackageName = ""
            var cycleCount = 0
            while (isActive) {
                cycleCount++
                try {
                    // Do not override Custom RPC or Manual RPC presence with app detection
                    val activeRpcTypes = rpcManager.activeRpc.value
                    val customOrManualRpcActive = activeRpcTypes.contains(RpcManager.RpcType.CustomRpc) ||
                            activeRpcTypes.contains(RpcManager.RpcType.ConsoleRpc) ||
                            activeRpcTypes.contains(RpcManager.RpcType.ManualRpc)
                    if (customOrManualRpcActive) {
                        if (cycleCount % 6 == 1) {
                            logger.d(MEDIA_RPC, "App detection loop running but Custom/Manual RPC is active - skipping app detection")
                        }
                        delay(5000.milliseconds)
                        continue
                    }

                    val useAppsRpc = Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]
                    if (!useAppsRpc) {
                        if (cycleCount % 6 == 1) {
                            logger.d(MEDIA_RPC, "App detection loop running but MEDIA_RPC_USE_APPS_RPC is false - enabling not set")
                        }
                        delay(5000.milliseconds)
                        continue
                    }

                    logger.d(MEDIA_RPC, "App detection cycle #$cycleCount: checking for foreground app")
                    val runningApp = getCurrentlyRunningApp()

                    if (runningApp.name.isEmpty()) {
                        if (cycleCount % 6 == 1) {
                            logger.d(MEDIA_RPC, "App detection: no foreground app found (getCurrentlyRunningApp returned empty)")
                        }
                    } else {
                        logger.d(MEDIA_RPC, "App detection: found foreground app=${runningApp.packageName} label=${runningApp.name}")
                    }

                    if (
                        runningApp.name.isNotEmpty() &&
                        runningApp.packageName != currentPackageName &&
                        runningApp.packageName !in AppDetectionService.EXCLUDED_APPS
                    ) {
                        logger.d(MEDIA_RPC, "App detection: new app detected=${runningApp.packageName}, checking if should show")
                        val isGameApp = dynamicActivityDetectionEngine.identifyGame(runningApp.packageName) != null
                        val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName) != null
                        val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_APPS, "[]"])
                        val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps

                        logger.d(MEDIA_RPC, "App detection: ${runningApp.packageName} isGame=$isGameApp isAI=$isAiApp inEnabledApps=${runningApp.packageName in enabledApps} shouldShow=$shouldShow")

                        if (shouldShow) {
                            currentPackageName = runningApp.packageName
                            val appLabel = runningApp.name.takeIf { it.isNotBlank() } ?: runningApp.packageName
                            val details = if (isGameApp) "Playing game" else if (isAiApp) "Chatting with AI" else null
                            val state = if (isGameApp) "In-game" else if (isAiApp) "Thinking..." else null
                            logger.d(MEDIA_RPC, "App detection FOUND: ${runningApp.packageName} (label=$appLabel, isGame=$isGameApp, isAI=$isAiApp)")
                            kizzyRPC?.apply {
                                setName(appLabel)
                                setDetails(details)
                                setState(state)
                                setStartTimestamps(System.currentTimeMillis())
                                setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "online"])
                                setLargeImage(runningApp.largeImage)
                                clearButtons()
                            }
                            kizzyRPC?.updateRPC(
                                CommonRpc(
                                    name = appLabel,
                                    type = ActivityTypeResolver.resolve(
                                        packageName = runningApp.packageName,
                                        fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                                        useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                                    ),
                                    details = details,
                                    state = state,
                                    largeImage = runningApp.largeImage,
                                    time = Timestamps(start = System.currentTimeMillis()),
                                    packageName = runningApp.packageName,
                                    appPackage = runningApp.packageName
                                ),
                                enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
                            )
                        } else if (currentPackageName != "") {
                            currentPackageName = ""
                            scope.launch { kizzyRPC?.clearPresence() }
                        }
                    } else if (runningApp.name.isEmpty() && currentPackageName != "") {
                        currentPackageName = ""
                        scope.launch { kizzyRPC?.clearPresence() }
                        logger.d(MEDIA_RPC, "App detection: foreground app cleared")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(MEDIA_RPC, "App detection cycle failed: ${e.message}")
                }
                delay(5000.milliseconds)
            }
        }
    }

    private fun stopAppDetectionCoroutine() {
        appDetectionJob?.cancel()
        appDetectionJob = null
    }

    /**
     * Serializes presence updates.
     *
     * The media callbacks and the timeline ticker call this at the same time. If two
     * updates run concurrently an older track can finish (cover-art upload) after a
     * newer one and overwrite it on Discord. Holding the mutex for the whole update -
     * including reading the media session and sending - makes the last call win.
     */
    private suspend fun updatePresence(forceRefresh: Boolean = false) {
        presenceUpdateMutex.withLock {
            updatePresenceLocked(forceRefresh)
        }
    }

    private suspend fun ensureLastFmCompatibleSession(auth: LastFmAuthState): Boolean =
        auth.mutex.withLock {
            if (!Prefs[auth.enabledPreference, false]) return@withLock false

            // Use user-configured API key/secret if set; otherwise fall back to
            // hardcoded defaults (like Morphe / YouTube Music) so users only need
            // to enter username + password.
            val configuredKey = Prefs[auth.apiKeyPreference, ""]
            val configuredSecret = Prefs[auth.secretPreference, ""]
            val defaultKey = when (auth.client::class.simpleName) {
                "LastFM" -> DEFAULT_LASTFM_API_KEY
                "LibreFM" -> DEFAULT_LIBREFM_API_KEY
                else -> ""
            }
            val defaultSecret = when (auth.client::class.simpleName) {
                "LastFM" -> DEFAULT_LASTFM_SECRET
                "LibreFM" -> DEFAULT_LIBREFM_SECRET
                else -> ""
            }
            val apiKey = configuredKey.takeIf { it.isNotBlank() } ?: defaultKey
            val apiSecret = configuredSecret.takeIf { it.isNotBlank() } ?: defaultSecret
            if (apiKey.isBlank() || apiSecret.isBlank()) {
                auth.client.sessionKey = null
                if (!auth.missingConfigLogged) {
                    logger.w(auth.name, "Scrobbling is enabled, but API key or secret is not configured")
                    auth.missingConfigLogged = true
                }
                return@withLock false
            }

            auth.missingConfigLogged = false
            auth.client.init(apiKey, apiSecret)
            val savedSessionKey = Prefs[auth.sessionKeyPreference, ""]
            if (savedSessionKey.isNotBlank()) {
                auth.client.sessionKey = savedSessionKey
                return@withLock true
            }
            auth.client.sessionKey = null

            val now = System.currentTimeMillis()
            if (now < auth.retryAfter) return@withLock false

            val username = Prefs[auth.usernamePreference, ""]
            val password = Prefs[auth.passwordPreference, ""]
            if (username.isBlank() || password.isBlank()) {
                logger.w(auth.name, "Scrobbling is enabled, but username or password is missing")
                auth.retryAfter = now + 60_000
                return@withLock false
            }

            val session = try {
                auth.client.getMobileSession(username, password)
            } catch (e: LastFmException) {
                logger.e(auth.name, "Authentication failed: ${e.message}")
                null
            }
            if (session == null) {
                logger.w(auth.name, "Could not obtain a scrobbling session; retrying in one minute")
                auth.retryAfter = now + 60_000
                return@withLock false
            }

            auth.client.sessionKey = session.key
            Prefs[auth.sessionKeyPreference] = session.key
            auth.retryAfter = 0
            true
        }

    private suspend fun invalidateLastFmCompatibleSessionIfRejected(
        auth: LastFmAuthState,
        error: Exception
    ): Boolean {
        if ((error as? LastFmException)?.code != 9) return false
        auth.mutex.withLock {
            auth.client.sessionKey = null
            Prefs[auth.sessionKeyPreference] = ""
            auth.retryAfter = 0
        }
        return true
    }

    private fun updateScrobblers(playingMedia: CommonRpc) {
        val metadata = currentMediaController
            ?.takeIf { it.packageName == playingMedia.appPackage }
            ?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(playingMedia.name, playingMedia.details)
                .firstOrNull { it.isNotBlank() && it != playingMedia.appName }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val artist = metadata?.let { MetadataResolver.getArtistOrAuthor(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: playingMedia.state?.trim()?.takeIf { it.isNotEmpty() }
        if (title == null || artist == null) return

        val identity = "$artist\u001f$title"
        val nowMs = System.currentTimeMillis()
        val playbackState = currentMediaController?.playbackState
        val playbackStateCode = playbackState?.state ?: playingMedia.playbackState
        val isPlaying = !isPaused &&
                (playbackStateCode == PlaybackState.STATE_PLAYING ||
                        (playbackStateCode == null && playingMedia.time != null))
        val metadataDurationSec = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
            .div(1000L).toInt().coerceAtLeast(0)
        val durationSec = maxOf(currentMediaDurationSec, metadataDurationSec)
        val timelineStartMs = playingMedia.time?.start
        val reportedPositionSec = ((playbackState?.position ?: 0L) / 1000L).toInt().coerceAtLeast(0)

        if (activeScrobbleTrack != identity) {
            activeScrobbleTrack = identity
            lastFmTrackStartTimestamp = timelineStartMs?.div(1000L)
                ?: (nowMs / 1000L - reportedPositionSec)
            lastFmScrobbledIdentity = null
            libreFmScrobbledIdentity = null
            listenBrainzScrobbledIdentity = null
            lastFmNowPlayingIdentity = null
            libreFmNowPlayingIdentity = null
            listenBrainzNowPlayingIdentity = null
            lastFmScrobbleRetryAfter = 0
            libreFmScrobbleRetryAfter = 0
            listenBrainzScrobbleRetryAfter = 0
            lastFmNowPlayingRetryAfter = 0
            libreFmNowPlayingRetryAfter = 0
            listenBrainzNowPlayingRetryAfter = 0
        }

        val positionSec = timelineStartMs?.let { ((nowMs - it) / 1000L).toInt().coerceAtLeast(0) }
            ?: if (isPlaying) {
                maxOf(reportedPositionSec, (nowMs / 1000L - lastFmTrackStartTimestamp).toInt())
            } else {
                reportedPositionSec
            }

        val album = metadata?.let { MetadataResolver.getAlbum(it) }
        val trackNumber = metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)
            ?.takeIf { it > 0L }
            ?.toInt()
        val lastFmEnabled = Prefs[lastFmAuth.enabledPreference, false]
        val libreFmEnabled = Prefs[libreFmAuth.enabledPreference, false]
        val listenBrainzEnabled = Prefs[Prefs.LISTENBRAINZ_ENABLED, false]

        if (!lastFmEnabled) {
            lastFmNowPlayingIdentity = null
        } else if (isPlaying && lastFmNowPlayingIdentity != identity &&
            lastFmNowPlayingInFlight != identity && nowMs >= lastFmNowPlayingRetryAfter
        ) {
            lastFmNowPlayingInFlight = identity
            lastFmNowPlayingIdentity = identity
            scope.launch {
                try {
                    if (ensureLastFmCompatibleSession(lastFmAuth)) {
                        val sent = lastFmAuth.client.updateNowPlaying(
                            artist, title, album, trackNumber, durationSec.takeIf { it > 0 }
                        )
                        if (!sent) throw IllegalStateException("Last.fm did not accept the now-playing update")
                        logger.d("LastFM", "Now-playing updated for $artist - $title")
                    } else {
                        lastFmNowPlayingIdentity = null
                        lastFmNowPlayingRetryAfter = System.currentTimeMillis() + 60_000
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("LastFM", "Now-playing update failed: ${e.message}")
                    val sessionRejected = invalidateLastFmCompatibleSessionIfRejected(lastFmAuth, e)
                    lastFmNowPlayingIdentity = null
                    lastFmNowPlayingRetryAfter =
                        if (sessionRejected) 0 else System.currentTimeMillis() + 60_000
                } finally {
                    if (lastFmNowPlayingInFlight == identity) lastFmNowPlayingInFlight = null
                }
            }
        }

        val scrobbleThresholdSec = if (durationSec > 0) minOf(durationSec / 2, 240) else 240
        val eligibleToScrobble = isPlaying &&
                (durationSec == 0 || durationSec >= 30) &&
                positionSec >= scrobbleThresholdSec

        if (lastFmEnabled && eligibleToScrobble &&
            lastFmScrobbledIdentity != identity && lastFmScrobbleInFlight != identity &&
            nowMs >= lastFmScrobbleRetryAfter
        ) {
            lastFmScrobbleInFlight = identity
            scope.launch {
                try {
                    if (!ensureLastFmCompatibleSession(lastFmAuth)) {
                        lastFmScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                        return@launch
                    }
                    val sent = lastFmAuth.client.scrobble(
                        artist = artist,
                        track = title,
                        timestamp = lastFmTrackStartTimestamp,
                        album = album,
                        trackNumber = trackNumber,
                        duration = durationSec.takeIf { it > 0 }
                    )
                    if (sent) {
                        lastFmScrobbledIdentity = identity
                        logger.d("LastFM", "Scrobbled $artist - $title")
                    } else {
                        lastFmScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                        logger.w("LastFM", "Scrobble failed for $artist - $title")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val sessionRejected = invalidateLastFmCompatibleSessionIfRejected(lastFmAuth, e)
                    lastFmScrobbleRetryAfter =
                        if (sessionRejected) 0 else System.currentTimeMillis() + 60_000
                    logger.e("LastFM", "Scrobble failed: ${e.message}")
                } finally {
                    if (lastFmScrobbleInFlight == identity) lastFmScrobbleInFlight = null
                }
            }
        }

        if (!libreFmEnabled) {
            libreFmNowPlayingIdentity = null
        } else if (isPlaying && libreFmNowPlayingIdentity != identity &&
            libreFmNowPlayingInFlight != identity && nowMs >= libreFmNowPlayingRetryAfter
        ) {
            libreFmNowPlayingInFlight = identity
            libreFmNowPlayingIdentity = identity
            scope.launch {
                try {
                    if (ensureLastFmCompatibleSession(libreFmAuth)) {
                        val sent = libreFmAuth.client.updateNowPlaying(
                            artist, title, album, trackNumber, durationSec.takeIf { it > 0 }
                        )
                        if (!sent) throw IllegalStateException("Libre.fm did not accept the now-playing update")
                        logger.d("LibreFM", "Now-playing updated for $artist - $title")
                    } else {
                        libreFmNowPlayingIdentity = null
                        libreFmNowPlayingRetryAfter = System.currentTimeMillis() + 60_000
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("LibreFM", "Now-playing update failed: ${e.message}")
                    val sessionRejected = invalidateLastFmCompatibleSessionIfRejected(libreFmAuth, e)
                    libreFmNowPlayingIdentity = null
                    libreFmNowPlayingRetryAfter =
                        if (sessionRejected) 0 else System.currentTimeMillis() + 60_000
                } finally {
                    if (libreFmNowPlayingInFlight == identity) libreFmNowPlayingInFlight = null
                }
            }
        }

        if (libreFmEnabled && eligibleToScrobble &&
            libreFmScrobbledIdentity != identity && libreFmScrobbleInFlight != identity &&
            nowMs >= libreFmScrobbleRetryAfter
        ) {
            libreFmScrobbleInFlight = identity
            scope.launch {
                try {
                    if (!ensureLastFmCompatibleSession(libreFmAuth)) {
                        libreFmScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                        return@launch
                    }
                    val sent = libreFmAuth.client.scrobble(
                        artist = artist,
                        track = title,
                        timestamp = lastFmTrackStartTimestamp,
                        album = album,
                        trackNumber = trackNumber,
                        duration = durationSec.takeIf { it > 0 }
                    )
                    if (sent) {
                        libreFmScrobbledIdentity = identity
                        logger.d("LibreFM", "Scrobbled $artist - $title")
                    } else {
                        libreFmScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                        logger.w("LibreFM", "Scrobble failed for $artist - $title")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val sessionRejected = invalidateLastFmCompatibleSessionIfRejected(libreFmAuth, e)
                    libreFmScrobbleRetryAfter =
                        if (sessionRejected) 0 else System.currentTimeMillis() + 60_000
                    logger.e("LibreFM", "Scrobble failed: ${e.message}")
                } finally {
                    if (libreFmScrobbleInFlight == identity) libreFmScrobbleInFlight = null
                }
            }
        }

        if (!listenBrainzEnabled) {
            listenBrainzNowPlayingIdentity = null
            return
        }

        val userToken = Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]
        if (userToken.isBlank()) {
            if (!listenBrainzMissingTokenLogged) {
                logger.w("ListenBrainz", "Scrobbling is enabled, but the user token is missing")
                listenBrainzMissingTokenLogged = true
            }
            listenBrainzNowPlayingIdentity = null
            return
        }
        listenBrainzMissingTokenLogged = false
        ListenBrainz.init(userToken)

        if (isPlaying && listenBrainzNowPlayingIdentity != identity &&
            listenBrainzNowPlayingInFlight != identity && nowMs >= listenBrainzNowPlayingRetryAfter
        ) {
            listenBrainzNowPlayingInFlight = identity
            listenBrainzNowPlayingIdentity = identity
            scope.launch {
                try {
                    val sent = ListenBrainz.updateNowPlaying(
                        artist, title, album, durationSec.takeIf { it > 0 }
                    )
                    if (!sent) throw IllegalStateException("ListenBrainz did not accept the now-playing update")
                    logger.d("ListenBrainz", "Now-playing updated for $artist - $title")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e("ListenBrainz", "Now-playing update failed: ${e.message}")
                    listenBrainzNowPlayingIdentity = null
                    listenBrainzNowPlayingRetryAfter = System.currentTimeMillis() + 60_000
                } finally {
                    if (listenBrainzNowPlayingInFlight == identity) listenBrainzNowPlayingInFlight = null
                }
            }
        }

        if (eligibleToScrobble && listenBrainzScrobbledIdentity != identity &&
            listenBrainzScrobbleInFlight != identity && nowMs >= listenBrainzScrobbleRetryAfter
        ) {
            listenBrainzScrobbleInFlight = identity
            scope.launch {
                try {
                    val sent = ListenBrainz.scrobble(
                        artist = artist,
                        track = title,
                        timestamp = lastFmTrackStartTimestamp,
                        album = album,
                        duration = durationSec.takeIf { it > 0 }
                    )
                    if (sent) {
                        listenBrainzScrobbledIdentity = identity
                        logger.d("ListenBrainz", "Scrobbled $artist - $title")
                    } else {
                        listenBrainzScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                        logger.w("ListenBrainz", "Scrobble failed for $artist - $title")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    listenBrainzScrobbleRetryAfter = System.currentTimeMillis() + 60_000
                    logger.e("ListenBrainz", "Scrobble failed: ${e.message}")
                } finally {
                    if (listenBrainzScrobbleInFlight == identity) listenBrainzScrobbleInFlight = null
                }
            }
        }
    }

    private suspend fun updatePresenceLocked(forceRefresh: Boolean = false) {
        if (BuildConfig.DEBUG) Trace.beginSection("MediaRpc.updatePresence")
        try {
            val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
            // Read per-instance scoped preferences so clones can have different
            // cover-art settings than built-ins.
            val coverArtKey = Prefs.scopedKey(Prefs.MEDIA_RPC_SHOW_COVER_ART, currentInstanceId)
            val showCoverArt = Prefs[coverArtKey, true]
            val playingMedia = getCurrentPlayingMedia(currentInstanceId)

            currentMediaTitle = playingMedia.name.takeIf { it.isNotBlank() }
            currentMediaArtist = playingMedia.state?.takeIf { it.isNotBlank() }
            // The "Show Cover Art" switch hides the art from the notification as well,
            // so the setting is honored in one place for the whole service.
            currentMediaCoverArt = if (showCoverArt) {
                playingMedia.largeImage?.let {
                    if (it is com.my.kizzy.data.rpc.RpcImage.BitmapImage) it.bitmap else null
                }
            } else {
                null
            }

            // Track playback progress for notification progress bar
            playingMedia.time?.let { time ->
                val start = time.start ?: 0L
                val end = time.end ?: 0L
                currentMediaDurationSec = ((end - start) / 1000).toInt().coerceAtLeast(0)
                // Derive the position from the timestamps instead of the raw playback position.
                // The raw position only changes when the player publishes an update, so using it
                // made the notification's progress bar freeze between updates. The timestamps are
                // already extrapolated by GetCurrentlyPlayingMedia, so "now - start" is accurate.
                val elapsedMs = System.currentTimeMillis() - start
                currentMediaPositionMs = elapsedMs.coerceIn(0L, currentMediaDurationSec * 1000L)
                currentMediaProgressSec = (currentMediaPositionMs / 1000).toInt().coerceIn(0, currentMediaDurationSec)
            } ?: run {
                currentMediaDurationSec = 0
                currentMediaProgressSec = 0
                currentMediaPositionMs = 0L
            }

            // While paused the preview must show the frozen position. The position is
            // computed from the timestamps above, so it is the real point where the
            // player stopped - not "now". Clearing it on resume restarts the timer.
            stateRepository.setPausedPosition(
                if (isPaused && currentMediaDurationSec > 0) currentMediaPositionMs else null
            )

            val playingPkg = playingMedia.appPackage.takeIf { it.isNotBlank() }

            if (playingPkg != null && playingPkg != currentPackageName) {
                logger.d(MEDIA_RPC, "Session switched: $currentPackageName -> $playingPkg")
                currentPackageName = playingPkg
                isPaused = false
                lastKnownTrackTitle = null
                lastKnownTrackArtist = null
                stateRepository.setPaused(false)
            }

            // Check if media is paused - if so, try app detection (show game instead of paused music)
            // But don't override Custom RPC or Manual RPC presence
            val activeRpcTypes = rpcManager.activeRpc.value
            val customOrManualRpcActive = activeRpcTypes.contains(RpcManager.RpcType.CustomRpc) ||
                    activeRpcTypes.contains(RpcManager.RpcType.ConsoleRpc) ||
                    activeRpcTypes.contains(RpcManager.RpcType.ManualRpc)
            val mediaPaused = isPaused
            if (Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false] && !customOrManualRpcActive && mediaPaused && playingPkg != null) {
                val runningApp = getCurrentlyRunningApp()
                if (runningApp.name.isNotEmpty() && runningApp.packageName !in AppDetectionService.EXCLUDED_APPS) {
                    val isGameApp = dynamicActivityDetectionEngine.identifyGame(runningApp.packageName) != null
                    val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName) != null
                    val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_APPS, "[]"])
                    val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps
                    if (shouldShow && runningApp.packageName != currentPackageName) {
                        currentPackageName = runningApp.packageName
                        val appLabel = runningApp.name.takeIf { it.isNotBlank() } ?: runningApp.packageName
                        val details = if (isGameApp) "Playing game" else if (isAiApp) "Chatting with AI" else null
                        val state = if (isGameApp) "In-game" else if (isAiApp) "Thinking..." else null
                        logger.d(MEDIA_RPC, "App detection found while paused: ${runningApp.packageName} (label=$appLabel, isGame=$isGameApp, isAI=$isAiApp)")
                        kizzyRPC?.apply {
                            setName(appLabel)
                            setDetails(details)
                            setState(state)
                            setStartTimestamps(System.currentTimeMillis())
                            setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "online"])
                            setLargeImage(runningApp.largeImage)
                            clearButtons()
                        }
                        kizzyRPC?.updateRPC(
                            CommonRpc(
                                name = appLabel,
                                type = ActivityTypeResolver.resolve(
                                    packageName = runningApp.packageName,
                                    fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                                    useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                                ),
                                details = details,
                                state = state,
                                largeImage = runningApp.largeImage,
                                time = Timestamps(start = System.currentTimeMillis()),
                                packageName = runningApp.packageName,
                                appPackage = runningApp.packageName
                            ),
                            enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
                        )
                        return
                    }
                }
            }

            if (playingPkg == null) {
                // No media session active - try app detection if enabled
                if (Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]) {
                    val runningApp = getCurrentlyRunningApp()
                    if (runningApp.name.isNotEmpty() && runningApp.packageName !in AppDetectionService.EXCLUDED_APPS) {
                        val isGameApp = dynamicActivityDetectionEngine.identifyGame(runningApp.packageName) != null
                        val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName) != null
                        val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_APPS, "[]"])
                        val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps
                        if (shouldShow) {
                            val appLabel = runningApp.name.takeIf { it.isNotBlank() } ?: runningApp.packageName
                            val details = if (isGameApp) "Playing game" else if (isAiApp) "Chatting with AI" else null
                            val state = if (isGameApp) "In-game" else if (isAiApp) "Thinking..." else null
                            logger.d(MEDIA_RPC, "Sending app RPC for: ${runningApp.packageName} (label=$appLabel, isGame=$isGameApp, isAI=$isAiApp)")
                            kizzyRPC?.apply {
                                setName(appLabel)
                                setDetails(details)
                                setState(state)
                                setStartTimestamps(System.currentTimeMillis())
                                setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "online"])
                                setLargeImage(runningApp.largeImage)
                                clearButtons()
                            }
                            kizzyRPC?.updateRPC(
                                CommonRpc(
                                    name = appLabel,
                                    type = ActivityTypeResolver.resolve(
                                        packageName = runningApp.packageName,
                                        fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                                        useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                                    ),
                                    details = details,
                                    state = state,
                                    largeImage = runningApp.largeImage,
                                    time = Timestamps(start = System.currentTimeMillis()),
                                    packageName = runningApp.packageName,
                                    appPackage = runningApp.packageName
                                ),
                                enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
                            )
                            return
                        }
                    }
                }
                // No media and no valid app detected - clear presence
                if (currentPackageName != null) {
                    currentPackageName = null
                    isPaused = false
                    lastKnownTrackTitle = null
                    lastKnownTrackArtist = null
                    currentMediaTitle = null
                    currentMediaArtist = null
                    currentMediaCoverArt = null
                    activeScrobbleTrack = null
                    lastFmNowPlayingIdentity = null
                    listenBrainzNowPlayingIdentity = null
                    stateRepository.setConnected(false)
                    stateRepository.clearTrackInfo()
                    stopTimelineUpdates()
                    scope.launch { kizzyRPC?.clearPresence() }
                    return
                }
            }

            val trackName = playingMedia.name.takeIf { it.isNotBlank() }
            stateRepository.setTrackInfo(
                title = trackName,
                artist = playingMedia.state,
                album = null
            )
            lastKnownTrackTitle = trackName
            lastKnownTrackArtist = playingMedia.state
            updateScrobblers(playingMedia)

            // Keep the 1-second timeline ticker running while media is playing. It is what
            // refreshes the Discord progress bar and the notification. It was previously
            // defined but never started, so the timeline never advanced.
            // Keep timeline ticker running when app detection is enabled even if paused,
            // so it can keep checking for foreground apps (games).
            val shouldKeepTimelineRunning = trackName != null && (!isPaused || Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false])
            if (shouldKeepTimelineRunning) {
                if (timelineJob.value?.isActive != true) {
                    startTimelineUpdates()
                }
            } else {
                stopTimelineUpdates()
            }

            val currentName = playingMedia.name.takeIf { it.isNotBlank() }
            val currentDetails = playingMedia.details
            val currentState = playingMedia.state
            val currentLargeImageUrl = if (showCoverArt) {
                playingMedia.largeImage?.let {
                    (it as? com.my.kizzy.data.rpc.RpcImage.BitmapImage)?.title ?: it.toString()
                }
            } else {
                null
            }

            // Update notification with current playback progress before skip check
            // Throttled: only update UI at most once per second while playing
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_MIN_INTERVAL_MS) {
                lastNotificationUpdateTime = now
                updateNotificationPauseButton()
            }

            val packageChanged = playingPkg != null && playingPkg != lastSentPackageName

            // The start timestamp is "now - position", so it stays constant while the track
            // plays and only moves when the user SEEKS. A move larger than the tolerance
            // is therefore a seek and must be pushed to Discord right away.
            val currentStart = playingMedia.time?.start
            val timelineJumped = currentStart != null && lastSentStartTimestamps != null &&
                    kotlin.math.abs(currentStart - lastSentStartTimestamps!!) > TIMELINE_JUMP_TOLERANCE_MS

            // The cover art belongs to the content as well: a new track can have the same
            // title/artist (e.g. a re-upload or a live stream) but different artwork.
            val contentChanged = currentName != lastSentName ||
                    currentDetails != lastSentDetails ||
                    currentState != lastSentState ||
                    currentLargeImageUrl != lastSentLargeImageUrl

            // Nothing worth sending: skip early to avoid the (costly) image resolution below.
            if (!forceRefresh && !packageChanged && !contentChanged && !timelineJumped) {
                logger.d(MEDIA_RPC, "Presence unchanged, skipping redundant update")
                return
            }

            // Publish the timeline only when the presence actually changes. The timeline
            // ticker used to call this every second, which pushed a new value into the
            // StateFlows once per second. The profile screen collects those flows, so it
            // recomposed the whole card every second for a progress bar that ticks on its
            // own anyway. A seek or a new track still reaches the UI immediately because
            // both bypass the "unchanged" check above.
            stateRepository.setTimestamps(playingMedia.time?.start, playingMedia.time?.end)

            // Resolving a bitmap cover art uploads it to Discord, which costs battery
            // and time - skip it entirely when the switch is off.
            val largeImageUrl = if (showCoverArt) {
                playingMedia.largeImage?.resolveImage(kizzyRepository)
            } else {
                null
            }
            val smallImageUrl = playingMedia.smallImage?.resolveImage(kizzyRepository)
            logger.d(MEDIA_RPC, "Small image resolved: original=${playingMedia.smallImage} resolved=$smallImageUrl smallImageChannelUrl=${playingMedia.smallImageChannelUrl}")
            stateRepository.setImageUrls(largeImageUrl, smallImageUrl)

            sendPresenceUpdate(playingMedia, enableTimestamps, showCoverArt)

            // Only remember what was sent after the send completed. If the upload or the
            // gateway failed, the next update must not be dropped as "unchanged".
            lastSentName = currentName
            lastSentDetails = currentDetails
            lastSentState = currentState
            lastSentStartTimestamps = currentStart
            lastSentStopTimestamps = playingMedia.time?.end
            lastSentLargeImageUrl = currentLargeImageUrl
            lastSentPackageName = playingPkg
            lastSentProgressSec = currentMediaProgressSec
            lastPresenceUpdateTime = now
        } finally {
            if (BuildConfig.DEBUG) Trace.endSection()
        }
    }

    /**
     * The direct url of the playing track.
     *
     * The media session of some players does not publish the url of the track,
     * so the link of the button used to be a search page. For YouTube the real
     * watch url is resolved through the oEmbed endpoint instead, which knows
     * the video that belongs to the title.
     */
    private suspend fun resolveDirectMediaUrl(): String? {
        val metadata = currentMediaController?.metadata ?: return null
        val packageName = currentPackageName ?: return null
        // For YouTube-based apps (including modded / wrapper clients), prefer the
        // suspend resolver that looks the video up by media id or title via the
        // oEmbed endpoint. It returns https://youtu.be/ID instead of falling back
        // to a "youtube.com/results?search_query=..." search page.
        if (MediaServiceResolver.isYouTube(packageName)) {
            MetadataResolver.getDirectMediaUrl(metadata, packageName)?.let { return it }
        }
        return MetadataResolver.getMediaUrl(metadata, packageName)
    }

    /**
     * The Discord activity type of the app.
     *
     * It used to be a single global preference, so every app - YouTube Music
     * included - was reported as "Playing", which Discord draws as a game. The
     * type is now resolved per app: the choice of the user wins, then the
     * built-in detection and finally the global fallback of the Media RPC.
     *
     * When the user explicitly sets a global activity type (not "Auto"), it
     * takes precedence over the built-in catalog but not over per-app overrides.
     */
    private fun resolveActivityType(playingMedia: CommonRpc): Int {
        val configured = Prefs[Prefs.MEDIA_RPC_ACTIVITY_TYPE, Prefs.ACTIVITY_TYPE_AUTO]
        val useAuto = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true]

        // Per-app override always wins (checked inside the resolver).
        // When the user explicitly sets a global type (not Auto), skip the
        // built-in catalog and metadata detection so the global choice takes
        // effect for all apps.
        val detectionEnabled = useAuto && configured == Prefs.ACTIVITY_TYPE_AUTO

        return ActivityTypeResolver.resolve(
            packageName = playingMedia.appPackage,
            isMusicMetadata = playingMedia.isMusicMetadata,
            fallback = if (configured == Prefs.ACTIVITY_TYPE_AUTO) {
                ActivityTypeResolver.DEFAULT_TYPE
            } else {
                configured
            },
            useAutomaticDetection = detectionEnabled,
        )
    }

    private suspend fun sendPresenceUpdate(
        playingMedia: CommonRpc,
        enableTimestamps: Boolean,
        showCoverArt: Boolean = Prefs[Prefs.MEDIA_RPC_SHOW_COVER_ART, true],
    ) {
        if (playingMedia.name.isBlank()) {
            stateRepository.setConnected(false)
            stateRepository.clearTrackInfo()
            stopTimelineUpdates()
            kizzyRPC?.clearPresence()
            return
        }

        stateRepository.setConnected(true)
        stateRepository.setPaused(isPaused)

        // Global toggle: if user disabled cover art entirely, hide cover art from all RPCs
        val showCoverArtGlobal = Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false

        // Resolve cover art: user explicit choice first, then per-app image, then default cover art
        val largeImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.largeImage ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
        } else {
            null
        }

        val smallImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.smallImage ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
        } else {
            null
        }

        // Final layout ("Show Song as Title" ON):
        //   Activity name (title) = song name
        //   details (line 2)      = "songName • appName"  (one clean line)
        //   state   (line 3)      = channel name  (link -> artist URL)
        val showSongAsTitle = Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true]
        val showAppNameInDetails = Prefs[Prefs.MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS, true]
        // details always carries the song name; appName is the dedicated app-name field.
        val songName = playingMedia.details?.takeIf { it.isNotBlank() }
            ?: playingMedia.name
        val appName = playingMedia.appName.takeIf { it.isNotBlank() }
            ?: playingMedia.name
        val channelName = playingMedia.state

        val rpcName = if (showSongAsTitle) songName else playingMedia.name
        val rpcDetails = listOfNotNull(
            songName,
            if (showAppNameInDetails) appName else null
        )
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .takeIf { it.isNotEmpty() } ?: playingMedia.details
        // state (line 3) = channel name
        val rpcState = channelName

        // Links are independent of the image toggles: hiding the cover art must
        // not disable the clickable text lines, and a default cover image still
        // needs its click target. Only real http(s) URLs reach the RPC fields.
        val songLink = YouTubeResolver.primaryMediaUrl(
            playingMedia.mediaUrl,
            playingMedia.albumUrl,
            playingMedia.channelUrl,
        ) ?: playingMedia.detailsUrl
        val detailsUrl = songLink
        val stateUrl = playingMedia.stateUrl
            ?: MediaServiceResolver.artistUrl(playingMedia.appPackage, channelName)

        // large image (cover art) -> album/playlist URL first, then video URL.
        // When line 4 shows the playlist name (no album), the playlist link wins.
        val largeUrl = YouTubeResolver.largeImageUrl(
            playingMedia.mediaUrl,
            playingMedia.albumUrl,
            playingMedia.playlistUrl,
        )
        // When the channel-avatar option is active, the small image links to
        // the channel page (direct link); otherwise to the app download page.
        val smallUrl = playingMedia.smallImageChannelUrl
            ?: MediaServiceResolver.appDownloadUrl(playingMedia.appPackage)

        // If no media/album/playlist URL was resolved, fall back to the app
        // download page so the cover art is always clickable when an image
        // is shown (e.g. Morphe, AndDea Revanced Patches, and other YouTube
        // wrapper clients that don't publish a YouTube video URL).
        val finalLargeUrl = largeUrl ?: smallUrl

        val commonRpc = CommonRpc(
            name = rpcName,
            type = resolveActivityType(playingMedia),
            details = rpcDetails,
            state = rpcState,
            detailsUrl = detailsUrl,
            stateUrl = stateUrl,
            largeImage = largeImage,
            smallImage = smallImage,
            largeUrl = finalLargeUrl,
            smallUrl = smallUrl,
            largeText = if (showCoverArtGlobal && showCoverArt) playingMedia.largeText else null,
            largeTextIsPlaylist = playingMedia.largeTextIsPlaylist,
            smallText = playingMedia.smallText,
            time = if (enableTimestamps && !isPaused) Timestamps(
                start = playingMedia.time?.start,
                end = playingMedia.time?.end
            ) else null,
            packageName = playingMedia.appPackage,
            playbackState = playingMedia.playbackState,
            mediaUrl = playingMedia.mediaUrl
        )

        // Expose the current presence as an RpcConfig so the Media/Manual RPC screens
        // can offer a "Copy to Custom RPC" button that pre-fills the Custom RPC editor.
        try {
            rpcManager.setCurrentPresenceConfig(
                commonRpcToRpcConfig(commonRpc).copy(instanceId = currentInstanceId ?: "media_rpc"),
                currentInstanceId ?: "media_rpc"
            )
        } catch (e: Exception) {
            logger.e(MEDIA_RPC, "Failed to expose current presence config: ${e.message}")
        }

        val mediaButtonEnabled = Prefs[MEDIA_RPC_MEDIA_BUTTON, false]
        val artistLinkEnabled = Prefs[MEDIA_RPC_ARTIST_LINK, false]
        // The buttons are cleared on every update. Without that the buttons of a
        // previous track (or of a setting that has been switched off) would stay
        // on the profile.
        kizzyRPC?.clearButtons()
        if (mediaButtonEnabled) {
            // Prefer the URL already resolved by GetCurrentlyPlayingMedia,
            // then the direct resolver, then a plain metadata search.
            // NEVER leave the button out just because the session published
            // no direct url: build a search link from the presence title
            // itself so the media (video / track / playlist) button always
            // exists - the artist button already falls back the same way.
            val candidateUrl = playingMedia.mediaUrl
                ?: resolveDirectMediaUrl()
                ?: currentMediaController?.metadata?.let { metadata ->
                    MetadataResolver.getMediaUrl(metadata, playingMedia.appPackage)
                }
                ?: MediaServiceResolver.mediaSearchUrl(
                    playingMedia.appPackage,
                    playingMedia.name.takeIf { it.isNotBlank() } ?: playingMedia.details,
                )
                ?: MediaServiceResolver.browserSearchUrl(
                    playingMedia.name.takeIf { it.isNotBlank() } ?: playingMedia.details,
                )
            val url = candidateUrl?.let { YouTubeResolver.toRpcUrl(it) ?: it }
            if (url != null) {
                kizzyRPC?.setButton1(
                    MediaServiceResolver.mediaLinkLabel(
                        url,
                        playingMedia.appPackage,
                        commonRpc.type ?: ActivityTypeResolver.DEFAULT_TYPE,
                    ),
                    url,
                )
            }
        }
        if (artistLinkEnabled) {
            currentMediaController?.metadata?.let { metadata ->
                // The artist url of YouTube is the real channel of the uploader,
                // which has to be looked up. It is a suspending call, so it is
                // made here and not inside the listener callback.
                val artistUrl = MetadataResolver.getArtistUrl(metadata, playingMedia.appPackage)
                artistUrl?.let { url ->
                    kizzyRPC?.setButton2(
                        MediaServiceResolver.artistButtonLabel(playingMedia.appPackage),
                        url,
                    )
                }
            }
        }

        // Saved button configurations from RPC Settings ("Use saved RPC
        // buttons") take precedence over the two media switches. They used to
        // be stored in preferences but never applied here, so enabling both
        // links in Settings produced a presence with no buttons at all.
        if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
            val saved = runCatching {
                rpcButtonsJson.decodeFromString<com.my.kizzy.domain.model.rpc.RpcButtons>(
                    Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]
                )
            }.getOrNull()
            if (saved != null) {
                kizzyRPC?.clearButtons()
                if (saved.button1.isNotBlank() && saved.button1Url.isNotBlank()) {
                    kizzyRPC?.setButton1(saved.button1, saved.button1Url)
                }
                if (saved.button2.isNotBlank() && saved.button2Url.isNotBlank()) {
                    kizzyRPC?.setButton2(saved.button2, saved.button2Url)
                }
            }
        }

        // forceRefresh = true: MediaRPC's own dedup above already determined the
        // content changed (new track, new artwork, seek, etc.). Tell KizzyRPC to
        // send it even if its hash-based dedup would otherwise drop it as
        // "content unchanged" - that keeps the RPC from freezing on the old song.
        // Priority 3 (Media): user-configured Custom/Manual RPC (priority 1/2)
        // takes precedence over automatic Media RPC.
        kizzyRPC?.updateRPC(commonRpc, enableTimestamps, forceRefresh = true, priority = 3)
        updateNotificationPauseButton()
    }

    /**
     * Converts the current [CommonRpc] presence into an [RpcConfig] so it can be
     * copied to the Custom RPC editor. Fields that require async resolution
     * (e.g. cover art upload) are left blank - the user can fill them manually.
     */
    private fun commonRpcToRpcConfig(commonRpc: CommonRpc): RpcConfig {
        val timestamps = commonRpc.time
        return RpcConfig(
            name = commonRpc.name,
            details = commonRpc.details.orEmpty(),
            state = commonRpc.state.orEmpty(),
            type = commonRpc.type?.toString().orEmpty(),
            timestampsStart = timestamps?.start?.toString().orEmpty(),
            timestampsStop = timestamps?.end?.toString().orEmpty(),
            largeImg = commonRpc.largeImage?.toUrlString().orEmpty(),
            smallImg = commonRpc.smallImage?.toUrlString().orEmpty(),
            largeText = commonRpc.largeText.orEmpty(),
            smallText = commonRpc.smallText.orEmpty(),
            detailsUrl = commonRpc.detailsUrl.orEmpty(),
            stateUrl = commonRpc.stateUrl.orEmpty(),
            largeUrl = commonRpc.largeUrl.orEmpty(),
            smallUrl = commonRpc.smallUrl.orEmpty(),
            partyCurrentSize = commonRpc.partyCurrentSize?.toString().orEmpty(),
            partyMaxSize = commonRpc.partyMaxSize?.toString().orEmpty(),
            status = "online",
            platform = commonRpc.platform.orEmpty(),
            url = commonRpc.mediaUrl.orEmpty(),
        )
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            // Players emit playback state events for every little buffering/position
            // change. Only a different state can change the presence, so the rest is
            // ignored instead of queueing another (rate limited) update.
            val stateCode = state?.state
            if (stateCode == lastPlaybackStateCode) return
            lastPlaybackStateCode = stateCode

            // Buffering/connecting are part of an active playback. Treating them as
            // "paused" stripped the timeline from the presence during the first second
            // of every new track (and then restored it a moment later).
            val newPaused = when (stateCode) {
                PlaybackState.STATE_PLAYING,
                PlaybackState.STATE_BUFFERING,
                PlaybackState.STATE_CONNECTING -> false
                else -> true
            }
            if (isPaused != newPaused) {
                isPaused = newPaused
                stateRepository.setPaused(isPaused)
                logger.d(MEDIA_RPC, "Playback state changed: paused=$isPaused")
                updateNotificationPauseButton()
            }

            scope.launch {
                delay(100)
                updatePresence(forceRefresh = true)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

            // The signature covers everything that ends up in the presence. A metadata
            // event that only repeats the same values must not delay the next real one.
            val signature = buildString {
                append(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)).append('|')
                append(metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)).append('|')
                append(metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)).append('|')
                append(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)).append('|')
                append(metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)).append('|')
                append(metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI))
            }
            if (signature == lastMetadataSignature) {
                logger.d(MEDIA_RPC, "Metadata unchanged, skipping redundant update")
                return
            }
            lastMetadataSignature = signature

            currentMediaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            currentMediaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

            scope.launch {
                // A new song is the update the user is waiting for; the old 1000 ms
                // delay made Discord show the previous track for over a second.
                delay(150)
                updatePresence(forceRefresh = true)
                updateNotificationPauseButton()
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()

            scope.launch {
                updatePresence(forceRefresh = true)
            }
        }
    }

    private fun updateNotificationPauseButton() {
        val title = currentMediaTitle ?: getString(R.string.rich_presence_active)

        // Build "artist · channels" plus the elapsed/total time, e.g.
        // "Artist · Stereo · 01:24 / 03:45".
        val parts = buildList {
            currentMediaArtist?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (currentMediaDurationSec > 0) {
                val currentStr = formatDuration(currentMediaPositionMs)
                val totalStr = formatDuration(currentMediaDurationSec.toLong() * 1000)
                add("$currentStr / $totalStr")
            }
        }
        val content = when {
            parts.isNotEmpty() -> parts.joinToString(" · ")
            isPaused -> getString(R.string.rich_presence_active)
            else -> getString(R.string.rich_presence_active)
        }

        val notification = buildNotification(
            title = title,
            content = content,
            coverArt = currentMediaCoverArt,
            progressSec = currentMediaProgressSec,
            durationSec = currentMediaDurationSec,
            paused = isPaused
        )
        notificationManager.notify(Constants.NOTIFICATION_ID_MEDIA_RPC, notification)
    }

    /**
     * Format milliseconds into MM:SS or HH:MM:SS string for notification display
     */
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        coverArt: Bitmap?,
        progressSec: Int,
        durationSec: Int,
        paused: Boolean,
    ): Notification {
        // The notification opens the app. The intent has to reuse the existing
        // task, otherwise the app is started a second time and the user sees
        // two layers of the UI on top of each other.
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        appIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.restart),
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, MediaRpcService::class.java).apply { action = Constants.ACTION_RESTART_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.exit),
                    PendingIntent.getService(
                        this, 2,
                        Intent(this, MediaRpcService::class.java).apply { action = Constants.ACTION_STOP_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )

        if (paused) {
            // A chronometer cannot be frozen, and the timeline must not keep
            // counting while the track is paused. The progress bar below still
            // shows the position where playback stopped.
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        } else {
            // Chronometer counts UP from the given time, so it has to be a wall-clock
            // timestamp of "now - position" for the elapsed time to be correct.
            builder.setWhen(System.currentTimeMillis() - currentMediaPositionMs)
            builder.setUsesChronometer(true)
            builder.setShowWhen(false)
        }

        coverArt?.let { builder.setLargeIcon(it) }

        // Progress bar support
        // Android 16+ (API 36+): NotificationCompat.ProgressStyle renders a rich timeline.
        // Android 8-15 (API 26-35): androidx's ProgressStyle degrades to a plain
        // setProgress() call, which is what shows the bar on those devices.
        if (durationSec > 0) {
            val progress = progressSec.coerceIn(0, durationSec)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                val progressStyle = NotificationCompat.ProgressStyle()
                    // 1. Defines the total timeline length (replaces setProgressMax)
                    .setProgressSegments(
                        listOf(NotificationCompat.ProgressStyle.Segment(durationSec))
                    )
                    // 2. Moves the tracker along the timeline in seconds
                    .setProgress(progress)
                    // 3. Draws a moving dot/icon on the timeline bar
                    .setProgressTrackerIcon(
                        IconCompat.createWithResource(this, FeatureRpcR.drawable.ic_rpc_tracker_dot)
                    )
                    // 4. Colors the elapsed portion of the timeline differently from the remaining portion
                    .setStyledByProgress(true)

                builder.setStyle(progressStyle)
            }

            // Always set the plain progress too, so API 26-35 devices show the bar.
            builder.setProgress(durationSec, progress, false)
        }

        return builder.build()
    }

    private fun activeSessionsListener(mediaSessions: List<MediaController>?, isEvent: Boolean = true) {
        logger.d(MEDIA_RPC, "Active sessions changed")

        if (isEvent) {
            scope.launch {
                delay(1000)
                applySessionChange(mediaSessions)
            }
        } else {
            applySessionChange(mediaSessions)
        }
    }

    private fun applySessionChange(mediaSessions: List<MediaController>?) {
        currentMediaController?.unregisterCallback(mediaControllerCallback)

        if (mediaSessions?.isNotEmpty() == true) {
            val selectedSession = mediaSessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
            } ?: mediaSessions.firstOrNull { it.metadata != null }
            ?: mediaSessions.firstOrNull()

            currentMediaController = selectedSession

            scope.launch(Dispatchers.Main) {
                currentMediaController?.registerCallback(mediaControllerCallback)
            }

            logger.d(MEDIA_RPC, "Tracking session: ${selectedSession?.packageName} (${selectedSession?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "no title"})")

            scope.launch {
                delay(500)
                updatePresence(forceRefresh = true)
            }
        } else {
            currentMediaController = null
            currentPackageName = null
            isPaused = false
            lastKnownTrackTitle = null
            lastKnownTrackArtist = null
            stateRepository.setConnected(false)
            stateRepository.clearTrackInfo()
            stopTimelineUpdates()

            scope.launch {
                updatePresence(forceRefresh = true)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun setupWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kizzy:MediaRPC")
        // Do NOT acquire here - wake lock is acquired per-work in withWakeLock()
        // so the CPU can sleep between media updates, saving battery.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wire Media RPC to slot "1" (default token). If no token is configured,
        // kizzyRPC stays null and no presence is sent.
        kizzyRPC = multiRpc.session("1")

        val ac = intent?.action
        when (ac) {
            Constants.ACTION_ADD_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    currentInstanceId = instanceId
                    val oauthIndex = intent.getIntExtra(Constants.EXTRA_OAUTH_INDEX, 1)
                    multiRpc.addInstance("1", instanceId, oauthIndex)
                    // Each Media clone gets its own independent notification.
                    CloneNotificationHelper.show(
                        context = this@MediaRpcService,
                        notificationManager = notificationManager,
                        serviceClass = MediaRpcService::class.java,
                        instanceId = instanceId,
                        label = Prefs.getHomeRpcInstances().firstOrNull { it.id == instanceId }?.label
                            ?: getString(R.string.main_mediaRpc),
                        content = currentMediaTitle ?: getString(R.string.rich_presence_active),
                        iconRes = R.drawable.ic_media_rpc,
                    )
                    // Broadcast the current media presence to the new socket.
                    restartInPlace()
                }
            }
            Constants.ACTION_REMOVE_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    multiRpc.removeInstance("1", instanceId)
                    CloneNotificationHelper.cancel(notificationManager, instanceId)
                    rpcManager.removePresenceConfig(instanceId)
                }
                if (multiRpc.instanceCount("1") == 0) {
                    stopSelf()
                } else {
                    // The remaining instance still needs a fresh presence on its
                    // own application ID after the other socket was removed.
                    restartInPlace()
                }
            }
            Constants.ACTION_STOP_SERVICE -> {
                // The user stopped the RPC on purpose, so forget the enabled
                // state as well - otherwise the next cold start would restore it.
                Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
                // Remove MediaRpc from ACTIVE_RPC_TYPES so a cold start doesn't restore it.
                val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                    .split(',')
                    .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                    .filter { it != RpcManager.RpcType.MediaRpc }
                    .joinToString(",") { it.name }
                Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
                // Also clear RpcManager's active set so refreshFromServices() doesn't
                // resurrect this service when the screen resumes.
                rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.MediaRpc))
                // Move DataStore write off main thread to avoid StrictMode DiskReadViolation
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Prefs[Prefs.MEDIA_RPC_ENABLED] = false
                    }
                }
                stopSelf()
            }
            Constants.ACTION_RESTART_SERVICE -> {
                // The service must NOT be stopped first. stopSelf() tears the
                // service down and the pending stop then killed the fresh
                // instance, so the restart did nothing. Instead the current
                // instance is reused and its state is reset.
                logger.d(MEDIA_RPC, "Restart requested, resetting the running service")
                restartInPlace()
            }
            Constants.ACTION_TOGGLE -> {
                isPaused = !isPaused
                stateRepository.setPaused(isPaused)
                logger.d(MEDIA_RPC, "Toggle pressed: paused=$isPaused")
                updateNotificationPauseButton()
                if (!isPaused) {
                    scope.launch { updatePresence() }
                } else {
                    stopTimelineUpdates()
                    scope.launch { kizzyRPC?.clearPresence() }
                    stateRepository.setConnected(false)
                }
            }
            null -> {
                // Normal start (no action): mark as enabled so the watchdog knows
                // it should stay alive.
                scope.launch {
                    withContext(Dispatchers.IO) {
                        Prefs[Prefs.MEDIA_RPC_ENABLED] = true
                    }
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    /**
     * Restarts the RPC without tearing the service down.
     *
     * The service re-reads the preferences, re-reads the media session and
     * sends the presence again, so a restart is visible on Discord right away.
     */
    private fun restartInPlace() {
        scope.launch {
            withContext(Dispatchers.IO) {
                Prefs[Prefs.MEDIA_RPC_ENABLED] = true
            }
        }

        // Forget what was sent, so the next update is not dropped as
        // "content unchanged".
        lastSentName = null
        lastSentDetails = null
        lastSentState = null
        lastSentStartTimestamps = null
        lastSentStopTimestamps = null
        lastSentLargeImageUrl = null
        lastSentPackageName = null
        lastPresenceUpdateTime = 0L
        lastPlaybackStateCode = null
        lastMetadataSignature = null

        isPaused = false
        stateRepository.setPaused(false)

        // Pick the current media session up again.
        val sessions: List<MediaController> = try {
            mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
        applySessionChange(sessions)

        scope.launch {
            updatePresence(forceRefresh = true)
        }
    }

    override fun onTaskRemoved(intent: Intent?) {
        logger.d(MEDIA_RPC, "onTaskRemoved: user swiped app from Recents")

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (Prefs[Prefs.USE_RPC_BUTTONS, false] || Prefs[MEDIA_RPC_MEDIA_BUTTON, false] || Prefs[MEDIA_RPC_ARTIST_LINK, false]) {
            logger.d(MEDIA_RPC, "Scheduling RPC recovery work after task removal")
            WorkManager.getInstance(this)
                .enqueueUniqueWork(
                    "kizzy_rpc_recovery",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<RpcRecoveryHiltWorker>()
                        .build()
                )
        }

        super.onTaskRemoved(intent)
    }

    override fun onDestroy() {
        stopTimelineUpdates()
        stopHeartbeat()
        stopAppDetectionCoroutine()
        mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        // Cancel pending work first, then clear/close off the main thread.
        scope.cancel()
        val rpc = kizzyRPC
        AppUtils.unregisterRpcService(this)
        if (rpc != null) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
        stateRepository.setConnected(false)
        stateRepository.setPaused(false)
        stateRepository.clearTrackInfo()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        // Schedule recovery only if MediaRpc is still in RpcManager.activeRpc.value
        // (system kill), not when stopRpcTypes() already removed it (explicit user stop).
        val mediaStillEnabled = RpcManager.RpcType.MediaRpc in rpcManager.activeRpc.value
        if (mediaStillEnabled) {
            WorkManagerHelper.scheduleRpcRecovery(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}