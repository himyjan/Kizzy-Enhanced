/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * FeaturesProvider.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 */

package com.my.kizzy.feature_home.feature

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType
import com.my.kizzy.navigation.AppRoute
import com.my.kizzy.preference.BUILT_IN_INSTANCES
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RpcInstance
import com.my.kizzy.preference.displayLabel
import com.my.kizzy.resources.R
import kotlinx.coroutines.launch

/**
 * Builds the list of [HomeFeature] cards from persisted RPC instances and live [RpcManager] state.
 */
@Composable
fun homeFeaturesProvider(
    prefsMap: Map<String, Any?>,
    navigateTo: (AppRoute) -> Unit,
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

    val instances = remember(prefsMap) { Prefs.getHomeRpcInstances() }
    val activeInstances by rpcManager.activeInstances.collectAsState()
    val builtInIds = remember { BUILT_IN_INSTANCES.map { it.id }.toSet() }

    return instances.map { instance ->
        val isClone = instance.id !in builtInIds

        val route: AppRoute? = when (instance.type) {
            "media_rpc" -> AppRoute.MediaRpc(instanceId = if (isClone) instance.id else null)
            "manual_rpc" -> AppRoute.ManualRpc(instanceId = if (isClone) instance.id else null)
            "custom_rpc" -> AppRoute.CustomRpc(instanceId = if (isClone) instance.id else null)
            else -> null
        }

        val rpcType = when (instance.type) {
            "media_rpc" -> RpcType.MediaRpc
            "manual_rpc" -> RpcType.ManualRpc
            "custom_rpc" -> RpcType.CustomRpc
            "app_detection_rpc" -> RpcType.AppDetectionRpc
            else -> null
        }

        val isRunning = instance.id in activeInstances
        val requiresUsageAccess = rpcType == RpcType.AppDetectionRpc

        val stopAndDelete: () -> Unit = {
            scope.launch {
                if (rpcType != null && isRunning) {
                    rpcManager.stopInstance(instance.id, rpcType)
                }
                onDeleteInstance(instance.id)
            }
        }

        HomeFeature(
            title = instance.displayLabel(ctx),
            icon = when (instance.type) {
                "media_rpc" -> R.drawable.ic_media_rpc
                "manual_rpc" -> R.drawable.ic_manual_rpc
                "custom_rpc" -> R.drawable.ic_custom_rpc
                else -> R.drawable.ic_media_rpc
            },
            route = route,
            instanceId = instance.id,
            isChecked = isRunning,
            isDeletable = isClone,
            onClick = { key ->
                navigateTo(key)
            },
            onCheckedChange = { enabled ->
                if (enabled && requiresUsageAccess && !hasUsageAccess.value) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.usage_access_required_for_app_detection),
                        Toast.LENGTH_LONG,
                    ).show()
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } else {
                    scope.launch {
                        if (enabled && rpcType != null) {
                            val configString = when (instance.type) {
                                "custom_rpc" -> instance.config.takeIf { it.isNotBlank() }
                                    ?: Prefs.getCustomRpcConfig(instance.id.takeIf { isClone })
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
            onDelete = { stopAndDelete() },
        )
    }
}