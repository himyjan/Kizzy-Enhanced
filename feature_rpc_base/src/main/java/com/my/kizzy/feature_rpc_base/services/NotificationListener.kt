package com.my.kizzy.feature_rpc_base.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.utils.MediaMetadataParser
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import kotlinx.coroutines.runBlocking
import kizzy.gateway.DiscordWebSocketImpl

class NotificationListener : NotificationListenerService() {

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeController: MediaController? = null

    /**
     * The watchdog that restarts the Media RPC when it died.
     */
    private var watchdogHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null

    companion object {
        /**
         * How often the watchdog checks whether the Media RPC is still alive.
         */
        private const val WATCHDOG_INTERVAL_MS = 30_000L
    }

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
        Log.d("NotificationListener", "Listener connected")
        startWatchdog()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationListener", "Listener disconnected")
        stopWatchdog()
    }

    /**
     * Starts the watchdog.
     *
     * The media session events only fire when something *changes*, so a
     * service that was killed while the music kept playing was never noticed.
     * The watchdog asks the system whether the service is still running and
     * starts it again when the user wants the Media RPC but it is gone.
     */
    private fun startWatchdog() {
        stopWatchdog()
        val handler = Handler(Looper.getMainLooper())
        watchdogHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                try {
                    checkMediaRpcService()
                } catch (_: Exception) {
                    // The watchdog must never crash the listener.
                }
                handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        watchdogRunnable = runnable
        handler.postDelayed(runnable, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler?.removeCallbacks(it) }
        watchdogRunnable = null
        watchdogHandler = null
    }

    /**
     * Starts the Media RPC when it should run but does not.
     */
    private fun checkMediaRpcService() {
        // The user wants the Media RPC.
        if (!Prefs[Prefs.MEDIA_RPC_ENABLED, false]) return
        // But it is running already.
        if (AppUtils.mediaRpcRunning()) return

        Log.d("NotificationListener", "Media RPC is enabled but not running - starting it")
        val intent = Intent(this, MediaRpcService::class.java)
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

        Log.d("NotificationListener", "App: $packageName | Track: $title by $artist ($album)")

        // The notification listener used to send a presence of its own, in
        // parallel to the Media RPC. Two features wrote the same presence, so
        // they overwrote each other and the cover art of the real presence was
        // lost. The Media RPC owns the presence, the listener only watches.
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
