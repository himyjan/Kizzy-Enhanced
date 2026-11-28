package com.my.kizzy.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hilt-injected worker for periodic Discord RPC synchronization.
 */
@HiltWorker
class RpcSyncHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Refresh cached metadata/config if needed without failing on transient network errors.
            kizzyRepository.getGames()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("RpcSyncHiltWorker", "RPC sync failed: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Hilt-injected worker for periodic cache and log directory cleanup.
 */
@HiltWorker
class CleanupHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cacheDir = applicationContext.cacheDir
            deleteContentsRecursively(cacheDir, keepRoot = true)

            val logsDir = File(applicationContext.filesDir, "logs")
            if (logsDir.exists()) {
                deleteContentsRecursively(logsDir, keepRoot = true)
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /**
     * Recursively deletes children of [fileOrDirectory]. When [keepRoot] is true,
     * [fileOrDirectory] itself is preserved.
     */
    private fun deleteContentsRecursively(fileOrDirectory: File, keepRoot: Boolean) {
        if (fileOrDirectory.isDirectory) {
            val children = fileOrDirectory.listFiles() ?: return
            for (child in children) {
                deleteContentsRecursively(child, keepRoot = false)
            }
        }
        if (!keepRoot) {
            fileOrDirectory.delete()
        }
    }
}

/**
 * Hilt-injected worker for background app/catalog checks.
 */
@HiltWorker
class AppDetectionHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRepository: KizzyRepository,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            kizzyRepository.getGames()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w("AppDetectionHiltWorker", "App detection check failed: ${e.message}")
            Result.retry()
        }
    }
}

/**
 * Hilt-injected worker for RPC state recovery after process termination or task removal.
 */
@HiltWorker
class RpcRecoveryHiltWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kizzyRPC: KizzyRPC,
    private val getCurrentPlayingMedia: GetCurrentPlayingMedia,
    private val logger: Logger,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logger.d("MediaRPC", "[$startTime] Recovery worker started")

        try {
            val rpcFeaturesEnabled = Prefs[Prefs.USE_RPC_BUTTONS, false] ||
                    Prefs[MEDIA_RPC_MEDIA_BUTTON, false] ||
                    Prefs[MEDIA_RPC_ARTIST_LINK, false]

            if (!rpcFeaturesEnabled) {
                logger.d("MediaRPC", "Recovery work skipped: RPC not enabled")
                return@withContext Result.success()
            }

            val playingMedia = getCurrentPlayingMedia()
            if (playingMedia.name.isBlank()) {
                logger.d("MediaRPC", "Recovery work: no active media session")
                return@withContext Result.success()
            }

            val enableTimestamps = Prefs[MEDIA_RPC_ENABLE_TIMESTAMPS, true]
            kizzyRPC.apply {
                setName(playingMedia.name)
                setType(
                    ActivityTypeResolver.resolve(
                        packageName = playingMedia.appPackage,
                        isMusicMetadata = playingMedia.isMusicMetadata,
                        fallback = Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, ActivityTypeResolver.PLAYING],
                        useAutomaticDetection = Prefs[Prefs.AUTO_ACTIVITY_TYPE, true],
                    )
                )
                setDetails(playingMedia.details)
                setState(playingMedia.state)
                setStartTimestamps(if (enableTimestamps) playingMedia.time?.start else null)
                setStopTimestamps(if (enableTimestamps) playingMedia.time?.end else null)
                setStatus(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
                setLargeImage(playingMedia.largeImage, playingMedia.largeText)
                setSmallImage(playingMedia.smallImage, playingMedia.smallText)
                build()
            }

            val elapsed = System.currentTimeMillis() - startTime
            logger.d("MediaRPC", "Recovery work completed in ${elapsed}ms: RPC presence restored for ${playingMedia.name}")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            logger.e("MediaRPC", "RPC recovery failed after ${elapsed}ms: ${e.message}")
            Result.retry()
        }
    }
}