package com.my.kizzy.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.my.kizzy.preference.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.work.ListenableWorker.Result

/**
 * Base worker for running background tasks with coroutine support.
 * Subclasses should implement [doWork] with their specific logic.
 */
abstract class BaseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : ListenableWorker(appContext, workerParams) {

    override fun startWork(): ListenableFuture<Result> {
        return CallbackToFutureAdapter.getFuture { completer ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = doWork()
                    completer.set(result)
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "BaseWorker"
        }
    }

    /**
     * Subclasses implement their work logic here.
     * Return [Result.success()] on success, [Result.failure()] on failure,
     * or [Result.retry()] if the work should be retried.
     */
    abstract suspend fun doWork(): Result
}

/**
 * Worker for periodic RPC status sync.
 * Ensures Discord presence stays active and updates presence data periodically.
 */
class RpcSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : BaseWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Sync RPC status - this would typically call into KizzyRPC or repository
        // to refresh presence data
        return Result.success()
    }
}

/**
 * Worker for cleaning up old log entries.
 * Runs periodically to prevent log storage from growing unbounded.
 */
class LogCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : BaseWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Clean old logs - implementation would interact with log storage
        return Result.success()
    }
}

/**
 * Worker for app detection sync.
 * Periodically checks for running applications and updates presence accordingly.
 */
class AppDetectionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : BaseWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Check for running apps and update presence
        return Result.success()
    }
}