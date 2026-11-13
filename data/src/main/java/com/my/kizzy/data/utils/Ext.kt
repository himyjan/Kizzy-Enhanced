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
import android.webkit.MimeTypeMap
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.my.kizzy.data.remote.ApiResponse
import com.my.kizzy.data.remote.ExternalAsset
import com.my.kizzy.data.remote.ImgurResponse
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.preference.Prefs
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.FileOutputStream

suspend fun HttpResponse.toImageURL(): String? {
    return try {
        if (this.status == HttpStatusCode.OK)
            this.body<ImgurResponse>().data.link
        else
            null
    } catch (e: Exception) {
        null
    }
}

suspend fun HttpResponse.toExternalAsset(): String? {
    return try {
        if (this.status == HttpStatusCode.OK)
            "mp:" + this.body<Array<ExternalAsset>>().first().externalAssetPath
        else
            null
    } catch (e: Exception) {
        null
    }
}

suspend fun HttpResponse.toAttachmentAsset(): String? {
    return try {
        if (this.status == HttpStatusCode.OK)
            this.body<ApiResponse>().id
        else
            null
    } catch (e: Exception) {
        null
    }
}

/**
 * Converts Bitmap to file
 * @param context Context
 * @param outputPathFolder Folder name for storing the image file eg (images, media, custom)
 * @param compressFormat PNG keeps transparency (app icons); JPEG is much smaller and is
 *        used for album art. A 500x500 PNG cover was ~400 KB and blocked the presence
 *        update on a slow connection for almost two seconds.
 * @param quality compression quality for lossy formats
 */
fun Bitmap?.toFile(
    context: Context,
    outputPathFolder: String,
    compressFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100,
): File {
    val dir = File(context.filesDir.toString() + File.separator + outputPathFolder)
    dir.mkdirs()
    val extension = if (compressFormat == Bitmap.CompressFormat.JPEG) "jpg" else "png"
    val image = File(dir, "Temp.$extension")
    FileOutputStream(image).use {
        this?.compress(compressFormat, quality, it)
    }
    return image
}

/**
 * Scales the bitmap down so its largest side is at most [maxDimension].
 *
 * Album art is shown at a few dozen pixels in Discord, so uploading a full-size
 * YouTube thumbnail (1280x720) wastes bandwidth and delays the presence update.
 */
fun Bitmap.scaleToMaxDimension(maxDimension: Int): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxDimension || largest <= 0) return this
    val scale = maxDimension.toFloat() / largest
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

@Suppress("DEPRECATION")
fun Context.getAppInfo(packageName: String): ApplicationInfo {
    return this.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
}

@Suppress("DEPRECATION")
fun ApplicationInfo.toBitmap(context: Context): Bitmap? {
    val res = context.packageManager.getResourcesForApplication(this)
    val icon = if (Prefs[Prefs.RPC_USE_LOW_RES_ICON, false])
        context.packageManager.getApplicationIcon(this.packageName)
    else res.getDrawableForDensity(
        this.icon,
        DisplayMetrics.DENSITY_XXXHIGH
    )

    val bitmap = icon?.let {
        Bitmap.createBitmap(
            it.intrinsicWidth,
            icon.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
    }
    val canvas = bitmap?.let { Canvas(it) }
    if (icon != null) {
        if (canvas != null) {
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
        }
    }
    return bitmap
}
fun String.toRpcImage(): RpcImage? {
    return if (this.isBlank())
        null
    else if (this.startsWith("attachments") || this.startsWith("external"))
        RpcImage.DiscordImage(this)
    else
        RpcImage.ExternalImage(this)
}

fun Context.getFileName(uri: Uri): String = "temp_file.${getFileExtension(this, uri)}"

private fun getFileExtension(context: Context, uri: Uri): String? =
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
    } else {
        uri.path?.let { MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(it)).toString()) }
    }

fun Context.uriToFile(uri: Uri): File {
    val file = File(cacheDir,getFileName(uri))
    val inputStream = contentResolver.openInputStream(uri)
    inputStream?.use { input ->
        file.outputStream().use { out ->
            input.copyTo(out)
        }
    }
    return file
}

fun Context.shareAsFile(content: String?, fileName: String) {
    val tempFile = File("$filesDir/$fileName")
    if (tempFile.exists()) {
        tempFile.delete()
    }
    tempFile.writeText(content ?: "")
    shareFile(tempFile)
}

fun Context.shareFile(file: File?) {
    if (file == null) return
    val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
    val intent = ShareCompat.IntentBuilder(this).setType("text/plain")
        .setStream(uri).intent.setAction(Intent.ACTION_SEND).setDataAndType(uri, "text/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    this.startActivity(Intent.createChooser(intent, "Share File With"))
}