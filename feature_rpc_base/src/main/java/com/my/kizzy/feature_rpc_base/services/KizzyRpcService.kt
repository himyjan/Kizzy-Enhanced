package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.security.KizzyStateRepository
import com.my.kizzy.feature_rpc_base.security.SecureTokenStorage
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.resources.R
import com.my.kizzy.data.work.WorkManagerHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KizzyRpcService : Service() {

    companion object {
        const val CHANNEL_ID = "kizzy_rpc_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_DISCONNECT = "kizzy.action.DISCONNECT"
        const val ACTION_EXIT = "kizzy.action.EXIT"
        const val ACTION_RETRY = "kizzy.action.RETRY"
        const val ACTION_STOP_SERVICE = "Stop Service"
        const val ACTION_RESTART_SERVICE = "Restart Service"
        const val ACTION_TOGGLE = "Toggle Playback"
    }

    @Inject
    lateinit var tokenStorage: SecureTokenStorage

    @Inject
    lateinit var stateRepository: KizzyStateRepository

    @Inject
    lateinit var logger: Logger

    private val TAG = "KizzyRpcService"

    private var isPaused = false
    private var lastKnownTitle: String? = null
    private var lastKnownArtist: String? = null
    private var lastKnownPosition: Long = 0
    private var lastKnownDuration: Long = 0

    override fun onCreate() {
        super.onCreate()
        stateRepository.setConnected(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT, ACTION_EXIT, ACTION_STOP_SERVICE -> {
                stateRepository.setConnected(false)
                stateRepository.setPaused(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                isPaused = !isPaused
                stateRepository.setPaused(isPaused)
                updateMediaState(
                    lastKnownTitle ?: "Kizzy RPC",
                    lastKnownArtist ?: (if (isPaused) "Paused" else "Active"),
                    !isPaused,
                    lastKnownPosition,
                    lastKnownDuration
                )
                return START_STICKY
            }
            ACTION_RETRY, ACTION_RESTART_SERVICE -> {
                isPaused = false
                stateRepository.setPaused(false)
                updateMediaState(
                    lastKnownTitle ?: "Kizzy RPC",
                    lastKnownArtist ?: "Listening...",
                    true,
                    lastKnownPosition,
                    lastKnownDuration
                )
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification("Kizzy RPC Active", "Waiting for media..."))
        return START_STICKY
    }

    /** Builds the foreground notification with always-visible Refresh and Exit buttons[cite: 1]. */
    private fun createNotification(title: String, content: String): Notification {
        createNotificationChannel()

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val retryIntent = Intent(this, KizzyRpcService::class.java).apply {
            action = ACTION_RETRY
        }
        val retryPendingIntent = PendingIntent.getService(
            this, 3, retryIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exitIntent = Intent(this, KizzyRpcService::class.java).apply {
            action = ACTION_EXIT
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_revert,
                    "Refresh",
                    retryPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Exit",
                    exitPendingIntent
                ).build()
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * **The Core Fix**: Instantly forces a notification refresh and updates 
     * metadata/timestamps on every jump time change, next track, app switch, or playback state change.
     */
    fun updateMediaState(
        title: String,
        artist: String,
        isPlaying: Boolean,
        position: Long = 0,
        duration: Long = 0
    ) {
        lastKnownTitle = title
        lastKnownArtist = artist
        lastKnownPosition = position
        lastKnownDuration = duration
        isPaused = !isPlaying

        // Push real-time media data to UserScreen via StateFlow
        stateRepository.setTrackInfo(title, artist, null)
        stateRepository.setPaused(isPaused)

        // Calculate timestamps for timeline/progress bar on UserScreen
        val now = System.currentTimeMillis()
        val startTs = now - position.coerceAtLeast(0L)
        val stopTs = if (duration > 0) startTs + duration else null
        stateRepository.setTimestamps(startTs, stopTs)

        val contentText = if (duration > 0) {
            "$artist • ${formatTime(position)} / ${formatTime(duration)}"
        } else {
            artist
        }

        val notification = createNotification(title, contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        logger.d(TAG, "Media State Refreshed -> $title by $artist [Pos: ${formatTime(position)}]")
    }

    fun updateNotificationText(title: String, content: String) {
        updateMediaState(title, content, true, lastKnownPosition, lastKnownDuration)
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kizzy Rich Presence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Kizzy RPC connected in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        logger.d(TAG, "onTaskRemoved: user swiped app from Recents")
        WorkManagerHelper.scheduleRpcRecovery(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stateRepository.setConnected(false)
        stateRepository.setPaused(false)
        logger.d(TAG, "onDestroy: scheduling RPC recovery via WorkManager")
        WorkManagerHelper.scheduleRpcRecovery(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}