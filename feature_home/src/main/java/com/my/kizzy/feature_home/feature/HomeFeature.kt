/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * HomeFeature.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  ******************************************************************
 *
 */

package com.my.kizzy.feature_home.feature

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class HomeFeature(
    val title: String,
    val route: String? = null,
    val isChecked: Boolean = false,
    val onClick: (String) -> Unit = {},
    val onCheckedChange: (Boolean) -> Unit = {},
    val showSwitch: Boolean = true,
    val shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    val tooltipText: String = "",
    val featureDocsLink: String = "",
    val instanceId: String = "",
    val isDeletable: Boolean = false,
    val onDelete: () -> Unit = {},
    val isAddButton: Boolean = false,
)
