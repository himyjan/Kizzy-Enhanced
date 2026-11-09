/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * UserScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_profile.ui.component.Logout
import com.my.kizzy.feature_profile.ui.component.ProfileCard
import com.my.kizzy.feature_profile.ui.component.ProfileNetworkError
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.shimmer.AnimatedShimmer
import com.my.kizzy.ui.components.shimmer.ShimmerProfileCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    state: UserState,
    onBackPressed: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRpcPreview: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(title = { },
                navigationIcon = { BackButton { onBackPressed() } })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is UserState.Error -> {
                    ProfileNetworkError(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopCenter),
                        error = (state.error)
                    )
                    ProfileCard(
                        state.user,
                        rpcConfig = state.rpcConfig,
                        onAvatarClick = {
                            onNavigateToRpcPreview()
                        }
                    )
                }

                UserState.Loading -> {
                    AnimatedShimmer {
                        ShimmerProfileCard(brush = it)
                    }
                }

                is UserState.LoadingCompleted -> {
                    ProfileCard(
                        state.user,
                        rpcConfig = state.rpcConfig,
                        onAvatarClick = {
                            onNavigateToRpcPreview()
                        }
                    )
                    Logout(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = ctx.getString(R.string.are_you_sure),
                                actionLabel = ctx.getString(R.string.yes),
                                duration = SnackbarDuration.Short,
                                withDismissAction = true
                            ).run {
                                when (this) {
                                    SnackbarResult.ActionPerformed -> {
                                        onLogout()
                                    }

                                    SnackbarResult.Dismissed -> Unit
                                }
                            }

                        }

                    }
                }
            }
        }
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
        onLogout = {}
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
        onLogout = {}
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
        onLogout = {}
    )
}
