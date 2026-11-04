package com.my.kizzy.feature_rpc_base.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.my.kizzy.feature_rpc_base.security.SecureTokenStorage
import com.my.kizzy.feature_rpc_base.utils.MediaMetadataParser
import kizzy.gateway.DiscordWebSocketImpl
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KizzyNotificationListener @Inject constructor(
    private val context: Context,
    private val tokenStorage: SecureTokenStorage
) : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null
    private val metadataParser = MediaMetadataParser()

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
            Log.e("KizzyNotificationListener", "Notification access permission not granted", e)
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

        Log.d("KizzyNotificationListener", "App: $packageName | Track: $title by $artist ($album)")

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