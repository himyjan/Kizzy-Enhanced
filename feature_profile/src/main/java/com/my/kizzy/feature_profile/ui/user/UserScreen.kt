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


@file:Suppress("DEPRECATION")

package com.my.kizzy.feature_profile.ui.user

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
import com.my.kizzy.feature_profile.fetchDiscordUser
import com.my.kizzy.feature_profile.fetchDiscordUserProfile
import com.my.kizzy.feature_profile.persistDiscordUser
import com.my.kizzy.feature_settings.rpc_settings.DiscordOAuth2Auth
import com.my.kizzy.feature_profile.ui.component.DiscordLoginButton
import com.my.kizzy.feature_profile.ui.component.ProfileCard
import com.my.kizzy.feature_profile.ui.component.ProfileNetworkError
import com.my.kizzy.preference.Prefs
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    state: UserState,
    onBackPressed: () -> Unit,
    onLogout: (Context) -> Unit,
    onNavigateToRpcPreview: () -> Unit = {},
    // AuthTab launcher + binder from MainActivity -> Kizzy, used by the inline
    // Discord login on this screen (no separate login screen).
    authTabLauncher: ActivityResultLauncher<Intent>? = null,
    onBindAuthTabCallback: (((Int, Intent?) -> Unit) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var oauth2Error by remember { mutableStateOf("") }
    var isAuthorizing by remember { mutableStateOf(false) }

    val discordAuth = remember(ctx, authTabLauncher) {
        DiscordOAuth2Auth(context = ctx, authTabLauncher = authTabLauncher)
    }

    DisposableEffect(discordAuth, onBindAuthTabCallback) {
        onBindAuthTabCallback?.invoke { resultCode, callbackIntent ->
            discordAuth.onActivityResult(resultCode, callbackIntent)
        }
        onDispose { onBindAuthTabCallback?.invoke { _, _ -> } }
    }

    fun startOAuth() {
        oauth2Error = ""
        isAuthorizing = true
        scope.launch {
            try {
                val result = discordAuth.authorize(ctx as Activity)
                // Never write an OAuth2 bearer token into TOKEN: the gateway
                // rejects it with 4004. TOKEN is reserved for the real account
                // token used by the Gateway connection method.
                Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = result.accessToken
                if (result.refreshToken.isNotEmpty()) {
                    Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN] = result.refreshToken
                }
                if (result.expiresInSec > 0) {
                    Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT] =
                        System.currentTimeMillis() + result.expiresInSec * 1000
                }
                // Persist avatar/bio/banner before the profile switches to logged-in.
                // fetchDiscordUser writes USER_DATA; the profile endpoint returns
                // the bio that /users/@me omits, so merge and persist it too.
                val user = fetchDiscordUser(result.accessToken)
                if (user != null) {
                    val profileBio = fetchDiscordUserProfile(result.accessToken)
                    persistDiscordUser(
                        user.copy(bio = profileBio?.takeIf { it.isNotBlank() } ?: user.bio)
                    )
                }
            } catch (e: DiscordOAuth2Auth.DiscordOAuth2Exception) {
                oauth2Error = e.message ?: "Authorization failed"
            } catch (e: Exception) {
                oauth2Error = "Unexpected error: ${e.message}"
            } finally {
                isAuthorizing = false
            }
        }
    }

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
                            rpcConfigs = state.rpcConfigs,
                            onAvatarClick = { onNavigateToRpcPreview() }
                        )
                        Spacer(Modifier.height(24.dp))
                        DiscordLoginButton(
                            onClick = { startOAuth() },
                            enabled = !isAuthorizing
                        )
                        if (oauth2Error.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(text = oauth2Error, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(16.dp))
                        TokenLoginSection()
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
                            rpcConfigs = state.rpcConfigs,
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

/**
 * Manual token entry shown on the profile page next to the Discord login button.
 * Validates by fetching the REST identity, which also persists USER_DATA so the
 * avatar/bio appear as soon as the user is logged in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenLoginSection() {
    var token by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Or paste your Discord account token:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Account Token") },
            placeholder = { Text("Paste your Discord account token here") },
            singleLine = true,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val value = token.trim()
                if (value.isBlank()) {
                    error = "Please paste a valid Discord account token"
                    return@Button
                }
                error = ""
                isSubmitting = true
                scope.launch {
                    Prefs[Prefs.TOKEN] = value
                    val user = fetchDiscordUser(value)
                    if (user == null) {
                        Prefs.remove(Prefs.TOKEN)
                        error = "Invalid token or network error"
                    } else {
                        // Also fetch bio/about me from REST client profile endpoint
                        val bio = fetchDiscordUserProfile(value)
                        persistDiscordUser(
                            user.copy(bio = bio?.takeIf { it.isNotBlank() } ?: user.bio)
                        )
                        error = ""
                    }
                    // A pasted account token belongs only in TOKEN; never overwrite
                    // the OAuth2 bearer used by the OAuth2 connection method.
                    isSubmitting = false
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (isSubmitting) "Validating..." else "Login with Token")
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
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
            rpcConfigs = listOf(
                RpcConfig(
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
            )
        ),
        onBackPressed = {},
        onLogout = { ctx -> }
    )
}
@Preview
@Composable
fun UserScreenPreview3() {
    UserScreen(
        state = UserState.LoadingCompleted(
            user = fakeUser,
            rpcConfigs = listOf(RpcConfig(name = "Kizzy", details = "Using Kizzy"))
        ),
        onBackPressed = {},
        onLogout = { ctx -> }
    )
}