/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Constants.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.resources.R

object Constants {
    const val NINTENDO_LINK =
        "https://media.discordapp.net/attachments/948828217312178227/1018855932496719932/default.png"
    const val NINTENDO_SWITCH_2_LINK =
        "https://media.discordapp.net/attachments/948828217312178227/1018855932496719932/nintendo-switch-2-2026.png"
    const val WII_U_LINK =
        "https://media.discordapp.net/attachments/948828217312178227/1020010414576255017/default.png"
    const val XBOX_LINK =
        "https://thumbs4.imgbox.com/ef/0c/08oxh57r_t.png"
    const val PS5_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/ps5-2026.png"
    const val PS4_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/ps4-2026.png"
    const val DROIDDECK_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/droideck-2026.png"
    const val EDEN_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/eden-2026.png"
    const val SAMSUNG_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/samsung-2026.png"
    const val IOS_LINK =
        "https://media.discordapp.net/attachments/1009061802593763398/1049675792952598599/ios-2026.png"
    const val ANDROID_LINK =
        "https://www.gstatic.com/devrel-devsite/prod/vc11e4c9d4c060345ba98585d538d4546e842f4a6ee5c5f9e85be4bd345767217/androidsource/images/touchicon-180.png"
    const val N3DS_LINK =
        "https://media.discordapp.net/attachments/948828217312178227/1040885415864967279/3ds.png"
    const val NINTENDO = "Nintendo Switch"
    const val NINTENDO_SWITCH_2 = "Nintendo Switch 2"
    const val NINTENDO_3DS = "Nintendo-3DS"
    const val WII_U = "Wii-U"
    const val XBOX = "Xbox"
    const val ANDROID = "Android"
    const val DROIDDECK = "DroidDeck"
    const val EDEN = "Eden"
    const val APPLICATION_ID = "1522589499903840448"
    const val IMGUR_CLIENT_ID = "d70305e7c3ac5c6"
    const val APP_DIRECTORY = "App Directory"
    const val DOWNLOADS_DIRECTORY = "Downloads Directory"
    const val MAX_ALLOWED_CHARACTER_LENGTH = 32
    /*
    See https://discord.com/developers/docs/reference#snowflakes
    */
    val MAX_APPLICATION_ID_LENGTH_RANGE = 18..19

    val ACTIVITY_TYPE = mapOf(
        "Playing" to 0,
        "Streaming" to 1,
        "Listening" to 2,
        "Watching" to 3,
        "Competing" to 5
    )
    val ACTIVITY_STATUS = mapOf(
        R.string.status_online to "online",
        R.string.status_idle to "idle",
        R.string.status_dnd to "dnd",
        R.string.status_offline to "offline",
        R.string.status_invisible_offline to "invisible"
    )

    val ACTIVITY_PLATFORMS = mapOf(
        "Android" to "android",
        "Desktop" to "desktop",
        "DroidDeck" to "droideck",
        "Eden" to "eden",
        "Embedded" to "embedded",
        "IOS" to "ios",
        "Nintendo Switch 2" to "nintendo-switch-2",
        "PlayStation 4" to "ps4",
        "PlayStation 5" to "ps5",
        "Samsung" to "samsung",
        "Xbox" to "xbox",
    )

    /**
     * Platform-indicator spoofing options (label -> id), ported from Equicord's
     * platformSpoofer plugin. The selected id drives the gateway IDENTIFY
     * "browser" property, which is what platform-indicator plugins read.
     * An empty id means "do not spoof" (default Discord Client / desktop).
     */
    val SPOOF_PLATFORMS = linkedMapOf(
        "None (Desktop)" to "",
        "Desktop" to "desktop",
        "Web" to "web",
        "Android" to "android",
        "iOS" to "ios",
        "Xbox" to "xbox",
        "Playstation" to "playstation",
        "VR" to "vr",
    )

    /**
     * Timestamp source modes for Manual RPC.
     * label -> stored id
     */
    val TIMESTAMP_MODES = linkedMapOf(
        "Media / App (default)" to "default",
        "Same as current time of day" to "current",
        "Custom" to "custom",
    )

    /**
     * Maps a spoof platform id to the gateway IDENTIFY connection properties
     * (browser / os / device). Mirrors Equicord's getPlatform browser mapping,
     * with os/device tuned so mobile ids register as a mobile client status.
     */
    fun spoofIdentifyProperties(platform: String): Triple<String, String, String> = when (platform) {
        "desktop" -> Triple("Discord Client", "Windows", "ktor")
        "web" -> Triple("Discord Web", "Windows", "ktor")
        "android" -> Triple("Discord Android", "Android", "Discord Android")
        "ios" -> Triple("Discord iOS", "iOS", "Discord iOS")
        "samsung" -> Triple("Discord Android", "Android", "Samsung")
        "xbox" -> Triple("Discord Embedded", "Windows", "ktor")
        "ps4" -> Triple("Discord Embedded", "Windows", "ktor")
        "ps5" -> Triple("Discord Embedded", "Windows", "ktor")
        "nintendo-switch-2" -> Triple("Discord Embedded", "Windows", "ktor")
        "droideck" -> Triple("Discord Android", "Android", "DroidDeck")
        "eden" -> Triple("Discord Embedded", "Windows", "ktor")
        "playstation" -> Triple("Discord Embedded", "Windows", "ktor")
        "vr" -> Triple("Discord VR", "Windows", "ktor")
        else -> Triple("Discord Client", "Windows", "ktor")
    }
}