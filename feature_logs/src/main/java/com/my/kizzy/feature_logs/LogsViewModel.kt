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
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.domain.model.logs.LogLevel
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogsUiState(
    val filterText: String = "",
    val selectedLevel: LogLevel? = null,
    val isPaused: Boolean = false,
    val isFilterBarVisible: Boolean = false,
    val isSearchBarVisible: Boolean = false,
    val showCompat: Boolean = false,
    val autoScroll: Boolean = false,
    val developerMode: Boolean = false,
)

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

    private val _uiState = MutableStateFlow(
        LogsUiState(
            showCompat = Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE, false],
            autoScroll = Prefs[Prefs.LOGS_AUTO_SCROLL, false],
            developerMode = Prefs[Prefs.LOGS_DEVELOPER_MODE, false],
        )
    )
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    val logsSnapshot: SnapshotStateList<LogEvent> = kLogger.getLogs()
    private val _pausedSnapshot = MutableStateFlow<List<LogEvent>>(emptyList())
    val pausedSnapshot: StateFlow<List<LogEvent>> = _pausedSnapshot.asStateFlow()

    val visibleLogs: List<LogEvent> by derivedStateOf {
        val state = _uiState.value
        val source: List<LogEvent> = if (state.isPaused) _pausedSnapshot.value else logsSnapshot
        val level = state.selectedLevel
        val query = state.filterText.trim()

        if (level == null && query.isEmpty()) {
            source
        } else {
            val terms = query.split(' ').filter { it.isNotEmpty() }
            source.filter { log ->
                val matchesLevel = level == null || log.level == level
                val matchesQuery = terms.all { term ->
                    log.text.contains(term, ignoreCase = true) ||
                            log.tag.contains(term, ignoreCase = true) ||
                            log.level.name.contains(term, ignoreCase = true)
                }
                matchesLevel && matchesQuery
            }
        }
    }

    fun suggestedLogFileName(): String =
        "kizzy_logs_${LogFileNameFormat.format(Date())}.txt"

    private fun formatLog(log: LogEvent): String =
        "[${LogEntryTimeFormat.format(Date(log.createdAt))}] ${log.level.name} ${log.tag}: ${log.text}"

    fun setFilterText(text: String) {
        _uiState.update { it.copy(filterText = text) }
    }

    fun toggleSearchBar() {
        _uiState.update { current ->
            val nextVisible = !current.isSearchBarVisible
            current.copy(
                isSearchBarVisible = nextVisible,
                filterText = if (nextVisible) current.filterText else ""
            )
        }
    }

    fun toggleFilterBar() {
        _uiState.update { current ->
            val nextVisible = !current.isFilterBarVisible
            current.copy(
                isFilterBarVisible = nextVisible,
                selectedLevel = if (nextVisible) current.selectedLevel else null
            )
        }
    }

    fun setSelectedLevel(level: LogLevel?) {
        _uiState.update { it.copy(selectedLevel = level) }
    }

    fun togglePaused() {
        val nextPaused = !_uiState.value.isPaused
        _pausedSnapshot.value = if (nextPaused) logsSnapshot.toList() else emptyList()
        _uiState.update { it.copy(isPaused = nextPaused) }
    }

    fun setShowCompat(value: Boolean) {
        Prefs[Prefs.SHOW_LOGS_IN_COMPACT_MODE] = value
        _uiState.update { it.copy(showCompat = value) }
    }

    fun setAutoScroll(value: Boolean) {
        Prefs[Prefs.LOGS_AUTO_SCROLL] = value
        _uiState.update { it.copy(autoScroll = value) }
    }

    fun setDeveloperMode(value: Boolean) {
        Prefs[Prefs.LOGS_DEVELOPER_MODE] = value
        _uiState.update { it.copy(developerMode = value) }
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _pausedSnapshot.value = emptyList()
            kLogger.clear()
        }
    }

    fun writeLogsToUri(context: Context, uri: Uri, logs: List<LogEvent>, onResult: (Boolean) -> Unit) {
        val snapshot = logs.toList()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: return@withContext false
                    stream.bufferedWriter().use { writer ->
                        snapshot.forEach { log ->
                            writer.write(formatLog(log))
                            writer.newLine()
                        }
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }
            onResult(ok)
        }
    }

    fun copyAllLogs(logs: List<LogEvent>, onReady: (String) -> Unit) {
        val snapshot = logs.toList()
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