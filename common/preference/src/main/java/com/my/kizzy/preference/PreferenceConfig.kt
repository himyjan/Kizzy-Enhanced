/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * PreferenceApp.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.preference

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object PreferenceConfig {
    lateinit var applicationScope: CoroutineScope

    fun apply(context: Context) {
        applicationScope = CoroutineScope(SupervisorJob())
        Prefs.initDataStore(context)
        initializeThemePrefs()
        Prefs.checkAndAutoDeleteSavedImages()
    }
}
