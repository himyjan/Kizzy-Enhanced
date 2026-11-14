/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  * DiscordOAuthRpcService.kt is part of Kizzy
 *  *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.my.kizzy.data.rpc.KizzyRPC
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch // <-- Added missing import for launch extension

/**
 * Discord OAuth RPC Service.
 *
 * Uses Discord OAuth2 token (obtained via OAuth2 authorization flow) instead of
 * a bot token to identify with the Discord Gateway. This allows users to show
 * their own Discord account as the RPC source instead of the Kizzy bot.
 */
@AndroidEntryPoint
class DiscordOAuthRpcService : Service() {

    @Inject
    lateinit var kizzyRPC: KizzyRPC

    @Inject
    lateinit var scope: CoroutineScope

    private val TAG = "DiscordOAuthRpc"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // Start the RPC presence loop safely inside the injected coroutine scope
        scope.launch {
            kizzyRPC.build(forceRefresh = true)
        }

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        kizzyRPC.closeRPC()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}