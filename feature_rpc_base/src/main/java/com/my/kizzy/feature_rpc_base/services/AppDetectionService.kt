/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppDetectionService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

@file:Suppress("DEPRECATION")
@file:SuppressLint("MissingPermission")

package com.my.kizzy.feature_rpc_base.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.my.kizzy.data.rpc.*
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.utils.toUrlString
import com.my.kizzy.domain.model.rpc.RpcButtons
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
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

private const val MIN_RPC_INTERVAL_MS = 500L
private const val MAX_RPC_IDLE_INTERVAL_MS = 14_500L

@AndroidEntryPoint
@SuppressLint("MissingPermission")
class AppDetectionService : Service() {

    private var kizzyRPC: KizzyRPC? = null

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var rpcManager: RpcManager

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var dynamicActivityDetectionEngine: DynamicActivityDetectionEngine

    private lateinit var pendingIntent: PendingIntent

    private lateinit var restartPendingIntent: PendingIntent

    private var runningPackage = ""
    private var activeStartTimestampMs: Long? = null
    private val rpcPacer = RpcPacer(
        minIntervalMs = MIN_RPC_INTERVAL_MS,
        maxIdleIntervalMs = MAX_RPC_IDLE_INTERVAL_MS,
    )

    private fun resolveStartTimestamp(packageName: String): Long {
        if (runningPackage != packageName || activeStartTimestampMs == null) {
            activeStartTimestampMs = System.currentTimeMillis()
        }
        return activeStartTimestampMs!!
    }

    override fun onTaskRemoved(intent: Intent?) {
        Log.d(TAG, "onTaskRemoved: app swiped from Recents - keeping App Detection alive")
        com.my.kizzy.data.work.WorkManagerHelper.scheduleRpcRecovery(this)
        super.onTaskRemoved(intent)
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        kizzyRPC = multiRpc.session("4")

        when (intent?.action) {
            Constants.ACTION_STOP_SERVICE -> {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
                val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                    .split(',')
                    .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                    .filter { it != RpcManager.RpcType.AppDetectionRpc }
                    .joinToString(",") { it.name }
                Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
                rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.AppDetectionRpc))
                stopSelf()
            }
            Constants.ACTION_RESTART_SERVICE -> {
                stopSelf()
                startService(Intent(this, AppDetectionService::class.java))
            }
            else -> {
                rpcPacer.reset()
                Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.AppDetectionRpc.name
                handleAppDetection()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
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
        super.onDestroy()
    }

    private fun handleAppDetection() {
        val stopIntent = createStopIntent()
        pendingIntent = createPendingIntent(stopIntent)

        val restartIntent = createRestartIntent()
        restartPendingIntent = PendingIntent.getService(
            this,
            0, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_ID_APP_DETECTION, createDefaultNotification())
        } else {
            startForeground(Constants.NOTIFICATION_ID_APP_DETECTION, createDefaultNotification(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }

        scope.launch {
            while (isActive) {
                try {
                    val enabledPackages = getEnabledPackages()
                    val rpcButtons = getRpcButtons()

                    val queryUsageStats = getUsageStats()

                    val packageName = getForegroundPackage()
                        ?: (if (queryUsageStats != null && queryUsageStats.size > 1) {
                            getLatestPackageName(queryUsageStats)
                        } else {
                            null
                        })

                    if (packageName != null && packageName !in EXCLUDED_APPS) {
                        Log.d(TAG, "Foreground package: $packageName (enabled=${packageName in enabledPackages}, game=${dynamicActivityDetectionEngine.identifyGame(packageName) != null}, ai=${dynamicActivityDetectionEngine.identifyAiApp(packageName) != null})")
                        handleValidPackage(packageName, enabledPackages, rpcButtons)
                    } else if (packageName == null && runningPackage != "") {
                        Log.d(TAG, "No foreground package detected, clearing old presence for: $runningPackage")
                        handleDisabledPackage()
                        runningPackage = ""
                        activeStartTimestampMs = null
                    }

                    if (dynamicActivityDetectionEngine.isAiAssistantActive()) {
                        val assistantPkg = dynamicActivityDetectionEngine.getActiveAssistantPackage()
                        if (assistantPkg != null && assistantPkg !in EXCLUDED_APPS) {
                            val aiApp = dynamicActivityDetectionEngine.identifyAiApp(assistantPkg)
                            handleEnabledPackage(assistantPkg, rpcButtons, aiApp = aiApp)
                            runningPackage = assistantPkg
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("AppDetectionService", "Detection cycle failed: ${e.message}")
                }
                delay(2000.milliseconds)
            }
        }
    }

    private fun getEnabledPackages(): List<String> {
        val apps = Prefs[Prefs.ENABLED_APPS, "[]"]
        return Json.decodeFromString(apps)
    }

    private fun getRpcButtons(): RpcButtons {
        val rpcButtonsString = Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]
        return Json.decodeFromString(rpcButtonsString)
    }

    private fun getUsageStats(): List<UsageStats>? {
        return try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTimeMillis = System.currentTimeMillis()
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTimeMillis - 10000,
                currentTimeMillis
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun getForegroundPackage(): String? {
        return try {
            val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTimeMillis = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(
                currentTimeMillis - EVENT_LOOKBACK_MS,
                currentTimeMillis
            )
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (!isForegroundEvent) continue
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            latestPackage
        } catch (_: Exception) {
            null
        }
    }

    private fun getLatestPackageName(usageStats: List<UsageStats>): String? {
        val treeMap: SortedMap<Long, UsageStats> = TreeMap()
        for (usageStatsItem in usageStats) {
            treeMap[usageStatsItem.lastTimeUsed] = usageStatsItem
        }
        return treeMap.lastKey()?.let { treeMap[it]?.packageName }
    }

    private suspend fun handleValidPackage(
        packageName: String,
        enabledPackages: List<String>,
        rpcButtons: RpcButtons,
    ) {
        val aiApp = dynamicActivityDetectionEngine.identifyAiApp(packageName)
        val isAiApp = aiApp != null

        val gameApp = dynamicActivityDetectionEngine.identifyGame(packageName)
        val isGameApp = gameApp != null

        val shouldShow = packageName in enabledPackages || isAiApp || isGameApp

        if (shouldShow) {
            val appChanged = packageName != runningPackage || runningPackage == ""
            handleEnabledPackage(
                packageName = packageName,
                rpcButtons = rpcButtons,
                aiApp = aiApp,
                gameApp = gameApp,
                hasMeaningfulChange = appChanged,
            )
            runningPackage = packageName
        } else if (runningPackage != "") {
            handleDisabledPackage()
            runningPackage = ""
            activeStartTimestampMs = null
        }
    }

    private suspend fun handleEnabledPackage(
        packageName: String,
        rpcButtons: RpcButtons,
        aiApp: DetectedAiApp? = null,
        gameApp: DetectedGameApp? = null,
        hasMeaningfulChange: Boolean = true,
    ) {
        val startMs = resolveStartTimestamp(packageName)
        if (!rpcPacer.acquireSendSlot(hasMeaningfulChange = hasMeaningfulChange, forceRefresh = false)) {
            return
        }

        val isAiApp = aiApp != null
        val isGameApp = gameApp != null
        val displayName = if (isAiApp) aiApp.displayName else if (isGameApp) gameApp.displayName else null
        val appLabel = displayName
            ?: this.packageManager.getApplicationLabel(
                this.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        Log.d(TAG, "Sending RPC for: $packageName (label=$appLabel, isGame=$isGameApp, isAI=$isAiApp)")
        val iconUrl = if (isAiApp) aiApp.iconUrl else if (isGameApp) gameApp.iconUrl else null
        val details = if (isAiApp) "Chatting with AI" else if (isGameApp) "Playing game" else null
        val state = if (isAiApp) "Thinking..." else if (isGameApp) "In-game" else null
        val largeImage = iconUrl?.let { RpcImage.ExternalImage(it) }
            ?: RpcImage.ApplicationIcon(packageName, this@AppDetectionService)

        kizzyRPC?.apply {
            setName(appLabel)
            setDetails(details)
            setState(state)
            setStartTimestamps(startMs)
            setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
            setLargeImage(largeImage)
            clearButtons()
            if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
                with(rpcButtons) {
                    setButton1(button1.takeIf { it.isNotEmpty() }, button1Url.takeIf { it.isNotEmpty() })
                    setButton2(button2.takeIf { it.isNotEmpty() }, button2Url.takeIf { it.isNotEmpty() })
                }
            }
        }

        kizzyRPC?.updateRPC(
            CommonRpc(
                name = appLabel,
                type = ActivityTypeResolver.resolve(
                    packageName = packageName,
                    fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                    useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                ),
                details = details,
                state = state,
                largeImage = largeImage,
                time = Timestamps(start = startMs),
                packageName = packageName,
                appPackage = packageName
            ),
            enableTimestamps = true,
            priority = 4,
        )

        val detectedType = ActivityTypeResolver.resolve(
            packageName = packageName,
            fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
            useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
        )
        rpcManager.setCurrentPresenceConfig(
            RpcConfig(
                instanceId = "app_detection_rpc",
                name = appLabel,
                details = details.orEmpty(),
                state = state.orEmpty(),
                type = detectedType.toString(),
                timestampsStart = startMs.toString(),
                timestampsStop = "",
                largeImg = largeImage.toUrlString().orEmpty(),
            ),
            "app_detection_rpc"
        )

        notificationManager.notify(
            Constants.NOTIFICATION_ID_APP_DETECTION, notificationBuilder
                .setContentText(displayName ?: packageName)
                .setLargeIcon(largeImage, this@AppDetectionService)
                .build()
        )
    }

    private suspend fun handleDisabledPackage() {
        if (kizzyRPC?.isRpcRunning() == true) {
            kizzyRPC?.clearPresence()
        }
        notificationManager.notify(Constants.NOTIFICATION_ID_APP_DETECTION, createDefaultNotification())
    }

    private fun createDefaultNotification(): Notification {
        return Notification.Builder(this, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apps)
            .setContentTitle(getString(R.string.service_enabled))
            .addAction(R.drawable.ic_apps, getString(R.string.exit), pendingIntent)
            .addAction(R.drawable.ic_apps, getString(R.string.restart), restartPendingIntent)
            .build()
    }

    private fun createStopIntent(): Intent {
        val stopIntent = Intent(this, AppDetectionService::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        return stopIntent
    }

    private fun createRestartIntent(): Intent {
        val restartIntent = Intent(this, AppDetectionService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        return restartIntent
    }

    private fun createPendingIntent(stopIntent: Intent): PendingIntent {
        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val EVENT_LOOKBACK_MS = 30000L

        val EXCLUDED_APPS = listOf("com.my.kizzy", "com.my.kizzy.enhanced", "com.discord")

        internal const val TAG = "AppDetectionService"
    }
}

object CloneNotificationHelper {

    private val ids = mutableMapOf<String, Int>()
    private var nextId = Constants.NOTIFICATION_ID_CLONE_BASE
    private val lastNotificationUpdateMs = mutableMapOf<String, Long>()

    @Synchronized
    fun notificationIdFor(instanceId: String): Int =
        ids.getOrPut(instanceId) { nextId++ }

    @Synchronized
    fun release(instanceId: String): Int? = ids.remove(instanceId)

    fun show(
        context: Context,
        notificationManager: NotificationManager,
        serviceClass: Class<*>,
        instanceId: String,
        label: String,
        content: String,
        iconRes: Int = R.drawable.ic_rpc_placeholder,
    ) {
        val id = notificationIdFor(instanceId)

        val stopIntent = Intent(context, serviceClass).apply {
            action = Constants.ACTION_REMOVE_INSTANCE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        }
        val stopPendingIntent = PendingIntent.getService(
            context, id + 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(context, serviceClass).apply {
            action = Constants.ACTION_RESTART_SERVICE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        }
        val restartPendingIntent = PendingIntent.getService(
            context, id + 2, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val appIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val appPendingIntent = PendingIntent.getActivity(
            context, id + 3, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(context, Constants.CHANNEL_ID_RPC_INSTANCES)
            .setContentTitle(label.ifBlank { context.getString(R.string.custom_rpc_running) })
            .setContentText(content.ifBlank { context.getString(R.string.custom_rpc_running) })
            .setSmallIcon(iconRes)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, iconRes),
                    context.getString(R.string.restart),
                    restartPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, iconRes),
                    context.getString(R.string.exit),
                    stopPendingIntent
                ).build()
            )
            .build()

        val now = System.currentTimeMillis()
        if (now - (lastNotificationUpdateMs[instanceId] ?: 0L) < 1000) return
        lastNotificationUpdateMs[instanceId] = now

        notificationManager.notify(id, notification)
    }

    fun cancel(
        notificationManager: NotificationManager,
        instanceId: String,
    ) {
        release(instanceId)?.let { notificationManager.cancel(it) }
    }
}