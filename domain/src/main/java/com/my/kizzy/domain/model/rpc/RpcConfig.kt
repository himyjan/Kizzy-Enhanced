/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcConfig.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.domain.model.rpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// data class used for saving/loading/previewing rpc in the app
@Serializable
data class RpcConfig(
    @SerialName("instanceId")
    val instanceId: String = "",
    @SerialName("button1")
    val button1: String = "",
    @SerialName("button1link")
    val button1link: String = "",
    @SerialName("button2")
    val button2: String = "",
    @SerialName("button2link")
    val button2link: String = "",
    @SerialName("details")
    val details: String = "",
    @SerialName("largeImg")
    val largeImg: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("smallImg")
    val smallImg: String = "",
    @SerialName("state")
    val state: String = "",
    @SerialName("status")
    val status: String = "",
    @SerialName("timestampsStart")
    val timestampsStart: String = "",
    @SerialName("timestampsStop")
    val timestampsStop: String = "",
    @SerialName("type")
    val type: String = "",
    @SerialName("large_text")
    val largeText: String = "",
    @SerialName("small_text")
    val smallText: String = "",
    @SerialName("url")
    val url: String = "",
    // Clickable field URLs (Discord Social SDK / rich presence). The profile
    // preview uses these to make details/state/image tappable.
    @SerialName("detailsUrl")
    val detailsUrl: String = "",
    @SerialName("stateUrl")
    val stateUrl: String = "",
    @SerialName("largeUrl")
    val largeUrl: String = "",
    @SerialName("smallUrl")
    val smallUrl: String = "",
    @SerialName("partyCurrentSize")
    val partyCurrentSize: String = "",
    @SerialName("partyMaxSize")
    val partyMaxSize: String = "",
    @SerialName("platform")
    val platform: String = "",
    // Media RPC only: playback is paused right now, and where it was paused.
    // The preview needs both to freeze the timeline instead of letting the
    // wall clock keep counting while the track is not playing.
    @SerialName("paused")
    val paused: Boolean = false,
    @SerialName("pausedPositionMs")
    val pausedPositionMs: Long? = null,
)