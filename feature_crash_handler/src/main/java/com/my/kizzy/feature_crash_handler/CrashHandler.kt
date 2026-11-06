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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.developer.crashx.CrashActivity
import com.my.kizzy.ui.theme.KizzyTheme
import com.my.kizzy.ui.theme.LocalDarkTheme
import com.my.kizzy.ui.theme.LocalDynamicColorSwitch

class CrashHandler : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashActivity.getStackTraceFromIntent(intent)
        setContent {
            KizzyTheme(
                darkTheme = LocalDarkTheme.current.isDarkTheme(),
                isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                isDynamicColorEnabled = LocalDynamicColorSwitch.current,
            ){
                CrashScreen(trace = buildCrashLog())
            }
        }
    }
    private fun Context.buildCrashLog(): String {
        val packageName = this.packageName
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return """Kizzy crash report
Manufacturer: ${Build.MANUFACTURER}
Device: ${Build.MODEL}
Android version: ${Build.VERSION.RELEASE}
App version: ${packageInfo?.versionName ?: "unknown"} (${packageInfo?.versionCode?.toString() ?: "unknown"})
Stacktrace: 
$this"""
    }
}