package com.my.kizzy.data.get_current_data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.domain.interfaces.Logger

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
        return channelMatch?.value?.capitalize()
    }

    /**
     * Extracts the media URL from media metadata.
     * Supports YouTube, Spotify, Apple Music, Deezer, SoundCloud, and other
     * players that expose source URLs or watch links in metadata.
     * Returns null if no playable URL is found.
     */
    fun getMediaUrl(metadata: MediaMetadata, packageName: String?): String? {
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

        // Check METADATA_KEY_SOURCE_URL for any YouTube URL
        val sourceUrl = metadata.getString(METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() && (sourceUrl.contains("youtube.com") || sourceUrl.contains("youtu.be"))) {
            return sourceUrl
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
                return value
            }
        }

        // Try to extract video ID from METADATA_KEY_MEDIA_ID or similar.
        // YouTube Music ships media ids like "yt:video:VIDEO_ID" or "t:VIDEO_ID".
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

        // If we have a title but no URL, build a search URL (fallback)
        if (!title.isNullOrEmpty()) {
            return "https://www.youtube.com/results?search_query=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
        }

        return null
    }

    private fun String.isValidMediaUrl(): Boolean {
        return contains("youtube.com") ||
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

        // The real channel of the uploader, when the service publishes one.
        // For YouTube, extract the video id from the media id (e.g. "yt:video:VIDEO_ID")
        // and look it up in the oEmbed endpoint, which returns the real channel URL
        // (https://www.youtube.com/@handle or https://www.youtube.com/channel/UCxxxx).
        // Guessing "@artistName" does not work because the artist name is not the channel handle.
        if (MediaServiceResolver.isYouTube(packageName)) {
            val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            var videoId: String? = null
            if (!mediaId.isNullOrEmpty()) {
                if (mediaId.contains("youtube.com/watch?v=") || mediaId.contains("youtu.be/")) {
                    videoId = YouTubeResolver.extractVideoId(mediaId)
                } else {
                    val id = mediaId.substringAfterLast(":").substringAfterLast("/").substringBefore('?').substringBefore('&')
                    if (id.isNotBlank() && id.length >= 11) {
                        videoId = id.take(11)
                    }
                }
            }
            if (videoId.isNullOrBlank()) {
                // Fall back to extracting from the resolved media URL.
                val mediaUrl = getMediaUrl(metadata, packageName)
                videoId = mediaUrl?.let { YouTubeResolver.extractVideoId(it) }
            }
            videoId?.let { id ->
                val video = YouTubeResolver.lookupById(id)
                if (video != null && video.channelUrl.isNotBlank()) {
                    return video.channelUrl
                }
            }
        }

        // The page of the service that belongs to the artist.
        return MediaServiceResolver.artistUrl(packageName, artist)
    }

    private fun metadataResolverArtistName(metadata: MediaMetadata): String? {
        return metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotEmpty() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)?.takeIf { it.isNotEmpty() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotEmpty() }
    }
}
