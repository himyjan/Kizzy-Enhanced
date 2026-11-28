package com.my.kizzy.data.get_current_data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.domain.interfaces.Logger
import jakarta.inject.Inject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves media metadata fields, cover art, collaborating artists, and canonical
 * track/album/playlist/artist/watch URLs from Android [MediaMetadata] across
 * streaming apps, YouTube clients, and web browsers.
 */
class MetadataResolver @Inject constructor(
    private val logger: Logger,
) {
    companion object {
        private const val METADATA_KEY_SOURCE_URL = "android.media.metadata.SOURCE_URL"
        private const val METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI"

        // Known non-string MediaMetadata keys (integers, longs, ratings, bitmaps,
        // dimensions) that must never be read via getString()/getCharSequence(),
        // preventing Android Bundle ClassCastException log spam.
        private val NON_STRING_METADATA_KEYS = setOf(
            MediaMetadata.METADATA_KEY_DURATION,
            MediaMetadata.METADATA_KEY_NUM_TRACKS,
            MediaMetadata.METADATA_KEY_TRACK_NUMBER,
            MediaMetadata.METADATA_KEY_DISC_NUMBER,
            MediaMetadata.METADATA_KEY_YEAR,
            MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
            MediaMetadata.METADATA_KEY_USER_RATING,
            MediaMetadata.METADATA_KEY_RATING,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
            "com.google.android.youtube.MEDIA_METADATA_VIDEO_HEIGHT_PX",
            "com.google.android.youtube.MEDIA_METADATA_VIDEO_WIDTH_PX",
        )
    }

    /**
     * Safely reads a string value for [key] from [metadata], skipping known
     * non-string keys and catching [ClassCastException] if a player stores an
     * unexpected non-CharSequence type under a custom key.
     */
    private fun safeGetString(metadata: MediaMetadata, key: String): String? {
        if (key in NON_STRING_METADATA_KEYS) return null
        return try {
            metadata.getString(key)
        } catch (_: ClassCastException) {
            null
        }
    }

    /**
     * Returns album or track cover bitmap, intentionally avoiding DISPLAY_ICON
     * since media apps frequently put their launcher logo there.
     */
    fun getCoverArt(metadata: MediaMetadata): Bitmap? {
        val albumArt = runCatching { metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) }.getOrNull()
        val art = runCatching { metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) }.getOrNull()
        return albumArt ?: art
    }

    /**
     * Checks standard artwork URI fields in priority order.
     */
    fun getArtworkUri(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
        for (key in keys) {
            val value = safeGetString(metadata, key)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    /**
     * Reads ARTIST first and falls back to AUTHOR if ARTIST is blank or absent.
     */
    fun getArtistOrAuthor(metadata: MediaMetadata): String? {
        return safeGetString(metadata, MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: safeGetString(metadata, MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotBlank() }
    }

    /**
     * Splits combined artist strings into distinct artist names.
     */
    fun getAllArtists(metadata: MediaMetadata): List<String> {
        val artist = getArtistOrAuthor(metadata) ?: return emptyList()
        return splitArtists(artist)
    }

    /**
     * Returns the first parsed artist name, or null if none is available.
     */
    fun getPrimaryArtist(metadata: MediaMetadata): String? {
        return getAllArtists(metadata).firstOrNull()
    }

    /**
     * Joins distinct artist names into a comma-separated string capped at [maxLength].
     */
    fun formatArtists(metadata: MediaMetadata, maxLength: Int = 128): String {
        val artists = getAllArtists(metadata)
        return when {
            artists.isEmpty() -> ""
            artists.size == 1 -> artists[0].take(maxLength)
            else -> artists.joinToString(", ").take(maxLength)
        }
    }

    /**
     * Splits common collaboration delimiters while preserving unique entries in order.
     */
    private fun splitArtists(artist: String): List<String> {
        val separators = listOf(" ft. ", " feat. ", " featuring ", " x ", " & ", ", ", "; ")
        var result = listOf(artist)
        for (sep in separators) {
            result = result.flatMap { it.split(sep, ignoreCase = true) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (result.size > 1) break
        }
        return result.distinct()
    }

    /**
     * Returns the non-blank ALBUM string if present.
     */
    fun getAlbum(metadata: MediaMetadata): String? {
        return safeGetString(metadata, MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
    }

    /**
     * Best-effort playlist name.
     *
     * Android's [MediaMetadata] has no standard playlist-title field, so this
     * only returns a value for players that publish a custom key (e.g.
     * "playlist_name" / "playlist_title"). URL-valued keys are ignored.
     */
    fun getPlaylistName(metadata: MediaMetadata): String? {
        val customKeys = listOf(
            "playlist_name", "playlist_title", "playlistName", "playlist",
            "android.media.metadata.PLAYLIST_TITLE", "android.media.metadata.PLAYLIST_NAME",
        )
        for (key in customKeys) {
            safeGetString(metadata, key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return runCatching {
            metadata.keySet().firstNotNullOfOrNull { key ->
                if (key in NON_STRING_METADATA_KEYS) return@firstNotNullOfOrNull null
                val lowerKey = key.lowercase()
                val looksLikeName = lowerKey == "playlist" ||
                        (lowerKey.contains("playlist") &&
                                (lowerKey.contains("name") || lowerKey.contains("title")))
                if (!looksLikeName) return@firstNotNullOfOrNull null
                val value = safeGetString(metadata, key)?.trim() ?: return@firstNotNullOfOrNull null
                value.takeIf {
                    it.isNotEmpty() &&
                            !it.startsWith("http://", ignoreCase = true) &&
                            !it.startsWith("https://", ignoreCase = true)
                }
            }
        }.getOrNull()
    }

    /**
     * Reads ALBUM_ARTIST or falls back to [getArtistOrAuthor].
     */
    fun getAlbumArtists(metadata: MediaMetadata): String? {
        return safeGetString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: getArtistOrAuthor(metadata)
    }

    /**
     * Detects channel layout or spatial audio format from custom metadata keys or album text.
     */
    fun getAudioChannels(metadata: MediaMetadata): String? {
        val channelKeys = listOf("audio_channels", "audio_channel_count", "channel_count", "surround", "audio_format")
        for (key in channelKeys) {
            val value = safeGetString(metadata, key)?.trim()
            if (value.isNullOrEmpty()) continue
            return when (value.lowercase()) {
                "5.1", "5.1ch", "51" -> "5.1 Surround"
                "7.1", "7.1ch", "71" -> "7.1 Surround"
                "2.0", "stereo" -> "Stereo"
                "atmos", "dolby atmos", "dolbyatmos" -> "Dolby Atmos"
                else -> value
            }
        }
        val album = safeGetString(metadata, MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val channelMatch = Regex("""(5\.1|7\.1|2\.0|stereo|surround|atmos)""", RegexOption.IGNORE_CASE).find(album)
        return channelMatch?.value?.replaceFirstChar { it.uppercase() }
    }

    /**
     * Resolves and normalizes the primary media URL for Discord Rich Presence.
     */
    fun getMediaUrl(
        metadata: MediaMetadata,
        packageName: String?,
        extraValues: List<String> = emptyList(),
    ): String? {
        return toRpcMediaUrl(resolveRawMediaUrl(metadata, packageName, extraValues))
    }

    /**
     * Normalizes YouTube-family URLs via [YouTubeResolver] while preserving
     * valid non-YouTube HTTP(S) URLs (excluding search result pages).
     */
    private fun toRpcMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        YouTubeResolver.toRpcUrl(url)?.let { canonicalUrl ->
            if (!canonicalUrl.contains("/results?search_query=")) {
                return canonicalUrl
            }
        }

        return url.takeIf {
            (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) &&
                    !it.contains("/results?search_query=")
        }
    }

    /**
     * Extracts a raw media or web-page URL from browser sessions, YouTube clients,
     * source/custom metadata fields, or fallback search URLs.
     */
    private fun resolveRawMediaUrl(
        metadata: MediaMetadata,
        packageName: String?,
        extraValues: List<String>,
    ): String? {
        if (MediaServiceResolver.isBrowser(packageName) || MediaServiceResolver.service(packageName) == null) {
            browserPageUrl(metadata, extraValues)?.let { return it }
        }
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { url ->
                normalizeMediaUrl(url, packageName)?.let { return it }
            }
        }
        val sourceUrl = firstMetadataUrl(metadata, extraValues)
        if (sourceUrl != null && sourceUrl.isValidMediaUrl()) {
            val normalized = normalizeMediaUrl(sourceUrl, packageName)
            if (normalized != null) return normalized
        }
        val customKeys = listOf(
            "youtube_url", "video_url", "source_url", "media_url", "content_url",
            "spotify_url", "apple_music_url", "deezer_url", "soundcloud_url",
        )
        for (key in customKeys) {
            val value = safeGetString(metadata, key)
            if (value != null && value.isValidMediaUrl()) {
                val normalized = normalizeMediaUrl(value, packageName)
                if (normalized != null) return normalized
            }
        }
        scanAllMetadataForUrl(metadata)?.let { url ->
            normalizeMediaUrl(url, packageName)?.let { return it }
        }
        extraValues.firstOrNull { it.isValidMediaUrl() }?.let { url ->
            normalizeMediaUrl(url, packageName)?.let { return it }
        }
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { url ->
                normalizeMediaUrl(url, packageName)?.let { return it }
            }
            if (MediaServiceResolver.isYouTubeWrapper(packageName)) {
                return null
            }
            val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)
            return MediaServiceResolver.mediaSearchUrl(packageName, title)
        }
        if (MediaServiceResolver.isBrowser(packageName)) {
            val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)
            return MediaServiceResolver.browserSearchUrl(title)
        }
        val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)
        return MediaServiceResolver.mediaSearchUrl(packageName, title)
    }

    /** Resolves a canonical track URL from sourceUrl, media URI/ID, or custom metadata keys. */
    fun getTrackUrl(metadata: MediaMetadata, packageName: String?): String? {
        val candidateKeys = listOf(
            METADATA_KEY_SOURCE_URL,
            METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
            "youtube_url", "video_url", "source_url", "media_url", "content_url",
            "spotify_url", "spotify_track_url", "apple_music_url", "apple_track_url",
            "deezer_url", "deezer_track_url", "soundcloud_url", "amazon_url",
            "amazon_track_url", "tidal_url", "tidal_track_url", "kkbox_url",
            "kkbox_track_url", "linemusic_url", "linemusic_track_url", "joox_url",
            "joox_track_url", "bilibili_url", "nicovideo_url", "streetvoice_url",
        )
        for (key in candidateKeys) {
            val value = safeGetString(metadata, key)?.trim() ?: continue
            if (value.isEmpty()) continue
            if (value.isValidMediaUrl() || value.startsWith("spotify:track:", ignoreCase = true)) {
                resolveTrackUrl(value, packageName)?.let { return it }
            }
        }
        scanAllMetadataForUrl(metadata)?.let { url ->
            resolveTrackUrl(url, packageName)?.let { return it }
        }
        return null
    }

    /** Resolves a canonical album URL from source/media URI/ID or custom metadata keys. */
    fun getAlbumUrl(metadata: MediaMetadata, packageName: String?): String? {
        val candidateKeys = listOf(
            METADATA_KEY_SOURCE_URL,
            METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
            "spotify_album_url", "apple_music_album_url", "deezer_album_url",
            "amazon_album_url", "tidal_album_url", "kkbox_album_url",
            "linemusic_album_url", "joox_album_url", "soundcloud_album_url",
            "bandcamp_album_url",
        )
        for (key in candidateKeys) {
            val value = safeGetString(metadata, key)?.trim() ?: continue
            if (value.isEmpty()) continue
            // Allow raw YouTube Music browse/album IDs (MPREb... / OLAK5uy_...) and spotify:album: URIs.
            val isRawAlbumId = value.startsWith("MPREb", ignoreCase = true) ||
                    value.startsWith("OLAK5uy", ignoreCase = true) ||
                    value.startsWith("spotify:album:", ignoreCase = true)
            if (value.isValidMediaUrl() || isRawAlbumId) {
                resolveAlbumUrl(value, packageName)?.let { return it }
            }
        }
        scanAllMetadataForUrl(metadata)?.let { url ->
            resolveAlbumUrl(url, packageName)?.let { return it }
        }
        return null
    }

    /** Resolves a canonical playlist URL from source/media URI/ID or custom metadata keys. */
    fun getPlaylistUrl(metadata: MediaMetadata, packageName: String?): String? {
        val candidateKeys = listOf(
            METADATA_KEY_SOURCE_URL,
            METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
            "spotify_playlist_url", "apple_music_playlist_url", "deezer_playlist_url",
            "amazon_playlist_url", "tidal_playlist_url", "kkbox_playlist_url",
            "linemusic_playlist_url", "joox_playlist_url", "soundcloud_playlist_url",
        )
        for (key in candidateKeys) {
            val value = safeGetString(metadata, key)?.trim() ?: continue
            if (value.isEmpty()) continue
            val isRawPlaylistId = value.startsWith("spotify:playlist:", ignoreCase = true) ||
                    value.startsWith("VLPL", ignoreCase = true) ||
                    value.startsWith("VLRDCLAK", ignoreCase = true)
            if (value.isValidMediaUrl() || isRawPlaylistId) {
                resolvePlaylistUrl(value, packageName)?.let { return it }
            }
        }
        scanAllMetadataForUrl(metadata)?.let { url ->
            resolvePlaylistUrl(url, packageName)?.let { return it }
        }
        return null
    }

    /**
     * Normalizes supported streaming platform track URLs into clean shareable web URLs.
     */
    private fun resolveTrackUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Spotify URI (spotify:track:{id})
        if (lower.startsWith("spotify:track:")) {
            val id = trimmed.substringAfter("spotify:track:").substringBefore('?').trim()
            if (id.isNotBlank()) return "https://open.spotify.com/track/$id"
        }

        // YouTube / YouTube Music / YMusic
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed)) {
            return YouTubeResolver.toRpcUrl(trimmed)
        }

        // Bilibili video: bilibili.com/video/{BV...|av...} or b23.tv/{code}
        if (lower.contains("bilibili.com") || lower.contains("b23.tv")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val videoIndex = segments.indexOf("video")
                if (videoIndex != -1 && segments.size > videoIndex + 1) {
                    return "https://www.bilibili.com/video/${segments[videoIndex + 1]}"
                }
                if (lower.contains("b23.tv") && segments.isNotEmpty()) {
                    return "https://b23.tv/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Niconico video: nicovideo.jp/watch/{id} or nico.ms/{id}
        if (lower.contains("nicovideo.jp") || lower.contains("nico.ms")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val watchIndex = segments.indexOf("watch")
                if (watchIndex != -1 && segments.size > watchIndex + 1) {
                    return "https://www.nicovideo.jp/watch/${segments[watchIndex + 1]}"
                }
                if (lower.contains("nico.ms") && segments.isNotEmpty()) {
                    return "https://www.nicovideo.jp/watch/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // StreetVoice track: streetvoice.com/{user}/songs/{id}
        if (lower.contains("streetvoice.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val songsIndex = segments.indexOf("songs")
                if (songsIndex >= 1 && segments.size > songsIndex + 1) {
                    return "https://streetvoice.com/${segments[songsIndex - 1]}/songs/${segments[songsIndex + 1]}/"
                }
            } catch (_: Exception) {}
        }

        // Spotify track (handles optional /intl-xx/ locale prefix)
        if (lower.contains("spotify.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://open.spotify.com/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music track: music.apple.com/{country}/album/{slug}/{album-id}?i={track-id}
        if (lower.contains("music.apple.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val query = uri.query ?: ""
                val trackIdParam = query.split("&").find { it.startsWith("i=") }?.substringAfter("i=")
                val albumIndex = segments.indexOf("album")
                if (!trackIdParam.isNullOrBlank() && albumIndex != -1 && segments.size > albumIndex + 2) {
                    val storefront = if (albumIndex >= 1) segments[0] else "us"
                    val albumSlug = segments[albumIndex + 1]
                    val albumId = segments[albumIndex + 2]
                    return "https://music.apple.com/$storefront/album/$albumSlug/$albumId?i=$trackIdParam"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music track
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer track
        if (lower.contains("deezer.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://www.deezer.com/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL track
        if (lower.contains("tidal.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://tidal.com/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX track
        if (lower.contains("kkbox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val prefix = if (songIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/song/${segments[songIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC track
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/track/${segments[trackIndex + 1]}"
                    else "https://music.line.me/webapp/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX track
        if (lower.contains("joox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val singleIndex = segments.indexOf("single")
                if (singleIndex != -1 && segments.size > singleIndex + 1) {
                    return "https://www.joox.com/single/${segments[singleIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music track
        if (lower.contains("kugou.com")) {
            try {
                val uri = URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.endsWith(".html")) {
                    val id = lastSegment.substringBefore(".html")
                    if (id.isNotBlank()) return "https://www.kugou.com/${id}.html"
                }
                if (lastSegment.length == 32 && lastSegment.matches(Regex("[0-9a-fA-F]{32}"))) {
                    return "https://www.kugou.com/${lastSegment}.html"
                }
            } catch (_: Exception) {}
        }

        // QQ Music track
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val mid = segments[songIndex + 1].substringBefore(".html")
                    if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/song/${mid}.html"
                }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.length >= 10 && lastSegment.matches(Regex("[0-9A-Za-z]{10,}"))) {
                    return "https://y.qq.com/n/yqq/song/${lastSegment}.html"
                }
            } catch (_: Exception) {}
        }

        // Qishui Music
        if (lower.contains("music.douyin.com") || lower.contains("douyin.com/qishui")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val trackIdParam = query.split("&").find { it.startsWith("track_id=") }?.substringAfter("track_id=")
                if (!trackIdParam.isNullOrBlank()) {
                    return "https://music.douyin.com/qishui/track?track_id=$trackIdParam"
                }
                val albumIdParam = query.split("&").find { it.startsWith("album_id=") }?.substringAfter("album_id=")
                if (!albumIdParam.isNullOrBlank()) {
                    return "https://music.douyin.com/qishui/album?album_id=$albumIdParam"
                }
                val artistIdParam = query.split("&").find { it.startsWith("artist_id=") }?.substringAfter("artist_id=")
                if (!artistIdParam.isNullOrBlank()) {
                    return "https://music.douyin.com/qishui/artist?artist_id=$artistIdParam"
                }
                val playlistIdParam = query.split("&").find { it.startsWith("playlist_id=") }?.substringAfter("playlist_id=")
                if (!playlistIdParam.isNullOrBlank()) {
                    return "https://music.douyin.com/qishui/playlist?playlist_id=$playlistIdParam"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music track
        if (lower.contains("douyin.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val code = segments.lastOrNull() ?: ""
                if (code.isNotBlank()) return "https://www.douyin.com/$code/"
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music track
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val songIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (!songIdParam.isNullOrBlank()) {
                    return "https://music.163.com/song?id=$songIdParam"
                }
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/song?id=$lastSegment"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music track
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playIndex = segments.indexOf("play_detail")
                if (playIndex != -1 && segments.size > playIndex + 1) {
                    return "https://kuwo.cn/play_detail/${segments[playIndex + 1]}"
                }
                val yinyueIndex = segments.indexOf("yinyue")
                if (yinyueIndex != -1 && segments.size > yinyueIndex + 1) {
                    return "https://m.kuwo.cn/yinyue/${segments[yinyueIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JioSaavn track
        if (lower.contains("jiosaavn.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val token = segments.lastOrNull() ?: ""
                if (token.isNotBlank()) return "https://www.jiosaavn.com/$token"
            } catch (_: Exception) {}
        }

        // Gaana track
        if (lower.contains("gaana.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val slug = segments.lastOrNull() ?: ""
                if (slug.isNotBlank()) return "https://gaana.com/$slug"
            } catch (_: Exception) {}
        }

        // Wynk Music track
        if (lower.contains("wynk.in")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 2) {
                    return "https://wynk.in/music/song/${segments[songIndex + 1]}/${segments[songIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Boomplay track
        if (lower.contains("boomplay.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://www.boomplay.com/$id"
            } catch (_: Exception) {}
        }

        // Audiomack track
        if (lower.contains("audiomack.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val artistSlug = if (songIndex > 0) segments[songIndex - 1] else ""
                    if (artistSlug.isNotBlank()) return "https://www.audiomack.com/$artistSlug/song/${segments[songIndex + 1]}"
                    return "https://www.audiomack.com/song/${segments[songIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Yandex Music track
        if (lower.contains("music.yandex.ru") || lower.contains("music.yandex.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    val albumId = if (trackIndex > 0) segments[trackIndex - 1] else ""
                    return "https://music.yandex.ru/album/$albumId/track/${segments[trackIndex + 1]}"
                }
                val artistIndex = segments.indexOf("artist")
                if (artistIndex != -1 && segments.size > artistIndex + 1) {
                    return "https://music.yandex.ru/artist/${segments[artistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // VK Music
        if (lower.contains("vk.com") || lower.contains("vk.me")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                if (query.contains("audio")) return trimmed
            } catch (_: Exception) {}
        }

        // Anghami track
        if (lower.contains("anghami.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://anghami.com/$id"
            } catch (_: Exception) {}
        }

        // AWA track
        if (lower.contains("awa.fm")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://s.awa.fm/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Recochoku track
        if (lower.contains("recochoku.jp")) return trimmed

        // Melon track
        if (lower.contains("melon.com")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val songIdParam = query.split("&").find { it.startsWith("songId=") }?.substringAfter("songId=")
                if (!songIdParam.isNullOrBlank()) {
                    return "https://www.melon.com/song/detail.htm?songId=$songIdParam"
                }
            } catch (_: Exception) {}
        }

        // Genie Music track
        if (lower.contains("genie.co.kr")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val songIdParam = query.split("&").find { it.startsWith("xgnm=") }?.substringAfter("xgnm=")
                if (!songIdParam.isNullOrBlank()) {
                    return "https://www.genie.co.kr/detail/songInfo?xgnm=$songIdParam"
                }
            } catch (_: Exception) {}
        }

        // Bugs! track
        if (lower.contains("bugs.co.kr")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://music.bugs.co.kr/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Beatport track
        if (lower.contains("beatport.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://www.beatport.com/$id"
            } catch (_: Exception) {}
        }

        // SoundCloud track
        if (lower.contains("soundcloud.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2) {
                    return "https://soundcloud.com/${segments[0]}/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // Bandcamp track
        if (lower.contains("bandcamp.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Normalizes supported streaming platform album URLs (and raw browse/album IDs)
     * into canonical web URLs.
     */
    private fun resolveAlbumUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Raw YouTube Music browse ID or album playlist ID
        if (trimmed.startsWith("MPREb", ignoreCase = true)) {
            val id = trimmed.substringBefore('?').substringBefore('&').substringBefore('/')
            if (id.isNotBlank()) return "https://music.youtube.com/browse/$id"
        }
        if (trimmed.startsWith("OLAK5uy", ignoreCase = true)) {
            val id = trimmed.substringBefore('?').substringBefore('&').substringBefore('/')
            if (id.isNotBlank()) return "https://music.youtube.com/playlist?list=$id"
        }

        // Spotify URI (spotify:album:{id})
        if (lower.startsWith("spotify:album:")) {
            val id = trimmed.substringAfter("spotify:album:").substringBefore('?').trim()
            if (id.isNotBlank()) return "https://open.spotify.com/album/$id"
        }

        // Spotify album (handles optional /intl-xx/ locale prefix)
        if (lower.contains("spotify.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://open.spotify.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music album
        if (lower.contains("music.apple.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 2) {
                    val storefront = if (albumIndex >= 1) segments[0] else "us"
                    return "https://music.apple.com/$storefront/album/${segments[albumIndex + 1]}/${segments[albumIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music album
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer album
        if (lower.contains("deezer.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.deezer.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL album
        if (lower.contains("tidal.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://tidal.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX album
        if (lower.contains("kkbox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val prefix = if (albumIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC album
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/album/${segments[albumIndex + 1]}"
                    else "https://music.line.me/webapp/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX album
        if (lower.contains("joox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.joox.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music album
        if (lower.contains("kugou.com")) {
            try {
                val uri = URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.kugou.com/album/${segments[albumIndex + 1]}/"
                }
                if (path.endsWith("/") && segments.isNotEmpty()) {
                    val last = segments.last()
                    if (last.isNotBlank()) return "https://www.kugou.com/$last/"
                }
            } catch (_: Exception) {}
        }

        // QQ Music album
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val mid = segments[albumIndex + 1].substringBefore(".html")
                    if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/album/${mid}.html"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music album
        if (lower.contains("douyin.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val code = segments.lastOrNull() ?: ""
                if (code.isNotBlank()) return "https://www.douyin.com/$code/"
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music album
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (!albumIdParam.isNullOrBlank()) {
                    return "https://music.163.com/album?id=$albumIdParam"
                }
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/album?id=$lastSegment"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music album
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album_detail")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://kuwo.cn/album_detail/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JioSaavn album
        if (lower.contains("jiosaavn.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val token = segments.lastOrNull() ?: ""
                if (token.isNotBlank()) return "https://www.jiosaavn.com/$token"
            } catch (_: Exception) {}
        }

        // Gaana album
        if (lower.contains("gaana.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val slug = segments.lastOrNull() ?: ""
                if (slug.isNotBlank()) return "https://gaana.com/$slug"
            } catch (_: Exception) {}
        }

        // Wynk Music album
        if (lower.contains("wynk.in")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 2) {
                    return "https://wynk.in/music/album/${segments[albumIndex + 1]}/${segments[albumIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Boomplay album
        if (lower.contains("boomplay.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://www.boomplay.com/$id"
            } catch (_: Exception) {}
        }

        // Audiomack album
        if (lower.contains("audiomack.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val artistSlug = if (albumIndex > 0) segments[albumIndex - 1] else ""
                    if (artistSlug.isNotBlank()) return "https://www.audiomack.com/$artistSlug/album/${segments[albumIndex + 1]}"
                    return "https://www.audiomack.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Yandex Music album
        if (lower.contains("music.yandex.ru") || lower.contains("music.yandex.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://music.yandex.ru/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // VK Music
        if (lower.contains("vk.com") || lower.contains("vk.me")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                if (query.contains("audio")) return trimmed
            } catch (_: Exception) {}
        }

        // Anghami album
        if (lower.contains("anghami.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://anghami.com/$id"
            } catch (_: Exception) {}
        }

        // AWA album
        if (lower.contains("awa.fm")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://s.awa.fm/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Recochoku album
        if (lower.contains("recochoku.jp")) return trimmed

        // Melon album
        if (lower.contains("melon.com")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("albumId=") }?.substringAfter("albumId=")
                if (!albumIdParam.isNullOrBlank()) {
                    return "https://www.melon.com/album/detail.htm?albumId=$albumIdParam"
                }
            } catch (_: Exception) {}
        }

        // Genie Music album
        if (lower.contains("genie.co.kr")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("axnm=") }?.substringAfter("axnm=")
                if (!albumIdParam.isNullOrBlank()) {
                    return "https://www.genie.co.kr/detail/albumInfo?axnm=$albumIdParam"
                }
            } catch (_: Exception) {}
        }

        // Bugs! album
        if (lower.contains("bugs.co.kr")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://music.bugs.co.kr/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Beatport album
        if (lower.contains("beatport.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val id = segments.lastOrNull() ?: ""
                if (id.isNotBlank()) return "https://www.beatport.com/$id"
            } catch (_: Exception) {}
        }

        // Bandcamp album
        if (lower.contains("bandcamp.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        // YouTube Music browse or OLAK5uy_ album playlist URLs
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            val browseIndex = lower.indexOf("/browse/")
            if (browseIndex != -1) {
                val id = trimmed.substring(browseIndex + "/browse/".length)
                    .substringBefore('?')
                    .substringBefore('&')
                    .substringBefore('#')
                    .substringBefore('/')
                    .trim()
                if (id.isNotBlank()) return "https://music.youtube.com/browse/$id"
            }
            val listId = YouTubeResolver.extractPlaylistId(trimmed)
            if (listId != null && listId.startsWith("OLAK5uy")) {
                return "https://music.youtube.com/playlist?list=$listId"
            }
        }

        return null
    }

    /**
     * Normalizes supported streaming platform playlist URLs (and raw playlist IDs)
     * into canonical web URLs.
     */
    private fun resolvePlaylistUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Raw YouTube Music VL-prefixed playlist ID
        if (trimmed.startsWith("VLPL", ignoreCase = true) || trimmed.startsWith("VLRDCLAK", ignoreCase = true)) {
            val listId = trimmed.removePrefix("VL").removePrefix("vl")
                .substringBefore('?').substringBefore('&').substringBefore('/')
            if (listId.isNotBlank()) return "https://music.youtube.com/playlist?list=$listId"
        }

        // Spotify URI (spotify:playlist:{id})
        if (lower.startsWith("spotify:playlist:")) {
            val id = trimmed.substringAfter("spotify:playlist:").substringBefore('?').trim()
            if (id.isNotBlank()) return "https://open.spotify.com/playlist/$id"
        }

        // Spotify playlist (handles optional /intl-xx/ locale prefix)
        if (lower.contains("spotify.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://open.spotify.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music playlist
        if (lower.contains("music.apple.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 2) {
                    val storefront = if (playlistIndex >= 1) segments[0] else "us"
                    return "https://music.apple.com/$storefront/playlist/${segments[playlistIndex + 1]}/${segments[playlistIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music playlist
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer playlist
        if (lower.contains("deezer.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://www.deezer.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL playlist
        if (lower.contains("tidal.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://tidal.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX playlist
        if (lower.contains("kkbox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val prefix = if (playlistIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC playlist
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/playlist/${segments[playlistIndex + 1]}"
                    else "https://music.line.me/webapp/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX playlist
        if (lower.contains("joox.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://www.joox.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music playlist
        if (lower.contains("kugou.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.endsWith(".html")) {
                    val id = lastSegment.substringBefore(".html")
                    if (id.isNotBlank()) return "https://www.kugou.com/${id}.html"
                }
            } catch (_: Exception) {}
        }

        // QQ Music playlist
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val id = segments[playlistIndex + 1].substringBefore(".html")
                    if (id.isNotBlank()) return "https://y.qq.com/n/yqq/playsquare/${id}.html"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music playlist
        if (lower.contains("douyin.com")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val code = segments.lastOrNull() ?: ""
                if (code.isNotBlank()) return "https://www.douyin.com/$code/"
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music playlist
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = URI(trimmed)
                val query = uri.query ?: ""
                val playlistIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (!playlistIdParam.isNullOrBlank()) {
                    return "https://music.163.com/#/playlist?id=$playlistIdParam"
                }
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/#/playlist?id=$lastSegment"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music playlist
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist_detail")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://kuwo.cn/playlist_detail/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Bugs! playlist/album list
        if (lower.contains("bugs.co.kr")) {
            try {
                val uri = URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://music.bugs.co.kr/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // YouTube / YouTube Music playlist URLs
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed)) {
            val playlistId = YouTubeResolver.extractPlaylistId(trimmed)
            if (playlistId != null) {
                val host = if (lower.contains("music.youtube.com")) "music.youtube.com" else "www.youtube.com"
                return "https://$host/playlist?list=$playlistId"
            }
        }

        return null
    }

    /**
     * Scans string metadata keys (ordered by ID/URL relevance) for a valid YouTube
     * video ID without making network requests, skipping non-string keys safely.
     */
    private fun findVideoIdInMetadata(metadata: MediaMetadata): String? {
        val orderedKeys = metadata.keySet().sortedByDescending { key ->
            val k = key.lowercase()
            when {
                ("video" in k && "id" in k) -> 3
                ("media" in k && "id" in k) -> 2
                k == MediaMetadata.METADATA_KEY_MEDIA_ID.lowercase() -> 2
                ("url" in k || "uri" in k || "link" in k) -> 1
                else -> 0
            }
        }
        for (key in orderedKeys) {
            if (key in NON_STRING_METADATA_KEYS) continue
            val value = safeGetString(metadata, key)?.trim() ?: continue
            if (value.isEmpty()) continue
            val id = YouTubeResolver.extractVideoId(value) ?: continue
            if (YouTubeResolver.isValidVideoId(id)) return id
        }
        return null
    }

    /**
     * Resolves a direct YouTube video, channel, or playlist URL locally or via
     * [resolveYouTubeVideo] when playing from a YouTube-family package.
     */
    suspend fun getDirectMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null
        findVideoIdInMetadata(metadata)?.let { return "https://youtu.be/$it" }
        firstMetadataUrl(metadata)?.let { url ->
            if (YouTubeResolver.isYoutubeFamilyUrl(url)) {
                toRpcMediaUrl(url)?.let { return it }
            }
        }
        val mediaId = safeGetString(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            if (YouTubeResolver.isYoutubeFamilyUrl(mediaId)) {
                toRpcMediaUrl(mediaId)?.let { return it }
            }
            YouTubeResolver.extractVideoId(mediaId)?.let { return "https://youtu.be/$it" }
            YouTubeResolver.extractChannelUrl(mediaId)?.let { return it }
            val playlistId = YouTubeResolver.extractPlaylistId(mediaId)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
            val id = mediaId.substringAfterLast(":").substringAfterLast("/")
                .substringBefore('?').substringBefore('&')
                .takeIf { it.isNotBlank() && it.length >= 11 }?.take(11)
            if (id != null && YouTubeResolver.isValidVideoId(id)) return "https://youtu.be/$id"
            val rawId = mediaId.trim()
            if (YouTubeResolver.isValidVideoId(rawId)) {
                return "https://youtu.be/$rawId"
            }
        }
        val video = resolveYouTubeVideo(metadata) ?: return null
        return toRpcMediaUrl(video.watchUrl)
    }

    /**
     * Looks up a [YouTubeResolver.Video] from an explicit YouTube URL in metadata
     * or by searching track title and artist.
     */
    suspend fun resolveYouTubeVideo(metadata: MediaMetadata): YouTubeResolver.Video? {
        val sourceUrl = firstMetadataUrl(metadata)
        if (!sourceUrl.isNullOrEmpty() && YouTubeResolver.isYoutubeFamilyUrl(sourceUrl)) {
            YouTubeResolver.lookup(YouTubeResolver.unwrapYmusicUrl(sourceUrl))?.let { return it }
        }
        val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)?.trim()
        if (title.isNullOrEmpty()) return null
        val artist = MetadataResolverArtistName(metadata)
        return YouTubeResolver.lookupByTitle(title, artist)
    }

    /**
     * Resolves the uploader's channel avatar URL for YouTube sessions.
     */
    suspend fun getChannelAvatarUrl(
        metadata: MediaMetadata,
        packageName: String?,
    ): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null
        val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)?.trim() ?: "unknown"
        val artist = safeGetString(metadata, MediaMetadata.METADATA_KEY_ARTIST)?.trim() ?: "unknown"
        val video = resolveYouTubeVideo(metadata)
        if (video == null) {
            logger.d("MediaImage", "Channel avatar: resolveYouTubeVideo returned null for '$title' by $artist")
            return null
        }
        val id = YouTubeResolver.extractVideoId(video.watchUrl)
        if (id == null) {
            logger.d("MediaImage", "Channel avatar: extractVideoId returned null for watchUrl=${video.watchUrl}")
            return null
        }
        logger.d("MediaImage", "Channel avatar: videoId=$id watchUrl=${video.watchUrl} title=$title artist=$artist")
        val avatar = YouTubeResolver.lookupChannelAvatar(id)
        logger.d("MediaImage", "Channel avatar for $id: ${avatar ?: "null - no avatar found in watch page HTML"}")
        return avatar
    }

    /**
     * Extracts a supported web player URL from standard and custom string fields
     * on a browser or generic [MediaMetadata] bundle without casting non-string keys.
     */
    private fun browserPageUrl(metadata: MediaMetadata, extraValues: List<String> = emptyList()): String? {
        val mediaHosts = listOf(
            "music.apple.com", "itunes.apple.com", "open.spotify.com", "play.spotify.com",
            "youtube.com", "youtu.be", "ymusicapp.com",
            "music.youtube.com", "deezer.com", "soundcloud.com",
            "music.amazon.com", "music.amazon.co.jp", "amazon.com/music",
            "tidal.com", "bandcamp.com", "qobuz.com", "play.qobuz.com",
            "nicovideo.jp", "sp.nicovideo.jp", "bilibili.com", "m.bilibili.com",
            "twitch.tv", "netflix.com", "disneyplus.com",
            "music.163.com", "y.qq.com", "streetvoice.com",
            "kkbox.com", "music.line.me", "music-tw.line.me",
            "bugs.co.kr", "melon.com", "genie.co.kr", "flo-music.co.kr",
            "vibe.naver.com", "archive.org",
        )
        val skipHosts = listOf(
            "blob:", "data:", "googlevideo.com", "mzstatic.com", "googleusercontent.com",
            "ytimg.com", "ggpht.com", "akamaized.net", "hdslb.com",
        )
        val candidates = mutableListOf<String>()
        extraValues.forEach { candidates.add(it) }
        listOf(
            METADATA_KEY_MEDIA_URI,
            METADATA_KEY_SOURCE_URL,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
            MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
            MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_ALBUM,
            MediaMetadata.METADATA_KEY_GENRE,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
        ).forEach { key ->
            safeGetString(metadata, key)?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        }
        metadata.keySet().forEach { key ->
            safeGetString(metadata, key)
                ?.takeIf { it.isNotBlank() }
                ?.let { candidates.add(it) }
        }
        for (candidate in candidates) {
            val url = Regex("https?://[^\\s\"<>]+").find(candidate)?.value
                ?: candidate.takeIf { it.startsWith("http", ignoreCase = true) }
                ?: continue
            val cleaned = url.trim().trimEnd(')', ',', ']', '"', '\'')
            if (skipHosts.any { host -> cleaned.contains(host, ignoreCase = true) }) continue
            if (mediaHosts.any { host -> cleaned.contains(host, ignoreCase = true) }) {
                return toRpcMediaUrl(cleaned)
            }
        }
        return null
    }

    /**
     * Returns the first valid HTTP(S) media URL from [extraValues], standard URI/ID keys,
     * or fallback text metadata fields.
     */
    private fun firstMetadataUrl(
        metadata: MediaMetadata,
        extraValues: List<String> = emptyList(),
    ): String? {
        extraValues.firstOrNull { it.isValidMediaUrl() }?.let { return it }
        listOf(
            METADATA_KEY_MEDIA_URI,
            METADATA_KEY_SOURCE_URL,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
        ).forEach { key ->
            safeGetString(metadata, key)?.takeIf { it.isValidMediaUrl() }?.let { return it }
        }
        return scanAllMetadataForUrl(metadata)
    }

    /**
     * Scans standard text fields for a valid HTTP(S) URL.
     */
    private fun scanAllMetadataForUrl(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR, MediaMetadata.METADATA_KEY_GENRE,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
        )
        for (key in keys) {
            val value = safeGetString(metadata, key)
            if (!value.isNullOrEmpty() && value.isValidMediaUrl()) return value
        }
        return null
    }

    /**
     * Normalizes browse and YouTube-family URLs into RPC-compatible links.
     */
    private fun normalizeMediaUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        if (trimmed.contains("/browse/")) return toRpcMediaUrl(trimmed)
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed) || MediaServiceResolver.isYouTube(packageName)) {
            return YouTubeResolver.toRpcUrl(trimmed)
        }
        return trimmed
    }

    /**
     * Extracts a YouTube video, channel, playlist, or search URL from [metadata].
     */
    private fun extractYouTubeUrl(metadata: MediaMetadata): String? {
        val title = safeGetString(metadata, MediaMetadata.METADATA_KEY_TITLE)
        findVideoIdInMetadata(metadata)?.let { return "https://youtu.be/$it" }
        fun fromRaw(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            if (raw.contains("/results?search_query")) return null
            if (YouTubeResolver.isYoutubeFamilyUrl(raw)) return YouTubeResolver.toRpcUrl(raw)
            YouTubeResolver.extractVideoId(raw)?.let { return "https://youtu.be/$it" }
            return null
        }
        val mediaId = safeGetString(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID)
        fromRaw(mediaId)?.let { return it }
        fromRaw(firstMetadataUrl(metadata))?.let { return it }
        if (!mediaId.isNullOrEmpty()) {
            val channelId = YouTubeResolver.extractChannelId(mediaId)
            if (channelId != null) return "https://www.youtube.com/channel/$channelId"
            val playlistId = YouTubeResolver.extractPlaylistId(mediaId)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
            val id = mediaId.substringAfterLast(":").substringAfterLast("/").substringBefore('?').substringBefore('&')
            if (YouTubeResolver.isValidVideoId(id)) return "https://youtu.be/$id"
            val rawId = mediaId.trim()
            if (YouTubeResolver.isValidVideoId(rawId)) return "https://youtu.be/$rawId"
        }
        val youtubeKeys = listOf(
            "youtube_url", "video_url", "source_url", "media_url", "content_url",
            "yt_url", "youtube_video_url", "youtube_watch_url",
        )
        for (key in youtubeKeys) {
            fromRaw(safeGetString(metadata, key))?.let { return it }
        }
        if (!title.isNullOrEmpty()) {
            return "https://www.youtube.com/results?search_query=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
        }
        return null
    }

    /**
     * Helper alias returning the primary artist or author string.
     */
    private fun MetadataResolverArtistName(metadata: MediaMetadata): String? {
        return getArtistOrAuthor(metadata)
    }

    /**
     * Resolves a platform-specific artist/channel URL or falls back to a search URL.
     */
    suspend fun getArtistUrl(metadata: MediaMetadata, packageName: String?): String? {
        val artist = MetadataResolverArtistName(metadata)
        if (artist.isNullOrEmpty()) return null
        val artists = getAllArtists(metadata)
        val rawArtistString = safeGetString(metadata, MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: safeGetString(metadata, MediaMetadata.METADATA_KEY_AUTHOR)
                ?.takeIf { it.isNotBlank() }
            ?: safeGetString(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val parsedCollaborators = parseCollaboratingArtists(rawArtistString)
        val isMultiArtist = artists.size > 1 || parsedCollaborators.size > 1
        val pkg = packageName?.lowercase().orEmpty()
        if (MediaServiceResolver.isYouTube(packageName) && isMultiArtist) {
            val combinedQuery = parsedCollaborators.joinToString(" ").ifBlank { artist }
            return MediaServiceResolver.artistUrl(packageName, combinedQuery)
        }
        val sourceUrl = safeGetString(metadata, METADATA_KEY_SOURCE_URL)?.takeIf { it.isNotBlank() }
            ?: safeGetString(metadata, METADATA_KEY_MEDIA_URI)?.takeIf { it.isNotBlank() }
        val parsedArtistUrl = when {
            "soundcloud" in pkg -> soundcloudArtistUrl(sourceUrl)
            "spotify" in pkg -> spotifyArtistUrl(sourceUrl)
            "bandcamp" in pkg -> bandcampArtistUrl(sourceUrl)
            "youtube" in pkg || "ymusic" in pkg || "simpmusic" in pkg ||
                    "vimusic" in pkg || "innertune" in pkg -> youtubeArtistUrl(sourceUrl)
            "spotube" in pkg -> if (sourceUrl?.contains("spotify.com") == true) {
                spotifyArtistUrl(sourceUrl)
            } else {
                youtubeArtistUrl(sourceUrl)
            }
            "deezer" in pkg -> deezerArtistUrl(sourceUrl)
            "apple" in pkg -> appleMusicArtistUrl(sourceUrl)
            "amazon" in pkg -> amazonMusicArtistUrl(sourceUrl)
            "streetvoice" in pkg -> streetVoiceArtistUrl(sourceUrl)
            "kkbox" in pkg -> kkboxArtistUrl(sourceUrl)
            "nicobox" in pkg || "nicovideo" in pkg -> nicoBoxArtistUrl(sourceUrl)
            "bilibili" in pkg -> bilibiliArtistUrl(sourceUrl)
            "linemusic" in pkg || "linecorp.linemusic" in pkg -> lineMusicArtistUrl(sourceUrl)
            "tidal" in pkg -> tidalArtistUrl(sourceUrl)
            "joox" in pkg -> jooxArtistUrl(sourceUrl)
            "kugou" in pkg || "kugoumusic" in pkg -> kugouArtistUrl(sourceUrl)
            "qqmusic" in pkg || "qq.com" in pkg || "y.qq.com" in pkg -> qqMusicArtistUrl(sourceUrl)
            "douyin" in pkg || "snapvideo" in pkg -> douyinMusicArtistUrl(sourceUrl)
            "163" in pkg || "netease" in pkg || "cloudmusic" in pkg -> neteaseArtistUrl(sourceUrl)
            "kuwo" in pkg -> kuwoArtistUrl(sourceUrl)
            "qobuz" in pkg -> qobuzArtistUrl(sourceUrl)
            "bugs" in pkg -> bugsArtistUrl(sourceUrl)
            "melon" in pkg -> melonArtistUrl(sourceUrl)
            "genie" in pkg -> genieArtistUrl(sourceUrl)
            "archivetune" in pkg || "archive.org" in pkg -> archiveTuneArtistUrl(sourceUrl)
            "lxmusic" in pkg || "yesplaymusic" in pkg -> null
            else -> null
        }
        if (parsedArtistUrl != null) return parsedArtistUrl
        if (MediaServiceResolver.isYouTube(packageName) && !isMultiArtist) {
            val video = resolveYouTubeVideo(metadata)
            if (video != null && video.channelUrl.isNotBlank()) return video.channelUrl
        }
        if (isMultiArtist && parsedCollaborators.isNotEmpty()) {
            val combinedQuery = parsedCollaborators.joinToString(" ")
            if (combinedQuery.isNotBlank()) {
                return MediaServiceResolver.artistUrl(packageName, combinedQuery)
            }
        }
        return MediaServiceResolver.artistUrl(packageName, artist)
    }

    /** Parses collaborating artist names from a raw artist string. */
    private fun parseCollaboratingArtists(rawArtistString: String?): List<String> {
        if (rawArtistString.isNullOrBlank()) return emptyList()
        return splitArtists(rawArtistString)
    }

    /** Extracts a YouTube channel URL from [sourceUrl] after unwrapping YMusic links if needed. */
    private fun youtubeArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl?.let { YouTubeResolver.unwrapYmusicUrl(it) } ?: return null
        if (!YouTubeResolver.isYoutubeFamilyUrl(url)) return null
        return YouTubeResolver.extractChannelUrl(url)
    }

    /** Extracts a SoundCloud artist profile URL. */
    private fun soundcloudArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("soundcloud.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) return "https://soundcloud.com/${segments[0]}"
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Spotify artist, album, or playlist URL (supporting locale-prefixed paths). */
    private fun spotifyArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("spotify.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val typeIndex = segments.indexOfFirst { it in listOf("artist", "album", "playlist") }
            if (typeIndex != -1 && segments.size > typeIndex + 1) {
                return "https://open.spotify.com/${segments[typeIndex]}/${segments[typeIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Bandcamp artist subdomain URL. */
    private fun bandcampArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("bandcamp.com")) return null
        try {
            val uri = URI(url)
            val host = uri.host ?: return null
            if (host.endsWith("bandcamp.com") && host != "bandcamp.com") return "https://$host"
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Deezer artist URL. */
    private fun deezerArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("deezer.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://www.deezer.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts an Apple Music artist URL while preserving storefront locale. */
    private fun appleMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.apple.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val storefront = if (artistIndex >= 1) segments[0] else "us"
                val artistId = segments.last()
                return "https://music.apple.com/$storefront/artist/$artistId"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts an Amazon Music artist URL. */
    private fun amazonMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("amazon.com") && !url.contains("amazon.co.jp")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artists")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val domain = uri.host ?: "music.amazon.com"
                return "https://$domain/artists/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a StreetVoice artist profile URL. */
    private fun streetVoiceArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("streetvoice.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val username = segments[0]
                if (username !in listOf("music", "discovery", "activity")) return "https://streetvoice.com/$username"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a KKBOX artist URL. */
    private fun kkboxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kkbox.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val prefix = if (artistIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                return "https://www.kkbox.com$prefix/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Niconico watch, channel, or user URL. */
    private fun nicoBoxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("nicovideo.jp") && !url.contains("nico.ms")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val host = uri.host ?: ""
            if (segments.size >= 2 && segments[0] == "watch") return url
            if (host == "ch.nicovideo.jp" && segments.isNotEmpty()) {
                return "https://ch.nicovideo.jp/${segments[0]}"
            }
            val userIndex = segments.indexOf("user")
            if (userIndex != -1 && segments.size > userIndex + 1) {
                return "https://www.nicovideo.jp/user/${segments[userIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Bilibili uploader space URL when present. */
    private fun bilibiliArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("bilibili.com")) return null
        try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (host == "space.bilibili.com" && segments.isNotEmpty()) {
                return "https://space.bilibili.com/${segments[0]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a LINE MUSIC artist URL. */
    private fun lineMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.line.me") && !url.contains("music-tw.line.me")) return null
        try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val artistId = segments[artistIndex + 1]
                return if (host.contains("music-tw")) "https://music-tw.line.me/artist/$artistId"
                else "https://music.line.me/webapp/artist/$artistId"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a TIDAL artist URL. */
    private fun tidalArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("tidal.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://tidal.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a JOOX artist URL. */
    private fun jooxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("joox.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://www.joox.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Kugou Music singer/artist URL. */
    private fun kugouArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kugou.com")) return null
        try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotEmpty() }
            val singerIndex = segments.indexOf("singer")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                return "https://www.kugou.com/singer/${segments[singerIndex + 1]}/"
            }
            if (path.endsWith("/") && segments.isNotEmpty()) {
                val id = segments.last()
                if (id.isNotBlank()) return "https://www.kugou.com/$id/"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a QQ Music singer URL. */
    private fun qqMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("qq.com") && !url.contains("y.qq.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val singerIndex = segments.indexOf("singer")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                val mid = segments[singerIndex + 1].substringBefore(".html")
                if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/singer/${mid}.html"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Douyin Music profile URL. */
    private fun douyinMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("douyin.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val code = segments.lastOrNull() ?: ""
            if (code.isNotBlank()) return "https://www.douyin.com/$code/"
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a NetEase Cloud Music artist URL. */
    private fun neteaseArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("163.com") && !url.contains("163cn.tv")) return null
        try {
            val uri = URI(url)
            val query = uri.query ?: ""
            val artistIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
            if (!artistIdParam.isNullOrBlank()) {
                return "https://music.163.com/#/artist?id=$artistIdParam"
            }
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val lastSegment = segments.lastOrNull() ?: ""
            if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                return "https://music.163.com/#/artist?id=$lastSegment"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Kuwo Music singer URL. */
    private fun kuwoArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kuwo.cn")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val singerIndex = segments.indexOf("singer_detail")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                return "https://kuwo.cn/singer_detail/${segments[singerIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Qobuz artist URL. */
    private fun qobuzArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("qobuz.com")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val interpreterIndex = segments.indexOf("interpreter")
            if (interpreterIndex != -1 && segments.size > interpreterIndex + 2) {
                return "https://www.qobuz.com/interpreter/${segments[interpreterIndex + 1]}/${segments[interpreterIndex + 2]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Bugs! artist URL. */
    private fun bugsArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("bugs.co.kr")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://music.bugs.co.kr/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Melon artist URL. */
    private fun melonArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("melon.com")) return null
        try {
            val uri = URI(url)
            val query = uri.query ?: ""
            val artistIdParam = query.split("&").find { it.startsWith("artistId=") }?.substringAfter("artistId=")
            if (!artistIdParam.isNullOrBlank()) {
                return "https://www.melon.com/artist/timeline.htm?artistId=$artistIdParam"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Extracts a Genie Music artist URL. */
    private fun genieArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("genie.co.kr")) return null
        try {
            val uri = URI(url)
            val query = uri.query ?: ""
            val artistIdParam = query.split("&").find { it.startsWith("xxnm=") }?.substringAfter("xxnm=")
            if (!artistIdParam.isNullOrBlank()) {
                return "https://www.genie.co.kr/detail/artistInfo?xxnm=$artistIdParam"
            }
        } catch (_: Exception) {}
        return null
    }

    /** Checks whether a string is a non-blank HTTP or HTTPS URL. */
    private fun String?.isValidMediaUrl(): Boolean {
        if (this.isNullOrBlank()) return false
        val lower = lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /** Extracts an Internet Archive item details URL. */
    private fun archiveTuneArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("archive.org")) return null
        try {
            val uri = URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val detailsIndex = segments.indexOf("details")
            if (detailsIndex != -1 && segments.size > detailsIndex + 1) {
                return "https://archive.org/details/${segments[detailsIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }
}