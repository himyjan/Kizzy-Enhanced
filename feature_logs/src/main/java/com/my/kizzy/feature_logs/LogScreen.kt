/*
 *
 * ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */

package com.my.kizzy.feature_logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.domain.model.logs.LogLevel
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.theme.LogColors.color
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs

@Suppress("LocaleGetDefault")
private val LogDateFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

private suspend fun LazyListState.smoothScrollToIndex(targetIndex: Int) {
    if (targetIndex < 0) return
    val current = firstVisibleItemIndex
    if (abs(current - targetIndex) > 16) {
        val anchor = if (current < targetIndex) {
            (targetIndex - 16).coerceAtLeast(0)
        } else {
            (targetIndex + 16).coerceAtMost((layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
        scrollToItem(anchor)
    }
    animateScrollToItem(targetIndex)
}

@Suppress("LocalContextGetResourceValueCall")
@Composable
fun LogScreen(viewModel: LogsViewModel, onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visibleLogs = viewModel.visibleLogs
    val logsSaveError = stringResource(R.string.logs_save_error)
    val copiedToClipboard = stringResource(R.string.copied_to_clipboard)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, logsSaveError, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.writeLogsToUri(context, uri, visibleLogs) { success ->
            val msg = if (success) copiedToClipboard else logsSaveError
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LogScreenContent(
        visibleLogs = visibleLogs,
        uiState = uiState,
        onBackPressed = onBackPressed,
        onTogglePause = viewModel::togglePaused,
        onToggleFilterBar = viewModel::toggleFilterBar,
        onSelectLevel = viewModel::setSelectedLevel,
        onToggleAutoScroll = { viewModel.setAutoScroll(!uiState.autoScroll) },
        onToggleShowCompat = { viewModel.setShowCompat(!uiState.showCompat) },
        onToggleDeveloperMode = { viewModel.setDeveloperMode(!uiState.developerMode) },
        onSaveLogs = { createDocumentLauncher.launch(viewModel.suggestedLogFileName()) },
        onClearLogs = viewModel::clearLogs,
        onCopyAllLogs = { onReady -> viewModel.copyAllLogs(visibleLogs, onReady) },
        onSearchToggle = viewModel::toggleSearchBar,
        onFilterChange = viewModel::setFilterText
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreenContent(
    visibleLogs: List<LogEvent>,
    uiState: LogsUiState,
    onBackPressed: () -> Unit = {},
    onTogglePause: () -> Unit = {},
    onToggleFilterBar: () -> Unit = {},
    onSelectLevel: (LogLevel?) -> Unit = {},
    onToggleAutoScroll: () -> Unit = {},
    onToggleShowCompat: () -> Unit = {},
    onToggleDeveloperMode: () -> Unit = {},
    onSaveLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onCopyAllLogs: ((String) -> Unit) -> Unit = {},
    onSearchToggle: () -> Unit = {},
    onFilterChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val copiedToClipboard = stringResource(R.string.copied_to_clipboard)
    val scope = rememberCoroutineScope()
    val sequenceByEntry = remember { IdentityHashMap<LogEvent, Long>() }
    var nextSequence by remember { mutableLongStateOf(0L) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ToolBarContent(
                uiState = uiState,
                onBackPressed = onBackPressed,
                onSearchToggle = onSearchToggle,
                onFilterToggle = onToggleFilterBar,
                onSelectLevel = onSelectLevel,
                onTogglePause = onTogglePause,
                onFilterChange = onFilterChange,
                onToggleAutoScroll = onToggleAutoScroll,
                onToggleShowCompat = onToggleShowCompat,
                onToggleDeveloperMode = onToggleDeveloperMode,
                onSaveLogs = onSaveLogs,
                onClearLogs = onClearLogs,
                onScrollToTop = {
                    scope.launch { lazyListState.smoothScrollToIndex(0) }
                },
                onScrollToBottom = {
                    val last = visibleLogs.lastIndex
                    if (last >= 0) {
                        scope.launch { lazyListState.smoothScrollToIndex(last) }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onCopyAllLogs { text ->
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("kizzy_logs", text))
                        Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
            }
        }
    ) { paddingValues ->
        LaunchedEffect(uiState.autoScroll, uiState.isPaused) {
            if (!uiState.autoScroll || uiState.isPaused) return@LaunchedEffect
            snapshotFlow { visibleLogs.size }
                .distinctUntilChanged()
                .collect { size ->
                    if (size > 0) {
                        lazyListState.scrollToItem(size - 1)
                    }
                }
        }
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(
                items = visibleLogs,
                key = { logEvent ->
                    val seq = sequenceByEntry.getOrPut(logEvent) { nextSequence++ }
                    "${logEvent.createdAt}_${logEvent.level.name}_${logEvent.tag}_$seq"
                }
            ) { logEvent ->
                val timeText = remember(logEvent.createdAt) {
                    LogDateFormat.format(Date(logEvent.createdAt))
                }
                if (uiState.showCompat) {
                    LogEntryCompat(logEvent, clipboardManager, copiedToClipboard, timeText)
                } else {
                    LogsCard(logEvent, clipboardManager, copiedToClipboard, timeText)
                }
            }
        }
    }
}

@Composable
fun LogEntryCompat(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    copiedToClipboard: String,
    timeText: String = LogDateFormat.format(Date(logEvent.createdAt)),
) {
    val context = LocalContext.current
    var isExpanded by remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = {
                        val formattedLog = "[$timeText] ${logEvent.level.name} ${logEvent.tag}: ${logEvent.text}"
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", formattedLog))
                        Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                    }
                )
                .padding(10.dp)
        ) {
            Text(
                text = logEvent.annotated(timeText),
                style = MaterialTheme.typography.labelMedium.copy(color = colorScheme.onSurface),
                overflow = TextOverflow.Ellipsis,
                maxLines = if (isExpanded) Int.MAX_VALUE else 10
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsCard(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    copiedToClipboard: String,
    timeText: String = LogDateFormat.format(Date(logEvent.createdAt)),
) {
    val context = LocalContext.current
    var isExpanded by remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = {
                        val formattedLog = "[$timeText] ${logEvent.level.name} ${logEvent.tag}: ${logEvent.text}"
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("", formattedLog))
                        Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(levelColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = logEvent.level.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = levelColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Text(
                        text = logEvent.tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = logEvent.text,
                    style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isExpanded) {
                    Text(
                        text = stringResource(R.string.tap_to_collapse),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolBarContent(
    uiState: LogsUiState,
    onBackPressed: () -> Unit,
    onSearchToggle: () -> Unit,
    onFilterToggle: () -> Unit,
    onSelectLevel: (LogLevel?) -> Unit,
    onTogglePause: () -> Unit,
    onFilterChange: (String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleDeveloperMode: () -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    onToggleShowCompat: () -> Unit,
    onSaveLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    var menuClicked by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.logs),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                BackButton { onBackPressed() }
            },
            actions = {
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (uiState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = stringResource(
                            if (uiState.isPaused) R.string.resume_logs else R.string.pause_logs
                        )
                    )
                }
                IconButton(onClick = onFilterToggle) {
                    Icon(Icons.Outlined.FilterList, contentDescription = stringResource(R.string.filter_logs))
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Outlined.Search, stringResource(id = R.string.search))
                }
                IconButton(onClick = { menuClicked = !menuClicked }) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more))
                    DropdownMenu(
                        expanded = menuClicked,
                        onDismissRequest = { menuClicked = false }
                    ) {
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onTogglePause() },
                            text = {
                                Text(
                                    text = stringResource(
                                        if (uiState.isPaused) R.string.resume_logs else R.string.pause_logs
                                    ),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        )
                        DropdownMenuItem(
                            onClick = onToggleAutoScroll,
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = stringResource(id = R.string.auto_scroll), style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = uiState.autoScroll, onClick = onToggleAutoScroll, modifier = Modifier.padding(start = 20.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onScrollToTop() },
                            text = { Text(text = stringResource(R.string.jump_to_top), style = MaterialTheme.typography.bodyLarge) }
                        )
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onScrollToBottom() },
                            text = { Text(text = stringResource(R.string.jump_to_bottom), style = MaterialTheme.typography.bodyLarge) }
                        )
                        DropdownMenuItem(
                            onClick = onToggleDeveloperMode,
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = stringResource(R.string.developer_mode), style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = uiState.developerMode, onClick = onToggleDeveloperMode, modifier = Modifier.padding(start = 20.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = onToggleShowCompat,
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = stringResource(R.string.compact_mode), style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = uiState.showCompat, onClick = onToggleShowCompat, modifier = Modifier.padding(start = 20.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onSaveLogs() },
                            text = { Text(text = stringResource(R.string.save_logs_to_file), style = MaterialTheme.typography.bodyLarge) }
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.clear), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { onClearLogs(); menuClicked = false }
                        )
                    }
                }
            }
        )
        AnimatedVisibility(visible = uiState.isFilterBarVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedLevel == null,
                    onClick = { onSelectLevel(null) },
                    label = { Text(stringResource(R.string.all)) }
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = uiState.selectedLevel == level,
                        onClick = {
                            onSelectLevel(if (uiState.selectedLevel == level) null else level)
                        },
                        label = { Text(level.name) }
                    )
                }
            }
        }
        AnimatedVisibility(visible = uiState.isSearchBarVisible) {
            SearchBar(
                text = uiState.filterText,
                placeholder = stringResource(id = R.string.search_placeholder),
                onClose = {
                    onSearchToggle()
                    onFilterChange("")
                },
                onTextChanged = onFilterChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun LogEvent.annotated(timeText: String = LogDateFormat.format(createdAt)) = buildAnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    withStyle(style = SpanStyle(color = colorScheme.onBackground, background = level.color().copy(0.4f))) {
        append(" ${level.name[0]} ")
    }
    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, background = colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        append(timeText)
    }
    append(" $tag: $text")
}