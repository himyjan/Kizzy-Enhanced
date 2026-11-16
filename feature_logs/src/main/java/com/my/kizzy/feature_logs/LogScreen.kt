/*
 *
 * ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  ******************************************************************
 *
 */

package com.my.kizzy.feature_logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.domain.model.logs.LogLevel
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.theme.KizzyTheme
import com.my.kizzy.ui.theme.LogColors.color
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("LocaleGetDefault")
private val LogDateFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())

@Suppress("LocalContextGetResourceValueCall")
@Composable
fun LogScreen(viewModel: LogsViewModel, onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val logsSaveError = stringResource(R.string.logs_save_error)

    LogScreenContent(
        visibleLogs = viewModel.visibleLogs,
        autoScroll = viewModel.autoScroll.value,
        showCompat = viewModel.showCompat.value,
        developerMode = viewModel.developerMode.value,
        isSearchBarVisible = viewModel.isSearchBarVisible.value,
        filterText = viewModel.filterStrings.value,
        onBackPressed = onBackPressed,
        onToggleAutoScroll = { viewModel.setAutoScroll(!viewModel.autoScroll.value) },
        onToggleShowCompat = { viewModel.setShowCompat(!viewModel.showCompat.value) },
        onToggleDeveloperMode = { viewModel.setDeveloperMode(!viewModel.developerMode.value) },
        onSaveLogs = {
            val path = viewModel.saveLogsToFile(context)
            if (path != null) {
                Toast.makeText(context, context.getString(R.string.logs_saved_toast, path), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, logsSaveError, Toast.LENGTH_SHORT).show()
            }
        },
        // @Suppress("LocalContextGetResourceValueCall") applied at LogScreen level
        onClearLogs = { viewModel.clearLogs() },
        onSearchToggle = {
            val visible = !viewModel.isSearchBarVisible.value
            viewModel.isSearchBarVisible.value = visible
            if (!visible) viewModel.setFilterStrings("")
        },
        onFilterChange = { viewModel.setFilterStrings(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreenContent(
    visibleLogs: List<LogEvent>,
    autoScroll: Boolean,
    showCompat: Boolean,
    developerMode: Boolean,
    isSearchBarVisible: Boolean,
    filterText: String,
    onBackPressed: () -> Unit = {},
    onToggleAutoScroll: () -> Unit = {},
    onToggleShowCompat: () -> Unit = {},
    onToggleDeveloperMode: () -> Unit = {},
    onSaveLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onSearchToggle: () -> Unit = {},
    onFilterChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val copiedToClipboard = stringResource(R.string.copied_to_clipboard)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ToolBarContent(
                isSearchBarVisible = isSearchBarVisible,
                filterText = filterText,
                autoScroll = autoScroll,
                showCompat = showCompat,
                developerMode = developerMode,
                onBackPressed = onBackPressed,
                onSearchToggle = onSearchToggle,
                onFilterChange = onFilterChange,
                onToggleAutoScroll = onToggleAutoScroll,
                onToggleShowCompat = onToggleShowCompat,
                onToggleDeveloperMode = onToggleDeveloperMode,
                onSaveLogs = onSaveLogs,
                onClearLogs = onClearLogs,
                onScrollToTop = { scope.launch { lazyListState.scrollToItem(0) } },
                onScrollToBottom = {
                    if (visibleLogs.isNotEmpty()) {
                        scope.launch { lazyListState.scrollToItem(visibleLogs.lastIndex) }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val allLogs = visibleLogs.joinToString("\n") { log ->
                        "[${LogDateFormat.format(Date(log.createdAt))}] ${log.level.name} ${log.tag}: ${log.text}"
                    }
                    val truncated = if (allLogs.length > 500_000) allLogs.takeLast(500_000) + "\n... (truncated)" else allLogs
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("kizzy_logs", truncated))
                    Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy all logs")
            }
        }
    ) { paddingValues ->
        LaunchedEffect(autoScroll, visibleLogs) {
            if (autoScroll && visibleLogs.isNotEmpty()) {
                val isNearBottom = lazyListState.firstVisibleItemIndex >= (visibleLogs.size - 2)
                if (isNearBottom || lazyListState.firstVisibleItemIndex == 0) {
                    lazyListState.scrollToItem(visibleLogs.lastIndex)
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
                key = { logEvent -> "${logEvent.createdAt}-${logEvent.tag}-${logEvent.text.hashCode()}" }
            ) { logEvent ->
                if (showCompat) {
                    LogEntryCompat(logEvent, clipboardManager, copiedToClipboard)
                } else {
                    LogsCard(logEvent, clipboardManager, copiedToClipboard)
                }
            }
        }
    }
}

@Composable
fun LogEntryCompat(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    copiedToClipboard: String
) {
    val context = LocalContext.current
    var isExpanded by remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.06f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(levelColor.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .border(1.dp, levelColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logEvent.annotated(),
                style = MaterialTheme.typography.labelMedium.copy(color = colorScheme.onSurface.copy(alpha = 0.9f)),
                modifier = Modifier
                    .combinedClickable(
                        onClick = { isExpanded = !isExpanded },
                        onLongClick = {
                            val formattedLog = "[${LogDateFormat.format(Date(logEvent.createdAt))}] ${logEvent.level.name} ${logEvent.tag}: ${logEvent.text}"
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", formattedLog))
                            Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                        }
                    )
                    .padding(4.dp),
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
    copiedToClipboard: String
) {
    val context = LocalContext.current
    var isExpanded by remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        ElevatedCard(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceVariant.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                .border(width = 1.dp, color = levelColor.copy(alpha = 0.35f), shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(levelColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = logEvent.level.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
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
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    Text(
                        text = LogDateFormat.format(Date(logEvent.createdAt)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = logEvent.text,
                    style = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface.copy(alpha = 0.92f)),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { isExpanded = !isExpanded },
                            onLongClick = {
                                val formattedLog = "[${LogDateFormat.format(Date(logEvent.createdAt))}] ${logEvent.level.name} ${logEvent.tag}: ${logEvent.text}"
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("", formattedLog))
                                Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                            }
                        )
                )

                if (isExpanded) {
                    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = "Tap to collapse",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp).fillMaxWidth())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolBarContent(
    isSearchBarVisible: Boolean,
    filterText: String,
    autoScroll: Boolean,
    showCompat: Boolean,
    developerMode: Boolean,
    onBackPressed: () -> Unit,
    onSearchToggle: () -> Unit,
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
        LargeTopAppBar(
            title = {
                Text(text = stringResource(id = R.string.logs), style = MaterialTheme.typography.headlineMedium)
            },
            navigationIcon = {
                BackButton { onBackPressed() }
            },
            actions = {
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Outlined.Search, stringResource(id = R.string.search))
                }
                IconButton(onClick = { menuClicked = !menuClicked }) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "menu")
                    DropdownMenu(
                        expanded = menuClicked,
                        onDismissRequest = { menuClicked = false }
                    ) {
                        DropdownMenuItem(
                            onClick = {},
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = stringResource(id = R.string.auto_scroll), style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = autoScroll, onClick = onToggleAutoScroll, modifier = Modifier.padding(start = 20.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onScrollToTop() },
                            text = { Text(text = "\uD83D\uDCC9 Jump to Top", style = MaterialTheme.typography.bodyLarge) }
                        )
                        DropdownMenuItem(
                            onClick = { menuClicked = false; onScrollToBottom() },
                            text = { Text(text = "\uD83D\uDCC9 Jump to Bottom", style = MaterialTheme.typography.bodyLarge) }
                        )
                        DropdownMenuItem(
                            onClick = onToggleDeveloperMode,
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Developer Mode", style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = developerMode, onClick = onToggleDeveloperMode, modifier = Modifier.padding(start = 20.dp))
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = onToggleShowCompat,
                            text = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = stringResource(R.string.compact_mode), style = MaterialTheme.typography.bodyLarge)
                                    KSwitch(checked = showCompat, onClick = onToggleShowCompat, modifier = Modifier.padding(start = 20.dp))
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
        AnimatedVisibility(visible = isSearchBarVisible) {
            SearchBar(
                text = filterText,
                placeholder = stringResource(id = R.string.search_placeholder),
                onClose = {
                    onSearchToggle()
                    onFilterChange("")
                },
                onTextChanged = onFilterChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun LogEvent.annotated() = buildAnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    withStyle(style = SpanStyle(color = colorScheme.onBackground, background = level.color().copy(0.4f))) {
        append(" ${level.name[0]} ")
    }
    withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold, background = colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        append(LogDateFormat.format(createdAt))
    }
    append(" $tag: $text")
}
