/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppUtils.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

@file:Suppress("DEPRECATION")

package com.my.kizzy.feature_rpc_base

import android.app.ActivityManager
import android.content.Context
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import javax.inject.Singleton

@Singleton
object AppUtils {
    private lateinit var activityManager: ActivityManager

    /**
     * The running services are cached for a short time.
     *
     * The list of the running services is a binder call into the system, and
     * the screens ask for it on every recomposition. Without the cache the
     * binder was called several times a second, which shows up in the battery
     * statistics.
     */
    private const val RUNNING_SERVICES_CACHE_MS = 1000L

    @Volatile
    private var cachedRunningServices: List<String>? = null

    @Volatile
    private var cachedRunningServicesAt: Long = 0L

    fun init(context: Context) {
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    fun appDetectionRunning(): Boolean {
        return checkForRunningService<AppDetectionService>()
    }

    fun mediaRpcRunning(): Boolean {
        return checkForRunningService<MediaRpcService>()
    }

    fun customRpcRunning(): Boolean {
        return Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""].isNotEmpty() && checkForRunningService<CustomRpcService>()
    }

    fun consoleRpcRunning(): Boolean {
        return Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""].isNotEmpty() && checkForRunningService<CustomRpcService>()
    }

    fun experimentalRpcRunning(): Boolean {
        return checkForRunningService<ExperimentalRpc>()
    }
    private fun isActivityManagerInitialized(): Boolean = ::activityManager.isInitialized

    private inline fun <reified T : Any> checkForRunningService(): Boolean {
        if (!isActivityManagerInitialized()) {
            return false
        }
        return runningServiceNames().contains(T::class.java.name)
    }

    /**
     * The names of the services that are currently running.
     */
    private fun runningServiceNames(): List<String> {
        val now = System.currentTimeMillis()
        val cached = cachedRunningServices
        if (cached != null && now - cachedRunningServicesAt < RUNNING_SERVICES_CACHE_MS) {
            return cached
        }
        val names = try {
            activityManager.getRunningServices(Int.MAX_VALUE)
                .map { it.service.className }
        } catch (_: Exception) {
            emptyList()
        }
        cachedRunningServices = names
        cachedRunningServicesAt = now
        return names
    }
}