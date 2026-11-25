/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ManualRpc.kt is part of Kizzy
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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMediaAll
import com.my.kizzy.data.get_current_data.media.MetadataResolver
import com.my.kizzy.data.get_current_data.media.RichMediaMetadata
import com.my.kizzy.data.rpc.*
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ManualRPC"

@Suppress("DEPRECATION")
@AndroidEntryPoint
class ManualRpc : Service() {

    @Inject
    lateinit var scope: CoroutineScope

    private var kizzyRPC: KizzyRPC? = null

    /**
     * True when the user explicitly stopped the service via the notification "Exit" action.
     * In that case we must NOT schedule RPC recovery, since the user intentionally turned
     * RPC off. Recovery is still scheduled for onTaskRemoved (system killed the app).
     */
    private var explicitStop = false

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var getCurrentPlayingMediaAll: GetCurrentPlayingMediaAll

    @Inject
    lateinit var MetadataResolver: MetadataResolver

    @Inject
    lateinit var getCurrentlyRunningApp: GetCurrentlyRunningApp

    @Inject
    lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var rpcManager: RpcManager

    private lateinit var mediaSessionManager: MediaSessionManager

    /**
     * Single listener instance shared by the add and remove calls.
     *
     * A bound method reference to activeSessionsListener allocates a new
     * wrapper on every evaluation, so the remove call never matches the
     * listener registered by the add call. The framework then retains the
     * binder stub — and this destroyed service through it — leaking the
     * whole service instance after onDestroy().
     */
    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            activeSessionsListener(sessions)
        }

    private var currentMediaController: MediaController? = null

    private val mediaControllerCallback = MediaControllerCallback()

    private suspend fun fetchPlayingMediaWithRetry(): RichMediaMetadata {
        var richMediaData = this.getCurrentPlayingMediaAll.invoke()
        var attempt = 1
        while (richMediaData.appName == null && attempt < maxMediaDetectionAttempts) {
            logger.d(TAG, "fetchPlayingMediaWithRetry attempt $attempt: appName=${richMediaData.appName}, title=${richMediaData.title}, pkg=${richMediaData.packageName} - retrying...")
            delay((mediaDetectionDelayMs * attempt).milliseconds)
            richMediaData = this.getCurrentPlayingMediaAll.invoke()
            attempt++
        }
        logger.d(TAG, "fetchPlayingMediaWithRetry final: appName=${richMediaData.appName}, title=${richMediaData.title}, pkg=${richMediaData.packageName}")
        return richMediaData
    }

    private var isMediaSessionActive = false
    private var lastSentPresenceKey: List<String?>? = null
    private var lastHadPresence: Boolean = false
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0L
    private var isPaused: Boolean = false

    // Periodic refresh to catch position changes that happen between onPlaybackStateChanged
    // callbacks (e.g., during drag scrubbing when the player may not fire callbacks for every
    // position change).
    private var mediaRefreshJob: Job? = null
    private val mediaRefreshIntervalMs = 1000L

    // Heartbeat to prevent stuck idling when media session callbacks are missed
    private val heartbeatJob = MutableStateFlow<Job?>(null)

    // The single app-detection polling loop. Tracked so it is never started twice.
    private var appDetectionJob: Job? = null

    // The pending presence update. Cancelling this one job cancels a superseded update
    // without touching the heartbeat / media refresh / app detection jobs.
    private var presenceUpdateJob: Job? = null

    /**
     * Starts (or restarts) the pending presence update.
     *
     * The previous implementation called `scope.coroutineContext.cancelChildren()`, which
     * cancelled EVERY child of the service scope - including the heartbeat and the media
     * refresh loop. After the first session event the periodic updates were dead, so the
     * presence stopped updating until the app was restarted.
     */
    private fun launchPresenceUpdate(block: suspend () -> Unit) {
        presenceUpdateJob?.cancel()
        presenceUpdateJob = scope.launch { block() }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob.value = scope.launch {
            while (isActive) {
                delay(5000.milliseconds)
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
                delay(mediaRefreshIntervalMs.milliseconds)
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

    private var useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
    private var useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]

    // Media detection retry settings - YouTube Music/LX Music can take 1-3 seconds
    // to populate title metadata after session appears.
    private val mediaDetectionDelayMs = 500L
    private val maxMediaDetectionAttempts = 4

    private var templateName =
        Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
    private var templateDetails =
        Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
    private var templateState =
        Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]

    // Advanced settings (matching Custom RPC / RpcConfig fields)
    // Read fresh each updatePresence() call so manual edits in the UI take effect
    // immediately without restarting the service.
    private var manualActivityType = Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE, ""]
    private var manualStatus = Prefs[Prefs.MANUAL_RPC_STATUS, "online"]
    private var manualLargeText = Prefs[Prefs.MANUAL_RPC_LARGE_TEXT, ""]
    private var manualSmallText = Prefs[Prefs.MANUAL_RPC_SMALL_TEXT, ""]
    private var manualLargeImage = Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE, ""]
    private var manualSmallImage = Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE, ""]
    private var manualStreamUrl = Prefs[Prefs.MANUAL_RPC_STREAM_URL, ""]
    private var manualPartyCurrentSize = Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE, ""]
    private var manualPartyMaxSize = Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE, ""]

    private var enabledExperimentalApps: List<String> = readEnabledExperimentalApps()

    /** Reads the list of apps enabled for the Manual RPC from the preferences. */
    private fun readEnabledExperimentalApps(): List<String> = try {
        Json.decodeFromString(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
    } catch (_: Exception) {
        emptyList()
    }

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wire Manual RPC to slot "3" (third application ID + token)
        kizzyRPC = multiRpc.session("3")

        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            // Explicit stop: the next cold start must not bring it back.
            explicitStop = true
            Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
            // Remove ManualRpc from ACTIVE_RPC_TYPES so a cold start doesn't restore it.
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.ManualRpc }
                .joinToString(",") { it.name }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
            // Also clear RpcManager's active set so refreshFromServices() doesn't
            // resurrect this service when the screen resumes.
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.ManualRpc))
            stopSelf()
        } else if (intent?.action == Constants.ACTION_RESTART_SERVICE) {
            stopSelf()
            startService(Intent(this, ManualRpc::class.java))
        } else {
            Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.ManualRpc.name
            val stopIntent = Intent(this, ManualRpc::class.java)
            stopIntent.action = Constants.ACTION_STOP_SERVICE
            val pendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val restartIntent = Intent(this, ManualRpc::class.java)
            restartIntent.action = Constants.ACTION_RESTART_SERVICE
            val restartPendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Open main app when notification is clicked. The intent reuses the
            // existing task, otherwise the app is started a second time and the
            // user sees two layers of the UI on top of each other.
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }


            mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            // Registering the session listener requires Notification Listener access.
            // Without it this throws SecurityException, so guard to avoid crashing.
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    activeSessionsChangedListener,
                    ComponentName(this, NotificationListener::class.java)
                )
            } catch (e: SecurityException) {
                logger.e(TAG, "No notification access, media part disabled: ${e.message}")
            }

            // Always reload settings on start
            // Move DataStore reads off main thread to avoid StrictMode DiskReadViolation
            scope.launch {
                withContext(Dispatchers.IO) {
                    templateName = Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
                    templateDetails = Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
                    templateState = Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]
                    useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
                    useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]
                    enabledExperimentalApps = readEnabledExperimentalApps()
                    // Advanced settings (matching Custom RPC / RpcConfig fields)
                    manualActivityType = Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE, ""]
                    manualStatus = Prefs[Prefs.MANUAL_RPC_STATUS, "online"]
                    manualLargeImage = Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE, ""]
                    manualSmallImage = Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE, ""]
                    manualLargeText = Prefs[Prefs.MANUAL_RPC_LARGE_TEXT, ""]
                    manualSmallText = Prefs[Prefs.MANUAL_RPC_SMALL_TEXT, ""]
                    manualStreamUrl = Prefs[Prefs.MANUAL_RPC_STREAM_URL, ""]
                    manualPartyCurrentSize = Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE, ""]
                    manualPartyMaxSize = Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE, ""]
                }
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
                // Use the same rule as the media detection itself, so the controller that
                // is tracked always matches the session that is broadcast.
                val firstActiveMediaController = initialMediaSessions.firstOrNull {
                    enabledExperimentalApps.contains(it.packageName) ||
                        Prefs.isMediaAppEnabled(it.packageName)
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
            kizzyRPC?.build(priority = 1) // Manual RPC: highest priority
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

                    if (currentApp.name.isNotEmpty() && currentApp.packageName != currentPackageName) {
                        val isGameApp = dynamicActivityDetectionEngine.identifyGame(currentApp.packageName) != null
                        val isAiApp = dynamicActivityDetectionEngine.identifyAiApp(currentApp.packageName) != null
                        val shouldShow = isGameApp || isAiApp || enabledExperimentalApps.contains(currentApp.packageName)
                        if (shouldShow) {
                            currentPackageName = currentApp.packageName
                            val displayName = if (isAiApp) {
                                dynamicActivityDetectionEngine.identifyAiApp(currentApp.packageName)?.displayName
                            } else if (isGameApp) {
                                dynamicActivityDetectionEngine.identifyGame(currentApp.packageName)?.displayName
                            } else null
                            val appInfo = currentApp.copy(
                                name = displayName?.takeIf { it.isNotBlank() } ?: currentApp.name,
                                time = startTimestamps
                            )
                            updatePresence(appInfo = appInfo)
                        } else if (!isMediaSessionActive || !useMediaRpc) {
                            currentPackageName = ""
                            updatePresence(CommonRpc())
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Detection cycle failed: ${e.message}")
                }
                delay(2000.milliseconds)
            }
        }
    }

    private suspend fun updatePresence(
        appInfo: CommonRpc? = null,
        richMediaInfo: RichMediaMetadata? = null,
        rawMediaMetadata: MediaMetadata? = null,
    ) {
        // If no media data at all, clear Discord presence if we had one before.
        // When media loads later, MediaControllerCallback will trigger updatePresence
        // with real data.
        if (richMediaInfo == null && appInfo == null) {
            if (lastHadPresence) {
                logger.d(TAG, "No media data available - clearing Discord presence")
                scope.launch { kizzyRPC?.clearPresence() }
                lastHadPresence = false
            }
            return
        }

        val manualActivityType = Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE, ""]
        val manualStatus = Prefs[Prefs.MANUAL_RPC_STATUS, "online"]
        val manualLargeImageStr = Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE, ""]
        val manualSmallImageStr = Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE, ""]
        val manualLargeTextStr = Prefs[Prefs.MANUAL_RPC_LARGE_TEXT, ""]
        val manualSmallTextStr = Prefs[Prefs.MANUAL_RPC_SMALL_TEXT, ""]
        val manualStreamUrl = Prefs[Prefs.MANUAL_RPC_STREAM_URL, ""]
        val manualPartyCurrentSize = Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE, ""]
        val manualPartyMaxSize = Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE, ""]
        val templateName = Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
        val templateDetails = Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
        val templateState = Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]
        val useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
        val useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]

        val finalName: String?
        val finalDetails: String?
        val finalState: String?
        var finalLargeImage: RpcImage?
        var finalSmallImage: RpcImage?
        var finalSmallImageChannelUrl: String? = null
        var finalLargeText: String?
        var finalSmallText: String?
        var finalTimestamps: Timestamps?
        var effectivePackageName: String?

        // Channel icon for small image when enabled
        val showChannelIcon = Prefs[Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON, false]

        // Hide media on pause if enabled
        if (richMediaInfo != null &&
            Prefs[Prefs.MANUAL_RPC_HIDE_ON_PAUSE, false] &&
            (richMediaInfo.playbackState == PlaybackState.STATE_PAUSED ||
                    richMediaInfo.playbackState == PlaybackState.STATE_STOPPED)
        ) {
            if (useAppsRpc) {
                startAppDetectionCoroutine()
            } else if (kizzyRPC?.isRpcRunning() == true) {
                // Clear the activity but keep the gateway connection alive, so playback can
                // resume instantly instead of having to reconnect from scratch.
                kizzyRPC?.clearPresence()
                lastHadPresence = false
                val stopIntent = Intent(this, ManualRpc::class.java)
                stopIntent.action = Constants.ACTION_STOP_SERVICE
                val stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
                )
                val restartIntent = Intent(this, ManualRpc::class.java)
                restartIntent.action = Constants.ACTION_RESTART_SERVICE
                val restartPendingIntent = PendingIntent.getService(
                    this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
                notificationManager.notify(
                    Constants.NOTIFICATION_ID_MANUAL_RPC,
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
        
        // Global toggle: if user disabled cover art entirely, hide cover art from all RPCs
        val showCoverArtGlobal = Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false

        val processor = TemplateProcessor(
            mediaMetadata = rawMediaMetadata,
            mediaPlayerAppName = richMediaInfo?.appName,
            mediaPlayerPackageName = richMediaInfo?.packageName,
            detectedAppInfo = appInfo,
            batteryLevel = currentBatteryLevel(),
            playbackState = richMediaInfo?.playbackState
        )

        if (currentContextIsMedia) {
            effectivePackageName = richMediaInfo.packageName

            currentMediaProgressSec = (richMediaInfo.position / 1000).toInt()
                .coerceIn(0, (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0))
            currentMediaDurationSec = (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0)
            currentMediaPositionMs = richMediaInfo.position.coerceAtLeast(0L)
            isPaused = richMediaInfo.playbackState != PlaybackState.STATE_PLAYING &&
                    richMediaInfo.playbackState != PlaybackState.STATE_BUFFERING &&
                    richMediaInfo.playbackState != PlaybackState.STATE_CONNECTING

            logger.d(TAG, "Processing Rich Media Context")
            if (Prefs[Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE, false]) {
                val titleSource = Prefs[Prefs.MANUAL_RPC_TITLE_SOURCE, "title"]
                finalName = if (titleSource == "artist") richMediaInfo.artist else richMediaInfo.title
                finalDetails = resolveTemplateValue(
                    processor.process(templateDetails),
                    richMediaInfo.appName
                )
                finalState = resolveTemplateValue(
                    processor.process(templateState),
                    richMediaInfo.state.takeIf { titleSource != "artist" }
                )
            } else {
                finalName = resolveTemplateValue(
                    processor.process(templateName),
                    richMediaInfo.appName
                )
                finalDetails = resolveTemplateValue(
                    processor.process(templateDetails),
                    richMediaInfo.title
                )
                finalState = resolveTemplateValue(
                    processor.process(templateState),
                    richMediaInfo.state
                )
            }

            finalLargeImage = when {
                Prefs[Prefs.MANUAL_RPC_SHOW_COVER_ART, true] &&
                Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false ->
                    // Fall back to the player's app icon when cover art is unavailable
                    richMediaInfo.coverArt ?: richMediaInfo.appIcon
                Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON, false] &&
                Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false -> richMediaInfo.appIcon
                else -> null
            }

            finalSmallImage = when {
                // 1. Channel icon (highest priority)
                Prefs[Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON, false] &&
                Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false ->
                    // Resolve channel avatar from channel URL (more reliable than
                    // scraping the watch page, which may match unrelated avatars)
                    richMediaInfo.channelUrl?.let { channelUrl ->
                        val avatarUrl = YouTubeResolver.lookupChannelAvatarByChannelUrl(channelUrl)
                        if (avatarUrl != null) {
                            finalSmallImageChannelUrl = richMediaInfo.channelUrl
                            RpcImage.ExternalImage(avatarUrl)
                        } else null
                    } ?: richMediaInfo.mediaUrl?.let { url ->
                        // Fall back to watch page scraping when no channel URL available
                        val videoId = YouTubeResolver.extractVideoId(url)
                        if (videoId != null) {
                            val avatarUrl = YouTubeResolver.lookupChannelAvatar(videoId)
                            if (avatarUrl != null) {
                                finalSmallImageChannelUrl = richMediaInfo.channelUrl
                                RpcImage.ExternalImage(avatarUrl)
                            } else null
                        } else null
                    }

                // 2. Playback state icon
                Prefs[Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE, true] ->
                    richMediaInfo.playbackStateIcon

                // 3. App icon
                Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON, false] &&
                Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false && finalLargeImage != richMediaInfo.appIcon ->
                    richMediaInfo.appIcon
                else -> null
            }

            // Line 4: album name, or the playlist name when there is no album.
            finalLargeText = richMediaInfo.album ?: richMediaInfo.playlistName
            finalSmallText =
                if (finalSmallImage == richMediaInfo.appIcon) richMediaInfo.appName else null

            finalTimestamps = if (richMediaInfo.playbackState == PlaybackState.STATE_PLAYING) {
                resolveTimestamps(richMediaInfo.timestamps)
            } else {
                // Keep timestamps frozen when paused or stopped so Discord
                // doesn't reset the progress bar.
                resolveTimestamps(richMediaInfo.timestamps)
            }

        } else if (currentContextIsApp) {
            effectivePackageName = appInfo.packageName
            logger.d(TAG, "Processing App Context")
            finalName = resolveTemplateValue(processor.process(templateName), appInfo.name)
            finalDetails = resolveTemplateValue(processor.process(templateDetails), appInfo.details)
            finalState = resolveTemplateValue(processor.process(templateState), appInfo.state)

            finalLargeImage = appInfo.largeImage
            finalSmallImage = appInfo.smallImage
            finalLargeText = appInfo.largeText
            finalSmallText = appInfo.smallText
            finalTimestamps = resolveTimestamps(appInfo.time)
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
            if (kizzyRPC?.isRpcRunning() == true) {
                // Clear the activity but keep the connection, so the next track shows up
                // immediately.
                kizzyRPC?.clearPresence()
                lastHadPresence = false
                val stopIntent = Intent(this, ManualRpc::class.java)
                stopIntent.action = Constants.ACTION_STOP_SERVICE
                val stopPendingIntent = PendingIntent.getService(
                    this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
                )
                val restartIntent = Intent(this, ManualRpc::class.java)
                restartIntent.action = Constants.ACTION_RESTART_SERVICE
                val restartPendingIntent = PendingIntent.getService(
                    this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
                )
                notificationManager.notify(
                    Constants.NOTIFICATION_ID_MANUAL_RPC,
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

        // Primary media link priority chain: mediaUrl -> albumUrl -> channelUrl.
        // Links are independent of the cover-art toggles.
        val mediaUrl = rawMediaMetadata?.let {
            MetadataResolver.getDirectMediaUrl(it, richMediaInfo?.packageName)
                ?: MetadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
        } ?: richMediaInfo?.mediaUrl
        val songLink = YouTubeResolver.primaryMediaUrl(
            mediaUrl,
            richMediaInfo?.albumUrl,
            richMediaInfo?.channelUrl ?: richMediaInfo?.artistUrl,
        )
        // details line -> media URL (song/track), with a search fallback so the
        // line stays clickable even when no direct URL was published.
        val detailsUrl = songLink
            ?: MediaServiceResolver.mediaSearchUrl(
                richMediaInfo?.packageName ?: effectivePackageName ?: "",
                richMediaInfo?.title ?: finalDetails ?: finalName ?: "",
            )
            ?: MediaServiceResolver.browserSearchUrl(
                richMediaInfo?.title ?: finalDetails ?: finalName ?: "",
            )

        // state line -> artist/channel URL, with a service-level fallback.
        val stateUrl = richMediaInfo?.artistUrl
            ?: MediaServiceResolver.artistUrl(
                richMediaInfo?.packageName ?: effectivePackageName,
                richMediaInfo?.artist ?: richMediaInfo?.state ?: "",
            )

        // large image (cover art) -> album/playlist URL first, then video URL.
        val largeUrl = YouTubeResolver.largeImageUrl(
            mediaUrl,
            richMediaInfo?.albumUrl,
            richMediaInfo?.playlistUrl,
        )

        // small image (app icon) -> app download page (GitHub release first priority)
        val smallUrl = MediaServiceResolver.appDownloadUrl(richMediaInfo?.packageName)

        // If no media/album/playlist URL was resolved, fall back to the app
        // download page so the cover art is always clickable when an image
        // is shown.
        val finalLargeUrl = largeUrl ?: smallUrl

        // Apply custom large/small image overrides if specified by the user
        if (manualLargeImageStr.isNotBlank()) {
            finalLargeImage = manualLargeImageStr.toRpcImage()
        }
        if (manualSmallImageStr.isNotBlank()) {
            finalSmallImage = manualSmallImageStr.toRpcImage()
        }

        // Build the CommonRpc once - this is what we want to send to Discord
        val commonRpc = CommonRpc(
            name = finalName ?: "",
            type = if (manualActivityType.isNotEmpty()) manualActivityType.toIntOrNull()
                ?: ActivityTypeResolver.resolve(
                    packageName = effectivePackageName,
                    isMusicMetadata = richMediaInfo?.isMusicMetadata == true,
                    fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                    useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                )
                else ActivityTypeResolver.resolve(
                    packageName = effectivePackageName,
                    isMusicMetadata = richMediaInfo?.isMusicMetadata == true,
                    fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                    useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                ),
            details = finalDetails,
            state = finalState,
            detailsUrl = detailsUrl,
            stateUrl = stateUrl,
            largeImage = finalLargeImage,
            smallImage = finalSmallImage,
            smallImageChannelUrl = finalSmallImageChannelUrl,
            largeUrl = finalLargeUrl,
            largeText = manualLargeTextStr.takeIf { it.isNotEmpty() } ?: finalLargeText,
            smallText = manualSmallTextStr.takeIf { it.isNotEmpty() } ?: finalSmallText,
            time = finalTimestamps,
            packageName = effectivePackageName ?: "",
            playbackState = richMediaInfo?.playbackState,
            mediaUrl = rawMediaMetadata?.let {
                MetadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
            },
            // Party size as Int? (CommonRpc uses Int?, not String?)
            partyCurrentSize = manualPartyCurrentSize.toIntOrNull(),
            partyMaxSize = manualPartyMaxSize.toIntOrNull(),
        )

        // De-duplication: skip if nothing changed since the last push.
        //
        // CommonRpc cannot be compared directly because RpcImage has no equals()
        // implementation, so two logically identical presences would never match and the
        // update would be pushed on every single cycle. Compare the meaningful fields
        // instead.
        val presenceKey = listOf(
            finalName,
            finalDetails,
            finalState,
            manualLargeTextStr.takeIf { it.isNotEmpty() } ?: finalLargeText,
            manualSmallTextStr.takeIf { it.isNotEmpty() } ?: finalSmallText,
            finalLargeImage?.toUrlString() ?: finalLargeImage?.toString(),
            finalSmallImage?.toUrlString() ?: finalSmallImage?.toString(),
            finalTimestamps?.start?.toString(),
            finalTimestamps?.end?.toString(),
            effectivePackageName,
            richMediaInfo?.playbackState?.toString(),
            manualActivityType,
            manualStatus,
            manualLargeImageStr,
            manualSmallImageStr,
            manualStreamUrl,
            manualPartyCurrentSize,
            manualPartyMaxSize,
            Prefs[Prefs.MANUAL_RPC_USE_BUTTONS, false].toString(),
            Prefs[Prefs.MANUAL_RPC_BUTTON1_TEXT, ""],
            Prefs[Prefs.MANUAL_RPC_BUTTON1_URL, ""],
            Prefs[Prefs.MANUAL_RPC_BUTTON2_TEXT, ""],
            Prefs[Prefs.MANUAL_RPC_BUTTON2_URL, ""],
            Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false].toString(),
            Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""],
        )
        if (presenceKey == lastSentPresenceKey) {
            logger.d(TAG, "Presence unchanged, skipping redundant update")
            return
        }
        lastSentPresenceKey = presenceKey

        // Apply the buttons and the custom application id that the user configured in the
        // Manual RPC screen. They were stored in the preferences but never actually
        // applied to the presence.
        kizzyRPC?.clearButtons()
        if (Prefs[Prefs.MANUAL_RPC_USE_BUTTONS, false]) {
            val button1Text = Prefs[Prefs.MANUAL_RPC_BUTTON1_TEXT, ""]
            val button1Url = Prefs[Prefs.MANUAL_RPC_BUTTON1_URL, ""]
            val button2Text = Prefs[Prefs.MANUAL_RPC_BUTTON2_TEXT, ""]
            val button2Url = Prefs[Prefs.MANUAL_RPC_BUTTON2_URL, ""]
            if (button1Text.isNotEmpty() && button1Url.isNotEmpty()) {
                kizzyRPC?.setButton1(button1Text, button1Url)
            }
            if (button2Text.isNotEmpty() && button2Url.isNotEmpty()) {
                kizzyRPC?.setButton2(button2Text, button2Url)
            }
        } else {
            // The Manual RPC did not have any link at all when the user did
            // not configure the buttons by hand. When a media session is
            // playing, the link of the track and of the artist are used instead,
            // so the presence links to the music like the Media RPC does.
            val buttonMediaUrl = rawMediaMetadata?.let {
                MetadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
            } ?: MediaServiceResolver.mediaSearchUrl(
                effectivePackageName,
                finalName.takeIf { !it.isNullOrBlank() } ?: finalDetails,
            ) ?: MediaServiceResolver.browserSearchUrl(
                finalName.takeIf { !it.isNullOrBlank() } ?: finalDetails,
            )
            buttonMediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
                kizzyRPC?.setButton1(
                    MediaServiceResolver.mediaLinkLabel(
                        url,
                        effectivePackageName,
                        commonRpc.type ?: ActivityTypeResolver.DEFAULT_TYPE,
                    ),
                    url,
                )
            }
            rawMediaMetadata?.let { metadata ->
                MetadataResolver.getArtistUrl(metadata, richMediaInfo?.packageName)?.let { url ->
                    kizzyRPC?.setButton2(
                        MediaServiceResolver.artistButtonLabel(effectivePackageName),
                        url,
                    )
                }
            }
        }
        if (Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false]) {
            kizzyRPC?.setApplicationId(Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""])
        }
        if (manualStreamUrl.isNotBlank()) {
            kizzyRPC?.setStreamUrl(manualStreamUrl)
        }
        kizzyRPC?.setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "online"])

        kizzyRPC?.updateRPC(
            commonRpc = commonRpc,
            enableTimestamps = Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true],
            status = manualStatus.takeIf { it.isNotEmpty() } ?: "online",
            forceRefresh = true,
            priority = 1, // Manual RPC: highest priority - user explicitly configured
        )
        lastHadPresence = true

        // Remember the presence for the RPC preview.
        // The image urls are the ones that were actually resolved (and uploaded) for the
        // presence above. Without them the preview of the Manual RPC had no cover
        // art, even though Discord showed it.
        val (resolvedLargeImage, resolvedSmallImage) = kizzyRPC?.currentImageUrls()
            ?: (null to null)
        val rpcConfig = RpcConfig(
            name = finalName.orEmpty(),
            details = finalDetails.orEmpty(),
            state = finalState.orEmpty(),
            type = (commonRpc.type ?: 0).toString(),
            timestampsStart = finalTimestamps?.start?.toString().orEmpty(),
            timestampsStop = finalTimestamps?.end?.toString().orEmpty(),
            largeText = finalLargeText.orEmpty(),
            smallText = finalSmallText.orEmpty(),
            detailsUrl = commonRpc.detailsUrl.orEmpty(),
            stateUrl = commonRpc.stateUrl.orEmpty(),
            largeUrl = commonRpc.largeUrl.orEmpty(),
            smallUrl = commonRpc.smallUrl.orEmpty(),
            largeImg = resolvedLargeImage.orEmpty(),
            smallImg = resolvedSmallImage.orEmpty(),
            button1 = kizzyRPC?.currentButtons()?.getOrNull(0)?.label.orEmpty(),
            button1link = kizzyRPC?.currentButtons()?.getOrNull(0)?.url.orEmpty(),
            button2 = kizzyRPC?.currentButtons()?.getOrNull(1)?.label.orEmpty(),
            button2link = kizzyRPC?.currentButtons()?.getOrNull(1)?.url.orEmpty(),
            // Advanced settings from Manual RPC UI
            status = manualStatus.takeIf { it.isNotEmpty() } ?: "online",
            url = manualStreamUrl.takeIf { it.isNotEmpty() } ?: "",
            partyCurrentSize = manualPartyCurrentSize.takeIf { it.isNotEmpty() } ?: "",
            partyMaxSize = manualPartyMaxSize.takeIf { it.isNotEmpty() } ?: "",
        )
        LastPresence.rememberExperimental(rpcConfig)
        // Expose the current presence so the Manual RPC screen can offer
        // a "Copy to Custom RPC" button that pre-fills the Custom RPC editor.
        rpcManager.setCurrentPresenceConfig(rpcConfig, RpcManager.RpcType.ManualRpc)

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

        val stopIntent = Intent(this, ManualRpc::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val restartIntent = Intent(this, ManualRpc::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        val restartPendingIntent = PendingIntent.getService(
            this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )
        notificationManager.notify(
            Constants.NOTIFICATION_ID_MANUAL_RPC,
            Notification.Builder(this, Constants.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dev_rpc)
                .setContentTitle(notifTitle)
                .setContentText(notifText)
                .setLargeIcon(finalLargeImage, this@ManualRpc)
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
            logger.i(TAG, "Media part of Manual RPC is disabled.")
            if (useAppsRpc && !isMediaSessionActive) {
                startAppDetectionCoroutine()
            } else if (!useAppsRpc) {
                launchPresenceUpdate {
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
            // Try to register callback on a matching session for playback state updates.
            // Use the same rule as the media detection so the tracked controller always
            // matches the session that is broadcast.
            currentMediaController = mediaSessions.firstOrNull {
                enabledExperimentalApps.contains(it.packageName) ||
                    Prefs.isMediaAppEnabled(it.packageName)
            }
            currentMediaController?.registerCallback(mediaControllerCallback)
            isMediaSessionActive = currentMediaController != null
        } else {
            isMediaSessionActive = false
        }

        launchPresenceUpdate {
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

            launchPresenceUpdate {
                delay(200.milliseconds)
                val richMediaData = this@ManualRpc.getCurrentPlayingMediaAll.invoke()
                val hasMediaData = richMediaData.appName != null

                if (hasMediaData && useMediaRpc) {
                    // Re-acquire controller from fresh session list so metadata matches the
                    // current song (not the old paused session).
                    val freshController = try {
                        mediaSessionManager
                            .getActiveSessions(ComponentName(this@ManualRpc, NotificationListener::class.java))
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

            launchPresenceUpdate {
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
        if (!Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true]) return null
        return when (Prefs[Prefs.MANUAL_RPC_TIMESTAMP_MODE, "default"]) {
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
                val start = Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_START, ""].toLongOrNull()
                val end = Prefs[Prefs.MANUAL_RPC_TIMESTAMP_CUSTOM_END, ""].toLongOrNull()
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
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            } catch (e: Exception) {
                logger.e(TAG, "removeOnActiveSessionsChangedListener failed: ${e.message}")
            }
        }
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        stopHeartbeat()
        stopMediaRefresh()
        // Cancel pending work first, then clear/close off the main thread.
        scope.cancel()
        val rpc = kizzyRPC
        AppUtils.unregisterRpcService(this)
        if (rpc != null) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
        // Schedule RPC recovery only when the system killed the app (onTaskRemoved)
        // or crashed it, not when stopRpcTypes() already removed ManualRpc from
        // RpcManager.activeRpc.value (explicit user stop).
        val manualStillEnabled = RpcManager.RpcType.ManualRpc in rpcManager.activeRpc.value
        if (!explicitStop && manualStillEnabled) {
            WorkManagerHelper.scheduleRpcRecovery(this)
        }
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