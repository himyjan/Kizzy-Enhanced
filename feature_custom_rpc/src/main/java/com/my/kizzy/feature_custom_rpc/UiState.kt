/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * UiState.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_custom_rpc

import androidx.compose.runtime.Immutable
import com.my.kizzy.domain.model.Game
import com.my.kizzy.domain.model.rpc.RpcConfig

@Immutable
data class UiState(
    val activityTypeIsExpanded: Boolean = false,
    val showBottomSheet: Boolean = false,
    val showLoadDialog: Boolean = false,
    val showShareDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showPreviewDialog: Boolean = false,
    val showStoragePermissionRequestDialog: Boolean = false,
    val showStartTimeStampsPickerDialog: Boolean = false,
    val showStopTimeStampsPickerDialog: Boolean = false,
    val statusIsExpanded: Boolean = false,
    val platformIsExpanded: Boolean = false,
    val showLargeCoverArt: Boolean = true,
    val showSmallCoverArt: Boolean = true,
    val consoleGames: List<Game> = emptyList(),
    val rpcConfig: RpcConfig = RpcConfig(),
    /** Null for built-in Custom RPC; the clone's id otherwise. */
    val instanceId: String? = null,
)