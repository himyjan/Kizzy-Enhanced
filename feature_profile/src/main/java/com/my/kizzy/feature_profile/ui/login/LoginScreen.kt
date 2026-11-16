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

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.my.kizzy.feature_profile.ui.component.DiscordLoginButton
import com.my.kizzy.ui.components.BackButton

private const val DISCORD_LOGIN_URL = "https://discord.com/login"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCompleted: () -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { BackButton { onBackPressed() } }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            DiscordLoginButton(
                onClick = {
                    val activity = context as? Activity ?: return@DiscordLoginButton
                    try {
                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .setEphemeralBrowsingEnabled(true)
                            .build()

                        customTabsIntent.launchUrl(activity, Uri.parse(DISCORD_LOGIN_URL))
                    } catch (e: Exception) {
                        // Handle browser launch exception if Custom Tabs are unavailable
                    }
                },
                enabled = true
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