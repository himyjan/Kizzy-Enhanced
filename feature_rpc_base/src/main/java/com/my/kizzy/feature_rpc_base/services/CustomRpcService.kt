/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CustomRpcService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import com.my.kizzy.data.get_current_data.app.GetForegroundPackage
import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.data.work.WorkManagerHelper
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.CustomRpcBoardManager
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RPC_INSTANCE_CUSTOM
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
import kotlinx.serialization.json.Json

private const val MIN_RPC_INTERVAL_MS = 500L
private const val MAX_RPC_IDLE_INTERVAL_MS = 14_500L

@AndroidEntryPoint
class CustomRpcService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var rpcData: RpcConfig? = null
    private var wakeLock: WakeLock? = null

    private var kizzyRPC: KizzyRPC? = null

    private val lastNotificationUpdateMs = mutableMapOf<String, Long>()

    private val customRpcs = LinkedHashMap<String, KizzyRPC>()
    private val customConfigs = LinkedHashMap<String, RpcConfig>()
    private val lastSentConfigs = LinkedHashMap<String, RpcConfig>()
    private val instancePacers = LinkedHashMap<String, RpcPacer>()

    private var foregroundNotificationId: Int = Constants.NOTIFICATION_ID_CUSTOM_RPC

    private fun pacerFor(instanceId: String): RpcPacer =
        instancePacers.getOrPut(instanceId) {
            RpcPacer(
                minIntervalMs = MIN_RPC_INTERVAL_MS,
                maxIdleIntervalMs = MAX_RPC_IDLE_INTERVAL_MS,
            )
        }

    private fun notificationIdFor(instanceId: String): Int =
        if (instanceId == RPC_INSTANCE_CUSTOM || instanceId == "console_rpc") {
            Constants.NOTIFICATION_ID_CUSTOM_RPC
        } else {
            CloneNotificationHelper.notificationIdFor(instanceId)
        }

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var rpcManager: RpcManager

    @Inject
    lateinit var foregroundPackage: GetForegroundPackage

    @Inject
    lateinit var boardManager: CustomRpcBoardManager

    @Inject
    lateinit var notificationManager: NotificationManager

    private var consoleMode = false

    private var consoleDetectionJob: Job? = null

    private var presenceRefreshJob: Job? = null

    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val initialNotification = Notification.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle(getString(R.string.custom_rpc_running))
            .setContentText("")
            .setSmallIcon(R.drawable.ic_rpc_placeholder)
            .setContentIntent(appPendingIntent)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIFICATION_ID_CUSTOM_RPC, initialNotification)
        } else {
            startForeground(Constants.NOTIFICATION_ID_CUSTOM_RPC, initialNotification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        foregroundNotificationId = Constants.NOTIFICATION_ID_CUSTOM_RPC
        foregroundStarted = true
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetId = intent?.getStringExtra(Constants.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: RPC_INSTANCE_CUSTOM
        foregroundNotificationId = notificationIdFor(targetId)
        kizzyRPC = multiRpc.customSession(targetId)?.also {
            customRpcs[targetId] = it
        }

        intent?.let { readConsoleMode(it) }

        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.CustomRpc && it != RpcManager.RpcType.ConsoleRpc }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes.joinToString(",") { it.name }
            if (
                Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.CustomRpc.name ||
                Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.ConsoleRpc.name
            ) {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = remainingTypes.firstOrNull()?.name ?: ""
            }
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.CustomRpc, RpcManager.RpcType.ConsoleRpc))
            stopSelf()
            return START_NOT_STICKY
        } else {
            Prefs[Prefs.ACTIVE_RPC_TYPE] =
                if (consoleMode) RpcManager.RpcType.ConsoleRpc.name
                else RpcManager.RpcType.CustomRpc.name
        }

        Log.d("CustomRpcService", "onStartCommand: intent action=${intent?.action}, hasRPCExtra=${intent?.getStringExtra("RPC")?.isNotBlank() == true}")

        when (intent?.action) {
            Constants.ACTION_ADD_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    val config = intent.getStringExtra("RPC")?.takeIf { it.isNotBlank() }
                        ?.let { raw ->
                            runCatching { Json.decodeFromString<RpcConfig>(raw) }
                                .getOrElse { e ->
                                    Log.e("CustomRpcService", "Failed to parse instance RPC config: ${e.message}")
                                    null
                                }
                        } ?: RpcConfig(name = "Custom RPC", details = "Kizzy Enhanced")
                    customConfigs[instanceId] = config
                    pacerFor(instanceId).reset()
                    val rpc = multiRpc.customSession(instanceId)
                    if (rpc != null) {
                        customRpcs[instanceId] = rpc
                        serviceScope.launch { sendInstancePresence(instanceId, rpc, config, forceRefresh = true) }
                    }
                    serviceScope.launch { showInstanceNotification(instanceId, config) }
                    startPresenceRefresh()
                }
                return START_REDELIVER_INTENT
            }

            Constants.ACTION_REMOVE_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    multiRpc.closeCustomSession(instanceId)
                    customRpcs.remove(instanceId)
                    customConfigs.remove(instanceId)
                    lastSentConfigs.remove(instanceId)
                    instancePacers.remove(instanceId)
                    rpcManager.removePresenceConfig(instanceId)
                    CloneNotificationHelper.cancel(notificationManager, instanceId)
                    if (foregroundNotificationId == notificationIdFor(instanceId)) {
                        foregroundNotificationId = customRpcs.keys.firstOrNull()?.let(::notificationIdFor)
                            ?: Constants.NOTIFICATION_ID_CUSTOM_RPC
                    }
                }
                if (customRpcs.isEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    customRpcs.forEach { (id, rpc) ->
                        serviceScope.launch {
                            sendInstancePresence(id, rpc, customConfigs[id] ?: RpcConfig(), forceRefresh = true)
                        }
                    }
                }
                return START_REDELIVER_INTENT
            }

            Constants.ACTION_RESTART_SERVICE -> {
                val string = intent.getStringExtra("RPC")
                string?.takeIf { it.isNotBlank() }?.let {
                    rpcData = try {
                        Json.decodeFromString(it)
                    } catch (e: Exception) {
                        Log.e("CustomRpcService", "Failed to parse RPC config JSON: ${e.message}")
                        null
                    }
                }
                Log.d("CustomRpcService", "ACTION_RESTART_SERVICE: rpcData=${rpcData?.name ?: "null"}")
                rpcData?.let { customConfigs[targetId] = it }
                pacerFor(targetId).reset()
                serviceScope.launch {
                    sendPresenceAndNotification(targetId, forceRefresh = true)
                }
                startConsoleDetectionIfNeeded()
                return START_REDELIVER_INTENT
            }
        }

        val string = intent?.getStringExtra("RPC")
        string?.takeIf { it.isNotBlank() }?.let {
            rpcData = try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                Log.e("CustomRpcService", "Failed to parse RPC config JSON: ${e.message}")
                null
            }
        }

        rpcData?.let { customConfigs[targetId] = it }
        Log.d("CustomRpcService", "onStartCommand: rpcData=${rpcData?.name ?: "null"}, details=${rpcData?.details ?: "null"}")
        serviceScope.launch {
            sendPresenceAndNotification(targetId, forceRefresh = true)
        }
        startPresenceRefresh()
        startConsoleDetectionIfNeeded()
        return START_REDELIVER_INTENT
    }

    private fun readConsoleMode(intent: Intent) {
        if (intent.hasExtra(EXTRA_CONSOLE_RPC)) {
            consoleMode = intent.getBooleanExtra(EXTRA_CONSOLE_RPC, false)
        } else if (
            intent.action != Constants.ACTION_RESTART_SERVICE &&
            intent.action != Constants.ACTION_STOP_SERVICE
        ) {
            consoleMode = false
        }
        Prefs[Prefs.CUSTOM_RPC_CONSOLE_MODE] = consoleMode
        if (!consoleMode) {
            consoleDetectionJob?.cancel()
            consoleDetectionJob = null
        }
    }

    private suspend fun sendInstancePresence(
        instanceId: String,
        rpc: KizzyRPC,
        config: RpcConfig,
        forceRefresh: Boolean,
    ) {
        val baseData = if (config.name.isBlank()) {
            config.copy(name = "Custom RPC", details = "Kizzy Enhanced")
        } else config
        val previousConfig = lastSentConfigs[instanceId]
        val preservedStart = if (
            !forceRefresh &&
            previousConfig != null &&
            baseData.name == previousConfig.name &&
            baseData.details == previousConfig.details &&
            baseData.state == previousConfig.state &&
            previousConfig.timestampsStart.isNotBlank()
        ) {
            previousConfig.timestampsStart
        } else {
            baseData.timestampsStart
        }
        val data = baseData.copy(timestampsStart = preservedStart)
        val hasMeaningfulChange = data != previousConfig

        if (!pacerFor(instanceId).acquireSendSlot(hasMeaningfulChange = hasMeaningfulChange, forceRefresh = forceRefresh)) {
            return
        }

        val activeBoard = boardManager.getActiveBoard()

        rpc.clearButtons()
        rpc.setName(data.name)
        rpc.setDetails(data.details.ifEmpty { null })
        rpc.setState(data.state.ifEmpty { null })
        rpc.setPartySize(data.partyCurrentSize.toIntOrNull(), data.partyMaxSize.toIntOrNull())
        rpc.setStatus(data.status.ifEmpty { "online" })
        val customAppId = Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""]
        if (Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false] && customAppId.isNotBlank()) {
            rpc.setApplicationId(customAppId)
        } else if (activeBoard != null && activeBoard.applicationId.isNotBlank()) {
            rpc.setApplicationId(activeBoard.applicationId)
        }
        rpc.setType(data.type.toIntOrNull() ?: 0)
        rpc.setPlatform(data.platform.takeIf { it.isNotBlank() })
        if (Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true]) {
            rpc.setStartTimestamps(data.timestampsStart.toLongOrNull())
            rpc.setStopTimestamps(data.timestampsStop.toLongOrNull())
        }
        rpc.setButton1(data.button1.ifEmpty { null }, data.button1link.ifEmpty { null })
        rpc.setButton2(data.button2.ifEmpty { null }, data.button2link.ifEmpty { null })
        val showCover = Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true] &&
                !Prefs[Prefs.RPC_HIDE_COVER_ART, false]
        rpc.setLargeImage(
            if (showCover) data.largeImg.toRpcImage()
            else Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }
                ?.let { RpcImage.ExternalImage(it) },
            data.largeText,
        )
        rpc.setSmallImage(
            if (showCover) data.smallImg.toRpcImage()
            else Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }
                ?.let { RpcImage.ExternalImage(it) },
            data.smallText,
        )
        rpc.setSmallUrl(data.smallUrl)
        rpc.setStreamUrl(data.url.takeIf { it.isNotBlank() })
        rpc.build(forceRefresh = forceRefresh || hasMeaningfulChange, priority = 2)
        lastSentConfigs[instanceId] = data

        rpcManager.setCurrentPresenceConfig(data.copy(instanceId = instanceId), instanceId)
        showInstanceNotification(instanceId, data)
    }

    private suspend fun showInstanceNotification(instanceId: String, data: RpcConfig) {
        val instanceNotificationId = notificationIdFor(instanceId)
        val stopIntent = Intent(this@CustomRpcService, CustomRpcService::class.java).apply {
            action = Constants.ACTION_REMOVE_INSTANCE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
        }
        val stopPendingIntent = PendingIntent.getService(
            this@CustomRpcService, instanceNotificationId + 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val restartIntent = Intent(this@CustomRpcService, CustomRpcService::class.java).apply {
            action = Constants.ACTION_RESTART_SERVICE
            putExtra(Constants.EXTRA_INSTANCE_ID, instanceId)
            putExtra("RPC", Json.encodeToString(data))
            putExtra(EXTRA_CONSOLE_RPC, consoleMode)
        }
        val restartPendingIntent = PendingIntent.getService(
            this@CustomRpcService, instanceNotificationId + 2, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val appPendingIntent = PendingIntent.getActivity(
            this@CustomRpcService, instanceNotificationId + 3, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this@CustomRpcService, Constants.CHANNEL_ID_RPC_INSTANCES)
            .setContentTitle(data.name.ifBlank { getString(R.string.custom_rpc_running) })
            .setContentText(
                listOfNotNull(data.details, data.state).joinToString(" · ")
                    .ifBlank { getString(R.string.custom_rpc_running) }
            )
            .setSmallIcon(R.drawable.ic_rpc_placeholder)
            .setContentIntent(appPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this@CustomRpcService, R.drawable.ic_rpc_placeholder),
                    getString(R.string.restart), restartPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this@CustomRpcService, R.drawable.ic_rpc_placeholder),
                    getString(R.string.exit), stopPendingIntent
                ).build()
            )
            .setLargeIcon(data.largeImg.toRpcImage(), this@CustomRpcService)
            .build()

        val now = System.currentTimeMillis()
        if (now - (lastNotificationUpdateMs[instanceId] ?: 0L) < 1000) return
        lastNotificationUpdateMs[instanceId] = now

        notificationManager.notify(instanceNotificationId, notification)
    }

    private fun startPresenceRefresh() {
        if (presenceRefreshJob?.isActive == true) return
        presenceRefreshJob = serviceScope.launch {
            while (isActive) {
                delay(MAX_RPC_IDLE_INTERVAL_MS)
                try {
                    customRpcs.forEach { (id, rpc) ->
                        sendInstancePresence(id, rpc, customConfigs[id] ?: RpcConfig(), forceRefresh = false)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun startConsoleDetectionIfNeeded() {
        if (!consoleMode) return
        if (!Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true]) return
        if (consoleDetectionJob?.isActive == true) return

        consoleDetectionJob = serviceScope.launch {
            while (isActive) {
                try {
                    if (Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true]) {
                        refreshDetectedConsole()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(CONSOLE_DETECTION_INTERVAL_MS)
            }
        }
    }

    private fun refreshDetectedConsole() {
        val current = rpcData ?: return
        val includeSpoofed = Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED, false]
        val packageName = foregroundPackage() ?: return
        val console = ConsoleAppRegistry.resolve(packageName, includeSpoofed) ?: return

        val gameTitle = ActiveGameTracker.titleFor(packageName) ?: return
        val matchedGame = ConsoleGameCatalog.match(gameTitle)
        val platformName = console.platform.displayName

        val updated = current.copy(
            name = platformName,
            details = gameTitle,
            type = "0",
            largeImg = matchedGame?.large_image.orEmpty(),
            smallImg = matchedGame?.small_image.orEmpty(),
        )

        if (updated == current) return

        rpcData = updated
        customConfigs[RPC_INSTANCE_CUSTOM] = updated
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = Json.encodeToString(updated)
        serviceScope.launch {
            sendPresenceAndNotification(RPC_INSTANCE_CUSTOM, forceRefresh = true)
        }
    }

    private suspend fun sendPresenceAndNotification(instanceId: String, forceRefresh: Boolean = false) {
        val activeBoard = boardManager.getActiveBoard()
        val rawRpcData: RpcConfig = if (activeBoard != null) {
            Log.d("CustomRpcService", "Using active board: ${activeBoard.id} (${activeBoard.label})")
            RpcConfig(
                name = activeBoard.name,
                details = activeBoard.details,
                state = activeBoard.state,
                type = activeBoard.activityType.toString(),
                largeImg = activeBoard.largeImage,
                smallImg = activeBoard.smallImage,
                largeText = activeBoard.largeText,
                smallText = activeBoard.smallText,
                button1 = activeBoard.button1Label,
                button1link = activeBoard.button1Url,
                button2 = activeBoard.button2Label,
                button2link = activeBoard.button2Url,
                url = if (activeBoard.activityType == 1) activeBoard.name else "",
                status = "online",
            )
        } else {
            rpcData ?: try {
                Json.decodeFromString<RpcConfig>(Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""])
            } catch (_: Exception) {
                null
            } ?: RpcConfig()
        }

        val baseData = if (rawRpcData.name.isBlank()) {
            rawRpcData.copy(name = "Custom RPC", details = "Kizzy Enhanced", state = "")
        } else {
            rawRpcData
        }

        val previousConfig = lastSentConfigs[instanceId]
        val preservedStart = if (
            !forceRefresh &&
            previousConfig != null &&
            baseData.name == previousConfig.name &&
            baseData.details == previousConfig.details &&
            baseData.state == previousConfig.state &&
            previousConfig.timestampsStart.isNotBlank()
        ) {
            previousConfig.timestampsStart
        } else {
            baseData.timestampsStart
        }
        val rpcDataToUse = baseData.copy(timestampsStart = preservedStart)
        val hasMeaningfulChange = rpcDataToUse != previousConfig

        if (!pacerFor(instanceId).acquireSendSlot(hasMeaningfulChange = hasMeaningfulChange, forceRefresh = forceRefresh)) {
            return
        }

        Log.d("CustomRpcService", "sendPresenceAndNotification: forceRefresh=$forceRefresh, rpcDataToUse.name=${rpcDataToUse.name}, rpcDataToUse.details=${rpcDataToUse.details}, rpcDataToUse.state=${rpcDataToUse.state}")

        val notificationId = notificationIdFor(instanceId)
        val stopIntent = Intent(this, CustomRpcService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        val pendingIntent: PendingIntent = PendingIntent.getService(
            this, notificationId + 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(this, CustomRpcService::class.java).apply {
            action = Constants.ACTION_RESTART_SERVICE
            putExtra("RPC", Json.encodeToString(rpcDataToUse))
            putExtra(EXTRA_CONSOLE_RPC, consoleMode)
        }
        val restartPendingIntent: PendingIntent = PendingIntent.getService(
            this, notificationId + 2, restartIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        appIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        val appPendingIntent = PendingIntent.getActivity(
            this, notificationId + 3, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wakeLock?.acquire(10_000)
        } else {
            wakeLock?.acquire()
        }
        try {
            val notification = Notification.Builder(this@CustomRpcService, Constants.CHANNEL_ID_RPC_INSTANCES)
                .setContentTitle(rpcDataToUse.name.ifBlank { getString(R.string.custom_rpc_running) })
                .setContentText(
                    listOfNotNull(rpcDataToUse.details, rpcDataToUse.state)
                        .joinToString(" · ")
                        .ifBlank { getString(R.string.custom_rpc_running) }
                )
                .setSmallIcon(R.drawable.ic_rpc_placeholder)
                .setContentIntent(appPendingIntent)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@CustomRpcService, R.drawable.ic_rpc_placeholder),
                        getString(R.string.restart),
                        restartPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this@CustomRpcService, R.drawable.ic_rpc_placeholder),
                        getString(R.string.exit),
                        pendingIntent
                    ).build()
                )
                .setLargeIcon(rpcDataToUse.largeImg.toRpcImage(), this@CustomRpcService)
                .build()

            notificationManager.notify(notificationId, notification)

            kizzyRPC?.apply {
                setName(rpcDataToUse.name.ifEmpty { "" })
                setDetails(rpcDataToUse.details.ifEmpty { null })
                setState(rpcDataToUse.state.ifEmpty { null })
                setPartySize(rpcDataToUse.partyCurrentSize.toIntOrNull(), rpcDataToUse.partyMaxSize.toIntOrNull())
                setStatus(rpcDataToUse.status.ifEmpty { "online" })
                val useCustomAppId = Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false]
                val customAppId = Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""]
                if (useCustomAppId && customAppId.isNotBlank()) {
                    setApplicationId(customAppId)
                } else if (activeBoard != null && activeBoard.applicationId.isNotBlank()) {
                    setApplicationId(activeBoard.applicationId)
                }
                setType(rpcDataToUse.type.toIntOrNull() ?: 0)
                setPlatform(rpcDataToUse.platform.takeIf { it.isNotBlank() })
                val enableTimestamps = Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true]
                if (enableTimestamps) {
                    setStartTimestamps(rpcDataToUse.timestampsStart.toLongOrNull())
                    setStopTimestamps(rpcDataToUse.timestampsStop.toLongOrNull())
                }
                setButton1(rpcDataToUse.button1.ifEmpty { null }, rpcDataToUse.button1link.ifEmpty { null })
                setButton2(rpcDataToUse.button2.ifEmpty { null }, rpcDataToUse.button2link.ifEmpty { null })
                setLargeImage(
                    if (Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true] && !Prefs[Prefs.RPC_HIDE_COVER_ART, false]) {
                        rpcDataToUse.largeImg.toRpcImage()
                    } else {
                        Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
                    },
                    rpcDataToUse.largeText
                )
                setSmallImage(
                    if (Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true] && !Prefs[Prefs.RPC_HIDE_COVER_ART, false]) {
                        rpcDataToUse.smallImg.toRpcImage()
                    } else {
                        Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
                    },
                    rpcDataToUse.smallText
                )
                setStreamUrl(rpcDataToUse.url.takeIf { it.isNotBlank() })
                Log.d("CustomRpcService", "Calling kizzyRPC?.build(forceRefresh=${forceRefresh || hasMeaningfulChange}, priority=2)")
                build(forceRefresh = forceRefresh || hasMeaningfulChange, priority = 2)
                lastSentConfigs[instanceId] = rpcDataToUse
                Log.d("CustomRpcService", "kizzyRPC?.build() completed")

                rpcManager.setCurrentPresenceConfig(rpcDataToUse.copy(instanceId = instanceId), instanceId)
            }
        } finally {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
    }

    override fun onDestroy() {
        consoleDetectionJob?.cancel()
        consoleDetectionJob = null
        presenceRefreshJob?.cancel()
        presenceRefreshJob = null
        customRpcs.keys.toList().forEach { CloneNotificationHelper.cancel(notificationManager, it) }
        notificationManager.cancel(foregroundNotificationId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceJob.cancel()
        val rpcs = customRpcs.values.toList() + listOfNotNull(kizzyRPC).distinct()
        customRpcs.keys.toList().forEach { multiRpc.closeCustomSession(it) }
        (customRpcs.keys + listOfNotNull(kizzyRPC?.let { "custom_rpc" })).distinct()
            .forEach { rpcManager.removePresenceConfig(it) }
        customRpcs.clear()
        customConfigs.clear()
        lastSentConfigs.clear()
        instancePacers.clear()
        AppUtils.unregisterRpcService(this)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            rpcs.forEach { rpc ->
                try {
                    rpc.clearPresence()
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(intent: Intent?) {
        Log.d("CustomRpcService", "onTaskRemoved: app swiped from Recents - keeping Custom RPC alive")
        WorkManagerHelper.scheduleRpcRecovery(this)
        super.onTaskRemoved(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val WAKELOCK = "kizzy:CustomRPC"

        const val EXTRA_CONSOLE_RPC = "CONSOLE_RPC"

        private const val CONSOLE_DETECTION_INTERVAL_MS = 5000L
    }
}