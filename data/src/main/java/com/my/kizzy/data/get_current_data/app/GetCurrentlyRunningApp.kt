/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetAppsUseCase.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.get_current_data.app

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.RpcImage
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import java.util.*

class GetCurrentlyRunningApp @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(beginTime: Long = System.currentTimeMillis() - 10000): CommonRpc {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTimeMillis = System.currentTimeMillis()
        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, beginTime, currentTimeMillis
        )
        if (queryUsageStats != null && queryUsageStats.size > 1) {
            val treeMap: SortedMap<Long, UsageStats> = TreeMap()
            for (usageStats in queryUsageStats) {
                treeMap[usageStats.lastTimeUsed] = usageStats
            }
            if (!(treeMap.isEmpty() || treeMap[treeMap.lastKey()]?.packageName == "com.my.kizzy" || treeMap[treeMap.lastKey()]?.packageName == "com.discord")) {
                val packageName = treeMap[treeMap.lastKey()]!!.packageName
                Objects.requireNonNull(packageName)
                return CommonRpc(
                    name = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString(),
                    details = null,
                    state = null,
                    largeImage = RpcImage.ApplicationIcon(packageName, context),
                    packageName = packageName
                )

            }
        }
        return CommonRpc()
    }
}