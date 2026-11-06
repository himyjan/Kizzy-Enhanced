/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KTextField.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */

package com.my.kizzy.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.my.kizzy.ui.components.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KTextField(
    readOnly: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    errorMessage: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)  // ???
        focusRequester.requestFocus()
    }

    TextField(
        modifier = Modifier.focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        maxLines = 1,
        enabled = !readOnly,
        value = value,
        onValueChange = {
            if (!readOnly) onValueChange(it)
        },
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        isError = errorMessage.isNotEmpty(),
        singleLine = true,
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = {
                    if (!readOnly) onValueChange("")
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "clear",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            } else {
                IconButton(onClick = {
                    if (!readOnly) {
                        val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        onValueChange(clipText)
                    }
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_content_paste),
                        contentDescription = "paste",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
