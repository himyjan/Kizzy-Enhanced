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
import com.my.kizzy.feature_rpc_base.BuildConfig
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.data.rpc.*
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.data.work.RpcRecoveryHiltWorker
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
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
import kotlin.time.Duration.Companion.milliseconds
import com.my.kizzy.feature_rpc_base.R as FeatureRpcR

import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LIBREFM_API_KEY
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LIBREFM_SECRET
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_API_KEY
import com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_SECRET
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFM
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFmApiClient
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFmException
import com.my.kizzy.feature_rpc_base.services.lastfm.LibreFM
import com.my.kizzy.feature_rpc_base.services.listenbrainz.ListenBrainz
import com.my.kizzy.feature_rpc_base.services.listenbrainz.ListenBrainzException
import kotlinx.serialization.json.Json

private const val TAG = "ManualRpc"

@AndroidEntryPoint
class ManualRpc : Service() {

    private var kizzyRPC: KizzyRPC? = null
    private var explicitStop = false

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

    private var activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            activeSessionsListener(sessions?.toList() ?: emptyList())
        }

    private var currentMediaController: MediaController? = null
    private var currentMediaTitle: String? = null
    private var currentMediaArtist: String? = null
    private var currentMediaCoverArt: Bitmap? = null
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0
    private var lastSentProgressSec: Int = -1

    private val timelineJob = MutableStateFlow<Job?>(null)
    private var appDetectionJob: Job? = null

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
    private val PRESENCE_UPDATE_MIN_INTERVAL_MS = 1500L

    private var lastNotificationUpdateTime: Long = 0L
    private val NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 1000L

    private var lastPlaybackStateCode: Int? = null
    private var lastMetadataSignature: String? = null

    private val TIMELINE_JUMP_TOLERANCE_MS = 3000L

    private val presenceUpdateMutex = Mutex()

    private val heartbeatJob = MutableStateFlow<Job?>(null)

    @Inject
    lateinit var notificationManager: NotificationManager

    private lateinit var mediaSessionManager: MediaSessionManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Manual RPC",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Manual Rich Presence notification channel"
        notificationManager.createNotificationChannel(channel)
    }

    private fun setupWakeLock() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "kizzy:ManualRpcWakeLock"
        )
    }

    private fun activeSessionsListener(sessions: List<MediaController>) {
        // Handle media session changes
    }

    private val useMediaRpc: Boolean
        get() = Prefs[Prefs.MEDIA_RPC_ENABLED, true]

    private val enabledExperimentalApps: List<String>
        get() = runCatching {
            Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
        }.getOrDefault(emptyList())

    private val useAppsRpc: Boolean
        get() = Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]

    private suspend fun fetchPlayingMediaWithRetry(): CommonRpc {
        return CommonRpc(
            name = "",
            type = null,
            details = "",
            state = "",
            appName = "",
            appPackage = "",
            playbackState = null,
            mediaUrl = null
        )
    }

    private fun startHeartbeat() {
        heartbeatJob.value = scope.launch {
            while (isActive) {
                delay(30_000)
            }
        }
    }

    private fun startMediaRefresh() {
        timelineJob.value = scope.launch {
            while (isActive) {
                delay(5_000)
                scope.launch { updatePresence() }
            }
        }
    }

    private fun stopTimelineUpdates() {
        timelineJob.value?.cancel()
        timelineJob.value = null
    }

    private suspend fun updateScrobblers(playingMedia: CommonRpc) {
        val songName = playingMedia.details?.takeIf { it.isNotBlank() } ?: playingMedia.name
        val artistName = playingMedia.state?.takeIf { it.isNotBlank() }
        if (songName.isBlank() || artistName.isNullOrBlank()) return

        val currentTrackIdentity = "$artistName - $songName"

        // Ensure session keys and API keys are initialized from preferences
        val lastFmApiKey = Prefs[Prefs.LASTFM_API_KEY, DEFAULT_LASTFM_API_KEY]
        val lastFmSecret = Prefs[Prefs.LASTFM_SECRET, DEFAULT_LASTFM_SECRET]
        LastFM.init(lastFmApiKey, lastFmSecret)
        LastFM.sessionKey = Prefs[Prefs.LASTFM_SESSION_KEY, ""]

        val libreFmApiKey = Prefs[Prefs.LIBREFM_API_KEY, DEFAULT_LIBREFM_API_KEY]
        val libreFmSecret = Prefs[Prefs.LIBREFM_SECRET, DEFAULT_LIBREFM_SECRET]
        LibreFM.init(libreFmApiKey, libreFmSecret)
        LibreFM.sessionKey = Prefs[Prefs.LIBREFM_SESSION_KEY, ""]

        ListenBrainz.setUserToken(Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""])

        val isLastFmEnabled = Prefs[Prefs.LASTFM_ENABLED, false] && LastFM.isAuthenticated
        val isLibreFmEnabled = Prefs[Prefs.LIBREFM_ENABLED, false] && LibreFM.isAuthenticated
        val isListenBrainzEnabled = Prefs[Prefs.LISTENBRAINZ_ENABLED, false] && ListenBrainz.isAuthenticated

        if (!isLastFmEnabled && !isLibreFmEnabled && !isListenBrainzEnabled) return

        val nowSec = System.currentTimeMillis() / 1000

        // 1. Now Playing update when track changes
        if (currentTrackIdentity != lastFmNowPlayingIdentity) {
            lastFmNowPlayingIdentity = currentTrackIdentity
            lastFmTrackStartTimestamp = nowSec
            lastFmScrobbledIdentity = null // Reset so new track can be scrobbled

            scope.launch(Dispatchers.IO) {
                if (isLastFmEnabled) {
                    try {
                        LastFM.updateNowPlaying(artist = artistName, track = songName)
                    } catch (e: Exception) {
                        logger.e(TAG, "Last.fm now playing failed: ${e.message}")
                    }
                }
                if (isLibreFmEnabled) {
                    try {
                        LibreFM.updateNowPlaying(artist = artistName, track = songName)
                    } catch (e: Exception) {
                        logger.e(TAG, "Libre.fm now playing failed: ${e.message}")
                    }
                }
                if (isListenBrainzEnabled) {
                    try {
                        ListenBrainz.updateNowPlaying(artist = artistName, track = songName, url = playingMedia.mediaUrl)
                    } catch (e: Exception) {
                        logger.e(TAG, "ListenBrainz now playing failed: ${e.message}")
                    }
                }
            }
        }

        // 2. Scrobble update after playing for >= 30 seconds
        val elapsedSec = nowSec - lastFmTrackStartTimestamp
        if (elapsedSec >= 30 && lastFmScrobbledIdentity != currentTrackIdentity) {
            lastFmScrobbledIdentity = currentTrackIdentity
            val scrobbleTimestamp = lastFmTrackStartTimestamp

            scope.launch(Dispatchers.IO) {
                if (isLastFmEnabled) {
                    try {
                        val success = LastFM.scrobble(artist = artistName, track = songName, timestamp = scrobbleTimestamp)
                        if (success) logger.i(TAG, "Scrobbled to Last.fm: $currentTrackIdentity")
                    } catch (e: Exception) {
                        logger.e(TAG, "Last.fm scrobble failed: ${e.message}")
                    }
                }
                if (isLibreFmEnabled) {
                    try {
                        val success = LibreFM.scrobble(artist = artistName, track = songName, timestamp = scrobbleTimestamp)
                        if (success) logger.i(TAG, "Scrobbled to Libre.fm: $currentTrackIdentity")
                    } catch (e: Exception) {
                        logger.e(TAG, "Libre.fm scrobble failed: ${e.message}")
                    }
                }
                if (isListenBrainzEnabled) {
                    try {
                        val success = ListenBrainz.scrobble(artist = artistName, track = songName, timestamp = scrobbleTimestamp, url = playingMedia.mediaUrl)
                        if (success) logger.i(TAG, "Scrobbled to ListenBrainz: $currentTrackIdentity")
                    } catch (e: Exception) {
                        logger.e(TAG, "ListenBrainz scrobble failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun updateNotificationPauseButton() {
        // Update notification pause button state
    }

    private suspend fun resolveActivityType(playingMedia: CommonRpc): Int? {
        return playingMedia.type
    }

    private fun commonRpcToRpcConfig(commonRpc: CommonRpc): RpcConfig {
        return RpcConfig(
            name = commonRpc.name,
            type = commonRpc.type?.toString() ?: "",
            details = commonRpc.details ?: "",
            state = commonRpc.state ?: "",
            detailsUrl = commonRpc.detailsUrl ?: "",
            stateUrl = commonRpc.stateUrl ?: "",
            largeImg = commonRpc.largeUrl ?: "",
            smallImg = commonRpc.smallUrl ?: "",
            status = commonRpc.state ?: "",
            timestampsStart = commonRpc.time?.start?.toString() ?: "",
            timestampsStop = commonRpc.time?.end?.toString() ?: "",
            url = commonRpc.mediaUrl ?: "",
            platform = commonRpc.packageName ?: "",
            paused = isPaused,
            pausedPositionMs = if (isPaused && currentMediaDurationSec > 0) currentMediaPositionMs else null,
            largeText = commonRpc.largeText ?: "",
            smallText = commonRpc.smallText ?: "",
            partyCurrentSize = "",
            partyMaxSize = "",
            button1 = "",
            button1link = "",
            button2 = "",
            button2link = ""
        )
    }

    private fun resolveDirectMediaUrl(): String? {
        return null
    }

    private val candidateUrl: String?
        get() = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
        createNotificationChannel()
        val basicNotification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dev_rpc)
            .setContentTitle("Manual RPC")
            .setContentText("Starting...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, basicNotification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, basicNotification)
        }

        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.ManualRpc.name
        setupWakeLock()

        // Restore saved Last.fm session key so scrobbling works after app restart
        LastFM.sessionKey = Prefs[Prefs.LASTFM_SESSION_KEY, ""]

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener, ComponentName(this, NotificationListener::class.java))

        val initialMediaSessions = try {
            mediaSessionManager.getActiveSessions(
                ComponentName(this, NotificationListener::class.java)
            )
        } catch (e: SecurityException) {
            logger.e(TAG, "No notification access, skipping media sessions: ${e.message}")
            emptyList()
        }
        var mediaActiveInitially = false
        if (useMediaRpc && initialMediaSessions.isNotEmpty()) {
            val firstActiveMediaController = initialMediaSessions.firstOrNull {
                enabledExperimentalApps.contains(it.packageName) ||
                    Prefs.isMediaAppEnabled(it.packageName)
            }
            if (firstActiveMediaController != null) {
                mediaActiveInitially = true
                activeSessionsListener(listOf(firstActiveMediaController))
            }
        }

        if (!mediaActiveInitially) {
            scope.launch {
                var foundMedia = false
                if (useMediaRpc) {
                    val directMedia = fetchPlayingMediaWithRetry()
                    if (directMedia.name.isNotEmpty()) {
                        logger.d(TAG, "Direct media detection found: ${directMedia.name} (${directMedia.appPackage})")
                        updatePresence()
                        foundMedia = true
                    }
                    if (!foundMedia && useAppsRpc) {
                        startAppDetectionCoroutine()
                    }
                }
            }
        }

        startHeartbeat()
        startMediaRefresh()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kizzyRPC = multiRpc.session("3")

        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            explicitStop = true
            Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.ManualRpc }
                .joinToString(",") { it.name }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.ManualRpc))
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        } else if (intent?.action == Constants.ACTION_RESTART_SERVICE) {
            stopSelf()
            startService(Intent(this, ManualRpc::class.java))
        } else {
            Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.ManualRpc.name
            val stopIntent = Intent(this, ManualRpc::class.java)
            stopIntent.action = Constants.ACTION_STOP_SERVICE
            val stopPendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val restartIntent = Intent(this, ManualRpc::class.java)
            restartIntent.action = Constants.ACTION_RESTART_SERVICE
            val restartPendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

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

            val notification = Notification.Builder(this, Constants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dev_rpc)
                .setContentTitle(getString(R.string.service_enabled))
                .setContentIntent(appPendingIntent)
                .addAction(
                    R.drawable.ic_dev_rpc,
                    getString(R.string.restart),
                    restartPendingIntent
                )
                .addAction(R.drawable.ic_dev_rpc, getString(R.string.exit), stopPendingIntent)
                .build()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }

            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsChangedListener, ComponentName(this, NotificationListener::class.java))

            val initialMediaSessions = try {
                mediaSessionManager.getActiveSessions(
                    ComponentName(this, NotificationListener::class.java)
                )
            } catch (e: SecurityException) {
                logger.e(TAG, "No notification access, skipping media sessions: ${e.message}")
                emptyList()
            }
            var mediaActiveInitially = false
            if (useMediaRpc && initialMediaSessions.isNotEmpty()) {
                val firstActiveMediaController = initialMediaSessions.firstOrNull {
                    enabledExperimentalApps.contains(it.packageName) ||
                        Prefs.isMediaAppEnabled(it.packageName)
                }
                if (firstActiveMediaController != null) {
                    mediaActiveInitially = true
                    activeSessionsListener(listOf(firstActiveMediaController))
                }
            }

            if (!mediaActiveInitially) {
                scope.launch {
                    var foundMedia = false
                    if (useMediaRpc) {
                        val directMedia = fetchPlayingMediaWithRetry()
                        if (directMedia.name.isNotEmpty()) {
                            logger.d(TAG, "Direct media detection found: ${directMedia.name} (${directMedia.appPackage})")
                            updatePresence()
                            foundMedia = true
                        }
                        if (!foundMedia && useAppsRpc) {
                            startAppDetectionCoroutine()
                        }
                    }
                }
            }
        }

        startHeartbeat()
        startMediaRefresh()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(intent: Intent) {
        super.onTaskRemoved(intent)
        if (!explicitStop) {
            logger.d(TAG, "onTaskRemoved: user swiped app from Recents")
            WorkManagerHelper.scheduleRpcRecovery(this)
        }
    }

    private fun startAppDetectionCoroutine() {
        if (appDetectionJob?.isActive == true) {
            logger.d(TAG, "App detection coroutine already running")
            return
        }
        logger.d(TAG, "Starting app detection coroutine")

        val startTimestamps = Timestamps(start = System.currentTimeMillis())

        appDetectionJob = scope.launch {
            var currentPackageName = ""
            var cycleCount = 0
            while (isActive) {
                cycleCount++
                try {
                    val currentApp = getCurrentlyRunningApp()

                    if (currentApp.name.isEmpty()) {
                        if (cycleCount % 6 == 1) {
                            logger.d(TAG, "App detection: no foreground app found")
                        }
                    } else {
                        logger.d(TAG, "App detection: found foreground app=${currentApp.packageName}")
                    }

                    if (currentApp.name.isNotEmpty() && currentApp.packageName != currentPackageName &&
                        currentApp.packageName !in AppDetectionService.EXCLUDED_APPS
                    ) {
                        val isGameApp = dynamicActivityDetectionEngine.identifyGame(currentApp.packageName) != null
                        val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(currentApp.packageName) != null
                        val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
                        val shouldShow = isGameApp || isAiApp || currentApp.packageName in enabledApps

                        if (shouldShow) {
                            currentPackageName = currentApp.packageName
                            val appInfo = currentApp.copy(
                                name = dynamicActivityDetectionEngine.identifyAiApp(currentApp.packageName)?.displayName
                                    ?: dynamicActivityDetectionEngine.identifyGame(currentApp.packageName)?.displayName
                                    ?: currentApp.name
                            )
                            updatePresence()
                        }
                    } else if (currentApp.name.isEmpty() && currentPackageName != "") {
                        currentPackageName = ""
                        scope.launch { kizzyRPC?.clearPresence() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Detection cycle failed: ${e.message}")
                }
                delay(2000.milliseconds)
            }
        }
    }

    private fun stopAppDetectionCoroutine() {
        appDetectionJob?.cancel()
        appDetectionJob = null
    }

    suspend fun updatePresence(forceRefresh: Boolean = false) {
        presenceUpdateMutex.withLock {
            updatePresenceLocked(forceRefresh)
        }
    }

    private suspend fun updatePresenceLocked(forceRefresh: Boolean = false) {
        if (BuildConfig.DEBUG) Trace.beginSection("ManualRpc.updatePresence")
        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
        val showCoverArt = Prefs[Prefs.MEDIA_RPC_SHOW_COVER_ART, true]
        val playingMedia = getCurrentPlayingMedia()

        currentMediaTitle = playingMedia.name.takeIf { it.isNotBlank() }
        currentMediaArtist = playingMedia.state?.takeIf { it.isNotBlank() }

        updateScrobblers(playingMedia)

        val showCoverArtGlobal = Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false
        currentMediaCoverArt = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.largeImage?.let {
                (it as? com.my.kizzy.data.rpc.RpcImage.BitmapImage)?.bitmap
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
            logger.d(TAG, "Session switched: $currentPackageName -> $playingPkg")
            currentPackageName = playingPkg
            isPaused = false
            lastKnownTrackTitle = null
            lastKnownTrackArtist = null
            stateRepository.setPaused(false)
        }

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
                val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
                val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps
                if (shouldShow && runningApp.packageName != currentPackageName) {
                    currentPackageName = runningApp.packageName
                    val appInfo = runningApp.copy(
                        name = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName)?.displayName
                            ?: dynamicActivityDetectionEngine.identifyGame(runningApp.packageName)?.displayName
                            ?: runningApp.name
                    )
                    updatePresence()
                    return
                }
            } else if (runningApp.name.isEmpty() && currentPackageName != "") {
                currentPackageName = ""
                scope.launch { kizzyRPC?.clearPresence() }
            }
        }

        if (playingPkg == null) {
            if (Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]) {
                val runningApp = getCurrentlyRunningApp()
                if (runningApp.name.isNotEmpty() && runningApp.packageName !in AppDetectionService.EXCLUDED_APPS) {
                    val isGameApp = dynamicActivityDetectionEngine.identifyGame(runningApp.packageName) != null
                    val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName) != null
                    val enabledApps = Json.decodeFromString<List<String>>(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
                    val shouldShow = isGameApp || isAiApp || runningApp.packageName in enabledApps
                    if (shouldShow) {
                        val appInfo = runningApp.copy(
                            name = dynamicActivityDetectionEngine.identifyAiApp(runningApp.packageName)?.displayName
                                ?: dynamicActivityDetectionEngine.identifyGame(runningApp.packageName)?.displayName
                                ?: runningApp.name
                        )
                        updatePresence()
                        Trace.endSection()
                        return
                    }
                }
            }
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
                Trace.endSection()
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

        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime >= NOTIFICATION_UPDATE_MIN_INTERVAL_MS) {
            lastNotificationUpdateTime = now
            updateNotificationPauseButton()
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

        val packageChanged = playingPkg != null && playingPkg != lastSentPackageName

        val currentStart = playingMedia.time?.start
        val timelineJumped = currentStart != null && lastSentStartTimestamps != null &&
            kotlin.math.abs(currentStart - lastSentStartTimestamps!!) > TIMELINE_JUMP_TOLERANCE_MS

        val contentChanged = currentName != lastSentName ||
            currentDetails != lastSentDetails ||
            currentState != lastSentState ||
            currentLargeImageUrl != lastSentLargeImageUrl

        if (!forceRefresh && !packageChanged && !contentChanged && !timelineJumped) {
            logger.d(TAG, "Presence unchanged, skipping redundant update")
            if (BuildConfig.DEBUG) Trace.endSection()
            return
        }

        stateRepository.setTimestamps(playingMedia.time?.start, playingMedia.time?.end)

        val largeImageUrl = if (showCoverArt) {
            playingMedia.largeImage?.resolveImage(kizzyRepository)
        } else {
            null
        }
        val smallImageUrl = playingMedia.smallImage?.resolveImage(kizzyRepository)

        sendPresenceUpdate(playingMedia, enableTimestamps, showCoverArt)

        lastSentName = currentName
        lastSentDetails = currentDetails
        lastSentState = currentState
        lastSentStartTimestamps = currentStart
        lastSentStopTimestamps = playingMedia.time?.end
        lastSentLargeImageUrl = currentLargeImageUrl
        lastSentPackageName = playingPkg
        lastSentProgressSec = currentMediaProgressSec
        lastPresenceUpdateTime = now
        if (BuildConfig.DEBUG) Trace.endSection()
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

        val showCoverArtGlobal = Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false

        val largeImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.largeImage ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }?.let { com.my.kizzy.data.rpc.RpcImage.ExternalImage(it) }
        } else {
            null
        }

        val smallImage = if (showCoverArtGlobal && showCoverArt) {
            playingMedia.smallImage ?: Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }?.let { com.my.kizzy.data.rpc.RpcImage.ExternalImage(it) }
        } else {
            null
        }

        val showSongAsTitle = Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true]
        val showAppNameInDetails = Prefs[Prefs.MEDIA_RPC_SHOW_APP_NAME_IN_DETAILS, true]
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
            time = if (enableTimestamps && !isPaused) com.my.kizzy.data.rpc.Timestamps(
                start = playingMedia.time?.start,
                end = playingMedia.time?.end
            ) else null,
            packageName = playingMedia.appPackage,
            playbackState = playingMedia.playbackState,
            mediaUrl = playingMedia.mediaUrl
        )

        rpcManager.setCurrentPresenceConfig(commonRpcToRpcConfig(commonRpc))

        kizzyRPC?.clearButtons()
        kizzyRPC?.updateRPC(commonRpc, enableTimestamps, forceRefresh = true, priority = 1)
        updateNotificationPauseButton()
    }
}