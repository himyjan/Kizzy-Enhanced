package com.my.kizzy.feature_rpc_base.services.lastfm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.security.MessageDigest

private const val LASTFM_API_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
private const val LIBREFM_API_BASE_URL = "https://libre.fm/2.0/"
private const val MAX_API_RETRIES = 3

// TODO: Replace with your own Last.fm API key and secret obtained from https://www.last.fm/api
const val DEFAULT_LASTFM_API_KEY = "d678407da0340da567478a3e5645f864"
const val DEFAULT_LASTFM_SECRET = "7c3200d4a626f3d546da37eecd0849a2"

// TODO: Replace with your own Libre.fm API key and secret
internal const val DEFAULT_LIBREFM_API_KEY = "YOUR_LIBREFM_API_KEY"
internal const val DEFAULT_LIBREFM_SECRET = "YOUR_LIBREFM_SECRET"

open class LastFmApiClient(
    private val apiBaseUrl: String,
    loggerName: String
) {

    private var apiKey: String = ""
    private var secret: String = ""

    /** Current session key, set after successful authentication */
    var sessionKey: String? = null

    /** Whether the user has an authenticated session for this service */
    val isAuthenticated: Boolean
        get() = !sessionKey.isNullOrEmpty()

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                url(apiBaseUrl)
                header(HttpHeaders.UserAgent, "Kizzy/1.0")
            }
            expectSuccess = false
        }
    }

    private val logger: Logger = LoggerFactory.getLogger(loggerName)

    /**
     * Maximum number of retry attempts for failed Last.fm API calls.
     * Exponential backoff: 1s, 2s, 4s
     */
    /**
     * Execute a Last.fm API call with retry logic and exponential backoff.
     * Returns null if all retries fail.
     */
    private suspend fun <T> apiCallWithRetry(call: suspend () -> T): T? {
        var delayMs = 1000L
        var lastError: String? = null
        for (attempt in 1..MAX_API_RETRIES) {
            val result = try {
                call()
            } catch (e: LastFmException) {
                lastError = e.errorMessage
                if (e.code in 400..499 || e.code in setOf(4, 6, 9, 10, 13, 26)) throw e
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message
                null
            }
            if (result != null) return result
            if (attempt < MAX_API_RETRIES) {
                logger.debug("LastFM API call attempt $attempt failed, retrying in ${delayMs}ms")
                delay(delayMs.milliseconds)
                delayMs *= 2L
            }
        }
        logger.warn("LastFM API call failed after $MAX_API_RETRIES attempts: $lastError")
        return null
    }

    /**
     * Initialize Last.fm with API credentials.
     * Called once during app initialization.
     */
    fun init(apiKey: String, secret: String) {
        this.apiKey = apiKey
        this.secret = secret
    }

    fun isInitialized(): Boolean = apiKey.isNotEmpty() && secret.isNotEmpty()

    private fun Map<String, String>.apiSig(secret: String): String {
        val sorted = toSortedMap()
        val toHash = sorted.entries.joinToString("") { it.key + it.value } + secret
        val digest = MessageDigest.getInstance("MD5").digest(toHash.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun validateResponse(responseCode: Int, responseBody: String) {
        if (responseCode !in 200..299) {
            throw LastFmException(responseCode, responseBody)
        }
        if (responseBody.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseBody)
            throw LastFmException(error.error, error.message)
        }
    }

    internal fun decodeSessionResponse(responseBody: String): LastFmSession {
        if (responseBody.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseBody)
            throw LastFmException(error.error, error.message)
        }
        return json.decodeFromString<LastFmSessionResponse>(responseBody).session
    }

    private fun buildPostData(
        method: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap()
    ): String {
        val params = mutableMapOf(
            "method" to method,
            "api_key" to apiKey
        )
        sessionKey?.let { params["sk"] = it }
        params.putAll(extra)
        val apiSig = params.apiSig(secret)
        params["api_sig"] = apiSig
        params["format"] = "json"
        return params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
    }

    /**
     * Get an OAuth token from Last.fm (first step of authentication).
     */
    suspend fun getToken(): LastFmTokenResponse? = apiCallWithRetry {
        val postData = buildPostData("auth.getToken")
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(postData)
        }
        val responseCode = response.status.value
        val responseBody = response.bodyAsText()
        validateResponse(responseCode, responseBody)
        json.decodeFromString<LastFmTokenResponse>(responseBody)
    }

    /**
     * Get a session key from a token (second step of authentication).
     */
    suspend fun getSession(token: String): LastFmSession? = apiCallWithRetry {
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "auth.getSession",
                    extra = mapOf("token" to token)
                )
            )
        }
        val responseText = response.bodyAsText()
        validateResponse(response.status.value, responseText)
        decodeSessionResponse(responseText)
    }

    /**
     * Authenticate using username and password (mobile auth).
     */
    suspend fun getMobileSession(username: String, password: String): LastFmSession? = apiCallWithRetry {
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "auth.getMobileSession",
                    extra = mapOf("username" to username, "password" to password)
                )
            )
            parameter("format", "json")
        }

        val responseText = response.bodyAsText()
        validateResponse(response.status.value, responseText)
        decodeSessionResponse(responseText)
    }

    /**
     * Scrobble a track to Last.fm.
     */
    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null
    ): Boolean = apiCallWithRetry {
        val currentSession = sessionKey ?: return@apiCallWithRetry false
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "track.scrobble",
                    sessionKey = currentSession,
                    extra = buildMap {
                        put("artist[0]", artist)
                        put("track[0]", track)
                        put("timestamp[0]", timestamp.toString())
                        album?.let { put("album[0]", it) }
                        trackNumber?.let { put("trackNumber[0]", it.toString()) }
                        duration?.let { put("duration[0]", duration.toString()) }
                    }
                )
            )
            parameter("format", "json")
        }
        validateResponse(response.status.value, response.bodyAsText())
        true
    } != null

    /**
     * Update now playing status for a track.
     */
    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null
    ): Boolean = apiCallWithRetry {
        val currentSession = sessionKey ?: return@apiCallWithRetry false
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "track.updateNowPlaying",
                    sessionKey = currentSession,
                    extra = buildMap {
                        put("artist", artist)
                        put("track", track)
                        album?.let { put("album", it) }
                        trackNumber?.let { put("trackNumber", it.toString()) }
                        duration?.let { put("duration", it.toString()) }
                    }
                )
            )
            parameter("format", "json")
        }
        validateResponse(response.status.value, response.bodyAsText())
        true
    } != null

    /**
     * Set love status for a track.
     */
    suspend fun setLoveStatus(
        artist: String,
        track: String,
        love: Boolean
    ): Boolean = apiCallWithRetry {
        val currentSession = sessionKey ?: return@apiCallWithRetry false
        val method = if (love) "track.love" else "track.unlove"
        val response = client.post {
            url(apiBaseUrl)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = method,
                    sessionKey = currentSession,
                    extra = mapOf(
                        "artist" to artist,
                        "track" to track
                    )
                )
            )
            parameter("format", "json")
        }
        validateResponse(response.status.value, response.bodyAsText())
        true
    } != null
}

object LastFM : LastFmApiClient(LASTFM_API_BASE_URL, "LastFM") {
    const val API_BASE_URL = "https://ws.audioscrobbler.com/2.0/"
}

object LibreFM : LastFmApiClient(LIBREFM_API_BASE_URL, "LibreFM") {
    const val API_BASE_URL = "https://libre.fm/2.0/"
}

/**
 * Exception thrown when a Last.fm-compatible API returns an error.
 */
class LastFmException(val code: Int, val errorMessage: String) :
    Exception("Scrobbling API error $code: $errorMessage")