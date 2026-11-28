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
import com.my.kizzy.feature_rpc_base.services.ManualRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.preference.Prefs
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks alive RPC services in-process and caches running system service queries
 * to avoid excessive binder IPC during UI recompositions.
 */
@Singleton
object AppUtils {
    private lateinit var activityManager: ActivityManager

    private const val RUNNING_SERVICES_CACHE_MS = 1_000L

    @Volatile
    private var cachedRunningServices: List<String>? = null

    @Volatile
    private var cachedRunningServicesAt: Long = 0L

    fun init(context: Context) {
        activityManager = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Names of the RPC services that are alive in this process.
     *
     * KizzyRPC and its Discord gateway are process-wide singletons, so a service
     * that is being destroyed must not close the gateway (or clear the activity)
     * while another RPC service is still using it.
     */
    private val activeRpcServices: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun registerRpcService(service: Any) {
        activeRpcServices.add(service.javaClass.name)
    }

    fun unregisterRpcService(service: Any) {
        activeRpcServices.remove(service.javaClass.name)
    }

    /** True when an RPC service other than [service] is still alive. */
    fun isAnotherRpcServiceActive(service: Any): Boolean =
        activeRpcServices.any { it != service.javaClass.name }

    /**
     * Drops the cached list of running services so subsequent checks reflect
     * immediate start/stop transitions.
     */
    fun invalidateRunningServicesCache() {
        cachedRunningServices = null
        cachedRunningServicesAt = 0L
    }

    fun appDetectionRunning(): Boolean = checkForRunningService<AppDetectionService>()

    fun mediaRpcRunning(): Boolean = checkForRunningService<MediaRpcService>()

    fun customRpcRunning(): Boolean =
        checkForRunningService<CustomRpcService>() && !Prefs[Prefs.CUSTOM_RPC_CONSOLE_MODE, false]

    fun consoleRpcRunning(): Boolean =
        checkForRunningService<CustomRpcService>() && Prefs[Prefs.CUSTOM_RPC_CONSOLE_MODE, false]

    fun ManualRpcRunning(): Boolean = checkForRunningService<ManualRpc>()

    private fun isActivityManagerInitialized(): Boolean = ::activityManager.isInitialized

    private inline fun <reified T : Any> checkForRunningService(): Boolean {
        val className = T::class.java.name
        if (activeRpcServices.contains(className)) {
            return true
        }
        if (!isActivityManagerInitialized()) {
            return false
        }
        return runningServiceNames().contains(className)
    }

    private fun runningServiceNames(): List<String> {
        val now = System.currentTimeMillis()
        val cached = cachedRunningServices
        if (cached != null && now - cachedRunningServicesAt < RUNNING_SERVICES_CACHE_MS) {
            return cached
        }
        val names = try {
            activityManager.getRunningServices(50)
                .mapNotNull { it.service?.className }
        } catch (_: Exception) {
            emptyList()
        }
        cachedRunningServices = names
        cachedRunningServicesAt = now
        return names
    }
}