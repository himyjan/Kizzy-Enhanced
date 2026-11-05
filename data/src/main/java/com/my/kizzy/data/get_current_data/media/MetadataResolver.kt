/*

 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MetadataResolver.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************

*/

package com.my.kizzy.data.get_current_data.media

import android.graphics.Bitmap
import android.media.MediaMetadata
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class MetadataResolver @Inject constructor() {
    fun getCoverArt(metadata: MediaMetadata): Bitmap? {
        return if (metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null) metadata.getBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART
        )
        else metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
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
        val sourceUrl = metadata.getString(MediaMetadata.METADATA_KEY_SOURCE_URL)
        if (!sourceUrl.isNullOrEmpty() && isValidMediaUrl(sourceUrl)) {
            return sourceUrl
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
            if (!value.isNullOrEmpty() && isValidMediaUrl(value)) {
                return value
            }
        }

        // Build URL from package name if we know the app's URL pattern
        packageName?.let { pkg ->
            return when {
                pkg.contains("youtube") || pkg.contains("youtubemusic") -> {
                    // First, try to extract any YouTube URL from metadata keys
                    val youtubeUrl = extractYouTubeUrl(metadata)
                    if (!youtubeUrl.isNullOrEmpty()) {
                        return youtubeUrl
                    }
                    // Fall back to search URL using video title
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    if (!title.isNullOrEmpty()) {
                        "https://www.youtube.com/results?search_query=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                    } else null
                }
                pkg == "com.spotify.music" -> {
                    // Spotify: try to find track ID in metadata
                    val albumUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                    if (!albumUri.isNullOrEmpty() && albumUri.contains("spotify")) {
                        "https://open.spotify.com/search/${URLEncoder.encode(albumUri, StandardCharsets.UTF_8.toString())}"
                    } else {
                        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                        if (!title.isNullOrEmpty()) {
                            "https://open.spotify.com/search/${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                        } else null
                    }
                }
                pkg.contains("apple") || pkg.contains("itunes") -> {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    if (!title.isNullOrEmpty()) {
                        "https://music.apple.com/search?term=${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                    } else null
                }
                pkg.contains("deezer") -> {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    if (!title.isNullOrEmpty()) {
                        "https://www.deezer.com/search/${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                    } else null
                }
                pkg.contains("soundcloud") -> {
                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    if (!title.isNullOrEmpty()) {
                        "https://soundcloud.com/search/${URLEncoder.encode(title, StandardCharsets.UTF_8.toString())}"
                    } else null
                }
                else -> null
            }
        }

        return null
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
        val sourceUrl = metadata.getString(MediaMetadata.METADATA_KEY_SOURCE_URL)
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

        // Try to extract video ID from METADATA_KEY_MEDIA_ID or similar
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        if (!mediaId.isNullOrEmpty() && mediaId.contains("youtube.com/watch?v=")) {
            return mediaId
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
            contains("open.spotify.com") ||
            contains("music.apple.com") ||
            contains("deezer.com") ||
            contains("soundcloud.com") ||
            contains("spotify.com") ||
            contains("itunes.apple.com")
    }
}
