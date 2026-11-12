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

data class RichMediaMetadata(
    val appName: String? = null,
    val packageName: String = "",
    val title: String? = null,
    val artist: String? = null,
    val state: String? = null,
    val album: String? = null,
    val coverArt: RpcImage? = null,
    val appIcon: RpcImage? = null,
    val playbackStateIcon: RpcImage? = null,
    val playbackState: Int? = null,
    val duration: Long = 0L,
    val position: Long = 0L,
    val timestamps: Timestamps? = null,
    val audioChannels: String? = null,
    val allArtists: List<String> = emptyList(),
    /**
     * Whether the media session carries artist or album metadata, which means
     * it is a music track rather than a video.
     */
    val isMusicMetadata: Boolean = false
)
