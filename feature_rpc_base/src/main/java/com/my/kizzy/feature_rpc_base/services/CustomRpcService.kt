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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import com.my.kizzy.data.get_current_data.app.GetForegroundPackage
import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class CustomRpcService : Service() {
    private var rpcData: RpcConfig? = null
    private var wakeLock: WakeLock? = null

    @Inject
    lateinit var kizzyRPC: com.my.kizzy.data.rpc.KizzyRPC

    @Inject
    lateinit var foregroundPackage: GetForegroundPackage

    @Inject
    lateinit var scope: CoroutineScope

    /** True when this service instance was started by the Console RPC screen. */
    private var consoleMode = false

    /** Polls the foreground app while Console RPC should follow DroidDeck/Eden. */
    private var consoleDetectionJob: Job? = null

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var notificationManager: NotificationManager

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { readConsoleMode(it) }

        intent?.action?.let { ac ->
            if (ac == Constants.ACTION_STOP_SERVICE) {
                stopSelf()
                return super.onStartCommand(intent, flags, startId)
            }
            if (ac == Constants.ACTION_RESTART_SERVICE) {
                // Restart in place: re-read the RPC config and resend presence.
                // forceRefresh=true ensures the presence is actually sent even
                // when the WebSocket is already connected (build() otherwise
                // returns early without sending).
                val string = intent?.getStringExtra("RPC")
                string?.takeIf { it.isNotBlank() }?.let {
                    rpcData = try {
                        Json.decodeFromString(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                sendPresenceAndNotification(forceRefresh = true)
                startConsoleDetectionIfNeeded()
                return super.onStartCommand(intent, flags, startId)
            }
        }

        val string = intent?.getStringExtra("RPC")
        string?.takeIf { it.isNotBlank() }?.let {
            rpcData = try {
                Json.decodeFromString(it)
            } catch (e: Exception) {
                null
            }
        }

        sendPresenceAndNotification()
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
        if (!consoleMode) {
            consoleDetectionJob?.cancel()
            consoleDetectionJob = null
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
        sendPresenceAndNotification()
    }

    /**
     * Builds the notification and sends the presence to Discord.
     * Extracted so both the initial start and the restart action can reuse it.
     *
     * @param forceRefresh when true, the presence is sent even if the WebSocket is
     *   already connected (bypasses the "already connected" early return in
     *   KizzyRPC.build()). Used by the notification restart button.
     */
    private fun sendPresenceAndNotification(forceRefresh: Boolean = false) {
        val stopIntent = Intent(this, CustomRpcService::class.java)
        stopIntent.action = Constants.ACTION_STOP_SERVICE
        val pendingIntent: PendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val restartIntent = Intent(this, CustomRpcService::class.java)
        restartIntent.action = Constants.ACTION_RESTART_SERVICE
        restartIntent.putExtra("RPC", if (rpcData != null) Json.encodeToString(rpcData) else "")
        restartIntent.putExtra(EXTRA_CONSOLE_RPC, consoleMode)
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
            val notification = Notification.Builder(this@CustomRpcService, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.custom_rpc_running))
                .setContentText(rpcData?.name ?: "")
                .setSmallIcon(R.drawable.ic_rpc_placeholder)
                .setContentIntent(appPendingIntent)
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_rpc_placeholder,
                        getString(R.string.restart),
                        restartPendingIntent
                    ).build()
                )
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_rpc_placeholder,
                        getString(R.string.exit),
                        pendingIntent
                    ).build()
                )
                .setLargeIcon(
                    rpcImage = rpcData?.largeImg?.toRpcImage(),
                    context = this@CustomRpcService
                )
                .build()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(Constants.NOTIFICATION_ID, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }

            kizzyRPC.apply {
                rpcData?.let {
                    setName(it.name.ifEmpty { "" })
                    setDetails(it.details.ifEmpty { null })
                    setState(it.state.ifEmpty { null })
                    setPartySize(it.partyCurrentSize.toIntOrNull(), it.partyMaxSize.toIntOrNull())
                    setStatus(it.status.ifEmpty { "online" })
                    setType(it.type.toIntOrNull() ?: 0)
                    setPlatform(it.platform.ifEmpty { null })
                    setStartTimestamps(it.timestampsStart.toLongOrNull())
                    setStopTimestamps(it.timestampsStop.toLongOrNull())
                    setButton1(it.button1.ifEmpty { null }, it.button1link.ifEmpty { null })
                    setButton2(it.button2.ifEmpty { null }, it.button2link.ifEmpty { null })
                    setLargeImage(it.largeImg.toRpcImage(), it.largeText)
                    setSmallImage(it.smallImg.toRpcImage(), it.smallText)
                    setStreamUrl(it.url.ifEmpty { null })
                    build(forceRefresh = forceRefresh)
                }
            }
        }
    }

    override fun onDestroy() {
        consoleDetectionJob?.cancel()
        consoleDetectionJob = null
        scope.cancel()
        kizzyRPC.closeRPC()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
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
