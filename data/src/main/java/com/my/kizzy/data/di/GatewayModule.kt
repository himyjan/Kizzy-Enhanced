/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GatewayModule.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.di

import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.data.rpc.MultiRpcManager
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl

/**
 * Provides process-wide singletons for the Discord gateway connection, RPC engine,
 * and multi-instance RPC manager so active services share presence state and avoid
 * conflicting gateway sessions.
 */
@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {

    @Provides
    @Singleton
    fun providesDiscordWebsocket(
        logger: Logger
    ): DiscordWebSocket {
        // Default blank/unset platform values to Android/mobile identify properties.
        val platform = Prefs[Prefs.MANUAL_RPC_PLATFORM, "android"]
            .trim()
            .takeIf { it.isNotEmpty() } ?: "android"
        val (browser, os, device) = Constants.spoofIdentifyProperties(platform)
        return DiscordWebSocketImpl(
            token = Prefs[Prefs.TOKEN, ""],
            logger = logger,
            identifyBrowser = browser,
            identifyOs = os,
            identifyDevice = device
        )
    }

    @Provides
    @Singleton
    fun provideKizzyRpc(
        kizzyRepository: KizzyRepository,
        discordWebSocket: DiscordWebSocket,
        logger: Logger
    ): KizzyRPC = KizzyRPC(
        Prefs[Prefs.TOKEN, ""],
        kizzyRepository,
        discordWebSocket,
        logger
    )

    @Provides
    @Singleton
    fun provideMultiRpcManager(
        kizzyRepository: KizzyRepository,
        logger: Logger
    ): MultiRpcManager = MultiRpcManager(kizzyRepository, logger).also {
        it.loadSessionsFromPrefs()
    }
}