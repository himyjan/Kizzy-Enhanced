package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleNotificationParser
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.preference.Prefs

class NotificationListener : NotificationListenerService() {

    private var watchdogHandler: Handler? = null
    private var watchdogRunnable: Runnable? = null

    companion object {
        private const val WATCHDOG_INTERVAL_MS = 30_000L
    }

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

    private fun startWatchdog() {
        stopWatchdog()
        val handler = Handler(Looper.getMainLooper())
        watchdogHandler = handler
        val runnable = object : Runnable {
            override fun run() {
                try {
                    checkMediaRpcService()
                } catch (_: Exception) {
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

    private fun checkMediaRpcService() {
        if (!Prefs[Prefs.MEDIA_RPC_ENABLED, false]) return
        if (AppUtils.mediaRpcRunning()) return

        Log.d("NotificationListener", "Media RPC is enabled but not running - starting it")
        val intent = Intent(this, MediaRpcService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val packageName = sbn?.packageName ?: return
        if (!ConsoleAppRegistry.isKnown(packageName, includeSpoofed = true)) return
        val extras = sbn.notification?.extras ?: return
        val title = ConsoleNotificationParser.pickTitle(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        )
        ActiveGameTracker.update(packageName, title)
        Log.d("NotificationListener", "Console notification from $packageName: game='${title ?: ""}'")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.packageName?.let { ActiveGameTracker.clearIfPackage(it) }
    }

    override fun onDestroy() {
        stopWatchdog()
        super.onDestroy()
    }
}