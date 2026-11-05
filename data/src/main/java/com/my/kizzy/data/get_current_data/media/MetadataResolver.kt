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
        val channelMatch = Regex("""(5\.1|7\.1|2\.0|stereo|surround|atmos)""", RegexOption.IGNORECASE)
            .find(album)
        return channelMatch?.value?.capitalize()
    }
}
