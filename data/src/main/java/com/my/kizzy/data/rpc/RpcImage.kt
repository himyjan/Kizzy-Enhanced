/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcImage.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import android.content.Context
import android.graphics.Bitmap
import com.my.kizzy.data.utils.getAppInfo
import com.my.kizzy.data.utils.scaleToMaxDimension
import com.my.kizzy.data.utils.toBitmap
import com.my.kizzy.data.utils.toFile
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File

private const val MAX_COVER_DIMENSION = 512
private const val MAX_ICON_DIMENSION = 256
private const val JPEG_QUALITY = 85

/**
 * Represents an image source used in Discord Rich Presence payloads and
 * local UI/notification previews.
 */
@Serializable
sealed class RpcImage {

    /**
     * Resolves this image into a Discord-compatible asset identifier or URL
     * via [kizzyRepository].
     */
    abstract suspend fun resolveImage(
        kizzyRepository: KizzyRepository,
        context: Context? = null,
    ): String?

    @Serializable
    data class ApplicationIcon(
        val packageName: String,
        @Transient
        val context: Context? = null,
    ) : RpcImage() {

        override suspend fun resolveImage(
            kizzyRepository: KizzyRepository,
            context: Context?,
        ): String? {
            if (packageName.isBlank()) return null
            val targetContext = this.context ?: context ?: return null
            val bitmap = try {
                targetContext.getAppInfo(packageName)
                    .toBitmap(targetContext)
                    ?.scaleToMaxDimension(MAX_ICON_DIMENSION)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null

            val compressFormat = if (Prefs[Prefs.RPC_USE_LOW_RES_ICON, false]) {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }
            val tempFile: File = bitmap.toFile(
                context = targetContext,
                outputPathFolder = "images/icons",
                compressFormat = compressFormat,
                quality = JPEG_QUALITY,
            ) ?: return null

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return null
            }

            return try {
                kizzyRepository.uploadImage(tempFile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                tempFile.delete()
            }
        }
    }

    @Serializable
    data class BitmapImage(
        @Transient
        val context: Context? = null,
        @Transient
        val bitmap: Bitmap? = null,
        val packageName: String = "",
        val title: String = "",
    ) : RpcImage() {

        override suspend fun resolveImage(
            kizzyRepository: KizzyRepository,
            context: Context?,
        ): String? {
            val sourceBitmap = bitmap ?: return null
            val targetContext = this.context ?: context ?: return null
            val scaledBitmap = sourceBitmap.scaleToMaxDimension(MAX_COVER_DIMENSION)

            val tempFile: File = scaledBitmap.toFile(
                context = targetContext,
                outputPathFolder = "images/covers",
                compressFormat = Bitmap.CompressFormat.JPEG,
                quality = JPEG_QUALITY,
            ) ?: return null

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return null
            }

            return try {
                kizzyRepository.uploadImage(tempFile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } finally {
                tempFile.delete()
            }
        }
    }

    @Serializable
    data class DiscordImage(
        val image: String,
    ) : RpcImage() {

        override suspend fun resolveImage(
            kizzyRepository: KizzyRepository,
            context: Context?,
        ): String? {
            val trimmed = image.trim()
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith("mp:") ||
                        trimmed.startsWith("attachments/") ||
                        trimmed.startsWith("external/") ||
                        trimmed.startsWith("app-assets/") -> trimmed
                trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                    kizzyRepository.getImage(trimmed)
                else -> "mp:$trimmed"
            }
        }
    }

    @Serializable
    data class ExternalImage(
        val image: String,
    ) : RpcImage() {

        override suspend fun resolveImage(
            kizzyRepository: KizzyRepository,
            context: Context?,
        ): String? {
            val trimmed = image.trim()
            if (trimmed.isEmpty()) return null
            return kizzyRepository.getImage(trimmed)
        }
    }
}