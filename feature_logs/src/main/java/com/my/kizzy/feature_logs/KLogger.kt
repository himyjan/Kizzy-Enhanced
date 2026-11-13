/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KLogger.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_logs

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.domain.model.logs.LogLevel
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class KLogger @Inject constructor() : Logger {
    private val logs = mutableStateListOf<LogEvent>()

    /**
     * Compose snapshot state is not thread-safe. The media service logs from
     * background coroutine workers, and appending to [logs] there corrupted the
     * global snapshot while the UI was composing ("Unsupported concurrent change
     * during composition"). Every mutation is therefore posted to the main thread;
     * background callers return immediately.
     */
    private val mainHandler by lazy {
        android.os.Handler(android.os.Looper.getMainLooper())
    }

    fun getLogs(): SnapshotStateList<LogEvent> {
        return logs
    }

    override fun clear() {
        runOnMain { logs.clear() }
    }

    override fun i(tag: String, event: String) {
        addToLog(LogLevel.INFO, tag, event)
    }

    override fun e(tag: String, event: String) {
        addToLog(LogLevel.ERROR, tag, event)
    }

    override fun d(tag: String, event: String) {
        addToLog(LogLevel.DEBUG, tag, event)
    }

    override fun w(tag: String, event: String) {
        addToLog(LogLevel.WARN, tag, event)
    }

    private fun addToLog(level: LogLevel, tag: String, event: String) {
        // Capture the time on the calling thread so a queued log keeps its order.
        val log = LogEvent(level, tag, event, System.currentTimeMillis())
        runOnMain {
            if (logs.size >= MAX_LOG_ENTRIES) {
                logs.removeAt(0)
            }
            logs.add(log)
        }
        // Mirror to logcat so the RPC can be debugged from adb without opening the app.
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

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
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
            if (instance == null) {
                instance = KLogger()
            }
        }
    }
}
