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
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KizzyRpcService : Service() {

    companion object {
        const val CHANNEL_ID = "kizzy_rpc_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_DISCONNECT = "kizzy.action.DISCONNECT"
        const val ACTION_TOGGLE = "kizzy.action.TOGGLE"
    }

    @Inject
    lateinit var tokenStorage: SecureTokenStorage

    @Inject
    lateinit var stateRepository: KizzyStateRepository

    private var isPaused = false
    private var lastKnownTitle: String? = null
    private var lastKnownArtist: String? = null

    override fun onCreate() {
        super.onCreate()
        stateRepository.setConnected(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stateRepository.setConnected(false)
                stateRepository.setPaused(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                isPaused = !isPaused
                stateRepository.setPaused(isPaused)

                if (isPaused) {
                    updateNotificationText("RPC Paused", "Tap Resume to continue syncing")
                } else {
                    lastKnownTitle?.let { title ->
                        lastKnownArtist?.let { artist ->
                            updateNotificationText(title, artist)
                        }
                    } ?: updateNotificationText("Kizzy RPC", "Listening for media...")
                }
                return START_STICKY
            }
        }

        // Default: start foreground service with persistent notification
        startForeground(NOTIFICATION_ID, createNotification("Kizzy RPC Active", "Waiting for media..."))
        return START_STICKY
    }

    /** Builds the foreground notification with dynamic Pause/Resume button. */
    private fun createNotification(title: String, content: String): Notification {
        createNotificationChannel()

        // PendingIntent for clicking the notification body (opens main app)
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PendingIntent for the Toggle Action (Pause / Resume)
        val toggleIntent = Intent(this, KizzyRpcService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // PendingIntent for the Disconnect Action
        val disconnectIntent = Intent(this, KizzyRpcService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Dynamically choose button label and icon based on pause state
        val toggleLabel = if (isPaused) "Resume" else "Pause"
        val toggleIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_media_rpc)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    toggleIcon,
                    toggleLabel,
                    togglePendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Disconnect",
                    disconnectPendingIntent
                ).build()
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Updates the foreground notification text dynamically without restarting the service. */
    fun updateNotificationText(title: String, content: String) {
        lastKnownTitle = title
        lastKnownArtist = content

        if (isPaused) return // Do not overwrite paused notification state

        val notification = createNotification(title, content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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

    override fun onDestroy() {
        super.onDestroy()
        stateRepository.setConnected(false)
        stateRepository.setPaused(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
