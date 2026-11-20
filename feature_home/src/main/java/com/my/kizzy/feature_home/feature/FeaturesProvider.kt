/*

 *

 *  ******************************************************************

 *  *  * Copyright (C) 2022

 *  *  * FeaturesProvider.kt is part of Kizzy

 *  *  *  and can not be copied and/or distributed without the express

 *  *  * permission of yzziK(Vaibhav)

 *  *  *****************************************************************

 *

 *

 */



package com.my.kizzy.feature_home.feature



import androidx.compose.foundation.shape.RoundedCornerShape

import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.my.kizzy.navigation.Routes

import com.my.kizzy.preference.Prefs

import com.my.kizzy.resources.R



@Composable
fun homeFeaturesProvider(
    navigateTo: (String) -> Unit,
    hasUsageAccess: MutableState<Boolean>,
    hasNotificationAccess: MutableState<Boolean>,
    userVerified: Boolean,
): List<HomeFeature> {
    val ctx = LocalContext.current
    val rpcManager = RpcManager.getInstance(ctx)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        rpcManager.refreshFromServices()
    }
    val mediaRpcRunning = remember { mutableStateOf(RpcType.MediaRpc in rpcManager.activeRpc.value) }
    val customRpcRunning = remember { mutableStateOf(RpcType.CustomRpc in rpcManager.activeRpc.value) }
    val ManualRpcRunning = remember { mutableStateOf(RpcType.ManualRpc in rpcManager.activeRpc.value) }
    val appDetectionRunning = remember { mutableStateOf(RpcType.AppDetectionRpc in rpcManager.activeRpc.value) }
    LaunchedEffect(rpcManager.activeRpc) {
        mediaRpcRunning.value = RpcType.MediaRpc in rpcManager.activeRpc.value
        ManualRpcRunning.value = RpcType.ManualRpc in rpcManager.activeRpc.value
        customRpcRunning.value = RpcType.CustomRpc in rpcManager.activeRpc.value
        appDetectionRunning.value = RpcType.AppDetectionRpc in rpcManager.activeRpc.value
    }

    return listOf(
        HomeFeature(
            title = stringResource(id = R.string.main_mediaRpc),
            icon = R.drawable.ic_media_rpc,
            route = Routes.MEDIA_RPC,
            isChecked = mediaRpcRunning.value,
            showSwitch = true,

            onClick = {

                navigateTo(it)

            },

            onCheckedChange = {
                scope.launch {
                    if (it) {
                        // Add Media RPC without stopping Custom RPC or other RPCs.
                        rpcManager.switchTo(setOf(RpcType.MediaRpc), add = true)
                    } else {
                        // Stop only Media RPC, leave Custom RPC and others running.
                        rpcManager.stopRpcTypes(setOf(RpcType.MediaRpc))
                    }
                }
            },

            shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),

            tooltipText = stringResource(id = R.string.main_mediaRpc_details),

            featureDocsLink = ToolTipContent.MEDIA_RPC_DOCS_LINK

        ),
        HomeFeature(
            title = stringResource(id = R.string.main_ManualRpc),
            icon = R.drawable.ic_dev_rpc,
            route = Routes.MANUAL_RPC,
            onClick = { navigateTo(it) },
            isChecked = ManualRpcRunning.value,

            onCheckedChange = {
                scope.launch {
                    if (it) {
                        // Add Manual RPC without stopping Custom RPC or other RPCs.
                        rpcManager.switchTo(setOf(RpcType.ManualRpc), add = true)
                    } else {
                        // Stop only Manual RPC, leave Custom RPC and others running.
                        rpcManager.stopRpcTypes(setOf(RpcType.ManualRpc))
                    }
                }
            },

            shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_ManualRpc_details),

            featureDocsLink = ToolTipContent.MANUAL_RPC_DOCS_LINK

        ),
        HomeFeature(
            title = stringResource(id = R.string.main_customRpc),
            icon = R.drawable.ic_rpc_placeholder,
            route = Routes.CUSTOM_RPC,
            isChecked = customRpcRunning.value,

            onClick = {

                navigateTo(it)

            },

            onCheckedChange = {
                scope.launch {
                    if (it) {
                        val lastRpc = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                        // Add Custom RPC without stopping Media RPC or other RPCs.
                        rpcManager.switchTo(
                            setOf(RpcType.CustomRpc),
                            add = true,
                            rpcConfigString = lastRpc,
                            isConsoleRpc = false
                        )
                    } else {
                        // Stop only Custom RPC, leave Media RPC and others running.
                        rpcManager.stopRpcTypes(setOf(RpcType.CustomRpc))
                    }
                }
            },

            shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_customRpc_details),

            featureDocsLink = ToolTipContent.CUSTOM_RPC_DOCS_LINK

        )

    )

}

