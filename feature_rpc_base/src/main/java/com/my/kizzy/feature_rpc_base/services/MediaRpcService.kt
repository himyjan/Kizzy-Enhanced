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
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.YouTubeResolver
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

    private var currentMediaController: MediaController? = null
    private var currentMediaTitle: String? = null
    private var currentMediaArtist: String? = null
    private var currentMediaCoverArt: Bitmap? = null
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0
    private var lastSentProgressSec: Int = -1

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
        // Don't stop the service when the token is empty - the user may log in
        // later. refreshFromPrefs() in updateRPC() will pick up the token when
        // it becomes available. MEDIA_RPC_ENABLED is set to true so the watchdog
        // knows the service should be running.
        Prefs[Prefs.MEDIA_RPC_ENABLED] = true
        setupWakeLock()

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
                // 2-second tick instead of 1 second: the CPU wakes half as often
                // while media is playing, saving battery. Discord's progress bar
                // does not need 1-second precision, and the presence update is
                // deduplicated anyway - only a real seek is sent to the gateway.
                delay(2000)
                if (!isPaused && kizzyRPC.isRpcRunning()) {
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
                delay(10000)
                withWakeLock {
                    scope.launch { updatePresence(forceRefresh = true) }
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob.value?.cancel()
        heartbeatJob.value = null
    }

    /**
     * Serializes presence updates.
     *
     * The media callbacks and the timeline ticker call this at the same time. If two
     * updates run concurrently an older track can finish (cover-art upload) after a
     * newer one and overwrite it on Discord. Holding the mutex for the whole update -
     * including reading the media session and sending - makes the last call win.
     */
    suspend private fun updatePresence(forceRefresh: Boolean = false) {
        presenceUpdateMutex.withLock {
            updatePresenceLocked(forceRefresh)
        }
    }

    private suspend fun updatePresenceLocked(forceRefresh: Boolean = false) {
        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
        val showCoverArt = Prefs[Prefs.MEDIA_RPC_SHOW_COVER_ART, true]
        val playingMedia = getCurrentPlayingMedia()

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

        // Keep the 1-second timeline ticker running while media is playing. It is what
        // refreshes the Discord progress bar and the notification. It was previously
        // defined but never started, so the timeline never advanced.
        if (trackName != null && !isPaused) {
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
        updateNotificationPauseButton()

        val now = System.currentTimeMillis()
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
            logger.d("MediaRPC", "Presence unchanged, skipping redundant update")
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
        return metadataResolver.getMediaUrl(metadata, packageName)
    }

    /**
     * The Discord activity type of the app.
     *
     * It used to be a single global preference, so every app - YouTube Music
     * included - was reported as "Playing", which Discord draws as a game. The
     * type is now resolved per app: the choice of the user wins, then the
     * built-in detection and finally the global fallback of the Media RPC.
     */
    private fun resolveActivityType(playingMedia: CommonRpc): Int {
        val configured = Prefs[Prefs.MEDIA_RPC_ACTIVITY_TYPE, ActivityTypeResolver.DEFAULT_TYPE]
        val fallback = if (configured == Prefs.ACTIVITY_TYPE_AUTO) {
            ActivityTypeResolver.DEFAULT_TYPE
        } else {
            configured
        }
        return ActivityTypeResolver.resolve(
            packageName = playingMedia.appPackage,
            isMusicMetadata = playingMedia.isMusicMetadata,
            fallback = fallback,
            useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
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
            kizzyRPC.clearPresence()
            return
        }

        stateRepository.setConnected(true)
        stateRepository.setPaused(isPaused)

        val commonRpc = CommonRpc(
            name = playingMedia.name,
            type = resolveActivityType(playingMedia),
            details = playingMedia.details,
            state = playingMedia.state,
            largeImage = if (showCoverArt) playingMedia.largeImage else null,
            smallImage = playingMedia.smallImage,
            largeText = if (showCoverArt) playingMedia.largeText else null,
            smallText = playingMedia.smallText,
            time = if (enableTimestamps && !isPaused) Timestamps(
                start = playingMedia.time?.start,
                end = playingMedia.time?.end
            ) else null,
            packageName = playingMedia.appPackage,
            playbackState = playingMedia.playbackState,
            mediaUrl = playingMedia.mediaUrl
        )

        val mediaButtonEnabled = Prefs[MEDIA_RPC_MEDIA_BUTTON, false]
        val artistLinkEnabled = Prefs[MEDIA_RPC_ARTIST_LINK, false]
        // The buttons are cleared on every update. Without that the buttons of a
        // previous track (or of a setting that has been switched off) would stay
        // on the profile.
        kizzyRPC.clearButtons()
        if (mediaButtonEnabled) {
            val url = playingMedia.mediaUrl ?: resolveDirectMediaUrl()
            if (url != null) {
                kizzyRPC.setButton1(
                    MediaServiceResolver.mediaButtonLabel(
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
                val artistUrl = metadataResolver.getArtistUrl(metadata, playingMedia.appPackage)
                artistUrl?.let { url ->
                    kizzyRPC.setButton2(
                        MediaServiceResolver.artistButtonLabel(playingMedia.appPackage),
                        url,
                    )
                }
            }
        }

        kizzyRPC.updateRPC(commonRpc, enableTimestamps)
        updateNotificationPauseButton()
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
                logger.d("MediaRPC", "Playback state changed: paused=$isPaused")
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
                logger.d("MediaRPC", "Metadata unchanged, skipping redundant update")
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
        // Do NOT acquire here - wake lock is acquired per-work in withWakeLock()
        // so the CPU can sleep between media updates, saving battery.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ac = intent?.action
        when (ac) {
            Constants.ACTION_STOP_SERVICE -> {
                // The user switched the Media RPC off, so the watchdog must
                // not bring the service back up.
                Prefs[Prefs.MEDIA_RPC_ENABLED] = false
                stopSelf()
            }
            Constants.ACTION_RESTART_SERVICE -> {
                // The service must NOT be stopped first. stopSelf() tears the
                // service down and the pending stop then killed the fresh
                // instance, so the restart did nothing. Instead the current
                // instance is reused and its state is reset.
                logger.d("MediaRPC", "Restart requested, resetting the running service")
                restartInPlace()
            }
            Constants.ACTION_TOGGLE -> {
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
            null -> {
                // Normal start (no action): the user wants the Media RPC running.
                // Mark it as enabled so the watchdog knows it should stay alive.
                Prefs[Prefs.MEDIA_RPC_ENABLED] = true
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
        Prefs[Prefs.MEDIA_RPC_ENABLED] = true

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
