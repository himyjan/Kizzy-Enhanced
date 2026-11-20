/*
 *
 *  *****************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LoginScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.my.kizzy.feature_profile.fetchUserInfo
import com.my.kizzy.feature_profile.ui.component.DiscordAuthView
import com.my.kizzy.feature_profile.ui.component.DiscordLoginButton
import com.my.kizzy.preference.Prefs
import com.my.kizzy.ui.components.BackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCompleted: () -> Unit,
    onBackPressed: () -> Unit
) {
    // Check if user is already logged in before showing anything
    val prefsState by remember { Prefs.prefsState }.collectAsState()
    val token = prefsState[Prefs.TOKEN] as? String ?: ""
    
    // If user is already logged in, navigate back immediately
    if (token.isNotEmpty()) {
        onCompleted()
        return
    }
    
    var showWebView by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showWebView) {
        if (showWebView) delay(100)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    BackButton {
                        if (showWebView) {
                            showWebView = false
                        } else {
                            onBackPressed()
                        }
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
            if (showWebView) {
                DiscordAuthView(
                    onLoginCompleted = { token ->
                        // Save the account token, then fetch the user ID from
                        // Discord's REST API immediately so the setup screen
                        // shows the Discord ID instead of just "Logged in".
                        Prefs[Prefs.TOKEN] = token
                        showWebView = false
                        isSaving = true
                        scope.launch {
                            // Fetch user ID via REST API right away (doesn't
                            // depend on gateway READY). Fall back to gateway
                            // READY fetch below if this fails.
                            fetchDiscordUserId(token)?.let { userId ->
                                Prefs[Prefs.USER_ID] = userId
                            }
                            fetchUserInfo(token)
                            isSaving = false
                            onCompleted()
                        }
                    }
                )
            } else {
                DiscordLoginButton(
                    onClick = { showWebView = true },
                    enabled = !isSaving
                )
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
