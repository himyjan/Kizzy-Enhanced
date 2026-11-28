package com.my.kizzy.feature_custom_rpc

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.SwitchBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Dialog that creates a new Console RPC preset or edits an existing one.
 *
 * Every value a preset stores is editable here: the name, the Discord platform,
 * the activity type, the status and the two optional extras (timestamps and
 * cover art). The dialog is also the place to delete a user preset.
 *
 * @param initial preset to edit, or a blank one from [ConsolePresetStore.newPreset]
 * @param onDelete null for built-in presets, which cannot be removed
 * @param pickLargeImage launcher for picking the large cover art image
 * @param pickSmallImage launcher for picking the small cover art image
 */
@Composable
fun ConsolePresetEditorDialog(
    initial: ConsolePreset,
    onDismiss: () -> Unit,
    onSave: (ConsolePreset) -> Unit,
    onDelete: (() -> Unit)? = null,
    pickLargeImage: ((PickVisualMediaRequest) -> Unit)? = null,
    pickSmallImage: ((PickVisualMediaRequest) -> Unit)? = null,
) {
    var name by remember(initial.id, initial.name) { mutableStateOf(initial.name) }
    val nameState = rememberTextFieldState(initialText = initial.name)
    var platform by remember(initial.id, initial.platform) { mutableStateOf(initial.platform) }
    var activityType by remember(initial.id, initial.activityType) { mutableStateOf(initial.activityType) }
    var status by remember(initial.id, initial.status) { mutableStateOf(initial.status) }
    var timestamps by remember(initial.id, initial.timestamps) { mutableStateOf(initial.timestamps) }
    var coverArt by remember(initial.id, initial.coverArt) { mutableStateOf(initial.coverArt) }
    var largeCoverArtUrl by remember(initial.id, initial.largeCoverArtUrl) { mutableStateOf(initial.largeCoverArtUrl) }
    val largeCoverArtUrlState = rememberTextFieldState(initialText = initial.largeCoverArtUrl)
    var smallCoverArtUrl by remember(initial.id, initial.smallCoverArtUrl) { mutableStateOf(initial.smallCoverArtUrl) }
    val smallCoverArtUrlState = rememberTextFieldState(initialText = initial.smallCoverArtUrl)
    var confirmDelete by remember(initial.id) { mutableStateOf(false) }

    LaunchedEffect(nameState) {
        snapshotFlow { nameState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { name = it }
    }

    LaunchedEffect(largeCoverArtUrlState) {
        snapshotFlow { largeCoverArtUrlState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { largeCoverArtUrl = it }
    }

    LaunchedEffect(smallCoverArtUrlState) {
        snapshotFlow { smallCoverArtUrlState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { smallCoverArtUrl = it }
    }

    val platformLabel = Constants.ACTIVITY_PLATFORMS.entries
        .firstOrNull { it.value == platform }?.key ?: platform
    val activityTypeLabel = Constants.ACTIVITY_TYPE.entries
        .firstOrNull { it.value.toString() == activityType }?.key ?: activityType
    val statusLabel = stringResource(
        Constants.ACTIVITY_STATUS.entries
            .firstOrNull { it.value == status }?.key ?: R.string.status_online
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial.builtIn) R.string.console_preset_edit
                    else if (initial.name.isBlank()) R.string.console_preset_new
                    else R.string.console_preset_edit
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    state = nameState,
                    label = { Text(stringResource(R.string.console_preset_name)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                PresetDropdown(
                    label = stringResource(R.string.console_platform),
                    value = platformLabel,
                    options = Constants.ACTIVITY_PLATFORMS.entries.map { it.key to it.value },
                    onSelect = { platform = it }
                )
                PresetDropdown(
                    label = stringResource(R.string.console_activity_type),
                    value = activityTypeLabel,
                    options = Constants.ACTIVITY_TYPE.entries.map { it.key to it.value.toString() },
                    onSelect = { activityType = it }
                )
                PresetDropdown(
                    label = stringResource(R.string.console_status),
                    value = statusLabel,
                    options = Constants.ACTIVITY_STATUS.entries.map { stringResource(it.key) to it.value },
                    onSelect = { status = it }
                )
                SwitchBar(
                    title = stringResource(R.string.console_enable_timestamps),
                    isChecked = timestamps,
                ) { timestamps = !timestamps }
                SwitchBar(
                    title = stringResource(R.string.console_show_cover_art),
                    isChecked = coverArt,
                ) { coverArt = !coverArt }
                OutlinedTextField(
                    state = largeCoverArtUrlState,
                    label = { Text(stringResource(R.string.console_large_cover_art)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, top = 8.dp),
                    trailingIcon = {
                        pickLargeImage?.let { launcher ->
                            IconButton(
                                onClick = {
                                    launcher(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*")
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Pick large cover art"
                                )
                            }
                        }
                    }
                )
                OutlinedTextField(
                    state = smallCoverArtUrlState,
                    label = { Text(stringResource(R.string.console_small_cover_art)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    trailingIcon = {
                        pickSmallImage?.let { launcher ->
                            IconButton(
                                onClick = {
                                    launcher(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*")
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Pick small cover art"
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            platform = platform,
                            activityType = activityType,
                            status = status,
                            timestamps = timestamps,
                            coverArt = coverArt,
                            largeCoverArtUrl = largeCoverArtUrl,
                            smallCoverArtUrl = smallCoverArtUrl,
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(text = stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = stringResource(R.string.console_preset_delete_title)) },
            text = { Text(text = stringResource(R.string.console_preset_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * A labelled button that opens a dropdown with [options] (label to value).
 */
@Composable
private fun PresetDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label: $value",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (optionLabel, optionValue) ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel) },
                    onClick = {
                        onSelect(optionValue)
                        expanded = false
                    }
                )
            }
        }
    }
}