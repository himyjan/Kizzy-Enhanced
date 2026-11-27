package com.my.kizzy.feature_rpc_base.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.resources.R

/**
 * Posts one independent notification per RPC clone.
 *
 * Android shows only ONE foreground notification per Service, so clones cannot
 * use startForeground(). Instead each clone gets a separate notification via
 * NotificationManager.notify() with a unique id on a DEFAULT-importance
 * channel, which keeps the cards ungrouped and visible.
 */
object CloneNotificationHelper {

    private val ids = mutableMapOf<String, Int>()
    private var nextId = Constants.NOTIFICATION_ID_CLONE_BASE

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

        notificationManager.notify(id, notification)
    }

    fun cancel(
        notificationManager: NotificationManager,
        instanceId: String,
    ) {
        release(instanceId)?.let { notificationManager.cancel(it) }
    }
}
