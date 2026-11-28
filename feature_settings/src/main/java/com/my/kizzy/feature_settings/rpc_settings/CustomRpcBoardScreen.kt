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

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.CustomRpcBoardItem
import com.my.kizzy.feature_rpc_base.CustomRpcBoardManager
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRpcBoardScreen(
    onBackPressed: () -> Unit,
    boardManager: CustomRpcBoardManager = CustomRpcBoardManager.getInstance(LocalContext.current.applicationContext)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingBoard by remember { mutableStateOf<CustomRpcBoardItem?>(null) }

    var boards by remember { mutableStateOf(boardManager.loadBoards()) }
    var activeBoardId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(boardManager) {
        activeBoardId = boardManager.getActiveBoardId()
    }

    fun refreshBoards() {
        boards = boardManager.loadBoards()
    }

    fun startCustomRpcService() {
        val intent = Intent(context, CustomRpcService::class.java).apply {
            putExtra("RPC", "")
        }
        context.startForegroundService(intent)
    }

    fun stopCustomRpcService() {
        context.stopService(Intent(context, CustomRpcService::class.java))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = {
                Text(text = stringResource(R.string.custom_rpc_board_open))
            },
            navigationIcon = {
                BackButton(onClick = onBackPressed)
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 16.dp,
                ),
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
                                refreshBoards()
                                stopCustomRpcService()
                            } else {
                                boardManager.activateBoard(board)
                                activeBoardId = board.id
                                refreshBoards()
                                startCustomRpcService()
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
                                stopCustomRpcService()
                            }
                            refreshBoards()
                        }
                    },
                    onMoveUp = {
                        scope.launch {
                            boardManager.moveBoard(board.id, -1)
                            refreshBoards()
                        }
                    },
                    onMoveDown = {
                        scope.launch {
                            boardManager.moveBoard(board.id, +1)
                            refreshBoards()
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
        ) {
            Text(text = stringResource(R.string.custom_rpc_board_add))
        }

        if (showEditorDialog) {
            CustomRpcBoardEditorDialog(
                board = editingBoard,
                onDismiss = {
                    showEditorDialog = false
                    editingBoard = null
                },
                onSave = { board ->
                    scope.launch {
                        if (editingBoard == null) {
                            boardManager.addBoard(board)
                        } else {
                            boardManager.updateBoard(board)
                        }
                        refreshBoards()
                    }
                    showEditorDialog = false
                    editingBoard = null
                },
            )
        }
    }
}

@Composable
private fun BoardCard(
    board: CustomRpcBoardItem,
    isActive: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                    text = board.label.ifBlank {
                        board.name.ifBlank { stringResource(R.string.custom_rpc_board_label) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(
                modifier = Modifier.width(116.dp),
            ) {
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onEdit) {
                        Text(text = stringResource(R.string.custom_rpc_board_edit))
                    }
                    TextButton(onClick = onDelete) {
                        Text(text = stringResource(R.string.custom_rpc_board_delete))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onMoveUp) {
                        Text(text = "▲")
                    }
                    TextButton(onClick = onMoveDown) {
                        Text(text = "▼")
                    }
                }
            }
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
    var activityTypeError by remember { mutableStateOf("") }
    val invalidTypeError = stringResource(R.string.custom_rpc_board_activity_type_invalid)
    val notRichPresenceError = stringResource(R.string.custom_rpc_board_activity_type_not_rich_presence)

    val id by remember(board) { mutableStateOf(board?.id ?: UUID.randomUUID().toString()) }
    var label by remember(board) { mutableStateOf(board?.label ?: "") }
    var applicationId by remember(board) { mutableStateOf(board?.applicationId ?: "") }
    var activityType by remember(board) { mutableStateOf(board?.activityType?.toString() ?: "0") }
    var name by remember(board) { mutableStateOf(board?.name ?: "") }
    var details by remember(board) { mutableStateOf(board?.details ?: "") }
    var state by remember(board) { mutableStateOf(board?.state ?: "") }
    var largeImage by remember(board) { mutableStateOf(board?.largeImage ?: "") }
    var smallImage by remember(board) { mutableStateOf(board?.smallImage ?: "") }
    var largeText by remember(board) { mutableStateOf(board?.largeText ?: "") }
    var smallText by remember(board) { mutableStateOf(board?.smallText ?: "") }
    var button1Label by remember(board) { mutableStateOf(board?.button1Label ?: "") }
    var button1Url by remember(board) { mutableStateOf(board?.button1Url ?: "") }
    var button2Label by remember(board) { mutableStateOf(board?.button2Label ?: "") }
    var button2Url by remember(board) { mutableStateOf(board?.button2Url ?: "") }
    var timestampMode by remember(board) { mutableStateOf(board?.timestampMode ?: "NONE") }
    var customStartMs by remember(board) { mutableStateOf(board?.customStartMs?.toString() ?: "") }

    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (board == null) {
                    stringResource(R.string.custom_rpc_board_add)
                } else {
                    stringResource(R.string.custom_rpc_board_edit)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_label), value = label, onValueChange = { label = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_application_id), value = applicationId, onValueChange = { applicationId = it })
                TextFieldRow(
                    label = stringResource(R.string.custom_rpc_board_activity_type),
                    value = activityType,
                    onValueChange = {
                        activityType = it
                        activityTypeError = ""
                    }
                )
                if (activityTypeError.isNotEmpty()) {
                    Text(
                        text = activityTypeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_name), value = name, onValueChange = { name = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_details), value = details, onValueChange = { details = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_state), value = state, onValueChange = { state = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_large_image), value = largeImage, onValueChange = { largeImage = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_small_image), value = smallImage, onValueChange = { smallImage = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_large_text), value = largeText, onValueChange = { largeText = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_small_text), value = smallText, onValueChange = { smallText = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_button1_label), value = button1Label, onValueChange = { button1Label = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_button1_url), value = button1Url, onValueChange = { button1Url = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_button2_label), value = button2Label, onValueChange = { button2Label = it })
                TextFieldRow(label = stringResource(R.string.custom_rpc_board_button2_url), value = button2Url, onValueChange = { button2Url = it })
                TextFieldRow(label = "Timestamp Mode", value = timestampMode, onValueChange = { timestampMode = it })
                TextFieldRow(label = "Custom Start (ms)", value = customStartMs, onValueChange = { customStartMs = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = activityType.toIntOrNull()
                val valid = when (parsed) {
                    null -> {
                        activityTypeError = invalidTypeError
                        false
                    }
                    0, 1, 2, 3, 5 -> true
                    4, 6 -> {
                        activityTypeError = notRichPresenceError
                        false
                    }
                    else -> {
                        activityTypeError = invalidTypeError
                        false
                    }
                }
                if (!valid) return@TextButton
                activityTypeError = ""
                onSave(
                    CustomRpcBoardItem(
                        id = id,
                        label = label,
                        applicationId = applicationId,
                        activityType = parsed ?: 0,
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

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}