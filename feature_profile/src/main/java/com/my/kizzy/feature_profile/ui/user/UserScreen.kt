/*
 *
-  ******************************************************************
-  *  * Copyright (C) 2022
-  *  * UserScreen.kt is part of Kizzy
-  *  *  and can not be copied and/or distributed without the express
-  *  * permission of yzziK(Vaibhav)
-  *  *****************************************************************
 *
 */


package com.my.kizzy.feature_profile.ui.user

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_profile.ui.component.DiscordLoginButton
import com.my.kizzy.feature_profile.ui.component.ProfileCard
import com.my.kizzy.feature_profile.ui.component.ProfileNetworkError
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.shimmer.AnimatedShimmer
import com.my.kizzy.ui.components.shimmer.ShimmerProfileCard
import com.my.kizzy.ui.theme.DISCORD_BLURPLE
import com.my.kizzy.ui.theme.DISCORD_BG
import com.my.kizzy.ui.theme.DISCORD_CARD
import com.my.kizzy.ui.theme.DISCORD_LOGOUT_RED
import com.my.kizzy.ui.theme.DISCORD_SUBTEXT
import com.my.kizzy.ui.theme.DISCORD_TEXT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    state: UserState,
    onBackPressed: () -> Unit,
    onLogout: (Context) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToRpcPreview: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Resolved at composition so the confirmation dialog stays config-aware.
    val sureMessage = stringResource(R.string.are_you_sure)
    val yesLabel = stringResource(R.string.yes)
    val cancelLabel = stringResource(android.R.string.cancel)
    val logoutLabel = stringResource(R.string.logout)

    if (showLogoutDialog) {
        // Modern Material 3 confirmation dialog for destructive logout action.
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = DISCORD_LOGOUT_RED,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(
                    text = logoutLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = sureMessage,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout(ctx)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DISCORD_LOGOUT_RED,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = yesLabel,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = cancelLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { BackButton { onBackPressed() } },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is UserState.Error -> {
                    // Not logged in - Discord-style card with fallback user
                    // plus the Discord Signin button.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProfileCard(
                            state.user,
                            rpcConfig = state.rpcConfig,
                            onAvatarClick = { onNavigateToRpcPreview() }
                        )
                        Spacer(Modifier.height(24.dp))
                        DiscordLoginButton(
                            onClick = { onNavigateToLogin() },
                            enabled = true
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                UserState.Loading -> {
                    AnimatedShimmer {
                        ShimmerProfileCard(brush = it)
                    }
                }

                is UserState.LoadingCompleted -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ProfileCard(
                            state.user,
                            rpcConfig = state.rpcConfig,
                            onAvatarClick = { onNavigateToRpcPreview() }
                        )

                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            MenuRow(
                                icon = Icons.Filled.PlayArrow,
                                label = stringResource(R.string.profile_open_rpc_preview),
                                onClick = { onNavigateToRpcPreview() },
                            )
                            MenuDivider()
                            // Log Out stays in the same menu card as Open RPC
                            // Preview, but keeps Discord's destructive red.
                            MenuRow(
                                icon = Icons.AutoMirrored.Filled.Logout,
                                label = logoutLabel,
                                contentColor = DISCORD_LOGOUT_RED,
                                onClick = { showLogoutDialog = true },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp,
        )
    }
}

val fakeUser = User(
    accentColor = null,
    avatar = null,
    avatarDecoration = null,
    badges = null,
    banner = null,
    bannerColor = null,
    discriminator = "3050",
    id = null,
    publicFlags = null,
    username = "yzziK",
    special = null,
    verified = false,
    nitro = true,
    bio = "Hello 👋"
)
@Preview
@Composable
fun UserScreenPreview() {
    UserScreen(
        state = UserState.Loading,
        onBackPressed = {},
        onLogout = { ctx -> }
    )
}
@Preview
@Composable
fun UserScreenPreview2() {
    UserScreen(
        state = UserState.Error(
            error = "No Internet Connection",
            user = fakeUser,
            rpcConfig = RpcConfig(
                name = "Lo-fi Beats",
                details = "Chillhop Music",
                state = "Jazzhop & Chillhop - September 2024",
                largeImg = "attachments/123456789/album_art.png",
                smallImg = "attachments/123456789/artist_icon.png",
                timestampsStart = "${System.currentTimeMillis() - 30000}",
                timestampsStop = "${System.currentTimeMillis() + 180000}",
                type = "2",
                button1 = "Listen on Spotify",
                button1link = "https://open.spotify.com/track/4iV5W9uYEdYUVa79Axb7Rh",
                button2 = "View Artist",
                button2link = "https://open.spotify.com/artist/7dGJo4pcD2V6oG8kP0tJRR"
            )
        ),
        onBackPressed = {},
        onLogout = { ctx -> },
        onNavigateToLogin = {}
    )
}
@Preview
@Composable
fun UserScreenPreview3() {
    UserScreen(
        state = UserState.LoadingCompleted(
            user = fakeUser,
            rpcConfig = RpcConfig(name = "Kizzy", details = "Using Kizzy")
        ),
        onBackPressed = {},
        onLogout = { ctx -> }
    )
}