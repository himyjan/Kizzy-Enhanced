package com.my.kizzy.feature_rpc_base.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
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
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_MEDIA_BUTTON
import com.my.kizzy.preference.Prefs.TOKEN
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

    private var wakeLock: WakeLock? = null

    // Playback state
    private var isPaused = false
    private var currentPackageName: String? = null
    private var lastKnownTrackTitle: String? = null
    private var lastKnownTrackArtist: String? = null

    // Jobs
    private val timelineJob = MutableStateFlow<Job?>(null)
    private val heartbeatJob = MutableStateFlow<Job?>(null)

    @Inject
    lateinit var notificationManager: NotificationManager

    private lateinit var mediaSessionManager: MediaSessionManager

    private var currentMediaController: MediaController? = null

    private val notificationBuilder: NotificationCompat.Builder by lazy {
        NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle("Rich Presence Active")
            .setContentText("Broadcasting media status")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        val token = Prefs[TOKEN, ""]
        if (token.isEmpty()) stopSelf()
        setupWakeLock()
        createNotificationChannel()

        val intent = Intent(this, MediaRpcService::class.java)
        intent.action = Constants.ACTION_STOP_SERVICE
        val pendingIntent = PendingIntent.getService(
            this,
            0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(this, MediaRpcService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        val restartPendingIntent = PendingIntent.getService(
            this,
            0, restartIntent, PendingIntent.FLAG_IMMUTABLE
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
                    pendingIntent
                ).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSessionManager.addOnActiveSessionsChangedListener(::activeSessionsListener, ComponentName(this, NotificationListener::class.java))

        activeSessionsListener(mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java)), false)

        // Heartbeat: periodically re-check presence to catch missed "next song" events
        startHeartbeat()
    }

    /**
     * Continuously updates the timeline (elapsed time) every second while media is playing.
     */
    private fun startTimelineUpdates() {
        stopTimelineUpdates()
        timelineJob.value = scope.launch {
            while (isActive) {
                delay(1000)
                if (!isPaused && kizzyRPC.isRpcRunning()) {
                    val playingMedia = getCurrentPlayingMedia()
                    if (playingMedia.name.isNotBlank()) {
                        kizzyRPC.updateRPC(playingMedia, Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, false])
                    }
                }
            }
        }
    }

    private fun stopTimelineUpdates() {
        timelineJob.value?.cancel()
        timelineJob.value = null
    }

    /**
     * Heartbeat: re-evaluates presence every 3 seconds to catch "next song" changes
     * that media session callbacks might miss (especially when switching between apps).
     */
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

    /**
     * Updates presence with the current media state.
     * @param forceRefresh if true, skips caching logic and always re-fetches media data
     */
    suspend private fun updatePresence(forceRefresh: Boolean = false) {
        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, false]
        val playingMedia = getCurrentPlayingMedia()

        // Detect session switch (new app/package) and reset state
        val currentPkg = currentMediaController?.packageName.takeIf { it.isNotBlank() }
        if (currentPkg != null && currentPkg != currentPackageName) {
            logger.d("MediaRPC", "Session switched: $currentPackageName -> $currentPkg")
            currentPackageName = currentPkg
            isPaused = false // Reset pause state for new session
            lastKnownTrackTitle = null
            lastKnownTrackArtist = null
            stateRepository.setPaused(false)
        } else if (currentPkg == null) {
            // No active media - clear everything
            if (currentPackageName != null) {
                currentPackageName = null
                isPaused = false
                lastKnownTrackTitle = null
                lastKnownTrackArtist = null
                stateRepository.setConnected(false)
                stateRepository.clearTrackInfo()
                stopTimelineUpdates()
                kizzyRPC.closeRPC()
                return
            }
        }

        // Update state repository so UI reflects current track
        stateRepository.setTrackInfo(
            title = playingMedia.name.takeIf { it.isNotBlank() },
            artist = playingMedia.state,
            album = null
        )
        lastKnownTrackTitle = playingMedia.name.takeIf { it.isNotBlank() }
        lastKnownTrackArtist = playingMedia.state

        // Update notification text
        notificationBuilder
            .setContentTitle(playingMedia.name.ifEmpty { getString(R.string.app_name_full) })
            .setContentText(
                (playingMedia.state ?: "").ifEmpty { getString(R.string.idling_notification) }
            )
        notificationManager.notify(
            Constants.NOTIFICATION_ID,
            notificationBuilder.build()
        )

        val rpcButtonsString = Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]
        val rpcButtons = Json.decodeFromString<RpcButtons>(rpcButtonsString)
        when (kizzyRPC.isRpcRunning()) {
            true -> {
                if (playingMedia.name.isBlank()) {
                    logger.d("MediaRPC", "Updating RPC with empty data, stopping RPC")
                    kizzyRPC.closeRPC()
                    stateRepository.setConnected(false)
                    stateRepository.clearTrackInfo()
                    stopTimelineUpdates()
                } else {
                    stateRepository.setConnected(true)
                    stateRepository.setPaused(isPaused)
                    // Add media button if enabled and media URL is available
                    if (Prefs[MEDIA_RPC_MEDIA_BUTTON, false]) {
                        playingMedia.mediaUrl?.let { url ->
                            val label = when {
                                playingMedia.packageName.contains("youtube") || playingMedia.packageName.contains("youtubemusic") -> "Watch on YouTube"
                                playingMedia.packageName == "com.spotify.music" -> "Listen on Spotify"
                                playingMedia.packageName.contains("apple") || playingMedia.packageName.contains("itunes") -> "Open in Apple Music"
                                playingMedia.packageName.contains("deezer") -> "Listen on Deezer"
                                playingMedia.packageName.contains("soundcloud") -> "Open in SoundCloud"
                                else -> "Open Media Link"
                            }
                            kizzyRPC.clearButtons()
                            kizzyRPC.setButton1(label, url)
                        }
                    }
                    kizzyRPC.updateRPC(playingMedia, enableTimestamps)
                }
            }
            false -> {
                if (playingMedia.name.isBlank()) {
                    logger.d("MediaRPC", "Skipping RPC update with empty data")
                    stateRepository.setConnected(false)
                    stateRepository.clearTrackInfo()
                    stopTimelineUpdates()
                    return
                }
                stateRepository.setConnected(true)
                stateRepository.setPaused(isPaused)
                // Add media button if enabled and media URL is available
                if (Prefs[MEDIA_RPC_MEDIA_BUTTON, false]) {
                    playingMedia.mediaUrl?.let { url ->
                        val label = when {
                            playingMedia.packageName.contains("youtube") || playingMedia.packageName.contains("youtubemusic") -> "Watch on YouTube"
                            playingMedia.packageName == "com.spotify.music" -> "Listen on Spotify"
                            playingMedia.packageName.contains("apple") || playingMedia.packageName.contains("itunes") -> "Open in Apple Music"
                            playingMedia.packageName.contains("deezer") -> "Listen on Deezer"
                            playingMedia.packageName.contains("soundcloud") -> "Open in SoundCloud"
                            else -> "Open Media Link"
                        }
                        kizzyRPC.clearButtons()
                        kizzyRPC.setButton1(label, url)
                    }
                }
                kizzyRPC.apply {
                    setName(playingMedia.name)
                    setType(Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, 0])
                    setDetails(playingMedia.state)
                    setState(playingMedia.state)
                    setStartTimestamps(if (enableTimestamps) playingMedia.time?.start else null)
                    setStopTimestamps(if (enableTimestamps) playingMedia.time?.end else null)
                    setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
                    setLargeImage(playingMedia.largeImage, playingMedia.largeText)
                    setSmallImage(playingMedia.smallImage, playingMedia.smallText)
                    if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
                        with(rpcButtons) {
                            val btn1 = button1.takeIf { it.isNotEmpty() }
                            val btn2 = button2.takeIf { it.isNotEmpty() }
                            if (btn1 != null) {
                                val url1 = button1Url.takeIf { it.isNotEmpty() }
                                setButton1(btn1, url1.orEmpty())
                            }
                            if (btn2 != null) {
                                val url2 = button2Url.takeIf { it.isNotEmpty() }
                                setButton2(btn2, url2.orEmpty())
                            }
                        }
                    }
                    build()
                }
            }
        }

        // Manage timeline updates
        if (playingMedia.name.isNotBlank() && !isPaused) {
            startTimelineUpdates()
        } else {
            stopTimelineUpdates()
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            val newPaused = state != PlaybackState.STATE_PLAYING
            if (isPaused != newPaused) {
                isPaused = newPaused
                stateRepository.setPaused(isPaused)
                logger.d("MediaRPC", "Playback state changed: paused=$isPaused")
                updateNotificationPauseButton()
            }

            // Immediate presence update on state change
            scope.launch {
                delay(500)
                updatePresence()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

            // Immediate presence update on metadata change (next song!)
            scope.launch {
                delay(800)
                updatePresence()
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()

            scope.launch {
                updatePresence()
            }
        }
    }

    /**
     * Updates the foreground notification's Pause/Resume button icon dynamically
     * without recreating the entire notification.
     */
    private fun updateNotificationPauseButton() {
        val notification = buildNotification(
            if (isPaused) "RPC Paused" else "Rich Presence Active",
            if (isPaused) "Tap Resume to continue syncing" else "Broadcasting media status"
        )
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            .build()
    }

    private fun activeSessionsListener(mediaSessions: List<MediaController>?, isEvent: Boolean = true) {
        logger.d("MediaRPC", "Active sessions changed: ${mediaSessions?.size ?: 0}")

        if (isEvent) {
            // Give the new session time to populate metadata
            scope.launch {
                delay(1000)
                applySessionChange(mediaSessions)
            }
        } else {
            applySessionChange(mediaSessions)
        }
    }

    private fun applySessionChange(mediaSessions: List<MediaController>?) {
        // Unregister from old session
        currentMediaController?.unregisterCallback(mediaControllerCallback)

        if (mediaSessions?.isNotEmpty() == true) {
            // Smart session selection:
            // 1. Prefer a session that is currently PLAYING
            // 2. Then prefer a session with metadata
            // 3. Then any session
            val selectedSession = mediaSessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING && it.metadata != null
            } ?: mediaSessions.firstOrNull { it.metadata != null }
                ?: mediaSessions.firstOrNull()

            currentMediaController = selectedSession
            currentMediaController?.registerCallback(mediaControllerCallback)

            logger.d("MediaRPC", "Tracking session: ${selectedSession?.packageName} (${selectedSession?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "no title"})")

            scope.launch {
                delay(500)
                updatePresence()
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
                updatePresence()
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
                        kizzyRPC.closeRPC()
                        stateRepository.setConnected(false)
                    }
                }
            }
        }
        return START_STICKY
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "Media Rich Presence",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your Discord Rich Presence active in the background"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
