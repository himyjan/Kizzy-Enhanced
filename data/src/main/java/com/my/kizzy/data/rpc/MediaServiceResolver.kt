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
    private const val YMUSIC_SEARCH = YOUTUBE_SEARCH
    private const val NETFLIX_SEARCH = "https://www.netflix.com/search?q=%s"
    private const val TWITCH_SEARCH = "https://www.twitch.tv/search?query=%s"
    private const val KICK_SEARCH = "https://kick.com/search?q=%s"
    private const val PANDORA_SEARCH = "https://www.pandora.com/search?q=%s"
    private const val TIDAL_SEARCH = "https://tidal.com/search?q=%s"
    private const val BANDCAMP_SEARCH = "https://bandcamp.com/search?q=%s"
    private const val QOBUZ_SEARCH = "https://www.qobuz.com/us-en/search?q=%s"
    private const val MIXCLOUD_SEARCH = "https://www.mixcloud.com/search/?q=%s"
    private const val JOOX_SEARCH = "https://www.joox.com/search?q=%s"
    private const val KUGOU_SEARCH = "https://www.kugou.com/search.html?keyword=%s"
    private const val QQ_SEARCH = "https://y.qq.com/n/ryqq/search?searchword=%s"
    private const val DOUYIN_SEARCH = "https://www.douyin.com/search/%s"
    private const val NETEASE_SEARCH = "https://music.163.com/#/search/m/?s=%s&type=1"
    private const val KUWO_SEARCH = "https://kuwo.cn/search/list?key=%s"
    private const val JIOSAAVN_SEARCH = "https://www.jiosaavn.com/search/%s"
    private const val GAANA_SEARCH = "https://gaana.com/search/%s"
    private const val WYNK_SEARCH = "https://wynk.in/music/search/%s"
    private const val BOOMPLAY_SEARCH = "https://www.boomplay.com/search/songs/%s"
    private const val AUDIOMACK_SEARCH = "https://www.audiomack.com/search?q=%s"
    private const val YANDEX_SEARCH = "https://music.yandex.ru/search?text=%s"
    private const val VK_SEARCH = "https://vk.com/audio?section=search&q=%s"
    private const val ANGHAMI_SEARCH = "https://anghami.com/search/%s"
    private const val AWA_SEARCH = "https://awa.fm/search?q=%s"
    private const val RECOCHOKU_SEARCH = "https://recochoku.jp/search?q=%s"
    private const val MELON_SEARCH = "https://www.melon.com/search/tab/index.htm?q=%s"
    private const val GENIE_SEARCH = "https://www.genie.co.kr/search/searchMain?kwd=%s"
    private const val BEATPORT_SEARCH = "https://www.beatport.com/search?q=%s"

    /**
     * Package signatures of apps that play YouTube / YouTube Music content but
     * do NOT contain "youtube" in their package name (open-source / modded
     * clients). These must also be treated as YouTube so the media session's
     * video id is resolved to https://youtu.be/ID instead of a search page,
     * and so the button labels / fallback links point at YouTube Music.
     *
     * StreetVoice, Spotify, Apple Music, etc. are deliberately excluded.
     */
    private val YOUTUBE_WRAPPER_SIGNATURES = listOf(
        // InnerTune / OuterTune / Metrolist ecosystem
        "metrolist", "archivetune", "natune", "intertune", "astertune", "metrofuse",
        "innertune", "outertune", "mist", "meld", "bitchord",

        // ViMusic / RiMusic ecosystem & YT Music clients
        "kreate", "vitune", "simpmusic", "ymusic", "ymusicapp", "harmony", "harmonymusic",
        "gyawun", "musify", "namida", "bloomee", "vimusic", "spotube",

        // Full YouTube video / extractor clients
        "newpipe", "pipepipe", "tubular", "libretube", "clipious", "skytube",
        "grayjay", "noutube", "freetube", "bravepipe",
    )

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
            Service("YouTube Music", Kind.MUSIC, YOUTUBE_SEARCH, YOUTUBE_ARTIST_SEARCH)
        ),
        Entry(
            // YouTube Music wrapper / alternative clients. They all play YouTube Music
            // content, so they link to the same YouTube search pages.
            YOUTUBE_WRAPPER_SIGNATURES,
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
            listOf("joox"),
            Service("Joox", Kind.MUSIC, JOOX_SEARCH, JOOX_SEARCH)
        ),
        Entry(
            listOf("kugou", "kugoumusic"),
            Service("Kugou Music", Kind.MUSIC, KUGOU_SEARCH, KUGOU_SEARCH)
        ),
        Entry(
            listOf("qqmusic", "qq.com", "y.qq.com"),
            Service("QQ Music", Kind.MUSIC, QQ_SEARCH, QQ_SEARCH)
        ),
        Entry(
            listOf("douyin", "snapvideo"),
            Service("Douyin Music", Kind.MUSIC, DOUYIN_SEARCH, DOUYIN_SEARCH)
        ),
        Entry(
            listOf("163", "netease", "cloudmusic"),
            Service("NetEase Cloud Music", Kind.MUSIC, NETEASE_SEARCH, NETEASE_SEARCH)
        ),
        Entry(
            listOf("kuwo"),
            Service("Kuwo Music", Kind.MUSIC, KUWO_SEARCH, KUWO_SEARCH)
        ),
        Entry(
            listOf("jiosaavn"),
            Service("JioSaavn", Kind.MUSIC, JIOSAAVN_SEARCH, JIOSAAVN_SEARCH)
        ),
        Entry(
            listOf("gaana"),
            Service("Gaana", Kind.MUSIC, GAANA_SEARCH, GAANA_SEARCH)
        ),
        Entry(
            listOf("wynk"),
            Service("Wynk Music", Kind.MUSIC, WYNK_SEARCH, WYNK_SEARCH)
        ),
        Entry(
            listOf("boomplay"),
            Service("Boomplay", Kind.MUSIC, BOOMPLAY_SEARCH, BOOMPLAY_SEARCH)
        ),
        Entry(
            listOf("audiomack"),
            Service("Audiomack", Kind.MUSIC, AUDIOMACK_SEARCH, AUDIOMACK_SEARCH)
        ),
        Entry(
            listOf("yandex"),
            Service("Yandex Music", Kind.MUSIC, YANDEX_SEARCH, YANDEX_SEARCH)
        ),
        Entry(
            listOf("vk.com", "vkontakte"),
            Service("VK Music", Kind.MUSIC, VK_SEARCH, VK_SEARCH)
        ),
        Entry(
            listOf("anghami"),
            Service("Anghami", Kind.MUSIC, ANGHAMI_SEARCH, ANGHAMI_SEARCH)
        ),
        Entry(
            listOf("awa"),
            Service("AWA", Kind.MUSIC, AWA_SEARCH, AWA_SEARCH)
        ),
        Entry(
            listOf("recochoku"),
            Service("Recochoku", Kind.MUSIC, RECOCHOKU_SEARCH, RECOCHOKU_SEARCH)
        ),
        Entry(
            listOf("melon"),
            Service("Melon", Kind.MUSIC, MELON_SEARCH, MELON_SEARCH)
        ),
        Entry(
            listOf("genie"),
            Service("Genie Music", Kind.MUSIC, GENIE_SEARCH, GENIE_SEARCH)
        ),
        Entry(
            listOf("beatport"),
            Service("Beatport", Kind.MUSIC, BEATPORT_SEARCH, BEATPORT_SEARCH)
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

    fun isYouTube(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        return pkg.contains("youtube") || YOUTUBE_WRAPPER_SIGNATURES.any { pkg.contains(it) }
    }

    /** Browsers that publish a MediaSession for a web player (Apple Music web,
     * YouTube web, Spotify web, ...). The link must be the PAGE url. */
    private val BROWSER_SIGNATURES = listOf(
        "chrome", "chromium", "cromite", "bromite", "vanadium",
        "microsoft.emmx", "edge",
        "firefox", "fennec", "fenix", "focus", "klar", "mozilla",
        "vivaldi", "brave", "opera", "kiwi",
        "duckduckgo", "ecosia", "qwant",
        "sbrowser", "samsung.android.internet",
        "android.browser", "miui.browser", "huawei.browser", "heytap.browser",
        "uc.browser", "ucmobile", "via.gp", "aloha", "ghostery",
        "org.chromium",
    )

    fun isBrowser(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        return BROWSER_SIGNATURES.any { sig -> pkg.contains(sig) }
    }

    /** Human friendly name of the browser, used on the button label. */
    fun browserName(packageName: String?): String {
        if (packageName == null) return "Browser"
        val pkg = packageName.lowercase()
        return when {
            "vivaldi" in pkg -> "Vivaldi"
            "microsoft.emmx" in pkg || "edge" in pkg -> "Edge"
            "firefox" in pkg || "fennec" in pkg || "fenix" in pkg -> "Firefox"
            "brave" in pkg -> "Brave"
            "opera" in pkg -> "Opera"
            "duckduckgo" in pkg -> "DuckDuckGo"
            "sbrowser" in pkg -> "Samsung Internet"
            "kiwi" in pkg -> "Kiwi"
            "cromite" in pkg -> "Cromite"
            "bromite" in pkg -> "Bromite"
            "chromium" in pkg -> "Chromium"
            "chrome" in pkg -> "Chrome"
            else -> "Browser"
        }
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

    /** Generic search fallback for browser players that publish no page url. */
    fun browserSearchUrl(title: String?): String? {
        if (title.isNullOrBlank()) return null
        return "https://www.google.com/search?q=" + encode(title)
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

    /**
     * The download page of the app that is playing.
     *
     * Open-source players are distributed through GitHub, so their releases
     * page is used first. Official / closed-source apps fall back to their
     * store listing (Play Store). Returns null only when the package is blank.
     */
    fun appDownloadUrl(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val pkg = packageName.lowercase()

        // Open-source / community players: GitHub releases are the canonical
        // download page. Matched by signature because forks rename the package.
        val gitHubRelease = when {
            "metrolist" in pkg -> "https://github.com/MetrolistGroup/Metrolist/releases"
            "innertubex" in pkg || "intertubex" in pkg ->
                "https://github.com/MetrolistGroup/InnertubeX/releases"
            "innertune" in pkg || "intertune" in pkg ->
                "https://github.com/z-huang/InnerTune/releases"
            "outertune" in pkg -> "https://github.com/OuterTune/OuterTune/releases"
            "vimusic" in pkg -> "https://github.com/vfsfitvnm/ViMusic/releases"
            "rimusic" in pkg -> "https://github.com/fast4x/RiMusic/releases"
            "simpmusic" in pkg -> "https://github.com/maxrave-dev/SimpMusic/releases"
            "vitune" in pkg -> "https://github.com/25huizengek1/ViTune/releases"
            "spotube" in pkg -> "https://github.com/KRTirtho/spotube/releases"
            "newpipe" in pkg -> "https://github.com/TeamNewPipe/NewPipe/releases"
            "pipepipe" in pkg -> "https://github.com/InfinityLoop1308/PipePipe/releases"
            "libretube" in pkg -> "https://github.com/libre-tube/LibreTube/releases"
            "skytube" in pkg -> "https://github.com/SkyTubeTeam/SkyTube/releases"
            "grayjay" in pkg -> "https://github.com/futo-org/grayjay-android/releases"
            "musify" in pkg -> "https://github.com/gokadzev/Musify/releases"
            "namida" in pkg -> "https://github.com/namidaco/namida/releases"
            "bloomee" in pkg -> "https://github.com/HemantKArya/BloomeeTunes/releases"
            else -> null
        }
        if (gitHubRelease != null) return gitHubRelease

        // YMusic is closed source but not on the Play Store.
        if ("ymusicapp" in pkg || "kapp.youtube" in pkg) return "https://ymusicapp.com"

        // Official / closed-source apps: link to the store page of the app.
        val storePackage = when {
            "youtube.music" in pkg -> "com.google.android.apps.youtube.music"
            "youtube" in pkg -> "com.google.android.youtube"
            "spotify" in pkg -> "com.spotify.music"
            "apple.android.music" in pkg -> "com.apple.android.music"
            "deezer" in pkg -> "deezer.android.app"
            "soundcloud" in pkg -> "com.soundcloud.android"
            "tidal" in pkg -> "com.aspiro.tidal"
            "amazon" in pkg -> "com.amazon.mp3"
            "netease" in pkg || "cloudmusic" in pkg -> "com.netease.cloudmusic"
            else -> packageName
        }
        return "https://play.google.com/store/apps/details?id=$storePackage"
    }

    /**
     * Label that names what the media link actually points at. Discord shows
     * this text on the button, so "Playlist" / "Video" / "Track" is clearer
     * than a generic "Listen on ...".
     */
    fun mediaLinkLabel(url: String?, packageName: String?, activityType: Int = ActivityTypeResolver.DEFAULT_TYPE): String {
        val service = service(packageName)
        val serviceName = if (isBrowser(packageName)) {
            MediaServiceResolver.browserName(packageName)
        } else {
            service?.name
        }
        val prefix = when {
            url?.contains("playlist?list=") == true || url?.contains("/album/") == true ||
                url?.contains("/playlist/") == true -> "Playlist"
            url?.contains("/channel/") == true -> "Channel"
            url?.contains("youtu.be/") == true || url?.contains("/watch?v=") == true -> "Video"
            service?.kind == Kind.LIVE -> "Stream"
            service?.kind == Kind.VIDEO -> "Video"
            activityType == ActivityTypeResolver.WATCHING -> "Video"
            activityType == ActivityTypeResolver.LISTENING -> "Track"
            else -> "Media"
        }
        return if (serviceName != null) "$prefix on $serviceName" else prefix
    }
}
