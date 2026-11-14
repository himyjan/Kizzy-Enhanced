package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred

class DiscordOAuthActivity : Activity() {

    data class OAuthCallback(val code: String, val state: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null && uri.scheme == "kizzy" && uri.host == "oauth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code") ?: ""
            val state = uri.getQueryParameter("state") ?: ""
            deferredResult?.complete(OAuthCallback(code, state))
        } else {
            deferredResult?.completeExceptionally(Exception("Invalid OAuth callback URI"))
        }
        finish()
    }

    companion object {
        private var deferredResult: CompletableDeferred<OAuthCallback>? = null

        fun startAuthorization(): CompletableDeferred<OAuthCallback> {
            deferredResult = CompletableDeferred()
            return deferredResult!!
        }
    }
}
