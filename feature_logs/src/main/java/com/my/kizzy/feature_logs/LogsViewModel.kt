/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogsViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 *
 */

package com.my.kizzy.feature_logs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.feature_logs.KLogger
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("LocaleGetDefault")
private val LogFileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
@Suppress("LocaleGetDefault")
private val LogEntryTimeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

@HiltViewModel
class LogsViewModel @Inject constructor(
    @Suppress("UNUSED_PARAMETER")
    @ApplicationContext private val context: Context,
    private val kLogger: KLogger
) : ViewModel() {
    val filterStrings = mutableStateOf("")
    val showCompat = mutableStateOf(Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE, false])
    val autoScroll = mutableStateOf(Prefs[Prefs.LOGS_AUTO_SCROLL, false])
    val developerMode = mutableStateOf(Prefs[Prefs.LOGS_DEVELOPER_MODE, false])
    val isSearchBarVisible = mutableStateOf(false)

    val logsSnapshot: SnapshotStateList<LogEvent> = kLogger.getLogs()

    /**
     * Filtering is a snapshot read of the state list (no toList() copy). The
     * derived state only recomputes when a log is appended/removed or the
     * query changes - not on unrelated recompositions.
     */
    @Suppress("UNCHECKED_CAST")
    private val _visibleLogs = derivedStateOf {
        val query = filterStrings.value
        if (query.isBlank()) {
            // SnapshotStateList is itself a List; exposing it avoids an O(n)
            // copy on every single log append.
            logsSnapshot as List<LogEvent>
        } else {
            logsSnapshot.filter { log ->
                log.text.contains(query, ignoreCase = true) ||
                    log.tag.contains(query, ignoreCase = true)
            }
        }
    }
    val visibleLogs: List<LogEvent> by _visibleLogs

    /** Formats a log line. Kept here so IO work reuses it without touching Compose. */
    private fun formatLog(log: LogEvent): String =
        "[${LogEntryTimeFormat.format(Date(log.createdAt))}] ${log.level.name} ${log.tag}: ${log.text}"

    fun setFilterStrings(text: String) {
        filterStrings.value = text
    }

    fun setShowCompat(value: Boolean) {
        showCompat.value = value
        Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE] = value
    }

    fun setAutoScroll(value: Boolean) {
        autoScroll.value = value
        Prefs[Prefs.LOGS_AUTO_SCROLL] = value
    }

    fun setDeveloperMode(value: Boolean) {
        developerMode.value = value
        Prefs[Prefs.LOGS_DEVELOPER_MODE] = value
    }

    fun clearLogs() {
        // Already posted to main by KLogger; do it from the VM scope so the
        // clear is not invoked synchronously from a click handler while the
        // LazyColumn is mid-composition.
        viewModelScope.launch(Dispatchers.Main.immediate) { kLogger.clear() }
    }

    /**
     * Saves the visible logs on Dispatchers.IO. joinToString of up to 300
     * entries plus disk IO on the main thread used to freeze the screen.
     * Returns the path via a callback on the main thread.
     */
    fun saveLogsToFile(context: Context, onResult: (String?) -> Unit) {
        val snapshot = visibleLogs.toList()
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    val file = File(
                        context.cacheDir,
                        "kizzy_logs_${LogFileNameFormat.format(Date())}.txt"
                    )
                    // Write incrementally - never build one giant string.
                    file.bufferedWriter().use { writer ->
                        snapshot.forEach { log ->
                            writer.write(formatLog(log))
                            writer.newLine()
                        }
                    }
                    file.absolutePath
                } catch (_: Exception) {
                    null
                }
            }
            onResult(path)
        }
    }

    /** Builds the copy-all text off the main thread and caps it at 500k chars. */
    fun copyAllLogs(onReady: (String) -> Unit) {
        val snapshot = visibleLogs.toList()
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                val sb = StringBuilder()
                snapshot.forEach { log ->
                    sb.append(formatLog(log)).append('\n')
                    if (sb.length > 500_000) {
                        sb.setLength(500_000)
                        return@withContext "$sb\n... (truncated)"
                    }
                }
                sb.toString()
            }
            onReady(text)
        }
    }
}
