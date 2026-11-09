/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * UserInfo.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile

import com.my.kizzy.domain.model.user.User
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.USER_BIO
import com.my.kizzy.preference.Prefs.USER_DATA
import com.my.kizzy.preference.Prefs.USER_ID
import com.my.kizzy.preference.Prefs.USER_NITRO
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.Payload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

suspend fun getUserInfo(token: String, onInfoSaved: () -> Unit) {
    val discordWebSocket: DiscordWebSocket = object: DiscordWebSocketImpl(token){
        override fun Payload.handleDispatch(jsonString: String) {
            if (this.t.toString() == "READY"){
                val readyUser = decodeReady(jsonString)?.user ?: return
                val user = User(
                    id = readyUser.id,
                    username = readyUser.username,
                    discriminator = readyUser.discriminator,
                    globalName = readyUser.globalName,
                    avatar = readyUser.avatar,
                    avatarDecoration = readyUser.avatarDecoration,
                    banner = readyUser.banner,
                    bannerColor = readyUser.bannerColor,
                    bio = readyUser.bio,
                    nitro = readyUser.premiumType in 1..3,
                    publicFlags = readyUser.publicFlags,
                    verified = readyUser.verified ?: false,
                )
                Prefs[USER_ID] = user.id
                Prefs[USER_BIO] = user.bio
                Prefs[USER_NITRO] = user.nitro
                Prefs[USER_DATA] = Json.encodeToString(user)
                close()
                onInfoSaved()
            }
        }
    }
    discordWebSocket.connect()
}
