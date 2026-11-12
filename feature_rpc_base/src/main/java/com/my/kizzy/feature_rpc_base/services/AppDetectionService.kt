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

package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.services.DetectedAiApp
import com.my.kizzy.feature_rpc_base.services.DetectedGameApp
import com.my.kizzy.feature_rpc_base.services.DynamicActivityDetectionEngine
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.SortedMap
import java.util.TreeMap
import javax.inject.Inject

@AndroidEntryPoint
class AppDetectionService : Service() {

    @Inject
    lateinit var kizzyRPC: KizzyRPC

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
    override fun onBind(intent: Intent): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            stopSelf()
        } else if (intent?.action == Constants.ACTION_RESTART_SERVICE) {
            stopSelf()
            startService(Intent(this, AppDetectionService::class.java))
        } else {
            handleAppDetection()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        scope.cancel()
        kizzyRPC.closeRPC()
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
        Notification.Builder(this, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apps)
            .addAction(R.drawable.ic_apps, getString(R.string.restart), restartPendingIntent)
            .addAction(R.drawable.ic_apps, getString(R.string.exit), pendingIntent)


        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID, createDefaultNotification())
        } else {
            startForeground(Constants.NOTIFICATION_ID, createDefaultNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }

        scope.launch {
            while (isActive) {
                try {
                    // Re-read the enabled apps and buttons on every cycle instead of caching
                    // them once at service start. Caching them meant that toggling an app in
                    // the apps list had no effect until the service was restarted, which is
                    // exactly the "app list does not switch the shown app" symptom.
                    val enabledPackages = getEnabledPackages()
                    val rpcButtons = getRpcButtons()

                    val queryUsageStats = getUsageStats()

                    // Prefer the raw foreground event stream; fall back to the coarse usage
                    // stats when the event stream is unavailable.
                    val packageName = getForegroundPackage()
                        ?: (if (queryUsageStats != null && queryUsageStats.size > 1) {
                            getLatestPackageName(queryUsageStats)
                        } else {
                            null
                        })

                    if (packageName != null && packageName !in EXCLUDED_APPS) {
                        handleValidPackage(packageName, enabledPackages, rpcButtons)
                    }

                    // Also check the active assistant (power-button / "Hey Google" triggers)
                    // that may appear as a floating overlay rather than a foreground activity.
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
                delay(2000)
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
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTimeMillis = System.currentTimeMillis()
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTimeMillis - 10000,
            currentTimeMillis
        )
    }

    /**
     * Resolves the package that is actually in the foreground right now.
     *
     * `queryUsageStats` with `INTERVAL_DAILY` only returns coarse daily buckets, so the
     * "last used" entry is frequently a background app and the foreground app never gets
     * picked up. Querying the raw usage EVENT stream and looking for the most recent
     * foreground transition is the reliable way to know which app the user is looking at
     * right now — this is what makes the apps list actually switch between apps.
     */
    private fun getForegroundPackage(): String? {
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
        return latestPackage
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
        // Check if this is an AI app via dynamic detection (no hardcoded list needed).
        val aiApp = dynamicActivityDetectionEngine.identifyAiApp(packageName)
        val isAiApp = aiApp != null

        // Check if this is a game via Android system game flags (no hardcoded list needed).
        val gameApp = dynamicActivityDetectionEngine.identifyGame(packageName)
        val isGameApp = gameApp != null

        // Determine if this package should be shown on Discord.
        // This is evaluated independently of runningPackage so that disabling
        // an app in Preferences immediately removes its presence even if the
        // app is still playing in the foreground.
        val shouldShow = packageName in enabledPackages || isAiApp || isGameApp

        if (shouldShow) {
            // Show the app. When it is a DIFFERENT app than the one currently shown, the
            // presence has to be replaced. When it is the SAME app, the presence is
            // already correct and re-sending it would only spam the gateway - but the
            // check is skipped entirely when the app was never shown before (the user
            // just enabled it), so that enabling an app takes effect immediately.
            if (packageName != runningPackage || runningPackage == "") {
                handleEnabledPackage(packageName, rpcButtons, aiApp = aiApp, gameApp = gameApp)
                runningPackage = packageName
            }
        } else if (runningPackage != "") {
            // App was disabled in prefs or is no longer relevant — clear presence.
            handleDisabledPackage()
            runningPackage = ""
        }
    }

    private suspend fun handleEnabledPackage(
        packageName: String,
        rpcButtons: RpcButtons,
        aiApp: DetectedAiApp? = null,
        gameApp: DetectedGameApp? = null
    ) {
        val isAiApp = aiApp != null
        val isGameApp = gameApp != null
        val displayName = if (isAiApp) aiApp!!.displayName else if (isGameApp) gameApp!!.displayName else null
        val iconUrl = if (isAiApp) aiApp!!.iconUrl else if (isGameApp) gameApp!!.iconUrl else null
        val appLabel = displayName
            ?: this.packageManager.getApplicationLabel(
                this.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        val details = if (isAiApp) "Chatting with AI" else if (isGameApp) "Playing game" else null
        val state = if (isAiApp) "Thinking..." else if (isGameApp) "In-game" else null
        val largeImage = iconUrl?.let { RpcImage.ExternalImage(it) }
            ?: RpcImage.ApplicationIcon(packageName, this@AppDetectionService)

        // Configure the RPC the same way for both the "already connected" and the
        // "not connected yet" path. Previously the update path silently dropped the
        // buttons and the status, so the presence changed depending on whether the
        // gateway happened to be connected already.
        kizzyRPC.apply {
            setName(appLabel)
            setDetails(details)
            setState(state)
            setStartTimestamps(System.currentTimeMillis())
            setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
            setLargeImage(largeImage)
            // Buttons are additive, so clear them first or they pile up on every app switch.
            clearButtons()
            if (Prefs[Prefs.USE_RPC_BUTTONS, false]) {
                with(rpcButtons) {
                    setButton1(button1.takeIf { it.isNotEmpty() }, button1Url.takeIf { it.isNotEmpty() })
                    setButton2(button2.takeIf { it.isNotEmpty() }, button2Url.takeIf { it.isNotEmpty() })
                }
            }
        }

        if (kizzyRPC.isRpcRunning()) {
            kizzyRPC.updateRPC(
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
                    time = Timestamps(start = System.currentTimeMillis()),
                    packageName = packageName,
                    appPackage = packageName
                ),
                enableTimestamps = true
            )
        } else {
            kizzyRPC.build()
        }

        notificationManager.notify(
            Constants.NOTIFICATION_ID, notificationBuilder
                .setContentText(displayName ?: packageName)
                .setLargeIcon(
                    rpcImage = largeImage,
                    context = this@AppDetectionService
                )
                .build()
        )
    }

    private suspend fun handleDisabledPackage() {
        if (kizzyRPC.isRpcRunning()) {
            // Clear the activity instead of tearing down the gateway connection. Closing it
            // meant the next app switch had to reconnect from scratch, and the reconnect
            // could fail, leaving Discord stuck showing the previous app.
            kizzyRPC.clearPresence()
        }
        notificationManager.notify(Constants.NOTIFICATION_ID, createDefaultNotification())
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
        /**
         * How far back the usage event stream is searched for a foreground transition.
         * Long enough to survive a short trip to another app, short enough that switching
         * apps is picked up quickly.
         */
        private const val EVENT_LOOKBACK_MS = 5000L

        val EXCLUDED_APPS = listOf("com.my.kizzy", "com.my.kizzy.enhanced", "com.discord")
    }
}
