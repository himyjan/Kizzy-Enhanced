/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ActivityTypeResolver.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import android.os.SystemClock
import com.my.kizzy.preference.Prefs

/**
 * Decides which Discord activity type an app should be reported as.
 *
 * Discord renders the activity type very differently: type [PLAYING] is shown as a
 * *game* card, [LISTENING] as a music card and [WATCHING] / [STREAMING] as a video
 * card. Every RPC feature used to report a single global type for all apps, so a
 * music app such as YouTube Music showed up as a game.
 *
 * The type is now resolved per app with this chain:
 *
 *  1. an explicit per-app override the user picked in the app list
 *  2. the built-in catalog of well known apps
 *  3. the metadata of the media session: a session that carries an artist or an
 *     album is a track, so it is reported as music
 *  4. the caller's fallback
 */
object ActivityTypeResolver {

    const val PLAYING = 0
    const val STREAMING = 1
    const val LISTENING = 2
    const val WATCHING = 3
    const val COMPETING = 5

    /**
     * Used when neither the app nor its media session reveal anything. Media
     * sessions are far more often video than audio on Android, so "Watching" is
     * the better guess than "Playing" (which Discord draws as a game).
     */
    const val DEFAULT_TYPE = WATCHING

    /**
     * Music streaming services and music players. They all report a track, so
     * Discord should draw the music card instead of the game card.
     */
    val MUSIC_APPS: Set<String> = setOf(
        // streaming services
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.googlemusic",
        "com.google.android.music",
        "com.amazon.mp3",
        "com.amazon.music",
        "com.apple.android.music",
        "com.soundcloud.android",
        "com.soundcloud.android.app",
        "deezer.android.app",
        "com.aspiro.tidal",
        "com.pandora.android",
        "com.rhapsody",
        "com.mixcloud.mixcloud.player",
        "com.mixcloud.android",
        "com.bandcamp.android",
        "com.qobuz.music",
        "com.anghmm.android",
        "com.kakao.music",
        "com.tencent.qqmusic",
        "com.netease.cloudmusic",
        "com.ximalaya.ting.android",
        "com.luna.music",
        "com.shazam.android",
        "com.himalaya.android",
        "com.ting.android.music",
        "com.musicradio.radiostation",
        // local players
        "com.jrtstudio.AnotherMusicPlayer",
        "com.tbig.playerpro",
        "com.maxmpz.audioplayer",
        "com.sec.android.app.music",
        "com.sec.android.app.samsungapps",
        "com.sonyericsson.music",
        "org.oxycblt.auxio",
        "com.github.patrykandpatrick.vime",
        "code.name.monkey.retromusic",
        "com.simplecity.amp_library",
    )

    /**
     * Video streaming services and video players.
     */
    val VIDEO_APPS: Set<String> = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.unplugged",
        "com.google.android.youtube.tv",
        "com.google.android.youtube.googletv",
        "com.google.android.youtube.vr",
        "com.google.android.apps.mediashell",
        "com.google.android.videos",
        "com.netflix.mediaclient",
        "com.amazon.avod",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.hulu.plus",
        "com.hbo.hbonow",
        "com.crunchyroll.crunchyroll",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "org.videolan.vlc",
        "org.schabi.newpipe",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.google.android.apps.youtube.music.pipe",
    )

    /**
     * Live streaming platforms. Discord draws these with the broadcast card.
     */
    val LIVESTREAM_APPS: Set<String> = setOf(
        "tv.twitch.android.app",
        "tv.twitch.android.viewer",
        "com.kick.mobile",
        "com.kick.android",
    )

    /**
     * The packages of the apps that are recognized by their name. The apps use
     * different package names for the same service (YouTube Music for example is
     * both "com.google..." and "app.morphe..."), so the name is matched as well.
     */
    private val MUSIC_SIGNATURES = listOf(
        "youtube.music",
        "spotify",
        "soundcloud",
        "deezer",
        "tidal",
        "pandora",
        "bandcamp",
        "qobuz",
        "mixcloud",
        "poweramp",
        "musicolet",
        "vimusic",
        "ymusicapp",
        "kapp.youtube",
        "amazon.mp3",
        "amazon.music",
        "apple.android.music",
        "google.android.music",
        "music",
        // YouTube Music wrapper / alternative clients - all play YouTube Music content
        "mist",
        "metrolist",
        "meld",
        "bitchord",
        "archivetune",
        "simpmusic",
        "ymusic",
    )

    private val VIDEO_SIGNATURES = listOf(
        "youtube",
        "netflix",
        "vlc",
        "mxtech",
        "disneyplus",
        "crunchyroll",
        "primevideo",
        "amazon.avod",
        "mediashell",
        "videolan",
    )

    private val LIVESTREAM_SIGNATURES = listOf(
        "twitch",
        "kick.mobile",
        "kick.android",
    )

    private val knownTypes: Map<String, Int> = buildMap {
        MUSIC_APPS.forEach { put(it, LISTENING) }
        VIDEO_APPS.forEach { put(it, WATCHING) }
        LIVESTREAM_APPS.forEach { put(it, STREAMING) }
    }

    /**
     * The overrides are read from the preferences, which is comparatively expensive
     * (a storage read plus a JSON decode). The presence is refreshed every second
     * while a track plays, so the map is cached for a short while.
     */
    private const val OVERRIDE_CACHE_TTL_MS = 5000L

    @Volatile
    private var cachedOverrides: Map<String, Int>? = null

    @Volatile
    private var cachedOverridesAt: Long = 0L

    private fun overrides(): Map<String, Int> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedOverrides
        if (cached != null && now - cachedOverridesAt < OVERRIDE_CACHE_TTL_MS) return cached
        val fresh = Prefs.getAppActivityTypes()
        cachedOverrides = fresh
        cachedOverridesAt = now
        return fresh
    }

    /**
     * Drops the cached overrides. Called after the user changes a per-app type.
     */
    fun invalidateCache() {
        cachedOverrides = null
        cachedOverridesAt = 0L
    }

    /**
     * The built-in type for the app, or null when the app is not known.
     */
    fun knownType(packageName: String?): Int? {
        if (packageName.isNullOrBlank()) return null
        knownTypes[packageName]?.let { return it }

        val pkg = packageName.lowercase()
        // The live streams are checked first, the music apps before the video apps,
        // so that "youtube.music" is not mistaken for a video app.
        if (LIVESTREAM_SIGNATURES.any { pkg.contains(it) }) return STREAMING
        if (MUSIC_SIGNATURES.any { pkg.contains(it) }) return LISTENING
        if (VIDEO_SIGNATURES.any { pkg.contains(it) }) return WATCHING
        return null
    }

    /**
     * Resolves the activity type for an app.
     *
     * @param packageName the package name of the app
     * @param isMusicMetadata whether the media session carries artist or album
     *        metadata, which means it is a music track
     * @param fallback the type to use when nothing at all is known
     * @param useAutomaticDetection whether the built-in catalog and the
     *        metadata of the session may be used. A per-app choice of the
     *        user always wins, even when the detection is switched off.
     */
    fun resolve(
        packageName: String?,
        isMusicMetadata: Boolean = false,
        fallback: Int = DEFAULT_TYPE,
        useAutomaticDetection: Boolean = true,
    ): Int {
        if (!packageName.isNullOrBlank()) {
            // 1. the explicit choice of the user always wins
            overrides()[packageName]?.let { return it }
            // 2. the built-in catalog
            if (useAutomaticDetection) knownType(packageName)?.let { return it }
        }

        // 3. the session itself tells us whether this is a track
        if (useAutomaticDetection && isMusicMetadata) return LISTENING

        // 4. nothing known
        return fallback
    }
}
