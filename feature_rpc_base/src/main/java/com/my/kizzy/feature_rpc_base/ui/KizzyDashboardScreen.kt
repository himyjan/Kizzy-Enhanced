package com.my.kizzy.feature_rpc_base.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.my.kizzy.feature_rpc_base.services.KizzyRpcService
import com.my.kizzy.feature_rpc_base.utils.KizzyServiceController

@Composable
fun KizzyDashboardScreen(
    viewModel: RpcViewModel,
    context: android.content.Context = LocalContext.current
) {
    var isLoading by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        scope.launch {
            viewModel.isLoading.collect { isLoading = it }
        }
        scope.launch {
            viewModel.isConnected.collect { isConnected = it }
        }
        scope.launch {
            viewModel.isPaused.collect { isPaused = it }
        }
    }

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
                currentTrackTitle = null,
                currentArtist = null,
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
