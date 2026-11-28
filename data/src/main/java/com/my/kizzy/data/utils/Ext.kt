/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Ext.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.DisplayMetrics
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.my.kizzy.data.remote.ApiResponse
import com.my.kizzy.data.remote.ExternalAsset
import com.my.kizzy.data.remote.ExternalAssetsResponse
import com.my.kizzy.data.remote.ImgurResponse
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.preference.Prefs
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private val MIME_TO_EXTENSION = mapOf(
    "image/jpeg" to "jpg",
    "image/jpg" to "jpg",
    "image/png" to "png",
    "image/webp" to "webp",
    "image/gif" to "gif",
    "image/heic" to "heic",
    "image/heif" to "heif",
    "image/bmp" to "bmp",
    "text/plain" to "txt",
    "application/json" to "json",
    "application/pdf" to "pdf",
    "application/zip" to "zip",
)

private val extJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Extracts the uploaded image link from a successful Imgur response. */
suspend fun HttpResponse.toImageURL(): String? {
    return try {
        if (this.status == HttpStatusCode.OK) {
            this.body<ImgurResponse>().data.link.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** Extracts an `mp:` external asset path from a successful Discord external-assets response. */
suspend fun HttpResponse.toExternalAsset(): String? {
    return try {
        if (this.status != HttpStatusCode.OK) return null
        val raw = this.bodyAsText().trim()
        if (raw.isEmpty()) return null

        val path = if (raw.startsWith("[")) {
            extJson.decodeFromString<List<ExternalAsset>>(raw)
                .firstOrNull()?.externalAssetPath
        } else if (raw.contains("\"data\"")) {
            extJson.decodeFromString<ExternalAssetsResponse>(raw)
                .data.firstOrNull()?.externalAssetPath
        } else {
            extJson.decodeFromString<ExternalAsset>(raw).externalAssetPath
        }?.trim()

        if (!path.isNullOrEmpty()) {
            if (path.startsWith("mp:")) path else "mp:$path"
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/** Extracts the uploaded attachment ID from a successful Kizzy worker response. */
suspend fun HttpResponse.toAttachmentAsset(): String? {
    return try {
        if (this.status == HttpStatusCode.OK) {
            this.body<ApiResponse>().id.takeIf { it.isNotBlank() }
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * Converts a [Bitmap] to a file under `filesDir/<outputPathFolder>`.
 *
 * Returns `null` if the bitmap is `null` or compression fails.
 */
fun Bitmap?.toFile(
    context: Context,
    outputPathFolder: String,
    compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100,
): File? {
    val bmp = this ?: return null
    val dir = File(context.filesDir, outputPathFolder)
    if (!dir.exists() && !dir.mkdirs()) return null
    val extension = if (compressFormat == Bitmap.CompressFormat.JPEG) "jpg" else "png"
    val image = File(dir, "Temp.$extension")
    val ok = runCatching {
        FileOutputStream(image).use { out ->
            bmp.compress(compressFormat, quality.coerceIn(0, 100), out)
        }
    }.getOrDefault(false)
    return if (ok) image else {
        image.delete()
        null
    }
}

/**
 * Scales the bitmap down so its largest side is at most [maxDimension].
 */
fun Bitmap.scaleToMaxDimension(maxDimension: Int): Bitmap {
    if (maxDimension <= 0) return this
    val largest = maxOf(width, height)
    if (largest <= maxDimension || largest <= 0) return this
    val scale = maxDimension.toFloat() / largest
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

/** Queries [ApplicationInfo] with metadata flags for [packageName]. */
fun Context.getAppInfo(packageName: String): ApplicationInfo {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        this.packageManager.getApplicationInfo(
            packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        this.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    }
}

/** Renders an [ApplicationInfo] icon into a non-null [Bitmap] when available. */
fun ApplicationInfo.toBitmap(context: Context): Bitmap? {
    return runCatching {
        val res = context.packageManager.getResourcesForApplication(this)
        val icon = if (Prefs[Prefs.RPC_USE_LOW_RES_ICON, false]) {
            context.packageManager.getApplicationIcon(this.packageName)
        } else {
            @Suppress("DEPRECATION")
            res.getDrawableForDensity(this.icon, DisplayMetrics.DENSITY_XXXHIGH)
        } ?: return@runCatching null

        val width = icon.intrinsicWidth.coerceAtLeast(1)
        val height = icon.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, canvas.width, canvas.height)
        icon.draw(canvas)
        bitmap
    }.getOrNull()
}

/** Converts a raw asset path or URL string into an [RpcImage]. */
fun String.toRpcImage(): RpcImage? {
    val trimmed = this.trim()
    return when {
        trimmed.isEmpty() -> null
        trimmed.startsWith("attachments") ||
                trimmed.startsWith("external") ||
                trimmed.startsWith("mp:") ||
                trimmed.startsWith("app-assets/") -> RpcImage.DiscordImage(trimmed)
        else -> RpcImage.ExternalImage(trimmed)
    }
}

/**
 * Extracts the URL/identifier string from an [RpcImage] without resolving or uploading it.
 */
fun RpcImage.toUrlString(): String? = when (this) {
    is RpcImage.DiscordImage -> image
    is RpcImage.ExternalImage -> image
    is RpcImage.ApplicationIcon -> null
    is RpcImage.BitmapImage -> null
}

/** Builds a temporary filename using the resolved extension for [uri]. */
fun Context.getFileName(uri: Uri): String {
    val ext = getFileExtension(this, uri)
    return if (ext.isNullOrBlank()) "temp_file" else "temp_file.$ext"
}

private fun getFileExtension(context: Context, uri: Uri): String? {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        val normalizedMime = Intent.normalizeMimeType(context.contentResolver.getType(uri))
        if (normalizedMime != null) {
            MIME_TO_EXTENSION[normalizedMime]?.let { return it }
            val subtype = normalizedMime.substringAfter('/', "").substringBefore('+')
            if (subtype.isNotBlank()) return subtype
        }
    }
    val path = uri.path ?: return null
    val dotIndex = path.lastIndexOf('.')
    if (dotIndex < 0 || dotIndex == path.lastIndex) return null
    return path.substring(dotIndex + 1).lowercase()
}

/** Copies content from [uri] into a temporary file inside [Context.getCacheDir]. */
fun Context.uriToFile(uri: Uri): File? {
    val file = File(cacheDir, getFileName(uri))
    return runCatching {
        val inputStream = contentResolver.openInputStream(uri) ?: return@runCatching null
        inputStream.use { input ->
            FileOutputStream(file).use { out ->
                input.copyTo(out)
            }
        }
        file
    }.getOrElse {
        file.delete()
        null
    }
}

/** Writes [content] to a temporary file and launches a share sheet for it. */
fun Context.shareAsFile(content: String?, fileName: String) {
    val safeName = File(fileName).name.ifBlank { "shared.txt" }
    val tempFile = File(filesDir, safeName)
    if (tempFile.exists()) {
        tempFile.delete()
    }
    tempFile.writeText(content ?: "")
    shareFile(tempFile)
}

/** Launches a chooser share intent for [file] via [FileProvider]. */
fun Context.shareFile(file: File?) {
    if (file == null || !file.exists()) return
    runCatching {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setStream(uri)
            .intent
            .setAction(Intent.ACTION_SEND)
            .setDataAndType(uri, "text/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        this.startActivity(Intent.createChooser(intent, "Share File With"))
    }
}