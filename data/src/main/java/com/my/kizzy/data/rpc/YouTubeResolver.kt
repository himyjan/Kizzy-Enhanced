/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * YouTubeResolver.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *  
 *
 */

package com.my.kizzy.data.rpc

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves the real links of a YouTube track.
 *
 * The RPC used to link to a *search page* ("youtube.com/results?search_query=...")
 * and to a guessed artist channel ("youtube.com/@Artist Name"). Both are wrong:
 * the user has to search again instead of landing on the track, and the guessed
 * handle usually does not exist at all.
 *
 * YouTube publishes an oEmbed document for every public video. It contains the
 * title of the video, the thumbnail of the video and - the important part - the
 * URL of the channel of the uploader ("author_url"), which is the real
 * "@handle" of the artist. That is what the artist button links to.
 */
object YouTubeResolver {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The result of a successful oEmbed lookup.
     */
    data class Video(
        /** The canonical watch url of the video. */
        val watchUrl: String,
        /** The URL of the channel of the uploader, e.g. "https://www.youtube.com/@RickAstleyYT". */
        val channelUrl: String,
        /** The title of the video. */
        val title: String,
        /** The thumbnail of the video. */
        val thumbnailUrl: String,
    )

    /**
     * The oEmbed endpoint. It is a plain GET, no api key needed.
     */
    private const val OEMBED = "https://www.youtube.com/oembed"

    /**
     * The search page of YouTube. It is used to find the video of a track when
     * the player does not publish the url of the video.
     */
    private const val SEARCH = "https://www.youtube.com/results?search_query="

    /**
     * The pattern of a video id in the html of the search page.
     */
    private val VIDEO_ID_PATTERN = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /**
     * The user agent of the request for the search page. Without it YouTube
     * answers with a page that carries no video ids.
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * The client used for the lookup. It is a plain HTTP client, the oEmbed
     * endpoint does not need the api of the app.
     */
    private val client by lazy {
        HttpClient {
            expectSuccess = false
            followRedirects = true
        }
    }

    /**
     * The resolved videos, keyed by their video id.
     *
     * The presence is refreshed every second while a track plays, so the lookup
     * must not hit the network every time. A video id never changes its channel,
     * so the result can be kept for the whole session.
     */
    private val cache = ConcurrentHashMap<String, Video>()

    /**
     * The ids that could not be resolved (private or removed videos). They are
     * remembered as well, so a broken video is not looked up over and over.
     */
    private val failed = ConcurrentHashMap<String, Boolean>()

    /**
     * The video id of every YouTube url form, or null when the url is not a
     * YouTube video url.
     *
     * Handles "youtu.be/ID", "youtube.com/watch?v=ID", "music.youtube.com/watch?v=ID",
     * "youtube.com/shorts/ID", "youtube.com/live/ID" and "youtube.com/embed/ID".
     */
    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()

        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) return null

        // youtu.be/<id>
        if (lower.contains("youtu.be/")) {
            val id = trimmed.substringAfter("youtu.be/").substringBefore('?').substringBefore('&')
            if (id.isNotBlank() && id.length >= 11) return id.take(11)
        }

        // /watch?v=<id>
        if (lower.contains("/watch")) {
            val id = trimmed.substringAfter("v=").substringBefore('&').substringBefore('#')
            if (id.isNotBlank() && id.length >= 11) return id.take(11)
        }

        // /shorts/<id>, /live/<id>, /embed/<id>
        listOf("/shorts/", "/live/", "/embed/").forEach { marker ->
            if (lower.contains(marker)) {
                val id = trimmed.substringAfter(marker).substringBefore('?').substringBefore('/')
                if (id.isNotBlank() && id.length >= 11) return id.take(11)
            }
        }

        return null
    }

    /**
     * The canonical watch url of a video id.
     */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Looks the video up in the oEmbed endpoint.
     *
     * Returns null when the video is private, was removed or the network is not
     * available - the caller then falls back to a search page.
     */
    suspend fun lookup(url: String): Video? {
        val videoId = extractVideoId(url) ?: return null
        return lookupById(videoId)
    }

    /**
     * Finds the video of a track by its title.
     *
     * Players like YouTube Music do not publish the url of the video, so the
     * only way to reach the track is to look it up. The search page of YouTube
     * carries the ids of the videos in its html, and the oEmbed endpoint then
     * turns the id into the real watch url and the channel of the artist.
     *
     * @param title the title of the track
     * @param artist the artist of the track, used to make the search precise
     */
    suspend fun lookupByTitle(title: String, artist: String? = null): Video? {
        val key = "${title.trim()}::${artist?.trim().orEmpty()}"
        cache[key]?.let { return it }
        if (failed.containsKey(key)) return null

        return try {
            val query = if (artist.isNullOrBlank()) title else "$title $artist"
            val videoId = scrapeFirstVideoId(query) ?: run {
                failed[key] = true
                null
            }
            val video = videoId?.let { lookupById(it) }
            if (video != null) cache[key] = video else failed[key] = true
            video
        } catch (_: Exception) {
            failed[key] = true
            null
        }
    }

    /**
     * The id of the first video of the search page.
     */
    private suspend fun scrapeFirstVideoId(query: String): String? {
        val encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val body = client.get("$SEARCH$encoded") {
            headers.append("User-Agent", USER_AGENT)
            headers.append("Accept-Language", "en-US,en;q=0.9")
        }.bodyAsText()

        // The search page lists a few ids, the first one is the best match.
        return VIDEO_ID_PATTERN.find(body)
            ?.groupValues
            ?.getOrNull(1)
    }

    /**
     * Looks a video id up in the oEmbed endpoint.
     */
    suspend fun lookupById(videoId: String): Video? {
        cache[videoId]?.let { return it }
        if (failed.containsKey(videoId)) return null
        return try {
            val target = watchUrl(videoId)
            val response = client.get("$OEMBED?url=$target&format=json")
            if (response.status.value !in 200..299) return null
            val body = response.bodyAsText()
            val obj = json.parseToJsonElement(body).jsonObject

            val authorUrl = obj["author_url"]?.jsonPrimitive?.content
            val thumbnail = obj["thumbnail_url"]?.jsonPrimitive?.content
            val title = obj["title"]?.jsonPrimitive?.content

            Video(
                watchUrl = target,
                channelUrl = authorUrl?.let { normalizeChannelUrl(it) } ?: "",
                title = title.orEmpty(),
                thumbnailUrl = thumbnail.orEmpty(),
            ).also { cache[videoId] = it }
        } catch (_: Exception) {
            // The oEmbed endpoint is optional. A failure must never break the
            // presence, the caller simply falls back to a search page.
            failed[videoId] = true
            null
        }
    }

    /**
     * The channel url of a handle.
     *
     * The oEmbed endpoint answers with the handle of the uploader
     * ("https://www.youtube.com/@RickAstleyYT"). Older videos answer with the
     * old "/channel/UC..." form, which is kept as it is - it is a real url too.
     */
    private fun normalizeChannelUrl(raw: String): String {
        val decoded = try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            raw
        }
        var url = decoded.trimEnd('/')
        // oEmbed can answer with an invalid "channel/@handle" form (mixing the
        // old /channel/ prefix with the new @handle). Discord cannot open that,
        // so normalise it to the real handle url: https://www.youtube.com/@handle.
        url = url.replace("https://www.youtube.com/channel/@", "https://www.youtube.com/@")
        return url
    }

    /**
     * The search page of YouTube, used when no direct url is known.
     */
    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return "https://www.youtube.com/results?search_query=$encoded"
    }
}
