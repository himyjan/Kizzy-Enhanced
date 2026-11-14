/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  * DiscordOAuthActivity.kt is part of Kizzy
 *  *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.DiscordAuth
import com.my.kizzy.preference.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles the Discord OAuth2 callback.
 *
 * When the user authorizes Kizzy via Discord's OAuth2 page, Discord redirects
 * to this activity with the authorization code in the query parameters.
 * The activity extracts the code and passes it back to DiscordAuth for
 * token exchange.
 */
class DiscordOAuthActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var discordAuth: DiscordAuth

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        discordAuth = DiscordAuth(this, scope)

        val uri = intent.data

        if (uri == null) {
            Timber.e("DiscordOAuthActivity: No URI in intent")
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Timber.w("DiscordOAuthActivity: OAuth error: $error")
            finish()
            return
        }

        if (code == null || state == null) {
            Timber.e("DiscordOAuthActivity: Missing code or state in URI")
            finish()
            return
        }

        // Complete the pending authorization with the code and state
        DiscordOAuthActivity.completeCallback(code, state)

        setContent {
            Surface {
                Text(
                    text = "Authorizing with Discord...",
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "DiscordOAuthActivity"

        private var pendingDeferred: kotlinx.coroutines.CompletableDeferred<Callback>? = null

        fun startAuthorization(): kotlinx.coroutines.CompletableDeferred<Callback> {
            pendingDeferred = kotlinx.coroutines.CompletableDeferred()
            return pendingDeferred!!
        }

        fun completeCallback(code: String, state: String) {
            pendingDeferred?.complete(Callback(code, state))
            pendingDeferred = null
        }

        data class Callback(val code: String, val state: String)
    }
}
