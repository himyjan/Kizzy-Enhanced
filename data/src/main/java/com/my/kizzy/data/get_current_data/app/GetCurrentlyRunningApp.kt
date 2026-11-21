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
import android.util.Log
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.RpcImage
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import java.util.*

class GetCurrentlyRunningApp @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(beginTime: Long = System.currentTimeMillis() - 30000): CommonRpc {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTimeMillis = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(
            currentTimeMillis - beginTime,
            currentTimeMillis
        )
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = 0L
        var eventCount = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCount++
            val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (!isForegroundEvent) continue
            if (event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }
        val packageName = latestPackage
        Log.d("GetCurrentlyRunningApp", "queryEvents: eventCount=$eventCount latestPackage=$packageName latestTimestamp=$latestTimestamp lookbackMs=$beginTime")
        if (packageName.isNullOrBlank() || packageName == "com.my.kizzy" || packageName == "com.discord") {
            Log.d("GetCurrentlyRunningApp", "Returning empty (package null/blank/excluded: $packageName)")
            return CommonRpc()
        }
        Log.d("GetCurrentlyRunningApp", "Returning app: $packageName")
        return CommonRpc(
            name = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString(),
            details = null,
            state = null,
            largeImage = RpcImage.ApplicationIcon(packageName, context),
            packageName = packageName
        )
    }
}
