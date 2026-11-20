package com.my.kizzy.data.get_current_data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.domain.interfaces.Logger
import jakarta.inject.Inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MetadataResolver @Inject constructor(
    private val logger: Logger,
) {
    companion object {
        private const val METADATA_KEY_SOURCE_URL = "android.media.metadata.SOURCE_URL"
        private const val METADATA_KEY_MEDIA_URI = "android.media.metadata.MEDIA_URI"
    }

    fun getCoverArt(metadata: MediaMetadata): Bitmap? {
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val displayIcon = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return albumArt ?: art ?: displayIcon
    }

    fun getArtworkUri(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    fun getArtistOrAuthor(metadata: MediaMetadata): String? {
        return if (!metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_ARTIST
        ) else if (!metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_AUTHOR
        ) else null
    }

    fun getAllArtists(metadata: MediaMetadata): List<String> {
        val artist = getArtistOrAuthor(metadata) ?: return emptyList()
        return splitArtists(artist)
    }

    fun getPrimaryArtist(metadata: MediaMetadata): String? {
        return getAllArtists(metadata).firstOrNull()
    }

    fun formatArtists(metadata: MediaMetadata, maxLength: Int = 128): String {
        val artists = getAllArtists(metadata)
        return if (artists.isEmpty()) "" else if (artists.size == 1) artists[0]
        else artists.joinToString(", ").take(maxLength)
    }

    private fun splitArtists(artist: String): List<String> {
        val separators = listOf(" ft. ", " featuring ", " x ", " & ", ", ", "; ")
        var result = listOf(artist)
        for (sep in separators) {
            result = result.flatMap { it.split(sep) }.map { it.trim() }.filter { it.isNotEmpty() }
            if (result.size > 1) break
        }
        return result.distinct()
    }

    fun getAlbum(metadata: MediaMetadata): String? {
        return if (!metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_ALBUM
        ) else null
    }

    /**
     * Best-effort playlist name.
     *
     * Android's [MediaMetadata] has no standard playlist-title field, so this
     * only returns a value for players that publish a custom key (e.g.
     * "playlist_name" / "playlist_title"). Url-valued keys are ignored.
     */
    fun getPlaylistName(metadata: MediaMetadata): String? {
        val customKeys = listOf(
            "playlist_name", "playlist_title", "playlistName", "playlist",
            "android.media.metadata.PLAYLIST_TITLE", "android.media.metadata.PLAYLIST_NAME",
        )
        for (key in customKeys) {
            metadata.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return runCatching {
            metadata.keySet().firstNotNullOfOrNull { key ->
                val lowerKey = key.lowercase()
                // Only keys that clearly name the playlist, never ids/urls.
                val looksLikeName = lowerKey == "playlist" ||
                    (lowerKey.contains("playlist") &&
                        (lowerKey.contains("name") || lowerKey.contains("title")))
                if (!looksLikeName) return@firstNotNullOfOrNull null
                val value = metadata.getString(key)?.trim() ?: return@firstNotNullOfOrNull null
                value.takeIf {
                    it.isNotEmpty() &&
                        !it.startsWith("http://", ignoreCase = true) &&
                        !it.startsWith("https://", ignoreCase = true)
                }
            }
        }.getOrNull()
    }

    fun getAlbumArtists(metadata: MediaMetadata): String? {
        return if (!metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST
        ) else getArtistOrAuthor(metadata)
    }

    fun getAudioChannels(metadata: MediaMetadata): String? {
        val channelKeys = listOf("audio_channels", "audio_channel_count", "channel_count", "surround", "audio_format")
        for (key in channelKeys) {
            val value = metadata.getString(key)
            if (value.isNullOrEmpty()) continue
            return when (value.lowercase()) {
                "5.1", "5.1ch", "51" -> "5.1 Surround"
                "7.1", "7.1ch", "71" -> "7.1 Surround"
                "2.0", "stereo" -> "Stereo"
                "atmos", "dolby atmos", "dolbyatmos" -> "Dolby Atmos"
                else -> value
            }
        }
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val channelMatch = Regex("""(5\.1|7\.1|2\.0|stereo|surround|atmos)""", RegexOption.IGNORE_CASE).find(album)
        return channelMatch?.value?.replaceFirstChar { it.uppercase() }
    }

    fun getMediaUrl(
        metadata: MediaMetadata,
        packageName: String?,
        extraValues: List<String> = emptyList(),
    ): String? {
        return toRpcMediaUrl(resolveRawMediaUrl(metadata, packageName, extraValues))
    }

    /**
     * YMusic (ymusicapp.com / www.ymusicapp.com) and every other YouTube-family
     * watch url must land on https://youtu.be/ID in the RPC button.
     *
     * Non-YouTube URLs (e.g. github.com/morphe-patches for Morphe,
     * github.com/.../revanced-patches for AndDea Revanced Patches) are kept
     * as-is so the cover art stays clickable for YouTube clients that publish
     * their own page instead of a YouTube video URL.
     */
    private fun toRpcMediaUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // Delegate directly to YouTubeResolver's robust unwrapping and normalization logic
        YouTubeResolver.toRpcUrl(url)?.let { canonicalUrl ->
            // Additional safety check: ensure it's either ayoutu.be video,
            // a channel link, or a playlist link, and not an accidental search query.
            if (!canonicalUrl.contains("/results?search_query=")) {
                return canonicalUrl
            }
        }

        // Non-YouTube URL: keep it as-is if it is a real http(s) URL.
        // This keeps the cover art clickable for clients like Morphe and
        // AndDea Revanced Patches that publish their project page instead
        // of a YouTube video link.
        return url.takeIf {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                !it.contains("/results?search_query=")
        }
    }

    private fun resolveRawMediaUrl(
        metadata: MediaMetadata,
        packageName: String?,
        extraValues: List<String>,
    ): String? {
        // A browser media session (Chrome / Edge / Firefox / Vivaldi / ...)
        // plays a WEB PAGE (e.g. music.apple.com/jp/album/...). The page url
        // is published in one of the metadata string fields, so extract it
        // before applying any app-specific normalisation.
        // Prefer a supported webpage URL for browsers, including browsers whose
        // package names are not in the known list. Unknown players are scanned
        // too, since they may be browser-based media sessions.
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
        val customKeys = listOf("youtube_url", "video_url", "source_url", "media_url", "content_url",
            "spotify_url", "apple_music_url", "deezer_url", "soundcloud_url")
        for (key in customKeys) {
            val value = metadata.getString(key)
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
            // YouTube wrapper clients (Morphe, AndDea Revanced, Metrolist, etc.)
            // publish their own metadata and should not fall back to a YouTube
            // search page when no video URL is found. Return null instead so no
            // misleading search link is sent to Discord.
            if (MediaServiceResolver.isYouTubeWrapper(packageName)) {
                return null
            }
            // No direct video/playlist id was published by the session.
            // Fall back to the title search instead of returning null: the
            // artist link still gets a search page, so the media link must too,
            // otherwise Discord shows only "Artist on ..." and the
            // video/music/playlist button disappears entirely.
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            return MediaServiceResolver.mediaSearchUrl(packageName, title)
        }
        if (MediaServiceResolver.isBrowser(packageName)) {
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            return MediaServiceResolver.browserSearchUrl(title)
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return MediaServiceResolver.mediaSearchUrl(packageName, title)
    }

    /** Resolves a track URL from sourceUrl or custom metadata keys. */
    fun getTrackUrl(metadata: MediaMetadata, packageName: String?): String? {
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (sourceUrl != null && sourceUrl.isValidMediaUrl()) {
            val url = resolveTrackUrl(sourceUrl, packageName)
            if (url != null) return url
        }
        val customKeys = listOf(
            "youtube_url", "video_url", "source_url", "media_url", "content_url",
            "spotify_url", "spotify_track_url", "apple_music_url", "apple_track_url",
            "deezer_url", "deezer_track_url", "soundcloud_url", "amazon_url",
            "amazon_track_url", "tidal_url", "tidal_track_url", "kkbox_url",
            "kkbox_track_url", "linemusic_url", "linemusic_track_url", "joox_url",
            "joox_track_url"
        )
        for (key in customKeys) {
            val value = metadata.getString(key)
            if (value != null && value.isValidMediaUrl()) {
                val url = resolveTrackUrl(value, packageName)
                if (url != null) return url
            }
        }
        scanAllMetadataForUrl(metadata)?.let { url ->
            val resolved = resolveTrackUrl(url, packageName)
            if (resolved != null) return resolved
        }
        return null
    }

    /** Resolves an album URL from sourceUrl or custom metadata keys. */
    fun getAlbumUrl(metadata: MediaMetadata, packageName: String?): String? {
        // YouTube Music clients often publish the album only through the media
        // uri / media id (a browse page or a list=OLAK5uy_... url), so those are
        // checked in addition to the source url.
        val candidateKeys = listOf(
            METADATA_KEY_SOURCE_URL,
            METADATA_KEY_MEDIA_URI,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
        )
        for (key in candidateKeys) {
            val value = metadata.getString(key)
            if (value != null && value.isValidMediaUrl()) {
                val url = resolveAlbumUrl(value, packageName)
                if (url != null) return url
            }
        }
        val customKeys = listOf(
            "spotify_album_url", "apple_music_album_url", "deezer_album_url",
            "amazon_album_url", "tidal_album_url", "kkbox_album_url",
            "linemusic_album_url", "joox_album_url", "soundcloud_album_url",
            "bandcamp_album_url"
        )
        for (key in customKeys) {
            val value = metadata.getString(key)
            if (value != null && value.isValidMediaUrl()) {
                val url = resolveAlbumUrl(value, packageName)
                if (url != null) return url
            }
        }
        return null
    }

    /** Resolves a playlist URL from sourceUrl or custom metadata keys. */
    fun getPlaylistUrl(metadata: MediaMetadata, packageName: String?): String? {
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (sourceUrl != null && sourceUrl.isValidMediaUrl()) {
            val url = resolvePlaylistUrl(sourceUrl, packageName)
            if (url != null) return url
        }
        val customKeys = listOf(
            "spotify_playlist_url", "apple_music_playlist_url", "deezer_playlist_url",
            "amazon_playlist_url", "tidal_playlist_url", "kkbox_playlist_url",
            "linemusic_playlist_url", "joox_playlist_url", "soundcloud_playlist_url"
        )
        for (key in customKeys) {
            val value = metadata.getString(key)
            if (value != null && value.isValidMediaUrl()) {
                val url = resolvePlaylistUrl(value, packageName)
                if (url != null) return url
            }
        }
        return null
    }

    private fun resolveTrackUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()
        val pkg = packageName?.lowercase().orEmpty()

        // YouTube / YouTube Music / YMusic (ymusicapp.com, www.ymusicapp.com)
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed)) {
            return YouTubeResolver.toRpcUrl(trimmed)
        }

        // Spotify track: open.spotify.com/track/{id}
        if (lower.contains("spotify.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2 && segments[0] == "track") {
                    return "https://open.spotify.com/track/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music track: music.apple.com/{country}/album/{slug}/{album-id}?i={track-id}
        if (lower.contains("music.apple.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val query = uri.query ?: ""
                val trackIdParam = query.split("&").find { it.startsWith("i=") }?.substringAfter("i=")
                if (trackIdParam != null && trackIdParam.isNotBlank()) {
                    val albumSlug = if (segments.size >= 4) segments[2] else ""
                    val albumId = if (segments.size >= 4) segments[3] else ""
                    return "https://music.apple.com/us/album/$albumSlug/$albumId?i=$trackIdParam"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music track: music.amazon.com/{asin}/{slug} or music.amazon.com/{asin}
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer track: deezer.com/{lang}/track/{id}
        if (lower.contains("deezer.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://www.deezer.com/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL track: tidal.com/track/{id}
        if (lower.contains("tidal.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://tidal.com/track/${segments[trackIndex + 1]}"
                }
                // Also match direct /{id}
                if (segments.size >= 2 && segments[0] == "track") {
                    return "https://tidal.com/track/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX track: kkbox.com/{region}/{lang}/song/{id}
        if (lower.contains("kkbox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val prefix = if (songIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/song/${segments[songIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC track: music.line.me/webapp/track/{id}
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/track/${segments[trackIndex + 1]}"
                    else "https://music.line.me/webapp/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX track: joox.com/{region}/single/{id}
        if (lower.contains("joox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val singleIndex = segments.indexOf("single")
                if (singleIndex != -1 && segments.size > singleIndex + 1) {
                    return "https://www.joox.com/single/${segments[singleIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music track: kugou.com/{encode_id}.html
        if (lower.contains("kugou.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                // kugou.com/mixsong/{encode_id}.html or kugou.com/{encode_id}.html
                if (lastSegment.endsWith(".html")) {
                    val id = lastSegment.substringBefore(".html")
                    if (id.isNotBlank()) return "https://www.kugou.com/${id}.html"
                }
                // kugou.com/{hash} (32-char MD5)
                if (lastSegment.length == 32 && lastSegment.matches(Regex("[0-9a-fA-F]{32}"))) {
                    return "https://www.kugou.com/${lastSegment}.html"
                }
            } catch (_: Exception) {}
        }

        // QQ Music track: qq.com{nmid} or y.qq.com/n/yqq/song/{mid}.html
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                // y.qq.com/n/yqq/song/{mid}.html
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val songSegment = segments[songIndex + 1]
                    val mid = songSegment.substringBefore(".html")
                    if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/song/${mid}.html"
                }
                // qq.com{nmid} — 14-char mid
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.length >= 10 && lastSegment.matches(Regex("[0-9A-Za-z]{10,}"))) {
                    return "https://y.qq.com/n/yqq/song/${lastSegment}.html"
                }
            } catch (_: Exception) {}
        }

        // Qishui Music (汽水音樂): music.douyin.com/qishui/(track|album|artist|playlist)?{type}_id={id}
        if (lower.contains("music.douyin.com") || lower.contains("douyin.com/qishui")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val query = uri.query ?: ""
                // qishui/track?track_id=12345
                val trackIdParam = query.split("&").find { it.startsWith("track_id=") }?.substringAfter("track_id=")
                if (trackIdParam != null && trackIdParam.isNotBlank()) {
                    return "https://music.douyin.com/qishui/track?track_id=${trackIdParam}"
                }
                val albumIdParam = query.split("&").find { it.startsWith("album_id=") }?.substringAfter("album_id=")
                if (albumIdParam != null && albumIdParam.isNotBlank()) {
                    return "https://music.douyin.com/qishui/album?album_id=${albumIdParam}"
                }
                val artistIdParam = query.split("&").find { it.startsWith("artist_id=") }?.substringAfter("artist_id=")
                if (artistIdParam != null && artistIdParam.isNotBlank()) {
                    return "https://music.douyin.com/qishui/artist?artist_id=${artistIdParam}"
                }
                val playlistIdParam = query.split("&").find { it.startsWith("playlist_id=") }?.substringAfter("playlist_id=")
                if (playlistIdParam != null && playlistIdParam.isNotBlank()) {
                    return "https://music.douyin.com/qishui/playlist?playlist_id=${playlistIdParam}"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music track: douyin.com{share_code}/
        if (lower.contains("douyin.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val code = segments.lastOrNull() ?: ""
                    if (code.isNotBlank()) return "https://www.douyin.com/${code}/"
                }
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music track: 163.com{id}
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val query = uri.query ?: ""
                // music.163.com/song?id=123456
                val songIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (songIdParam != null && songIdParam.isNotBlank()) {
                    return "https://music.163.com/song?id=${songIdParam}"
                }
                // 163.com/{id} or 163cn.tv/{code}
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/song?id=${lastSegment}"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music track: kuwo.cn/play_detail/{id}
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playIndex = segments.indexOf("play_detail")
                if (playIndex != -1 && segments.size > playIndex + 1) {
                    return "https://kuwo.cn/play_detail/${segments[playIndex + 1]}"
                }
                // m.kuwo.cn/yinyue/{id}
                val yinyueIndex = segments.indexOf("yinyue")
                if (yinyueIndex != -1 && segments.size > yinyueIndex + 1) {
                    return "https://m.kuwo.cn/yinyue/${segments[yinyueIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JioSaavn track: jiosaavn.com{slug}/{token}
        if (lower.contains("jiosaavn.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val token = segments.lastOrNull() ?: ""
                    if (token.isNotBlank()) return "https://www.jiosaavn.com/${token}"
                }
            } catch (_: Exception) {}
        }

        // Gaana track: gaana.com{slug}
        if (lower.contains("gaana.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val slug = segments.lastOrNull() ?: ""
                    if (slug.isNotBlank()) return "https://gaana.com/${slug}"
                }
            } catch (_: Exception) {}
        }

        // Wynk Music track: wynk.in/music/song/{slug}/{id}
        if (lower.contains("wynk.in")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 2) {
                    return "https://wynk.in/music/song/${segments[songIndex + 1]}/${segments[songIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Boomplay track: boomplay.com{id}
        if (lower.contains("boomplay.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://www.boomplay.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // Audiomack track: audiomack.com{artist-slug}/song/{song-slug}
        if (lower.contains("audiomack.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val songIndex = segments.indexOf("song")
                if (songIndex != -1 && segments.size > songIndex + 1) {
                    val artistSlug = if (songIndex > 0) segments[songIndex - 1] else ""
                    if (artistSlug.isNotBlank()) return "https://www.audiomack.com/${artistSlug}/song/${segments[songIndex + 1]}"
                    return "https://www.audiomack.com/song/${segments[songIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Yandex Music track: music.yandex.ru/album/{album-id}/track/{track-id}
        if (lower.contains("music.yandex.ru") || lower.contains("music.yandex.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    val albumId = if (trackIndex > 0) segments[trackIndex - 1] else ""
                    return "https://music.yandex.ru/album/${albumId}/track/${segments[trackIndex + 1]}"
                }
                // music.yandex.ru/artist/{artist-id}
                val artistIndex = segments.indexOf("artist")
                if (artistIndex != -1 && segments.size > artistIndex + 1) {
                    return "https://music.yandex.ru/artist/${segments[artistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // VK Music: vk.com/audio?section=search&q=...
        if (lower.contains("vk.com") || lower.contains("vk.me")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                if (query.contains("audio")) {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        // Anghami track: anghami.com{id}
        if (lower.contains("anghami.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://anghami.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // AWA track: s.awa.fm/track/{id}
        if (lower.contains("awa.fm")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return "https://s.awa.fm/track/${segments[trackIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Recochoku track: recochoku.jp - search by title fallback
        if (lower.contains("recochoku.jp")) return trimmed

        // Melon track: melon.com{id}
        if (lower.contains("melon.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val songIdParam = query.split("&").find { it.startsWith("songId=") }?.substringAfter("songId=")
                if (songIdParam != null && songIdParam.isNotBlank()) {
                    return "https://www.melon.com/song/detail.htm?songId=${songIdParam}"
                }
            } catch (_: Exception) {}
        }

        // Genie Music track: genie.co.kr/detail/songInfo?xgnm={id}
        if (lower.contains("genie.co.kr")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val songIdParam = query.split("&").find { it.startsWith("xgnm=") }?.substringAfter("xgnm=")
                if (songIdParam != null && songIdParam.isNotBlank()) {
                    return "https://www.genie.co.kr/detail/songInfo?xgnm=${songIdParam}"
                }
            } catch (_: Exception) {}
        }

        // Beatport track: beatport.com{slug}/{id}
        if (lower.contains("beatport.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://www.beatport.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // SoundCloud track: soundcloud.com/{user}/{track}
        if (lower.contains("soundcloud.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2) {
                    return "https://soundcloud.com/${segments[0]}/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // Bandcamp track: {artist-subdomain}.bandcamp.com/track/{track-slug}
        if (lower.contains("bandcamp.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val trackIndex = segments.indexOf("track")
                if (trackIndex != -1 && segments.size > trackIndex + 1) {
                    return trimmed
                }
                // Also handle direct track URLs: /track/{slug}
                if (segments.size >= 2 && segments[0] == "track") {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun resolveAlbumUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Spotify album: open.spotify.com/album/{id}
        if (lower.contains("spotify.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2 && segments[0] == "album") {
                    return "https://open.spotify.com/album/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music album: music.apple.com/{country}/album/{slug}/{id}
        if (lower.contains("music.apple.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 2) {
                    return "https://music.apple.com/us/album/${segments[albumIndex + 1]}/${segments[albumIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music album
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer album: deezer.com/{lang}/album/{id}
        if (lower.contains("deezer.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.deezer.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL album: tidal.com/album/{id}
        if (lower.contains("tidal.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://tidal.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX album: kkbox.com/{region}/{lang}/album/{id}
        if (lower.contains("kkbox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val prefix = if (albumIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC album: music.line.me/webapp/album/{id}
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/album/${segments[albumIndex + 1]}"
                    else "https://music.line.me/webapp/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX album: joox.com/{region}/album/{id}
        if (lower.contains("joox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.joox.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music album: kugou.com/{album_id}/
        if (lower.contains("kugou.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                // kugou.com/album/{id}/ or kugou.com/{id}/
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://www.kugou.com/album/${segments[albumIndex + 1]}/"
                }
                // Last segment could be album id
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.endsWith("/") || lastSegment.isBlank()) {
                    val secondLast = segments.getOrNull(segments.size - 2) ?: ""
                    if (secondLast.isNotBlank()) return "https://www.kugou.com/${secondLast}/"
                }
            } catch (_: Exception) {}
        }

        // QQ Music album: qq.com{album_mid}
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val mid = segments[albumIndex + 1].substringBefore(".html")
                    if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/album/${mid}.html"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music album: douyin.com{share_code}/
        if (lower.contains("douyin.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val code = segments.lastOrNull() ?: ""
                    if (code.isNotBlank()) return "https://www.douyin.com/${code}/"
                }
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music album: 163.com{id}
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (albumIdParam != null && albumIdParam.isNotBlank()) {
                    return "https://music.163.com/album?id=${albumIdParam}"
                }
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/album?id=${lastSegment}"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music album: kuwo.cn/album_detail/{id}
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album_detail")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://kuwo.cn/album_detail/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JioSaavn album: jiosaavn.com{slug}/{token}
        if (lower.contains("jiosaavn.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val token = segments.lastOrNull() ?: ""
                    if (token.isNotBlank()) return "https://www.jiosaavn.com/${token}"
                }
            } catch (_: Exception) {}
        }

        // Gaana album: gaana.com{slug}
        if (lower.contains("gaana.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val slug = segments.lastOrNull() ?: ""
                    if (slug.isNotBlank()) return "https://gaana.com/${slug}"
                }
            } catch (_: Exception) {}
        }

        // Wynk Music album: wynk.in/music/album/{slug}/{id}
        if (lower.contains("wynk.in")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 2) {
                    return "https://wynk.in/music/album/${segments[albumIndex + 1]}/${segments[albumIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Boomplay album: boomplay.com{id}
        if (lower.contains("boomplay.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://www.boomplay.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // Audiomack album: audiomack.com{artist-slug}/album/{album-slug}
        if (lower.contains("audiomack.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    val artistSlug = if (albumIndex > 0) segments[albumIndex - 1] else ""
                    if (artistSlug.isNotBlank()) return "https://www.audiomack.com/${artistSlug}/album/${segments[albumIndex + 1]}"
                    return "https://www.audiomack.com/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Yandex Music album: music.yandex.ru/album/{album-id}
        if (lower.contains("music.yandex.ru") || lower.contains("music.yandex.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://music.yandex.ru/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // VK Music playlist/album: vk.com/audio
        if (lower.contains("vk.com") || lower.contains("vk.me")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                if (query.contains("audio")) {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        // Anghami album: anghami.com{id}
        if (lower.contains("anghami.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://anghami.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // AWA album: s.awa.fm/album/{id}
        if (lower.contains("awa.fm")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return "https://s.awa.fm/album/${segments[albumIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Recochoku album: recochoku.jp - search by title fallback
        if (lower.contains("recochoku.jp")) return trimmed

        // Melon album: melon.com{id}
        if (lower.contains("melon.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("albumId=") }?.substringAfter("albumId=")
                if (albumIdParam != null && albumIdParam.isNotBlank()) {
                    return "https://www.melon.com/album/detail.htm?albumId=${albumIdParam}"
                }
            } catch (_: Exception) {}
        }

        // Genie Music album: genie.co.kr/detail/albumInfo?axnm={id}
        if (lower.contains("genie.co.kr")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val albumIdParam = query.split("&").find { it.startsWith("axnm=") }?.substringAfter("axnm=")
                if (albumIdParam != null && albumIdParam.isNotBlank()) {
                    return "https://www.genie.co.kr/detail/albumInfo?axnm=${albumIdParam}"
                }
            } catch (_: Exception) {}
        }

        // Beatport album: beatport.com{slug}/{id}
        if (lower.contains("beatport.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val id = segments.lastOrNull() ?: ""
                    if (id.isNotBlank()) return "https://www.beatport.com/${id}"
                }
            } catch (_: Exception) {}
        }

        // Bandcamp album: {artist-subdomain}.bandcamp.com/album/{album-slug}
        if (lower.contains("bandcamp.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val albumIndex = segments.indexOf("album")
                if (albumIndex != -1 && segments.size > albumIndex + 1) {
                    return trimmed
                }
            } catch (_: Exception) {}
        }

        // YouTube Music album:
        //  - browse page: music.youtube.com/browse/MPREb...
        //  - album playlist: any YouTube url carrying list=OLAK5uy_...
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
            // OLAK5uy_... is the id YouTube Music uses for an album playlist.
            if (listId != null && listId.startsWith("OLAK5uy")) {
                return "https://music.youtube.com/playlist?list=$listId"
            }
        }

        return null
    }

    private fun resolvePlaylistUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Spotify playlist: open.spotify.com/playlist/{id}
        if (lower.contains("spotify.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2 && segments[0] == "playlist") {
                    return "https://open.spotify.com/playlist/${segments[1]}"
                }
            } catch (_: Exception) {}
        }

        // Apple Music playlist: music.apple.com/{country}/playlist/{slug}/{id}
        if (lower.contains("music.apple.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 2) {
                    return "https://music.apple.com/us/playlist/${segments[playlistIndex + 1]}/${segments[playlistIndex + 2]}"
                }
            } catch (_: Exception) {}
        }

        // Amazon Music playlist
        if (lower.contains("amazon.com") || lower.contains("amazon.co.jp")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val domain = uri.host ?: "music.amazon.com"
                    return "https://$domain/${segments[0]}"
                }
            } catch (_: Exception) {}
        }

        // Deezer playlist: deezer.com/{lang}/playlist/{id}
        if (lower.contains("deezer.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://www.deezer.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // TIDAL playlist: tidal.com/playlist/{id}
        if (lower.contains("tidal.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://tidal.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // KKBOX playlist: kkbox.com/{region}/{lang}/playlist/{id}
        if (lower.contains("kkbox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val prefix = if (playlistIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                    return "https://www.kkbox.com$prefix/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // LINE MUSIC playlist: music.line.me/webapp/playlist/{id}
        if (lower.contains("music.line.me") || lower.contains("music-tw.line.me")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val host = uri.host ?: "music.line.me"
                    return if (host.contains("music-tw")) "https://music-tw.line.me/webapp/playlist/${segments[playlistIndex + 1]}"
                    else "https://music.line.me/webapp/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // JOOX playlist: joox.com/{region}/playlist/{id}
        if (lower.contains("joox.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://www.joox.com/playlist/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // Kugou Music playlist: kugou.com/{id}.html
        if (lower.contains("kugou.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.endsWith(".html")) {
                    val id = lastSegment.substringBefore(".html")
                    if (id.isNotBlank()) return "https://www.kugou.com/${id}.html"
                }
            } catch (_: Exception) {}
        }

        // QQ Music playlist: qq.com{id}
        if (lower.contains("qq.com") || lower.contains("y.qq.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    val id = segments[playlistIndex + 1].substringBefore(".html")
                    if (id.isNotBlank()) return "https://y.qq.com/n/yqq/playsquare/${id}.html"
                }
            } catch (_: Exception) {}
        }

        // Douyin Music playlist: douyin.com{share_code}/
        if (lower.contains("douyin.com")) {
            try {
                val uri = java.net.URI(trimmed)
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.isNotEmpty()) {
                    val code = segments.lastOrNull() ?: ""
                    if (code.isNotBlank()) return "https://www.douyin.com/${code}/"
                }
            } catch (_: Exception) {}
        }

        // NetEase Cloud Music playlist: 163.com{id}
        if (lower.contains("163.com") || lower.contains("163cn.tv")) {
            try {
                val uri = java.net.URI(trimmed)
                val query = uri.query ?: ""
                val playlistIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
                if (playlistIdParam != null && playlistIdParam.isNotBlank()) {
                    return "https://music.163.com/#/playlist?id=${playlistIdParam}"
                }
                val path = uri.path ?: ""
                val segments = path.split("/").filter { it.isNotEmpty() }
                val lastSegment = segments.lastOrNull() ?: ""
                if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                    return "https://music.163.com/#/playlist?id=${lastSegment}"
                }
            } catch (_: Exception) {}
        }

        // Kuwo Music playlist: kuwo.cn/playlist_detail/{id}
        if (lower.contains("kuwo.cn")) {
            try {
                val uri = java.net.URI(trimmed)
                val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
                val playlistIndex = segments.indexOf("playlist_detail")
                if (playlistIndex != -1 && segments.size > playlistIndex + 1) {
                    return "https://kuwo.cn/playlist_detail/${segments[playlistIndex + 1]}"
                }
            } catch (_: Exception) {}
        }

        // YouTube playlist handled by extractPlaylistId (incl. www.ymusicapp.com)
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed)) {
            val playlistId = YouTubeResolver.extractPlaylistId(trimmed)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
        }

        return null
    }

    /**
     * Scans EVERY string metadata value for a YouTube video id.
     *
     * YouTube Music wrapper clients (Morphe, AndDea Revanced, InnerTune, ...)
     * publish the playing video id under custom keys that vary by client, so a
     * hardcoded key list misses them. Falling back to a network scrape of the
     * YouTube search page is unreliable (often blocked) and produced search
     * links like `youtube.com/results?search_query=...` instead of a direct
     * `youtu.be/ID`. This local scan needs no network and makes the details /
     * cover links resolve to the real video.
     */
    private fun findVideoIdInMetadata(metadata: MediaMetadata): String? {
        // Check id/url-ish keys first, then everything else.
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
            val value = metadata.getString(key)?.trim() ?: continue
            if (value.isEmpty()) continue
            val id = YouTubeResolver.extractVideoId(value) ?: continue
            if (YouTubeResolver.isValidVideoId(id)) return id
        }
        return null
    }

    suspend fun getDirectMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null
        // Local full-metadata scan first — no network needed. Handles clients
        // that publish the video id under a custom key.
        findVideoIdInMetadata(metadata)?.let { return "https://youtu.be/$it" }
        firstMetadataUrl(metadata)?.let { url ->
            if (YouTubeResolver.isYoutubeFamilyUrl(url)) {
                toRpcMediaUrl(url)?.let { return it }
            }
        }
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            if (YouTubeResolver.isYoutubeFamilyUrl(mediaId)) {
                toRpcMediaUrl(mediaId)?.let { return it }
            }
            YouTubeResolver.extractVideoId(mediaId)?.let { return "https://youtu.be/$it" }
            // Channel URL — keep as a channel link (supports /channel/UC..., /@handle, /c/, /user/)
            YouTubeResolver.extractChannelUrl(mediaId)?.let { return it }
            // Playlist URL — keep as a playlist link
            val playlistId = YouTubeResolver.extractPlaylistId(mediaId)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
            // yt:video:VIDEO_ID or t:VIDEO_ID
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

    suspend fun resolveYouTubeVideo(metadata: MediaMetadata): YouTubeResolver.Video? {
        val sourceUrl = firstMetadataUrl(metadata)
        if (!sourceUrl.isNullOrEmpty() && YouTubeResolver.isYoutubeFamilyUrl(sourceUrl)) {
            YouTubeResolver.lookup(YouTubeResolver.unwrapYmusicUrl(sourceUrl))?.let { return it }
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        if (title.isNullOrEmpty()) return null
        val artist = MetadataResolverArtistName(metadata)
        return YouTubeResolver.lookupByTitle(title, artist)
    }

    /**
     * Channel avatar URL for the "Show Channel Icon" small-image option.
     * Only for YouTube-family clients with a SINGLE artist/channel — a
     * collaboration (multiple artists) has no single channel to represent.
     */
    suspend fun getChannelAvatarUrl(
        metadata: MediaMetadata,
        packageName: String?,
    ): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null
        // Single channel only: skip collaborations / multi-artist tracks.
        if (getAllArtists(metadata).size > 1) return null
        val raw = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
        if (!raw.isNullOrBlank() && splitArtists(raw).size > 1) return null
        val video = resolveYouTubeVideo(metadata) ?: return null
        val id = YouTubeResolver.extractVideoId(video.watchUrl) ?: return null
        return YouTubeResolver.lookupChannelAvatar(id)
    }

    /**
     * Extracts the page url of a web player from a browser MediaSession.
     *
     * Browsers (Chrome, Edge, Firefox, Vivaldi, ...) publish the page url in
     * different metadata fields depending on the site. We scan every string
     * metadata value for a known media host and keep the FULL url - e.g.
     * https://music.apple.com/jp/album/celestia-fragments/1856761201 -
     * instead of replacing it with a search page.
     *
     * YouTube / YMusic pages are rewritten to https://youtu.be/ID.
     * Returns null when no page url was published so the caller can fall back.
     */
    private fun browserPageUrl(metadata: MediaMetadata, extraValues: List<String> = emptyList()): String? {
        val mediaHosts = listOf(
            "music.apple.com", "itunes.apple.com", "open.spotify.com",
            "youtube.com", "youtu.be", "ymusicapp.com",
            "music.youtube.com", "deezer.com", "soundcloud.com",
            "music.amazon.com", "amazon.com/music", "tidal.com", "bandcamp.com",
            "nicovideo.jp", "bilibili.com", "twitch.tv", "netflix.com",
            "disneyplus.com", "music.163.com", "y.qq.com", "streetvoice.com",
        )
        val skipHosts = listOf(
            "blob:", "data:", "googlevideo.com", "mzstatic.com", "googleusercontent.com",
            "ytimg.com", "ggpht.com", "akamaized.net",
        )
        val candidates = mutableListOf<String>()
        extraValues.forEach { candidates.add(it) }
        // Standard keys first (most browsers put the url here).
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
            metadata.getString(key)?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        }
        // Then every key available in the bundle (sites use custom keys).
        metadata.keySet().forEach { key ->
            runCatching { metadata.getString(key) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { candidates.add(it) }
        }
        for (candidate in candidates) {
            val url = Regex("https?://[^\\s\"<>]+").find(candidate)?.value
                ?: candidate.takeIf { it.startsWith("http") }
                ?: continue
            val cleaned = url.trim().trimEnd(')', ',', ']', '"', '\'')
            if (skipHosts.any { host -> cleaned.contains(host, ignoreCase = true) }) continue
            if (mediaHosts.any { host -> cleaned.contains(host, ignoreCase = true) }) {
                return toRpcMediaUrl(cleaned)
            }
        }
        return null
    }

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
            metadata.getString(key)?.takeIf { it.isValidMediaUrl() }?.let { return it }
        }
        return scanAllMetadataForUrl(metadata)
    }

    private fun scanAllMetadataForUrl(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR, MediaMetadata.METADATA_KEY_GENRE,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
        )
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty() && value.isValidMediaUrl()) return value
        }
        return null
    }

    private fun normalizeMediaUrl(url: String, packageName: String?): String? {
        val trimmed = url.trim()
        if (trimmed.contains("/browse/")) return toRpcMediaUrl(trimmed)
        if (YouTubeResolver.isYoutubeFamilyUrl(trimmed) || MediaServiceResolver.isYouTube(packageName)) {
            return YouTubeResolver.toRpcUrl(trimmed)
        }
        return trimmed
    }

    private fun extractYouTubeUrl(metadata: MediaMetadata): String? {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        // Local full-metadata scan first (no network), so wrapper clients resolve
        // to youtu.be/ID instead of a search page.
        findVideoIdInMetadata(metadata)?.let { return "https://youtu.be/$it" }
        fun fromRaw(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            if (raw.contains("/results?search_query")) return null
            if (YouTubeResolver.isYoutubeFamilyUrl(raw)) return YouTubeResolver.toRpcUrl(raw)
            YouTubeResolver.extractVideoId(raw)?.let { return "https://youtu.be/$it" }
            return null
        }
        fromRaw(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))?.let { return it }
        fromRaw(firstMetadataUrl(metadata))?.let { return it }
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
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
        val youtubeKeys = listOf("youtube_url", "video_url", "source_url", "media_url", "content_url",
            "yt_url", "youtube_video_url", "youtube_watch_url")
        for (key in youtubeKeys) {
            fromRaw(metadata.getString(key))?.let { return it }
        }
        if (!title.isNullOrEmpty()) {
            return "https://www.youtube.com/results?search_query=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
        }
        return null
    }

    private fun MetadataResolverArtistName(metadata: MediaMetadata): String? {
        return getArtistOrAuthor(metadata)
    }

    suspend fun getArtistUrl(metadata: MediaMetadata, packageName: String?): String? {
        val artist = MetadataResolverArtistName(metadata)
        if (artist.isNullOrEmpty()) return null
        val artists = getAllArtists(metadata)
        val rawArtistString = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val parsedCollaborators = parseCollaboratingArtists(rawArtistString)
        val isMultiArtist = artists.size > 1 || parsedCollaborators.size > 1
        val pkg = packageName?.lowercase().orEmpty()
        if (MediaServiceResolver.isYouTube(packageName) && isMultiArtist) {
            val combinedQuery = parsedCollaborators.joinToString(" ").ifBlank { artist }
            return MediaServiceResolver.artistUrl(packageName, combinedQuery)
        }
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        val parsedArtistUrl = when {
            "soundcloud" in pkg -> soundcloudArtistUrl(sourceUrl)
            "spotify" in pkg -> spotifyArtistUrl(sourceUrl)
            "bandcamp" in pkg -> bandcampArtistUrl(sourceUrl)
            "youtube" in pkg || "ymusic" in pkg || "simpmusic" in pkg ||
            "vimusic" in pkg || "innertune" in pkg -> youtubeArtistUrl(sourceUrl)
            "spotube" in pkg -> if (sourceUrl?.contains("spotify.com") == true) {
                spotifyArtistUrl(sourceUrl)
            } else { youtubeArtistUrl(sourceUrl) }
            "deezer" in pkg -> deezerArtistUrl(sourceUrl)
            "apple" in pkg -> appleMusicArtistUrl(sourceUrl)
            "amazon" in pkg -> amazonMusicArtistUrl(sourceUrl)
            "streetvoice" in pkg -> streetVoiceArtistUrl(sourceUrl)
            "kkbox" in pkg -> kkboxArtistUrl(sourceUrl)
            "nicobox" in pkg || "nicovideo" in pkg -> nicoBoxArtistUrl(sourceUrl)
            "linemusic" in pkg || "linecorp.linemusic" in pkg -> lineMusicArtistUrl(sourceUrl)
            "tidal" in pkg -> tidalArtistUrl(sourceUrl)
            "joox" in pkg -> jooxArtistUrl(sourceUrl)
            "kugou" in pkg || "kugoumusic" in pkg -> kugouArtistUrl(sourceUrl)
            "qqmusic" in pkg || "qq.com" in pkg || "y.qq.com" in pkg -> qqMusicArtistUrl(sourceUrl)
            "douyin" in pkg || "snapvideo" in pkg -> douyinMusicArtistUrl(sourceUrl)
            "163" in pkg || "netease" in pkg || "cloudmusic" in pkg -> neteaseArtistUrl(sourceUrl)
            "kuwo" in pkg -> kuwoArtistUrl(sourceUrl)
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

    private fun parseCollaboratingArtists(rawArtistString: String?): List<String> {
        if (rawArtistString.isNullOrBlank()) return emptyList()
        return splitArtists(rawArtistString)
    }

    private fun youtubeArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl?.let { YouTubeResolver.unwrapYmusicUrl(it) } ?: return null
        if (!YouTubeResolver.isYoutubeFamilyUrl(url)) return null
        // Reuse the canonical resolver so /@handle, /c/, /user/ and /channel/UC...
        // all map to a real, clickable channel url (the @ must be kept).
        YouTubeResolver.extractChannelUrl(url)?.let { return it }
        return null
    }

    private fun soundcloudArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("soundcloud.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) return "https://soundcloud.com/${segments[0]}"
        } catch (_: Exception) {}
        return null
    }

    private fun spotifyArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("spotify.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                when (segments[0]) {
                    "artist", "album", "playlist" -> return "https://open.spotify.com/${segments[0]}/${segments[1]}"
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun bandcampArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("bandcamp.com")) return null
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            if (host.endsWith("bandcamp.com") && host != "bandcamp.com") return "https://$host"
        } catch (_: Exception) {}
        return null
    }

    private fun deezerArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("deezer.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://www.deezer.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun appleMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.apple.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val artistId = segments[artistIndex + 1]
                return "https://music.apple.com/us/artist/$artistId"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun amazonMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("amazon.com") && !url.contains("amazon.co.jp")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artists")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val domain = uri.host ?: "music.amazon.com"
                return "https://$domain/artists/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun streetVoiceArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("streetvoice.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val username = segments[0]
                if (username !in listOf("music", "discovery", "activity")) return "https://streetvoice.com/$username"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun kkboxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kkbox.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val prefix = if (artistIndex >= 2) "/${segments[0]}/${segments[1]}" else ""
                return "https://www.kkbox.com$prefix/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun nicoBoxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("nicovideo.jp") && !url.contains("nico.ms")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val host = uri.host ?: ""
            // Watch video URL — return as-is
            if (segments.size >= 2 && segments[0] == "watch") {
                return url
            }
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

    private fun lineMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.line.me") && !url.contains("music-tw.line.me")) return null
        try {
            val uri = java.net.URI(url)
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

    private fun tidalArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("tidal.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://tidal.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun jooxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("joox.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                return "https://www.joox.com/artist/${segments[artistIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun kugouArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kugou.com")) return null
        try {
            val uri = java.net.URI(url)
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotEmpty() }
            // kugou.com/singer/{id}/ or kugou.com/{id}/
            val singerIndex = segments.indexOf("singer")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                return "https://www.kugou.com/singer/${segments[singerIndex + 1]}/"
            }
            val lastSegment = segments.lastOrNull() ?: ""
            if (lastSegment.endsWith("/") && lastSegment.length > 1) {
                val id = lastSegment.substringBefore("/")
                if (id.isNotBlank()) return "https://www.kugou.com/${id}/"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun qqMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("qq.com") && !url.contains("y.qq.com")) return null
        try {
            val uri = java.net.URI(url)
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotEmpty() }
            val singerIndex = segments.indexOf("singer")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                val mid = segments[singerIndex + 1].substringBefore(".html")
                if (mid.isNotBlank()) return "https://y.qq.com/n/yqq/singer/${mid}.html"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun douyinMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("douyin.com")) return null
        try {
            val uri = java.net.URI(url)
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val code = segments.lastOrNull() ?: ""
                if (code.isNotBlank()) return "https://www.douyin.com/${code}/"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun neteaseArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("163.com") && !url.contains("163cn.tv")) return null
        try {
            val uri = java.net.URI(url)
            val query = uri.query ?: ""
            val artistIdParam = query.split("&").find { it.startsWith("id=") }?.substringAfter("id=")
            if (artistIdParam != null && artistIdParam.isNotBlank()) {
                return "https://music.163.com/#/artist?id=${artistIdParam}"
            }
            val path = uri.path ?: ""
            val segments = path.split("/").filter { it.isNotEmpty() }
            val lastSegment = segments.lastOrNull() ?: ""
            if (lastSegment.isNotBlank() && lastSegment.matches(Regex("[0-9A-Za-z]{5,}"))) {
                return "https://music.163.com/#/artist?id=${lastSegment}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun kuwoArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("kuwo.cn")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val singerIndex = segments.indexOf("singer_detail")
            if (singerIndex != -1 && segments.size > singerIndex + 1) {
                return "https://kuwo.cn/singer_detail/${segments[singerIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    private fun String.isValidMediaUrl(): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}

private fun archiveTuneArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("archive.org")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val detailsIndex = segments.indexOf("details")
            if (detailsIndex != -1 && segments.size > detailsIndex + 1) {
                return "https://archive.org/details/${segments[detailsIndex + 1]}"
            }
        } catch (_: Exception) {}
        return null
    }
}
