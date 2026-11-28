/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * SvgString.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_settings.style.svg

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kyant.monet.TonalPalettes
import java.util.Locale

private val DYNAMIC_COLOR_FILL_REGEX = Regex("""fill="([^"]+)"""")
private val TOKEN_REGEX = Regex("""^([a-zA-Z]+)(\d+(?:\.\d+)?)$""")

fun String.parseDynamicColor(
    tonalPalettes: TonalPalettes,
    isDarkTheme: Boolean,
): String = DYNAMIC_COLOR_FILL_REGEX.replace(this) { matchResult ->
    val value = matchResult.groupValues[1]
    if (value.startsWith("#")) return@replace matchResult.value

    val match = TOKEN_REGEX.matchEntire(value) ?: return@replace matchResult.value
    val scheme = match.groupValues[1]
    val tone = match.groupValues[2].toDoubleOrNull()?.autoToDarkTone(isDarkTheme)
        ?: return@replace matchResult.value

    val argb = when (scheme) {
        "p" -> tonalPalettes.accent1(tone)
        "s" -> tonalPalettes.accent2(tone)
        "t" -> tonalPalettes.accent3(tone)
        "n" -> tonalPalettes.neutral1(tone)
        "nv" -> tonalPalettes.neutral2(tone)
        else -> Color.Transparent
    }.toArgb()

    "fill=\"${String.format(Locale.ROOT, "#%06X", 0xFFFFFF and argb)}\""
}

internal fun Double.autoToDarkTone(isDarkTheme: Boolean): Double =
    if (!isDarkTheme) this
    else when (this) {
        10.0 -> 99.0
        20.0 -> 95.0
        25.0 -> 90.0
        30.0 -> 90.0
        40.0 -> 80.0
        50.0 -> 60.0
        60.0 -> 50.0
        70.0 -> 40.0
        80.0 -> 40.0
        90.0 -> 30.0
        95.0 -> 20.0
        98.0 -> 10.0
        99.0 -> 10.0
        100.0 -> 20.0
        else -> this
    }