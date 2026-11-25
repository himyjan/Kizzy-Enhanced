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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_rpc_base.services.leaderboard.DiscordLeaderboardManager
import com.my.kizzy.feature_rpc_base.services.leaderboard.LeaderboardEntry
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SettingItem
import com.my.kizzy.ui.components.Subtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardSettings(
    onBackPressed: () -> Unit,
) {
    val context: Context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val manager: DiscordLeaderboardManager = remember {
        DiscordLeaderboardManager.getInstance(context.applicationContext)
    }

    // Snapshot of prefs so the UI reacts to DataStore priming.
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap<String, Any?>())

    var isEnabled by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.DISCORD_LEADERBOARD_ENABLED] as? Boolean ?: false)
    }
    var botToken by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN] as? String ?: "")
    }
    var channelId by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID] as? String ?: "")
    }
    var intervalMinutes by remember(prefsMap) {
        mutableStateOf((prefsMap[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN] as? Long ?: 5L).toInt())
    }
    var maxEntries by remember(prefsMap) {
        mutableStateOf((prefsMap[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES] as? Int ?: 10).coerceAtLeast(1).coerceAtMost(25))
    }
    var title by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.DISCORD_LEADERBOARD_TITLE] as? String ?: "🏆 Kizzy Leaderboard")
    }
    var description by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.DISCORD_LEADERBOARD_DESCRIPTION] as? String ?: "")
    }

    var entries by remember { mutableStateOf(emptyList<LeaderboardEntry>()) }
    var refreshKey by remember { mutableStateOf(0L) }

    // Dialog visibility flags (dialogs are rendered below the column so they run
    // in a Compose context, unlike SettingItem.onClick).
    var showBotTokenDialog by remember { mutableStateOf(false) }
    var showChannelIdDialog by remember { mutableStateOf(false) }
    var showTitleDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showMaxEntriesDialog by remember { mutableStateOf(false) }

    // Draft values for the open dialog.
    var draftBotToken by remember { mutableStateOf(botToken) }
    var draftChannelId by remember { mutableStateOf(channelId) }
    var draftTitle by remember { mutableStateOf(title) }
    var draftDescription by remember { mutableStateOf(description) }
    var draftInterval by remember { mutableStateOf(intervalMinutes.toString()) }
    var draftMaxEntries by remember { mutableStateOf(maxEntries.toString()) }

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

    LaunchedEffect(refreshKey) {
        loadEntries()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.discord_leaderboard)) },
            navigationIcon = {
                BackButton(onClick = onBackPressed)
            },
        )

        androidx.compose.material3.Scaffold() { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_connection))
                }
                item(key = "leaderboard_enabled") {
                    com.my.kizzy.ui.components.preference.PreferenceSwitch(
                        title = stringResource(R.string.discord_leaderboard_enabled),
                        description = stringResource(R.string.discord_leaderboard_enabled_desc),
                        icon = Icons.Outlined.List,
                        isChecked = isEnabled,
                    ) {
                        isEnabled = !isEnabled
                        Prefs[Prefs.DISCORD_LEADERBOARD_ENABLED] = isEnabled
                        if (isEnabled) {
                            manager.startPeriodicUpdates(intervalMinutes.toLong())
                        } else {
                            manager.stopPeriodicUpdates()
                        }
                    }
                }
                item(key = "leaderboard_bot_token") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_bot_token),
                        description = stringResource(R.string.discord_leaderboard_bot_token_desc) + "  •  ${botToken.ifEmpty { "(not set)" } }",
                        icon = Icons.Outlined.Link,
                    ) {
                        draftBotToken = botToken
                        showBotTokenDialog = true
                    }
                }
                item(key = "leaderboard_channel_id") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_channel_id),
                        description = stringResource(R.string.discord_leaderboard_channel_id_desc) + "  •  ${channelId.ifEmpty { "(not set)" } }",
                        icon = Icons.Outlined.Link,
                    ) {
                        draftChannelId = channelId
                        showChannelIdDialog = true
                    }
                }
                item(key = "leaderboard_post_now") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_post_now),
                        description = stringResource(R.string.discord_leaderboard_post_now_desc),
                        icon = Icons.Outlined.PostAdd,
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
                        icon = Icons.Outlined.List,
                    ) {
                        draftTitle = title
                        showTitleDialog = true
                    }
                }
                item(key = "leaderboard_description") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_description),
                        description = stringResource(R.string.discord_leaderboard_description_desc) + (if (description.isNotEmpty()) "  •  $description" else "  •  (not set)"),
                        icon = Icons.Outlined.List,
                    ) {
                        draftDescription = description
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
                        icon = Icons.Outlined.Schedule,
                    ) {
                        draftInterval = intervalMinutes.toString()
                        showIntervalDialog = true
                    }
                }
                item(key = "leaderboard_max_entries") {
                    SettingItem(
                        title = stringResource(R.string.discord_leaderboard_max_entries),
                        description = stringResource(R.string.discord_leaderboard_max_entries_desc) + "  •  $maxEntries",
                        icon = Icons.Outlined.List,
                    ) {
                        draftMaxEntries = maxEntries.toString()
                        showMaxEntriesDialog = true
                    }
                }

                item {
                    Subtitle(text = stringResource(R.string.discord_leaderboard_top_entries))
                }
                if (entries.isEmpty()) {
                    item {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
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
                    items(entries, key = { it.playerId }) { entry ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${entries.indexOf(entry) + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
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

        // --- Dialogs (rendered here so they run in a Compose context) ---------

        @Composable
    fun textDialog(titleRes: Int, draft: () -> String, onSave: (String) -> Unit, visible: Boolean, onDismiss: () -> Unit) {
            if (!visible) return
            var text by remember { mutableStateOf(draft()) }
            AlertDialog(
                onDismissRequest = { onDismiss(); text = draft() },
                title = { Text(text = stringResource(titleRes)) },
                text = {
                    androidx.compose.material3.TextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(text = stringResource(titleRes)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDismiss()
                        onSave(text)
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

        // Note: the dialog lambdas capture `text` via remember, but the draft is
        // reset when the dialog opens (see onClick handlers above).

        textDialog(
            titleRes = R.string.discord_leaderboard_bot_token,
            draft = { draftBotToken },
            onSave = { botToken = it; Prefs[Prefs.DISCORD_LEADERBOARD_BOT_TOKEN] = botToken },
            visible = showBotTokenDialog,
            onDismiss = { showBotTokenDialog = false },
        )
        textDialog(
            titleRes = R.string.discord_leaderboard_channel_id,
            draft = { draftChannelId },
            onSave = { channelId = it; Prefs[Prefs.DISCORD_LEADERBOARD_CHANNEL_ID] = channelId },
            visible = showChannelIdDialog,
            onDismiss = { showChannelIdDialog = false },
        )
        textDialog(
            titleRes = R.string.discord_leaderboard_title,
            draft = { draftTitle },
            onSave = { title = it; Prefs[Prefs.DISCORD_LEADERBOARD_TITLE] = title },
            visible = showTitleDialog,
            onDismiss = { showTitleDialog = false },
        )
        textDialog(
            titleRes = R.string.discord_leaderboard_description,
            draft = { draftDescription },
            onSave = { description = it; Prefs[Prefs.DISCORD_LEADERBOARD_DESCRIPTION] = description },
            visible = showDescriptionDialog,
            onDismiss = { showDescriptionDialog = false },
        )
        textDialog(
            titleRes = R.string.discord_leaderboard_interval,
            draft = { draftInterval },
            onSave = {
                val parsed = it.toIntOrNull() ?: 5
                intervalMinutes = parsed.coerceAtLeast(1)
                Prefs[Prefs.DISCORD_LEADERBOARD_UPDATE_INTERVAL_MIN] = intervalMinutes.toLong()
                if (isEnabled) {
                    manager.startPeriodicUpdates(intervalMinutes.toLong())
                }
            },
            visible = showIntervalDialog,
            onDismiss = { showIntervalDialog = false },
        )
        textDialog(
            titleRes = R.string.discord_leaderboard_max_entries,
            draft = { draftMaxEntries },
            onSave = {
                val parsed = it.toIntOrNull() ?: 10
                maxEntries = parsed.coerceAtLeast(1).coerceAtMost(25)
                Prefs[Prefs.DISCORD_LEADERBOARD_MAX_ENTRIES] = maxEntries
                refreshKey = System.currentTimeMillis()
            },
            visible = showMaxEntriesDialog,
            onDismiss = { showMaxEntriesDialog = false },
        )
    }
}
