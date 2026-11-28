/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Features.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 */

package com.my.kizzy.feature_home.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.my.kizzy.preference.RpcInstance
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.KSwitch

/**
 * Renders a vertical list of [HomeFeature] cards, an "Add RPC" action card,
 * and a dialog for creating new RPC instance clones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Features(
    homeItems: List<HomeFeature> = emptyList(),
    instances: List<RpcInstance> = emptyList(),
    onValueUpdate: (Int) -> Unit = {},
    onAddInstance: (RpcInstance) -> Unit = {},
    onDeleteInstance: (String) -> Unit = {},
    onReorder: (List<RpcInstance>) -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    var showAddRpcDialog by remember { mutableStateOf(false) }
    var selectedRpcType by remember { mutableStateOf<String?>(null) }

    val mediaRpcLabel = stringResource(R.string.main_mediaRpc)
    val manualRpcLabel = stringResource(R.string.main_ManualRpc)
    val customRpcLabel = stringResource(R.string.main_customRpc)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        homeItems.forEachIndexed { idx, item ->
            val isBuiltIn = !item.isDeletable

            val canMoveUp = !isBuiltIn && idx > 0 && homeItems[idx - 1].isDeletable
            val canMoveDown = !isBuiltIn && idx < homeItems.lastIndex - 1 && homeItems[idx + 1].isDeletable

            val moveUp: () -> Unit = {
                val newInstances = instances.toMutableList()
                val instanceIdx = newInstances.indexOfFirst { it.id == item.instanceId }
                if (instanceIdx > 0) {
                    val moved = newInstances.removeAt(instanceIdx)
                    newInstances.add(instanceIdx - 1, moved)
                    onReorder(newInstances)
                }
            }

            val moveDown: () -> Unit = {
                val newInstances = instances.toMutableList()
                val instanceIdx = newInstances.indexOfFirst { it.id == item.instanceId }
                if (instanceIdx >= 0 && instanceIdx < newInstances.lastIndex) {
                    val moved = newInstances.removeAt(instanceIdx)
                    newInstances.add(instanceIdx + 1, moved)
                    onReorder(newInstances)
                }
            }

            val deleteAction: () -> Unit = {
                item.onDelete()
                onDeleteInstance(item.instanceId)
            }

            if (item.tooltipText.isNotBlank()) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above
                    ),
                    state = rememberTooltipState(),
                    tooltip = {
                        RichTooltip(
                            title = {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                            },
                            text = { Text(item.tooltipText) },
                            action = {
                                if (item.featureDocsLink.isNotBlank()) {
                                    TextButton(onClick = { uriHandler.openUri(item.featureDocsLink) }) {
                                        Text(text = stringResource(R.string.learn_more))
                                    }
                                }
                            },
                        )
                    },
                ) {
                    FeatureCard(
                        feature = item,
                        onToggle = {
                            item.onCheckedChange(!item.isChecked)
                            onValueUpdate(idx)
                        },
                        onDelete = deleteAction,
                        isDeletable = item.isDeletable,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = moveUp,
                        onMoveDown = moveDown,
                    )
                }
            } else {
                FeatureCard(
                    feature = item,
                    onToggle = {
                        item.onCheckedChange(!item.isChecked)
                        onValueUpdate(idx)
                    },
                    onDelete = deleteAction,
                    isDeletable = item.isDeletable,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = moveUp,
                    onMoveDown = moveDown,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            FeatureCard(
                feature = HomeFeature(
                    title = "Add RPC",
                    icon = R.drawable.ic_home_add_rpc,
                    route = null,
                    isChecked = false,
                    showSwitch = false,
                    isAddButton = true,
                    tooltipText = "Add a new RPC instance (Media, Manual, or Custom)",
                ),
                onToggle = {},
                onDelete = {},
                isDeletable = false,
                canMoveUp = false,
                canMoveDown = false,
                onMoveUp = {},
                onMoveDown = {},
                onClick = {
                    showAddRpcDialog = true
                },
            )
        }
    }

    if (showAddRpcDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddRpcDialog = false
                selectedRpcType = null
            },
            title = { Text(text = stringResource(R.string.add_rpc_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.add_rpc_select_type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RpcTypeRow("media_rpc", mediaRpcLabel, selectedRpcType) { selectedRpcType = it }
                    RpcTypeRow("manual_rpc", manualRpcLabel, selectedRpcType) { selectedRpcType = it }
                    RpcTypeRow("custom_rpc", customRpcLabel, selectedRpcType) { selectedRpcType = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val type = selectedRpcType ?: return@TextButton
                    showAddRpcDialog = false
                    selectedRpcType = null
                    val label = when (type) {
                        "media_rpc" -> mediaRpcLabel
                        "manual_rpc" -> manualRpcLabel
                        "custom_rpc" -> customRpcLabel
                        else -> type
                    }
                    onAddInstance(
                        RpcInstance(
                            id = "${type}_clone_${System.currentTimeMillis()}",
                            type = type,
                            label = label,
                        )
                    )
                }) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddRpcDialog = false
                    selectedRpcType = null
                }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RpcTypeRow(
    type: String,
    label: String,
    selectedRpcType: String?,
    onSelect: (String) -> Unit,
) {
    val isSelected = selectedRpcType == type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(type) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isSelected) {
            Text(text = "✓", color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun FeatureCard(
    feature: HomeFeature,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    isDeletable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit = {},
) {
    val brush = if (feature.isChecked) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(brush = brush)
            .clickable {
                if (feature.isAddButton) {
                    onClick()
                } else {
                    feature.route?.let { feature.onClick(it) }
                }
            }
            .padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Icon(
            painter = painterResource(id = feature.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W500),
            color = if (feature.isChecked) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        if (feature.showSwitch) {
            Spacer(modifier = Modifier.width(12.dp))
            KSwitch(
                checked = feature.isChecked,
                modifier = Modifier.padding(start = 12.dp),
                onClick = onToggle
            )
        }
        if (isDeletable && !feature.isAddButton) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canMoveUp) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(text = "\u2191")
                    }
                }
                if (canMoveDown) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(text = "\u2193")
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Text(text = "\u2716", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}