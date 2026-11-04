package com.my.kizzy.feature_rpc_base.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledPackages = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return enabledPackages?.contains(context.packageName) ?: false
}

fun openNotificationListenerSettings(context: Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    context.startActivity(intent)
}

fun startDiscordRpcService(context: Context) {
    val intent = android.content.Intent(context, com.my.kizzy.feature_rpc_base.services.MediaRpcService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

fun checkNotificationListenerPermission(context: Context): Boolean {
    return isNotificationListenerEnabled(context)
}