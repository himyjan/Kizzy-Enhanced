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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCompleted: () -> Unit,
    onBackPressed: () -> Unit
) {
    var showWebView by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                        // Save the account token, then fetch the identity from the
                        // gateway READY payload. Without this step USER_ID /
                        // USER_DATA / USER_BIO are never written: the profile has
                        // no avatar or bio and still shows "Login with Discord".
                        Prefs[Prefs.TOKEN] = token
                        showWebView = false
                        isSaving = true
                        scope.launch {
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
