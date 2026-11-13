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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

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
    // Remember the feature list keyed on RPC running states so the list
    // is not rebuilt on every recomposition. AppUtils caches running
    // services for 1 second, so these values are stable enough.
    val mediaRpcRunning = remember(AppUtils.mediaRpcRunning().toString()) {
        mutableStateOf(AppUtils.mediaRpcRunning())
    }
    val customRpcRunning = remember(AppUtils.customRpcRunning().toString()) {
        mutableStateOf(AppUtils.customRpcRunning())
    }
    val consoleRpcRunning = remember(AppUtils.consoleRpcRunning().toString()) {
        mutableStateOf(AppUtils.consoleRpcRunning())
    }
    val experimentalRpcRunning = remember(AppUtils.experimentalRpcRunning().toString()) {
        mutableStateOf(AppUtils.experimentalRpcRunning())
    }
    val appDetectionRunning = remember(AppUtils.appDetectionRunning().toString()) {
        mutableStateOf(AppUtils.appDetectionRunning())
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

                if (it) {

                    ctx.stopService(Intent(ctx, CustomRpcService::class.java))

                    ctx.stopService(Intent(ctx, ExperimentalRpc::class.java))

                    ctx.stopService(Intent(ctx, AppDetectionService::class.java))

                    ctx.startService(Intent(ctx, MediaRpcService::class.java))

                } else

                    ctx.stopMediaRpcService()

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
                if (it) {
                    val lastRpc = Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""]
                    val intent = Intent(ctx, CustomRpcService::class.java)
                    intent.apply {
                        putExtra("RPC", lastRpc)
                    }
                    Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
                    ctx.stopMediaRpcService()

                    ctx.stopService(Intent(ctx, ExperimentalRpc::class.java))

                    ctx.stopService(Intent(ctx, AppDetectionService::class.java))

                    ctx.startService(intent)

                } else

                    ctx.stopService(Intent(ctx, CustomRpcService::class.java))

            },

            shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_customRpc_details),

            featureDocsLink = ToolTipContent.CUSTOM_RPC_DOCS_LINK

        ),

        HomeFeature(
            title = stringResource(id = R.string.main_consoleRpc),
            icon = R.drawable.ic_console_games,
            route = Routes.CONSOLE_RPC,
            isChecked = consoleRpcRunning.value,

            onClick = { navigateTo(it) },

            onCheckedChange = {
                if (it) {
                    val lastRpc = Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""]
                    val intent = Intent(ctx, CustomRpcService::class.java)
                    intent.putExtra("RPC", lastRpc)
                    intent.putExtra(CustomRpcService.EXTRA_CONSOLE_RPC, true)
                    ctx.stopMediaRpcService()
                    ctx.stopService(Intent(ctx, ExperimentalRpc::class.java))
                    ctx.stopService(Intent(ctx, AppDetectionService::class.java))
                    ctx.startService(intent)
                } else {
                    Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = ""
                    ctx.stopService(Intent(ctx, CustomRpcService::class.java))
                }
            },

            shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_consoleRpc_details),

            featureDocsLink = ToolTipContent.CONSOLE_RPC_DOCS_LINK

        ),

        HomeFeature(
            title = stringResource(id = R.string.main_experimentalRpc),
            icon = R.drawable.ic_dev_rpc,
            route = Routes.EXPERIMENTAL_RPC,
            onClick = { navigateTo(it) },
            isChecked = experimentalRpcRunning.value,

            onCheckedChange = {

                if (it) {

                    ctx.stopMediaRpcService()

                    ctx.stopService(Intent(ctx, CustomRpcService::class.java))

                    ctx.stopService(Intent(ctx, AppDetectionService::class.java))

                    ctx.startService(Intent(ctx, ExperimentalRpc::class.java))

                } else

                    ctx.stopService(Intent(ctx, ExperimentalRpc::class.java))

            },

            shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),

            showSwitch = true,

            tooltipText = stringResource(id = R.string.main_experimentalRpc_details),

            featureDocsLink = ToolTipContent.EXPERIMENTAL_RPC_DOCS_LINK

        )

    )

}

