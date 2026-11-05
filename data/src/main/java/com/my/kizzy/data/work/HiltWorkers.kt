package com.my.kizzy.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.my.kizzy.data.repository.KizzyRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.ListenableWorker.Result

/**
 * Hilt-injected worker for Discord RPC sync.
 * Uses dependency injection to access repository and other dependencies.
 */
@HiltWorker
class RpcSyncHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRepository: KizzyRepository
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Sync RPC status using the repository
                // kizzyRepository.syncPresence()
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}

/**
 * Hilt-injected worker for log cleanup.
 */
@HiltWorker
class LogCleanupHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Clean old logs
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}

/**
 * Hilt-injected worker for app detection.
 */
@HiltWorker
class AppDetectionHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRepository: KizzyRepository
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // Check for running apps and update presence
                Result.success()
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }
}