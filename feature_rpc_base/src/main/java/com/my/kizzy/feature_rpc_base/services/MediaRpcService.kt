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
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.graphics.Bitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.annotation.RequiresApi
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.work.RpcRecoveryHiltWorker
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.R as FeatureRpcR
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_LINK
import com.my.kizzy.preference.Prefs.MEDIA_RPC_MEDIA_BUTTON
import com.my.kizzy.preference.Prefs.TOKEN
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject

@AndroidEntryPoint
class MediaRpcService : Service() {

    @Inject
    lateinit var kizzyRPC: KizzyRPC

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var getCurrentPlayingMedia: GetCurrentPlayingMedia

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var stateRepository: KizzyStateRepository

    @Inject
    lateinit var metadataResolver: MetadataResolver

    @Inject
    lateinit var kizzyRepository: com.my.kizzy.domain.repository.KizzyRepository

    private var wakeLock: WakeLock? = null

    private var isPaused = false
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
    private val PRESENCE_UPDATE_MIN_INTERVAL_MS = 5000L

    private val presenceUpdateMutex = Mutex()

    private val timelineJob = MutableStateFlow<Job?>(null)
    private val heartbeatJob = MutableStateFlow<Job?>(null)

    @Inject
    lateinit var notificationManager: NotificationManager

    private lateinit var mediaSessionManager: MediaSessionManager

    private var currentMediaController: MediaController? = null
    private var currentMediaTitle: String? = null
    private var currentMediaArtist: String? = null
    private var currentMediaCoverArt: Bitmap? = null
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0

    private val timelineTickerJob = MutableStateFlow<Job?>(null)


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
        createNotificationChannel()
        val basicNotification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle("Rich Presence Active")
            .setContentText("Starting...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID, basicNotification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Constants.NOTIFICATION_ID, basicNotification)
        }

        val token = Prefs[TOKEN, ""]
        if (token.isEmpty()) stopSelf()
        setupWakeLock()

        val stopIntent = Intent(this, MediaRpcService::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(this, MediaRpcService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        val restartPendingIntent = PendingIntent.getService(
            this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
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

        notificationManager.notify(Constants.NOTIFICATION_ID, notification)

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.addOnActiveSessionsChangedListener(::activeSessionsListener, ComponentName(this, NotificationListener::class.java))

        val sessions: List<android.media.session.MediaController> = try {
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
                delay(1000)
                if (!isPaused && kizzyRPC.isRpcRunning()) {
                    val playingMedia = getCurrentPlayingMedia()
                    if (playingMedia.name.isNotBlank() && playingMedia.time?.start != null) {
                        stateRepository.setTimestamps(playingMedia.time?.start, playingMedia.time?.end)
                        val largeImageUrl = playingMedia.largeImage?.resolveImage(kizzyRepository)
                        val smallImageUrl = playingMedia.smallImage?.resolveImage(kizzyRepository)
                        stateRepository.setImageUrls(largeImageUrl, smallImageUrl)

                        // Update presence - respects interval limits (no forceRefresh)
                        updatePresence()
                    }
                    // Update notification progress bar every second while playing
                    updateNotificationPauseButton()
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
                delay(3000)
                scope.launch { updatePresence(forceRefresh = true) }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob.value?.cancel()
        heartbeatJob.value = null
    }

    suspend private fun updatePresence(forceRefresh: Boolean = false) {
        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
        val playingMedia = getCurrentPlayingMedia()

        currentMediaTitle = playingMedia.name.takeIf { it.isNotBlank() }
        currentMediaArtist = playingMedia.state?.takeIf { it.isNotBlank() }
        currentMediaCoverArt = playingMedia.largeImage?.let {
            if (it is com.my.kizzy.data.rpc.RpcImage.BitmapImage) it.bitmap else null
        }

        // Track playback progress for notification progress bar
        playingMedia.time?.let { time ->
            val start = time.start ?: 0L
            val end = time.end ?: 0L
            currentMediaDurationSec = ((end - start) / 1000).toInt().coerceAtLeast(0)
            // Get current position from media controller if available, otherwise estimate from timestamps
            currentMediaPositionMs = currentMediaController?.playbackState?.position ?: (System.currentTimeMillis() - start)
            currentMediaProgressSec = (currentMediaPositionMs / 1000).toInt().coerceIn(0, currentMediaDurationSec)
        } ?: run {
            currentMediaDurationSec = 0
            currentMediaProgressSec = 0
            currentMediaPositionMs = 0L
        }

        val playingPkg = playingMedia.packageName.takeIf { it.isNotBlank() }

        if (playingPkg != null && playingPkg != currentPackageName) {
            logger.d("MediaRPC", "Session switched: $currentPackageName -> $playingPkg")
            currentPackageName = playingPkg
            isPaused = false
            lastKnownTrackTitle = null
            lastKnownTrackArtist = null
            stateRepository.setPaused(false)
        } else if (playingPkg == null) {
            if (currentPackageName != null) {
                currentPackageName = null
                isPaused = false
                lastKnownTrackTitle = null
                lastKnownTrackArtist = null
                currentMediaTitle = null
                currentMediaArtist = null
                currentMediaCoverArt = null
                stateRepository.setConnected(false)
                stateRepository.clearTrackInfo()
                stopTimelineUpdates()
                scope.launch { kizzyRPC.clearPresence() }
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

        val currentName = playingMedia.name.takeIf { it.isNotBlank() }
        val currentDetails = playingMedia.details
        val currentState = playingMedia.state

        // Update notification with current playback progress before skip check
        updateNotificationPauseButton()

        stateRepository.setTimestamps(playingMedia.time?.start, playingMedia.time?.end)
        val largeImageUrl = playingMedia.largeImage?.resolveImage(kizzyRepository)
        val smallImageUrl = playingMedia.smallImage?.resolveImage(kizzyRepository)
        stateRepository.setImageUrls(largeImageUrl, smallImageUrl)

        presenceUpdateMutex.withLock {
            val currentLargeImageUrl = playingMedia.largeImage?.toString()
            val now = System.currentTimeMillis()
            val intervalElapsed = now - lastPresenceUpdateTime >= PRESENCE_UPDATE_MIN_INTERVAL_MS

            val packageChanged = playingPkg != null && playingPkg != lastSentPackageName

            val shouldSkip = !forceRefresh && !packageChanged &&
                (currentName == lastSentName &&
                currentDetails == lastSentDetails &&
                currentState == lastSentState &&
                currentLargeImageUrl == lastSentLargeImageUrl) ||
                !intervalElapsed

            if (shouldSkip) {
                logger.d("MediaRPC", "Presence unchanged or interval not elapsed, skipping redundant update")
                return
            }

            lastSentName = currentName
            lastSentDetails = currentDetails
            lastSentState = currentState
            lastSentStartTimestamps = playingMedia.time?.start
            lastSentStopTimestamps = playingMedia.time?.end
            lastSentLargeImageUrl = currentLargeImageUrl
            lastSentPackageName = playingPkg
            lastPresenceUpdateTime = now
        }

        sendPresenceUpdate(playingMedia, enableTimestamps)
    }

    private suspend fun sendPresenceUpdate(playingMedia: CommonRpc, enableTimestamps: Boolean) {
        if (playingMedia.name.isBlank()) {
            stateRepository.setConnected(false)
            stateRepository.clearTrackInfo()
            stopTimelineUpdates()
            kizzyRPC.clearPresence()
            return
        }

        stateRepository.setConnected(true)
        stateRepository.setPaused(isPaused)

        val commonRpc = CommonRpc(
            name = playingMedia.name,
            type = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, 0],
            details = playingMedia.details,
            state = playingMedia.state,
            largeImage = playingMedia.largeImage,
            smallImage = playingMedia.smallImage,
            largeText = playingMedia.largeText,
            smallText = playingMedia.smallText,
            time = if (enableTimestamps && !isPaused) Timestamps(
                start = playingMedia.time?.start,
                end = playingMedia.time?.end
            ) else null,
            packageName = playingMedia.packageName,
            playbackState = playingMedia.playbackState
        )

        if (Prefs[MEDIA_RPC_MEDIA_BUTTON, false] || Prefs[MEDIA_RPC_ARTIST_LINK, false]) {
            kizzyRPC.clearButtons()
            if (Prefs[MEDIA_RPC_MEDIA_BUTTON, false]) {
                playingMedia.mediaUrl?.let { url ->
                    val label = when {
                        playingMedia.packageName.contains("youtube") || playingMedia.packageName.contains("youtubemusic") -> "Watch on YouTube"
                        playingMedia.packageName == "com.spotify.music" -> "Listen on Spotify"
                        playingMedia.packageName.contains("apple") || playingMedia.packageName.contains("itunes") -> "Open in Apple Music"
                        playingMedia.packageName.contains("deezer") -> "Listen on Deezer"
                        playingMedia.packageName.contains("soundcloud") -> "Open in SoundCloud"
                        playingMedia.packageName.contains("streetvoice") -> "Listen on StreetVoice"
                        playingMedia.packageName.contains("ymusicapp") -> "Play on YMusic"
                        else -> "Open Media Link"
                    }
                    kizzyRPC.setButton1(label, url)
                }
            }
            if (Prefs[MEDIA_RPC_ARTIST_LINK, false]) {
                currentMediaController?.metadata?.let { metadata ->
                    metadataResolver.getArtistUrl(metadata, playingMedia.packageName)?.let { url ->
                        val label = when {
                            playingMedia.packageName.contains("youtube") || playingMedia.packageName.contains("youtubemusic") -> "Artist on YouTube"
                            playingMedia.packageName == "com.spotify.music" -> "Artist on Spotify"
                            playingMedia.packageName.contains("apple") || playingMedia.packageName.contains("itunes") -> "Artist on Apple Music"
                            playingMedia.packageName.contains("deezer") -> "Artist on Deezer"
                            playingMedia.packageName.contains("soundcloud") -> "Artist on SoundCloud"
                            playingMedia.packageName.contains("streetvoice") -> "Artist on StreetVoice"
                            playingMedia.packageName.contains("ymusicapp") -> "Artist on YMusic"
                            else -> "Artist Channel"
                        }
                        kizzyRPC.setButton2(label, url)
                    }
                }
            }
        }

        kizzyRPC.updateRPC(commonRpc, enableTimestamps)
        updateNotificationPauseButton()
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            val newPaused = state?.state != PlaybackState.STATE_PLAYING
            if (isPaused != newPaused) {
                isPaused = newPaused
                stateRepository.setPaused(isPaused)
                logger.d("MediaRPC", "Playback state changed: paused=$isPaused")
                updateNotificationPauseButton()
            }

            scope.launch {
                delay(200)
                updatePresence(forceRefresh = true)
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

            currentMediaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            currentMediaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

            scope.launch {
                delay(1000)
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
        val progressText = if (currentMediaDurationSec > 0) {
            val currentStr = formatDuration(currentMediaPositionMs)
            val totalStr = formatDuration(currentMediaDurationSec.toLong() * 1000)
            "$currentStr / $totalStr"
        } else {
            "No time info"
        }
        val content = if (isPaused) "Tap Resume to continue syncing" else currentMediaArtist ?: "Broadcasting media status"
        val notification = buildNotification(title, content, currentMediaCoverArt, currentMediaProgressSec, currentMediaDurationSec, currentMediaProgressSec, currentMediaDurationSec)
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
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

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun buildNotification(title: String, content: String, coverArt: Bitmap?, progressSec: Int, durationSec: Int, currentSec: Int = 0, totalSec: Int = 0): Notification {
        val appIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
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
            // Native chronometer for elapsed time display
            .setWhen(currentMediaPositionMs)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.restart),
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, MediaRpcService::class.java).apply { action = Constants.ACTION_RESTART_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_media_rpc,
                    getString(R.string.exit),
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, MediaRpcService::class.java).apply { action = Constants.ACTION_STOP_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )

        coverArt?.let { builder.setLargeIcon(it) }

        // Progress bar support
        // Android 16+ (API 36+): Use Notification.ProgressStyle for efficient live updates
        // Android 8-15 (API 26-35): Use setProgress()
        // API < 26: No progress bar support
        if (currentMediaDurationSec > 0) {
            val progressStyle = NotificationCompat.ProgressStyle()
                // 1. Defines the total timeline length (replaces setProgressMax)
                .setProgressSegments(
                    listOf(NotificationCompat.ProgressStyle.Segment(currentMediaDurationSec))
                )
                // 2. Moves the tracker along the timeline in seconds
                .setProgress(currentMediaProgressSec.coerceIn(0, currentMediaDurationSec))
                // 3. Draws a moving dot/icon on the timeline bar
                .setProgressTrackerIcon(
                    IconCompat.createWithResource(this, FeatureRpcR.drawable.ic_rpc_tracker_dot)
                )
                // 4. Colors the elapsed portion of the timeline differently from the remaining portion
                .setStyledByProgress(true)

            builder.setStyle(progressStyle)
        }

        return builder.build()
    }

    private fun activeSessionsListener(mediaSessions: List<MediaController>?, isEvent: Boolean = true) {
        logger.d("MediaRPC", "Active sessions changed")

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

            logger.d("MediaRPC", "Tracking session: ${selectedSession?.packageName} (${selectedSession?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "no title"})")

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
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            it.action?.let { ac ->
                if (ac == Constants.ACTION_STOP_SERVICE) {
                    stopSelf()
                } else if (ac == Constants.ACTION_RESTART_SERVICE) {
                    stopSelf()
                    startService(Intent(this, MediaRpcService::class.java))
                } else if (ac == Constants.ACTION_TOGGLE) {
                    isPaused = !isPaused
                    stateRepository.setPaused(isPaused)
                    logger.d("MediaRPC", "Toggle pressed: paused=$isPaused")
                    updateNotificationPauseButton()
                    if (!isPaused) {
                        scope.launch { updatePresence() }
                    } else {
                        stopTimelineUpdates()
                        scope.launch { kizzyRPC.clearPresence() }
                        stateRepository.setConnected(false)
                    }
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(intent: Intent?) {
        logger.d("MediaRPC", "onTaskRemoved: user swiped app from Recents")

        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (Prefs[Prefs.USE_RPC_BUTTONS, false] || Prefs[MEDIA_RPC_MEDIA_BUTTON, false] || Prefs[MEDIA_RPC_ARTIST_LINK, false]) {
            logger.d("MediaRPC", "Scheduling RPC recovery work after task removal")
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
        mediaSessionManager.removeOnActiveSessionsChangedListener(::activeSessionsListener)
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        scope.cancel()
        kizzyRPC.closeRPC()
        stateRepository.setConnected(false)
        stateRepository.setPaused(false)
        stateRepository.clearTrackInfo()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
