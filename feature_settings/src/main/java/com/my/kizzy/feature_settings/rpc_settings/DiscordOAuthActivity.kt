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

class DiscordOAuthActivity : ComponentActivity() {

    data class OAuthCallback(val code: String, val state: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = when {
            intent?.data?.scheme != "kizzy" ->
                OAuthResult.Error("Invalid callback scheme")
            intent?.data?.host != "oauth" ->
                OAuthResult.Error("Invalid callback host")
            intent?.data?.path != "/callback" ->
                OAuthResult.Error("Invalid callback path")
            else -> {
                val code = intent?.data?.getQueryParameter("code") ?: ""
                val state = intent?.data?.getQueryParameter("state") ?: ""
                val error = intent?.data?.getQueryParameter("error")

                if (error != null) {
                    OAuthResult.Error("Discord authorization denied: $error")
                } else if (code.isBlank() || state.isBlank()) {
                    OAuthResult.Error("Missing code or state in callback")
                } else {
                    OAuthResult.Success(OAuthCallback(code, state))
                }
            }
        }

        when (result) {
            is OAuthResult.Success -> {
                deferredResult?.complete(result.callback)
            }
            is OAuthResult.Error -> {
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
                            is OAuthResult.Success -> "Authorizing with Discord..."
                            is OAuthResult.Error -> "Authorization failed"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (result) {
                            is OAuthResult.Success -> "Please wait while we complete the Discord OAuth2 authorization."
                            is OAuthResult.Error -> result.message
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result is OAuthResult.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { finish() }) {
                            Text(text = "Close")
                        }
                    }
                }
            }
        }

        if (result is OAuthResult.Success) {
            finish()
        }
    }

    companion object {
        private var deferredResult: CompletableDeferred<OAuthCallback>? = null

        fun startAuthorization(): CompletableDeferred<OAuthCallback> {
            deferredResult = CompletableDeferred()
            return deferredResult!!
        }
    }

    private sealed class OAuthResult {
        data class Success(val callback: OAuthCallback) : OAuthResult()
        data class Error(val message: String) : OAuthResult()
    }
}
