package com.my.kizzy.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import com.my.kizzy.resources.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

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
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    val focusRequester = remember { FocusRequester() }
    val textFieldState = rememberTextFieldState(initialText = value)

    LaunchedEffect(value) {
        if (textFieldState.text != value) {
            textFieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { updated ->
                if (!readOnly) onValueChange(updated)
            }
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    TextField(
        state = textFieldState,
        modifier = Modifier.focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = !readOnly,
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        isError = errorMessage.isNotEmpty(),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = {
                    if (!readOnly) onValueChange("")
                }) {
                    Icon(
                        imageVector = vectorResource(R.drawable.ic_close),
                        contentDescription = "clear",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            } else {
                IconButton(onClick = {
                    if (!readOnly) {
                        val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
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