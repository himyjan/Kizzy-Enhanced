/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Ext.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */


package com.my.kizzy.feature_rpc_base

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.utils.getAppInfo
import com.my.kizzy.data.utils.toBitmap
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend fun Notification.Builder.setLargeIcon(
    rpcImage: RpcImage?,
    context: Context,
): Notification.Builder = suspendCancellableCoroutine { continuation ->
    if (rpcImage == null) {
        continuation.resume(this)
        return@suspendCancellableCoroutine
    }

    val data = when (rpcImage) {
        is RpcImage.ApplicationIcon -> context.getAppInfo(rpcImage.packageName).toBitmap(context)
        is RpcImage.BitmapImage -> rpcImage.bitmap
        is RpcImage.DiscordImage -> "https://cdn.discordapp.com/${rpcImage.image}"
        is RpcImage.ExternalImage -> rpcImage.image
    }

    val bitmap = when (data) {
        is Bitmap -> data
        is String -> {
            try {
                val url = URL(data)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val decoded = BitmapFactory.decodeStream(input)
                input.close()
                connection.disconnect()
                decoded
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }

    if (bitmap != null) {
        continuation.resume(this@setLargeIcon.setLargeIcon(bitmap))
    } else {
        continuation.resume(this@setLargeIcon)
    }
}
