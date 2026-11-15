package com.my.kizzy.feature_rpc_base.services.lastfm

import com.my.kizzy.preference.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.security.MessageDigest

object LastFM {

    const val API_BASE_URL = "https://ws.audioscrobbler.com/2.0/"

    // These will be set via init() from the app's configuration
    private var apiKey: String = ""
    private var secret: String = ""

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

    private fun HttpRequestBuilder.lastfmParams(
        method: String,
        apiKey: String,
        secret: String,
        sessionKey: String? = null,
        extra: Map<String, String> = emptyMap(),
        format: String = "json"
    ) {
        contentType(ContentType.Application.FormUrlEncoded)
        val paramsForSig = mutableMapOf(
            "method" to method,
            "api_key" to apiKey
        ).apply {
            sessionKey?.let { put("sk", it) }
            putAll(extra)
        }
        val apiSig = paramsForSig.apiSig(secret)
        setBody(FormDataContent(Parameters.build {
            paramsForSig.forEach { (k, v) -> append(k, v) }
            append("api_sig", apiSig)
            append("format", format)
        }))
    }

    /**
     * Get an OAuth token from Last.fm (first step of authentication).
     */
    suspend fun getToken(): LastFmTokenResponse? = runCatching {
        client.post {
            lastfmParams(
                method = "auth.getToken",
                apiKey = apiKey,
                secret = secret
            )
        }.body<LastFmTokenResponse>()
    }.getOrNull()

    /**
     * Get a session key from a token (second step of authentication).
     */
    suspend fun getSession(token: String): LastFmSession? = runCatching {
        client.post {
            lastfmParams(
                method = "auth.getSession",
                apiKey = apiKey,
                secret = secret,
                extra = mapOf("token" to token)
            )
        }.body<LastFmSession>()
    }.getOrNull()

    /**
     * Authenticate using username and password (mobile auth).
     */
    suspend fun getMobileSession(username: String, password: String): LastFmSession? = runCatching {
        val response = client.post {
            lastfmParams(
                method = "auth.getMobileSession",
                apiKey = apiKey,
                secret = secret,
                extra = mapOf("username" to username, "password" to password)
            )
            parameter("format", "json")
        }

        val responseText = response.bodyAsText()
        if (responseText.contains("\"error\"")) {
            val error = json.decodeFromString<LastFmError>(responseText)
            throw LastFmException(error.error, error.message)
        }
        json.decodeFromString<LastFmSession>(responseText)
    }

    /**
     * Scrobble a track to Last.fm.
     *
     * @param artist The artist name
     * @param track The track name
     * @param timestamp Unix timestamp when the track started playing
     * @param album Optional album name
     * @param trackNumber Optional track number
     * @param duration Optional track duration in seconds
     */
    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        trackNumber: Int? = null,
        duration: Int? = null
    ): Boolean = runCatching {
        client.post {
            lastfmParams(
                method = "track.scrobble",
                apiKey = apiKey,
                secret = secret,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist[0]", artist)
                    put("track[0]", track)
                    put("timestamp[0]", timestamp.toString())
                    album?.let { put("album[0]", it) }
                    trackNumber?.let { put("trackNumber[0]", it.toString()) }
                    duration?.let { put("duration[0]", it.toString()) }
                }
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
        client.post {
            lastfmParams(
                method = "track.updateNowPlaying",
                apiKey = apiKey,
                secret = secret,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist", artist)
                    put("track", track)
                    album?.let { put("album", it) }
                    trackNumber?.let { put("trackNumber", it.toString()) }
                    duration?.let { put("duration", it.toString()) }
                }
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
        val method = if (love) "track.love" else "track.unlove"
        client.post {
            lastfmParams(
                method = method,
                apiKey = apiKey,
                secret = secret,
                sessionKey = sessionKey!!,
                extra = buildMap {
                    put("artist", artist)
                    put("track", track)
                }
            )
            parameter("format", "json")
        }
        true
    }.getOrDefault(false)

    /** Current session key, set after successful authentication */
    var sessionKey: String? = null

    /** Whether the user is logged in to Last.fm */
    val isAuthenticated: Boolean
        get() = sessionKey.isNotEmpty()
}

/**
 * Exception thrown when Last.fm API returns an error.
 */
class LastFmException(val code: Int, val message: String) : Exception("Last.fm error $code: $message")
