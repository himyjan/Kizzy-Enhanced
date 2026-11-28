/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ServiceModule.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Service-scoped bindings shared by Android services running RPC and media tracking.
 */
@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @Provides
    @ServiceScoped
    fun providesCoroutineScope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, exception ->
            Log.e("CoroutineExceptionHandler", "Uncaught coroutine exception", exception)
        }
        return CoroutineScope(SupervisorJob() + handler + Dispatchers.IO)
    }
}