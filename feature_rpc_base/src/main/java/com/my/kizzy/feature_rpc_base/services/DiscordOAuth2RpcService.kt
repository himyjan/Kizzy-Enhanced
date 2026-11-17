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
import android.os.IBinder
import android.util.Log
import com.my.kizzy.data.rpc.KizzyRPC
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch // <-- Added missing import for launch extension

/**
 * Discord OAuth2 RPC Service.
 *
 * Uses Discord OAuth2 token (obtained via OAuth2 authorization flow) instead of
 * a bot token to identify with the Discord Gateway. This allows users to show
 * their own Discord account as the RPC source instead of the Kizzy bot.
 */
@AndroidEntryPoint
class DiscordOAuth2RpcService : Service() {

    @Inject
    lateinit var kizzyRPC: KizzyRPC

    @Inject
    lateinit var scope: CoroutineScope

    private val TAG = "DiscordOAuth2Rpc"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        // Refresh the gateway token and identify properties from prefs.
        // Also send presence so Discord shows this account as online.
        // Unlike build() which sends an empty presence, updateRPC() reuses the
        // existing presence object which may already contain user data.
        scope.launch {
            kizzyRPC.refreshFromPrefs()
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