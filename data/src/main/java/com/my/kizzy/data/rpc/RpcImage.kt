/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcImage.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */


package com.my.kizzy.data.rpc

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import com.my.kizzy.data.utils.getAppInfo
import com.my.kizzy.data.utils.scaleToMaxDimension
import com.my.kizzy.data.utils.toBitmap
import com.my.kizzy.data.utils.toFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

private const val IMAGE_TAG = "MediaImage"
private const val UPLOAD_TAG = "RpcImageUpload"

// How long a cached upload id stays usable before it is re-uploaded. The kizzy-api /upload host
// returns a Discord attachment-backed id, and those links expire; once expired Discord renders the
// cached id as its default "broken image" placeholder. Re-uploading well before that expiry keeps
// the id fresh, so album art never turns into the placeholder.
private const val IMAGE_CACHE_TTL_MS = 12L * 60 * 60 * 1000

// Serialize each cache's read-check-write. Two concurrent resolves (rapid track changes, or the
// status-roll loop racing a media event) used to each read the map then write back only their own
// entry, dropping the other's and forcing needless re-uploads.
private val artworkCacheMutex = Mutex()
private val appIconCacheMutex = Mutex()

// Album-art / app-icon uploads run on THIS scope, never the caller's. A media event storm cancels
// the service scope's children between events; when the ~269KB art POST ran inline in the
// presence-send coroutine, each event killed the upload mid-flight and, because the upload's
// CancellationException propagated up, aborted the whole send before it ever reached the gateway.
// Running the upload here decouples it from that cancellation: a superseded resolve still cancels
// ITS send (await() rethrows, structured concurrency intact), but the upload finishes once and
// writes the 12h cache, so the very next media event resolves from cache instantly and the
// presence sends. The in-flight maps de-dupe a burst of identical resolves onto one shared upload.
private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val inFlightArtUploads = ConcurrentHashMap<String, Deferred<String?>>()
private val inFlightIconUploads = ConcurrentHashMap<String, Deferred<String?>>()

// Corrupt cache JSON must not throw; reset to empty and let it re-fill instead.
private fun decodeCache(raw: String): HashMap<String, String> =
    runCatching { Json.decodeFromString<HashMap<String, String>>(raw) }.getOrElse { HashMap() }

// Cache values are stored as "id|timestampMillis". A fresh, non-blank, non-expired id is returned;
// anything else (blank, old plain-id format, or past the TTL) is treated as a miss so it re-uploads.
private fun freshId(rawValue: String?): String? {
    val value = rawValue ?: return null
    val sep = value.lastIndexOf('|')
    if (sep <= 0) return null
    val id = value.substring(0, sep)
    val ts = value.substring(sep + 1).toLongOrNull() ?: return null
    if (id.isBlank()) return null
    if (System.currentTimeMillis() - ts > IMAGE_CACHE_TTL_MS) return null
    return id
}

/** The cached id IGNORING the TTL. When a re-upload is NOT possible (the player has stopped
 * putting the artwork in its metadata) the choice is between a possibly-aging id and no art at
 * all, and an aging id is strictly better: worst case Discord shows its placeholder, which is
 * exactly what showing nothing does anyway. This stops album art VANISHING mid-session. */
private fun anyId(rawValue: String?): String? {
    val value = rawValue ?: return null
    val sep = value.lastIndexOf('|')
    val id = if (sep <= 0) value else value.substring(0, sep)
    return id.takeIf { it.isNotBlank() }
}

private fun stamp(id: String): String = "$id|${System.currentTimeMillis()}"

// Keep the newest ARTWORK_CACHE_MAX entries. The cache is one JSON blob re-encoded on every upload;
// unbounded it eventually grows large enough that a truncated write corrupts it, and decodeCache
// then silently resets EVERYTHING to empty, forcing a re-upload of every track at once.
private const val ARTWORK_CACHE_MAX = 300
private fun prunedCache(cache: HashMap<String, String>): Map<String, String> {
    if (cache.size <= ARTWORK_CACHE_MAX) return cache
    return cache.entries
        .sortedByDescending { it.value.substringAfterLast('|').toLongOrNull() ?: 0L }
        .take(ARTWORK_CACHE_MAX)
        .associate { it.key to it.value }
}

/**
 * 指數退避重試：網路切換（Wi-Fi ↔ 行動網路）時第一次 POST 可能失敗，
 * 透過重試可讓封面上傳無縫自癒，不需使用者手動重開 RPC。
 *
 * @param maxAttempts 最大重試次數（含第一次嘗試）
 * @param initialDelayMs 第一次失敗後的初始延遲（毫秒）
 * @param block 要執行的上傳區塊，回傳 null 表示失敗需重試，非 null 表示成功
 *
 * ⚠️ CancellationException 不會被吃掉，會直接向上拋出，
 * 確保協程取消時能正常停止，不會造成記憶體洩漏或重複上傳。
 */
private suspend fun <T> retryUpload(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000L,
    block: suspend () -> T?
): T? {
    var delayMs = initialDelayMs
    for (attempt in 1..maxAttempts) {
        val result = try {
            block()
        } catch (e: Exception) {
            // 🚨 結構化並行核心規範：絕對不能吃掉 CancellationException
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            Log.w(UPLOAD_TAG, "Upload attempt $attempt failed: ${e.message}", e)
            null
        }
        if (result != null) return result
        if (attempt < maxAttempts) {
            Log.w(UPLOAD_TAG, "Upload attempt $attempt failed, retrying in ${delayMs}ms")
            delay(delayMs)
            delayMs *= 2L // 指數退避：1s → 2s → 4s
        }
    }
    Log.e(UPLOAD_TAG, "Upload failed after $maxAttempts attempts")
    return null
}

sealed class RpcImage {
    abstract suspend fun resolveImage(repository: KizzyRepository): String?

    class DiscordImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: KizzyRepository): String {
            return "mp:${image}"
        }
    }

    class ExternalImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: KizzyRepository): String? {
            return repository.getImage(image)
        }
    }

    class ApplicationIcon(val packageName: String, private val context: Context) : RpcImage() {
        override suspend fun resolveImage(repository: KizzyRepository): String? {
            appIconCacheMutex.withLock {
                freshId(decodeCache(Prefs[Prefs.SAVED_IMAGES, "{}"])[packageName])?.let { return it }
            }
            return retrieveImageFromApi(packageName, context, repository)
        }

        private suspend fun retrieveImageFromApi(
            packageName: String,
            context: Context,
            repository: KizzyRepository,
        ): String? {
            // Upload on uploadScope (cancellation-immune) and de-dupe by package; see the note by
            // the in-flight maps for why the caller must not own this upload.
            val upload = inFlightIconUploads.computeIfAbsent(packageName) {
                uploadScope.async {
                    try {
                        val applicationInfo = context.getAppInfo(packageName)
                        val bitmap = applicationInfo.toBitmap(context) ?: return@async null
                        val file = bitmap.toFile(context, "image")
                        if (!file.exists() || file.length() == 0L) {
                            runCatching { file.delete() }
                            return@async null
                        }
                        val response = try {
                            retryUpload {
                                repository.uploadImage(file)?.takeIf { it.isNotBlank() }
                            }
                        } finally {
                            runCatching { file.delete() }
                        }
                        if (response != null) {
                            appIconCacheMutex.withLock {
                                val cache = decodeCache(Prefs[Prefs.SAVED_IMAGES, "{}"])
                                cache[packageName] = stamp(response)
                                Prefs[Prefs.SAVED_IMAGES] = Json.encodeToString(cache)
                            }
                        }
                        response
                    } finally {
                        inFlightIconUploads.remove(packageName)
                    }
                }
            }
            return upload.await()
        }
    }

    class BitmapImage(
        private val context: Context,
        val bitmap: Bitmap?,
        private val packageName: String,
        val title: String,
    ) : RpcImage() {
        override suspend fun resolveImage(repository: KizzyRepository): String? {
            val schema = "${this.packageName}:${this.title}"
            artworkCacheMutex.withLock {
                freshId(decodeCache(Prefs[Prefs.SAVED_ARTWORK, "{}"])[schema])?.let { return it }
            }
            // No bitmap to encode: never upload a blank file (that used to cache a broken asset). But do
            // not give up either - fall back to the last id we ever had for this exact track, even if it
            // is past the refresh TTL. Returning null here is what made working art disappear.
            if (bitmap == null) {
                return artworkCacheMutex.withLock {
                    anyId(decodeCache(Prefs[Prefs.SAVED_ARTWORK, "{}"])[schema])
                }
            }
            // Upload on uploadScope (cancellation-immune) and de-dupe by schema; see the note by the
            // in-flight maps. This is the core fix for "Media RPC never appears on a busy player".
            val bmp = bitmap
            val upload = inFlightArtUploads.computeIfAbsent(schema) {
                uploadScope.async {
                    try {
                        // Album art: downscale and use JPEG. PNG kept the full size
                        // (often ~400 KB) and the upload delayed the presence for seconds.
                        // Discord renders the cover at well under 400 px, so 400 px q80
                        // looks identical and uploads several times faster.
                        val file = bmp.scaleToMaxDimension(400)
                            .toFile(context, "art", Bitmap.CompressFormat.JPEG, 80)
                        if (!file.exists() || file.length() == 0L) {
                            runCatching { file.delete() }
                            return@async null
                        }
                        val result = try {
                            retryUpload {
                                repository.uploadImage(file)?.takeIf { it.isNotBlank() }
                            }
                        } finally {
                            runCatching { file.delete() }
                        }
                        if (result != null) {
                            artworkCacheMutex.withLock {
                                val cache = decodeCache(Prefs[Prefs.SAVED_ARTWORK, "{}"])
                                cache[schema] = stamp(result)
                                Prefs[Prefs.SAVED_ARTWORK] = Json.encodeToString(prunedCache(cache))
                            }
                        }
                        result
                    } finally {
                        inFlightArtUploads.remove(schema)
                    }
                }
            }
            // A failed upload still falls back to the last id this track ever had, so one flaky
            // request mid-session shows the previous (possibly aging) asset instead of blanking art.
            return upload.await() ?: artworkCacheMutex.withLock {
                anyId(decodeCache(Prefs[Prefs.SAVED_ARTWORK, "{}"])[schema])
            }
        }
    }
}
