/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppIcon.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The icons of the installed apps.
 *
 * Reading an icon from the package manager is a blocking call that talks to the
 * system. Doing it inside the composition stalled the UI thread for every row
 * that appeared while scrolling an app list, so the icons are read in the
 * background and cached.
 *
 * The icons are cached as bitmaps and not as drawables: a drawable has a single
 * bounds rectangle, so the same instance cannot be drawn in several rows with
 * different sizes - the rows would fight over the bounds and most of them would
 * stay empty.
 */
object AppIconCache {

    /** How many icons are kept. The lists are long, but only a few are visible. */
    private const val MAX_CACHED = 96

    private val bitmaps = LruCache<String, Bitmap>(MAX_CACHED)
    private val lock = Any()

    /** The icon if it was loaded before, without touching the package manager. */
    fun peek(packageName: String, sizePx: Int): Bitmap? =
        synchronized(lock) { bitmaps.get("$packageName@$sizePx") }

    /** Reads the icon of an app and scales it down to the given size. */
    suspend fun load(context: Context, packageName: String, sizePx: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            peek(packageName, sizePx) ?: runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx)
            }.getOrNull()?.also { bitmap ->
                synchronized(lock) { bitmaps.put("$packageName@$sizePx", bitmap) }
            }
        }
}

/**
 * The icon of an app, loaded in the background.
 *
 * @return null while the icon is still being loaded
 */
@Composable
fun rememberAppIcon(packageName: String, sizePx: Int = 144): Bitmap? {
    val context = LocalContext.current
    var icon by remember(packageName, sizePx) {
        mutableStateOf(AppIconCache.peek(packageName, sizePx))
    }
    LaunchedEffect(packageName, sizePx) {
        if (icon == null) icon = AppIconCache.load(context, packageName, sizePx)
    }
    return icon
}

/**
 * The icon of an app as a bitmap, loaded and scaled in the background.
 *
 * @return null while the icon is still being loaded
 */
@Composable
fun rememberAppIconBitmap(packageName: String, sizePx: Int = 144): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName, sizePx) {
        mutableStateOf(AppIconCache.peek(packageName, sizePx)?.asImageBitmap())
    }
    LaunchedEffect(packageName, sizePx) {
        if (bitmap == null) {
            bitmap = AppIconCache.load(context, packageName, sizePx)?.asImageBitmap()
        }
    }
    return bitmap
}

/**
 * Draws the drawable into a bitmap of the given size.
 */
private fun Drawable.toBitmap(sizePx: Int): Bitmap? = runCatching {
    val width = intrinsicWidth.takeIf { it > 0 } ?: sizePx
    val height = intrinsicHeight.takeIf { it > 0 } ?: sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    if (width == sizePx && height == sizePx) bitmap
    else Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
}.getOrNull()
