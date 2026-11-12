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

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {
    /**
     * The gateway and the RPC are provided by [GatewayModule] as singletons of
     * the whole process.
     *
     * They used to be provided per service, so every service (and the recovery
     * worker) opened its own gateway connection and kept its own "last
     * presence". A stale presence of one of them could then overwrite the
     * current presence of another one on Discord, and Discord rejected the
     * second connection with a "4003 Not authenticated" error.
     */
    @Provides
    fun providesCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}