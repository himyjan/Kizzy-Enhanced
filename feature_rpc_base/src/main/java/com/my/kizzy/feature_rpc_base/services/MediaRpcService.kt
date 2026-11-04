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
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.TOKEN
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
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

    private var wakeLock: WakeLock? = null

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
    }

    suspend private fun updatePresence() {
        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, false]
        val playingMedia = getCurrentPlayingMedia()

        notificationBuilder
            .setContentTitle(playingMedia.name.ifEmpty { getString(R.string.app_name_full) })
            .setContentText(
                (playingMedia.details ?: "").ifEmpty { getString(R.string.idling_notification) }
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
                }
                kizzyRPC.updateRPC(playingMedia, enableTimestamps)
            }
            false -> {
                if (playingMedia.name.isBlank()) {
                    logger.d("MediaRPC", "Skipping RPC update with empty data")
                    return
                }
                kizzyRPC.apply {
                    setName(playingMedia.name)
                    setType(Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, 0])
                    setDetails(playingMedia.details)
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
                }
            }
        }
    }

    private val mediaControllerCallback = MediaControllerCallback()

    private fun activeSessionsListener(mediaSessions: List<MediaController>?, isEvent: Boolean = true) {
        logger.d("MediaRPC", "Active sessions changed")

        if (isEvent) runBlocking { delay(1500) }

        if (mediaSessions?.isNotEmpty() == true) {
            currentMediaController?.unregisterCallback(mediaControllerCallback)
            currentMediaController = mediaSessionManager.getActiveSessions(ComponentName(this, NotificationListener::class.java)).firstOrNull()
            currentMediaController?.registerCallback(mediaControllerCallback)
        } else {
            currentMediaController?.unregisterCallback(mediaControllerCallback)
            currentMediaController = null
        }

        scope.coroutineContext.cancelChildren()
        scope.launch { updatePresence() }
    }

    private inner class MediaControllerCallback : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)

            scope.coroutineContext.cancelChildren()
            scope.launch {
                delay(1000)
                updatePresence()
            }
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)

            scope.coroutineContext.cancelChildren()
            scope.launch {
                delay(1000)
                updatePresence()
            }
        }
        override fun onSessionDestroyed() {
            super.onSessionDestroyed()

            scope.coroutineContext.cancelChildren()
            scope.launch { updatePresence() }
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
                if (ac == Constants.ACTION_STOP_SERVICE)
                    stopSelf()
                else if (ac == Constants.ACTION_RESTART_SERVICE) {
                    stopSelf()
                    startService(Intent(this, MediaRpcService::class.java))
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(::activeSessionsListener)
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        scope.cancel()
        kizzyRPC.closeRPC()
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