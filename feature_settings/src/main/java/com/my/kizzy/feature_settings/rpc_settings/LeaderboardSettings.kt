/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LeaderboardSettings.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  ******************************************************************
 *
 */

package com.my.kizzy.feature_settings.rpc_settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_rpc_base.services.leaderboard.DiscordLeaderboardManager
import com.my.kizzy.feature_rpc_base.services.leaderboard.LeaderboardEntry
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SettingItem
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardSettings(
    onBackPressed: () -> Unit,
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager: DiscordLeaderboardManager = remember(context) {
        DiscordLeaderboardManager.getInstance(context.applicationContext)
    }

    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap<String, Any?>())

    val isEnabled = prefsMap[Prefs.DISCORD_LEADERBOARD_ENABLED] as? Boolean ?: false
    val botToken = prefsMap[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN] as? String ?: ""
    val channelId = prefsMap[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID] as? String ?: ""
    val intervalMinutes = (prefsMap[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN] as? Long ?: 5L).toInt().coerceAtLeast(1)
    val maxEntries = (prefsMap[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES] as? Int ?: 10).coerceIn(1, 25)
    val title = prefsMap[Prefs.DISCORD_LEADERBOARD_TITLE] as? String ?: "🏆 Kizzy Leaderboard"
    val description = prefsMap[Prefs.DISCORD_LEADERBOARD_DESCRIPTION] as? String ?: ""

    var entries by remember { mutableStateOf(emptyList<LeaderboardEntry>()) }
    var refreshKey by remember { mutableLongStateOf(0L) }

    var showBotTokenDialog by remember { mutableStateOf(false) }
    var showChannelIdDialog by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showMaxEntriesDialog by remember { mutableStateOf(false) }

    fun loadEntries() {
        scope.launch {
            entries = manager.getTopEntries(maxEntries)
        }
    }

    fun postNow() {
        scope.launch {
            val top = manager.getTopEntries(maxEntries)
            if (top.isEmpty()) {
                Toast.makeText(context, R.string.discord_leaderboard_no_scores, Toast.LENGTH_SHORT).show()
                return@launch
            }
            manager.updateLeaderboard(top)
            Toast.makeText(context, R.string.discord_leaderboard_post_success, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(refreshKey, maxEntries) {
        loadEntries()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.discord_leaderboard)) },
            navigationIcon = {
                BackButton(onClick = onBackPressed)
            },
        )

        Scaffold(modifier = Modifier.weight(1f)) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_connection))
                }
                item(key = "leaderboard_enabled") {
                    PreferenceSwitch(
                        title = stringResource(R.string.discord_leaderboard_enabled),
                        description = stringResource(R.string.discord_leaderboard_enabled_desc),
                        icon = Icons.AutoMirrored.Outlined.List,
                        isChecked = isEnabled,
                    ) {
                        val next = !isEnabled
                        Prefs[Prefs.DISCORD_LEADERBOARD_ENABLED] = next
                        if (next) {
                            manager.startPeriodicUpdates(intervalMinutes.toLong())
                        } else {
                            manager.stopPeriodicUpdates()
                        }
                    }
                }
                item(key = "leaderboard_bot_token") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_bot_token),
                        description = stringResource(R.string.discord_leaderboard_bot_token_desc) + "  •  ${botToken.ifEmpty { "(not set)" }}",
                        icon = Icons.AutoMirrored.Outlined.List,
                    ) {
                        showBotTokenDialog = true
                    }
                }
                item(key = "leaderboard_channel_id") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_channel_id),
                        description = stringResource(R.string.discord_leaderboard_channel_id_desc) + "  •  ${channelId.ifEmpty { "(not set)" }}",
                        icon = Icons.AutoMirrored.Outlined.List,
                    ) {
                        showChannelIdDialog = true
                    }
                }
                item(key = "leaderboard_post_now") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_post_now),
                        description = stringResource(R.string.discord_leaderboard_post_now_desc),
                        icon = vectorResource(R.drawable.ic_post_add),
                    ) {
                        if (botToken.isBlank() || channelId.isBlank()) {
                            Toast.makeText(context, R.string.discord_leaderboard_config_missing, Toast.LENGTH_SHORT).show()
                        } else {
                            postNow()
                        }
                    }
                }

                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_embed))
                }
                item(key = "leaderboard_title") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_title),
                        description = stringResource(R.string.discord_leaderboard_title_desc) + "  •  $title",
                        icon = Icons.AutoMirrored.Outlined.List,
                    ) {
                        showTitleDialog = true
                    }
                }
                item(key = "leaderboard_description") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_description),
                        description = stringResource(R.string.discord_leaderboard_description_desc) + (if (description.isNotEmpty()) "  •  $description" else "  •  (not set)"),
                        icon = Icons.AutoMirrored.Outlined.List,
                    ) {
                        showDescriptionDialog = true
                    }
                }

                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_updates))
                }
                item(key = "leaderboard_interval") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_interval),
                        description = stringResource(R.string.discord_leaderboard_interval_desc) + "  •  $intervalMinutes min",
                        icon = vectorResource(R.drawable.ic_schedule),
                    ) {
                        showIntervalDialog = true
                    }
                }
                item(key = "leaderboard_max_entries") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_max_entries),
                        description = stringResource(R.string.discord_leaderboard_max_entries_desc) + "  •  $maxEntries",
                        icon = Icons.AutoMirrored.Outlined.List,
                    ) {
                        showMaxEntriesDialog = true
                    }
                }

                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_top_entries))
                }
                if (entries.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.discord_leaderboard_no_entries),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> "${entry.playerId}_$index" },
                    ) { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.discord_leaderboard_entry_points, entry.score),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = {
                            if (botToken.isBlank() || channelId.isBlank()) {
                                Toast.makeText(context, R.string.discord_leaderboard_config_missing, Toast.LENGTH_SHORT).show()
                            } else {
                                postNow()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.discord_leaderboard_post_now))
                    }
                }
            }
        }
    }

    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_bot_token,
        initialValue = botToken,
        visible = showBotTokenDialog,
        onDismiss = { showBotTokenDialog = false },
        onSave = { Prefs[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN] = it.trim() },
    )
    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_channel_id,
        initialValue = channelId,
        visible = showChannelIdDialog,
        onDismiss = { showChannelIdDialog = false },
        onSave = { Prefs[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID] = it.trim() },
    )
    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_title,
        initialValue = title,
        visible = showTitleDialog,
        onDismiss = { showTitleDialog = false },
        onSave = { Prefs[Prefs.DISCORD_LEADERBOARD_TITLE] = it },
    )
    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_description,
        initialValue = description,
        visible = showDescriptionDialog,
        onDismiss = { showDescriptionDialog = false },
        onSave = { Prefs[Prefs.DISCORD_LEADERBOARD_DESCRIPTION] = it },
    )
    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_interval,
        initialValue = intervalMinutes.toString(),
        visible = showIntervalDialog,
        onDismiss = { showIntervalDialog = false },
        onSave = {
            val nextInterval = (it.toIntOrNull() ?: 5).coerceAtLeast(1)
            Prefs[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN] = nextInterval.toLong()
            if (isEnabled) {
                manager.startPeriodicUpdates(nextInterval.toLong())
            }
        },
    )
    LeaderboardTextDialog(
        titleRes = R.string.discord_leaderboard_max_entries,
        initialValue = maxEntries.toString(),
        visible = showMaxEntriesDialog,
        onDismiss = { showMaxEntriesDialog = false },
        onSave = {
            val nextMax = (it.toIntOrNull() ?: 10).coerceIn(1, 25)
            Prefs[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES] = nextMax
            refreshKey = System.currentTimeMillis()
        },
    )
}

@Composable
private fun LeaderboardTextDialog(
    titleRes: Int,
    initialValue: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    if (!visible) return
    val textFieldState = rememberTextFieldState(initialText = initialValue)

    LaunchedEffect(visible, initialValue) {
        if (visible && textFieldState.text.toString() != initialValue) {
            textFieldState.setTextAndPlaceCursorAtEnd(initialValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                state = textFieldState,
                label = { Text(text = stringResource(titleRes)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val value = textFieldState.text.toString()
                onDismiss()
                onSave(value)
            }) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}