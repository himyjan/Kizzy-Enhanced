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
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.Payload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

suspend fun fetchUserInfo(token: String, timeoutMs: Long = 15_000L): User? =
    withTimeoutOrNull(timeoutMs) {
        val isAccountToken = token.count { it == '.' } >= 2 || token.startsWith("mfa.")
        val identifyToken = if (token.isNotBlank() && !isAccountToken) "Bearer $token" else token
        val deferred = CompletableDeferred<User?>()
        val socket: DiscordWebSocket = object : DiscordWebSocketImpl(identifyToken) {
            override fun Payload.handleDispatch(jsonString: String) {
                if (this.t.toString() != "READY") return
                val ready = decodeReady(jsonString) ?: return
                val readyUser = ready.user ?: return
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
                    special = ready.userSettings?.customStatus?.text.orEmpty().takeIf { it.isNotBlank() },
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
    }?.also(::persistDiscordUser)

suspend fun getUserInfo(token: String, onInfoSaved: () -> Unit) {
    fetchUserInfo(token)?.let { onInfoSaved() }
}

private val restJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun persistDiscordUser(user: User) {
    val existingBio = (Prefs[USER_BIO, ""]).takeIf { it.isNotBlank() }
    val merged = user.copy(
        bio = user.bio?.takeIf { it.isNotBlank() } ?: existingBio,
        nitro = user.nitro || Prefs[USER_NITRO, false],
    )
    Prefs[USER_ID] = merged.id
    Prefs[USER_BIO] = merged.bio
    Prefs[USER_NITRO] = merged.nitro
    Prefs[USER_DATA] = restJson.encodeToString(merged)
}

suspend fun fetchDiscordUser(token: String): User? {
    if (token.isBlank()) return null

    val authHeaders = if (token.count { it == '.' } >= 2) {
        listOf(token, "Bearer $token")
    } else {
        listOf("Bearer $token", token)
    }

    HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }.use { client ->
        for (auth in authHeaders) {
            val body = try {
                val response = client.get("https://discord.com/api/v10/users/@me") {
                    header("Authorization", auth)
                    header("Accept", "application/json")
                }
                if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
            } catch (_: Exception) {
                null
            } ?: continue

            val user = runCatching {
                val obj = restJson.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
                val parsed = restJson.decodeFromString<User>(body)
                val premiumType = (obj["premium_type"] as? JsonPrimitive)?.intOrNull
                parsed.copy(
                    nitro = premiumType in 1..3 || parsed.nitro,
                    verified = (obj["verified"] as? JsonPrimitive)?.booleanOrNull ?: parsed.verified,
                )
            }.getOrNull() ?: continue

            if (user.id.isNullOrBlank()) continue

            persistDiscordUser(user)
            return user
        }
    }
    return null
}

private fun extractBioFromJson(obj: JsonObject): String? {
    val direct = (obj["bio"] as? JsonPrimitive)?.contentOrNull
        ?: (obj["about_me"] as? JsonPrimitive)?.contentOrNull
    if (!direct.isNullOrBlank()) return direct

    val userObj = obj["user"] as? JsonObject
    val fromUser = (userObj?.get("bio") as? JsonPrimitive)?.contentOrNull
        ?: (userObj?.get("about_me") as? JsonPrimitive)?.contentOrNull
    if (!fromUser.isNullOrBlank()) return fromUser

    val profileObj = obj["user_profile"] as? JsonObject
    val fromProfile = (profileObj?.get("bio") as? JsonPrimitive)?.contentOrNull
        ?: (profileObj?.get("about_me") as? JsonPrimitive)?.contentOrNull
    return fromProfile?.takeIf { it.isNotBlank() }
}

suspend fun fetchDiscordUserProfile(token: String): String? {
    if (token.isBlank()) return null

    val authHeaders = if (token.count { it == '.' } >= 2) {
        listOf(token, "Bearer $token")
    } else {
        listOf("Bearer $token", token)
    }

    HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 8_000
            socketTimeoutMillis = 8_000
        }
    }.use { client ->
        for (auth in authHeaders) {
            val body = try {
                val response = client.get("https://discord.com/api/v10/users/@me/profile") {
                    header("Authorization", auth)
                    header("Accept", "application/json")
                }
                if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
            } catch (_: Exception) {
                null
            } ?: continue

            if (body.isEmpty()) continue

            val bio = runCatching {
                val obj = restJson.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
                extractBioFromJson(obj)
            }.getOrNull()

            if (!bio.isNullOrBlank()) return bio
        }
    }
    return null
}