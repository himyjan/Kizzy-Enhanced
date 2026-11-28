/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Color.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.theme

import androidx.compose.ui.graphics.Color
import com.my.kizzy.domain.model.logs.LogLevel

// Dark Theme (Discord's current dark palette: near-black surfaces)
val DISCORD_LIGHT_DARK = Color(0xFF232428)
val DISCORD_GREY = Color(0xFF232428)
val DISCORD_BLURPLE = Color(0xFF5865F2)

// Discord rich presence theme colors
val DISCORD_BG = Color(0xFF000000)
val DISCORD_CARD = Color(0xFF1A1A1E)
val DISCORD_TEXT = Color(0xFFF2F3F5)
val DISCORD_SUBTEXT = Color(0xFFB5BAC1)

// Unfilled part of Discord's media timeline (the track behind the progress bar)
val DISCORD_TRACK_COLOR = Color(0xFF3F4147)

// Discord brand status colors
val DISCORD_GREEN = Color(0xFF57F287)
val DISCORD_YELLOW = Color(0xFFFEE75C)
val DISCORD_RED = Color(0xFFED4245)

// Destructive "Log Out" row on the profile page (Discord's danger red)
val DISCORD_LOGOUT_RED = Color(0xFFF23F43)

object LogColors {
    private val error = Color(0xFFF44336)
    private val info = Color(0xFF4CAF50)
    private val warn = Color(0xFFFFC107)
    private val debug = Color(0xFF2196F3)

    fun LogLevel.color(): Color = when (this) {
        LogLevel.INFO -> info
        LogLevel.DEBUG -> debug
        LogLevel.WARN -> warn
        LogLevel.ERROR -> error
    }
}