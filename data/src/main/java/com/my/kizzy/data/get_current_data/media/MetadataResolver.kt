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

    fun getMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { return normalizeMediaUrl(it, packageName) }
        }
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
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
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { url ->
                normalizeMediaUrl(url, packageName)?.let { return it }
            }
            return null
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
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (sourceUrl != null && sourceUrl.isValidMediaUrl()) {
            val url = resolveAlbumUrl(sourceUrl, packageName)
            if (url != null) return url
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

        // YouTube / YouTube Music track URLs
        if (lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("ymusicapp.com")) {
            val videoId = YouTubeResolver.extractVideoId(trimmed)
            if (videoId != null) return "https://youtu.be/$videoId"
            return null
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

        // YouTube playlist handled by extractPlaylistId
        if (lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("ymusicapp.com")) {
            val playlistId = YouTubeResolver.extractPlaylistId(trimmed)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
        }

        return null
    }

    suspend fun getDirectMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            // Full watch url or youtu.be — extract just the video id
            // Also handle ymusicapp.com/watch?v=ID (YMusic app publishes full URLs)
            if (mediaId.contains("youtube.com/watch?v=") || mediaId.contains("youtu.be/") || mediaId.contains("ymusicapp.com/watch?v=")) {
                val id = YouTubeResolver.extractVideoId(mediaId)
                if (id != null) return "https://youtu.be/$id"
            }
            // Channel URL — keep as a channel link
            val channelId = YouTubeResolver.extractChannelId(mediaId)
            if (channelId != null) return "https://www.youtube.com/channel/$channelId"
            // Playlist URL — keep as a playlist link
            val playlistId = YouTubeResolver.extractPlaylistId(mediaId)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
            // yt:video:VIDEO_ID or t:VIDEO_ID
            val id = mediaId.substringAfterLast(":").substringAfterLast("/")
                .substringBefore('?').substringBefore('&')
                .takeIf { it.isNotBlank() && it.length >= 11 }?.take(11)
            if (id != null) return "https://youtu.be/$id"
            val rawId = mediaId.trim()
            if (rawId.length == 11 && rawId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                return "https://youtu.be/$rawId"
            }
            YouTubeResolver.extractVideoId(mediaId)?.let { return "https://youtu.be/$it" }
        }
        val video = resolveYouTubeVideo(metadata) ?: return null
        return video.watchUrl
    }

    suspend fun resolveYouTubeVideo(metadata: MediaMetadata): YouTubeResolver.Video? {
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() && (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be") || sourceUrl.contains("ymusicapp.com"))) {
            YouTubeResolver.lookup(sourceUrl)?.let { return it }
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        if (title.isNullOrEmpty()) return null
        val artist = MetadataResolverArtistName(metadata)
        return YouTubeResolver.lookupByTitle(title, artist)
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
        if (trimmed.contains("/browse/")) return trimmed
        // Do not return early for list= (playlist parameter) — extract just the video id
        // so Discord opens the single video, not a playlist that may auto-play a different track.
        if (!MediaServiceResolver.isYouTube(packageName) &&
            !trimmed.contains("youtube.com") && !trimmed.contains("youtu.be") && !trimmed.contains("ymusicapp.com")) return trimmed
        // Try video id first
        val videoId = YouTubeResolver.extractVideoId(trimmed)
        if (videoId != null) return "https://youtu.be/$videoId"
        // Try channel id — keep as a channel link, not a video link
        val channelId = YouTubeResolver.extractChannelId(trimmed)
        if (channelId != null) return "https://www.youtube.com/channel/$channelId"
        // Try playlist id — keep as a playlist link
        val playlistId = YouTubeResolver.extractPlaylistId(trimmed)
        if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
        return null
    }

    private fun extractYouTubeUrl(metadata: MediaMetadata): String? {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            // Full watch url or youtu.be — extract just the video id
            // Also handle ymusicapp.com/watch?v=ID (YMusic app publishes full URLs)
            if (mediaId.contains("youtube.com/watch?v=") || mediaId.contains("youtu.be/") || mediaId.contains("ymusicapp.com/watch?v=")) {
                return YouTubeResolver.extractVideoId(mediaId)?.let { "https://youtu.be/$it" }
            }
            // Channel URL — keep as a channel link, not a video link
            val channelId = YouTubeResolver.extractChannelId(mediaId)
            if (channelId != null) return "https://www.youtube.com/channel/$channelId"
            // Playlist URL — keep as a playlist link
            val playlistId = YouTubeResolver.extractPlaylistId(mediaId)
            if (playlistId != null) return "https://www.youtube.com/playlist?list=$playlistId"
            // yt:video:VIDEO_ID or t:VIDEO_ID
            val id = mediaId.substringAfterLast(":").substringAfterLast("/").substringBefore('?').substringBefore('&')
            if (id.isNotBlank() && id.length >= 11) return "https://youtu.be/$id"
            // Raw video ID (11 chars, alphanumeric/_/-) from modded YouTube apps
            val rawId = mediaId.trim()
            if (rawId.length == 11 && rawId.matches(Regex("[A-Za-z0-9_-]{11}"))) return "https://youtu.be/$rawId"
            // Fallback: try YouTubeResolver.extractVideoId for any URL format
            val resolvedId = YouTubeResolver.extractVideoId(mediaId)
            if (resolvedId != null) return "https://youtu.be/$resolvedId"
        }
        // Check METADATA_KEY_SOURCE_URL for any YouTube URL
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() && (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be") || sourceUrl.contains("ymusicapp.com"))) {
            if (!sourceUrl.contains("/results?search_query")) {
                // Try video id first
                YouTubeResolver.extractVideoId(sourceUrl)?.let { return "https://youtu.be/$it" }
                // Then channel id
                YouTubeResolver.extractChannelId(sourceUrl)?.let { return "https://www.youtube.com/channel/$it" }
                // Then playlist id
                YouTubeResolver.extractPlaylistId(sourceUrl)?.let { return "https://www.youtube.com/playlist?list=$it" }
            }
        }
        // Check custom keys that might contain YouTube URLs
        val youtubeKeys = listOf("youtube_url", "video_url", "source_url", "media_url", "content_url",
            "yt_url", "youtube_video_url", "youtube_watch_url")
        for (key in youtubeKeys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty() && (value.contains("youtube.com") || value.contains("youtu.be") || value.contains("ymusicapp.com"))) {
                if (!value.contains("/results?search_query")) {
                    // Try video id first
                    YouTubeResolver.extractVideoId(value)?.let { return "https://youtu.be/$it" }
                    // Then channel id
                    YouTubeResolver.extractChannelId(value)?.let { return "https://www.youtube.com/channel/$it" }
                    // Then playlist id
                    YouTubeResolver.extractPlaylistId(value)?.let { return "https://www.youtube.com/playlist?list=$it" }
                }
            }
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
        val url = sourceUrl ?: return null
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2 && segments[0] == "channel") return "https://www.youtube.com/channel/${segments[1]}"
            if (segments.size >= 2 && segments[0] == "c") return "https://www.youtube.com/c/${segments[1]}"
            if (segments.size >= 2 && segments[0] == "@") return "https://www.youtube.com/${segments[1]}"
            val videoId = YouTubeResolver.extractVideoId(url)
            if (videoId != null) return "https://www.youtube.com/channel/$videoId"
        } catch (_: Exception) {}
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
