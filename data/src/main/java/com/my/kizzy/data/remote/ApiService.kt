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

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.io.File
import javax.inject.Inject

class ApiService @Inject constructor(
    private val client: HttpClient,
    @Base private val baseUrl: String,
    @Github private val githubBaseUrl: String,
) {
    suspend fun getImage(url: String) = runCatching {
        client.get {
            url("$baseUrl/image")
            parameter("url", url)
        }
    }

    suspend fun uploadImage(file: File) = runCatching {
        client.post {
            url("$baseUrl/upload")
            setBody(MultiPartFormDataContent(
                formData {
                    // The field name must be exactly "temp". It used to be sent as
                    // "\"temp\"" (with the quotes as part of the name), which the
                    // worker rejected with "No image file uploaded" - so EVERY
                    // cover art / app icon upload failed and Discord never got an
                    // image for the presence.
                    append("temp", file.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/*")
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                    })
                }
            ))
        }
    }

    suspend fun getGames() = runCatching {
        client.get {
            url("$baseUrl/games")
        }
    }

    suspend fun getUser(userid: String) = runCatching {
        client.get {
            url("$baseUrl/user/$userid")
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