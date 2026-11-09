/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * SwitchBar.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.ui.theme.getColorScheme

@Composable
fun SwitchBar(
    title: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colorScheme = getColorScheme(darkTheme = false)
    val containerColor = if (enabled) colorScheme.primaryContainer else colorScheme.surfaceVariant
    val textColor = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .toggleable(enabled) {
                onClick()
            }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = textColor,
            overflow = TextOverflow.Ellipsis
        )
        KSwitch(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp),
            checked = isChecked,
            enable = enabled
        ) {
            if (enabled) onClick()
        }
    }
}

@Preview
@Composable
fun PreviewSwitchBar() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black), contentAlignment = Alignment.Center
    ) {
        var state by remember { mutableStateOf(false) }
        SwitchBar(title = "SwitchBar", isChecked = state) {
            state = !state
        }
    }
}
