/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  *  * DiscordLogin.kt is part of Kizzy
 *  *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.component

import android.app.Activity
import android.content.Context
import com.my.kizzy.feature_settings.rpc_settings.DiscordOAuth2Auth

class DiscordLogin(private val context: Context) {
    suspend fun authorize(activity: Activity): DiscordOAuth2Auth.DiscordOAuth2Result {
        return DiscordOAuth2Auth(activity).authorize(activity)
    }
}