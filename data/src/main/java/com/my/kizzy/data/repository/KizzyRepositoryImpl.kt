/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyRepositoryImpl.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.repository

import com.my.kizzy.data.remote.ApiService
import com.my.kizzy.data.remote.GamesResponse
import com.my.kizzy.data.remote.ImgurApiService
import com.my.kizzy.data.remote.toGame
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.data.utils.toAttachmentAsset
import com.my.kizzy.data.utils.toExternalAsset
import com.my.kizzy.data.utils.toImageURL
import com.my.kizzy.domain.model.Contributor
import com.my.kizzy.domain.model.Game
import com.my.kizzy.domain.model.release.Release
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Default implementation of [KizzyRepository] backed by Ktor remote services,
 * shared preferences, and `kotlinx.serialization`.
 */
class KizzyRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val imgurApi: ImgurApiService,
    private val json: Json,
) : KizzyRepository {

    override suspend fun getImage(url: String): String? = withContext(Dispatchers.IO) {
        try {
            if (Prefs[Prefs.USE_IMGUR, false]) {
                val token = Prefs[Prefs.TOKEN, ""]
                if (token.isBlank()) return@withContext null
                imgurApi.getImage(url, token).getOrNull()?.toExternalAsset()
            } else {
                api.getImage(url).getOrNull()?.toAttachmentAsset()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun uploadImage(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile) return@withContext null
        try {
            if (Prefs[Prefs.USE_IMGUR, false]) {
                val clientId = Prefs[Prefs.IMGUR_CLIENT_ID, Constants.IMGUR_CLIENT_ID]
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?: Constants.IMGUR_CLIENT_ID
                val uploadedUrl = imgurApi.uploadImage(file, clientId)
                    .getOrNull()
                    ?.toImageURL()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                getImage(uploadedUrl)
            } else {
                api.uploadImage(file).getOrNull()?.toAttachmentAsset()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getGames(): List<Game> = withContext(Dispatchers.IO) {
        try {
            val response = api.getGames().getOrNull() ?: return@withContext emptyList()
            if (!response.status.isSuccess()) return@withContext emptyList()
            val body = response.bodyAsText()
            if (body.isBlank()) return@withContext emptyList()
            json.decodeFromString<List<GamesResponse>>(body).map { it.toGame() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getUser(userid: String): User = withContext(Dispatchers.IO) {
        if (userid.isBlank()) return@withContext User()
        try {
            val response = api.getUser(userid).getOrNull() ?: return@withContext User()
            if (!response.status.isSuccess()) return@withContext User()
            response.body<User>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            User()
        }
    }

    override suspend fun getContributors(): List<Contributor> = withContext(Dispatchers.IO) {
        try {
            val response = api.getContributors().getOrNull() ?: return@withContext emptyList()
            if (!response.status.isSuccess()) return@withContext emptyList()
            response.body<List<Contributor>>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun checkForUpdate(): Release = withContext(Dispatchers.IO) {
        try {
            api.checkForUpdate().getOrNull()?.releaseBody()
                ?: Prefs.getSavedLatestRelease()
                ?: Release()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Prefs.getSavedLatestRelease() ?: Release()
        }
    }
}

/**
 * Parses a GitHub release response on HTTP 200 and caches it in [Prefs],
 * falling back to the last saved release on non-200 or decode failure.
 */
suspend fun HttpResponse.releaseBody(): Release {
    return if (this.status.value == HttpStatusCode.OK.value) {
        try {
            val release = this.body<Release>()
            Prefs.saveLatestRelease(release)
            release
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Prefs.getSavedLatestRelease() ?: Release()
        }
    } else {
        Prefs.getSavedLatestRelease() ?: Release()
    }
}