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



import android.content.Intent

import androidx.compose.foundation.shape.RoundedCornerShape

import com.my.kizzy.feature_rpc_base.AppUtils
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

import com.my.kizzy.feature_rpc_base.services.AppDetectionService

import com.my.kizzy.feature_rpc_base.services.CustomRpcService

import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc

import com.my.kizzy.feature_rpc_base.services.MediaRpcService

import com.my.kizzy.feature_rpc_base.utils.stopMediaRpcService
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
    val mediaRpcRunning = remember { mutableStateOf(rpcManager.activeRpc.value == RpcType.MediaRpc) }
    val customRpcRunning = remember { mutableStateOf(rpcManager.activeRpc.value == RpcType.CustomRpc) }
    val experimentalRpcRunning = remember { mutableStateOf(rpcManager.activeRpc.value == RpcType.ExperimentalRpc) }
    val appDetectionRunning = remember { mutableStateOf(rpcManager.activeRpc.value == RpcType.AppDetectionRpc) }
    LaunchedEffect(rpcManager.activeRpc) {
        mediaRpcRunning.value = rpcManager.activeRpc.value == RpcType.MediaRpc
        customRpcRunning.value = rpcManager.activeRpc.value == RpcType.CustomRpc
        experimentalRpcRunning.value = rpcManager.activeRpc.value == RpcType.ExperimentalRpc
        appDetectionRunning.value = rpcManager.activeRpc.value == RpcType.AppDetectionRpc
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
                    rpcManager.switchTo(if (it) RpcType.MediaRpc else RpcType.None)
                }
            },

            shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),

            tooltipText = stringResource(id = R.string.main_mediaRpc_details),

            featureDocsLink = ToolTipContent.MEDIA_RPC_DOCS_LINK

        ), HomeFeature(
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
                        rpcManager.switchTo(RpcType.CustomRpc, rpcConfigString = lastRpc, isConsoleRpc = false)
                    } else {
                        rpcManager.switchTo(RpcType.None)
                    }
                }
            },

            shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_customRpc_details),

            featureDocsLink = ToolTipContent.CUSTOM_RPC_DOCS_LINK

        ),

        HomeFeature(
            title = stringResource(id = R.string.main_experimentalRpc),
            icon = R.drawable.ic_dev_rpc,
            route = Routes.EXPERIMENTAL_RPC,
            onClick = { navigateTo(it) },
            isChecked = experimentalRpcRunning.value,

            onCheckedChange = {
                scope.launch {
                    rpcManager.switchTo(if (it) RpcType.ExperimentalRpc else RpcType.None)
                }
            },

            shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_experimentalRpc_details),

            featureDocsLink = ToolTipContent.EXPERIMENTAL_RPC_DOCS_LINK

        )

    )

}

