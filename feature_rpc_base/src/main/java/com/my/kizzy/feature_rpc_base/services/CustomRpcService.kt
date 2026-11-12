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
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.setLargeIcon
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class CustomRpcService : Service() {
    private var rpcData: RpcConfig? = null
    private var wakeLock: WakeLock? = null

    @Inject
    lateinit var kizzyRPC: com.my.kizzy.data.rpc.KizzyRPC

    @Inject
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var notificationBuilder: Notification.Builder

    @Inject
    lateinit var notificationManager: NotificationManager

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action.equals(Constants.ACTION_STOP_SERVICE)) stopSelf()
        else {
            val string = intent?.getStringExtra("RPC")
            string?.takeIf { it.isNotBlank() }?.let {
                rpcData = try {
                    Json.decodeFromString(it)
                } catch (e: Exception) {
                    null
                }
            }

            val stopIntent = Intent(this, CustomRpcService::class.java)
            stopIntent.action = Constants.ACTION_STOP_SERVICE
            val pendingIntent: PendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
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

            val notification = Notification.Builder(this@CustomRpcService, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.custom_rpc_running))
                .setContentText(rpcData?.name ?: "")
                .setSmallIcon(R.drawable.ic_rpc_placeholder)
                .setContentIntent(appPendingIntent)
                .addAction(R.drawable.ic_rpc_placeholder, getString(R.string.exit), pendingIntent)
                .build()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                startForeground(Constants.NOTIFICATION_ID, notification)
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK)
            wakeLock?.acquire()
            scope.launch {
                notificationManager.notify(
                    Constants.NOTIFICATION_ID,
                    Notification.Builder(this@CustomRpcService, Constants.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_rpc_placeholder)
                        .setLargeIcon(
                            rpcImage = rpcData?.largeImg?.toRpcImage(),
                            context = this@CustomRpcService
                        )
                        .build()
                )

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
                        build()
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
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
    }
}
