package com.my.kizzy.data.get_current_data.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

/**
 * Returns the package of the app that is in the foreground right now.
 *
 * The coarse `queryUsageStats` buckets are unreliable for "what is open now",
 * so the raw usage event stream is used. This is the same approach as
 * [com.my.kizzy.feature_rpc_base.services.AppDetectionService], extracted so
 * Console RPC can use it without starting the app detection service.
 *
 * Requires usage access (`PACKAGE_USAGE_STATS`), which Kizzy already asks for.
 */
class GetForegroundPackage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    operator fun invoke(lookBackMs: Long = DEFAULT_LOOK_BACK_MS): String? {
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - lookBackMs, now)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent =
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                if (!isForegroundEvent) continue
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
            latestPackage
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val DEFAULT_LOOK_BACK_MS = 6000L
    }
}
