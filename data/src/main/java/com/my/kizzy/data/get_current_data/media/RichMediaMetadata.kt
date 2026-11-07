package com.my.kizzy.data.get_current_data.media

import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps

data class RichMediaMetadata(
    val appName: String? = null,
    val packageName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val state: String? = null,
    val album: String? = null,
    val coverArt: RpcImage? = null,
    val appIcon: RpcImage? = null,
    val playbackStateIcon: RpcImage? = null,
    val playbackState: Int? = null,
    val duration: Long? = null,
    val position: Long? = null,
    val timestamps: Timestamps? = null,
    val audioChannels: String? = null,
    val allArtists: List<String> = emptyList(),
)
