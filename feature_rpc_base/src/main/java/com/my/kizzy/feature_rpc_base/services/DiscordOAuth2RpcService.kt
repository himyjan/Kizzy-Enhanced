/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DiscordOAuth2RpcService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Discord OAuth2 RPC Service.
 *
 * Uses Discord OAuth2 token (obtained via OAuth2 authorization flow) to identify
 * with the Discord Gateway on session slot "5".
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        kizzyRPC = multiRpc.session("5")
        Prefs[Prefs.ACTIVE_RPC_TYPE] = RpcManager.RpcType.DiscordOAuth2Rpc.name

        scope.launch {
            kizzyRPC?.refreshFromPrefs()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        val rpc = kizzyRPC
        kizzyRPC = null
        scope.cancel()
        AppUtils.unregisterRpcService(this)
        if (rpc != null) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    rpc.clearPresence()
                } catch (_: Exception) {
                }
                rpc.closeRPC()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}