package com.my.kizzy.data.get_current_data.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

/**
 * Returns the package name of the app currently in the foreground.
 *
 * Uses raw [UsageEvents] rather than coarse bucketed `queryUsageStats` so rapid
 * app switches are detected promptly. Requires Usage Access (`PACKAGE_USAGE_STATS`).
 */
class GetForegroundPackage @Inject constructor(
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
     * Returns the most recently foregrounded package name within [lookBackMs], or null
     * if unavailable.
     */
    operator fun invoke(lookBackMs: Long = DEFAULT_LOOK_BACK_MS): String? {
        val safeLookBackMs = lookBackMs.coerceAtLeast(1_000L)
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return null
            val now = System.currentTimeMillis()
            val events = usageStatsManager.queryEvents(now - safeLookBackMs, now)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = 0L
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != foregroundEventType) continue
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
        const val DEFAULT_LOOK_BACK_MS = 6_000L
    }
}