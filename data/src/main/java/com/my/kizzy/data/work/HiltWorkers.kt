package com.my.kizzy.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.KizzyRPC
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.repository.KizzyRepository
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ARTIST_LINK
import com.my.kizzy.preference.Prefs.MEDIA_RPC_ENABLE_TIMESTAMPS
import com.my.kizzy.preference.Prefs.MEDIA_RPC_MEDIA_BUTTON
import com.my.kizzy.preference.Prefs.USE_RPC_BUTTONS
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

/**
 * Hilt-injected worker for RPC state recovery after process termination.
 * Restores Rich Presence if the user has RPC enabled and the app was
 * killed by the system or removed from Recents.
 */
@HiltWorker
class RpcRecoveryHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRPC: KizzyRPC,
    private val getCurrentPlayingMedia: GetCurrentPlayingMedia,
    private val logger: Logger
) : androidx.work.CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            logger.d("MediaRPC", "[$startTime] Recovery worker started")

            try {
                val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]

                if (!Prefs[Prefs.USE_RPC_BUTTONS, false] &&
                    !Prefs[MEDIA_RPC_MEDIA_BUTTON, false] &&
                    !Prefs[MEDIA_RPC_ARTIST_LINK, false]
                ) {
                    logger.d("MediaRPC", "Recovery work skipped: RPC not enabled")
                    return@withContext Result.success()
                }

                val playingMedia = getCurrentPlayingMedia()
                if (playingMedia.name.isBlank()) {
                    logger.d("MediaRPC", "Recovery work: no active media session")
                    return@withContext Result.success()
                }

                // Restore presence state
                kizzyRPC.apply {
                    setName(playingMedia.name)
                    // The activity type is resolved per app, exactly like the Media
                    // RPC does it, so the restored presence shows the same card.
                    setType(
                        ActivityTypeResolver.resolve(
                            packageName = playingMedia.appPackage,
                            isMusicMetadata = playingMedia.isMusicMetadata,
                            fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                            useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                        )
                    )
                    setDetails(playingMedia.state)
                    setState(playingMedia.state)
                    setStartTimestamps(if (enableTimestamps) playingMedia.time?.start else null)
                    setStopTimestamps(if (enableTimestamps) playingMedia.time?.end else null)
                    setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
                    setLargeImage(playingMedia.largeImage, playingMedia.largeText)
                    setSmallImage(playingMedia.smallImage, playingMedia.smallText)
                    build()
                }

                val endTime = System.currentTimeMillis()
                logger.d("MediaRPC", "Recovery work completed in ${endTime - startTime}ms: RPC presence restored for ${playingMedia.name}")
                Result.success()
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                logger.e("MediaRPC", "RPC recovery failed after ${endTime - startTime}ms: ${e.message}")
                Result.retry()
            }
        }
    }
}