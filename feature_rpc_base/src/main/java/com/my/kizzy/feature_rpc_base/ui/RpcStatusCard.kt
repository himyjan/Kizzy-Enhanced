package com.my.kizzy.feature_rpc_base.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RpcStatusCard(
    isConnected: Boolean,
    isPaused: Boolean,
    currentTrackTitle: String?,
    currentArtist: String?,
    onTogglePause: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamically animate card background container color based on state
    val containerColorState = animateColorAsState(
        targetValue = when {
            !isConnected -> MaterialTheme.colorScheme.surfaceVariant
            isPaused -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "CardColorAnimation"
    )
    val containerColor = containerColorState.value

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header: Status Indicator & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !isConnected -> "RPC Disconnected"
                            isPaused -> "RPC Paused"
                            else -> "Broadcasting Presence"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Live status dot badge
                Badge(
                    containerColor = when {
                        !isConnected -> Color.Gray
                        isPaused -> Color.Yellow
                        else -> Color.Green
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body: Track Details or Idle State
            if (isConnected) {
                Text(
                    text = currentTrackTitle ?: "Waiting for media...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = currentArtist ?: "Play a song on Spotify, YouTube, or SimpMusic",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Service is currently inactive. Toggle RPC on to start syncing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer: Quick Action Buttons
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause / Resume Button
                    TextButton(onClick = onTogglePause) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isPaused) "Resume" else "Pause")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Disconnect Button
                    OutlinedButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.Default.PowerOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Disconnect")
                    }
                }
            }
        }
    }
}
