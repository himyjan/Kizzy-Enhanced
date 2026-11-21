package com.my.kizzy.feature_rpc_base.utils

import android.content.Context
import com.my.kizzy.feature_rpc_base.RpcManager

/**
 * Stops only Media RPC, leaving the other RPCs running.
 *
 * State is managed by [RpcManager] so the persisted active set, the switches and
 * the actually running services stay in sync.
 */
fun Context.stopMediaRpcService() {
    RpcManager.getInstance(this).stopRpcTypes(setOf(RpcManager.RpcType.MediaRpc))
}
