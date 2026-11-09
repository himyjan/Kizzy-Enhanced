/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogsViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_logs

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.feature_logs.LoggerProvider.logger
import com.my.kizzy.preference.Prefs
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Stable
class LogsViewModel: ViewModel() {
    // Expose MutableState directly - Compose tracks reads inside composables
    val filterStrings = mutableStateOf("")
    val showCompat = mutableStateOf(Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE, false])
    val autoScroll = mutableStateOf(Prefs[Prefs.LOGS_AUTO_SCROLL, false])
    val isSearchBarVisible = mutableStateOf(false)

    // Underlying snapshot list from KLogger (observable by Compose)
    val logsSnapshot: SnapshotStateList<LogEvent> = logger.getLogs()

    // Cached filtered results using derivedStateOf - only recomputes when logs or filter changes
    private val _visibleLogs = derivedStateOf {
        filterLogs(logsSnapshot.toList(), filterStrings.value)
    }
    val visibleLogs: List<LogEvent> by _visibleLogs

    init {
        // Initial filter pass is handled by derivedStateOf lazily
    }

    fun setFilterStrings(text: String) {
        filterStrings.value = text
    }

    private fun filterLogs(logs: List<LogEvent>, filter: String): List<LogEvent> {
        if (filter.isBlank()) return logs
        return logs.filter { log ->
            log.text.contains(filter, ignoreCase = true) || log.tag.contains(filter, ignoreCase = true)
        }
    }

    fun setShowCompat(value: Boolean) {
        showCompat.value = value
        Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE] = value
    }

    fun setAutoScroll(value: Boolean) {
        autoScroll.value = value
        Prefs[Prefs.LOGS_AUTO_SCROLL] = value
    }

    fun clearLogs() {
        logger.clear()
    }

    fun saveLogsToFile(context: Context): String? {
        val dir = context.cacheDir
        val fileName = "kizzy_logs_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val file = File(dir, fileName)
        return try {
            val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
            val allLogs = visibleLogs.joinToString("\n") { log ->
                "[${sdf.format(Date(log.createdAt))}] ${log.level.name} ${log.tag}: ${log.text}"
            }
            file.writeText(allLogs)
            file.absolutePath
        } catch (e: IOException) {
            null
        }
    }
}
