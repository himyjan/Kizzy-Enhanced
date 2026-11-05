/*

 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LoggerProvider.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************

*/

package com.my.kizzy.feature_logs

import android.content.Context
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.feature_logs.KLogger
import com.my.kizzy.feature_logs.di.LoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

object LoggerProvider {
    private var instance: KLogger? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, LoggerEntryPoint::class.java)
        instance = entryPoint.logger() as KLogger
    }

    val logger: KLogger
        get() =
            instance ?: error(
                "LoggerProvider not initialized. Call LoggerProvider.init(context) from Application.onCreate()."
            )

    fun get(context: Context): Logger {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, LoggerEntryPoint::class.java)
        return entryPoint.logger()
    }
}
