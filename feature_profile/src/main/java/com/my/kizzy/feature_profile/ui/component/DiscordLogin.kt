/*
 *
 *  *****************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DiscordAuth.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

private const val DISCORD_LOGIN_URL = "https://discord.com/login"
private const val EPHEMERAL_PROFILE_NAME = "kizzy_ephemeral_profile"

private const val EXTRACT_TOKEN_JS = """
    (function() {
        try {
            var i = document.createElement('iframe');
            document.body.appendChild(i);
            var token = i.contentWindow.localStorage.token;
            i.remove();
            return token || '';
        } catch (e) {
            return '';
        }
    })();
"""

class DiscordAuthManager(context: Context) {
    private val appContext = context.applicationContext
    private val contextWrapper = MutableContextWrapper(appContext)
    private var cachedWebView: WebView? = null
    private var isUrlLoaded = false

    @SuppressLint("SetJavaScriptEnabled")
    fun preWarm() {
        if (cachedWebView != null) return

        // 1. Instantiating WebView loads the heavy Chromium native library early
        val webView = WebView(contextWrapper).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Use cache first so repeat loads or cached assets render instantly
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                setLoadsImagesAutomatically(true)
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        // Apply AndroidX isolated multi-profile storage & ensure clean profile state
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val profileStore = ProfileStore.getInstance()
                // Wipe previous profile data if it existed to guarantee freshness
                if (profileStore.getAllProfileNames().contains(EPHEMERAL_PROFILE_NAME)) {
                    profileStore.deleteProfile(EPHEMERAL_PROFILE_NAME)
                }
                profileStore.getProfile(EPHEMERAL_PROFILE_NAME)
                WebViewCompat.setProfile(webView, EPHEMERAL_PROFILE_NAME)
            }
        } catch (e: Exception) {
            // Ignored
        }

        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        cachedWebView = webView
    }

    fun attach(activityContext: Context, onTokenCaptured: (String) -> Unit): WebView {
        if (cachedWebView == null) {
            preWarm()
        }

        contextWrapper.baseContext = activityContext
        val webView = cachedWebView!!
        (webView.parent as? ViewGroup)?.removeView(webView)

        var tokenHandled = false

        fun tryExtractToken(view: WebView, url: String?) {
            if (tokenHandled || url == null) return
            if (url.endsWith("/app") || url.contains("/channels/@me")) {
                view.evaluateJavascript(EXTRACT_TOKEN_JS) { rawResult ->
                    val cleanToken = rawResult
                        ?.removeSurrounding("\"")
                        ?.replace("\\\"", "")
                        ?.trim()
                        .orEmpty()

                    if (cleanToken.length > 20 && cleanToken != "null" && !tokenHandled) {
                        tokenHandled = true
                        onTokenCaptured(cleanToken)
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                tryExtractToken(view, url)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                tryExtractToken(view, url)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                destroy()
                return true
            }
        }

        // Load the URL immediately when attached to window (un-throttled full speed)
        if (!isUrlLoaded || webView.url.isNullOrEmpty()) {
            isUrlLoaded = true
            webView.loadUrl(DISCORD_LOGIN_URL)
        }

        return webView
    }

    fun detach() {
        (cachedWebView?.parent as? ViewGroup)?.removeView(cachedWebView)
        contextWrapper.baseContext = appContext
    }

    fun destroy() {
        cachedWebView?.apply {
            stopLoading()
            clearCache(true)
            clearHistory()
            clearFormData()
            clearSslPreferences()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }

        // Thoroughly purge isolated profile storage, cookies, and local web storage
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                val profileStore = ProfileStore.getInstance()
                if (profileStore.getAllProfileNames().contains(EPHEMERAL_PROFILE_NAME)) {
                    profileStore.deleteProfile(EPHEMERAL_PROFILE_NAME)
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        CookieManager.getInstance().apply {
            removeAllCookies(null)
            removeSessionCookies(null)
            flush()
        }

        try {
            android.webkit.WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            // Ignored
        }

        contextWrapper.baseContext = appContext
        cachedWebView = null
        isUrlLoaded = false
    }
}

interface DiscordAuthManagerProvider {
    val discordAuthManager: DiscordAuthManager
}

@Composable
internal fun DiscordAuthView(
    modifier: Modifier = Modifier,
    onLoginCompleted: (String) -> Unit
) {
    val context = LocalContext.current
    val authManager = remember(context) {
        (context.applicationContext as? DiscordAuthManagerProvider)?.discordAuthManager
            ?: DiscordAuthManager(context)
    }

    DisposableEffect(authManager) {
        onDispose {
            authManager.detach()
            // Force destruction on compose disposal so cookies never leak across visits
            authManager.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            authManager.attach(ctx) { token ->
                onLoginCompleted(token)
                authManager.destroy()
            }
        }
    )
}