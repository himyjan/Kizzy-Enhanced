/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcPreviewScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.theme.DISCORD_BLURPLE
import com.my.kizzy.ui.theme.DISCORD_GREEN
import com.my.kizzy.ui.theme.DISCORD_RED
import com.my.kizzy.ui.theme.DISCORD_YELLOW

// Discord presence dots - mirrored from ProfileCard.
private val ONLINE = DISCORD_GREEN
private val IDLE = DISCORD_YELLOW
private val DND = DISCORD_RED
private val OFFLINE = Color(0xFF80848E)

private fun presenceColor(status: String?): Color? = when (status) {
    "online" -> ONLINE
    "idle" -> IDLE
    "dnd" -> DND
    "offline", "invisible" -> OFFLINE
    else -> null
}

/**
 * Discord-styled RPC preview: full-bleed banner, overlapping avatar with the
 * presence dot driven by the activity status, the account name, and the live
 * "Current activity" card (with timeline and buttons).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcPreviewScreen(
    userId: String = "",
    avatarUrl: String = "",
    rpcConfig: RpcConfig? = null,
    onBackPressed: () -> Unit
) {
    // The navigation library already decodes the arguments of the route, so the
    // avatar url must not be decoded a second time - that broke the url.
    val decodedAvatarUrl = remember(userId, avatarUrl) {
        avatarUrl
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("RPC Preview") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton { onBackPressed() } },
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Banner + overlapping avatar + presence dot --------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp)
                        .size(96.dp)
                        .border(width = 5.dp, color = DISCORD_BLURPLE, shape = CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (decodedAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = decodedAvatarUrl,
                            placeholder = painterResource(R.drawable.error_avatar),
                            error = painterResource(R.drawable.error_avatar),
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = vectorResource(R.drawable.ic_person),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val dotColor = presenceColor(rpcConfig?.status?.takeIf { it.isNotBlank() })
                    if (dotColor != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                                .border(5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // Account name - userId is the only identity the route carries.
                if (userId.isNotBlank()) {
                    Text(
                        text = userId,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (rpcConfig != null) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeader(text = stringResource(R.string.user_profile_rpc_name))
                    Spacer(Modifier.height(8.dp))
                    ActivityRow(
                        rpcConfig = rpcConfig,
                        showTs = true,
                        special = null,
                    )
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "No activity detected",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(24.dp))
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