/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GamesResponse.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.remote


import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.domain.model.Game
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GamesResponse(
    @SerialName("label")
    val label: String,
    @SerialName("link")
    val link: String? = null,
    @SerialName("title")
    val title: String
)

fun GamesResponse.toGame() : Game {
    return Game(
        platform = when (label) {
            "nintendo" -> Constants.NINTENDO
            "nintendo-switch-2" -> Constants.NINTENDO_SWITCH_2
            "wii" -> Constants.WII_U
            "nintendo-3ds" -> Constants.NINTENDO_3DS
            else -> Constants.ANDROID_LINK
        },
        small_image = when (label) {
            "nintendo" -> Constants.NINTENDO_LINK
            "nintendo-switch-2" -> Constants.NINTENDO_SWITCH_2_LINK
            "wii" -> Constants.WII_U_LINK
            "nintendo-3ds" -> Constants.N3DS_LINK
            "xbox" -> Constants.XBOX_LINK
            "ps5" -> Constants.PS5_LINK
            "ps4" -> Constants.PS4_LINK
            "android" -> Constants.ANDROID_LINK
            "ios" -> Constants.IOS_LINK
            "samsung" -> Constants.SAMSUNG_LINK
            "droideck" -> Constants.DROIDDECK_LINK
            "eden" -> Constants.EDEN_LINK
            else -> Constants.ANDROID_LINK
        },
        large_image = link,
        game_title = title
    )
}