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
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

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
    if (token.isBlank()) null
    else withTimeoutOrNull(timeoutMs) {
        // Account tokens look like "id.timestamp.hmac" where the first part is a numeric user ID.
        // OAuth2 bearer tokens (e.g., ya29.a0AfH6B...) also have dots but their first part is not numeric.
        val parts = token.split(".")
        val isAccountToken = parts.size >= 3 && parts[0].toLongOrNull() != null

        if (isAccountToken) {
            // Gateway accepts account tokens
            val deferred = CompletableDeferred<User?>()
            val socket: DiscordWebSocket = object : DiscordWebSocketImpl(token) {
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
                        // Custom status ("About Me") lives under user_settings in the
                        // READY payload, not under user.bio.
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
            return@withTimeoutOrNull deferred.await()
        } else {
            // OAuth2 bearer token - use REST API instead of gateway (gateway rejects OAuth2 tokens with 4004)
            return@withTimeoutOrNull fetchDiscordUserViaRest(token)
        }
    }

/** Legacy callback variant kept for callers outside this module. */
suspend fun getUserInfo(token: String, onInfoSaved: () -> Unit) {
    fetchUserInfo(token)?.let { onInfoSaved() }
}

private val restJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Fetches the full account identity from Discord's REST API (`/users/@me`) and
 * persists it so the profile card has an avatar, banner, bio and badges.
 *
 * The gateway-based [fetchUserInfo] cannot be used here: Discord does not accept
 * an OAuth2 bearer token on the gateway, so the READY payload never arrives and
 * USER_DATA is never written (profile shows no avatar/bio and keeps the login
 * button visible). The REST endpoint works for both OAuth2 bearer tokens and
 * raw account tokens, so both Authorization schemes are tried.
 *
 * Returns null when the token is rejected or the request fails.
 */
suspend fun fetchDiscordUser(token: String): User? {
    if (token.isBlank()) return null

    // Account tokens look like "id.timestamp.hmac" (dots); OAuth2 bearer tokens don't.
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
                parsed.copy(
                    // Discord reports subscription via premium_type, not "nitro".
                    nitro = (obj["premium_type"] as? JsonPrimitive)?.intOrNull in 1..3,
                    verified = (obj["verified"] as? JsonPrimitive)?.booleanOrNull ?: parsed.verified,
                )
            }.getOrNull() ?: continue

            if (user.id.isNullOrBlank()) continue

            // Also fetch the custom status (About Me) from /users/@me/profile
            // since /users/@me does not return bio.
            val profileBio = fetchDiscordUserProfile(token)
            val userWithBio = user.copy(bio = profileBio?.takeIf { it.isNotBlank() } ?: user.bio)

            Prefs[USER_ID] = userWithBio.id
            Prefs[USER_BIO] = userWithBio.bio
            Prefs[USER_NITRO] = userWithBio.nitro
            Prefs[USER_DATA] = restJson.encodeToString(userWithBio)
            return userWithBio
        }
    }
    return null
}

/**
 * Fetches the full account identity from Discord's REST API (/users/@me) and
 * persists it so the profile card has an avatar, banner, bio and badges.
 * The gateway-based fetchUserInfo cannot be used here: Discord does not accept
 * an OAuth2 bearer token on the gateway, so the READY payload never arrives and
 * USER_DATA is never written (profile shows no avatar/bio and keeps the login
 * button visible). The REST endpoint works for both OAuth2 bearer tokens and
 * raw account tokens, so both Authorization schemes are tried.
 *
 * Returns null when the token is rejected or the request fails.
 */
suspend fun fetchDiscordUserViaRest(token: String): User? {
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
                restJson.decodeFromString<User>(body)
            }.getOrNull()
            if (user?.id.isNullOrBlank() == true) continue

            Prefs[USER_ID] = user!!.id
            Prefs[USER_BIO] = user!!.bio
            Prefs[USER_NITRO] = user!!.nitro
            Prefs[USER_DATA] = restJson.encodeToString(user!!)
            return user
        }
    }
    return null
}

/**
 * Best-effort fetch of the current user's client profile, which can contain
 * the About Me/bio field. Discord's public /users/@me endpoint does not return
 * bio, but the client profile endpoint may return it for OAuth2 bearer tokens.
 * Any failure is ignored so the normal profile load still succeeds.
 */
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

            val bio = runCatching {
                val obj = restJson.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
                val userObj = obj["user"] as? JsonObject
                val bioField = userObj?.get("bio") as? JsonPrimitive
                val aboutMeField = userObj?.get("about_me") as? JsonPrimitive
                bioField?.content ?: aboutMeField?.content
            }.getOrNull()

            if (!bio.isNullOrBlank()) return bio
        }
    }
    return null
}
