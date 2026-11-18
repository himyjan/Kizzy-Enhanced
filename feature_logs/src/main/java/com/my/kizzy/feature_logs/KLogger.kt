/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KLogger.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.feature_logs

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.domain.model.logs.LogLevel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.ArrayDeque

@Singleton
class KLogger @Inject constructor() : Logger {
    private val logs = mutableStateListOf<LogEvent>()

    /**
     * Compose snapshot state is not thread-safe. The media service and the
     * gateway log from background coroutine workers; appending there corrupted
     * the global snapshot while the UI was composing ("Unsupported concurrent
     * change during composition"). All mutations therefore happen on the main
     * thread.
     *
     * The gateway can emit dozens of entries per second (full JSON payloads on
     * every DISPATCH). Posting one runnable AND one snapshot mutation per log
     * kept the main thread busy and recomposed the Logs screen on every single
     * entry. Bursts are now buffered and flushed as ONE mutation per frame
     * (~16ms), which is the lowest-cost path for both CPU and recomposition.
     */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val pending = ArrayDeque<LogEvent>()
    @Volatile
    private var flushScheduled = false

    private val flushRunnable = Runnable {
        flushScheduled = false
        if (pending.isEmpty()) return@Runnable
        // Single snapshot write for the whole burst.
        while (pending.isNotEmpty()) {
            val entry = pending.removeFirst()
            if (logs.size >= MAX_LOG_ENTRIES) logs.removeAt(0)
            logs.add(entry)
        }
    }

    fun getLogs(): SnapshotStateList<LogEvent> = logs

    override fun clear() {
        // Drop buffered entries as well, then clear the visible list in ONE
        // mutation (instead of 300 individual invalidations).
        mainHandler.post {
            pending.clear()
            if (logs.isNotEmpty()) logs.removeRange(0, logs.size)
        }
    }

    override fun i(tag: String, event: String) = addToLog(LogLevel.INFO, tag, event)
    override fun e(tag: String, event: String) = addToLog(LogLevel.ERROR, tag, event)
    override fun d(tag: String, event: String) = addToLog(LogLevel.DEBUG, tag, event)
    override fun w(tag: String, event: String) = addToLog(LogLevel.WARN, tag, event)

    private fun addToLog(level: LogLevel, tag: String, event: String) {
        // SESSIONS_REPLACE payloads can be tens of KB. Suppress those (and the
        // huge "Incoming" line) unless Developer Mode is on - this is the
        // single biggest source of log churn and main-thread work.
        if (!developerModeEnabled() && level == LogLevel.DEBUG &&
            (event.startsWith("Incoming: {") || event.length > 8_000)
        ) return

        // Capture the time on the calling thread so a queued log keeps its order.
        val log = LogEvent(level, tag, event, System.currentTimeMillis())
        if (Looper.myLooper() == Looper.getMainLooper()) {
            pending.addLast(log)
            scheduleFlush()
        } else {
            // Queue AND schedule on the same main-thread message so the flush
            // can never run before the entry is in the buffer.
            mainHandler.post {
                pending.addLast(log)
                scheduleFlush()
            }
        }
        // Mirror to logcat so the RPC can be debugged from adb without opening
        // the app. This is a native call and does not touch Compose state.
        android.util.Log.println(
            when (level) {
                LogLevel.ERROR -> android.util.Log.ERROR
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.INFO -> android.util.Log.INFO
                else -> android.util.Log.DEBUG
            },
            tag,
            event
        )
    }

    private fun developerModeEnabled(): Boolean =
        com.my.kizzy.preference.Prefs[com.my.kizzy.preference.Prefs.LOGS_DEVELOPER_MODE, false]

    private fun scheduleFlush() {
        if (flushScheduled) return
        synchronized(this) {
            if (flushScheduled) return
            flushScheduled = true
            mainHandler.post(flushRunnable)
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 300
        private var instance: KLogger? = null

        @JvmStatic
        fun getInstance(): KLogger? {
            var localInstance = instance
            if (localInstance == null) {
                synchronized(KLogger::class.java) {
                    localInstance = instance
                    if (localInstance == null) {
                        localInstance = KLogger()
                        instance = localInstance
                    }
                }
            }
            return localInstance
        }

        @JvmStatic
        fun init() {
            if (instance == null) instance = KLogger()
        }
    }
}
