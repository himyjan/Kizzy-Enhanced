/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcPreviewScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcPreviewScreen(
    userId: String = "",
    avatarUrl: String = "",
    rpcConfig: RpcConfig? = null,
    onBackPressed: () -> Unit
) {
    // Decode the avatar URL from navigation argument
    val decodedAvatarUrl = remember(userId, avatarUrl) {
        if (avatarUrl.isNotBlank()) {
            try {
                URLDecoder.decode(avatarUrl, StandardCharsets.UTF_8.toString())
            } catch (_: Exception) {
                avatarUrl
            }
        } else {
            ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RPC Preview") },
                navigationIcon = { BackButton { onBackPressed() } }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // User Avatar (circular)
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (decodedAvatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = decodedAvatarUrl,
                        placeholder = painterResource(R.drawable.error_avatar),
                        error = painterResource(R.drawable.error_avatar),
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Fallback: Person icon when no avatar URL
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "User Avatar",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Display userId as username fallback
            if (userId.isNotBlank() && decodedAvatarUrl.isBlank()) {
                Text(
                    text = userId,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // RPC Activity Info (name, details, timestamps, media progress bar)
            if (rpcConfig != null) {
                ActivityRow(
                    rpcConfig = rpcConfig,
                    showTs = true,
                    special = null
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewRpcPreviewScreen() {
    RpcPreviewScreen(
        userId = "123456789",
        avatarUrl = "https://cdn.discordapp.com/avatars/123456789/avatar.png",
        rpcConfig = RpcConfig(
            name = "Kizzy",
            details = "Using Kizzy",
            state = "Listening to music",
            button1 = "Spotify",
            button1link = "https://spotify.com",
            button2 = "YouTube",
            button2link = "https://youtube.com",
            timestampsStart = "",
            timestampsStop = "",
            type = "0"
        ),
        onBackPressed = {}
    )
}

@Preview
@Composable
fun PreviewRpcPreviewScreenNoRpc() {
    RpcPreviewScreen(
        userId = "123456789",
        avatarUrl = "https://cdn.discordapp.com/embed/avatars/0.png",
        rpcConfig = null,
        onBackPressed = {}
    )
}

@Preview
@Composable
fun PreviewRpcPreviewScreenNoAvatar() {
    RpcPreviewScreen(
        userId = "123456789",
        avatarUrl = "",
        rpcConfig = null,
        onBackPressed = {}
    )
}