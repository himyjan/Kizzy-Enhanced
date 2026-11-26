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

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
    val ctx: Context = LocalContext.current
    val rpcManager = RpcManager.getInstance(ctx)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        rpcManager.refreshFromServices()
    }

    // Collect prefsState so the provider recomposes when clones are added/removed/reordered.
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap<String, Any?>())
    val instances = Prefs.getHomeRpcInstances()

    val activeRpc by rpcManager.activeRpc.collectAsState()
    val activeInstances by rpcManager.activeInstances.collectAsState()

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
        // Per-instance state: two instances of the same type can be on/off
        // independently, each with its own Discord socket.
        val isRunning = instance.id in activeInstances ||
            (rpcType != null && rpcType in activeRpc && instance.id in builtInIds)

        // App Detection requires Usage Access permission; gate the toggle
        val requiresUsageAccess = rpcType == RpcType.AppDetectionRpc

        HomeFeature(
            title = instance.label,
            icon = when (instance.type) {
                "media_rpc" -> R.drawable.ic_media_rpc
                "manual_rpc" -> R.drawable.ic_manual_rpc
                "custom_rpc" -> R.drawable.ic_custom_rpc
                else -> R.drawable.ic_media_rpc
            },
            route = route,
            instanceId = instance.id,
            isChecked = isRunning,
            isDeletable = instance.id !in builtInIds,
            onClick = {
                navigateTo(it)
            },
            onCheckedChange = { enabled ->
                // App Detection needs Usage Access permission before it can work
                if (enabled && requiresUsageAccess && !hasUsageAccess.value) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.usage_access_required_for_app_detection),
                        Toast.LENGTH_LONG,
                    ).show()
                    ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    scope.launch {
                        if (enabled && rpcType != null) {
                            val configString = when (instance.type) {
                                "custom_rpc" -> instance.config.takeIf { it.isNotBlank() }
                                    ?: Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                                else -> ""
                            }
                            rpcManager.switchToInstance(
                                instanceId = instance.id,
                                rpcType = rpcType,
                                oauthIndex = instance.oauthIndex,
                                config = configString.takeIf { it.isNotBlank() },
                            )
                        } else if (!enabled && rpcType != null) {
                            rpcManager.stopInstance(instance.id, rpcType)
                        }
                    }
                }
            },
            showSwitch = true,
            tooltipText = when (instance.type) {
                "media_rpc" -> stringResource(R.string.main_mediaRpc_details)
                "manual_rpc" -> stringResource(R.string.main_ManualRpc_details)
                "custom_rpc" -> stringResource(R.string.main_customRpc_details)
                "app_detection_rpc" -> stringResource(R.string.main_appDetection_details)
                else -> ""
            },
            featureDocsLink = when (instance.type) {
                "media_rpc" -> ToolTipContent.MEDIA_RPC_DOCS_LINK
                "manual_rpc" -> ToolTipContent.MANUAL_RPC_DOCS_LINK
                "custom_rpc" -> ToolTipContent.CUSTOM_RPC_DOCS_LINK
                "app_detection_rpc" -> ToolTipContent.APP_DETECTION_DOCS_LINK
                else -> ""
            },
            onDelete = {
                scope.launch {
                    if (rpcType != null && isRunning) {
                        rpcManager.stopInstance(instance.id, rpcType)
                    }
                    onDeleteInstance(instance.id)
                }
            },
        )
    }
}
