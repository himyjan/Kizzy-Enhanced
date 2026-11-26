/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Features.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  ******************************************************************
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
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
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.BUILT_IN_INSTANCES
import com.my.kizzy.preference.RpcInstance
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.KSwitch

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (i in homeItems.indices) {
            val item = homeItems[i]
            val instance = instances.firstOrNull { it.id == item.instanceId }
            val isBuiltIn = !item.isDeletable
            val canMoveUp = !isBuiltIn && i > 0 && homeItems[i - 1].isDeletable
            val canMoveDown = !isBuiltIn && i < homeItems.lastIndex - 1 && homeItems[i + 1].isDeletable

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
                                TextButton(onClick = { uriHandler.openUri(item.featureDocsLink) }) {
                                    Text(text = stringResource(R.string.learn_more))
                                }
                            },
                        )
                    },
                ) {
                    FeatureCard(
                        feature = item,
                        instance = instance,
                        onToggle = {
                            item.onCheckedChange(!item.isChecked)
                            onValueUpdate(i)
                        },
                        onDelete = { onDeleteInstance(item.instanceId) },
                        isDeletable = item.isDeletable,
                        canMoveUp = canMoveUp,
                        canMoveDown = canMoveDown,
                        onMoveUp = {
                            val newInstances = instances.toMutableList()
                            val idx = newInstances.indexOfFirst { it.id == item.instanceId }
                            if (idx > 0) {
                                val it = newInstances.removeAt(idx)
                                newInstances.add(idx - 1, it)
                                onReorder(newInstances)
                            }
                        },
                        onMoveDown = {
                            val newInstances = instances.toMutableList()
                            val idx = newInstances.indexOfFirst { it.id == item.instanceId }
                            if (idx < newInstances.lastIndex) {
                                val it = newInstances.removeAt(idx)
                                newInstances.add(idx + 1, it)
                                onReorder(newInstances)
                            }
                        },
                    )
                }
            } else {
                FeatureCard(
                    feature = item,
                    instance = instance,
                    onToggle = {
                        item.onCheckedChange(!item.isChecked)
                        onValueUpdate(i)
                    },
                    onDelete = { onDeleteInstance(item.instanceId) },
                    isDeletable = item.isDeletable,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = {
                        val newInstances = instances.toMutableList()
                        val idx = newInstances.indexOfFirst { it.id == item.instanceId }
                        if (idx > 0) {
                            val it = newInstances.removeAt(idx)
                            newInstances.add(idx - 1, it)
                            onReorder(newInstances)
                        }
                    },
                    onMoveDown = {
                        val newInstances = instances.toMutableList()
                        val idx = newInstances.indexOfFirst { it.id == item.instanceId }
                        if (idx < newInstances.lastIndex) {
                            val it = newInstances.removeAt(idx)
                            newInstances.add(idx + 1, it)
                            onReorder(newInstances)
                        }
                    },
                )
            }
        }

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
            instance = null,
            onToggle = {},
            onDelete = {},
            isDeletable = false,
            canMoveUp = false,
            canMoveDown = false,
            onMoveUp = {},
            onMoveDown = {},
            onClick = {
                onAddInstance(
                    RpcInstance(
                        id = "media_rpc_clone_${System.currentTimeMillis()}",
                        type = "media_rpc",
                        label = "Media RPC Clone",
                    )
                )
            },
        )
    }
}

@Composable
private fun FeatureCard(
    feature: HomeFeature,
    instance: RpcInstance?,
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
                MaterialTheme.colorScheme.primaryContainer.copy(0.8f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondaryContainer.copy(0.8f),
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
        androidx.compose.material3.Icon(
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
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                if (canMoveUp) {
                    androidx.compose.material3.IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(32.dp),
                    ) {
                        androidx.compose.material3.Text(text = "\u2191")
                    }
                }
                if (canMoveDown) {
                    androidx.compose.material3.IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(32.dp),
                    ) {
                        androidx.compose.material3.Text(text = "\u2193")
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.material3.IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                androidx.compose.material3.Text(text = "\u2716", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
