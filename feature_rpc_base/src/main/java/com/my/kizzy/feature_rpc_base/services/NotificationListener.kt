package com.my.kizzy.feature_rpc_base.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.util.Log
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.utils.MediaMetadataParser
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import kotlinx.coroutines.runBlocking
import kizzy.gateway.DiscordWebSocketImpl

class NotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null

    private val activeSessionChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        try {
            val componentName = ComponentName(this, this::class.java)
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveController(controllers)
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionChangedListener, componentName)
        } catch (e: SecurityException) {
            Log.e("NotificationListener", "Notification access permission not granted", e)
        }
    }

    /**
     * Called when the system binds to this NotificationListenerService.
     * System-bound services get auto-restarted by the OS framework on OEM
     * firmware when the process is killed, so we restart MediaRpcService here
     * to ensure Rich Presence continues after aggressive background cleanup.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationListener", "Listener connected - restarting MediaRpcService")
        restartMediaRpcService()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationListener", "Listener disconnected")
    }

    private fun restartMediaRpcService() {
        val intent = Intent(this, MediaRpcService::class.java)
        intent.action = Constants.ACTION_RESTART_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        activeController = controllers?.firstOrNull()

        activeController?.registerCallback(controllerCallback)

        extractMetadata(activeController)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            parseAndBroadcastMetadata(metadata, activeController?.packageName)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            super.onPlaybackStateChanged(state)
        }
    }

    private fun extractMetadata(controller: MediaController?) {
        val metadata = controller?.metadata
        val packageName = controller?.packageName
        parseAndBroadcastMetadata(metadata, packageName)
    }

    private fun parseAndBroadcastMetadata(metadata: MediaMetadata?, packageName: String?) {
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Track"
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "Unknown Album"
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        Log.d("NotificationListener", "App: $packageName | Track: $title by $artist ($album)")

        val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, false]

        DiscordWebSocketImpl.instance?.let { discord ->
            runBlocking {
                discord.updatePresence(
                    name = title,
                    details = artist,
                    state = album,
                    largeImageKey = null,
                    largeText = "$artist - $album",
                    status = "online",
                    afk = false
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeController?.unregisterCallback(controllerCallback)
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionChangedListener)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
