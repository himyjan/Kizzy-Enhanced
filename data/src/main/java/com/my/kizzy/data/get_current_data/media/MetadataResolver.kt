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
        // Prefer real album art, then generic art, then the DISPLAY_ICON bitmap. YouTube commonly
        // ships its thumbnail as the DISPLAY_ICON bitmap and sets neither ALBUM_ART nor ART, so
        // without this last fallback the presence had no image and Discord showed the Kizzy logo.
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val displayIcon = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        logger.d("MetadataResolver", "getCoverArt: title='${metadata.getString(MediaMetadata.METADATA_KEY_TITLE)}', albumArt=${albumArt != null}, art=${art != null}, displayIcon=${displayIcon != null}")

        return albumArt
            ?: art
            ?: displayIcon
    }

    /**
     * A URL to the artwork/thumbnail when no embedded bitmap is present. Apps like YouTube
     * do not populate the ALBUM_ART/ART bitmap keys but do expose a thumbnail URL through
     * the *_URI keys, so reading these lets the presence show a real image (the video
     * thumbnail) instead of Discord's default app icon.
     */
    fun getArtworkUri(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        )
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty()) {
                logger.d("MetadataResolver", "getArtworkUri: found ${key.substringAfterLast('.')} = $value")
                return value
            }
        }
        logger.d("MetadataResolver", "getArtworkUri: no artwork URI found in ALBUM_ART_URI, ART_URI, or DISPLAY_ICON_URI")
        return null
    }

    fun getArtistOrAuthor(metadata: MediaMetadata): String? {
        return if (!metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_ARTIST
        ) else if (!metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR).isNullOrEmpty()) metadata.getString(
            MediaMetadata.METADATA_KEY_AUTHOR
        ) else null
    }

    /**
     * Returns all collaborating artists from the media metadata.
     * YouTube and other players often report multiple artists in METADATA_KEY_ARTIST
     * separated by ", ", " & ", " ft. ", " featuring ", or " x ".
     */
    fun getAllArtists(metadata: MediaMetadata): List<String> {
        val artist = getArtistOrAuthor(metadata) ?: return emptyList()
        return splitArtists(artist)
    }

    /**
     * Returns the primary (first) artist from the metadata.
     */
    fun getPrimaryArtist(metadata: MediaMetadata): String? {
        return getAllArtists(metadata).firstOrNull()
    }

    /**
     * Formats all artists for display in Discord Rich Presence.
     * Joins collaborating artists with ", " and truncates if too long.
     */
    fun formatArtists(metadata: MediaMetadata, maxLength: Int = 128): String {
        val artists = getAllArtists(metadata)
        return if (artists.isEmpty()) ""
        else if (artists.size == 1) artists[0]
        else artists.joinToString(", ").take(maxLength)
    }

    /**
     * Splits a combined artist string into individual artists using common separators
     * used by YouTube and other music players for collaborating artists.
     */
    private fun splitArtists(artist: String): List<String> {
        // Common separators used by YouTube Music and other players for multi-artist tracks
        val separators = listOf(" ft. ", " featuring ", " x ", " & ", ", ", "; ")
        var result = listOf(artist)
        for (sep in separators) {
            result = result.flatMap { it.split(sep) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (result.size > 1) break // Use the first separator that produces multiple artists
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

    /**
     * Detects multi-channel audio (surround sound) from media metadata.
     * Some players report audio channel info in custom keys or the album field.
     * Returns a human-readable channel description like "5.1", "7.1", "Dolby Atmos", etc.
     */
    fun getAudioChannels(metadata: MediaMetadata): String? {
        // Check common custom keys used by YouTube Music and other players
        val channelKeys = listOf(
            "audio_channels",
            "audio_channel_count",
            "channel_count",
            "surround",
            "audio_format"
        )
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

        // Check if album or genre field contains channel info (some players put it there)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val channelMatch = Regex("""(5\.1|7\.1|2\.0|stereo|surround|atmos)""", RegexOption.IGNORE_CASE)
            .find(album)
        return channelMatch?.value?.replaceFirstChar { it.uppercase() }
    }

    /**
     * Extracts the media URL from media metadata.
     * Supports YouTube, Spotify, Apple Music, Deezer, SoundCloud, and other
     * players that expose source URLs or watch links in metadata.
     * Returns null if no playable URL is found.
     */
    fun getMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        // YouTube apps: extract the video ID from METADATA_KEY_MEDIA_ID first
        // (e.g. "yt:video:VIDEO_ID" or "t:VIDEO_ID") so we link directly to
        // https://youtu.be/VIDEO_ID instead of a search page. sourceUrl often
        // contains a search page URL which would be returned as-is.
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { return normalizeMediaUrl(it, packageName) }
        }

        // Try standard source URL key first
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (sourceUrl != null && sourceUrl.isValidMediaUrl()) {
            return normalizeMediaUrl(sourceUrl, packageName)
        }

        // Try common custom keys used by various players
        val customKeys = listOf(
            "youtube_url",
            "video_url",
            "source_url",
            "media_url",
            "content_url",
            "spotify_url",
            "apple_music_url",
            "deezer_url",
            "soundcloud_url"
        )
        for (key in customKeys) {
            val value = metadata.getString(key)
            if (value != null && value.isValidMediaUrl()) {
                return normalizeMediaUrl(value, packageName)
            }
        }

        // Some players publish the url under a key nobody knows. The whole
        // metadata is scanned for a url, so the real link of the track is found
        // even when the player uses its own key.
        scanAllMetadataForUrl(metadata)?.let { return normalizeMediaUrl(it, packageName) }

        // YouTube apps publish the real watch url, so it is preferred over a
        // search page.
        if (MediaServiceResolver.isYouTube(packageName)) {
            extractYouTubeUrl(metadata)?.let { return normalizeMediaUrl(it, packageName) }
        }

        // Fall back to the page of the service that lists the track.
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        return MediaServiceResolver.mediaSearchUrl(packageName, title)
    }

    /**
     * The direct url of the track, when it can be found.
     *
     * The media session of YouTube Music does not publish the url of the
     * video, so the link of the button used to be a search page. The video is
     * looked up by its id (YouTube Music ships it in the media id) or by its
     * title, and the direct watch url is built from it.
     *
     * Returns null when the url cannot be found, so the caller can fall back
     * to the search page.
     */
    suspend fun getDirectMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
        if (!MediaServiceResolver.isYouTube(packageName)) return null

        // The id of the video, when the session ships it.
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            val id = if (mediaId.contains("youtube.com/watch?v=") || mediaId.contains("youtu.be/")) {
                YouTubeResolver.extractVideoId(mediaId)
            } else {
                mediaId.substringAfterLast(":").substringAfterLast("/")
                    .substringBefore('?').substringBefore('&')
                    .takeIf { it.isNotBlank() && it.length >= 11 }
                    ?.take(11)
            }
            if (id != null) {
                return "https://youtu.be/$id"
            }
        }

        // The video of the track, looked up by its title.
        val video = resolveYouTubeVideo(metadata) ?: return null
        return video.watchUrl
    }

    /**
     * The video of the playing track, when the app is a YouTube app.
     *
     * The url of the video is used when the session publishes it, otherwise the
     * video is looked up by its title.
     */
    suspend fun resolveYouTubeVideo(metadata: MediaMetadata): YouTubeResolver.Video? {
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() &&
            (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be"))
        ) {
            YouTubeResolver.lookup(sourceUrl)?.let { return it }
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        if (title.isNullOrEmpty()) return null
        val artist = metadataResolverArtistName(metadata)

        return YouTubeResolver.lookupByTitle(title, artist)
    }

    /**
     * Looks through every entry of the metadata for something that looks like
     * a url of a media service.
     *
     * The metadata of a media session is a bundle, but the bundle cannot be
     * read from the outside, so the well known keys are checked one by one.
     */
    private fun scanAllMetadataForUrl(metadata: MediaMetadata): String? {
        val keys = listOf(
            MediaMetadata.METADATA_KEY_TITLE,
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_AUTHOR,
            MediaMetadata.METADATA_KEY_GENRE,
            MediaMetadata.METADATA_KEY_MEDIA_ID,
        )
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty() && value.isValidMediaUrl()) {
                logger.d("MetadataResolver", "Found media url in metadata key '$key'")
                return value
            }
        }
        return null
    }

    /**
     * Brings a url into the form the user expects.
     *
     * Every YouTube url becomes the short "youtu.be/<video id>" form, so the
     * button links to the video itself instead of a long query url.
     */
    private fun normalizeMediaUrl(url: String, packageName: String?): String {
        val trimmed = url.trim()
        // Do not alter browse links or playlist links into single-video short links
        if (trimmed.contains("/browse/") || trimmed.contains("list=")) {
            return trimmed
        }
        if (!MediaServiceResolver.isYouTube(packageName) &&
            !trimmed.contains("youtube.com") && !trimmed.contains("youtu.be")
        ) {
            return trimmed
        }
        val videoId = YouTubeResolver.extractVideoId(trimmed) ?: return trimmed
        return "https://youtu.be/$videoId"
    }

    /**
     * Extracts a YouTube URL from media metadata. Handles all YouTube URL formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://www.youtube.com/watch?v=VIDEO_ID&list=PLAYLIST_ID
     * - https://music.youtube.com/watch?v=VIDEO_ID
     * - https://music.youtube.com/playlist?list=PLAYLIST_ID
     * - https://www.youtube.com/playlist?list=PLAYLIST_ID
     * - https://youtu.be/VIDEO_ID?t=SECONDS
     * - https://www.youtube.com/live/VIDEO_ID
     */
    private fun extractYouTubeUrl(metadata: MediaMetadata): String? {
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)

        // Try to extract video ID from METADATA_KEY_MEDIA_ID FIRST.
        // YouTube Music ships media ids like "yt:video:VIDEO_ID" or "t:VIDEO_ID".
        // This gives the direct watch URL https://youtu.be/VIDEO_ID instead of
        // a search page. METADATA_KEY_SOURCE_URL often contains a search page
        // (e.g. "youtube.com/results?search_query=..."), so it must not be
        // preferred over the real video id.
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty()) {
            // Full watch url
            if (mediaId.contains("youtube.com/watch?v=")) {
                return mediaId
            }
            // youtu.be/VIDEO_ID
            if (mediaId.contains("youtu.be/")) {
                return mediaId
            }
            // yt:video:VIDEO_ID or t:VIDEO_ID
            val id = mediaId.substringAfterLast(":").substringAfterLast("/").substringBefore('?').substringBefore('&')
            if (id.isNotBlank() && id.length >= 11) {
                return "https://youtu.be/$id"
            }
        }

        // Check METADATA_KEY_SOURCE_URL for any YouTube URL, but skip search
        // pages - they don't link to a specific video.
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() && (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be"))) {
            if (!sourceUrl.contains("/results?search_query")) {
                return sourceUrl
            }
        }

        // Check custom keys that might contain YouTube URLs
        val youtubeKeys = listOf(
            "youtube_url",
            "video_url",
            "source_url",
            "media_url",
            "content_url",
            "yt_url",
            "youtube_video_url",
            "youtube_watch_url"
        )
        for (key in youtubeKeys) {
            val value = metadata.getString(key)
            if (!value.isNullOrEmpty() && (value.contains("youtube.com") || value.contains("youtu.be"))) {
                if (!value.contains("/results?search_query")) {
                    return value
                }
            }
        }

        // If we have a title but no URL, build a search URL (fallback)
        if (!title.isNullOrEmpty()) {
            return "https://www.youtube.com/results?search_query=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
        }

        return null
    }

    private fun String.isValidMediaUrl(): Boolean {
        return contains("youtube.com") ||
            contains("music.youtube.com") || // YouTube Music domain explicitly
            contains("youtu.be") ||
            contains("streetvoice.com") ||
            contains("ymusicapp.com") ||
            contains("open.spotify.com") ||
            contains("music.apple.com") ||
            contains("deezer.com") ||
            contains("soundcloud.com") ||
            contains("spotify.com") ||
            contains("itunes.apple.com")
    }

    /**
     * Extracts an artist channel URL from media metadata.
     *
     * For YouTube the real channel of the uploader is used, which is read from
     * the oEmbed document of the video. Guessing the handle from the name of
     * the artist ("youtube.com/@Rick Astley") produced a url that does not
     * exist, so the artist button led nowhere.
     *
     * Returns null if no artist is known.
     */
    suspend fun getArtistUrl(metadata: MediaMetadata, packageName: String?): String? {
        val artist = metadataResolverArtistName(metadata)
        if (artist.isNullOrEmpty()) return null

        // Detect multi-artist (collaboration) tracks from both the structured
        // metadata (getAllArtists) and the raw artist string
        // (parseCollaboratingArtists). Collaborations have no single channel,
        // so they fall back to a search page instead of a direct artist link.
        val artists = getAllArtists(metadata)
        val rawArtistString = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val parsedCollaborators = parseCollaboratingArtists(rawArtistString)
        val isMultiArtist = artists.size > 1 || parsedCollaborators.size > 1

        // Comprehensive multi-platform URL parser for RPC and artist links.
        // Derives the artist URL directly from the track URL the session
        // publishes (METADATA_KEY_SOURCE_URL), instead of falling back to a
        // search page or guessing a handle from the artist name.
        val pkg = packageName?.lowercase().orEmpty()
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)

        val parsedArtistUrl = when {
            // 1. SoundCloud
            "soundcloud" in pkg -> soundcloudArtistUrl(sourceUrl)

            // 2. Spotify
            "spotify" in pkg -> spotifyArtistUrl(sourceUrl)

            // 3. Bandcamp
            "bandcamp" in pkg -> bandcampArtistUrl(sourceUrl)

            // 4. YouTube, YouTube Music & Open-Source Clients
            //    (SimpMusic, ViMusic, InnerTune, YMusic)
            "youtube" in pkg || "ymusic" in pkg || "simpmusic" in pkg ||
            "vimusic" in pkg || "innertune" in pkg -> youtubeArtistUrl(sourceUrl)

            // 5. Spotube (Spotify UI + YouTube backend)
            "spotube" in pkg -> if (sourceUrl?.contains("spotify.com") == true) {
                spotifyArtistUrl(sourceUrl)
            } else {
                youtubeArtistUrl(sourceUrl)
            }

            // 6. Deezer
            "deezer" in pkg -> deezerArtistUrl(sourceUrl)

            // 7. Apple Music
            "apple" in pkg -> appleMusicArtistUrl(sourceUrl)

            // 8. Amazon Music
            "amazon" in pkg -> amazonMusicArtistUrl(sourceUrl)

            // 9. StreetVoice
            "streetvoice" in pkg -> streetVoiceArtistUrl(sourceUrl)

            // 10. KKBOX
            "kkbox" in pkg -> kkboxArtistUrl(sourceUrl)

            // 11. NicoBox / Niconico
            "nicobox" in pkg || "nicovideo" in pkg -> nicoBoxArtistUrl(sourceUrl)

            // 12. LINE MUSIC
            "linemusic" in pkg || "linecorp.linemusic" in pkg -> lineMusicArtistUrl(sourceUrl)

            // 13. ArchiveTune / Internet Archive
            "archivetune" in pkg || "archive.org" in pkg -> archiveTuneArtistUrl(sourceUrl)

            // 14. LX Music / YesPlayMusic
            "lxmusic" in pkg || "yesplaymusic" in pkg -> null

            else -> null
        }

        if (parsedArtistUrl != null) return parsedArtistUrl

        // For YouTube single-artist tracks the real channel comes from oEmbed
        // (@handle or /channel/UCxxxx). Guessing "@artistName" does not work
        // because the artist name is not the channel handle.
        // Multi-artist (collaboration) videos fall back to a search page since
        // there is no single channel.
        if (MediaServiceResolver.isYouTube(packageName) && !isMultiArtist) {
            val video = resolveYouTubeVideo(metadata)
            if (video != null && video.channelUrl.isNotBlank()) {
                return video.channelUrl
            }
        }

        // The page of the service that belongs to the artist.
        // For multi-artist collaborations, build a combined search query
        // from all parsed artists instead of passing the raw string
        // ("Aimer feat. milet" -> "Aimer milet").
        if (isMultiArtist && parsedCollaborators.isNotEmpty()) {
            val combinedQuery = parsedCollaborators.joinToString(" ")
            if (combinedQuery.isNotBlank()) {
                return MediaServiceResolver.artistUrl(packageName, combinedQuery)
            }
        }
        return MediaServiceResolver.artistUrl(packageName, artist)
    }

    /**
     * Derives a SoundCloud artist URL from a track URL.
     *
     * "https://soundcloud.com/artist-name/track-name" ->
     *   "https://soundcloud.com/artist-name"
     *
     * Returns null when the URL is not a SoundCloud track URL.
     */
    private fun soundcloudArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("soundcloud.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            // soundcloud.com/<artist>[/<track>] -> need at least the artist segment
            if (segments.isNotEmpty()) return "https://soundcloud.com/${segments[0]}"
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives a Spotify artist/album/playlist URL from a track/artist/album/playlist URL.
     *
     * "https://open.spotify.com/artist/XXXX" ->
     *   "https://open.spotify.com/artist/XXXX"
     * "https://open.spotify.com/album/XXXX" ->
     *   "https://open.spotify.com/album/XXXX"
     * "https://open.spotify.com/playlist/XXXX" ->
     *   "https://open.spotify.com/playlist/XXXX"
     */
    private fun spotifyArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("spotify.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2) {
                when (segments[0]) {
                    "artist", "album", "playlist" -> {
                        return "https://open.spotify.com/${segments[0]}/${segments[1]}"
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives a Bandcamp artist URL from a track/album URL.
     *
     * "https://artistname.bandcamp.com/track/..." ->
     *   "https://artistname.bandcamp.com"
     */
    private fun bandcampArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("bandcamp.com")) return null
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            if (host.endsWith("bandcamp.com") && host != "bandcamp.com") {
                return "https://$host"
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives a YouTube channel/playlist URL from a video/channel/playlist URL.
     *
     * "https://www.youtube.com/@handle" ->
     *   "https://www.youtube.com/@handle"
     * "https://www.youtube.com/channel/UCxxxx" ->
     *   "https://www.youtube.com/channel/UCxxxx"
     * "https://www.youtube.com/playlist?list=PLxxx" ->
     *   "https://www.youtube.com/playlist?list=PLxxx"
     * "https://www.youtube.com/watch?v=ID&list=PLxxx" ->
     *   "https://www.youtube.com/playlist?list=PLxxx"
     */
    private fun youtubeArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("youtube.com") && !url.contains("youtu.be")) return null
        try {
            val uri = java.net.URI(url)
            val path = uri.path ?: return null
            val query = uri.query ?: ""

            // 1. Handle Playlists and Browse/Album links
            //    (e.g., youtube.com/playlist?list=PLxxx or music.youtube.com/browse/xxxxxxxxx)
            if (path.contains("playlist") || path.contains("browse") || query.contains("list=")) {
                val listParam = query.split("&")
                    .map { it.split("=") }
                    .find { it.size == 2 && it[0] == "list" }
                if (listParam != null) {
                    return "https://www.youtube.com/playlist?list=${listParam[1]}"
                }

                // If it's a browse URL (like an album browse link), preserve it
                if (path.contains("browse")) {
                    return url
                }
            }

            // 2. Handle Handles (/@handle)
            if (path.startsWith("/@")) {
                val handle = path.split("/").filter { it.isNotEmpty() }.getOrNull(0)
                if (handle != null) return "https://www.youtube.com/$handle"
            }

            // 3. Handle Traditional Channels (/channel/ or /c/)
            if (path.startsWith("/channel/")) {
                val segments = path.split("/").filter { it.isNotEmpty() }
                if (segments.size >= 2) return "https://www.youtube.com/channel/${segments[1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives a Deezer artist URL from a track/album URL.
     *
     * "https://www.deezer.com/track/..." or ".../album/..." ->
     *   does NOT contain an artist segment -> null
     * "https://www.deezer.com/artist/XXXX" ->
     *   "https://www.deezer.com/artist/XXXX"
     */
    private fun deezerArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("deezer.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size >= 2 && segments[0] == "artist") {
                return "https://www.deezer.com/artist/${segments[1]}"
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives an Apple Music artist/album/playlist URL from a track URL.
     *
     * "https://music.apple.com/us/artist/NAME/idXXXX" ->
     *   "https://music.apple.com/us/artist/NAME/idXXXX"
     * "https://music.apple.com/us/album/NAME/idXXXX" ->
     *   "https://music.apple.com/us/album/NAME/idXXXX"
     * "https://music.apple.com/us/playlist/NAME/idXXXX" ->
     *   "https://music.apple.com/us/playlist/NAME/idXXXX"
     */
    private fun appleMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.apple.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.size >= 4) {
                val country = segments[0]
                val type = segments[1] // artist, album, playlist
                if (type in listOf("artist", "album", "playlist")) {
                    return "https://music.apple.com/$country/$type/${segments[2]}/${segments[3]}"
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives an Amazon Music artist URL from a track URL.
     *
     * "https://music.amazon.com/artists/XXXX..." ->
     *   "https://music.amazon.com/artists/XXXX"
     */
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

    /**
     * Derives a StreetVoice artist URL from a track URL.
     *
     * "https://streetvoice.com/username/track/..." ->
     *   "https://streetvoice.com/username"
     */
    private fun streetVoiceArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("streetvoice.com")) return null
        try {
            val uri = java.net.URI(url)
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            if (segments.isNotEmpty()) {
                val username = segments[0]
                if (username !in listOf("music", "discovery", "activity")) {
                    return "https://streetvoice.com/$username"
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives a KKBOX artist URL from a track/album URL.
     *
     * "https://www.kkbox.com/tw/artist/XXXX" ->
     *   "https://www.kkbox.com/tw/artist/XXXX"
     */
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

    /**
     * Derives a NicoBox / Niconico artist (channel) URL from a video URL.
     *
     * "https://ch.nicovideo.jp/username" ->
     *   "https://ch.nicovideo.jp/username"
     * "https://www.nicovideo.jp/user/XXXX" ->
     *   "https://www.nicovideo.jp/user/XXXX"
     */
    private fun nicoBoxArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("nicovideo.jp") && !url.contains("nico.ms")) return null
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
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

    /**
     * Derives a LINE MUSIC artist URL from a track URL.
     *
     * "https://music.line.me/webapp/artist/XXXX" ->
     *   "https://music.line.me/webapp/artist/XXXX"
     * "https://music-tw.line.me/artist/XXXX" ->
     *   "https://music-tw.line.me/artist/XXXX"
     */
    private fun lineMusicArtistUrl(sourceUrl: String?): String? {
        val url = sourceUrl ?: return null
        if (!url.contains("music.line.me") && !url.contains("music-tw.line.me")) return null
        try {
            val uri = java.net.URI(url)
            val host = uri.host ?: return null
            val segments = (uri.path ?: "").split("/").filter { it.isNotEmpty() }
            val artistIndex = segments.indexOf("artist")
            if (artistIndex != -1 && segments.size > artistIndex + 1) {
                val artistId = segments[artistIndex + 1]
                return if (host.contains("music-tw")) "https://music-tw.line.me/artist/$artistId" else "https://music.line.me/webapp/artist/$artistId"
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Derives an ArchiveTune / Internet Archive artist URL from an item URL.
     *
     * "https://archive.org/details/ITEM_ID" ->
     *   "https://archive.org/details/ITEM_ID"
     */
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

    private fun metadataResolverArtistName(metadata: MediaMetadata): String? {
        return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotEmpty() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotEmpty() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotEmpty() }
    }

    /**
     * Parses a track's artist metadata string into a clean list of individual artist names.
     *
     * Handles common collaboration keywords, symbols, and separators:
     * - "feat.", "featuring", "ft", "with", "vs", "and"
     * - " & ", " x ", " × ", ","
     *
     * Examples:
     *   "Aimer feat. milet"        -> ["Aimer", "milet"]
     *   "Orangestar & IA"          -> ["Orangestar", "IA"]
     *   "Artist A x Artist B"      -> ["Artist A", "Artist B"]
     *   "Artist 1, Artist 2, Artist 3" -> ["Artist 1", "Artist 2", "Artist 3"]
     */
    private fun parseCollaboratingArtists(artistMetadata: String?): List<String> {
        if (artistMetadata.isNullOrBlank()) return emptyList()

        // Normalize common collaboration markers and symbols into a standard delimiter ('|')
        val normalized = artistMetadata
            // Match words like feat, featuring, ft, with, vs, and (case-insensitive, optional dots)
            .replace(Regex("(?i)\\b(feat\\.?|featuring|ft\\.?|with|vs\\.?|and)\\b"), "|")
            // Match common collaborative symbols
            .replace(" & ", "|")
            .replace(" x ", "|")
            .replace(" × ", "|")
            .replace(",", "|")

        // Split by the pipe delimiter, trim whitespace, and drop empty entries
        return normalized.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
