package com.my.kizzy.feature_rpc_base

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.utils.getAppInfo
import com.my.kizzy.data.utils.toBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

// Reuse a single HttpClient instance across requests for optimal connection pooling
private val httpClient = HttpClient(OkHttp) {
    engine {
        config {
            retryOnConnectionFailure(true)
        }
    }
}

internal suspend fun Notification.Builder.setLargeIcon(
    rpcImage: RpcImage?,
    context: Context,
): Notification.Builder {
    if (rpcImage == null) {
        return this
    }

    val data = when (rpcImage) {
        is RpcImage.ApplicationIcon -> context.getAppInfo(rpcImage.packageName)?.toBitmap(context)
        is RpcImage.BitmapImage -> rpcImage.bitmap
        is RpcImage.DiscordImage -> "https://cdn.discordapp.com/${rpcImage.image}"
        is RpcImage.ExternalImage -> rpcImage.image
    }

    val bitmap = when (data) {
        is Bitmap -> data
        is String -> {
            try {
                val bytes = httpClient.get(data).bodyAsBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        else -> null
    }

    if (bitmap != null) {
        this.setLargeIcon(bitmap)
    }
    return this
}