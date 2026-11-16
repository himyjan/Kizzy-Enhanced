package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.my.kizzy.preference.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class DiscordOAuth2Auth(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    companion object {
        private const val TAG = "DiscordOAuth2Auth"
        private const val DISCORD_OAUTH2_AUTHORIZE = "https://discord.com/oauth2/authorize"
        private const val DISCORD_OAUTH2_TOKEN = "https://discord.com/api/v10/oauth2/token"
        private const val DISCORD_SCOPES = "identify guilds"
        private const val REDIRECT_URI = "kizzy://oauth2/callback"

        private const val DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID = "1571063796418744412"
    }

    data class DiscordOAuth2Result(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSec: Long,
        val scope: String,
    )

    data class PkcePair(val verifier: String, val challenge: String)

    sealed class DiscordOAuth2Exception(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class UserCancelled(message: String = "User cancelled authorization") : DiscordOAuth2Exception(message)
        class NetworkFailure(cause: Throwable) : DiscordOAuth2Exception("Network failure: ${cause.message}", cause)
        class InvalidGrant(message: String = "Invalid or expired grant") : DiscordOAuth2Exception(message)
        class StateMismatch : DiscordOAuth2Exception("OAuth2 state mismatch")
        class NoBrowser(message: String = "No browser available") : DiscordOAuth2Exception(message)
        class InvalidClientId(message: String = "Invalid or missing Client ID") : DiscordOAuth2Exception(message)
    }

    suspend fun authorize(activity: Activity): DiscordOAuth2Result {
        val clientId = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID]
        if (clientId.isBlank()) {
            throw DiscordOAuth2Exception.InvalidClientId()
        }

        val pkce = generatePkcePair()
        val state = generateState()

        try {
            val authUrl = buildAuthorizeUrl(
                clientId = clientId,
                redirectUri = REDIRECT_URI,
                state = state,
                challenge = pkce.challenge,
            )

            Log.d(TAG, "authorize: launching OAuth2 URL via Custom Tabs")

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setEphemeralBrowsingEnabled(true)
                .build()

            try {
                customTabsIntent.launchUrl(activity, authUrl.toUri())
            } catch (e: Exception) {
                Log.e(TAG, "authorize: Custom Tabs failed", e)
                throw DiscordOAuth2Exception.NetworkFailure(e)
            }

            val deferred = DiscordOAuth2Activity.startAuthorization()
            val callback = deferred.await()

            if (callback.state != state) {
                Log.w(TAG, "authorize: state mismatch")
                throw DiscordOAuth2Exception.StateMismatch()
            }

            return exchangeAuthorizationCode(
                code = callback.code,
                verifier = pkce.verifier,
                redirectUri = REDIRECT_URI,
                clientId = clientId,
            )
        } catch (e: DiscordOAuth2Exception) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "authorize: unexpected failure", e)
            throw DiscordOAuth2Exception.NetworkFailure(e)
        }
    }

    private suspend fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        redirectUri: String,
        clientId: String,
    ): DiscordOAuth2Result {
        val params = mapOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier
        )
        return performTokenExchange(params)
    }

    suspend fun refresh(refreshToken: String): DiscordOAuth2Result {
        val clientId = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID]
        val params = mapOf(
            "client_id" to clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken
        )
        return performTokenExchange(params)
    }

    private suspend fun performTokenExchange(params: Map<String, String>): DiscordOAuth2Result {
        return withContext(Dispatchers.IO) {
            val client = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 10_000
                }
                defaultRequest {
                    url(DISCORD_OAUTH2_TOKEN)
                }
            }
            val postData = params.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
            val response = client.post {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(postData)
            }
            val responseCode = response.status.value
            val responseBody = response.bodyAsText()

            if (responseCode in 200..299) {
                val json = JSONObject(responseBody)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 0L)
                val scope = json.optString("scope", DISCORD_SCOPES)

                Log.d(TAG, "token exchange: success")

                Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = accessToken
                if (refreshToken.isNotEmpty()) {
                    Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN] = refreshToken
                }
                Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT] = System.currentTimeMillis() + expiresIn * 1000

                return@withContext DiscordOAuth2Result(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSec = expiresIn,
                    scope = scope,
                )
            } else {
                Log.w(TAG, "token exchange: HTTP $responseCode error body=$responseBody")
                throw DiscordOAuth2Exception.NetworkFailure(java.io.IOException("HTTP $responseCode:$responseBody"))
            }
        }
    }

    private fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        challenge: String,
    ): String {
        val encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.name())
        val encodedScope = URLEncoder.encode(DISCORD_SCOPES, StandardCharsets.UTF_8.name())
        return buildString {
            append(DISCORD_OAUTH2_AUTHORIZE)
            append("?client_id=").append(clientId)
            append("&response_type=code")
            append("&redirect_uri=").append(encodedRedirect)
            append("&scope=").append(encodedScope)
            append("&state=").append(state)
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(challenge)
            // Force login screen so user can switch accounts after logout
            append("&prompt=login")
        }
    }

    private fun generatePkcePair(): PkcePair {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return PkcePair(verifier = verifier, challenge = challenge)
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    fun hasValidToken(): Boolean {
        val token = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        return token.isNotEmpty() && !isTokenExpired()
    }

    fun isTokenExpired(): Boolean {
        val expiresAt = Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT, 0L]
        return expiresAt > 0L && System.currentTimeMillis() >= expiresAt
    }

    suspend fun getValidAccessToken(): String? {
        val token = Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        if (token.isEmpty()) return null
        if (!isTokenExpired()) return token
        val refreshToken = Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN, ""]
        if (refreshToken.isEmpty()) return token
        try {
            val refreshed = refresh(refreshToken)
            return refreshed.accessToken
        } catch (e: DiscordOAuth2Exception) {
            Log.w(TAG, "getValidAccessToken: refresh failed", e)
            return token
        }
    }

    fun getAccessToken(): String? {
        return Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].takeIf { it.isNotEmpty() }
    }

    fun clearTokens() {
        Prefs.remove(Prefs.DISCORD_OAUTH2_ACCESS_TOKEN)
        Prefs.remove(Prefs.DISCORD_OAUTH2_REFRESH_TOKEN)
        Prefs.remove(Prefs.DISCORD_OAUTH2_EXPIRES_AT)
        Prefs.remove(Prefs.DISCORD_OAUTH2_USER_ID)
        Prefs.remove(Prefs.DISCORD_OAUTH2_USERNAME)
    }
}