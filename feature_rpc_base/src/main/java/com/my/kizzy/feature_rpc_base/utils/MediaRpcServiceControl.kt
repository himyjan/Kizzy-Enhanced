package com.my.kizzy.feature_rpc_base.utils

import android.content.Context
import android.content.Intent
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs

/**
 * Stops the Media RPC because another RPC feature takes over.
 *
 * The "Media RPC is enabled" state is cleared as well, so the watchdog does not
 * bring the service back up while the other feature is running.
 */
fun Context.stopMediaRpcService() {
    Prefs[Prefs.MEDIA_RPC_ENABLED] = false
    // Only clear the persisted state when the media RPC is the active one; a
    // caller that is switching to another RPC sets its own value afterwards.
    if (Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.MediaRpc.name) {
        Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
    }
    stopService(Intent(this, MediaRpcService::class.java))
}
