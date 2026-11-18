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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Fetches the account identity from the gateway READY payload and persists it.
 *
 * The WebView login only captures the account token; it never writes USER_ID /
 * USER_DATA / USER_BIO / USER_NITRO. Without those the profile has no avatar,
 * banner, bio or badges, and UserViewModel treats the account as logged out
 * even though TOKEN exists (so the "Login with Discord" button stays visible).
 *
 * Returns null when READY does not arrive within [timeoutMs] or the token was
 * rejected - callers must fall back to cached data instead of crashing.
 */
suspend fun fetchUserInfo(token: String, timeoutMs: Long = 15_000L): User? =
    withTimeoutOrNull(timeoutMs) {
        val deferred = CompletableDeferred<User?>()
        val socket: DiscordWebSocket = object : DiscordWebSocketImpl(token) {
            override fun Payload.handleDispatch(jsonString: String) {
                if (this.t.toString() != "READY") return
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
                close()
                deferred.complete(user)
            }
        }
        socket.connect()
        deferred.await()
    }?.also { user ->
        Prefs[USER_ID] = user.id
        Prefs[USER_BIO] = user.bio
        Prefs[USER_NITRO] = user.nitro
        Prefs[USER_DATA] = Json.encodeToString(user)
    }

/** Legacy callback variant kept for callers outside this module. */
suspend fun getUserInfo(token: String, onInfoSaved: () -> Unit) {
    fetchUserInfo(token)?.let { onInfoSaved() }
}
