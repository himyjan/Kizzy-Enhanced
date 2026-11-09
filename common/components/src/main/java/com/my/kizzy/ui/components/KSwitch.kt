/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KSwitch.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun KSwitch(
    modifier: Modifier = Modifier,
    checked: Boolean,
    enable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme

    // Smooth color and position animations with labels for Layout Inspector debugging
    val backgroundColor by animateColorAsState(
        targetValue = if (checked) colorScheme.primary else colorScheme.surfaceVariant,
        label = "SwitchTrackColor"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colorScheme.onPrimary else colorScheme.outline,
        label = "SwitchThumbColor"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 32.dp else 4.dp,
        label = "SwitchThumbOffset"
    )

    Surface(
        modifier = modifier
            .requiredSize(56.dp, 28.dp)
            .alpha(if (enable) 1f else 0.5f),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .clickable(enabled = enable && onClick != null) {
                    onClick?.invoke()
                }
        ) {
            Surface(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = thumbX),
                shape = CircleShape,
                color = thumbColor
            ) {}
        }
    }
}

@Preview
@Composable
fun KSwitchPreview() {
    var switchState by remember { mutableStateOf(true) }
    KSwitch(checked = switchState, enable = switchState, onClick = {
        switchState = !switchState
    })
}
