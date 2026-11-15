package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred

class DiscordOAuth2Activity : ComponentActivity() {

    data class OAuth2Callback(val code: String, val state: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = when {
            intent?.data?.scheme != "kizzy" ->
                OAuth2Result.Error("Invalid callback scheme")
            intent?.data?.host != "oauth2" ->
                OAuth2Result.Error("Invalid callback host")
            intent?.data?.path != "/callback" ->
                OAuth2Result.Error("Invalid callback path")
            else -> {
                val code = intent?.data?.getQueryParameter("code") ?: ""
                val state = intent?.data?.getQueryParameter("state") ?: ""
                val error = intent?.data?.getQueryParameter("error")

                if (error != null) {
                    OAuth2Result.Error("Discord authorization denied: $error")
                } else if (code.isBlank() || state.isBlank()) {
                    OAuth2Result.Error("Missing code or state in callback")
                } else {
                    OAuth2Result.Success(OAuth2Callback(code, state))
                }
            }
        }

        when (result) {
            is OAuth2Result.Success -> {
                deferredResult?.complete(result.callback)
            }
            is OAuth2Result.Error -> {
                deferredResult?.completeExceptionally(Exception(result.message))
            }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column {
                    Text(
                        text = when (result) {
                            is OAuth2Result.Success -> "Authorizing with Discord..."
                            is OAuth2Result.Error -> "Authorization failed"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (result) {
                            is OAuth2Result.Success -> "Please wait while we complete the Discord OAuth2 authorization."
                            is OAuth2Result.Error -> result.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result is OAuth2Result.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { finish() }) {
                            Text(text = "Close")
                        }
                    }
                }
            }
        }

        if (result is OAuth2Result.Success) {
            finish()
        }
    }

    companion object {
        private var deferredResult: CompletableDeferred<OAuth2Callback>? = null

        fun startAuthorization(): CompletableDeferred<OAuth2Callback> {
            deferredResult = CompletableDeferred()
            return deferredResult!!
        }
    }

    private sealed class OAuth2Result {
        data class Success(val callback: OAuth2Callback) : OAuth2Result()
        data class Error(val message: String) : OAuth2Result()
    }
}