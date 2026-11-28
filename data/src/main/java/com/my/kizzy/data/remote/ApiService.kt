/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ApiService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */
package com.my.kizzy.data.remote

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import jakarta.inject.Inject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Ktor client wrapper for Kizzy's backend endpoints, GitHub releases, and game catalog API.
 */
class ApiService @Inject constructor(
    private val client: HttpClient,
    @Base private val baseUrl: String,
    @Github private val githubBaseUrl: String,
    @GamesApi private val gamesApiUrl: String,
) {
    suspend fun getImage(url: String) = runCatching {
        client.get {
            url("$baseUrl/image")
            parameter("url", url)
        }
    }

    suspend fun uploadImage(file: File) = runCatching {
        val bytes = file.readBytes()
        client.post {
            url("$baseUrl/upload")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        // Field name must remain "temp" (unquoted) so the upload worker accepts it.
                        append(
                            "temp",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/*")
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                            }
                        )
                    }
                )
            )
        }
    }

    suspend fun getGames() = runCatching {
        client.get {
            url(gamesApiUrl)
        }
    }

    suspend fun getUser(userid: String) = runCatching {
        val encodedId = URLEncoder.encode(userid.trim(), StandardCharsets.UTF_8.toString())
        client.get {
            url("$baseUrl/user/$encodedId")
        }
    }

    suspend fun getContributors() = runCatching {
        client.get {
            url("$baseUrl/contributors")
        }
    }

    suspend fun checkForUpdate() = runCatching {
        client.get {
            url("$githubBaseUrl/repos/himyjan/Kizzy-Enhanced/releases/latest")
        }
    }
}