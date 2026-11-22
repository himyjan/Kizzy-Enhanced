package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.my.kizzy.preference.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DiscordOAuth2Auth(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val authTabLauncher: ActivityResultLauncher<Intent>? = null,
) {

    companion object {
        private const val TAG = "DiscordOAuth2Auth"
        private const val DISCORD_OAUTH2_AUTHORIZE = "https://discord.com/oauth2/authorize"
        private const val DISCORD_OAUTH2_TOKEN = "https://discord.com/api/v10/oauth2/token"
        // Social SDK default presence scopes: required for the OAuth2 token to
        // connect to the gateway and publish rich presence (same as Metrolist).
        // `identify` keeps REST /users/@me working for the profile (avatar/bio).
        private const val DISCORD_SCOPES = "openid identify sdk.social_layer_presence"
        private const val REDIRECT_URI = "kizzy://oauth2/callback"
        private const val REDIRECT_SCHEME = "kizzy"

        private const val DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID = "1571063796418744412"
        private const val DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID2 = "1573725984845201438"
        private const val DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID3 = "1573728208854065293"
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
        class OAuthError(message: String) : DiscordOAuth2Exception(message)
    }

    private var continuation: CancellableContinuation<DiscordOAuth2Result>? = null
    private var pendingAuth: PendingAuth? = null

    private data class PendingAuth(
        val slot: String,
        val clientId: String,
        val redirectUri: String,
        val pkce: PkcePair,
        val state: String
    )

    suspend fun authorize(activity: Activity, slot: String = "1"): DiscordOAuth2Result {
        val clientId = when (slot) {
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID2].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID2 }
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID3].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID3 }
            else -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID }
        }
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
            val authUri = authUrl.toUri()

            pendingAuth = PendingAuth(slot, clientId, REDIRECT_URI, pkce, state)

            val browserPackage = CustomTabsClient.getPackageName(activity, null)
            val launcher = authTabLauncher
            val authTabSupported = launcher != null &&
                browserPackage != null &&
                CustomTabsClient.isAuthTabSupported(activity, browserPackage)

            Log.d(
                TAG,
                "authorize: browserPackage=$browserPackage, launcherNull=${launcher == null}, authTabSupported=$authTabSupported"
            )

            if (authTabSupported) {
                Log.d(TAG, "authorize: launching OAuth2 URL via AuthTabIntent (ephemeral)")
                val authTabIntent = AuthTabIntent.Builder()
                    .setEphemeralBrowsingEnabled(true)
                    .build()
                authTabIntent.launch(launcher, authUri, REDIRECT_SCHEME)
            } else {
                Log.d(TAG, "authorize: launching OAuth2 URL via CustomTabsIntent fallback")
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(activity, authUri)
            }

            return suspendCancellableCoroutine { cont ->
                continuation = cont
                cont.invokeOnCancellation {
                    if (continuation === cont) {
                        continuation = null
                        pendingAuth = null
                    }
                }
            }
        } catch (e: DiscordOAuth2Exception) {
            pendingAuth = null
            throw e
        } catch (e: CancellationException) {
            pendingAuth = null
            throw e
        } catch (e: Exception) {
            pendingAuth = null
            Log.e(TAG, "authorize: unexpected failure", e)
            throw DiscordOAuth2Exception.NetworkFailure(e)
        }
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val cont = this.continuation
        val auth = this.pendingAuth
        Log.d(TAG, "onActivityResult: resultCode=$resultCode, uri=${data?.data}, hasCont=${cont != null}, hasPending=${auth != null}")

        if (cont == null) return
        this.continuation = null
        this.pendingAuth = null

        when (resultCode) {
            Activity.RESULT_OK -> {
                if (auth == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(DiscordOAuth2Exception.NetworkFailure(IOException("Missing pending auth state")))
                    }
                    return
                }

                val callbackUri = data?.data
                val code = callbackUri?.getQueryParameter("code")
                val state = callbackUri?.getQueryParameter("state")

                // Discord may redirect back with ?error=... instead of ?code=...
                // (e.g. invalid_scope, access_denied). Surface that clearly
                // instead of the misleading "Missing code" network error.
                val oauthError = callbackUri?.getQueryParameter("error")
                if (!oauthError.isNullOrBlank()) {
                    val description = callbackUri.getQueryParameter("error_description")
                        ?.replace("+", " ")
                        ?.let { ": $it" }.orEmpty()
                    val message = "$oauthError$description"
                    Log.w(TAG, "onActivityResult: OAuth error: $message")
                    if (cont.isActive) {
                        cont.resumeWithException(DiscordOAuth2Exception.OAuthError(message))
                    }
                    return
                }

                if (state != auth.state) {
                    Log.w(TAG, "onActivityResult: state mismatch expected=${auth.state} actual=$state")
                    if (cont.isActive) {
                        cont.resumeWithException(DiscordOAuth2Exception.StateMismatch())
                    }
                    return
                }

                if (code.isNullOrBlank()) {
                    Log.w(TAG, "onActivityResult: missing authorization code in uri=$callbackUri")
                    if (cont.isActive) {
                        cont.resumeWithException(DiscordOAuth2Exception.NetworkFailure(IOException("Missing code")))
                    }
                    return
                }

                scope.launch {
                    try {
                        val result = exchangeAuthorizationCode(
                            code = code,
                            verifier = auth.pkce.verifier,
                            redirectUri = auth.redirectUri,
                            clientId = auth.clientId,
                            slot = auth.slot
                        )
                        if (cont.isActive) {
                            cont.resume(result)
                        }
                    } catch (e: CancellationException) {
                        if (cont.isActive) {
                            cont.cancel(e)
                        }
                        throw e
                    } catch (e: DiscordOAuth2Exception) {
                        if (cont.isActive) {
                            cont.resumeWithException(e)
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) {
                            cont.resumeWithException(DiscordOAuth2Exception.NetworkFailure(e))
                        }
                    }
                }
            }

            Activity.RESULT_CANCELED -> {
                if (cont.isActive) {
                    cont.resumeWithException(DiscordOAuth2Exception.UserCancelled())
                }
            }

            else -> {
                if (cont.isActive) {
                    cont.resumeWithException(DiscordOAuth2Exception.NetworkFailure(IOException("Unknown result code: $resultCode")))
                }
            }
        }
    }

    private suspend fun exchangeAuthorizationCode(
        code: String,
        verifier: String,
        redirectUri: String,
        clientId: String,
        slot: String = "1"
    ): DiscordOAuth2Result {
        val params = mapOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier
        )
        return performTokenExchange(params, slot)
    }

    suspend fun refresh(refreshToken: String, slot: String = "1"): DiscordOAuth2Result {
        val clientId = when (slot) {
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID2].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID2 }
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID3].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID3 }
            else -> Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID].ifBlank { DEFAULT_DISCORD_OAUTH2_APPLICATION_CLIENT_ID }
        }
        val params = mapOf(
            "client_id" to clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken
        )
        return performTokenExchange(params, slot)
    }

    private suspend fun performTokenExchange(params: Map<String, String>, slot: String = "1"): DiscordOAuth2Result {
        return withContext(Dispatchers.IO) {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 15_000
                    socketTimeoutMillis = 10_000
                }
                defaultRequest {
                    url(DISCORD_OAUTH2_TOKEN)
                }
            }.use { client ->
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

                    Log.d(TAG, "token exchange: success for slot $slot")

                    when (slot) {
                        "2" -> {
                            Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_2] = accessToken
                            Prefs[Prefs.RPC_SESSION_2_ENABLED] = true
                        }
                        "3" -> {
                            Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_3] = accessToken
                            Prefs[Prefs.RPC_SESSION_3_ENABLED] = true
                        }
                        else -> {
                            Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = accessToken
                            if (refreshToken.isNotEmpty()) {
                                Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN] = refreshToken
                            }
                            Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT] = System.currentTimeMillis() + expiresIn * 1000
                        }
                    }

                    DiscordOAuth2Result(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresInSec = expiresIn,
                        scope = scope,
                    )
                } else {
                    val errorMsg = runCatching {
                        val json = JSONObject(responseBody)
                        json.getString("error") + (json.optString("error_description").let { if (it.isNotEmpty()) ": $it" else "" })
                    }.getOrElse { responseBody }

                    Log.w(TAG, "token exchange: HTTP $responseCode error body=$responseBody")
                    throw DiscordOAuth2Exception.NetworkFailure(IOException("HTTP $responseCode: $errorMsg"))
                }
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

    fun hasValidToken(slot: String = "1"): Boolean {
        val token = when (slot) {
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_2, ""]
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_3, ""]
            else -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        }
        return token.isNotEmpty() && !isTokenExpired(slot)
    }

    fun isTokenExpired(slot: String = "1"): Boolean {
        if (slot != "1") return false
        val expiresAt = Prefs[Prefs.DISCORD_OAUTH2_EXPIRES_AT, 0L]
        return expiresAt > 0L && System.currentTimeMillis() >= expiresAt
    }

    suspend fun getValidAccessToken(slot: String = "1"): String? {
        val token = when (slot) {
            "2" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_2, ""]
            "3" -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN_3, ""]
            else -> Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""]
        }
        if (token.isEmpty()) return null
        if (!isTokenExpired(slot)) return token
        val refreshToken = Prefs[Prefs.DISCORD_OAUTH2_REFRESH_TOKEN, ""]
        if (refreshToken.isEmpty()) return token
        try {
            val refreshed = refresh(refreshToken, slot)
            return refreshed.accessToken
        } catch (e: Exception) {
            Log.w(TAG, "getValidAccessToken: refresh failed", e)
            return token
        }
    }

    fun getAccessToken(): String? {
        return Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].takeIf { it.isNotEmpty() }
    }

    suspend fun clearTokens() {
        Prefs.remove(Prefs.DISCORD_OAUTH2_ACCESS_TOKEN)
        Prefs.remove(Prefs.DISCORD_OAUTH2_REFRESH_TOKEN)
        Prefs.remove(Prefs.DISCORD_OAUTH2_EXPIRES_AT)
        Prefs.remove(Prefs.DISCORD_OAUTH2_USER_ID)
        Prefs.remove(Prefs.DISCORD_OAUTH2_USERNAME)
    }
}