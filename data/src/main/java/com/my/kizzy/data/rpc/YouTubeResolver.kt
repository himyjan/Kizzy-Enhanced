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
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.util.Log
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

    data class Video(
        val watchUrl: String,
        val channelUrl: String,
        val title: String,
        val thumbnailUrl: String,
        /** Avatar URL of the uploader/channel, or empty when not found. */
        val avatarUrl: String = "",
        /** True when the uploader is a single channel (not a multi-artist track). */
        val isSingleChannel: Boolean = false,
    )

    private const val OEMBED = "https://www.youtube.com/oembed"
    private const val SEARCH = "https://www.youtube.com/results?search_query="

    // Matches "videoId":"ID" and escaped variants (\\"videoId\\":\\"ID\\") that
    // appear inside ytInitialData JSON string payloads.
    private val VIDEO_ID_PATTERN = Regex("\\\\?\"videoId\\\\?\":\\\\?\"([A-Za-z0-9_-]{11})\\\\?\"")

    // YouTube serves a stripped/consent page to the old mobile UA that contains
    // no "videoId" fields, so the title scrape always failed. A desktop UA returns
    // the full ytInitialData with real videoIds (verified 1.5 MB response).
    private val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = true
        }
    }

    private val cache = ConcurrentHashMap<String, Video>()
    private val failed = ConcurrentHashMap<String, Boolean>()

    fun isValidVideoId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val clean = id.trim()
        if (clean.contains("YtVideo", ignoreCase = true) || clean.contains("___")) return false
        return clean.length in 10..12 && clean.matches(Regex("^[A-Za-z0-9_-]{10,12}$"))
    }

    private val CHANNEL_ID_PATTERN = Regex("""(?:"channelId"\s*:\s*"|itemprop=["']channelId["']\s+content=["'])(UC[A-Za-z0-9_-]{22})""")
    // Uploader avatar + channel name from the watch page. The first "avatar" block
    // paired with an "a11yLabel":"Go to channel <name>" is the video's owner.
    // Multiple patterns to extract channel avatar from YouTube watch page HTML.
    // YouTube's HTML varies by region, client, and A/B test, so we try several.
    private val CHANNEL_AVATAR_PATTERNS = listOf(
        // Pattern 1: avatar object with a11yLabel "Go to channel"
        "\"avatar\"\\s*:\\s*\\{[\\s\\S]{0,500}?\"url\"\\s*:\\s*\"(https://yt3[^\"]+)\"[\\s\\S]{0,500}?\"a11yLabel\"\\s*:\\s*\"Go to channel",
        // Pattern 2: any avatarViewModel image source
        "\"avatarViewModel\"\\s*:\\s*\\{[\\s\\S]{0,300}?\"sources\"\\s*:\\s*\\[\\{[\\s\\S]{0,100}?\"url\"\\s*:\\s*\"(https://yt3[^\"]+)\"",
        // Pattern 3: any yt3.ggpht.com URL near "avatar" keyword
        "avatar[\\s\\S]{0,300}?(https://yt3[^\"]+)",
    )
    private val YMUSIC_HOST = Regex("(?i)^(?:https?://)?(?:www\\.)?ymusicapp\\.com")

    fun isYoutubeFamilyUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val candidate = url.trim().let {
            if (it.contains("://")) it else "https://$it"
        }
        val host = runCatching { java.net.URI(candidate).host?.lowercase() }.getOrNull()
            ?: return false
        return listOf("youtube.com", "youtu.be", "ymusicapp.com").any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun unwrapYmusicUrl(url: String): String {
        return url.replace(YMUSIC_HOST, "https://www.youtube.com")
    }

    /**
     * Converts YouTube/YMusic family URLs into their canonical RPC forms:
     * - Videos -> https://youtu.be/ID
     * - Channels -> https://www.youtube.com/channel/UC...
     * - Playlists -> https://www.youtube.com/playlist?list=PL...
     * Returns null if no valid video, channel, or playlist can be extracted.
     */
    fun toRpcUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        if (!isYoutubeFamilyUrl(trimmed)) return null

        val unwrapped = unwrapYmusicUrl(trimmed)

        extractVideoId(unwrapped)?.let { id ->
            if (isValidVideoId(id)) return "https://youtu.be/$id"
        }
        extractVideoId(trimmed)?.let { id ->
            if (isValidVideoId(id)) return "https://youtu.be/$id"
        }

        extractChannelUrl(unwrapped)?.let { return it }
        extractChannelUrl(trimmed)?.let { return it }

        extractPlaylistId(unwrapped)?.let { playlistId ->
            return "https://www.youtube.com/playlist?list=$playlistId"
        }
        extractPlaylistId(trimmed)?.let { playlistId ->
            return "https://www.youtube.com/playlist?list=$playlistId"
        }

        return null
    }

    /**
     * Keeps only real http(s) URLs. null, blank, or any other scheme is dropped
     * so it can never reach a Discord RPC field/button.
     */
    fun sanitizeHttpsUrl(url: String?): String? =
        url?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true) ||
                it.startsWith("http://", ignoreCase = true)
        }

    /**
     * Returns a DIRECT link for a URL, or null. YouTube-family URLs are
     * canonicalized via [toRpcUrl] to youtu.be/ID, a channel, or a playlist.
     * A YouTube search page (`results?search_query=` / `search?`) is NOT a
     * direct link, so it is rejected instead of leaking into an RPC field.
     * Non-YouTube http(s) links are kept as-is.
     */
    private fun directUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        // Bare ids are direct links too - support ALL forms:
        //   - video id (11 chars)       -> https://youtu.be/ID
        //   - channel id (UC + 22)      -> https://www.youtube.com/channel/UC...
        //   - handle (@id, explicit @)  -> https://www.youtube.com/@id
        if (isBareChannelId(trimmed)) return channelUrl(trimmed)
        if (isValidVideoId(trimmed)) return "https://youtu.be/$trimmed"
        // Only an explicit "@" prefix is treated as a handle. A bare word could
        // be an artist/song name, so it must never become a fake @handle link.
        if (trimmed.startsWith("@")) {
            val handle = trimmed.removePrefix("@")
            if (isValidHandle(handle)) return "https://www.youtube.com/@$handle"
        }
        val canonical = toRpcUrl(trimmed)?.let { sanitizeHttpsUrl(it) }
        if (canonical != null) return canonical
        val plain = sanitizeHttpsUrl(trimmed) ?: return null
        if (plain.contains("/results?", ignoreCase = true) ||
            plain.contains("/search?", ignoreCase = true)
        ) return null
        return plain
    }

    /** True for a raw YouTube channel id (UC followed by 22 url-safe chars). */
    private fun isBareChannelId(value: String): Boolean =
        value.length == 24 && value.startsWith("UC") &&
                value.substring(2).all { it.isLetterOrDigit() || it == '_' || it == '-' }

    /**
     * Primary media link priority chain (direct links only):
     *   1. mediaUrl (direct video/track URL -> youtu.be/ID)
     *   2. albumUrl (direct album URL)
     *   3. channelUrl (direct channel URL)
     */
    fun primaryMediaUrl(mediaUrl: String?, albumUrl: String?, channelUrl: String?): String? =
        directUrl(mediaUrl) ?: directUrl(albumUrl) ?: directUrl(channelUrl)

    /**
     * Large/cover image link priority (direct links only):
     *   1. album URL
     *   2. playlist URL
     *   3. video/media URL (youtu.be/ID)
     *
     * Search pages are never returned. This is the click target of the large
     * cover art, so for Morphe/AndDea it resolves to the direct video link.
     */
    fun largeImageUrl(
        mediaUrl: String?,
        albumUrl: String?,
        playlistUrl: String?,
    ): String? = directUrl(albumUrl) ?: directUrl(playlistUrl) ?: directUrl(mediaUrl)

    fun extractVideoId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        if (isValidVideoId(trimmed)) return trimmed

        val unwrapped = unwrapYmusicUrl(trimmed)
        val lower = unwrapped.lowercase()

        if (lower.contains("/channel/")) return null

        val vIndex = unwrapped.indexOf("v=")
        if (vIndex != -1) {
            val id = unwrapped.substring(vIndex + 2)
                .substringBefore('&')
                .substringBefore('#')
                .substringBefore('/')
                .trim()
            if (isValidVideoId(id)) return id
        }

        val shortIndex = unwrapped.indexOf("youtu.be/")
        if (shortIndex != -1) {
            val id = unwrapped.substring(shortIndex + 9)
                .substringBefore('?')
                .substringBefore('&')
                .substringBefore('#')
                .substringBefore('/')
                .trim()
            if (isValidVideoId(id)) return id
        }

        if (lower.contains("/playlist?") || lower.contains("/playlist/")) return null

        listOf("/shorts/", "/live/", "/embed/", "/watch/").forEach { marker ->
            val markerIndex = unwrapped.indexOf(marker, ignoreCase = true)
            if (markerIndex != -1) {
                val id = unwrapped.substring(markerIndex + marker.length)
                    .substringBefore('?')
                    .substringBefore('&')
                    .substringBefore('#')
                    .substringBefore('/')
                    .trim()
                if (isValidVideoId(id)) return id
            }
        }

        return null
    }

    fun extractChannelId(url: String): String? {
        val unwrapped = unwrapYmusicUrl(url)
        if (unwrapped.isEmpty()) return null
        val lower = unwrapped.lowercase()

        if (!isYoutubeFamilyUrl(unwrapped)) return null
        if (!lower.contains("/channel/")) return null

        val id = unwrapped.substringAfter("/channel/").substringBefore('?').substringBefore('&').substringBefore('#').trim()
        if (id.isNotBlank() && id.startsWith("UC") && id.length >= 22) return id
        return null
    }

    /**
     * Canonical channel url of a YouTube-family link.
     *
     * Supports every channel form YouTube hands out:
     * - legacy id:   youtube.com/channel/UC...
     * - handle:      youtube.com/@Rusui-channel
     * - custom:      youtube.com/c/Name
     * - legacy user: youtube.com/user/Name
     *
     * Returns null when the url does not point at a channel.
     */
    fun extractChannelUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val unwrapped = unwrapYmusicUrl(url)
        if (unwrapped.isEmpty()) return null
        if (!isYoutubeFamilyUrl(unwrapped)) return null
        val lower = unwrapped.lowercase()

        fun segmentAfter(marker: String): String =
            unwrapped.substringAfter(marker, "")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('&')
                .substringBefore('#')
                .trim()

        // Legacy id form: youtube.com/channel/UC...
        if (lower.contains("/channel/")) {
            val id = segmentAfter("/channel/")
            if (id.startsWith("@")) {
                val handle = id.removePrefix("@")
                if (isValidHandle(handle)) return "https://www.youtube.com/@$handle"
            }
            if (id.startsWith("UC") && id.length >= 22) {
                return "https://www.youtube.com/channel/$id"
            }
            return null
        }

        // Modern handle form: youtube.com/@handle
        val atIndex = lower.indexOf("/@")
        if (atIndex != -1) {
            val handle = unwrapped.substring(atIndex + 2)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('&')
                .substringBefore('#')
                .trim()
            if (isValidHandle(handle)) return "https://www.youtube.com/@$handle"
            return null
        }

        // Custom / legacy user forms: youtube.com/c/Name, youtube.com/user/Name
        listOf("/c/", "/user/").forEach { marker ->
            if (lower.contains(marker)) {
                val name = segmentAfter(marker)
                if (name.isNotBlank()) return "https://www.youtube.com${marker}$name"
            }
        }

        return null
    }

    /** YouTube handles are 3..30 chars of letters, digits, "_", "-" or ".". */
    private fun isValidHandle(handle: String): Boolean =
        handle.length in 3..30 && handle.matches(Regex("^[A-Za-z0-9._-]+$"))

    fun extractPlaylistId(url: String): String? {
        val unwrapped = unwrapYmusicUrl(url)
        if (unwrapped.isEmpty()) return null
        val lower = unwrapped.lowercase()

        if (!isYoutubeFamilyUrl(unwrapped)) return null
        if (!lower.contains("list=")) return null

        val id = unwrapped.substringAfter("list=").substringBefore('&').substringBefore('#').trim()
        if (id.isNotBlank() && id.length >= 10) return id
        return null
    }

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    suspend fun lookup(url: String): Video? {
        val videoId = extractVideoId(url) ?: return null
        return lookupById(videoId)
    }

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

    private suspend fun scrapeFirstVideoId(query: String): String? {
        val encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val body = client.get("$SEARCH$encoded") {
            headers.append("User-Agent", USER_AGENT)
            headers.append("Accept-Language", "en-US,en;q=0.9")
        }.bodyAsText()

        return VIDEO_ID_PATTERN.find(body)
            ?.groupValues
            ?.getOrNull(1)
    }

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
            val channelUrl = lookupChannelId(videoId)?.let(::channelUrl)
                ?: authorUrl?.let { extractChannelId(it)?.let(::channelUrl) ?: normalizeChannelUrl(it) }

            Video(
                watchUrl = target,
                channelUrl = channelUrl.orEmpty(),
                title = title.orEmpty(),
                thumbnailUrl = thumbnail.orEmpty(),
            ).also { cache[videoId] = it }
        } catch (_: Exception) {
            failed[videoId] = true
            null
        }
    }

    private suspend fun lookupChannelId(videoId: String): String? {
        return try {
            val body = client.get(watchUrl(videoId)) {
                headers.append("User-Agent", USER_AGENT)
            }.bodyAsText()
            CHANNEL_ID_PATTERN.find(body)?.groupValues?.getOrNull(1)
        } catch (_: Exception) {
            null
        }
    }

    private val avatarCache = ConcurrentHashMap<String, String>()

    /**
     * Returns the uploader's avatar URL (yt3.ggpht.com) for a video, or null.
     * Scrapes the watch page once and caches by video id. The avatar of the
     * video owner is the first "avatar" block paired with a
     * "Go to channel <name>" a11y label.
     */
    suspend fun lookupChannelAvatar(videoId: String): String? {
        avatarCache[videoId]?.let { return it.takeIf { s -> s.isNotEmpty() } }
        return try {
            val body = client.get(watchUrl(videoId)) {
                headers.append("User-Agent", USER_AGENT)
                headers.append("Accept-Language", "en-US,en;q=0.9")
            }.bodyAsText()
            Log.d("YouTubeResolver", "lookupChannelAvatar $videoId: watch page body length=${body.length}")
            // Try multiple patterns - YouTube's HTML varies by region/client
            for (patternStr in CHANNEL_AVATAR_PATTERNS) {
                val matchResult = patternStr.toRegex().find(body)
                val avatar = matchResult?.groupValues?.getOrNull(1)
                if (avatar != null) {
                    // Strip YouTube size parameters (e.g., =s88-c-k-c0x00ffffff-no-rj)
                    // so Discord's server can fetch the image reliably
                    val cleanAvatar = avatar.substringBefore('=')
                    Log.d("YouTubeResolver", "lookupChannelAvatar $videoId: pattern matched, raw=$avatar clean=$cleanAvatar")
                    avatarCache[videoId] = cleanAvatar
                    return cleanAvatar
                }
            }
            Log.d("YouTubeResolver", "lookupChannelAvatar $videoId: no pattern matched, body snippet=${body.take(200)}")
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun channelUrl(channelId: String): String =
        "https://www.youtube.com/channel/$channelId"

    private fun normalizeChannelUrl(raw: String): String {
        val decoded = try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            raw
        }
        var url = decoded.trimEnd('/')
        url = url.replace("https://www.youtube.com/channel/@", "https://www.youtube.com/@")
        return url
    }

    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return "https://www.youtube.com/results?search_query=$encoded"
    }
}