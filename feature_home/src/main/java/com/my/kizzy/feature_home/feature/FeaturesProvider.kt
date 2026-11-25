/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * FeaturesProvider.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  ******************************************************************
 *
 */

package com.my.kizzy.feature_home.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import com.my.kizzy.navigation.Routes

import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.BUILT_IN_INSTANCES
import com.my.kizzy.preference.RpcInstance

import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType
import com.my.kizzy.feature_home.feature.ToolTipContent
import com.my.kizzy.resources.R

@Composable
fun homeFeaturesProvider(
    navigateTo: (String) -> Unit,
    hasUsageAccess: MutableState<Boolean>,
    hasNotificationAccess: MutableState<Boolean>,
    userVerified: Boolean,
    onAddInstance: (RpcInstance) -> Unit = {},
    onDeleteInstance: (String) -> Unit = {},
    onReorder: (List<RpcInstance>) -> Unit = {},
): List<HomeFeature> {
    val ctx = LocalContext.current
    val rpcManager = RpcManager.getInstance(ctx)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        rpcManager.refreshFromServices()
    }

    val instances = Prefs.getHomeRpcInstances()

    val activeRpc by rpcManager.activeRpc.collectAsState()

    val builtInIds = BUILT_IN_INSTANCES.map { it.id }.toSet()

    return instances.map { instance ->
        val route = when (instance.type) {
            "media_rpc" -> Routes.MEDIA_RPC
            "manual_rpc" -> Routes.MANUAL_RPC
            "custom_rpc" -> Routes.CUSTOM_RPC
            else -> null
        }
        val rpcType = when (instance.type) {
            "media_rpc" -> RpcType.MediaRpc
            "manual_rpc" -> RpcType.ManualRpc
            "custom_rpc" -> RpcType.CustomRpc
            else -> null
        }
        val isRunning = rpcType != null && rpcType in activeRpc

        HomeFeature(
            title = instance.label,
            route = route,
            instanceId = instance.id,
            isChecked = isRunning,
            isDeletable = instance.id !in builtInIds,
            onClick = {
                navigateTo(it)
            },
            onCheckedChange = { enabled ->
                scope.launch {
                    if (enabled && rpcType != null) {
                        val configString = when (instance.type) {
                            "custom_rpc" -> Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                            else -> ""
                        }
                        rpcManager.switchTo(
                            setOf(rpcType),
                            add = true,
                            rpcConfigString = if (configString.isNotBlank()) configString else null,
                            isConsoleRpc = false
                        )
                    } else if (!enabled && rpcType != null) {
                        rpcManager.stopRpcTypes(setOf(rpcType))
                    }
                }
            },
            showSwitch = true,
            tooltipText = when (instance.type) {
                "media_rpc" -> stringResource(R.string.main_mediaRpc_details)
                "manual_rpc" -> stringResource(R.string.main_ManualRpc_details)
                "custom_rpc" -> stringResource(R.string.main_customRpc_details)
                else -> ""
            },
            featureDocsLink = when (instance.type) {
                "media_rpc" -> ToolTipContent.MEDIA_RPC_DOCS_LINK
                "manual_rpc" -> ToolTipContent.MANUAL_RPC_DOCS_LINK
                "custom_rpc" -> ToolTipContent.CUSTOM_RPC_DOCS_LINK
                else -> ""
            },
            onDelete = {
                scope.launch {
                    onDeleteInstance(instance.id)
                    if (rpcType != null && isRunning) {
                        rpcManager.stopRpcTypes(setOf(rpcType))
                    }
                }
            },
        )
    } + listOf(
        HomeFeature(
            title = "Add RPC",
            route = null,
            isChecked = false,
            showSwitch = false,
            isAddButton = true,
            tooltipText = "Add a new RPC instance (Media, Manual, or Custom)",
        )
    )
}
