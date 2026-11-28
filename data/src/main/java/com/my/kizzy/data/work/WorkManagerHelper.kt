package com.my.kizzy.data.work

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper object for scheduling and cancelling WorkManager tasks used by Kizzy.
 */
object WorkManagerHelper {

    private const val WORK_RPC_SYNC = "kizzy_work_rpc_sync"
    private const val WORK_LOG_CLEANUP = "kizzy_work_log_cleanup"
    private const val WORK_APP_DETECTION = "kizzy_work_app_detection"
    private const val WORK_RPC_RECOVERY = "kizzy_work_rpc_recovery"
    private const val MIN_PERIODIC_MINUTES = 15L

    /**
     * Constraints for work that requires network connectivity and sufficient battery.
     */
    val networkConstraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Constraints for cleanup work—runs when the device is idle and battery is not low.
     */
    val cleanupConstraints: Constraints = Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Schedules periodic RPC sync work. Runs every hour by default.
     */
    fun scheduleRpcSync(context: Context, repeatIntervalHours: Long = 1) {
        val safeHours = repeatIntervalHours.coerceAtLeast(1L)
        val request = PeriodicWorkRequestBuilder<RpcSyncHiltWorker>(
            safeHours, TimeUnit.HOURS
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_RPC_SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Schedules periodic log cleanup work. Runs every 24 hours by default.
     */
    fun scheduleLogCleanup(context: Context, repeatIntervalHours: Long = 24) {
        val safeHours = repeatIntervalHours.coerceAtLeast(1L)
        val request = PeriodicWorkRequestBuilder<CleanupHiltWorker>(
            safeHours, TimeUnit.HOURS
        )
            .setConstraints(cleanupConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_LOG_CLEANUP, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Schedules periodic app detection work. Runs every 30 minutes by default.
     */
    fun scheduleAppDetection(context: Context, repeatIntervalMinutes: Long = 30) {
        val safeMinutes = repeatIntervalMinutes.coerceAtLeast(MIN_PERIODIC_MINUTES)
        val request = PeriodicWorkRequestBuilder<AppDetectionHiltWorker>(
            safeMinutes, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_APP_DETECTION, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Schedules daily cleanup work (combines log and cache cleanup).
     */
    fun scheduleDailyCleanup(context: Context) {
        scheduleLogCleanup(context, repeatIntervalHours = 24)
    }

    /**
     * Schedules RPC recovery work after process termination or task removal.
     *
     * Optimised to be as close as possible to NotificationListenerService reliability:
     * - No constraints (no battery, no network, no device idle requirement)
     * - Android 12+: Expedited Work for priority execution
     * - Android 11-: Standard OneTimeWorkRequest
     * - Executes immediately with no delay
     */
    fun scheduleRpcRecovery(context: Context) {
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            OneTimeWorkRequestBuilder<RpcRecoveryHiltWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        } else {
            OneTimeWorkRequestBuilder<RpcRecoveryHiltWorker>()
                .build()
        }

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_RPC_RECOVERY,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

    /**
     * Cancels all scheduled Kizzy work requests.
     */
    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    /**
     * Cancels a specific work request by unique work name.
     */
    fun cancelWork(context: Context, workName: String) {
        if (workName.isBlank()) return
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }
}