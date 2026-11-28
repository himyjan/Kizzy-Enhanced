/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetAppsUseCase.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.data.get_current_data.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.RpcImage
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

/**
 * Queries Android [UsageStatsManager] events to find the most recently resumed
 * foreground application and builds a [CommonRpc] snapshot for it.
 */
class GetCurrentlyRunningApp @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ACTIVITY_RESUMED was introduced on Android 10 (API 29); use MOVE_TO_FOREGROUND on older APIs.
    private val foregroundEventType: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    /**
     * Returns a [CommonRpc] representing the foreground app within the last [lookBackMs]
     * milliseconds, or an empty [CommonRpc] if none is found or the app is excluded.
     */
    operator fun invoke(lookBackMs: Long = DEFAULT_LOOK_BACK_MS): CommonRpc {
        val safeLookBackMs = lookBackMs.coerceAtLeast(1_000L)
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return CommonRpc()

        val now = System.currentTimeMillis()
        var latestPackage: String? = null
        var latestTimestamp = 0L
        var eventCount = 0

        try {
            val events = usageStatsManager.queryEvents(now - safeLookBackMs, now)
            val event = UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                eventCount++
                if (event.eventType != foregroundEventType) continue
                // Use >= so events sharing the exact same millisecond keep the latest in stream order.
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        } catch (e: Exception) {
            Log.w("GetCurrentlyRunningApp", "Failed to query usage events", e)
            return CommonRpc()
        }

        val packageName = latestPackage
        Log.d(
            "GetCurrentlyRunningApp",
            "queryEvents: eventCount=$eventCount latestPackage=$packageName latestTimestamp=$latestTimestamp lookBackMs=$safeLookBackMs"
        )

        if (packageName.isNullOrBlank() || packageName in EXCLUDED_PACKAGES) {
            Log.d("GetCurrentlyRunningApp", "Returning empty (package null/blank/excluded: $packageName)")
            return CommonRpc()
        }

        val appLabel = runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() }
        }.getOrNull() ?: packageName

        Log.d("GetCurrentlyRunningApp", "Returning app: $packageName ($appLabel)")
        return CommonRpc(
            name = appLabel,
            details = null,
            state = null,
            largeImage = RpcImage.ApplicationIcon(packageName, context),
            packageName = packageName,
        )
    }

    private companion object {
        const val DEFAULT_LOOK_BACK_MS = 30_000L
        val EXCLUDED_PACKAGES = setOf(
            "com.my.kizzy",
            "com.my.kizzy.enhanced",
            "com.discord",
        )
    }
}