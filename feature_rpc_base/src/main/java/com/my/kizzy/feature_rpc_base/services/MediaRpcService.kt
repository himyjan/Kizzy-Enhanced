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
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.*
import com.my.kizzy.data.rpc.MultiRpcManager
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
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicLong

private val rpcButtonsJson = Json { ignoreUnknownKeys = true }

@AndroidEntryPoint
class MediaRpcService : Service() {

    private val TAG = "MediaRpcService"

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

    private val activeScrobbleTrack = AtomicLong(0L)
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

    private val useMediaRpc: Boolean
        get() = Prefs[Prefs.MEDIA_RPC_ENABLED, true]

    private val enabledExperimentalApps: List<String>
        get() = Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, emptyList()]

    private val useAppsRpc: Boolean
        get() = Prefs[Prefs.MEDIA_RPC_USE_APPS_RPC, false]

    private fun setupWakeLock() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "kizzy:MediaRpcWakeLock"
        )
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
    private val PRESENCE_MIN_INTERVAL_MS = 1500L

    private var lastNotificationUpdateTime: Long = 0L
    private val NOTIFICATION_UPDATE_MIN_INTERVAL_MS = 1000L

    private var lastPlaybackStateCode: Int? = null
    private var lastMetadataSignature: String? = null

    private val TIMELINE_JUMP_TOLERANCE_MS = 3000L

    private val presenceUpdateMutex = Mutex()

    private val timelineJob = MutableStateFlow<Job?>(null)
    private val heartbeatJob = MutableStateFlow<Job?>(null)
    private var appDetectionJob: Job? = null

    @Inject
    lateinit var notificationManager: NotificationManager

    private lateinit var mediaSessionManager: MediaSessionManager

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Media RPC",
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
            .setSmallIcon(R.drawable.ic_dev_rpc)
            .setContentTitle("Media RPC")
            .setContentText("Starting...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, basicNotification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, basicNotification)
        }

        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.MediaRpc.name
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
        kizzyRPC = multiRpc.session("1")

        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            explicitStop = true
            Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.MediaRpc }
                .joinToString(",") { it.name }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.MediaRpc))
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        } else if (intent?.action == Constants.ACTION_RESTART_SERVICE) {
            stopSelf()
            startService(Intent(this, MediaRpcService::class.java))
        } else {
            Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.MediaRpc.name
            val stopIntent = Intent(this, MediaRpcService::class.java)
            stopIntent.action = Constants.ACTION_STOP_SERVICE
            val stopPendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val restartIntent = Intent(this, MediaRpcService::class.java)
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
                startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID_MEDIA_RPC, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
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
            val enableTimestamps = Prefs[Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS, true]
            val playingMedia = getCurrentPlayingMedia()

            val rpcName = playingMedia.name.takeIf { it.isNotBlank() }
            if (rpcName.isNullOrBlank()) {
                stateRepository.setConnected(false)
                stateRepository.clearTrackInfo()
                kizzyRPC?.clearPresence()
                return
            }

            stateRepository.setConnected(true)
            stateRepository.setPaused(isPaused)

            val showCoverArtGlobal = Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false
            val showCoverArt = Prefs[Prefs.MEDIA_RPC_SHOW_COVER_ART, true]

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

            val commonRpc = CommonRpc(
                name = rpcName,
                type = playingMedia.type,
                details = playingMedia.details,
                state = playingMedia.state,
                detailsUrl = playingMedia.detailsUrl,
                stateUrl = playingMedia.stateUrl,
                largeImage = largeImage,
                smallImage = smallImage,
                largeUrl = playingMedia.largeUrl,
                smallUrl = playingMedia.smallUrl,
                largeText = playingMedia.largeText,
                smallText = playingMedia.smallText,
                time = if (enableTimestamps && !isPaused) Timestamps(
                    start = playingMedia.time?.start,
                    end = playingMedia.time?.end
                ) else null,
                packageName = playingMedia.appPackage,
                platform = playingMedia.platform,
                mediaUrl = playingMedia.mediaUrl,
                playbackState = playingMedia.playbackState
            )

            rpcManager.setCurrentPresenceConfig(com.my.kizzy.domain.model.rpc.RpcConfig(
                name = commonRpc.name,
                type = commonRpc.type?.toString() ?: "",
                details = commonRpc.details.orEmpty(),
                state = commonRpc.state.orEmpty(),
                detailsUrl = commonRpc.detailsUrl.orEmpty(),
                stateUrl = commonRpc.stateUrl.orEmpty(),
                largeImg = commonRpc.largeUrl.orEmpty(),
                smallImg = commonRpc.smallUrl.orEmpty(),
                timestampsStart = commonRpc.time?.start?.toString() ?: "",
                timestampsStop = commonRpc.time?.end?.toString() ?: "",
                status = commonRpc.state.orEmpty(),
                url = commonRpc.mediaUrl.orEmpty(),
                platform = commonRpc.packageName,
                largeText = commonRpc.largeText.orEmpty(),
                smallText = commonRpc.smallText.orEmpty(),
            ))

            kizzyRPC?.clearButtons()
            kizzyRPC?.updateRPC(commonRpc, enableTimestamps, forceRefresh = forceRefresh, priority = 1)

            // Scrobble to Last.fm independently of Discord RPC presence
            // Read the latest token from prefs each time to ensure we use the
            // most recent auth state even if the service was started before login
            LastFM.sessionKey = Prefs[Prefs.LASTFM_SESSION_KEY, ""]
            if (lastFmAuth.client.isAuthenticated) {
                val artist = playingMedia.state ?: playingMedia.name ?: ""
                val track = playingMedia.details ?: playingMedia.name ?: ""
                val timestamp = System.currentTimeMillis() / 1000
                val url = playingMedia.mediaUrl ?: playingMedia.detailsUrl

                lastFmAuth.client.scrobble(
                    artist = artist,
                    track = track,
                    timestamp = timestamp,
                    url = url
                )

                lastFmAuth.client.updateNowPlaying(
                    artist = artist,
                    track = track,
                    url = url
                )
            }
        }
    }

    private fun normalizedPlaybackState(state: Int?): Int = when (state) {
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> PlaybackState.STATE_PLAYING
        else -> state ?: 0
    }

    private fun imageFingerprint(image: RpcImage?): Int = when (image) {
        null -> 0
        is RpcImage.DiscordImage -> image.image.hashCode()
        is RpcImage.ExternalImage -> image.image.hashCode()
        is RpcImage.ApplicationIcon -> image.packageName.hashCode()
        is RpcImage.BitmapImage -> image.title.hashCode()
    }

    private fun activeSessionsListener(sessions: List<MediaController>) {
        // Handle media session changes
    }

    private fun fetchPlayingMediaWithRetry(): CommonRpc {
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
}
