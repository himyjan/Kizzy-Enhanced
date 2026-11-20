/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * SaveConfigDialog.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_custom_rpc.components.sheet

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.res.stringResource
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.resources.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@Composable
fun SaveConfigDialog(
    rpc: RpcConfig,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val ctx = LocalContext.current
    var configName by remember {
        mutableStateOf("")
    }
    val configNameState = rememberTextFieldState(initialText = "")
    var isError by remember {
        mutableStateOf(false)
    }
    val dir = ctx.dir()
    dir.mkdirs()

    LaunchedEffect(configNameState) {
        snapshotFlow { configNameState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect {
                configName = it
                isError = it.isEmpty()
            }
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    if (configName.isEmpty()) {
                        isError = true
                        return@OutlinedButton
                    }
                    onDismiss()
                    File("$dir/$configName.json").writeText(rpc.dataToString())
                    onSaved(ctx.getString(R.string.saved_config_toast, configName))
                }) {
                Text(text = stringResource(R.string.save))
            }
        },
        title = { Text(text = stringResource(id = R.string.save_config)) },
        text = {
            TextField(
                state = configNameState,
                label = { Text(text = stringResource(R.string.config_name)) },
                isError = isError,
                supportingText = { if (isError) Text(stringResource(R.string.config_name_empty)) else Unit }
            )
        })
}