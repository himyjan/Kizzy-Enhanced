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
import java.io.File

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
 * Clears application cache and log files daily.
 */
@HiltWorker
class CleanupHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Clear application cache directory
                val cacheDir = applicationContext.cacheDir
                deleteRecursive(cacheDir)

                // 2. Clear application logs directory (if stored locally)
                val logsDir = File(applicationContext.filesDir, "logs")
                if (logsDir.exists()) {
                    deleteRecursive(logsDir)
                }

                Result.success()
            } catch (e: Exception) {
                // If an error occurs, WorkManager can safely retry later
                Result.retry()
            }
        }
    }

    /**
     * Recursively deletes file contents while preserving the root directory itself.
     */
    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        // Keep the root folder itself, only delete its contents
        if (fileOrDirectory != applicationContext.cacheDir &&
            fileOrDirectory != File(applicationContext.filesDir, "logs")
        ) {
            fileOrDirectory.delete()
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