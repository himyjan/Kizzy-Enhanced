/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CustomRpcBoardScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_settings.rpc_settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.CustomRpcBoardItem
import com.my.kizzy.feature_rpc_base.CustomRpcBoardManager
import com.my.kizzy.resources.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRpcBoardScreen(
    onBackPressed: () -> Unit,
    boardManager: CustomRpcBoardManager = CustomRpcBoardManager.getInstance(LocalContext.current.applicationContext)
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingBoard: CustomRpcBoardItem? by remember { mutableStateOf(null) }

    val boards by remember { mutableStateOf(boardManager.loadBoards()) }
    var activeBoardId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        activeBoardId = boardManager.getActiveBoardId()
    }

    @Composable
    fun BoardCard(board: CustomRpcBoardItem, isActive: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = board.label.ifBlank { board.name.ifBlank { context.getString(R.string.custom_rpc_board_label) } },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val desc = buildString {
                        if (board.applicationId.isNotBlank()) append("App ID: ${board.applicationId}  ")
                        if (board.name.isNotBlank()) append("• Name: ${board.name}  ")
                        if (board.details.isNotBlank()) append("• Details: ${board.details}")
                    }
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(
                    modifier = Modifier.width(84.dp),
                ) {
                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggle() },
                    )
                    TextButton(onClick = onEdit) {
                        Text(text = context.getString(R.string.custom_rpc_board_edit))
                    }
                    TextButton(onClick = onDelete) {
                        Text(text = context.getString(R.string.custom_rpc_board_delete))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        androidx.compose.material3.TopAppBar(
            title = {
                Text(text = context.getString(R.string.custom_rpc_board_open))
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackArrowBack,
                        contentDescription = null,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(boards, key = { it.id }) { board ->
                val isActive = board.id == activeBoardId
                BoardCard(
                    board = board,
                    isActive = isActive,
                    onToggle = {
                        scope.launch {
                            if (isActive) {
                                boardManager.deactivateBoard()
                                activeBoardId = null
                            } else {
                                boardManager.activateBoard(board)
                                activeBoardId = board.id
                            }
                        }
                    },
                    onEdit = {
                        editingBoard = board
                        showEditorDialog = true
                    },
                    onDelete = {
                        scope.launch {
                            boardManager.deleteBoard(board.id)
                            if (board.id == activeBoardId) {
                                activeBoardId = null
                            }
                        }
                    },
                )
            }
        }

        FilledTonalButton(
            onClick = {
                editingBoard = null
                showEditorDialog = true
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(text = context.getString(R.string.custom_rpc_board_add))
        }

        if (showEditorDialog) {
            CustomRpcBoardEditorDialog(
                board = editingBoard,
                onDismiss = { showEditorDialog = false; editingBoard = null },
                onSave = { board ->
                    scope.launch {
                        if (board.id.isBlank()) {
                            boardManager.addBoard(board)
                        } else {
                            boardManager.updateBoard(board)
                        }
                    }
                    showEditorDialog = false
                    editingBoard = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRpcBoardEditorDialog(
    board: CustomRpcBoardItem?,
    onDismiss: () -> Unit,
    onSave: (CustomRpcBoardItem) -> Unit,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var id by remember { mutableStateOf(board?.id ?: java.util.UUID.randomUUID().toString()) }
    var label by remember { mutableStateOf(board?.label ?: "") }
    var applicationId by remember { mutableStateOf(board?.applicationId ?: "") }
    var activityType by remember { mutableStateOf(board?.activityType?.toString() ?: "0") }
    var name by remember { mutableStateOf(board?.name ?: "") }
    var details by remember { mutableStateOf(board?.details ?: "") }
    var state by remember { mutableStateOf(board?.state ?: "") }
    var largeImage by remember { mutableStateOf(board?.largeImage ?: "") }
    var smallImage by remember { mutableStateOf(board?.smallImage ?: "") }
    var largeText by remember { mutableStateOf(board?.largeText ?: "") }
    var smallText by remember { mutableStateOf(board?.smallText ?: "") }
    var button1Label by remember { mutableStateOf(board?.button1Label ?: "") }
    var button1Url by remember { mutableStateOf(board?.button1Url ?: "") }
    var button2Label by remember { mutableStateOf(board?.button2Label ?: "") }
    var button2Url by remember { mutableStateOf(board?.button2Url ?: "") }
    var timestampMode by remember { mutableStateOf(board?.timestampMode ?: "NONE") }
    var customStartMs by remember { mutableStateOf(board?.customStartMs?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (board == null) context.getString(R.string.custom_rpc_board_add) else context.getString(R.string.custom_rpc_board_edit)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_label), value = label, onValueChange = { label = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_application_id), value = applicationId, onValueChange = { applicationId = it })
                DropdownMenuRow(
                    label = context.getString(R.string.custom_rpc_board_activity_type),
                    items = listOf("0", "1", "2", "3", "5"),
                    labels = listOf("Playing", "Streaming", "Listening", "Watching", "Competing"),
                    onValueChange = { activityType = it },
                )
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_name), value = name, onValueChange = { name = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_details), value = details, onValueChange = { details = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_state), value = state, onValueChange = { state = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_large_image), value = largeImage, onValueChange = { largeImage = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_small_image), value = smallImage, onValueChange = { smallImage = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_large_text), value = largeText, onValueChange = { largeText = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_small_text), value = smallText, onValueChange = { smallText = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_button1_label), value = button1Label, onValueChange = { button1Label = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_button1_url), value = button1Url, onValueChange = { button1Url = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_button2_label), value = button2Label, onValueChange = { button2Label = it })
                TextFieldRow(label = context.getString(R.string.custom_rpc_board_button2_url), value = button2Url, onValueChange = { button2Url = it })
                TextFieldRow(label = "Timestamp Mode", value = timestampMode, onValueChange = { timestampMode = it })
                TextFieldRow(label = "Custom Start (ms)", value = customStartMs, onValueChange = { customStartMs = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    CustomRpcBoardItem(
                        id = id,
                        label = label,
                        applicationId = applicationId,
                        activityType = activityType.toIntOrNull() ?: 0,
                        name = name,
                        details = details,
                        state = state,
                        largeImage = largeImage,
                        smallImage = smallImage,
                        largeText = largeText,
                        smallText = smallText,
                        button1Label = button1Label,
                        button1Url = button1Url,
                        button2Label = button2Label,
                        button2Url = button2Url,
                        timestampMode = timestampMode,
                        customStartMs = customStartMs.toLongOrNull(),
                    )
                )
            }) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.material3.TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DropdownMenuRow(
    label: String,
    items: List<String>,
    labels: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        items.forEachIndexed { index ->
            DropdownMenuItem(
                text = { Text(text = labels[index]) },
                onClick = {
                    selectedIndex = index
                    onValueChange(items[index])
                    expanded = false
                },
            )
        }
    }

    TextButton(onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = labels[selectedIndex])
            Text(text = "▾")
        }
    }
}
