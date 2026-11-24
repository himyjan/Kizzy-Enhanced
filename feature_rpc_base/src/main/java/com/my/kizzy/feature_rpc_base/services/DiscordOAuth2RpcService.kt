/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  * DiscordOAuth2RpcService.kt is part of Kizzy
 *  *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

/**
 * Discord OAuth2 RPC Service.
 *
 * Uses Discord OAuth2 token (obtained via OAuth2 authorization flow) instead of
 * a bot token to identify with the Discord Gateway. This allows users to show
 * their own Discord account as the RPC source instead of the Kizzy bot.
 */
@AndroidEntryPoint
class DiscordOAuth2RpcService : Service() {

    private var kizzyRPC: KizzyRPC? = null

    @Inject
    lateinit var multiRpc: MultiRpcManager

    @Inject
    lateinit var scope: CoroutineScope

    private val TAG = "DiscordOAuth2Rpc"

    override fun onCreate() {
        super.onCreate()
        AppUtils.registerRpcService(this)
        Log.d(TAG, "onCreate")
        // Start foreground immediately so Android doesn't kill the service
        // for not calling startForeground() within 5 seconds.
        val basicNotification = android.app.Notification.Builder(this, Constants.CHANNEL_ID)
            .setContentTitle(getString(R.string.custom_rpc_running))
            .setContentText("")
            .setSmallIcon(R.drawable.ic_dev_rpc)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startForeground(Constants.NOTIFICATION_ID_OAUTH2_RPC, basicNotification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Constants.NOTIFICATION_ID_OAUTH2_RPC, basicNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // Wire OAuth2 RPC to slot "5" (default application token)
        kizzyRPC = multiRpc.session("5")
        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.DiscordOAuth2Rpc.name

        // Refresh the gateway token and identify properties from prefs.
        // Also send presence so Discord shows this account as online.
        // Unlike build() which sends an empty presence, updateRPC() reuses the
        // existing presence object which may already contain user data.
        scope.launch {
            kizzyRPC?.refreshFromPrefs()
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        AppUtils.unregisterRpcService(this)
        kizzyRPC?.closeRPC()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
