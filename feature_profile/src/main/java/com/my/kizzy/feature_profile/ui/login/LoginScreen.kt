/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  * LoginScreen.kt is part of Kizzy
 *  *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.login

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.my.kizzy.feature_profile.getUserInfo
import com.my.kizzy.feature_profile.ui.component.DiscordLoginButton
import com.my.kizzy.feature_settings.rpc_settings.DiscordOAuth2Auth
import com.my.kizzy.preference.Prefs
import com.my.kizzy.ui.components.BackButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCompleted: () -> Unit,
    onBackPressed: () -> Unit
) {
    var buttonEnabledState by remember { mutableStateOf(true) }
    var uiState: LoginUiState by remember { mutableStateOf(LoginUiState.InitialState) }
    val scope = rememberCoroutineScope()
    val activityContext = LocalContext.current as Activity
    val discordAuth = remember { DiscordOAuth2Auth(activityContext) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { }, navigationIcon = { BackButton { onBackPressed() } })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.padding(paddingValues).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                LoginUiState.InitialState -> {}
                LoginUiState.OnLoginClicked -> {
                    scope.launch {
                        try {
                            val result = discordAuth.authorize(activityContext)
                            Prefs[Prefs.TOKEN] = result.accessToken
                            uiState = LoginUiState.OnLoginCompleted
                            getUserInfo(result.accessToken, onInfoSaved = {
                                onCompleted()
                            })
                        } catch (e: Exception) {
                            uiState = LoginUiState.InitialState
                        }
                    }
                }
                LoginUiState.OnLoginCompleted -> {
                    buttonEnabledState = false
                    CircularProgressIndicator()
                }
            }
            DiscordLoginButton(
                onClick = { uiState = LoginUiState.OnLoginClicked },
                enabled = buttonEnabledState
            )
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