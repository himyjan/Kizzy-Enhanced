/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LogScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_logs

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.theme.LogColors.color
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: LogsViewModel, onBackPressed: () -> Unit = {}) {
    val ctx = LocalContext.current
    val lazyListState = rememberLazyListState()
    val clipboardManager: ClipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Read observable state - Compose recomposes only when these change
    val visibleLogs = viewModel.visibleLogs
    val autoScroll = viewModel.autoScroll.value
    val showCompat = viewModel.showCompat.value

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { ToolBar(viewModel, onBackPressed) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val allLogs = visibleLogs.joinToString("\n") { it.text }
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("kizzy_logs", allLogs))
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
        LaunchedEffect(visibleLogs.lastOrNull(), autoScroll) {
            if (visibleLogs.isNotEmpty() && autoScroll) {
                lazyListState.animateScrollToItem(visibleLogs.lastIndex)
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
                key = { "${it.createdAt}/${it.tag.hashCode()}/${System.identityHashCode(it)}" }
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
private fun LogEntryCompat(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    ctx: Context
) {
    val isExpanded = remember(logEvent) { mutableStateOf(false) }
    Text(
        text = logEvent.annotated(),
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .combinedClickable(
                onClick = {
                    isExpanded.value = !isExpanded.value
                },
                onLongClick = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("", logEvent.text))
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogsCard(
    logEvent: LogEvent,
    clipboardManager: ClipboardManager,
    ctx: Context
) {
    val isExpanded = remember(logEvent) { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val levelColor = logEvent.level.color()

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(levelColor.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = logEvent.tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Text(
                        text = logEvent.text,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (isExpanded.value) Int.MAX_VALUE else 3,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                isExpanded.value = !isExpanded.value
                            },
                            onLongClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("", logEvent.text))
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.copied_to_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        )
                    )
                    Text(
                        text = SimpleDateFormat("h:mm:ss a").format(Date(logEvent.createdAt)),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    Spacer(
        modifier = Modifier
            .height(8.dp)
            .fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ToolBar(viewModel: LogsViewModel, onBackPressed: () -> Unit) {
    var menuClicked by remember {
        mutableStateOf(false)
    }
    val showCompat = viewModel.showCompat.value
    val autoScroll = viewModel.autoScroll.value
    val filterText = viewModel.filterStrings.value
    val colorScheme = MaterialTheme.colorScheme

    LargeTopAppBar(
        title = {
            if (viewModel.isSearchBarVisible.value) {
                SearchBar(
                    text = filterText,
                    placeholder = stringResource(id = R.string.search_placeholder),
                    onClose = { viewModel.isSearchBarVisible.value = false }
                ) {
                    viewModel.setFilterStrings(it)
                }
            } else
                Text(
                    text = stringResource(R.string.logs),
                    style = MaterialTheme.typography.headlineMedium
                )
        },
        navigationIcon = {
            BackButton {
                onBackPressed()
            }
        },
        actions = {
            if (!viewModel.isSearchBarVisible.value) {
                IconButton(onClick = { viewModel.isSearchBarVisible.value = true }) {
                    Icon(Icons.Outlined.Search, stringResource(id = R.string.search))
                }
            }
            IconButton(onClick = { menuClicked = !menuClicked }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert, contentDescription = "menu"
                )
                DropdownMenu(
                    expanded = menuClicked,
                    onDismissRequest = { menuClicked = !menuClicked }) {
                    DropdownMenuItem(
                        onClick = {},
                        text = {
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        viewModel.setAutoScroll(!autoScroll)
                                    }, horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.auto_scroll),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                KSwitch(
                                    checked = autoScroll,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        })
                    DropdownMenuItem(
                        onClick = {},
                        text = {
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        viewModel.setShowCompat(!showCompat)
                                    }, horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.compact_mode),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                KSwitch(
                                    checked = showCompat,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        })
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.clear),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        onClick = {
                            viewModel.clearLogs()
                            menuClicked = false
                        }
                    )
                }
            }
        })
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
