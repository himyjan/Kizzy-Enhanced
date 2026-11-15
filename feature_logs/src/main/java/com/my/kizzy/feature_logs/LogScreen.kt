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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.width
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.domain.model.logs.LogEvent
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.theme.LogColors.color
import androidx.compose.ui.tooling.preview.Preview
import com.my.kizzy.domain.model.logs.LogLevel
import com.my.kizzy.ui.theme.KizzyTheme
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreenContent(
    visibleLogs: List<LogEvent>,
    autoScroll: Boolean,
    showCompat: Boolean,
    onBackPressed: () -> Unit = {},
    onToggleAutoScroll: () -> Unit = {},
    onToggleShowCompat: () -> Unit = {},
    onSaveLogs: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onSearchToggle: () -> Unit = {},
    onFilterChange: (String) -> Unit = {},
    isSearchBarVisible: Boolean = false,
    filterText: String = "",
) {
    val ctx = LocalContext.current
    val lazyListState = rememberLazyListState()
    val clipboardManager: ClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ToolBarContent(
                isSearchBarVisible = isSearchBarVisible,
                filterText = filterText,
                autoScroll = autoScroll,
                showCompat = showCompat,
                visibleLogs = visibleLogs,
                onBackPressed = onBackPressed,
                onSearchToggle = onSearchToggle,
                onFilterChange = onFilterChange,
                onToggleAutoScroll = onToggleAutoScroll,
                onScrollToTop = { lazyListState.scrollToItem(0) },
                onScrollToBottom = { lazyListState.scrollToItem(visibleLogs.lastIndex) },
                onToggleShowCompat = onToggleShowCompat,
                onSaveLogs = onSaveLogs,
                onClearLogs = onClearLogs,
                ctx = ctx,
                lazyListState = lazyListState,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val sdf = SimpleDateFormat("h:mm:ss a")
                    val allLogs = visibleLogs.joinToString("\n") { log ->
                        "[" + sdf.format(Date(log.createdAt)) + "] " + log.level.name + " " + log.tag + ": " + log.text
                    }
                    val truncated = if (allLogs.length > 500_000) allLogs.takeLast(500_000) + "\n... (truncated)" else allLogs
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("kizzy_logs", truncated))
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.copied_to_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy all logs to clipboard"
                )
            }
        }
    ) { paddingValues ->
        LaunchedEffect(autoScroll, visibleLogs.size) {
            if (autoScroll && visibleLogs.isNotEmpty()) {
                // Use scrollToItem for instant, lag-free scrolling instead of animateScrollToItem
                lazyListState.scrollToItem(visibleLogs.lastIndex)
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
                    "${logEvent.createdAt}-${logEvent.tag}-${logEvent.text.length}-${logEvent.text.hashCode()}"
                }
            ) { logEvent ->
                if (showCompat) {
                    LogEntryCompat(logEvent, clipboardManager, ctx)
                } else {
                    LogsCard(logEvent, clipboardManager, ctx)
                }
            }
        }
    }
}

@Composable
fun LogScreen(viewModel: LogsViewModel, onBackPressed: () -> Unit = {}) {
    val ctx = LocalContext.current
    LogScreenContent(
        visibleLogs = viewModel.visibleLogs,
        autoScroll = viewModel.autoScroll.value,
        showCompat = viewModel.showCompat.value,
        onBackPressed = onBackPressed,
        onToggleAutoScroll = { viewModel.setAutoScroll(!viewModel.autoScroll.value) },
        onToggleShowCompat = { viewModel.setShowCompat(!viewModel.showCompat.value) },
        onSaveLogs = {
            val path = viewModel.saveLogsToFile(ctx)
            if (path != null) {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.logs_saved_toast, path),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.logs_save_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onClearLogs = { viewModel.clearLogs() },
        onSearchToggle = {
            val visible = !viewModel.isSearchBarVisible.value
            viewModel.isSearchBarVisible.value = visible
            if (!visible) viewModel.setFilterStrings("")
        },
        onFilterChange = { viewModel.setFilterStrings(it) },
        isSearchBarVisible = viewModel.isSearchBarVisible.value,
        filterText = viewModel.filterStrings.value,
    )
}

@Composable
fun LogEntryCompat(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    ctx: Context
) {
    val isExpanded = remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceVariant.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(levelColor.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .border(1.dp, levelColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = logEvent.annotated(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .combinedClickable(
                        onClick = { isExpanded.value = !isExpanded.value },
                        onLongClick = {
                            val sdf = SimpleDateFormat("h:mm:ss a")
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("", "[" + sdf.format(Date(logEvent.createdAt)) + "] " + logEvent.level.name + " " + logEvent.tag + ": " + logEvent.text))
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.copied_to_clipboard),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                    )
                    .padding(4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = if (isExpanded.value) Int.MAX_VALUE else 10
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsCard(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    ctx: Context
) {
    val isExpanded = remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        ElevatedCard(
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surface.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceVariant.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = levelColor.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
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
                            text = SimpleDateFormat("h:mm:ss a").format(Date(logEvent.createdAt)),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = logEvent.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colorScheme.onSurface.copy(alpha = 0.92f)
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (isExpanded.value) Int.MAX_VALUE else 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { isExpanded.value = !isExpanded.value },
                                onLongClick = {
                                    val sdf = SimpleDateFormat("h:mm:ss a")
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", "[" + sdf.format(Date(logEvent.createdAt)) + "] " + logEvent.level.name + " " + logEvent.tag + ": " + logEvent.text))
                                    Toast.makeText(
                                        ctx,
                                        ctx.getString(R.string.copied_to_clipboard),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                            )
                    )

                    if (isExpanded.value) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
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
    visibleLogs: List<LogEvent>,
    onBackPressed: () -> Unit,
    onSearchToggle: () -> Unit,
    onFilterChange: (String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onScrollToTop: suspend () -> Unit,
    onScrollToBottom: suspend () -> Unit,
    onToggleShowCompat: () -> Unit,
    onSaveLogs: () -> Unit,
    onClearLogs: () -> Unit,
    ctx: Context,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
) {
    var menuClicked by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    Column {
        LargeTopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.logs),
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            navigationIcon = {
                BackButton {
                    onBackPressed()
                }
            },
            actions = {
                IconButton(onClick = onSearchToggle) {
                    Icon(Icons.Outlined.Search, stringResource(id = R.string.search))
                }
                IconButton(onClick = { menuClicked = !menuClicked }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert, contentDescription = "menu"
                    )
                    DropdownMenu(
                        expanded = menuClicked,
                        onDismissRequest = { menuClicked = !menuClicked }
                    ) {
                        DropdownMenuItem(
                            onClick = {},
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.auto_scroll),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    KSwitch(
                                        checked = autoScroll,
                                        onClick = onToggleAutoScroll,
                                        modifier = Modifier.padding(start = 20.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                menuClicked = false
                                scope.launch {
                                    onScrollToTop()
                                }
                            },
                            text = {
                                Text(
                                    text = "📍 跳至最上方",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                menuClicked = false
                                scope.launch {
                                    onScrollToBottom()
                                }
                            },
                            text = {
                                Text(
                                    text = "📍 跳至最下方",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        )
                        DropdownMenuItem(
                            onClick = onToggleShowCompat,
                            text = {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.compact_mode),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    KSwitch(
                                        checked = showCompat,
                                        onClick = onToggleShowCompat,
                                        modifier = Modifier.padding(start = 20.dp)
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            onClick = {
                                menuClicked = false
                                scope.launch {
                                    onSaveLogs()
                                }
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.save_logs_to_file),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.clear),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            onClick = {
                                onClearLogs()
                                menuClicked = false
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolBar(
    viewModel: LogsViewModel,
    ctx: Context,
    onBackPressed: () -> Unit,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    visibleLogs: List<LogEvent>,
    autoScroll: Boolean
) {
    ToolBarContent(
        isSearchBarVisible = viewModel.isSearchBarVisible.value,
        filterText = viewModel.filterStrings.value,
        autoScroll = autoScroll,
        showCompat = viewModel.showCompat.value,
        visibleLogs = visibleLogs,
        onBackPressed = onBackPressed,
        onSearchToggle = {
            val visible = !viewModel.isSearchBarVisible.value
            viewModel.isSearchBarVisible.value = visible
            if (!visible) viewModel.setFilterStrings("")
        },
        onFilterChange = { viewModel.setFilterStrings(it) },
        onToggleAutoScroll = { viewModel.setAutoScroll(!autoScroll) },
        onScrollToTop = { lazyListState.scrollToItem(0) },
        onScrollToBottom = { lazyListState.scrollToItem(visibleLogs.lastIndex) },
        onToggleShowCompat = { viewModel.setShowCompat(!viewModel.showCompat.value) },
        onSaveLogs = {
            val path = viewModel.saveLogsToFile(ctx)
            if (path != null) {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.logs_saved_toast, path),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.logs_save_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        onClearLogs = { viewModel.clearLogs() },
        ctx = ctx,
        lazyListState = lazyListState,
    )
}

@SuppressLint("SimpleDateFormat")
@Composable
fun LogEvent.annotated() = buildAnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    withStyle(
        style = SpanStyle(
            color = colorScheme.onBackground,
            background = level.color().copy(0.4f)
        )
    ) {
        append(" ${level.name[0]} ")
    }
    withStyle(
        style = SpanStyle(
            fontWeight = FontWeight.ExtraBold,
            background = colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        append(SimpleDateFormat("h:mm:ss a").format(createdAt))
    }
    append(" $tag: $text")
}

@Preview(showBackground = true, name = "Log Screen (Light)")
@Composable
fun LogScreenPreviewLight() {
    val sampleLogs = listOf(
        LogEvent(LogLevel.INFO, "MainActivity", "Application started", System.currentTimeMillis()),
        LogEvent(LogLevel.DEBUG, "Network", "GET /api/users request sent", System.currentTimeMillis() - 15000),
        LogEvent(LogLevel.WARN, "Database", "Slow query detected: 2.5s", System.currentTimeMillis() - 30000),
        LogEvent(LogLevel.ERROR, "Auth", "Login failed: invalid credentials", System.currentTimeMillis() - 45000),
    )
    KizzyTheme(
        darkTheme = false,
        isHighContrastModeEnabled = false,
        isDynamicColorEnabled = false,
    ) {
        LogScreenContent(
            visibleLogs = sampleLogs,
            autoScroll = false,
            showCompat = false,
            onBackPressed = {},
        )
    }
}

@Preview(showBackground = true, name = "Log Screen (Dark)")
@Composable
fun LogScreenPreviewDark() {
    val sampleLogs = listOf(
        LogEvent(LogLevel.INFO, "MainActivity", "Application started", System.currentTimeMillis()),
        LogEvent(LogLevel.DEBUG, "Network", "GET /api/users request sent", System.currentTimeMillis() - 15000),
        LogEvent(LogLevel.WARN, "Database", "Slow query detected: 2.5s", System.currentTimeMillis() - 30000),
        LogEvent(LogLevel.ERROR, "Auth", "Login failed: invalid credentials", System.currentTimeMillis() - 45000),
    )
    KizzyTheme(
        darkTheme = true,
        isHighContrastModeEnabled = false,
        isDynamicColorEnabled = false,
    ) {
        LogScreenContent(
            visibleLogs = sampleLogs,
            autoScroll = true,
            showCompat = true,
            onBackPressed = {},
        )
    }
}