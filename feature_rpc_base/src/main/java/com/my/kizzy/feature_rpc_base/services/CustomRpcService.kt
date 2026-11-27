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
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.graphics.drawable.Icon
import android.util.Log
import com.my.kizzy.data.get_current_data.app.GetForegroundPackage
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.data.rpc.CustomRpcBoardItem
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
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class CustomRpcService : Service() {
    private var rpcData: RpcConfig? = null
    private var wakeLock: WakeLock? = null

    private var kizzyRPC: com.my.kizzy.data.rpc.KizzyRPC? = null

    /** Per-instance Custom RPC sessions and their own RpcConfig. */
    private val customRpcs = LinkedHashMap<String, KizzyRPC>()
    private val customConfigs = LinkedHashMap<String, RpcConfig>()
    /** Stable notification id for the instance currently in the foreground. */
    private var foregroundNotificationId: Int = Constants.NOTIFICATION_ID_CUSTOM_RPC

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
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var boardManager: CustomRpcBoardManager

    /** True when this service instance was started by the Console RPC screen. */
    private var consoleMode = false

    /** Polls the foreground app while Console RPC should follow DroidDeck/Eden. */
    private var consoleDetectionJob: Job? = null

    /** Keeps the activity alive; Discord drops idle presences after a few seconds. */
    private var presenceRefreshJob: Job? = null

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var notificationManager: NotificationManager

    @Volatile
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
        // Start ONE foreground notification for the service. Each clone later
        // gets its own independent notification via notificationManager.notify().
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
        // Each Custom RPC instance owns its own KizzyRPC/socket so it can show
        // completely different presence data from the others.
        val targetId = intent?.getStringExtra(Constants.EXTRA_INSTANCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: RPC_INSTANCE_CUSTOM
        foregroundNotificationId = notificationIdFor(targetId)
        kizzyRPC = multiRpc.customSession(targetId)?.also {
            customRpcs[targetId] = it
        }

        intent?.let { readConsoleMode(it) }

        if (intent?.action == Constants.ACTION_STOP_SERVICE) {
            // Explicit stop: the next cold start must not bring it back.
            Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
            // Remove Custom/Console RPC from ACTIVE_RPC_TYPES so a cold start doesn't restore it.
            val remainingTypes = Prefs[Prefs.ACTIVE_RPC_TYPES, ""]
                .split(',')
                .mapNotNull { runCatching { RpcManager.RpcType.valueOf(it.trim()) }.getOrNull() }
                .filter { it != RpcManager.RpcType.CustomRpc && it != RpcManager.RpcType.ConsoleRpc }
                .joinToString(",") { it.name }
            Prefs[Prefs.ACTIVE_RPC_TYPES] = remainingTypes
            // Also clear RpcManager's active set so refreshFromServices() doesn't
            // resurrect this service when the screen resumes.
            rpcManager.stopRpcTypes(setOf(RpcManager.RpcType.CustomRpc, RpcManager.RpcType.ConsoleRpc))
            stopSelf()
            return super.onStartCommand(intent, flags, startId)
        } else {
            Prefs[Prefs.ACTIVE_RPC_TYPE] =
                if (consoleMode) RpcManager.RpcType.ConsoleRpc.name
                else RpcManager.RpcType.CustomRpc.name
        }

        Log.d("CustomRpcService", "onStartCommand: intent action=${intent?.action}, hasRPCExtra=${intent?.getStringExtra("RPC")?.isNotBlank() == true}")

        // Foreground notification is started once in onCreate(). Do NOT call
        // startForeground() again here: it would replace the foreground slot
        // and cancel the previous clone's notification.

        // Parallel instance sockets: add/remove one socket without disturbing
        // the presence or connection of any other instance.
        when (intent?.action) {
            Constants.ACTION_ADD_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    // The instance's own RpcConfig travels in the intent; each
                    // clone must show its own data, not the global config.
                    val config = intent.getStringExtra("RPC")?.takeIf { it.isNotBlank() }
                        ?.let { raw ->
                            runCatching { Json.decodeFromString<RpcConfig>(raw) }
                                .getOrElse { e ->
                                    Log.e("CustomRpcService", "Failed to parse instance RPC config: ${e.message}")
                                    null
                                }
                        } ?: RpcConfig(name = "Custom RPC", details = "Kizzy Enhanced")
                    customConfigs[instanceId] = config
                    val rpc = multiRpc.customSession(instanceId)
                    if (rpc != null) {
                        customRpcs[instanceId] = rpc
                        scope.launch { sendInstancePresence(instanceId, rpc, config, forceRefresh = true) }
                    }
                    // Always show this clone's own notification, even if the socket
                    // is not ready yet, so every enabled RPC has a visible card.
                    scope.launch { showInstanceNotification(instanceId, config) }
                    startPresenceRefresh()
                }
                return super.onStartCommand(intent, flags, startId)
            }
            Constants.ACTION_REMOVE_INSTANCE -> {
                val instanceId = intent.getStringExtra(Constants.EXTRA_INSTANCE_ID)
                if (!instanceId.isNullOrBlank()) {
                    multiRpc.closeCustomSession(instanceId)
                    customRpcs.remove(instanceId)
                    customConfigs.remove(instanceId)
                    // Remove the profile preview config for this instance.
                    rpcManager.removePresenceConfig(instanceId)
                    // Only this instance's notification goes away; other custom
                    // RPC instances keep running and keep their own notification.
                    CloneNotificationHelper.cancel(notificationManager, instanceId)
                    if (foregroundNotificationId == notificationIdFor(instanceId)) {
                        foregroundNotificationId = customRpcs.keys.firstOrNull()?.let(::notificationIdFor)
                            ?: Constants.NOTIFICATION_ID_CUSTOM_RPC
                    }
                }
                if (customRpcs.isEmpty()) {
                    stopSelf()
                } else {
                    customRpcs.forEach { (id, rpc) ->
                        scope.launch {
                            sendInstancePresence(id, rpc, customConfigs[id] ?: RpcConfig(), forceRefresh = true)
                        }
                    }
                }
                return super.onStartCommand(intent, flags, startId)
            }
        }

        intent?.action?.let { ac ->
            if (ac == Constants.ACTION_RESTART_SERVICE) {
                // Restart in place: re-read the RPC config and resend presence.
                // forceRefresh=true ensures the presence is actually sent even
                // when the WebSocket is already connected (build() otherwise
                // returns early without sending).
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
                rpcData?.let { customConfigs["custom_rpc"] = it }
                scope.launch {
                    sendPresenceAndNotification(targetId, forceRefresh = true)
                }
                startConsoleDetectionIfNeeded()
                return super.onStartCommand(intent, flags, startId)
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

        rpcData?.let { customConfigs["custom_rpc"] = it }
        Log.d("CustomRpcService", "onStartCommand: rpcData=${rpcData?.name ?: "null"}, details=${rpcData?.details ?: "null"}")
        scope.launch {
            sendPresenceAndNotification(targetId)
        }
        startPresenceRefresh()
        startConsoleDetectionIfNeeded()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Console RPC and Custom RPC share this service. The screen marks its
     * intent so the service only starts the DroidDeck/Eden watcher for a
     * Console RPC session.
     */
    private fun readConsoleMode(intent: Intent) {
        if (intent.hasExtra(EXTRA_CONSOLE_RPC)) {
            consoleMode = intent.getBooleanExtra(EXTRA_CONSOLE_RPC, false)
        } else if (
            intent.action != Constants.ACTION_RESTART_SERVICE &&
            intent.action != Constants.ACTION_STOP_SERVICE
        ) {
            // A normal start without the marker is a Custom RPC session. The
            // same service instance may still be running from a Console RPC
            // session, so the watcher has to be switched off.
            consoleMode = false
        }
        // Persist the mode so AppUtils can tell Custom from Console without
        // relying on a single ACTIVE_RPC_TYPE (several RPCs run in parallel).
        Prefs[Prefs.CUSTOM_RPC_CONSOLE_MODE] = consoleMode
        if (!consoleMode) {
            consoleDetectionJob?.cancel()
            consoleDetectionJob = null
        }
    }

    /** Applies one instance's own RpcConfig to its dedicated KizzyRPC. */
    private suspend fun sendInstancePresence(
        instanceId: String,
        rpc: KizzyRPC,
        config: RpcConfig,
        forceRefresh: Boolean,
    ) {
        val data = if (config.name.isBlank()) {
            config.copy(name = "Custom RPC", details = "Kizzy Enhanced")
        } else config
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
        rpc.build(forceRefresh = forceRefresh, priority = 2)

        // Register this clone's own config so the profile page shows a preview
        // for EVERY instance, not just the built-in one.
        rpcManager.setCurrentPresenceConfig(data.copy(instanceId = instanceId), instanceId)

        // Service is already foreground (started in onCreate()). Every instance gets
        // its own independent notification via notify(), so 5 clones show 5 cards.
        showInstanceNotification(instanceId, data)
    }

    /** Shows (or updates) one instance's independent notification. */
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
            .setContentText(listOfNotNull(data.details, data.state).joinToString(" · ")
                .ifBlank { getString(R.string.custom_rpc_running) })
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
        notificationManager.notify(instanceNotificationId, notification)
    }

    /** Re-sends the Custom RPC activity periodically so Discord never drops it. */
    private fun startPresenceRefresh() {
        if (presenceRefreshJob?.isActive == true) return
        presenceRefreshJob = scope.launch {
            while (isActive) {
                delay(10_000)
                try {
                    customRpcs.forEach { (id, rpc) ->
                        sendInstancePresence(id, rpc, customConfigs[id] ?: RpcConfig(), forceRefresh = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A failed refresh is retried on the next tick.
                }
            }
        }
    }

    /**
     * Starts the watcher that keeps the presence on the game DroidDeck/Eden is
     * running. It is only active for Console RPC and only while the user has
     * not switched the auto-detection off.
     */
    private fun startConsoleDetectionIfNeeded() {
        if (!consoleMode) return
        if (!Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true]) return
        if (consoleDetectionJob?.isActive == true) return

        consoleDetectionJob = scope.launch {
            while (isActive) {
                try {
                    if (Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true]) {
                        refreshDetectedConsole()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Detection must never take the RPC service down.
                }
                delay(CONSOLE_DETECTION_INTERVAL_MS)
            }
        }
    }

    /**
     * Reads the foreground app and, when it is DroidDeck or Eden, replaces the
     * console presence with the game that the launcher reports.
     *
     * Only a change is sent. When the user switches to a different app the
     * last detected game stays on Discord, which is the expected behaviour for
     * a console presence.
     */
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
        Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = Json.encodeToString(updated)
        scope.launch {
            sendPresenceAndNotification(RPC_INSTANCE_CUSTOM)
        }
    }

    /**
     * Builds the notification and sends the presence to Discord.
     * Extracted so both the initial start and the restart action can reuse it.
     *
     * @param forceRefresh when true, the presence is sent even if the WebSocket is
     *   already connected (bypasses the "already connected" early return in
     *   KizzyRPC.build()). Used by the notification restart button.
     */
    private suspend fun sendPresenceAndNotification(instanceId: String, forceRefresh: Boolean = false) {
        // If there is an active custom RPC board, use its fields to build the
        // presence instead of the stored RpcConfig.
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
            } catch (e: Exception) {
                null
            } ?: RpcConfig()
        }

        // Discord drops activities with an empty name. When the user enables
        // Custom RPC without a saved config, send a valid default activity so
        // the instance is actually visible alongside the other parallel RPCs.
        val rpcDataToUse = if (rawRpcData.name.isBlank()) {
            rawRpcData.copy(name = "Custom RPC", details = "Kizzy Enhanced", state = "")
        } else {
            rawRpcData
        }

        Log.d("CustomRpcService", "sendPresenceAndNotification: forceRefresh=$forceRefresh, rpcDataToUse.name=${rpcDataToUse.name}, rpcDataToUse.details=${rpcDataToUse.details}, rpcDataToUse.state=${rpcDataToUse.state}")

        // Restart/Exit must be distinct PendingIntents: a shared request code
        // makes the second action overwrite the first, leaving one button dead.
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
            this, notificationId + 3, appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        // Auto-detection may update the presence several times. Releasing the
        // previous lock first keeps the wake-lock count at one instead of
        // stacking a new lock on every update.
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK)
        wakeLock?.acquire()
        scope.launch {
            // Build the complete notification (content intent, actions, and large icon)
            // in one place so nothing is dropped. A second notify() call had previously
            // replaced the notification entirely, removing its click intent and buttons.
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

            // Service is already foreground (started in onCreate()); update this
            // instance's notification without promoting/replacing the foreground slot.
            notificationManager.notify(notificationId, notification)

            rpcDataToUse.let {
                kizzyRPC?.apply {
                    setName(it.name.ifEmpty { "" })
                    setDetails(it.details.ifEmpty { null })
                    setState(it.state.ifEmpty { null })
                    setPartySize(it.partyCurrentSize.toIntOrNull(), it.partyMaxSize.toIntOrNull())
                    setStatus(it.status.ifEmpty { "online" })
                    // Apply custom application ID when user configured it
                    val useCustomAppId = Prefs[Prefs.MANUAL_RPC_USE_CUSTOM_APP_ID, false]
                    val customAppId = Prefs[Prefs.MANUAL_RPC_APPLICATION_ID, ""]
                    if (useCustomAppId && customAppId.isNotBlank()) {
                        setApplicationId(customAppId)
                    } else if (activeBoard != null && activeBoard.applicationId.isNotBlank()) {
                        setApplicationId(activeBoard.applicationId)
                    }
                    setType(it.type.toIntOrNull() ?: 0)
                    // Platform applies to both Custom and Console RPC
                    setPlatform(it.platform.takeIf { it.isNotBlank() })
                    // Only set timestamps when enabled
                    val enableTimestamps = Prefs[Prefs.MANUAL_RPC_ENABLE_TIMESTAMPS, true]
                    if (enableTimestamps) {
                        setStartTimestamps(it.timestampsStart.toLongOrNull())
                        setStopTimestamps(it.timestampsStop.toLongOrNull())
                    }
                    setButton1(it.button1.ifEmpty { null }, it.button1link.ifEmpty { null })
                    setButton2(it.button2.ifEmpty { null }, it.button2link.ifEmpty { null })
                    setLargeImage(
                        if (Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true] && Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false) {
                            it.largeImg.toRpcImage()
                        } else {
                            Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
                        },
                        it.largeText
                    )
                    setSmallImage(
                        if (Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true] && Prefs[Prefs.RPC_HIDE_COVER_ART, false] == false) {
                            it.smallImg.toRpcImage()
                        } else {
                            Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""].takeIf { it.isNotEmpty() }?.let { RpcImage.ExternalImage(it) }
                        },
                        it.smallText
                    )
                    setStreamUrl(it.url.takeIf { it.isNotBlank() })
                    Log.d("CustomRpcService", "Calling kizzyRPC?.build(forceRefresh=$forceRefresh, priority=2)")
                    build(forceRefresh = forceRefresh, priority = 2) // Custom/Console RPC: user-configured, priority 2
                    Log.d("CustomRpcService", "kizzyRPC?.build() completed")

                    rpcManager.setCurrentPresenceConfig(rpcDataToUse.copy(instanceId = instanceId), instanceId)
                }
            }
        }
    }

    override fun onDestroy() {
        consoleDetectionJob?.cancel()
        consoleDetectionJob = null
        presenceRefreshJob?.cancel()
        presenceRefreshJob = null
        // Remove each instance's notification so stale notifications
        // don't linger after the Custom RPC service is stopped.
        customRpcs.keys.toList().forEach { CloneNotificationHelper.cancel(notificationManager, it) }
        notificationManager.cancel(foregroundNotificationId)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        // Cancel pending work first, then clear/close off the main thread.
        scope.cancel()
        val rpcs = customRpcs.values.toList() + listOfNotNull(kizzyRPC).distinct()
        customRpcs.keys.toList().forEach { multiRpc.closeCustomSession(it) }
        // Remove all profile preview configs registered by this service.
        (customRpcs.keys + listOfNotNull(kizzyRPC?.let { "custom_rpc" })).distinct()
            .forEach { rpcManager.removePresenceConfig(it) }
        customRpcs.clear()
        customConfigs.clear()
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
        com.my.kizzy.data.work.WorkManagerHelper.scheduleRpcRecovery(this)
        super.onTaskRemoved(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val WAKELOCK = "kizzy:CustomRPC"

        /**
         * Marks an intent as belonging to Console RPC. Custom RPC uses the same
         * service, so the DroidDeck/Eden watcher must only run for console
         * sessions.
         */
        const val EXTRA_CONSOLE_RPC = "CONSOLE_RPC"

        private const val CONSOLE_DETECTION_INTERVAL_MS = 5000L
    }
}