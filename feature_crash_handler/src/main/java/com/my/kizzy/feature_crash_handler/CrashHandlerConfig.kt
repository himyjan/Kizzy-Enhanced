/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Config.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_crash_handler

object CrashHandlerConfig {
    fun apply() {
        // CrashX removed - crash handling now handled by CrashReportActivity in app module
        // via Thread.setDefaultUncaughtExceptionHandler
    }
}