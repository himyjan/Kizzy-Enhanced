package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.SecurePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class DiscordAuth(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    companion object {
        private const val TAG = "DiscordAuth"
        private const val DISCORD_OAUTH_AUTHORIZE = "https://discord.com/oauth2/authorize"
        private const val DISCORD_OAUTH_TOKEN = "https://discord.com/api/v10/oauth2/token"
        private const val DISCORD_SCOPES = "identify guilds"
        private const val REDIRECT_URI = "kizzy://oauth/callback"
        private const val DEFAULT_CLIENT_ID = "1144460930916270110"
    }

    data class DiscordAuthResult(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSec: Long,
        val scope: String,
    )

    data class PkcePair(val verifier: String, val challenge: String)

    sealed class DiscordAuthException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class UserCancelled(message: String = "User cancelled authorization") : DiscordAuthException(message)
        class NetworkFailure(cause: Throwable) : DiscordAuthException("Network failure: ${cause.message}", cause)
        class InvalidGrant(message: String = "Invalid or expired grant") : DiscordAuthException(message)
        class StateMismatch : DiscordAuthException("OAuth state mismatch")
        class NoBrowser(message: String = "No browser available") : DiscordAuthException(message)
        class InvalidClientId(message: String = "Invalid or missing Client ID") : DiscordAuthException(message)
    }

    suspend fun authorize(activity: Activity): DiscordAuthResult {
        val clientId = Prefs[Prefs.DISCORD_OAUTH_CLIENT_ID, DEFAULT_CLIENT_ID]
        if (clientId.isBlank()) {
            throw DiscordAuthException.InvalidClientId()
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

            Log.d(TAG, "authorize: launching OAuth URL")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = authUrl.toUri()
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "authorize: no browser available", e)
                throw DiscordAuthException.NoBrowser()
            }

            val deferred = DiscordOAuthActivity.startAuthorization()
            val callback = deferred.await()

            if (callback.state != state) {
                Log.w(TAG, "authorize: state mismatch")
                throw DiscordAuthException.StateMismatch()
            }

            return exchangeAuthorizationCode(
                code = callback.code,
                verifier = pkce.verifier,
                redirectUri = REDIRECT_URI,
                clientId = clientId,
            )
        } catch (e: DiscordAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "authorize: unexpected failure", e)
            throw DiscordAuthException.NetworkFailure(e)
        }
    }

    private suspend fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        redirectUri: String,
        clientId: String,
    ): DiscordAuthResult {
        return performTokenExchange(
            grantType = "authorization_code",
            extraParams = mutableMapOf(
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
                "client_id" to clientId
            ),
        )
    }

    suspend fun refresh(refreshToken: String): DiscordAuthResult {
        val clientId = Prefs[Prefs.DISCORD_OAUTH_CLIENT_ID, DEFAULT_CLIENT_ID]
        return performTokenExchange(
            grantType = "refresh_token",
            extraParams = mutableMapOf(
                "refresh_token" to refreshToken,
                "client_id" to clientId
            ),
        )
    }

    private suspend fun performTokenExchange(
        grantType: String,
        extraParams: Map<String, String>,
    ): DiscordAuthResult {
        val client = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 10_000L
            }
            expectSuccess = false
        }

        try {
            val response = client.post(DISCORD_OAUTH_TOKEN) {
                setBody(io.ktor.client.request.forms.submitForm {
                    append("client_id", Prefs[Prefs.DISCORD_OAUTH_CLIENT_ID, DEFAULT_CLIENT_ID])
                    append("grant_type", grantType)
                    extraParams.forEach { name, values ->
                        values.forEach { value -> append(name, value) }
                    }
                })
            }

            val status = response.status
            val body = response.bodyAsText()

            if (status.value in 200..299) {
                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 0L)
                val scope = json.optString("scope", DISCORD_SCOPES)

                Log.d(TAG, "token exchange: success")

                Prefs[Prefs.DISCORD_OAUTH_ACCESS_TOKEN] = accessToken
                if (refreshToken.isNotEmpty()) {
                    Prefs[Prefs.DISCORD_OAUTH_REFRESH_TOKEN] = refreshToken
                }
                Prefs[Prefs.DISCORD_OAUTH_EXPIRES_AT] = System.currentTimeMillis() + expiresIn * 1000

                return DiscordAuthResult(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSec = expiresIn,
                    scope = scope,
                )
            } else {
                val errorCode = runCatching { JSONObject(body).optString("error", "") }
                    .getOrDefault("")
                Log.w(TAG, "token exchange: HTTP $status.value error=$errorCode body=$body")
                throw DiscordAuthException.NetworkFailure(java.io.IOException("HTTP ${status.value}: $body"))
            }
        } catch (e: DiscordAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "token exchange: network failure", e)
            throw DiscordAuthException.NetworkFailure(e)
        } finally {
            client.close()
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
            append(DISCORD_OAUTH_AUTHORIZE)
            append("?client_id=").append(clientId)
            append("&response_type=code")
            append("&redirect_uri=").append(encodedRedirect)
            append("&scope=").append(encodedScope)
            append("&state=").append(state)
            append("&code_challenge_method=S256")
            append("&code_challenge=").append(challenge)
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
        val token = Prefs[Prefs.DISCORD_OAUTH_ACCESS_TOKEN, ""]
        return token.isNotEmpty()
    }

    fun getAccessToken(): String? {
        return Prefs[Prefs.DISCORD_OAUTH_ACCESS_TOKEN, ""].takeIf { it.isNotEmpty() }
    }

    fun clearTokens() {
        Prefs[Prefs.DISCORD_OAUTH_ACCESS_TOKEN] = ""
        Prefs[Prefs.DISCORD_OAUTH_REFRESH_TOKEN] = ""
        Prefs[Prefs.DISCORD_OAUTH_EXPIRES_AT] = ""
        Prefs[Prefs.DISCORD_OAUTH_USER_ID] = ""
        Prefs[Prefs.DISCORD_OAUTH_USERNAME] = ""
    }
}
