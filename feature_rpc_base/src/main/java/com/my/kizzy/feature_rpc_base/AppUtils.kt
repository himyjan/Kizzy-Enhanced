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

    /**
     * Names of the RPC services that are alive in this process.
     *
     * KizzyRPC and its Discord gateway are process-wide singletons, so a service
     * that is being destroyed must not close the gateway (or clear the activity)
     * while another RPC service is still using it.
     */
    private val activeRpcServices: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

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
     * Drops the cached list of running services.
     *
     * Called after starting or stopping an RPC so the next check does not
     * return the state from before the switch (the cache lives for one second,
     * which is long enough for a screen to show the wrong switch state).
     */
    fun invalidateRunningServicesCache() {
        cachedRunningServices = null
        cachedRunningServicesAt = 0L
    }
    fun appDetectionRunning(): Boolean {
        return checkForRunningService<AppDetectionService>()
    }

    fun mediaRpcRunning(): Boolean {
        return checkForRunningService<MediaRpcService>()
    }

    fun customRpcRunning(): Boolean {
        return checkForRunningService<CustomRpcService>()
            && Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.CustomRpc.name
    }

    fun consoleRpcRunning(): Boolean {
        return checkForRunningService<CustomRpcService>()
            && Prefs[Prefs.ACTIVE_RPC_TYPE, ""] == RpcManager.RpcType.ConsoleRpc.name
    }

    fun ManualRpcRunning(): Boolean {
        return checkForRunningService<ManualRpc>()
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