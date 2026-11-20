/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CommonRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

data class CommonRpc(
    val name: String = "",
    val type: Int? = null,
    val details: String? = "",
    val state: String? = "",
    val detailsUrl: String? = null,
    val stateUrl: String? = null,
    val partyCurrentSize: Int? = null,
    val partyMaxSize: Int? = null,
    val largeImage: RpcImage? = null,
    val smallImage: RpcImage? = null,
    val largeUrl: String? = null,
    val smallUrl: String? = null,
    var largeText: String? = null,
    var smallText: String? = null,
    val time: Timestamps? = null,
    val packageName: String = "",
    val platform: String? = null,
    val mediaUrl: String? = null,
    /** Album URL (fallback link when mediaUrl is unavailable). */
    val albumUrl: String? = null,
    /** Playlist URL (preferred link for the large/cover image). */
    val playlistUrl: String? = null,
    /**
     * True when [largeText] is the playlist name (no album was available), so
     * the large image / line 4 should link to [playlistUrl] instead of the album.
     */
    val largeTextIsPlaylist: Boolean = false,
    /** Channel/artist URL (final fallback link). */
    val channelUrl: String? = null,
    val playbackState: Int? = null,
    /**
     * The clean package name of the app, without the "title::" prefix that
     * [packageName] carries for backwards compatibility.
     */
    val appPackage: String = "",
        /** The human readable name of the app, e.g. "YouTube Music". */
    val appName: String = "",
    /**
     * Whether the media session carries artist or album metadata, which means
     * it is a music track rather than a video.
     */
    val isMusicMetadata: Boolean = false,
    /**
     * Controls which field (name, state, or details) is displayed in users'
     * status text in the member list. Values: 0 = name, 1 = details, 2 = state.
     * Defaults to 0 (name) if not set.
     */
    val statusDisplayType: Int? = null
)
