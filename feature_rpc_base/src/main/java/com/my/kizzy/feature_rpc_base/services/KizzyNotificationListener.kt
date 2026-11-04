package com.my.kizzy.feature_rpc_base.services

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log
import com.my.kizzy.feature_rpc_base.security.SecureTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for media session changes across the device (Spotify, YouTube Music, etc.)
 * to capture track metadata for Discord Rich Presence updates.
 *
 * Requires BIND_NOTIFICATION_LISTENER_SERVICE permission in AndroidManifest.xml
 * and user-granted notification access in Settings > Apps > Special app access > Notification access.
 */
@Singleton
class KizzyNotificationListener @Inject constructor(
    private val tokenStorage: SecureTokenStorage
) : NotificationListenerService() {

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
            // Get currently active media sessions
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            updateActiveController(controllers)

            // Listen for changes when apps start/stop playing music
            mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionChangedListener, componentName)
        } catch (e: SecurityException) {
            Log.e("KizzyNotificationListener", "Notification access permission not granted", e)
        }
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        // Unregister previous controller callbacks if any
        activeController?.unregisterCallback(controllerCallback)

        // Find the first playing controller (or fallback to first available)
        activeController = controllers?.firstOrNull()

        activeController?.registerCallback(controllerCallback)

        // Read initial metadata from the active session
        extractMetadata(activeController)
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            parseAndBroadcastMetadata(metadata, activeController?.packageName)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            super.onPlaybackStateChanged(state)
            // Handle play/pause/stopped states here if needed
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

        // TODO: Broadcast metadata to DiscordWebSocketImpl via SharedFlow or callback
        // The WebSocket client can then update Discord Rich Presence with media info
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
