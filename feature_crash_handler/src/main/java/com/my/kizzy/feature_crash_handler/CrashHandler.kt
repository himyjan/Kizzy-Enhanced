/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CrashHandler.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_crash_handler

import android.os.Bundle
import androidx.activity.ComponentActivity

class CrashHandler : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CrashX removed - crash handling now handled by CrashReportActivity in app module
        // via Thread.setDefaultUncaughtExceptionHandler
        // This activity is kept for manifest compatibility only
        finish()
    }
}