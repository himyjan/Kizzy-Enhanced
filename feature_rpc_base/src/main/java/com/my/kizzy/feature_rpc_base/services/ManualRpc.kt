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
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.DynamicActivityDetectionEngine
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.LastPresence
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.TemplateKeys
import com.my.kizzy.data.rpc.TemplateProcessor
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RPC_INSTANCE_MANUAL
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ManualRPC"
private const val MIN_RPC_INTERVAL_MS = 500L
private const val MAX_RPC_IDLE_INTERVAL_MS = 14_500L
private const val NOTIFICATION_MIN_INTERVAL_MS = 1_000L
private const val MEDIA_RETRY_BASE_DELAY_MS = 500L
private const val MAX_MEDIA_RETRY_ATTEMPTS = 4
private const val APP_DETECTION_INTERVAL_MS = 2_000L

@Suppress("DEPRECATION")
@AndroidEntryPoint
class ManualRpc : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var kizzyRPC: KizzyRPC? = null
    private var lastNotificationUpdateMs: Long = 0L
    private var currentInstanceId: String = RPC_INSTANCE_MANUAL
    private val manualCloneInstanceIds = mutableSetOf<String>()
    private var explicitStop = false

    @Inject lateinit var multiRpc: MultiRpcManager
    @Inject lateinit var getCurrentPlayingMediaAll: GetCurrentPlayingMediaAll
    @Inject lateinit var metadataResolver: MetadataResolver
    @Inject lateinit var getCurrentlyRunningApp: GetCurrentlyRunningApp
    @Inject lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine
    @Inject lateinit var logger: Logger
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var notificationBuilder: Notification.Builder
    @Inject lateinit var rpcManager: RpcManager

    private lateinit var mediaSessionManager: MediaSessionManager

    private val activeSessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            activeSessionsListener(sessions)
        }

    private var currentMediaController: MediaController? = null
    private val mediaControllerCallback = MediaControllerCallback()

    private var isMediaSessionActive = false
    private var lastSentPresenceKey: List<String?>? = null
    private var lastHadPresence: Boolean = false
    private var currentMediaProgressSec: Int = 0
    private var currentMediaDurationSec: Int = 0
    private var currentMediaPositionMs: Long = 0L
    private var isPaused: Boolean = false

    private val rpcPacer = RpcPacer(
        minIntervalMs = MIN_RPC_INTERVAL_MS,
        maxIdleIntervalMs = MAX_RPC_IDLE_INTERVAL_MS,
    )

    private var detectedAppPackage: String? = null
    private var detectedAppStartMs: Long? = null

    private var periodicRefreshJob: Job? = null
    private var appDetectionJob: Job? = null
    private var presenceUpdateJob: Job? = null

    private var useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
    private var useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]
    private var enabledExperimentalApps: List<String> = readEnabledExperimentalApps()

    private fun readEnabledExperimentalApps(): List<String> = try {
        Json.decodeFromString(Prefs[Prefs.ENABLED_EXPERIMENTAL_APPS, "[]"])
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun refreshCachedPrefs() {
        val apps = withContext(Dispatchers.IO) { readEnabledExperimentalApps() }
        useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
        useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]
        enabledExperimentalApps = apps
    }

    private fun resolveAppStartTimestamp(packageName: String): Long {
        if (detectedAppPackage != packageName || detectedAppStartMs == null) {
            detectedAppPackage = packageName
            detectedAppStartMs = System.currentTimeMillis()
        }
        return detectedAppStartMs!!
    }

    private fun clearDetectedAppTimestamp() {
        detectedAppPackage = null
        detectedAppStartMs = null
    }

    private fun launchPresenceUpdate(block: suspend () -> Unit) {
        presenceUpdateJob?.cancel()
        presenceUpdateJob = serviceScope.launch { block() }
    }

    private fun startPeriodicRefresh() {
        if (periodicRefreshJob?.isActive == true) return
        periodicRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(MAX_RPC_IDLE_INTERVAL_MS)
                try {
                    val richMediaData = getCurrentPlayingMediaAll.invoke()
                    if (richMediaData.appName != null) {
                        updatePresence(
                            richMediaInfo = richMediaData,
                            rawMediaMetadata = null,
                            forceRefresh = false,
                        )
                    } else if (lastHadPresence && kizzyRPC?.isRpcRunning() == true) {
                        kizzyRPC?.build(forceRefresh = false, priority = 1)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Periodic refresh failed: ${e.message}")
                }
            }
        }
    }

    private fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    private fun buildServiceNotification(
        title: String,
        contentText: String,
        largeImage: RpcImage? = null,
        progressMaxSec: Int = 0,
        progressSec: Int = 0,
    ): Notification {
        val stopIntent = Intent(this, ManualRpc::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val restartIntent = Intent(this, ManualRpc::class.java).apply {
            action = Constants.ACTION_RESTART_SERVICE
        }
        val restartPendingIntent = PendingIntent.getService(
            this, 0, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_manual_rpc)
            .setContentTitle(title)
            .setContentText(contentText)
            .apply {
                if (largeImage != null) {
                    setLargeIcon(largeImage, this@ManualRpc)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && progressMaxSec > 0) {
                    setProgress(progressMaxSec, progressSec, false)
                }
            }
            .addAction(R.drawable.ic_manual_rpc, getString(R.string.restart), restartPendingIntent)
            .addAction(R.drawable.ic_manual_rpc, getString(R.string.exit), stopPendingIntent)
            .build()
    }

    private fun notifyIdleIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs < NOTIFICATION_MIN_INTERVAL_MS) return
        lastNotificationUpdateMs = now

        notificationManager.notify(
            Constants.NOTIFICATION_ID_MANUAL_RPC,
            buildServiceNotification(
                title = getString(R.string.service_enabled),
                contentText = getString(R.string.idling_notification),
            )
        )
    }

    private suspend fun fetchPlayingMediaWithRetry(): RichMediaMetadata {
        var richMediaData = getCurrentPlayingMediaAll.invoke()
        var attempt = 1
        while (richMediaData.appName == null && attempt < MAX_MEDIA_RETRY_ATTEMPTS) {
            logger.d(
                TAG,
                "fetchPlayingMediaWithRetry attempt $attempt: appName=${richMediaData.appName}, title=${richMediaData.title}, pkg=${richMediaData.packageName}"
            )
            delay((MEDIA_RETRY_BASE_DELAY_MS * attempt).milliseconds)
            richMediaData = getCurrentPlayingMediaAll.invoke()
            attempt++
        }
        return richMediaData
    }

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        currentInstanceId = if (action == Constants.ACTION_ADD_INSTANCE) {
            intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                ?.takeIf { it.isNotBlank() } ?: RPC_INSTANCE_MANUAL
        } else {
            RPC_INSTANCE_MANUAL
        }

        kizzyRPC = if (action == Constants.ACTION_ADD_INSTANCE && currentInstanceId != RPC_INSTANCE_MANUAL) {
            multiRpc.customSession(currentInstanceId)
        } else {
            multiRpc.session("3")
        }

        if (action == Constants.ACTION_REMOVE_INSTANCE) {
            val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
            if (!instanceId.isNullOrBlank()) {
                multiRpc.removeInstance("3", instanceId)
                manualCloneInstanceIds.remove(instanceId)
                CloneNotificationHelper.cancel(notificationManager, instanceId)
                rpcManager.removePresenceConfig(instanceId)
            }
            if (multiRpc.instanceCount("3") == 0) {
                rpcManager.removePresenceConfig(currentInstanceId)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (action == Constants.ACTION_STOP_SERVICE) {
            explicitStop = true
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.ManualRpc }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes.joinToString(",") { it.name }
            if (Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.ManualRpc.name) {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = remainingTypes.firstOrNull()?.name ?: ""
            }
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.ManualRpc))
            stopSelf()
            return START_NOT_STICKY
        }

        rpcPacer.reset()
        if (action == Constants.ACTION_RESTART_SERVICE) {
            lastSentPresenceKey = null
        }

        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.ManualRpc.name

        val foregroundNotification = buildServiceNotification(
            title = getString(R.string.service_enabled),
            contentText = getString(R.string.rich_presence_active),
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_ID_MANUAL_RPC, foregroundNotification)
        } else {
            startForeground(
                Constants.NOTIFICATION_ID_MANUAL_RPC,
                foregroundNotification,
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }

        if (action == Constants.ACTION_ADD_INSTANCE) {
            val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
            if (!instanceId.isNullOrBlank()) {
                currentInstanceId = instanceId
                manualCloneInstanceIds.add(instanceId)
                val oauthIndex = intent.getIntExtra(Constants.EXTRA_OAUTH_INDEX, 1)
                multiRpc.addInstance("3", instanceId, oauthIndex)
                if (instanceId != RPC_INSTANCE_MANUAL) {
                    CloneNotificationHelper.show(
                        context = this@ManualRpc,
                        notificationManager = notificationManager,
                        serviceClass = ManualRpc::class.java,
                        instanceId = instanceId,
                        label = Prefs.getHomeRpcInstances().firstOrNull { it.id == instanceId }?.label
                            ?: getString(R.string.main_ManualRpc),
                        content = getString(R.string.rich_presence_active),
                        iconRes = R.drawable.ic_manual_rpc,
                    )
                }
            }
        }

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsChangedListener,
                ComponentName(this, NotificationListener::class.java)
            )
        } catch (e: SecurityException) {
            logger.e(TAG, "No notification access, media part disabled: ${e.message}")
        }

        serviceScope.launch {
            refreshCachedPrefs()

            val initialMediaSessions = try {
                mediaSessionManager.getActiveSessions(
                    ComponentName(this@ManualRpc, NotificationListener::class.java)
                )
            } catch (e: SecurityException) {
                logger.e(TAG, "No notification access, skipping media sessions: ${e.message}")
                emptyList()
            }

            var mediaActiveInitially = false
            if (useMediaRpc && initialMediaSessions.isNotEmpty()) {
                val firstActiveMediaController = initialMediaSessions.firstOrNull {
                    enabledExperimentalApps.contains(it.packageName) ||
                        Prefs.isMediaAppEnabled(it.packageName)
                }
                if (firstActiveMediaController != null) {
                    mediaActiveInitially = true
                    activeSessionsListener(listOf(firstActiveMediaController), isEvent = false)
                }
            }

            if (!mediaActiveInitially) {
                var foundMedia = false
                if (useMediaRpc) {
                    val directMedia = fetchPlayingMediaWithRetry()
                    if (directMedia.appName != null) {
                        logger.d(TAG, "Direct media detection found: ${directMedia.title} (${directMedia.packageName})")
                        updatePresence(
                            richMediaInfo = directMedia,
                            rawMediaMetadata = null,
                            forceRefresh = true,
                        )
                        foundMedia = true
                    }
                }
                if (!foundMedia && useAppsRpc) {
                    startAppDetectionCoroutine()
                }
            }

            startPeriodicRefresh()
            kizzyRPC?.build(priority = 1)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(intent: Intent?) {
        super.onTaskRemoved(intent)
        logger.d(TAG, "onTaskRemoved: user swiped app from Recents")
        WorkManagerHelper.scheduleRpcRecovery(this)
    }

    private fun startAppDetectionCoroutine() {
        if (appDetectionJob?.isActive == true) return

        appDetectionJob = serviceScope.launch {
            var currentPackageName = ""
            while (isActive) {
                try {
                    val updatedApps = withContext(Dispatchers.IO) { readEnabledExperimentalApps() }
                    enabledExperimentalApps = updatedApps
                    useAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
                    useMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]

                    if (!useAppsRpc) {
                        delay(APP_DETECTION_INTERVAL_MS.milliseconds)
                        continue
                    }

                    val currentApp = getCurrentlyRunningApp()
                    if (currentApp.name.isNotEmpty() && currentApp.packageName != currentPackageName) {
                        val aiApp = dynamicActivityDetectionEngine.identifyAiApp(currentApp.packageName)
                        val gameApp = dynamicActivityDetectionEngine.identifyGame(currentApp.packageName)
                        val shouldShow = aiApp != null || gameApp != null || updatedApps.contains(currentApp.packageName)
                        if (shouldShow) {
                            currentPackageName = currentApp.packageName
                            val displayName = (aiApp ?: gameApp)?.displayName
                            val startMs = resolveAppStartTimestamp(currentApp.packageName)
                            val appInfo = currentApp.copy(
                                name = displayName?.takeIf { it.isNotBlank() } ?: currentApp.name,
                                time = Timestamps(start = startMs)
                            )
                            updatePresence(appInfo = appInfo, forceRefresh = true)
                        } else if (!isMediaSessionActive || !useMediaRpc) {
                            if (currentPackageName.isNotEmpty()) {
                                currentPackageName = ""
                                clearDetectedAppTimestamp()
                                updatePresence(CommonRpc(), forceRefresh = true)
                            }
                        }
                    } else if (currentApp.name.isEmpty() && currentPackageName.isNotEmpty()) {
                        currentPackageName = ""
                        clearDetectedAppTimestamp()
                        updatePresence(CommonRpc(), forceRefresh = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(TAG, "Detection cycle failed: ${e.message}")
                }
                delay(APP_DETECTION_INTERVAL_MS.milliseconds)
            }
        }
    }

    private suspend fun updatePresence(
        appInfo: CommonRpc? = null,
        richMediaInfo: RichMediaMetadata? = null,
        rawMediaMetadata: MediaMetadata? = null,
        forceRefresh: Boolean = false,
    ) {
        if (richMediaInfo == null && appInfo == null) {
            if (lastHadPresence) {
                logger.d(TAG, "No media data available - clearing Discord presence")
                kizzyRPC?.clearPresence()
                lastHadPresence = false
                lastSentPresenceKey = null
            }
            return
        }

        val currentActivityType = Prefs[Prefs.MANUAL_RPC_ACTIVITY_TYPE, ""]
        val currentStatus = Prefs[Prefs.MANUAL_RPC_STATUS, "online"]
        val currentLargeImageStr = Prefs[Prefs.MANUAL_RPC_LARGE_IMAGE, ""]
        val currentSmallImageStr = Prefs[Prefs.MANUAL_RPC_SMALL_IMAGE, ""]
        val currentLargeTextStr = Prefs[Prefs.MANUAL_RPC_LARGE_TEXT, ""]
        val currentSmallTextStr = Prefs[Prefs.MANUAL_RPC_SMALL_TEXT, ""]
        val currentStreamUrl = Prefs[Prefs.MANUAL_RPC_STREAM_URL, ""]
        val currentPartyCurrentSize = Prefs[Prefs.MANUAL_RPC_PARTY_CURRENT_SIZE, ""]
        val currentPartyMaxSize = Prefs[Prefs.MANUAL_RPC_PARTY_MAX_SIZE, ""]
        val currentTemplateName = Prefs[Prefs.MANUAL_RPC_TEMPLATE_NAME, TemplateKeys.APP_NAME]
        val currentTemplateDetails = Prefs[Prefs.MANUAL_RPC_TEMPLATE_DETAILS, TemplateKeys.MEDIA_TITLE]
        val currentTemplateState = Prefs[Prefs.MANUAL_RPC_TEMPLATE_STATE, TemplateKeys.MEDIA_ARTIST]
        val currentUseAppsRpc = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, true]
        val currentUseMediaRpc = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, true]

        if (richMediaInfo != null &&
            Prefs[Prefs.MANUAL_RPC_HIDE_ON_PAUSE, false] &&
            (richMediaInfo.playbackState == PlaybackState.STATE_PAUSED ||
                richMediaInfo.playbackState == PlaybackState.STATE_STOPPED)
        ) {
            if (currentUseAppsRpc) {
                startAppDetectionCoroutine()
            } else if (kizzyRPC?.isRpcRunning() == true) {
                kizzyRPC?.clearPresence()
                lastHadPresence = false
                lastSentPresenceKey = null
                notifyIdleIfNeeded()
            }
            return
        }

        val currentContextIsMedia =
            currentUseMediaRpc && richMediaInfo != null && richMediaInfo.appName != null
        val currentContextIsApp = currentUseAppsRpc && appInfo != null && appInfo.name.isNotEmpty()

        val processor = TemplateProcessor(
            mediaMetadata = rawMediaMetadata,
            mediaPlayerAppName = richMediaInfo?.appName,
            mediaPlayerPackageName = richMediaInfo?.packageName,
            detectedAppInfo = appInfo,
            batteryLevel = currentBatteryLevel(),
            playbackState = richMediaInfo?.playbackState
        )

        var finalSmallImageChannelUrl: String? = null
        val finalName: String?
        val finalDetails: String?
        val finalState: String?
        var finalLargeImage: RpcImage?
        var finalSmallImage: RpcImage?
        val finalLargeText: String?
        val finalSmallText: String?
        val finalTimestamps: Timestamps?
        val effectivePackageName: String?

        if (currentContextIsMedia) {
            effectivePackageName = richMediaInfo.packageName
            currentMediaProgressSec = (richMediaInfo.position / 1000).toInt()
                .coerceIn(0, (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0))
            currentMediaDurationSec = (richMediaInfo.duration / 1000).toInt().coerceAtLeast(0)
            currentMediaPositionMs = richMediaInfo.position.coerceAtLeast(0L)
            isPaused = richMediaInfo.playbackState != PlaybackState.STATE_PLAYING &&
                richMediaInfo.playbackState != PlaybackState.STATE_BUFFERING &&
                richMediaInfo.playbackState != PlaybackState.STATE_CONNECTING

            if (Prefs[Prefs.MANUAL_RPC_SHOW_SONG_AS_TITLE, false]) {
                val titleSource = Prefs[Prefs.MANUAL_RPC_TITLE_SOURCE, "title"]
                finalName = if (titleSource == "artist") richMediaInfo.artist else richMediaInfo.title
                finalDetails = resolveTemplateValue(
                    processor.process(currentTemplateDetails),
                    richMediaInfo.appName
                )
                finalState = resolveTemplateValue(
                    processor.process(currentTemplateState),
                    richMediaInfo.state.takeIf { titleSource != "artist" }
                )
            } else {
                finalName = resolveTemplateValue(
                    processor.process(currentTemplateName),
                    richMediaInfo.appName
                )
                finalDetails = resolveTemplateValue(
                    processor.process(currentTemplateDetails),
                    richMediaInfo.title
                )
                finalState = resolveTemplateValue(
                    processor.process(currentTemplateState),
                    richMediaInfo.state
                )
            }

            val hideCoverArt = Prefs[Prefs.RPC_HIDE_COVER_ART, false]
            val showCoverArt = Prefs[Prefs.MANUAL_RPC_SHOW_COVER_ART, true] && !hideCoverArt
            val showAppIcon = Prefs[Prefs.MANUAL_RPC_SHOW_APP_ICON, false] && !hideCoverArt
            val showChannelIcon = Prefs[Prefs.MANUAL_RPC_SHOW_CHANNEL_ICON, false] && !hideCoverArt
            val showPlaybackState = Prefs[Prefs.MANUAL_RPC_SHOW_PLAYBACK_STATE, true]

            finalLargeImage = when {
                showCoverArt -> richMediaInfo.coverArt ?: richMediaInfo.appIcon
                showAppIcon -> richMediaInfo.appIcon
                else -> null
            }

            finalSmallImage = when {
                showChannelIcon -> resolveChannelAvatar(richMediaInfo)?.also {
                    finalSmallImageChannelUrl = richMediaInfo.channelUrl
                }
                showPlaybackState -> richMediaInfo.playbackStateIcon
                showAppIcon && finalLargeImage != richMediaInfo.appIcon -> richMediaInfo.appIcon
                else -> null
            }

            finalLargeText = richMediaInfo.album ?: richMediaInfo.playlistName
            finalSmallText = when {
                finalSmallImage == richMediaInfo.appIcon -> richMediaInfo.appName
                finalSmallImageChannelUrl != null -> richMediaInfo.state?.takeIf { it.isNotEmpty() }
                else -> null
            }

            finalTimestamps = resolveTimestamps(richMediaInfo.timestamps)
        } else if (currentContextIsApp) {
            effectivePackageName = appInfo.packageName
            finalName = resolveTemplateValue(processor.process(currentTemplateName), appInfo.name)
            finalDetails = resolveTemplateValue(processor.process(currentTemplateDetails), appInfo.details)
            finalState = resolveTemplateValue(processor.process(currentTemplateState), appInfo.state)
            finalLargeImage = appInfo.largeImage
            finalSmallImage = appInfo.smallImage
            finalLargeText = appInfo.largeText
            finalSmallText = appInfo.smallText
            finalTimestamps = resolveTimestamps(appInfo.time)
        } else {
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
            if (kizzyRPC?.isRpcRunning() == true) {
                kizzyRPC?.clearPresence()
                lastHadPresence = false
                lastSentPresenceKey = null
                notifyIdleIfNeeded()
            }
            return
        }

        val mediaUrl = rawMediaMetadata?.let {
            metadataResolver.getDirectMediaUrl(it, richMediaInfo?.packageName)
                ?: metadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
        } ?: richMediaInfo?.mediaUrl
        val songLink = YouTubeResolver.primaryMediaUrl(
            mediaUrl,
            richMediaInfo?.albumUrl,
            richMediaInfo?.channelUrl ?: richMediaInfo?.artistUrl,
        )
        val detailsUrl = songLink
            ?: MediaServiceResolver.mediaSearchUrl(
                richMediaInfo?.packageName ?: effectivePackageName ?: "",
                richMediaInfo?.title ?: finalDetails ?: finalName ?: "",
            )
            ?: MediaServiceResolver.browserSearchUrl(
                richMediaInfo?.title ?: finalDetails ?: finalName ?: "",
            )

        val stateUrl = richMediaInfo?.artistUrl
            ?: MediaServiceResolver.artistUrl(
                richMediaInfo?.packageName ?: effectivePackageName,
                richMediaInfo?.artist ?: richMediaInfo?.state ?: "",
            )

        val largeUrl = YouTubeResolver.largeImageUrl(
            mediaUrl,
            richMediaInfo?.albumUrl,
            richMediaInfo?.playlistUrl,
        )
        val smallUrl = MediaServiceResolver.appDownloadUrl(richMediaInfo?.packageName)
        val finalLargeUrl = largeUrl ?: smallUrl

        if (currentLargeImageStr.isNotBlank()) {
            finalLargeImage = currentLargeImageStr.toRpcImage()
        }
        if (currentSmallImageStr.isNotBlank()) {
            finalSmallImage = currentSmallImageStr.toRpcImage()
        }

        val useButtons = Prefs[Prefs.MANUAL_RPC_USE_BUTTONS, false]
        val button1Text = Prefs[Prefs.MANUAL_RPC_BUTTON1_TEXT, ""]
        val button1Url = Prefs[Prefs.MANUAL_RPC_BUTTON1_URL, ""]
        val button2Text = Prefs[Prefs.MANUAL_RPC_BUTTON2_TEXT, ""]
        val button2Url = Prefs[Prefs.MANUAL_RPC_BUTTON2_URL, ""]
        val useCustomAppId = Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false]
        val customAppId = Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""]

        val presenceKey = listOf(
            finalName,
            finalDetails,
            finalState,
            currentLargeTextStr.takeIf { it.isNotEmpty() } ?: finalLargeText,
            currentSmallTextStr.takeIf { it.isNotEmpty() } ?: finalSmallText,
            finalLargeImage?.toUrlString() ?: finalLargeImage?.toString(),
            finalSmallImage?.toUrlString() ?: finalSmallImage?.toString(),
            finalSmallImageChannelUrl,
            finalTimestamps?.start?.toString(),
            finalTimestamps?.end?.toString(),
            effectivePackageName,
            richMediaInfo?.playbackState?.toString(),
            currentActivityType,
            currentStatus,
            currentLargeImageStr,
            currentSmallImageStr,
            currentStreamUrl,
            currentPartyCurrentSize,
            currentPartyMaxSize,
            useButtons.toString(),
            button1Text,
            button1Url,
            button2Text,
            button2Url,
            useCustomAppId.toString(),
            customAppId,
        )
        val hasMeaningfulChange = presenceKey != lastSentPresenceKey
        if (!rpcPacer.acquireSendSlot(hasMeaningfulChange = hasMeaningfulChange, forceRefresh = forceRefresh)) {
            return
        }

        val resolvedActivityType = currentActivityType.takeIf { it.isNotEmpty() }?.toIntOrNull()
            ?: ActivityTypeResolver.resolve(
                packageName = effectivePackageName,
                isMusicMetadata = richMediaInfo?.isMusicMetadata == true,
                fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
            )

        val commonRpc = CommonRpc(
            name = finalName ?: "",
            type = resolvedActivityType,
            details = finalDetails,
            state = finalState,
            detailsUrl = detailsUrl,
            stateUrl = stateUrl,
            largeImage = finalLargeImage,
            smallImage = finalSmallImage,
            smallImageChannelUrl = finalSmallImageChannelUrl,
            largeUrl = finalLargeUrl,
            largeText = currentLargeTextStr.takeIf { it.isNotEmpty() } ?: finalLargeText,
            smallText = currentSmallTextStr.takeIf { it.isNotEmpty() } ?: finalSmallText,
            time = finalTimestamps,
            packageName = effectivePackageName ?: "",
            playbackState = richMediaInfo?.playbackState,
            mediaUrl = rawMediaMetadata?.let {
                metadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
            },
            partyCurrentSize = currentPartyCurrentSize.toIntOrNull(),
            partyMaxSize = currentPartyMaxSize.toIntOrNull(),
        )

        kizzyRPC?.clearButtons()
        if (useButtons) {
            if (button1Text.isNotEmpty() && button1Url.isNotEmpty()) {
                kizzyRPC?.setButton1(button1Text, button1Url)
            }
            if (button2Text.isNotEmpty() && button2Url.isNotEmpty()) {
                kizzyRPC?.setButton2(button2Text, button2Url)
            }
        } else {
            val buttonMediaUrl = rawMediaMetadata?.let {
                metadataResolver.getMediaUrl(it, richMediaInfo?.packageName)
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
                metadataResolver.getArtistUrl(metadata, richMediaInfo?.packageName)?.let { url ->
                    kizzyRPC?.setButton2(
                        MediaServiceResolver.artistButtonLabel(effectivePackageName),
                        url,
                    )
                }
            }
        }

        if (useCustomAppId) {
            kizzyRPC?.setApplicationId(customAppId)
        }
        if (currentStreamUrl.isNotBlank()) {
            kizzyRPC?.setStreamUrl(currentStreamUrl)
        }

        val resolvedStatus = currentStatus.takeIf { it.isNotEmpty() } ?: "online"
        kizzyRPC?.updateRPC(
            commonRpc = commonRpc,
            enableTimestamps = Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true],
            status = resolvedStatus,
            forceRefresh = forceRefresh || hasMeaningfulChange,
            priority = 1,
        )
        lastSentPresenceKey = presenceKey
        lastHadPresence = true

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
            status = resolvedStatus,
            url = currentStreamUrl.ifEmpty { "" },
            partyCurrentSize = currentPartyCurrentSize.ifEmpty { "" },
            partyMaxSize = currentPartyMaxSize.ifEmpty { "" },
        )
        LastPresence.rememberExperimental(rpcConfig)
        rpcManager.setCurrentPresenceConfig(rpcConfig.copy(instanceId = currentInstanceId), currentInstanceId)

        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateMs >= NOTIFICATION_MIN_INTERVAL_MS) {
            lastNotificationUpdateMs = now
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

            notificationManager.notify(
                Constants.NOTIFICATION_ID_MANUAL_RPC,
                buildServiceNotification(
                    title = notifTitle,
                    contentText = notifText,
                    largeImage = finalLargeImage,
                    progressMaxSec = currentMediaDurationSec,
                    progressSec = currentMediaProgressSec,
                )
            )
        }
    }

    private suspend fun resolveChannelAvatar(richMediaInfo: RichMediaMetadata): RpcImage.ExternalImage? {
        val byChannel = richMediaInfo.channelUrl?.let { channelUrl ->
            YouTubeResolver.lookupChannelAvatarByChannelUrl(channelUrl)
        }
        if (byChannel != null) {
            return RpcImage.ExternalImage(byChannel)
        }
        val byVideo = richMediaInfo.mediaUrl?.let { url ->
            YouTubeResolver.extractVideoId(url)?.let { videoId ->
                YouTubeResolver.lookupChannelAvatar(videoId)
            }
        }
        return byVideo?.let { RpcImage.ExternalImage(it) }
    }

    private fun activeSessionsListener(
        mediaSessions: List<MediaController>?,
        isEvent: Boolean = true,
    ) {
        val mediaEnabled = Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, useMediaRpc]
        val appsEnabled = Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, useAppsRpc]
        useMediaRpc = mediaEnabled
        useAppsRpc = appsEnabled

        if (!mediaEnabled) {
            if (appsEnabled && !isMediaSessionActive) {
                startAppDetectionCoroutine()
            } else if (!appsEnabled) {
                launchPresenceUpdate {
                    updatePresence(appInfo = null, richMediaInfo = null, rawMediaMetadata = null, forceRefresh = true)
                }
            }
            return
        }
        logger.d(TAG, "Active media sessions changed (isEvent=$isEvent)")

        currentMediaController?.unregisterCallback(mediaControllerCallback)
        currentMediaController = null

        if (!mediaSessions.isNullOrEmpty()) {
            currentMediaController = mediaSessions.firstOrNull {
                enabledExperimentalApps.contains(it.packageName) || Prefs.isMediaAppEnabled(it.packageName)
            }
            currentMediaController?.registerCallback(mediaControllerCallback)
            isMediaSessionActive = currentMediaController != null
        } else {
            isMediaSessionActive = false
        }

        launchPresenceUpdate {
            val richMediaData = fetchPlayingMediaWithRetry()
            if (richMediaData.appName != null && useMediaRpc) {
                updatePresence(
                    richMediaInfo = richMediaData,
                    rawMediaMetadata = currentMediaController?.metadata,
                    forceRefresh = true,
                )
            } else if (useAppsRpc) {
                startAppDetectionCoroutine()
            }
        }
    }

    private inner class MediaControllerCallback : MediaController.Callback() {
        private fun handleMediaUpdate() {
            if (!Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, useMediaRpc]) return

            launchPresenceUpdate {
                delay(200.milliseconds)
                val richMediaData = getCurrentPlayingMediaAll.invoke()
                if (richMediaData.appName != null && Prefs[Prefs.MANUAL_RPC_USE_MEDIA_RPC, useMediaRpc]) {
                    val freshController = try {
                        mediaSessionManager
                            .getActiveSessions(ComponentName(this@ManualRpc, NotificationListener::class.java))
                            .firstOrNull { it.packageName == richMediaData.packageName }
                    } catch (_: SecurityException) {
                        null
                    }
                    updatePresence(
                        richMediaInfo = richMediaData,
                        rawMediaMetadata = freshController?.metadata,
                        forceRefresh = true,
                    )
                } else if (Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, useAppsRpc]) {
                    startAppDetectionCoroutine()
                } else {
                    updatePresence(appInfo = null, richMediaInfo = null, rawMediaMetadata = null, forceRefresh = true)
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            handleMediaUpdate()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            handleMediaUpdate()
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            currentMediaController?.unregisterCallback(this)
            currentMediaController = null
            isMediaSessionActive = false

            launchPresenceUpdate {
                if (Prefs[Prefs.MANUAL_RPC_USE_APPS_RPC, useAppsRpc]) {
                    startAppDetectionCoroutine()
                } else {
                    updatePresence(appInfo = null, richMediaInfo = null, rawMediaMetadata = null, forceRefresh = true)
                }
            }
        }
    }

    private fun resolveTimestamps(contextTimestamps: Timestamps?): Timestamps? {
        if (!Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true]) return null
        return when (Prefs[Prefs.MANUAL_RPC_TIMESTAMP_MODE, "default"]) {
            "current" -> {
                val cal = Calendar.getInstance()
                val midnightMs = System.currentTimeMillis() -
                    (cal.get(Calendar.HOUR_OF_DAY) * 3600L +
                        cal.get(Calendar.MINUTE) * 60L +
                        cal.get(Calendar.SECOND)) * 1000L
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

    private fun currentBatteryLevel(): Int? = try {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    } catch (_: Exception) {
        null
    }

    private fun resolveTemplateValue(result: String?, fallback: String?): String? {
        val resolved = result?.trim()
        if (resolved == null || resolved.isEmpty() || resolved.startsWith("{")) {
            return fallback
        }
        return resolved
    }

    override fun onDestroy() {
        AppUtils.unregisterRpcService(this)
        if (::mediaSessionManager.isInitialized) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
            } catch (e: Exception) {
                logger.e(TAG, "removeOnActiveSessionsChangedListener failed: ${e.message}")
            }
        }
        currentMediaController?.unregisterCallback(mediaControllerCallback)
        stopPeriodicRefresh()
        appDetectionJob?.cancel()
        presenceUpdateJob?.cancel()
        clearDetectedAppTimestamp()
        serviceJob.cancel()

        val rpc = kizzyRPC
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
        val manualStillEnabled = RpcManager.RpcType.ManualRpc in rpcManager.activeRpc.value
        if (!explicitStop && manualStillEnabled) {
            WorkManagerHelper.scheduleRpcRecovery(this)
        }
        manualCloneInstanceIds.forEach { CloneNotificationHelper.cancel(notificationManager, it) }
        manualCloneInstanceIds.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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