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
import kotlinx.coroutines.CancellationException

// Reuse a single HttpClient instance across requests for connection pooling.
private val httpClient = HttpClient(OkHttp) {
    engine {
        config {
            retryOnConnectionFailure(true)
        }
    }
}

/**
 * Resolves an [RpcImage] into a [Bitmap] and applies it as the notification's large icon
 * when available.
 */
suspend fun Notification.Builder.setLargeIcon(
    rpcImage: RpcImage?,
    context: Context,
): Notification.Builder {
    if (rpcImage == null) {
        return this
    }

    val data: Any? = when (rpcImage) {
        is RpcImage.ApplicationIcon -> runCatching {
            context.getAppInfo(rpcImage.packageName).toBitmap(context)
        }.getOrNull()
        is RpcImage.BitmapImage -> rpcImage.bitmap
        is RpcImage.DiscordImage -> "https://cdn.discordapp.com/${rpcImage.image}"
        is RpcImage.ExternalImage -> rpcImage.image
    }

    val bitmap: Bitmap? = when (data) {
        is Bitmap -> data
        is String -> {
            if (data.isBlank()) {
                null
            } else {
                try {
                    val bytes = httpClient.get(data).bodyAsBytes()
                    if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }
        else -> null
    }

    if (bitmap != null) {
        this.setLargeIcon(bitmap)
    }
    return this
}

/**
 * Sets the large icon from a [Bitmap] directly without any network download.
 */
internal fun Notification.Builder.setLargeIcon(bitmap: Bitmap): Notification.Builder {
    this.setLargeIcon(bitmap)
    return this
}