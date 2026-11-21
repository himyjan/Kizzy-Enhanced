/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LoginScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaizhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.login

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.my.kizzy.feature_profile.fetchUserInfo
import com.my.kizzy.feature_settings.rpc_settings.DiscordOAuth2Auth
import com.my.kizzy.preference.Prefs
import com.my.kizzy.ui.components.BackButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCompleted: () -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discordAuth = remember { DiscordOAuth2Auth(context, scope) }

    // Check if user is already logged in before showing anything
    val prefsState by remember { Prefs.prefsState }.collectAsState()
    val token = prefsState[Prefs.TOKEN] as? String ?: ""

    // If user is already logged in, navigate back immediately
    if (token.isNotEmpty()) {
        onCompleted()
        return@LoginScreen
    }

    var isAuthorizing by remember { mutableStateOf(false) }
    var oauth2Error by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    BackButton {
                        onBackPressed()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (oauth2Error.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "OAuth2 Error",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = oauth2Error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { oauth2Error = "" }) {
                        Text(text = "Retry")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Discord Login",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isAuthorizing) "Authorizing..." else "Login with Discord",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .clickable {
                                oauth2Error = ""
                                isAuthorizing = true
                                scope.launch {
                                    try {
                                        // Safely get Activity from context
                                        val activity = context as? Activity
                                            ?: throw DiscordOAuth2Auth.DiscordOAuth2Exception.NoBrowser()

                                        val result = discordAuth.authorize(activity)

                                        // Save OAuth2 access token as the gateway token
                                        Prefs[Prefs.TOKEN] = result.accessToken
                                        Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = result.accessToken
                                        if (result.refreshToken.isNotEmpty()) {
                                            Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN] = result.refreshToken
                                        }
                                        if (result.expiresInSec > 0) {
                                            Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT] =
                                                System.currentTimeMillis() + result.expiresInSec * 1000
                                        }

                                        // Fetch user ID via REST API right away
                                        fetchDiscordUserId(result.accessToken)?.let { userId ->
                                            Prefs[Prefs.USER_ID] = userId
                                        }
                                        fetchUserInfo(result.accessToken)

                                        isAuthorizing = false
                                        onCompleted()
                                    } catch (e: DiscordOAuth2Auth.DiscordOAuth2Exception) {
                                        isAuthorizing = false
                                        oauth2Error = e.message ?: "Authorization failed"
                                    } catch (e: Exception) {
                                        isAuthorizing = false
                                        oauth2Error = "Unexpected error: ${e.message}"
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun LoginScreenPreview() {
    LoginScreen(
        onCompleted = {},
        onBackPressed = {}
    )
}

/**
 * Fetches the Discord user ID via REST API /users/@me.
 *
 * Returns the numeric user ID string, or null if the request fails.
 * Works with both account tokens (user_id.timestamp.hmac) and OAuth2
 * bearer tokens.
 */
private suspend fun fetchDiscordUserId(token: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://discord.com/api/v10/users/@me")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val body = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                // Extract "id":"123456789" from the JSON response
                Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
