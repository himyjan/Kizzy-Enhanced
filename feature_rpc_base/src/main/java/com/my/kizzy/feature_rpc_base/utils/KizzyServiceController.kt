package com.my.kizzy.feature_rpc_base.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import com.my.kizzy.feature_rpc_base.services.KizzyRpcService

object KizzyServiceController {

    fun sendAction(context: Context, action: String) {
        val intent = Intent(context, KizzyRpcService::class.java).apply {
            this.action = action
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
