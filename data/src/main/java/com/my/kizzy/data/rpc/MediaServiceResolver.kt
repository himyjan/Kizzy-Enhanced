/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * MediaServiceResolver.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Knows the media services the RPC is able to link to.
 *
 * The buttons of the presence link to the track and to the artist of the
 * playing media. The services are matched by their package name, because the
 * same service uses different packages on different devices (YouTube Music for
 * example is both "com.google..." and "app.morphe...").
 *
 * A *direct* url is always preferred over a search page: the user wants to land
 * on the track, not on a list of results. When the service publishes the real
 * url of the track in the media session, that url is used (see
 * [MetadataResolver.getMediaUrl]). When it does not, the service specific
 * search page is the fallback - and for YouTube the real watch url is resolved
 * through the oEmbed endpoint, so even a player that does not publish the url
 * ends up on the correct video.
 */
object MediaServiceResolver {

    enum class Kind {
        MUSIC,
        VIDEO,
        LIVE,
    }

    data class Service(
        val name: String,
        val kind: Kind,
        /** The "%s" is replaced with the url encoded title of the track. */
        val mediaUrlTemplate: String?,
        /** The "%s" is replaced with the url encoded name of the artist. */
        val artistUrlTemplate: String?,
    )

    private const val YOUTUBE_SEARCH = "https://www.youtube.com/results?search_query=%s"
    private const val YOUTUBE_ARTIST_SEARCH = "https://www.youtube.com/results?search_query=%s"
    private const val SPOTIFY_SEARCH = "https://open.spotify.com/search/%s"
    private const val APPLE_MUSIC_SEARCH = "https://music.apple.com/search?term=%s"
    private const val DEEZER_SEARCH = "https://www.deezer.com/search/%s"
    private const val SOUNDCLOUD_SEARCH = "https://soundcloud.com/search/%s"
    private const val STREETVOICE_SEARCH = "https://streetvoice.com/search?q=%s"
    private const val YMUSIC_SEARCH = "https://ymusicapp.com/watch?v=%s"
    private const val NETFLIX_SEARCH = "https://www.netflix.com/search?q=%s"
    private const val TWITCH_SEARCH = "https://www.twitch.tv/search?query=%s"
    private const val KICK_SEARCH = "https://kick.com/search?q=%s"
    private const val PANDORA_SEARCH = "https://www.pandora.com/search?q=%s"
    private const val TIDAL_SEARCH = "https://tidal.com/search?q=%s"
    private const val BANDCAMP_SEARCH = "https://bandcamp.com/search?q=%s"
    private const val QOBUZ_SEARCH = "https://www.qobuz.com/us-en/search?q=%s"
    private const val MIXCLOUD_SEARCH = "https://www.mixcloud.com/search/?q=%s"

    private data class Entry(
        val signatures: List<String>,
        val service: Service,
    )

    /**
     * The order matters: the more specific services come first, so that
     * "com.kapp.youtube.final" (YMusic) is not mistaken for YouTube.
     */
    private val entries = listOf(
        Entry(
            listOf("youtube.music"),
            Service("YouTube Music", Kind.MUSIC, YOUTUBE_SEARCH, YOUTUBE_ARTIST_SEARCH)
        ),
        Entry(
            listOf("kapp.youtube", "ymusicapp"),
            Service("YMusic", Kind.MUSIC, YMUSIC_SEARCH, YMUSIC_SEARCH)
        ),
        Entry(
            // YouTube Music wrapper / alternative clients. They all play YouTube Music
            // content, so they link to the same YouTube search pages.
            listOf("mist", "metrolist", "meld", "bitchord", "archivetune", "simpmusic", "ymusic"),
            Service("YouTube Music", Kind.MUSIC, YOUTUBE_SEARCH, YOUTUBE_ARTIST_SEARCH)
        ),
        Entry(
            listOf("youtube"),
            Service("YouTube", Kind.VIDEO, YOUTUBE_SEARCH, YOUTUBE_ARTIST_SEARCH)
        ),
        Entry(
            listOf("spotify"),
            Service("Spotify", Kind.MUSIC, SPOTIFY_SEARCH, SPOTIFY_SEARCH)
        ),
        Entry(
            listOf("apple.android.music", "itunes"),
            Service("Apple Music", Kind.MUSIC, APPLE_MUSIC_SEARCH, APPLE_MUSIC_SEARCH)
        ),
        Entry(
            listOf("deezer"),
            Service("Deezer", Kind.MUSIC, DEEZER_SEARCH, DEEZER_SEARCH)
        ),
        Entry(
            listOf("soundcloud"),
            Service("SoundCloud", Kind.MUSIC, SOUNDCLOUD_SEARCH, SOUNDCLOUD_SEARCH)
        ),
        Entry(
            listOf("streetvoice"),
            Service("StreetVoice", Kind.MUSIC, STREETVOICE_SEARCH, STREETVOICE_SEARCH)
        ),
        Entry(
            listOf("nicobox"),
            Service("Nicobox", Kind.MUSIC, null, null)
        ),
        Entry(
            listOf("netflix"),
            Service("Netflix", Kind.VIDEO, NETFLIX_SEARCH, null)
        ),
        Entry(
            listOf("twitch"),
            Service("Twitch", Kind.LIVE, TWITCH_SEARCH, TWITCH_SEARCH)
        ),
        Entry(
            listOf("kick.mobile", "kick.android"),
            Service("Kick", Kind.LIVE, KICK_SEARCH, KICK_SEARCH)
        ),
        Entry(
            listOf("pandora"),
            Service("Pandora", Kind.MUSIC, PANDORA_SEARCH, PANDORA_SEARCH)
        ),
        Entry(
            listOf("tidal"),
            Service("Tidal", Kind.MUSIC, TIDAL_SEARCH, TIDAL_SEARCH)
        ),
        Entry(
            listOf("bandcamp"),
            Service("Bandcamp", Kind.MUSIC, BANDCAMP_SEARCH, BANDCAMP_SEARCH)
        ),
        Entry(
            listOf("qobuz"),
            Service("Qobuz", Kind.MUSIC, QOBUZ_SEARCH, QOBUZ_SEARCH)
        ),
        Entry(
            listOf("mixcloud"),
            Service("Mixcloud", Kind.MUSIC, MIXCLOUD_SEARCH, MIXCLOUD_SEARCH)
        ),
        // Local players have no page to link to, but the app is still known.
        Entry(
            listOf("poweramp"),
            Service("Poweramp", Kind.MUSIC, null, null)
        ),
        Entry(
            listOf("musicolet"),
            Service("Musicolet", Kind.MUSIC, null, null)
        ),
        Entry(
            listOf("vimusic"),
            Service("ViMusic", Kind.MUSIC, null, null)
        ),
    )

    /**
     * The service of the app, or null when the app is not known.
     */
    fun service(packageName: String?): Service? {
        if (packageName.isNullOrBlank()) return null
        val pkg = packageName.lowercase()
        return entries.firstOrNull { entry -> entry.signatures.any { pkg.contains(it) } }?.service
    }

    /**
     * Package signatures of apps that play YouTube / YouTube Music content but
     * do NOT contain "youtube" in their package name (open-source / modded
     * clients). These must also be treated as YouTube so the media session's
     * video id is resolved to https://youtu.be/ID instead of a search page.
     *
     * StreetVoice, Spotify, Apple Music, etc. are deliberately excluded.
     */
    private val YOUTUBE_WRAPPER_SIGNATURES = listOf(
        "mist", "metrolist", "meld", "bitchord", "archivetune", "simpmusic",
        "ymusic", "vimusic", "innertune", "outertune", "spotube",
    )

    fun isYouTube(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        return pkg.contains("youtube") || YOUTUBE_WRAPPER_SIGNATURES.any { pkg.contains(it) }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    /**
     * The page of the service that lists the given track.
     *
     * This is only the fallback - the direct url of the track is preferred.
     */
    fun mediaSearchUrl(packageName: String?, title: String?): String? {
        val service = service(packageName) ?: return null
        val template = service.mediaUrlTemplate ?: return null
        if (title.isNullOrBlank()) return null
        return template.replace("%s", encode(title))
    }

    /**
     * The page of the service that belongs to the given artist.
     *
     * This is only the fallback - the real channel of the artist is preferred.
     */
    fun artistUrl(packageName: String?, artist: String?): String? {
        val service = service(packageName) ?: return null
        val template = service.artistUrlTemplate ?: return null
        if (artist.isNullOrBlank()) return null
        return template.replace("%s", encode(artist))
    }

    /**
     * The label of the button that links to the track.
     */
    fun mediaButtonLabel(packageName: String?, activityType: Int = ActivityTypeResolver.DEFAULT_TYPE): String {
        val service = service(packageName)
        if (service != null) {
            return when (service.kind) {
                Kind.MUSIC -> "Listen on ${service.name}"
                Kind.VIDEO -> "Watch on ${service.name}"
                Kind.LIVE -> "Watch ${service.name}"
            }
        }
        // The app is unknown, so the activity type decides how the button reads.
        return when (activityType) {
            ActivityTypeResolver.LISTENING -> "Listen to this track"
            ActivityTypeResolver.WATCHING -> "Watch this video"
            ActivityTypeResolver.STREAMING -> "Watch this stream"
            else -> "Open Media Link"
        }
    }

    /**
     * The label of the button that links to the artist.
     */
    fun artistButtonLabel(packageName: String?): String {
        val service = service(packageName)
        return if (service != null) "Artist on ${service.name}" else "Artist Channel"
    }
}
