/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Constants.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

/**
 * Shared notification channel IDs, foreground notification IDs, intent actions,
 * and extras for RPC services and multi-instance management.
 */
object Constants {
    const val CHANNEL_ID = "kizzy.notification"

    // New channel with IMPORTANCE_DEFAULT so each RPC instance notification
    // stays a separate card. Notification channel importance is immutable
    // once created, so a NEW id is required (bumping the old one has no effect).
    const val CHANNEL_ID_RPC_INSTANCES = "kizzy.rpc.instances.v2"
    const val NOTIFICATION_CHANNEL_ID = "kizzy.media_rpc_channel"
    const val CHANNEL_NAME = "Kizzy Enhanced RPC"
    const val CHANNEL_DESCRIPTION = "Background Service notification which runs rpc"

    // Unique foreground notification IDs per service so Android OS never
    // replaces or cancels another service's foreground notification when
    // multiple RPC services run concurrently.
    const val NOTIFICATION_ID_MEDIA_RPC = 2022_03_01
    const val NOTIFICATION_ID_CUSTOM_RPC = 2022_03_02
    const val NOTIFICATION_ID_MANUAL_RPC = 2022_03_03
    const val NOTIFICATION_ID_APP_DETECTION = 2022_03_04
    const val NOTIFICATION_ID_OAUTH2_RPC = 2022_03_05

    // Per-instance clone notifications must never overlap the fixed service IDs
    // above (20220301..20220305). Each clone gets its own incrementing id from
    // this base so multiple Media/Manual/Custom clones show separate cards.
    const val NOTIFICATION_ID_CLONE_BASE = 30_000

    @Deprecated("Use per-service NOTIFICATION_ID_* instead", ReplaceWith("NOTIFICATION_ID_MEDIA_RPC"))
    const val NOTIFICATION_ID = NOTIFICATION_ID_MEDIA_RPC

    const val ACTION_STOP_SERVICE = "Stop Service"
    const val ACTION_RESTART_SERVICE = "Restart Service"
    const val ACTION_TOGGLE = "Toggle Playback"

    // Per-instance RPC sockets (parallel RPC instances on the home screen).
    const val ACTION_ADD_INSTANCE = "Add RPC Instance"
    const val ACTION_REMOVE_INSTANCE = "Remove RPC Instance"
    const val EXTRA_INSTANCE_ID = "RPC_INSTANCE_ID"
    const val EXTRA_OAUTH_INDEX = "RPC_OAUTH_INDEX"
    const val EXTRA_NOTIFICATION_ID = "RPC_NOTIFICATION_ID"
}