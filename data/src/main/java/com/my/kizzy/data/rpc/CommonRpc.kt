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
    val partyCurrentSize: Int? = null,
    val partyMaxSize: Int? = null,
    val largeImage: RpcImage? = null,
    val smallImage: RpcImage? = null,
    var largeText: String? = null,
    var smallText: String? = null,
    val time: Timestamps? = null,
    val packageName: String = "",
    val platform: String? = null,
    val mediaUrl: String? = null,
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
    val isMusicMetadata: Boolean = false
)
