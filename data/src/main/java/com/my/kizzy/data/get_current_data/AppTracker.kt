/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppTracker.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.get_current_data

import android.util.Log
import com.my.kizzy.data.get_current_data.app.GetCurrentlyRunningApp
import com.my.kizzy.data.get_current_data.media.GetCurrentPlayingMedia
import com.my.kizzy.data.rpc.CommonRpc
import jakarta.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Periodically polls active media sessions and foreground app state to produce a
 * stream of [CommonRpc] presence updates.
 */
class AppTracker @Inject constructor(
    private val getCurrentlyRunningApp: GetCurrentlyRunningApp,
    private val getCurrentPlayingMedia: GetCurrentPlayingMedia,
) {
    /**
     * Emits [CommonRpc] snapshots every [POLL_INTERVAL_MS], prioritizing active/enabled
     * media sessions and falling back to the current foreground app.
     */
    fun getCurrentAppData(instanceId: String? = null): Flow<CommonRpc> = flow {
        while (currentCoroutineContext().isActive) {
            val currentMedia = getCurrentPlayingMedia(instanceId)
            if (currentMedia.name.isNotEmpty()) {
                emit(currentMedia)
            } else {
                val currentApp = getCurrentlyRunningApp()
                if (currentApp.name.isNotEmpty()) {
                    emit(currentApp)
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }.distinctUntilChanged()
        .catch { exception ->
            if (exception is CancellationException) throw exception
            Log.e(TAG, "Error while tracking current app/media data", exception)
        }

    private companion object {
        const val TAG = "AppTracker"
        const val POLL_INTERVAL_MS = 5_000L
    }
}