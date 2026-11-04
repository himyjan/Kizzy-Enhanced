package com.my.kizzy.feature_rpc_base.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Checks if notification listener access is granted for this app.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledPackages = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return enabledPackages?.contains(context.packageName) ?: false
}

/**
 * Opens system notification listener settings so the user can grant access.
 */
fun openNotificationListenerSettings(context: Context) {
    val intent = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

/**
 * Starts the foreground service for media RPC across different Android versions.
 */
fun startDiscordRpcService(context: Context) {
    val intent = android.content.Intent(context, com.my.kizzy.feature_rpc_base.services.MediaRpcService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

/**
 * Prompts user to grant notification listener access with rationale dialog.
 * Returns true if permission is already granted, false otherwise.
 */
fun checkNotificationListenerPermission(context: Context): Boolean {
    return isNotificationListenerEnabled(context)
}
