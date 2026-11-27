/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcBaseModule.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.di

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import com.my.kizzy.feature_rpc_base.Constants
import com.my.kizzy.feature_rpc_base.services.NotificationListener
import com.my.kizzy.resources.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ServiceComponent::class)
object RpcBaseModule {

    @Provides
    fun providesComponentName(
        @ApplicationContext context: Context
    ) = ComponentName(context, NotificationListener::class.java)

    @Provides
    fun providesNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return context.getSystemService(NotificationManager::class.java)
    }

    @Provides
    fun providesNotificationBuilder(
        @ApplicationContext context: Context,
        notificationManager: NotificationManager
    ): Notification.Builder {
        // IMPORTANCE_DEFAULT keeps each RPC instance's notification visible as
        // its own card. IMPORTANCE_LOW makes Android auto-group them into a
        // single "N notifications" summary (the collapsed "3" badge).
        val channel = NotificationChannel(
            Constants.CHANNEL_ID,
            Constants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = Constants.CHANNEL_DESCRIPTION
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        // Separate channel for per-instance RPC notifications with DEFAULT
        // importance so Android does not auto-group them into one card.
        val instanceChannel = NotificationChannel(
            Constants.CHANNEL_ID_RPC_INSTANCES,
            "Kizzy RPC Instances",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Independent notifications for each RPC instance"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(instanceChannel)

        return Notification.Builder(context, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apps)
    }
}
