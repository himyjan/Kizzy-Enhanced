/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Theme.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors
import com.kyant.monet.dynamicColorScheme
import com.my.kizzy.preference.ThemeStyle

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }

// ---------------------------------------------------------------------------
// Discord theme schemes - values mirror Discord's own dark and light UI
// (see discord.com/branding and the shipped DISCORD_* tokens).
// Built on the M3 base schemes so fixed/tonal slots get sane defaults.
// ---------------------------------------------------------------------------
private val DiscordDark = darkColorScheme(
    primary = DISCORD_BLURPLE,
    onPrimary = Color.White,
    primaryContainer = DISCORD_BLURPLE,
    onPrimaryContainer = Color.White,
    secondary = DISCORD_BLURPLE,
    onSecondary = Color.White,
    secondaryContainer = DISCORD_CARD,
    onSecondaryContainer = DISCORD_TEXT,
    tertiary = DISCORD_GREEN,
    onTertiary = Color(0xFF0B3D1E),
    tertiaryContainer = DISCORD_CARD,
    onTertiaryContainer = DISCORD_GREEN,
    error = DISCORD_RED,
    onError = Color.White,
    errorContainer = DISCORD_RED,
    onErrorContainer = Color.White,
    background = Color.Black,
    onBackground = DISCORD_TEXT,
    surface = Color.Black,
    onSurface = DISCORD_TEXT,
    surfaceVariant = DISCORD_CARD,
    onSurfaceVariant = DISCORD_SUBTEXT,
    surfaceTint = DISCORD_BLURPLE,
    inverseSurface = DISCORD_TEXT,
    inverseOnSurface = DISCORD_BG,
    outline = DISCORD_TRACK_COLOR,
    outlineVariant = DISCORD_CARD,
    scrim = Color.Black,
    surfaceBright = DISCORD_BG,
    surfaceDim = Color.Black,
    surfaceContainer = DISCORD_CARD,
    surfaceContainerHigh = DISCORD_CARD,
    surfaceContainerHighest = DISCORD_LIGHT_DARK,
    surfaceContainerLow = DISCORD_BG,
    surfaceContainerLowest = Color.Black,
)

private val DiscordLight = lightColorScheme(
    primary = DISCORD_BLURPLE,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF1E1F4B),
    secondary = DISCORD_BLURPLE,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFFFFF),
    onSecondaryContainer = Color(0xFF060607),
    tertiary = Color(0xFF23A55A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F5DF),
    onTertiaryContainer = Color(0xFF0B3D1E),
    error = DISCORD_RED,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF060607),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF060607),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF4E5058),
    surfaceTint = DISCORD_BLURPLE,
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF2F3F5),
    outline = Color(0xFFC4C6CE),
    outlineVariant = Color(0xFFE3E5E8),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDADCE0),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F3F5),
    surfaceContainerHighest = Color(0xFFE3E5E8),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainerLowest = Color.White,
)

@Composable
fun getColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    isDynamicColorEnabled: Boolean = LocalDynamicColorSwitch.current,
    themeStyle: Int = LocalThemeStyle.current,
): ColorScheme {
    val colorScheme = when (themeStyle) {
        // The Discord styles ignore seed/dynamic color and dark-mode: the
        // palette itself decides whether the app is dark or light.
        ThemeStyle.DISCORD_DARK -> DiscordDark
        ThemeStyle.DISCORD_LIGHT -> DiscordLight
        else -> when {
            DynamicColors.isDynamicColorAvailable() && isDynamicColorEnabled -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            else -> dynamicColorScheme(!darkTheme)
        }.run {
            if (isHighContrastModeEnabled && darkTheme) copy(
                surface = Color.Black,
                background = Color.Black,
            )
            else this
        }
    }
    return colorScheme
}
@Composable
fun KizzyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isHighContrastModeEnabled: Boolean = false,
    isDynamicColorEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(
        darkTheme,
        isHighContrastModeEnabled,
        isDynamicColorEnabled,
    )
    val window = LocalView.current.context.findWindow()
    val view = LocalView.current
    val themeStyle = LocalThemeStyle.current
    val isLightMode = themeStyle == ThemeStyle.DISCORD_LIGHT || (!darkTheme && themeStyle != ThemeStyle.DISCORD_DARK)

    // Modern native edge-to-edge layout: transparent status/navigation bars
    // Icon appearance (light/dark) controlled via WindowInsetsControllerCompat
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme, themeStyle) {
            window?.let {
                val controller = WindowCompat.getInsetsController(it, view)
                controller.isAppearanceLightStatusBars = isLightMode
                controller.isAppearanceLightNavigationBars = isLightMode
            }
        }
    }

    val typography = if (themeStyle == ThemeStyle.DISCORD_DARK || themeStyle == ThemeStyle.DISCORD_LIGHT) {
        DiscordTypography
    } else {
        AppTypography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}