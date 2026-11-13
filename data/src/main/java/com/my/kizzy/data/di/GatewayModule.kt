/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GatewayModule.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.data.di

import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.data.rpc.KizzyRPC
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
 * The gateway and the RPC are shared by the whole process.
 *
 * They used to be provided per service, so every service (and the recovery
 * worker) opened its own gateway connection and kept its own "last presence".
 * A stale presence of one of them could then overwrite the current presence of
 * another one on Discord, and Discord rejected the second connection with a
 * "4003 Not authenticated" error.
 */
@Module
@InstallIn(SingletonComponent::class)
object GatewayModule {

    @Provides
    @Singleton
    fun providesDiscordWebsocket(
        logger: Logger
    ): DiscordWebSocket {
        // Spoof the gateway IDENTIFY "browser" (what platform-indicator plugins read)
        // based on the platform selected in Experimental RPC.
        val (browser, os, device) = Constants.spoofIdentifyProperties(
            Prefs[Prefs.EXPERIMENTAL_RPC_PLATFORM, ""]
        )
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
    ) = KizzyRPC(Prefs[Prefs.TOKEN, ""], kizzyRepository, discordWebSocket, logger)
}
