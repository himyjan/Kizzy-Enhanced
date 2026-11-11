/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ExperimentalRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMediaAll
import com.my.kizzy.data.get_current_data.media.RichMediaMetadata
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.TemplateKeys
import com.my.kizzy.data.rpc.TemplateProcessor
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.data.work.WorkManagerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "ExperimentalRPC"

@Suppress("DEPRECATION")
@AndroidEntryPoint
class ExperimentalRpc : Service() {

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var kizzyRPC: KizzyRPC

    @Inject
    lateinit var getCurrentPlayingMediaAll: GetCurrentPlayingMediaAll

    @Inject
    lateinit var getCurrentlyRunningApp: GetCurrentlyRunningApp

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    private lateinit var mediaSessionManager: MediaSessionManager

    private var currentMediaController: MediaController? = null

    private val mediaControllerCallback = MediaControllerCallback()

    private suspend fun fetchPlayingMediaWithRetry(): RichMediaMetadata {
        var richMediaData = this.getCurrentPlayingMediaAll.invoke()
        var attempt = 1
        while (richMediaData.appName == null && attempt < maxMediaDetectionAttempts) {
            logger.d(TAG, "fetchPlayingMediaWithRetry attempt $attempt: appName=${richMediaData.appName}, title=${richMediaData.title}, pkg=${richMediaData.packageName} - retrying...")
            delay(mediaDetectionDelayMs * attempt)
            richMediaData = this.getCurrentPlayingMediaAll.invoke()
            attempt++
        }
        logger.d(TAG, "fetchPlayingMediaWithRetry final: appName=${richMediaData.appName}, title=${richMediaData.title}, pkg=${richMediaData.packageName}")
        return richMediaData
    }

    private var isMediaSessionActive = false
    private var lastSentCommonRpc: CommonRpc? = null
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0

    // Periodic refresh to catch position changes that happen between onPlaybackStateChanged
    // callbacks (e.g., during drag scrubbing when the player may not fire callbacks for every
    // position change).
    private var mediaRefreshJob: Job? = null
    private val mediaRefreshIntervalMs = 1000L

    // Heartbeat to prevent stuck idling when media session callbacks are missed
    private val heartbeatJob = MutableStateFlow<Job?>(null)

    // The single app-detection polling loop. Tracked so it is never started twice.
    private var appDetectionJob: Job? = null

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob.value = scope.launch {
            while (isActive) {
                delay(5000)
                try {
                    val richMediaData = getCurrentPlayingMediaAll.invoke()
                    if (richMediaData.appName != null) {
                        updatePresence(
                            richMediaInfo = richMediaData,
                            rawMediaMetadata = null
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob.value?.cancel()
        heartbeatJob.value = null
    }

    // Periodic media refresh to catch position changes that happen between onPlaybackStateChanged
    // callbacks (e.g., during drag scrubbing when the player may not fire callbacks for every
    // position change).
    private fun startMediaRefresh() {
        mediaRefreshJob?.cancel()
        mediaRefreshJob = scope.launch {
            while (isActive) {
                delay(mediaRefreshIntervalMs)
                try {
                    // Fetch fresh media data each cycle instead of calling updatePresence() with no args
                    val richMediaData = getCurrentPlayingMediaAll.invoke()
                    if (richMediaData.appName != null) {
                        updatePresence(
                            richMediaInfo = richMediaData,
                            rawMediaMetadata = null
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Media refresh failed: ${e.message}")
                }
            }
        }
    }

    private fun stopMediaRefresh() {
        mediaRefreshJob?.cancel()
        mediaRefreshJob = null
    }

    private var useAppsRpc = Prefs[Prefs.EXPERIMENTAL_RPC_USE_APPS_RPC, true]
    private var useMediaRpc = Prefs[Prefs.EXPERIMENTAL_RPC_USE_MEDIA_RPC, true]

    // Media detection retry settings - YouTube Music/LX Music can take 1-3 seconds
    // to populate title metadata after session appears.
    private val mediaDetectionDelayMs = 500L
    private val maxMediaDetectionAttempts = 4

    private var templateName =
        Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
    private var templateDetails =
        Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
    private var templateState =
        Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]

    private var appActivityTypes: Map<String, Int> = Prefs.getAppActivityTypes()
    private var enabledExperimentalApps: List<String> = readEnabledExperimentalApps()

    /** Reads the list of apps enabled for the Experimental RPC from the preferences. */
    private fun readEnabledExperimentalApps(): List<String> = try {
        Json.decodeFromString(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
    } catch (_: Exception) {
        emptyList()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action.equals(Constants.ACTION_STOP_SERVICE)) stopSelf()
        else if (intent?.action.equals(Constants.ACTION_RESTART_SERVICE)) {
            stopSelf()
            startService(Intent(this, ExperimentalRpc::class.java))
        } else {
            val stopIntent = Intent(this, ExperimentalRpc::class.java)
            stopIntent.action = Constants.ACTION_STOP_SERVICE
            val pendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
            )
            val restartIntent = Intent(this, ExperimentalRpc::class.java)
            restartIntent.action = Constants.ACTION_RESTART_SERVICE
            val restartPendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
            )

            // Open main app when notification is clicked
            val appIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN)
            val appPendingIntent = PendingIntent.getActivity(
                this, 0, appIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = Notification.Builder(this, Constants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dev_rpc)
                .setContentTitle(getString(R.string.service_enabled))
                .setContentIntent(appPendingIntent)
                .addAction(
                    R.drawable.ic_dev_rpc,
                    getString(R.string.restart),
                    restartPendingIntent
                )
                .addAction(R.drawable.ic_dev_rpc, getString(R.string.exit), pendingIntent)
                .build()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(Constants.NOTIFICATION_ID, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }


            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            // Registering the session listener requires Notification Listener access.
            // Without it this throws SecurityException, so guard to avoid crashing.
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    ::activeSessionsListener,
                    ComponentName(this, NotificationListener::class.java)
                )
            } catch (e: SecurityException) {
                logger.e(TAG, "No notification access, media part disabled: ${e.message}")
            }

            // Always reload settings on start
            templateName = Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
            templateDetails = Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
            templateState = Prefs[Prefs.EXPERIMENTAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]
            useAppsRpc = Prefs[Prefs.EXPERIMENTAL_RPC_USE_APPS_RPC, true]
            useMediaRpc = Prefs[Prefs.EXPERIMENTAL_RPC_USE_MEDIA_RPC, true]
            appActivityTypes = Prefs.getAppActivityTypes()
            enabledExperimentalApps = try {
                Json.decodeFromString(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
            } catch (_: Exception) {
                emptyList()
            }

            val initialMediaSessions = try {
                mediaSessionManager.getActiveSessions(
                    ComponentName(this, NotificationListener::class.java)
                )
            } catch (e: SecurityException) {
                logger.e(TAG, "No notification access, skipping media sessions: ${e.message}")
                emptyList()
            }
            var mediaActiveInitially = false
            if (useMediaRpc && initialMediaSessions.isNotEmpty()) {
                val firstActiveMediaController = initialMediaSessions.firstOrNull {
                    enabledExperimentalApps.contains(it.packageName)
                }
                if (firstActiveMediaController != null) {
                    mediaActiveInitially = true
                    activeSessionsListener(listOf(firstActiveMediaController), false)
                }
            }

            if (!mediaActiveInitially) {
                scope.launch {
                    var foundMedia = false
                    if (useMediaRpc) {
                        // Try direct media detection before falling to app polling.
                        // This lets the first song appear immediately without waiting
                        // for the user to switch tracks or for the 5s app detection cycle.
                        // Retry up to 4 times with increasing delay because YouTube/LX Music
                        // can take 1-3 seconds to populate title metadata.
                        val directMedia = fetchPlayingMediaWithRetry()
                        if (directMedia.appName != null) {
                            logger.d(TAG, "Direct media detection found: ${directMedia.title} (${directMedia.packageName})")
                            updatePresence(
                                richMediaInfo = directMedia,
                                rawMediaMetadata = null
                            )
                            foundMedia = true
                        }
                    }
                    if (!foundMedia && useAppsRpc) {
                        startAppDetectionCoroutine()
                    }
                }
            }
        }
        // Start heartbeat to prevent stuck idling
        startHeartbeat()
        // Start periodic media refresh to catch position changes that happen between
        // onPlaybackStateChanged callbacks (e.g., during drag scrubbing).
        startMediaRefresh()

        // Establish the Discord gateway connection so that updateRPC()'s sendActivity()
        // can deliver presence. Without connect(), the socket is never opened and every
        // presence update times out with "Socket not connected within timeout".
        scope.launch {
            kizzyRPC.build()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(intent: Intent) {
        super.onTaskRemoved(intent)
        logger.d(TAG, "onTaskRemoved: user swiped app from Recents")
        // Schedule RPC recovery via WorkManager so presence can be restored after
        // aggressive task removal or process termination by the system.
        WorkManagerHelper.scheduleRpcRecovery(this)
    }

    private fun startAppDetectionCoroutine() {
        // Only ever run ONE detection loop. This method is called from several places
        // (session changes, playback callbacks, hide-on-pause), and each call used to
        // start another loop, so multiple loops competed and pushed conflicting
        // presences - which made the shown app jump around.
        if (appDetectionJob?.isActive == true) {
            logger.d(TAG, "App detection coroutine already running")
            return
        }
        logger.d(TAG, "Starting app detection coroutine")

        val startTimestamps = Timestamps(start = System.currentTimeMillis())

        appDetectionJob = scope.launch {
            var currentPackageName = ""
            while (isActive) {
                try {
                    // Re-read the enabled apps on every cycle so that toggling an app in
                    // the app list takes effect immediately, without a service restart.
                    enabledExperimentalApps = readEnabledExperimentalApps()

                    val currentApp = getCurrentlyRunningApp()

                    if (
                        currentApp.name.isNotEmpty() &&
                        currentApp.packageName != currentPackageName &&
                        enabledExperimentalApps.contains(currentApp.packageName)
                    ) {
                        currentPackageName = currentApp.packageName
                        updatePresence(appInfo = currentApp.copy(time = startTimestamps))
                    } else if (currentApp.name.isNotEmpty() && currentApp.packageName != currentPackageName) {
                        currentPackageName = ""
                        if (!isMediaSessionActive || !useMediaRpc) {
                            updatePresence(CommonRpc())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Detection cycle failed: ${e.message}")
                }
                delay(2000)
            }
        }
    }

    private suspend fun updatePresence(
        appInfo: CommonRpc? = null,
        richMediaInfo: RichMediaMetadata? = null,
        rawMediaMetadata: MediaMetadata? = null,
    ) {
        // If no media data at all, don't touch Discord at all - just return.
        // Sending empty presence would clear Discord's display and close the RPC.
        // When media loads later, MediaControllerCallback will trigger updatePresence
        // with real data.
        if (richMediaInfo == null && appInfo == null) {
            logger.d(TAG, "No media data available - skipping presence update, waiting for media to load")
            return
        }

        val finalName: String?
        val finalDetails: String?
        val finalState: String?
        var finalLargeImage: RpcImage?
        var finalSmallImage: RpcImage?
        var finalLargeText: String?
        var finalSmallText: String?
        var finalTimestamps: Timestamps?
        var effectivePackageName: String?

        // Hide media on pause if enabled
        if (richMediaInfo != null &&
            Prefs[Prefs.EXPERIMENTAL_RPC_HIDE_ON_PAUSE, false] &&
            (richMediaInfo.playbackState == PlaybackState.STATE_PAUSED ||
                    richMediaInfo.playbackState == PlaybackState.STATE_STOPPED)
        ) {
            if (useAppsRpc) {
                startAppDetectionCoroutine()
            } else if (kizzyRPC.isRpcRunning()) {
                // Clear the activity but keep the gateway connection alive, so playback can
                // resume instantly instead of having to reconnect from scratch.
                kizzyRPC.clearPresence()
                val stopIntent = Intent(this, ExperimentalRpc::class.java)
                stopIntent.action = Constants.ACTION_STOP_SERVICE
                val stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
                )
                val restartIntent = Intent(this, ExperimentalRpc::class.java)
                restartIntent.action = Constants.ACTION_RESTART_SERVICE
                val restartPendingIntent = PendingIntent.getService(
                    this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
                notificationManager.notify(
                    Constants.NOTIFICATION_ID,
                    Notification.Builder(this, Constants.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_dev_rpc)
                        .setContentTitle(getString(R.string.service_enabled))
                        .setContentText(getString(R.string.idling_notification))
                        .addAction(R.drawable.ic_dev_rpc, getString(R.string.restart), restartPendingIntent)
                        .addAction(R.drawable.ic_dev_rpc, getString(R.string.exit), stopPendingIntent)
                        .build()
                )
            }
            return
        }

        val currentContextIsMedia =
            useMediaRpc && richMediaInfo != null && richMediaInfo.appName != null
        val currentContextIsApp = useAppsRpc && appInfo != null && appInfo.name.isNotEmpty()

        val processor = TemplateProcessor(
            mediaMetadata = rawMediaMetadata,
            mediaPlayerAppName = richMediaInfo?.appName,
            mediaPlayerPackageName = richMediaInfo?.packageName,
            detectedAppInfo = appInfo,
            batteryLevel = currentBatteryLevel(),
            playbackState = richMediaInfo?.playbackState
        )

        if (currentContextIsMedia) {
            effectivePackageName = richMediaInfo?.packageName

            currentMediaProgressSec = (richMediaInfo.position / 1000).toInt().coerceIn(0, (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0))
            currentMediaDurationSec = (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0)

            logger.d(TAG, "Processing Rich Media Context")
            if (Prefs[Prefs.EXPERIMENTAL_RPC_SHOW_SONG_AS_TITLE, false]) {
                val titleSource = Prefs[Prefs.EXPERIMENTAL_RPC_TITLE_SOURCE, "title"]
                finalName = if (titleSource == "artist") richMediaInfo?.artist else richMediaInfo?.title
                finalDetails = resolveTemplateValue(processor.process(templateDetails), richMediaInfo?.appName)
                finalState = resolveTemplateValue(processor.process(templateState), richMediaInfo?.state.takeIf { titleSource != "artist" })
            } else {
                finalName = resolveTemplateValue(processor.process(templateName), richMediaInfo?.appName)
                finalDetails = resolveTemplateValue(processor.process(templateDetails), richMediaInfo?.title)
                finalState = resolveTemplateValue(processor.process(templateState), richMediaInfo?.state)
            }

            finalLargeImage = when {
                Prefs[Prefs.EXPERIMENTAL_RPC_SHOW_COVER_ART, true] ->
                    // Fall back to the player's app icon when cover art is unavailable
                    richMediaInfo?.coverArt ?: richMediaInfo?.appIcon
                Prefs[Prefs.EXPERIMENTAL_RPC_SHOW_APP_ICON, false] -> richMediaInfo?.appIcon
                else -> null
            }

            finalSmallImage = when {
                Prefs[Prefs.EXPERIMENTAL_RPC_SHOW_PLAYBACK_STATE, true] ->
                    richMediaInfo?.playbackStateIcon

                Prefs[Prefs.EXPERIMENTAL_RPC_SHOW_APP_ICON, false] && finalLargeImage != richMediaInfo?.appIcon ->
                    richMediaInfo?.appIcon
                else -> null
            }

            finalLargeText = richMediaInfo?.album
            finalSmallText =
                if (finalSmallImage == richMediaInfo?.appIcon) richMediaInfo?.appName else null

            finalTimestamps = if (richMediaInfo?.playbackState == PlaybackState.STATE_PLAYING) {
                resolveTimestamps(richMediaInfo?.timestamps)
            } else {
                // Keep timestamps frozen when paused or stopped so Discord
                // doesn't reset the progress bar.
                resolveTimestamps(richMediaInfo?.timestamps)
            }

        } else if (currentContextIsApp) {
            effectivePackageName = appInfo?.packageName
            logger.d(TAG, "Processing App Context")
            finalName = resolveTemplateValue(processor.process(templateName), appInfo?.name)
            finalDetails = resolveTemplateValue(processor.process(templateDetails), appInfo?.details)
            finalState = resolveTemplateValue(processor.process(templateState), appInfo?.state)

            finalLargeImage = appInfo?.largeImage
            finalSmallImage = appInfo?.smallImage
            finalLargeText = appInfo?.largeText
            finalSmallText = appInfo?.smallText
            finalTimestamps = resolveTimestamps(appInfo?.time)
        } else {
            logger.d(TAG, "No active context (App or Media) — using default template presence")
            // No media/app active: leave presence fields empty so Discord shows nothing
            // instead of placeholder text like "Using Kizzy RPC".
            finalName = null
            finalDetails = null
            finalState = null
            finalLargeImage = null
            finalSmallImage = null
            finalLargeText = null
            finalSmallText = null
            finalTimestamps = null
            effectivePackageName = null
        }
        val rpcDataIsEmpty =
            finalName.isNullOrEmpty() && finalDetails.isNullOrEmpty() && finalState.isNullOrEmpty()

        if (rpcDataIsEmpty) {
            logger.d(TAG, "Calculated RPC data is empty, clearing presence.")
            if (kizzyRPC.isRpcRunning()) {
                // Clear the activity but keep the connection, so the next track shows up
                // immediately.
                kizzyRPC.clearPresence()
                val stopIntent = Intent(this, ExperimentalRpc::class.java)
                stopIntent.action = Constants.ACTION_STOP_SERVICE
                val stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
                )
                val restartIntent = Intent(this, ExperimentalRpc::class.java)
                restartIntent.action = Constants.ACTION_RESTART_SERVICE
                val restartPendingIntent = PendingIntent.getService(
                    this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
                notificationManager.notify(
                    Constants.NOTIFICATION_ID,
                    Notification.Builder(this, Constants.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_dev_rpc)
                        .setContentTitle(getString(R.string.service_enabled))
                        .setContentText(getString(R.string.idling_notification))
                        .addAction(R.drawable.ic_dev_rpc, getString(R.string.restart), restartPendingIntent)
                        .addAction(R.drawable.ic_dev_rpc, getString(R.string.exit), stopPendingIntent)
                        .build()
                )
            }
            return
        }

        // Build the CommonRpc once - this is what we want to send to Discord
        val commonRpc = CommonRpc(
            name = finalName ?: "",
            type = appActivityTypes[effectivePackageName] ?: 0,
            details = finalDetails,
            state = finalState,
            largeImage = finalLargeImage,
            smallImage = finalSmallImage,
            largeText = finalLargeText,
            smallText = finalSmallText,
            time = finalTimestamps,
            packageName = effectivePackageName ?: "",
            playbackState = richMediaInfo?.playbackState
        )

        // De-duplication: skip if nothing changed since last push
        if (lastSentCommonRpc == commonRpc) {
            logger.d(TAG, "Presence unchanged, skipping redundant update")
            return
        }
        lastSentCommonRpc = commonRpc

        // Apply the buttons and the custom application id that the user configured in the
        // Experimental RPC screen. They were stored in the preferences but never actually
        // applied to the presence.
        kizzyRPC.clearButtons()
        if (Prefs[Prefs.EXPERIMENTAL_RPC_USE_BUTTONS, false]) {
            val button1Text = Prefs[Prefs.EXPERIMENTAL_RPC_BUTTON1_TEXT, ""]
            val button1Url = Prefs[Prefs.EXPERIMENTAL_RPC_BUTTON1_URL, ""]
            val button2Text = Prefs[Prefs.EXPERIMENTAL_RPC_BUTTON2_TEXT, ""]
            val button2Url = Prefs[Prefs.EXPERIMENTAL_RPC_BUTTON2_URL, ""]
            if (button1Text.isNotEmpty() && button1Url.isNotEmpty()) {
                kizzyRPC.setButton1(button1Text, button1Url)
            }
            if (button2Text.isNotEmpty() && button2Url.isNotEmpty()) {
                kizzyRPC.setButton2(button2Text, button2Url)
            }
        }
        if (Prefs[Prefs.EXPERIMENTAL_RPC_USE_CUSTOM_APP_ID, false]) {
            kizzyRPC.setApplicationId(Prefs[Prefs.EXPERIMENTAL_RPC_APPLICATION_ID, ""])
        }
        kizzyRPC.setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "online"])

        kizzyRPC.updateRPC(
            commonRpc = commonRpc,
            enableTimestamps = Prefs[Prefs.EXPERIMENTAL_RPC_ENABLE_TIMESTAMPS, true]
        )

        val notifTitle = finalName.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.app_name_full)
        val notifText = if (currentMediaDurationSec > 0) {
            val currentStr = formatDuration(currentMediaProgressSec * 1000L)
            val totalStr = formatDuration(currentMediaDurationSec * 1000L)
            val label = listOfNotNull(finalDetails, finalState).joinToString(" · ")
            if (label.isEmpty()) "$currentStr / $totalStr" else "$label ($currentStr / $totalStr)"
        } else {
            listOfNotNull(finalDetails, finalState).joinToString(" · ")
                .ifEmpty { getString(R.string.idling_notification) }
        }

        val stopIntent = Intent(this, ExperimentalRpc::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val restartIntent = Intent(this, ExperimentalRpc::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        val restartPendingIntent = PendingIntent.getService(
            this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(
            Constants.NOTIFICATION_ID,
            Notification.Builder(this, Constants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dev_rpc)
                .setContentTitle(notifTitle)
                .setContentText(notifText)
                .setLargeIcon(rpcImage = finalLargeImage, context = this@ExperimentalRpc)
                .addAction(R.drawable.ic_dev_rpc, getString(R.string.restart), restartPendingIntent)
                .addAction(R.drawable.ic_dev_rpc, getString(R.string.exit), stopPendingIntent)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && currentMediaDurationSec > 0) {
                        setProgress(currentMediaDurationSec, currentMediaProgressSec, false)
                    }
                }
                .build()
        )
    }

    private fun activeSessionsListener(
        mediaSessions: List<MediaController>?,
        isEvent: Boolean = true,
    ) {
        if (!useMediaRpc) {
            logger.i(TAG, "Media part of Experimental RPC is disabled.")
            if (useAppsRpc && !isMediaSessionActive) {
                scope.coroutineContext.cancelChildren()
                startAppDetectionCoroutine()
            } else if (!useAppsRpc) {
                scope.launch {
                    updatePresence(
                        appInfo = null,
                        richMediaInfo = null,
                        rawMediaMetadata = null
                    )
                }
            }
            return
        }
        logger.d(TAG, "Active media sessions changed")

        currentMediaController?.unregisterCallback(mediaControllerCallback)
        currentMediaController = null

        if (mediaSessions?.isNotEmpty() == true) {
            // Try to register callback on a matching session for playback state updates
            currentMediaController = mediaSessions.firstOrNull {
                enabledExperimentalApps.contains(it.packageName)
            }
            currentMediaController?.registerCallback(mediaControllerCallback)
        }

        scope.coroutineContext.cancelChildren()
        scope.launch {
            // Give media session metadata time to populate on initial load.
            // YouTube/LX Music often take 1-3 seconds to populate title metadata.
            // Retry up to 3 times with increasing delay before giving up.
            val richMediaData = fetchPlayingMediaWithRetry()
            // Process media data even without a registered controller — controller is only
            // needed for playback callbacks, presence updates just need media data
            val hasMediaData = richMediaData.appName != null

            if (hasMediaData && useMediaRpc) {
                updatePresence(
                    richMediaInfo = richMediaData,
                    rawMediaMetadata = currentMediaController?.metadata
                )
            } else if (useAppsRpc) {
                startAppDetectionCoroutine()
            } else {
                // Don't send empty presence to Discord - just wait. When media loads later,
                // MediaControllerCallback will trigger updatePresence with real data.
                logger.d(TAG, "No media data available yet - skipping presence update, waiting for media to load")
            }
        }
    }

    private inner class MediaControllerCallback : MediaController.Callback() {
        private fun handleMediaUpdate() {
            if (!useMediaRpc) return

            scope.coroutineContext.cancelChildren()
            scope.launch {
                delay(200)
                val richMediaData = this@ExperimentalRpc.getCurrentPlayingMediaAll.invoke()
                val hasMediaData = richMediaData.appName != null

                if (hasMediaData && useMediaRpc) {
                    // Re-acquire controller from fresh session list so metadata matches the
                    // current song (not the old paused session).
                    val freshController = try {
                        mediaSessionManager
                            .getActiveSessions(ComponentName(this@ExperimentalRpc, NotificationListener::class.java))
                            .firstOrNull { it.packageName == richMediaData.packageName }
                    } catch (_: Exception) {
                        null
                    }
                    updatePresence(
                        richMediaInfo = richMediaData,
                        rawMediaMetadata = freshController?.metadata
                    )
                } else {
                    if (useAppsRpc) {
                        startAppDetectionCoroutine()
                    } else {
                        updatePresence(
                            appInfo = null,
                            richMediaInfo = null,
                            rawMediaMetadata = null
                        )
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            logger.d(TAG, "MediaControllerCallback: onPlaybackStateChanged")
            handleMediaUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            logger.d(TAG, "MediaControllerCallback: onMetadataChanged")
            handleMediaUpdate()
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            logger.d(TAG, "MediaControllerCallback: onSessionDestroyed")
            currentMediaController?.unregisterCallback(this)
            currentMediaController = null
            isMediaSessionActive = false

            scope.coroutineContext.cancelChildren()
            scope.launch {
                if (useAppsRpc) {
                    startAppDetectionCoroutine()
                } else {
                    updatePresence(appInfo = null, richMediaInfo = null, rawMediaMetadata = null)
                }
            }
        }
    }

    /**
     * Resolves which timestamps to show based on the user's selected mode:
     *  - "default": timestamps coming from the media/app context (original behaviour)
     *  - "current": a fixed start anchored to when the user picked this mode, so the
     *    elapsed timer keeps counting and does NOT reset after 24h / restarts
     *  - "custom": raw start/end epoch millis typed by the user
     * Returns null when timestamps are globally disabled.
     */
    private fun resolveTimestamps(contextTimestamps: Timestamps?): Timestamps? {
        if (!Prefs[Prefs.EXPERIMENTAL_RPC_ENABLE_TIMESTAMPS, true]) return null
        return when (Prefs[Prefs.EXPERIMENTAL_RPC_TIMESTAMP_MODE, "default"]) {
            "current" -> {
                // Mirror Vencord's customRPC "TIME" mode:
                // start = midnight of today → Discord shows elapsed = current time of day
                val cal = java.util.Calendar.getInstance()
                val midnightMs = System.currentTimeMillis() -
                    (cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600L +
                     cal.get(java.util.Calendar.MINUTE) * 60L +
                     cal.get(java.util.Calendar.SECOND)) * 1000L
                Timestamps(start = midnightMs)
            }
            "custom" -> {
                val start = Prefs[Prefs.EXPERIMENTAL_RPC_TIMESTAMP_CUSTOM_START, ""].toLongOrNull()
                val end = Prefs[Prefs.EXPERIMENTAL_RPC_TIMESTAMP_CUSTOM_END, ""].toLongOrNull()
                if (start == null && end == null) null else Timestamps(start = start, end = end)
            }
            else -> contextTimestamps
        }
    }

    /** Current battery percentage (0..100) for the {{battery}} template placeholder. */
    private fun currentBatteryLevel(): Int? = try {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    } catch (e: Exception) {
        null
    }

    /**
     * Resolves a template processor result, falling back to [fallback] if the result is null,
     * empty, or still contains unresolved template keys (e.g. {{app_name}}).
     */
    private fun resolveTemplateValue(result: String?, fallback: String?): String? {
        val resolved = result?.trim()
        if (resolved == null || resolved.isEmpty() || resolved.startsWith("{")) {
            return fallback
        }
        return resolved
    }

    override fun onDestroy() {
        // mediaSessionManager is only initialized in the else-branch of onStartCommand;
        // guard so destroying an un-started/permission-less service never crashes.
        if (::mediaSessionManager.isInitialized) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(::activeSessionsListener)
            } catch (e: Exception) {
                logger.e(TAG, "removeOnActiveSessionsChangedListener failed: ${e.message}")
            }
        }
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        stopHeartbeat()
        stopMediaRefresh()
        scope.cancel()
        kizzyRPC.closeRPC()
        // Schedule RPC recovery via WorkManager so presence can be restored after
        // aggressive task removal or process termination by the system.
        WorkManagerHelper.scheduleRpcRecovery(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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
}
