package com.my.kizzy.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper class for scheduling WorkManager tasks.
 * Provides convenient methods for enqueueing common work requests.
 */
object WorkManagerHelper {

    private const val WORK_RPC_SYNC = "kizzy_work_rpc_sync"
    private const val WORK_LOG_CLEANUP = "kizzy_work_log_cleanup"
    private const val WORK_APP_DETECTION = "kizzy_work_app_detection"

    /**
     * Constraints for work that requires network connectivity.
     */
    val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Schedules periodic RPC sync work.
     * Runs every hour by default.
     */
    fun scheduleRpcSync(context: Context, repeatIntervalHours: Long = 1) {
        val request = PeriodicWorkRequestBuilder<RpcSyncWorker>(
            repeatIntervalHours, TimeUnit.HOURS
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_RPC_SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Schedules periodic log cleanup work.
     * Runs every 24 hours by default.
     */
    fun scheduleLogCleanup(context: Context, repeatIntervalHours: Long = 24) {
        val request = PeriodicWorkRequestBuilder<LogCleanupWorker>(
            repeatIntervalHours, TimeUnit.HOURS
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_LOG_CLEANUP, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Schedules periodic app detection work.
     * Runs every 30 minutes by default.
     */
    fun scheduleAppDetection(context: Context, repeatIntervalMinutes: Long = 30) {
        val request = PeriodicWorkRequestBuilder<AppDetectionWorker>(
            repeatIntervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_APP_DETECTION, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Cancels all scheduled Kizzy work requests.
     */
    fun cancelAllWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }

    /**
     * Cancels a specific work request by name.
     */
    fun cancelWork(context: Context, workName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }
}