package com.my.kizzy.feature_rpc_base.services.lastfm

import com.my.kizzy.preference.Prefs
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
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.MessageDigest

object LastFM {

    const val API_BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    private var apiKey: String = ""
    private var secret: String = ""

    /** Current session key, set after successful authentication */
    var sessionKey: String? = null

    /** Whether the user is logged in to Last.fm */
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
                url(API_BASE_URL)
            }
            expectSuccess = false
        }
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
    suspend fun getToken(): LastFmTokenResponse? = runCatching {
        val postData = buildPostData("auth.getToken")
        val response = client.post {
            url(API_BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(postData)
        }
        val responseCode = response.status.value
        val responseBody = response.bodyAsText()
        if (responseCode in 200..299) {
            json.decodeFromString<LastFmTokenResponse>(responseBody)
        } else {
            throw LastFmException(responseCode, responseBody)
        }
    }.getOrNull()

    /**
     * Get a session key from a token (second step of authentication).
     */
    suspend fun getSession(token: String): LastFmSession? = runCatching {
        val response = client.post {
            url(API_BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "auth.getSession",
                    extra = mapOf("token" to token)
                )
            )
        }
        val responseText = response.bodyAsText()
        if (responseText.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseText)
            throw LastFmException(error.error, error.message)
        }
        json.decodeFromString<LastFmSession>(responseText)
    }.getOrNull()

    /**
     * Authenticate using username and password (mobile auth).
     */
    suspend fun getMobileSession(username: String, password: String): LastFmSession? = runCatching {
        val response = client.post {
            url(API_BASE_URL)
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
        if (responseText.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseText)
            throw LastFmException(error.error, error.message)
        }
        json.decodeFromString<LastFmSession>(responseText)
    }.getOrNull()

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
    ): Boolean = runCatching {
        val currentSession = sessionKey ?: return@runCatching false
        client.post {
            url(API_BASE_URL)
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
        true
    }.getOrDefault(false)

    /**
     * Update now playing status for a track.
     */
    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null
    ): Boolean = runCatching {
        val currentSession = sessionKey ?: return@runCatching false
        client.post {
            url(API_BASE_URL)
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                buildPostData(
                    method = "track.updateNowPlaying",
                    sessionKey = currentSession,
                    extra = buildMap {
                        put("artist", artist)
                        put("track", track)
                        album?.let { put("album", it) }
                        trackNumber?.let { put("trackNumber[0]", it.toString()) }
                        duration?.let { put("duration[0]", duration.toString()) }
                    }
                )
            )
            parameter("format", "json")
        }
        true
    }.getOrDefault(false)

    /**
     * Set love status for a track.
     */
    suspend fun setLoveStatus(
        artist: String,
        track: String,
        love: Boolean
    ): Boolean = runCatching {
        val currentSession = sessionKey ?: return@runCatching false
        val method = if (love) "track.love" else "track.unlove"
        client.post {
            url(API_BASE_URL)
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
        true
    }.getOrDefault(false)
}

/**
 * Exception thrown when Last.fm API returns an error.
 */
class LastFmException(val code: Int, val errorMessage: String) : Exception("Last.fm error $code: $errorMessage")