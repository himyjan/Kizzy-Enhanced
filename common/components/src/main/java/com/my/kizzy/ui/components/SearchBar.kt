/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * SearchBar.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextFieldState
import androidx.compose.foundation.text.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.resources.R

/** Height of the search field.
 *
 * The height is fixed on purpose: the field is placed inside containers that hand
 * out unbounded height constraints (a `TopAppBar` action slot, a `Column`, ...) and a
 * field that is free to grow would stretch those containers instead of staying a
 * single compact row.
 */
private val SearchBarHeight = 48.dp

private val SearchBarShape = RoundedCornerShape(24.dp)

/**
 * A compact, single line search field.
 *
 * It is meant to be dropped into a full width slot (the title of a top app bar or a
 * dedicated row below it). The field never takes more than [SearchBarHeight] of
 * vertical space and requests the focus (and therefore the keyboard) as soon as it
 * becomes visible.
 *
 * @param text current query
 * @param placeholder hint shown while the query is empty
 * @param onClose invoked when the field is dismissed without any query
 * @param autoFocus whether the field should grab the focus when it is displayed
 * @param onTextChanged invoked whenever the query changes
 * @param modifier modifier applied to the field, e.g. to add horizontal padding
 */
@Composable
fun SearchBar(
    text: String = "",
    placeholder: String = "",
    onClose: () -> Unit = {},
    autoFocus: Boolean = true,
    onTextChanged: ((String) -> Unit),
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldState = rememberTextFieldState(text)

    // Sync external text changes into the field state
    LaunchedEffect(text) {
        textFieldState.updateText(text)
    }

    // The focus has to be requested *after* the field was attached to the
    // composition. Asking for it during the very first composition is silently
    // dropped, which left the user with a focused-looking field that never opened
    // the keyboard.
    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchBarHeight)
            .clip(SearchBarShape)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 6.dp)
        ) {
            BasicTextField(
                state = textFieldState,
                onValueChange = { newText ->
                    textFieldState.updateText(newText)
                    onTextChanged(newText)
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colorScheme.onSurface
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box {
                        if (textFieldState.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        IconButton(
            onClick = {
                if (textFieldState.text.isNotEmpty()) textFieldState.updateText("")
                else onClose()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(
                    id = if (textFieldState.text.isNotEmpty()) R.string.clear else R.string.cancel
                ),
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
fun Search() {
    SearchBar(
        text = "test",
        placeholder = "Search…",
        onTextChanged = {}
    )
}
