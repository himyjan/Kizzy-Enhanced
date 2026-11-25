package com.my.kizzy.feature_rpc_base.services.listenbrainz

import com.my.kizzy.preference.Prefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ListenBrainz {

    const val API_BASE_URL = "https://api.listenbrainz.org/1/"

    private var _userToken: String = ""

    /** Whether the user has configured a ListenBrainz token */
    val userToken: String
        get() = _userToken

    /** Whether the user has configured a ListenBrainz token */
    val isAuthenticated: Boolean
        get() = _userToken.isNotEmpty()

    /** Set the user token after login */
    fun setUserToken(token: String) {
        _userToken = token
    }

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
                header(HttpHeaders.UserAgent, "Kizzy/1.0")
            }
            expectSuccess = false
        }
    }

    private val logger: Logger = LoggerFactory.getLogger("ListenBrainz")

    /** Maximum number of retry attempts for failed ListenBrainz API calls */
    private const val MAX_API_RETRIES = 3

    /**
     * Execute a ListenBrainz API call with retry logic and exponential backoff.
     * Returns null if all retries fail.
     */
    private suspend fun <T> apiCallWithRetry(call: suspend () -> T): T? {
        var delayMs = 1000L
        var lastError: String? = null
        for (attempt in 1..MAX_API_RETRIES) {
            val result = try {
                call()
            } catch (e: ListenBrainzException) {
                lastError = e.errorMessage
                if (e.code in 400..499) throw e
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message
                null
            }
            if (result != null) return result
            if (attempt < MAX_API_RETRIES) {
                logger.debug("ListenBrainz API call attempt $attempt failed, retrying in ${delayMs}ms")
                delay(delayMs.milliseconds)
                delayMs *= 2L
            }
        }
        logger.warn("ListenBrainz API call failed after $MAX_API_RETRIES attempts: $lastError")
        return null
    }

    /**
     * Initialize ListenBrainz with user token.
     * Called once during app initialization.
     */
    fun init(userToken: String) {
        _userToken = userToken
    }

    fun isInitialized(): Boolean = userToken.isNotEmpty()

    private suspend fun validateResponse(response: io.ktor.client.statement.HttpResponse) {
        if (response.status.value !in 200..299) {
            throw ListenBrainzException(response.status.value, response.bodyAsText())
        }
    }

    /**
     * Validate the ListenBrainz user token.
     */
    suspend fun validateToken(): Boolean = apiCallWithRetry {
        val response = client.get {
            url("validate-token")
            header(HttpHeaders.Authorization, "Token $userToken")
        }
        val responseCode = response.status.value
        val responseBody = response.bodyAsText()
        if (responseCode in 200..299) {
            val result = json.decodeFromString<ValidateTokenResponse>(responseBody)
            result.valid
        } else {
            throw ListenBrainzException(responseCode, responseBody)
        }
    } != null

    /**
     * Scrobble a track to ListenBrainz.
     */
    /**
     * Check whether [url] is a valid http(s) origin URL suitable for ListenBrainz origin_url.
     * Returns null when empty, blank, or not a web page URL.
     */
    private fun validOriginUrl(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return trimmed
    }

    /**
     * Scrobble a track to ListenBrainz.
     */
    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        duration: Int? = null,
        url: String? = null
    ): Boolean = apiCallWithRetry {
        if (userToken.isEmpty()) return@apiCallWithRetry false

        val originUrl = validOriginUrl(url)

        val payload = buildMap {
            put("listened_at", timestamp)
            put("track_metadata", buildMap {
                put("artist_name", artist)
                put("track_name", track)
                album?.let { put("release_name", it) }
                put("additional_info", buildMap {
                    duration?.let { put("duration_ms", it * 1000L) }
                    originUrl?.let { put("origin_url", it) }
                    put("submission_client", "Kizzy")
                    put("submission_client_version", "1.0")
                })
            })
        }

        val requestBody = buildMap {
            put("listen_type", "single")
            put("payload", listOf(payload))
        }

        val response = client.post {
            url("submit-listens")
            header(HttpHeaders.Authorization, "Token $userToken")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(requestBody))
        }
        validateResponse(response)
        true
    } != null

    /**
     * Update now playing status for a track on ListenBrainz.
     */
    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        duration: Int? = null,
        url: String? = null
    ): Boolean = apiCallWithRetry {
        if (userToken.isEmpty()) return@apiCallWithRetry false

        val originUrl = validOriginUrl(url)

        val payload = buildMap {
            put("track_metadata", buildMap {
                put("artist_name", artist)
                put("track_name", track)
                album?.let { put("release_name", it) }
                put("additional_info", buildMap {
                    duration?.let { put("duration_ms", it * 1000L) }
                    originUrl?.let { put("origin_url", it) }
                    put("submission_client", "Kizzy")
                    put("submission_client_version", "1.0")
                })
            })
        }

        val requestBody = buildMap {
            put("listen_type", "playing_now")
            put("payload", listOf(payload))
        }

        val response = client.post {
            url("submit-listens?return_msid=true")
            header(HttpHeaders.Authorization, "Token $userToken")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(requestBody))
        }
        validateResponse(response)
        true
    } != null
}

/**
 * Exception thrown when ListenBrainz API returns an error.
 */
class ListenBrainzException(val code: Int, val errorMessage: String) : Exception("ListenBrainz error $code: $errorMessage")

@Serializable
private data class ValidateTokenResponse(
    @SerialName("valid") val valid: Boolean
)
