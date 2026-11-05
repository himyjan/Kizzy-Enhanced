package com.my.kizzy.feature_rpc_base.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_rpc_base.services.KizzyRpcService
import com.my.kizzy.feature_rpc_base.utils.KizzyServiceController

@Composable
fun KizzyDashboardScreen(
    viewModel: RpcViewModel,
    context: android.content.Context = LocalContext.current
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val currentTrackTitle by viewModel.currentTrackTitle.collectAsState()
    val currentArtist by viewModel.currentArtist.collectAsState()

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            RpcStatusCardLoadingSkeleton()
        } else {
            RpcStatusCard(
                isConnected = isConnected,
                isPaused = isPaused,
                currentTrackTitle = currentTrackTitle,
                currentArtist = currentArtist,
                onTogglePause = {
                    KizzyServiceController.sendAction(context, KizzyRpcService.ACTION_TOGGLE)
                },
                onDisconnect = {
                    KizzyServiceController.sendAction(context, KizzyRpcService.ACTION_DISCONNECT)
                }
            )
        }
    }
}
