/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RichMediaMetadata.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */

package com.my.kizzy.data.get_current_data.media

import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps

/**
 * Rich snapshot of an active Android media session resolved for Discord Rich Presence
 * and UI preview rendering.
 */
data class RichMediaMetadata(
    val appName: String? = null,
    val packageName: String = "",
    val title: String? = null,
    val artist: String? = null,
    val state: String? = null,
    val album: String? = null,
    /**
     * Best-effort playlist name, used for the large image text when the track
     * has no album (players rarely publish it).
     */
    val playlistName: String? = null,
    /**
     * Resolved hover text for the large image (album name or playlist name).
     */
    val largeText: String? = null,
    /**
     * True when [largeText] came from a playlist name rather than an album title.
     */
    val largeTextIsPlaylist: Boolean = false,
    val coverArt: RpcImage? = null,
    val appIcon: RpcImage? = null,
    val playbackStateIcon: RpcImage? = null,
    val smallImage: RpcImage? = null,
    val smallText: String? = null,
    val smallImageChannelUrl: String? = null,
    val playbackState: Int? = null,
    val duration: Long = 0L,
    val position: Long = 0L,
    val timestamps: Timestamps? = null,
    val audioChannels: String? = null,
    val allArtists: List<String> = emptyList(),
    /**
     * Whether the media session carries artist or album metadata, which means
     * it is a music track rather than a generic video.
     */
    val isMusicMetadata: Boolean = false,
    /**
     * The direct URL of the track/video, when resolved (never a search URL).
     */
    val mediaUrl: String? = null,
    /**
     * Clickable URL for the details line (direct [mediaUrl] with search fallback).
     */
    val detailsUrl: String? = null,
    /**
     * Clickable URL for the state line (artist/channel page or artist search fallback).
     */
    val stateUrl: String? = null,
    /**
     * The URL of the artist or uploader channel, when known.
     */
    val artistUrl: String? = null,
    /**
     * The URL of the album, when the session publishes one (fallback link).
     */
    val albumUrl: String? = null,
    /**
     * The URL of the playlist, when the session publishes one (preferred cover-art link).
     */
    val playlistUrl: String? = null,
    /**
     * The URL of the channel (final fallback link).
     */
    val channelUrl: String? = null,
    val statusDisplayType: Int? = null,
)