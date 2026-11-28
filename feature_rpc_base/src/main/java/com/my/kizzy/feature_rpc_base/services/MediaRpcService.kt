package com.my.kizzy.feature_rpc_base.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.data.rpc.DynamicActivityDetectionEngine
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.BuildConfig
import com.my.kizzy.feature_rpc_base.R as FeatureRpcR
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_API_KEY
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_SECRET
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LIBREFM_API_KEY
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LIBREFM_SECRET
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFMandLibreFM
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFMandLibreFMApiClient
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFmException
import com.my.kizzy.feature_rpc_base.services.lastfm.LibreFMandLibreFM
import com.my.kizzy.feature_rpc_base.services.listenbrainz.ListenBrainz
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_LINK
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_MEDIA_BUTTON
import com.my.kizzy.preference.Prefs.TOKEN
import com.my.kizzy.preference.RPC_INSTANCE_MEDIA
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private const val MEDIA_RPC = "MediaRPC"
private const val MIN_RPC_INTERVAL_MS = 500L
private const val MAX_RPC_IDLE_INTERVAL_MS = 14_500L
private const val NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 1_000L
private const val TIMELINE_JUMP_TOLERANCE_MS = 3_000L
private val rpcButtonsJson = Json { ignoreUnknownKeys = true }

@AndroidEntryPoint
class MediaRpcService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var kizzyRPC: KizzyRPC? = null
    private var currentInstanceId: String? = null
    private val activeInstanceIds = LinkedHashSet<String>()

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var getCurrentPlayingMedia: GetCurrentPlayingMedia

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var stateRepository: KizzyStateRepository

    @Inject
    lateinit var metadataResolver: MetadataResolver

    @Inject
    lateinit var kizzyRepository: KizzyRepository

    @Inject
    lateinit var getCurrentlyRunningApp: GetCurrentlyRunningApp

    @Inject
    lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine

    @Inject
    lateinit var rpcManager: RpcManager

    @Inject
    lateinit var notificationManager: NotificationManager

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
    private var libreFmScrobbledIdentity: String? = null
    private var libreFmNowPlayingIdentity: String? = null
    private var libreFmScrobbleInFlight: String? = null
    private var libreFmNowPlayingInFlight: String? = null
    private var libreFmScrobbleRetryAfter: Long = 0
    private var libreFmNowPlayingRetryAfter: Long = 0

    private val lastFmAuth = LastFmAuthState(
        name = "LastFM",
        client = LastFMandLibreFM,
        enabledPreference = Prefs.LASTFM_ENABLED,
        apiKeyPreference = Prefs.LASTFM_API_KEY,
        secretPreference = Prefs.LASTFM_SECRET,
        usernamePreference = Prefs.LASTFM_USERNAME,
        passwordPreference = Prefs.LASTFM_PASSWORD,
        sessionKeyPreference = Prefs.LASTFM_SESSION_KEY
    )
    private val libreFmAuth = LastFmAuthState(
        name = "LibreFM",
        client = LibreFMandLibreFM,
        enabledPreference = Prefs.LIBREFM_ENABLED,
        apiKeyPreference = Prefs.LIBREFM_API_KEY,
        secretPreference = Prefs.LIBREFM_SECRET,
        usernamePreference = Prefs.LIBREFM_USERNAME,
        passwordPreference = Prefs.LIBREFM_PASSWORD,
        sessionKeyPreference = Prefs.LIBREFM_SESSION_KEY
    )

    private data class LastFmAuthState(
        val name: String,
        val client: LastFMandLibreFMApiClient,
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
    private var explicitStop = false

    private fun readInstanceBool(instanceId: String?, prefKey: String, default: Boolean): Boolean =
        if (instanceId == null || instanceId == RPC_INSTANCE_MEDIA) {
            Prefs[prefKey, default]
        } else {
            Prefs[Prefs.scopedKey(prefKey, instanceId), default]
        }

    private fun readInstanceInt(instanceId: String?, prefKey: String, default: Int): Int =
        if (instanceId == null || instanceId == RPC_INSTANCE_MEDIA) {
            Prefs[prefKey, default]
        } else {
            Prefs[Prefs.scopedKey(prefKey, instanceId), default]
        }

    private fun targetInstances(): List<String?> {
        val tracked = activeInstanceIds.map { id -> if (id == RPC_INSTANCE_MEDIA) null else id }
        return if (tracked.isEmpty()) listOf(currentInstanceId) else tracked
    }

    private suspend fun withWakeLock(block: suspend () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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

    private var detectedAppPackage: String? = null
    private var detectedAppStartMs: Long? = null
    private var activeAppOverride: Boolean = false

    private val rpcPacer = RpcPacer(
        minIntervalMs = MIN_RPC_INTERVAL_MS,
        maxIdleIntervalMs = MAX_RPC_IDLE_INTERVAL_MS,
    )

    private var lastNotificationUpdateTime: Long = 0L
    private var lastPlaybackStateCode: Int? = null
    private var lastMetadataSignature: String? = null

    private val presenceUpdateMutex = Mutex()
    private var timelineJob: Job? = null
    private var appDetectionJob: Job? = null

    private lateinit var mediaSessionManager: MediaSessionManager

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

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            val stateCode = state?.state
            if (stateCode == lastPlaybackStateCode) return
            lastPlaybackStateCode = stateCode

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

            serviceScope.launch {
                delay(100)
                updatePresence(forceRefresh = true)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

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

            serviceScope.launch {
                delay(150)
                updatePresence(forceRefresh = true)
                updateNotificationPauseButton()
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()

            serviceScope.launch {
                updatePresence(forceRefresh = true)
            }
        }
    }

    private fun resolveAppStartTimestamp(packageName: String): Long {
        if (detectedAppPackage != packageName || detectedAppStartMs == null) {
            detectedAppPackage = packageName
            detectedAppStartMs = System.currentTimeMillis()
        }
        return detectedAppStartMs!!
    }

    private fun clearDetectedAppTimestamp() {
        detectedAppPackage = null
        detectedAppStartMs = null
        activeAppOverride = false
    }

    private suspend fun tryPublishPriorityAppActivity(forceRefresh: Boolean): Boolean {
        if (!readInstanceBool(currentInstanceId, Prefs.MEDIA_RPC_USE_APPS_RPC, false)) {
            if (activeAppOverride) {
                activeAppOverride = false
                detectedAppPackage = null
                detectedAppStartMs = null
            }
            return false
        }

        val activeRpcTypes = rpcManager.activeRpc.value
        val customOrManualRpcActive = activeRpcTypes.contains(RpcManager.RpcType.CustomRpc) ||
                activeRpcTypes.contains(RpcManager.RpcType.ConsoleRpc) ||
                activeRpcTypes.contains(RpcManager.RpcType.ManualRpc)
        if (customOrManualRpcActive) {
            return false
        }

        val runningApp = getCurrentlyRunningApp()
        if (runningApp.name.isEmpty() || runningApp.packageName in AppDetectionService.EXCLUDED_APPS) {
            if (activeAppOverride) {
                activeAppOverride = false
                detectedAppPackage = null
                detectedAppStartMs = null
                return true
            }
            return false
        }

        val isGameApp = dynamicActivityDetectionEngine.identifyGame(runningApp.packageName) != null
        val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName) != null
        val enabledApps = runCatching {
            Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_APPS, "[]"])
        }.getOrDefault(emptyList())
        val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps
        if (!shouldShow) {
            if (activeAppOverride) {
                activeAppOverride = false
                detectedAppPackage = null
                detectedAppStartMs = null
                return true
            }
            return false
        }

        val appChanged = runningApp.packageName != detectedAppPackage || !activeAppOverride
        activeAppOverride = true
        val appLabel = runningApp.name.takeIf { it.isNotBlank() } ?: runningApp.packageName
        val details = when {
            isGameApp -> "Playing game"
            isAiApp -> "Chatting with AI"
            else -> null
        }
        val state = when {
            isGameApp -> "In-game"
            isAiApp -> "Thinking..."
            else -> null
        }
        val startMs = resolveAppStartTimestamp(runningApp.packageName)

        if (rpcPacer.acquireSendSlot(hasMeaningfulChange = appChanged, forceRefresh = forceRefresh || appChanged)) {
            kizzyRPC?.apply {
                setName(appLabel)
                setDetails(details)
                setState(state)
                setStartTimestamps(startMs)
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
                    time = Timestamps(start = startMs),
                    packageName = runningApp.packageName,
                    appPackage = runningApp.packageName
                ),
                enableTimestamps = readInstanceBool(currentInstanceId, MEDIA_RPC_ENABLE_TIMESTAMPS, true)
            )
        }
        return true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_CHANNEL_ID,
            "Kizzy Media RPC",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Media Rich Presence notification channel"
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        activeInstanceIds.add(RPC_INSTANCE_MEDIA)
        AppUtils.registerRpcService(this)
        createNotificationChannel()
        val basicNotification = NotificationCompat.Builder(
            this,
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle("Rich Presence Active")
            .setContentText("Starting...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(
                com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_ID_MEDIA_RPC,
                basicNotification,
                FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_ID_MEDIA_RPC,
                basicNotification
            )
        }

        val token = Prefs[TOKEN, ""]
        if (token.isBlank()) {
            logger.d(MEDIA_RPC, "onCreate: token currently empty, waiting for refreshFromPrefs()")
        }
        Prefs[Prefs.MEDIA_RPC_ENABLED] = true
        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.MediaRpc.name
        setupWakeLock()
        logger.d(
            MEDIA_RPC,
            "onCreate: starting app detection coroutine, useAppsRpc=${Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]}"
        )
        startAppDetectionCoroutine()

        val stopIntent = Intent(this, MediaRpcService::class.java).apply {
            action = com.my.kizzy.feature_rpc_base.Constants.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(this, MediaRpcService::class.java).apply {
            action = com.my.kizzy.feature_rpc_base.Constants.ACTION_RESTART_SERVICE
        }
        val restartPendingIntent = PendingIntent.getService(
            this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(
            this,
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_CHANNEL_ID
        )
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

        notificationManager.notify(
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_ID_MEDIA_RPC,
            notification
        )

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                ComponentName(this, NotificationListener::class.java)
            )
        } catch (e: SecurityException) {
            logger.w(MEDIA_RPC, "No notification access for active media sessions: ${e.message}")
        }

        val sessions: List<MediaController> = try {
            mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
        activeSessionsListener(sessions, isEvent = false)
    }

    private fun startTimelineUpdates() {
        stopTimelineUpdates()
        timelineJob = serviceScope.launch {
            while (isActive) {
                delay(MAX_RPC_IDLE_INTERVAL_MS)
                if (!isPaused && kizzyRPC?.isRpcRunning() == true) {
                    withWakeLock {
                        updatePresence(forceRefresh = false)
                    }
                }
            }
        }
    }

    private fun stopTimelineUpdates() {
        timelineJob?.cancel()
        timelineJob = null
    }

    private fun startAppDetectionCoroutine() {
        if (appDetectionJob?.isActive == true) {
            logger.d(MEDIA_RPC, "App detection coroutine already running")
            return
        }
        logger.d(MEDIA_RPC, "Starting app detection coroutine")

        appDetectionJob = serviceScope.launch {
            var cycleCount = 0
            while (isActive) {
                cycleCount++
                try {
                    val useAppsRpc = readInstanceBool(currentInstanceId, Prefs.MEDIA_RPC_USE_APPS_RPC, false)
                    if (!useAppsRpc) {
                        if (cycleCount % 6 == 1) {
                            logger.d(
                                MEDIA_RPC,
                                "App detection loop running but MEDIA_RPC_USE_APPS_RPC is false - skipping"
                            )
                        }
                        if (activeAppOverride) {
                            activeAppOverride = false
                            detectedAppPackage = null
                            detectedAppStartMs = null
                            updatePresence(forceRefresh = true)
                        }
                        delay(5000.milliseconds)
                        continue
                    }

                    val hadOverride = activeAppOverride
                    val overrideApplied = tryPublishPriorityAppActivity(forceRefresh = false)
                    if (hadOverride && !overrideApplied) {
                        updatePresence(forceRefresh = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(MEDIA_RPC, "App detection cycle failed: ${e.message}")
                }
                delay(2000.milliseconds)
            }
        }
    }

    private fun stopAppDetectionCoroutine() {
        appDetectionJob?.cancel()
        appDetectionJob = null
        clearDetectedAppTimestamp()
    }

    private suspend fun updatePresence(forceRefresh: Boolean = false) {
        presenceUpdateMutex.withLock {
            updatePresenceLocked(forceRefresh)
        }
    }

    private suspend fun ensureLastFmCompatibleSession(auth: LastFmAuthState): Boolean =
        auth.mutex.withLock {
            if (!Prefs[auth.enabledPreference, false]) return@withLock false

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
        val artist = metadata?.let { metadataResolver.getArtistOrAuthor(it) }
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

        val album = metadata?.let { metadataResolver.getAlbum(it) }
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
            serviceScope.launch {
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
            serviceScope.launch {
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
            serviceScope.launch {
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
            serviceScope.launch {
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
            serviceScope.launch {
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
            serviceScope.launch {
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
            if (tryPublishPriorityAppActivity(forceRefresh = forceRefresh)) {
                return
            }

            val instances = targetInstances()
            val primaryInstance = currentInstanceId
            val enableTimestamps = readInstanceBool(primaryInstance, MEDIA_RPC_ENABLE_TIMESTAMPS, true)
            val showCoverArt = readInstanceBool(primaryInstance, Prefs.MEDIA_RPC_SHOW_COVER_ART, true)
            val playingMedia = getCurrentPlayingMedia(primaryInstance)

            currentMediaTitle = playingMedia.name.takeIf { it.isNotBlank() }
            currentMediaArtist = playingMedia.state?.takeIf { it.isNotBlank() }
            currentMediaCoverArt = if (showCoverArt) {
                playingMedia.largeImage?.let {
                    if (it is RpcImage.BitmapImage) it.bitmap else null
                }
            } else {
                null
            }

            playingMedia.time?.let { time ->
                val start = time.start ?: 0L
                val end = time.end ?: 0L
                currentMediaDurationSec = ((end - start) / 1000).toInt().coerceAtLeast(0)
                val elapsedMs = System.currentTimeMillis() - start
                currentMediaPositionMs = elapsedMs.coerceIn(0L, currentMediaDurationSec * 1000L)
                currentMediaProgressSec = (currentMediaPositionMs / 1000).toInt().coerceIn(0, currentMediaDurationSec)
            } ?: run {
                currentMediaDurationSec = 0
                currentMediaProgressSec = 0
                currentMediaPositionMs = 0L
            }

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

            if (playingPkg == null) {
                if (currentPackageName != null) {
                    currentPackageName = null
                    isPaused = false
                    clearDetectedAppTimestamp()
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
                    kizzyRPC?.clearPresence()
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

            val shouldKeepTimelineRunning = trackName != null && (!isPaused || readInstanceBool(primaryInstance, Prefs.MEDIA_RPC_USE_APPS_RPC, false))
            if (shouldKeepTimelineRunning) {
                if (timelineJob?.isActive != true) {
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
                    (it as? RpcImage.BitmapImage)?.title ?: it.toString()
                }
            } else {
                null
            }

            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_MIN_INTERVAL_MS) {
                lastNotificationUpdateTime = now
                updateNotificationPauseButton()
            }

            val packageChanged = playingPkg != null && playingPkg != lastSentPackageName
            val currentStart = playingMedia.time?.start
            val timelineJumped = currentStart != null && lastSentStartTimestamps != null &&
                    abs(currentStart - lastSentStartTimestamps!!) > TIMELINE_JUMP_TOLERANCE_MS

            val contentChanged = currentName != lastSentName ||
                    currentDetails != lastSentDetails ||
                    currentState != lastSentState ||
                    currentLargeImageUrl != lastSentLargeImageUrl

            val hasMeaningfulChange = packageChanged || contentChanged || timelineJumped
            if (!rpcPacer.acquireSendSlot(hasMeaningfulChange = hasMeaningfulChange, forceRefresh = forceRefresh)) {
                logger.d(MEDIA_RPC, "Presence unchanged within idle window, skipping update")
                return
            }

            stateRepository.setTimestamps(playingMedia.time?.start, playingMedia.time?.end)

            val largeImageUrl = if (showCoverArt) {
                playingMedia.largeImage?.resolveImage(kizzyRepository)
            } else {
                null
            }
            val smallImageUrl = playingMedia.smallImage?.resolveImage(kizzyRepository)
            logger.d(
                MEDIA_RPC,
                "Small image resolved: original=${playingMedia.smallImage} resolved=$smallImageUrl smallImageChannelUrl=${playingMedia.smallImageChannelUrl}"
            )
            stateRepository.setImageUrls(largeImageUrl, smallImageUrl)

            val effectiveTimestamps = if (hasMeaningfulChange) {
                playingMedia.time
            } else {
                playingMedia.time?.copy(start = lastSentStartTimestamps ?: playingMedia.time?.start)
            }
            val mediaForDispatch = if (effectiveTimestamps != null) {
                playingMedia.copy(time = effectiveTimestamps)
            } else {
                playingMedia
            }

            for (instanceKey in instances) {
                val instTimestamps = readInstanceBool(instanceKey, MEDIA_RPC_ENABLE_TIMESTAMPS, enableTimestamps)
                val instCoverArt = readInstanceBool(instanceKey, Prefs.MEDIA_RPC_SHOW_COVER_ART, showCoverArt)
                val instMedia = if (instanceKey == primaryInstance) {
                    mediaForDispatch
                } else {
                    getCurrentPlayingMedia(instanceKey).let { inst ->
                        if (effectiveTimestamps != null) inst.copy(time = effectiveTimestamps) else inst
                    }
                }
                sendPresenceUpdate(
                    playingMedia = instMedia,
                    enableTimestamps = instTimestamps,
                    showCoverArt = instCoverArt,
                    instanceId = instanceKey,
                )
            }

            lastSentName = currentName
            lastSentDetails = currentDetails
            lastSentState = currentState
            lastSentStartTimestamps = effectiveTimestamps?.start ?: currentStart
            lastSentStopTimestamps = effectiveTimestamps?.end ?: playingMedia.time?.end
            lastSentLargeImageUrl = currentLargeImageUrl
            lastSentPackageName = playingPkg
            lastSentProgressSec = currentMediaProgressSec
            lastPresenceUpdateTime = System.currentTimeMillis()
        } finally {
            if (BuildConfig.DEBUG) Trace.endSection()
        }
    }

    private suspend fun resolveDirectMediaUrl(): String? {
        val metadata = currentMediaController?.metadata ?: return null
        val pkg = currentPackageName ?: return null
        if (MediaServiceResolver.isYouTube(pkg)) {
            metadataResolver.getDirectMediaUrl(metadata, pkg)?.let { return it }
        }
        return metadataResolver.getMediaUrl(metadata, pkg)
    }

    private fun resolveActivityType(playingMedia: CommonRpc, instanceId: String? = currentInstanceId): Int {
        val configured = readInstanceInt(instanceId, Prefs.MEDIA_RPC_ACTIVITY_TYPE, Prefs.ACTIVITY_TYPE_AUTO)
        val useAuto = readInstanceBool(instanceId, Prefs.AUTO_ACTIVITY_TYPE, true)
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
        instanceId: String? = currentInstanceId,
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

        val showCoverArtGlobal = !Prefs[Prefs.RPC_HIDE_COVER_ART, false]

        val largeImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.largeImage
                ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
        } else {
            null
        }

        val smallImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.smallImage
                ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
        } else {
            null
        }

        val showSongAsTitle = readInstanceBool(instanceId, Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true)
        val showAppNameInDetails = readInstanceBool(instanceId, Prefs.MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS, true)
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
        val rpcState = channelName

        val songLink = YouTubeResolver.primaryMediaUrl(
            playingMedia.mediaUrl,
            playingMedia.albumUrl,
            playingMedia.channelUrl,
        ) ?: playingMedia.detailsUrl
        val detailsUrl = songLink
        val stateUrl = playingMedia.stateUrl
            ?: MediaServiceResolver.artistUrl(playingMedia.appPackage, channelName)

        val largeUrl = YouTubeResolver.largeImageUrl(
            playingMedia.mediaUrl,
            playingMedia.albumUrl,
            playingMedia.playlistUrl,
        )
        val smallUrl = playingMedia.smallImageChannelUrl
            ?: MediaServiceResolver.appDownloadUrl(playingMedia.appPackage)
        val finalLargeUrl = largeUrl ?: smallUrl

        val commonRpc = CommonRpc(
            name = rpcName,
            type = resolveActivityType(playingMedia, instanceId),
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

        try {
            val instanceKey = instanceId ?: RPC_INSTANCE_MEDIA
            rpcManager.setCurrentPresenceConfig(
                commonRpcToRpcConfig(commonRpc).copy(instanceId = instanceKey),
                instanceKey
            )
        } catch (e: Exception) {
            logger.e(MEDIA_RPC, "Failed to expose current presence config: ${e.message}")
        }

        val mediaButtonEnabled = Prefs[MEDIA_RPC_MEDIA_BUTTON, false]
        val artistLinkEnabled = Prefs[MEDIA_RPC_ARTIST_LINK, false]
        kizzyRPC?.clearButtons()
        if (mediaButtonEnabled) {
            val candidateUrl = playingMedia.mediaUrl
                ?: resolveDirectMediaUrl()
                ?: currentMediaController?.metadata?.let { metadata ->
                    metadataResolver.getMediaUrl(metadata, playingMedia.appPackage)
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
                val artistUrl = metadataResolver.getArtistUrl(metadata, playingMedia.appPackage)
                artistUrl?.let { url ->
                    kizzyRPC?.setButton2(
                        MediaServiceResolver.artistButtonLabel(playingMedia.appPackage),
                        url,
                    )
                }
            }
        }

        if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
            val saved = runCatching {
                rpcButtonsJson.decodeFromString<RpcButtons>(
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

        kizzyRPC?.updateRPC(commonRpc, enableTimestamps, forceRefresh = true, priority = 3)
        updateNotificationPauseButton()
    }

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

    private fun updateNotificationPauseButton() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < NOTIFICATION_UPDATE_MIN_INTERVAL_MS) return
        lastNotificationUpdateTime = now

        val title = currentMediaTitle ?: getString(R.string.rich_presence_active)

        val parts = buildList {
            currentMediaArtist?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (currentMediaDurationSec > 0) {
                val currentStr = formatDuration(currentMediaPositionMs)
                val totalStr = formatDuration(currentMediaDurationSec.toLong() * 1000)
                add("$currentStr / $totalStr")
            }
        }
        val content = parts.joinToString(" · ").ifEmpty { getString(R.string.rich_presence_active) }

        val notification = buildNotification(
            title = title,
            content = content,
            coverArt = currentMediaCoverArt,
            progressSec = currentMediaProgressSec,
            durationSec = currentMediaDurationSec,
            paused = isPaused
        )
        notificationManager.notify(
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_ID_MEDIA_RPC,
            notification
        )
    }

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

        val builder = NotificationCompat.Builder(
            this,
            com.my.kizzy.feature_rpc_base.Constants.NOTIFICATION_CHANNEL_ID
        )
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
                        Intent(this, MediaRpcService::class.java).apply {
                            action = com.my.kizzy.feature_rpc_base.Constants.ACTION_RESTART_SERVICE
                        },
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
                        Intent(this, MediaRpcService::class.java).apply {
                            action = com.my.kizzy.feature_rpc_base.Constants.ACTION_STOP_SERVICE
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )

        if (paused) {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        } else {
            builder.setWhen(System.currentTimeMillis() - currentMediaPositionMs)
            builder.setUsesChronometer(true)
            builder.setShowWhen(false)
        }

        coverArt?.let { builder.setLargeIcon(it) }

        if (durationSec > 0) {
            val progress = progressSec.coerceIn(0, durationSec)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                val progressStyle = NotificationCompat.ProgressStyle()
                    .setProgressSegments(
                        listOf(NotificationCompat.ProgressStyle.Segment(durationSec))
                    )
                    .setProgress(progress)
                    .setProgressTrackerIcon(
                        IconCompat.createWithResource(this, FeatureRpcR.drawable.ic_rpc_tracker_dot)
                    )
                    .setStyledByProgress(true)

                builder.setStyle(progressStyle)
            }

            builder.setProgress(durationSec, progress, false)
        }

        return builder.build()
    }

    private fun activeSessionsListener(mediaSessions: List<MediaController>?, isEvent: Boolean = true) {
        logger.d(MEDIA_RPC, "Active sessions changed (isEvent=$isEvent)")

        if (isEvent) {
            serviceScope.launch {
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
            selectedSession?.registerCallback(mediaControllerCallback)

            logger.d(
                MEDIA_RPC,
                "Tracking session: ${selectedSession?.packageName} (${selectedSession?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "no title"})"
            )

            serviceScope.launch {
                delay(500)
                updatePresence(forceRefresh = true)
            }
        } else {
            currentMediaController = null
            currentPackageName = null
            isPaused = false
            clearDetectedAppTimestamp()
            lastKnownTrackTitle = null
            lastKnownTrackArtist = null
            stateRepository.setConnected(false)
            stateRepository.clearTrackInfo()
            stopTimelineUpdates()

            serviceScope.launch {
                updatePresence(forceRefresh = true)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun setupWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kizzy:MediaRPC")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kizzyRPC = multiRpc.session("1")

        when (intent?.action) {
            com.my.kizzy.feature_rpc_base.Constants.ACTION_ADD_INSTANCE -> {
                val instanceId = intent.getStringExtra(com.my.kizzy.feature_rpc_base.Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    activeInstanceIds.add(instanceId)
                    currentInstanceId = instanceId
                    val oauthIndex = intent.getIntExtra(com.my.kizzy.feature_rpc_base.Constants.EXTRA_OAUTH_INDEX, 1)
                    multiRpc.addInstance("1", instanceId, oauthIndex)
                    if (instanceId != RPC_INSTANCE_MEDIA) {
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
                    }
                    serviceScope.launch {
                        updatePresence(forceRefresh = true)
                    }
                }
            }

            com.my.kizzy.feature_rpc_base.Constants.ACTION_REMOVE_INSTANCE -> {
                val instanceId = intent.getStringExtra(com.my.kizzy.feature_rpc_base.Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    activeInstanceIds.remove(instanceId)
                    multiRpc.removeInstance("1", instanceId)
                    CloneNotificationHelper.cancel(notificationManager, instanceId)
                    rpcManager.removePresenceConfig(instanceId)
                    if (currentInstanceId == instanceId) {
                        currentInstanceId = activeInstanceIds.lastOrNull()
                    }
                }
                if (activeInstanceIds.isEmpty() || multiRpc.instanceCount("1") == 0) {
                    explicitStop = true
                    stopSelf()
                } else {
                    serviceScope.launch {
                        updatePresence(forceRefresh = true)
                    }
                }
            }

            com.my.kizzy.feature_rpc_base.Constants.ACTION_STOP_SERVICE -> {
                explicitStop = true
                activeInstanceIds.clear()
                val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                    .split(',')
                    .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                    .filter { it != RpcManager.RpcType.MediaRpc }
                Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes.joinToString(",") { it.name }
                if (Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.MediaRpc.name) {
                    Prefs[Prefs.ACTIVE_RPC_TYPE] = remainingTypes.firstOrNull()?.name ?: ""
                }
                rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.MediaRpc))
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        Prefs[Prefs.MEDIA_RPC_ENABLED] = false
                    }
                }
                stopSelf()
                return START_NOT_STICKY
            }

            com.my.kizzy.feature_rpc_base.Constants.ACTION_RESTART_SERVICE -> {
                logger.d(MEDIA_RPC, "Restart requested, resetting the running service")
                restartInPlace()
            }

            com.my.kizzy.feature_rpc_base.Constants.ACTION_TOGGLE -> {
                isPaused = !isPaused
                stateRepository.setPaused(isPaused)
                logger.d(MEDIA_RPC, "Toggle pressed: paused=$isPaused")
                updateNotificationPauseButton()
                if (!isPaused) {
                    serviceScope.launch { updatePresence() }
                } else {
                    stopTimelineUpdates()
                    serviceScope.launch { kizzyRPC?.clearPresence() }
                    stateRepository.setConnected(false)
                }
            }

            null -> {
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        Prefs[Prefs.MEDIA_RPC_ENABLED] = true
                    }
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun restartInPlace() {
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Prefs[Prefs.MEDIA_RPC_ENABLED] = true
            }
        }

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
        rpcPacer.reset()

        isPaused = false
        stateRepository.setPaused(false)

        val sessions: List<MediaController> = try {
            mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java))
        } catch (_: SecurityException) {
            emptyList()
        }
        applySessionChange(sessions)

        serviceScope.launch {
            updatePresence(forceRefresh = true)
        }
    }

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        logger.d(MEDIA_RPC, "onTaskRemoved: user swiped app from Recents")
        WorkManagerHelper.scheduleRpcRecovery(this)
    }

    override fun onDestroy() {
        stopTimelineUpdates()
        stopAppDetectionCoroutine()
        if (::mediaSessionManager.isInitialized) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            } catch (e: Exception) {
                logger.e(MEDIA_RPC, "removeOnActiveSessionsChangedListener failed: ${e.message}")
            }
        }
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        serviceJob.cancel()
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
        val mediaStillEnabled = RpcManager.RpcType.MediaRpc in rpcManager.activeRpc.value
        if (!explicitStop && mediaStillEnabled) {
            WorkManagerHelper.scheduleRpcRecovery(this)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}