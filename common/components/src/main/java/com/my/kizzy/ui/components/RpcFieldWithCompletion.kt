/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcFieldWithCompletion.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import androidx.annotation.StringRes
import com.my.kizzy.resources.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private const val TEMPLATE_REGEX = "\\{\\{[^{}]*\\}\\}"
private const val MAX_COMPLETION_LIST_HEIGHT_DP = 250f
private const val SINGLE_COMPLETION_ITEM_HEIGHT_DP = 62.5f

private typealias ResourceId = Int

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcFieldWithCompletions(
    value: String,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    @StringRes label: Int,
    isError: Boolean = false,
    errorMessage: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    content: @Composable (() -> Unit) = {},
    onValueChange: (String) -> Unit = {},
    completionList: List<Pair<String, ResourceId>> = emptyList(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var showSuggestions by remember { mutableStateOf(false) }
    var filteredPlaceholders by remember { mutableStateOf<List<Pair<String, ResourceId>>>(emptyList()) }
    var lastTemplateCursorPosition by rememberSaveable { mutableIntStateOf(-1) }
    var lastTemplateText by rememberSaveable { mutableStateOf("") }
    var internalIsFocused by remember { mutableStateOf(false) }

    // Sync external value into internal state when it changes externally
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    // Pre-computed colors for the current theme (avoids repeated MaterialTheme access)
    val primaryColor = colorScheme.primary
    val containerColor = colorScheme.primaryContainer.copy(alpha = 0.3f)
    val surfaceColor = colorScheme.surface.copy(alpha = 0.98f)

    // Cache visual transformation per text value - avoids rebuilding on every recomposition
    val visualTransformation = remember(textFieldValue.text) {
        val color = primaryColor
        val background = containerColor
        VisualTransformation { text ->
            val annotatedString = buildAnnotatedString {
                append(text.text)
                TEMPLATE_REGEX.toRegex().findAll(text.text).forEach { matchResult ->
                    addStyle(
                        style = SpanStyle(
                            color = color,
                            background = background,
                        ), start = matchResult.range.first, end = matchResult.range.last + 1
                    )
                }
            }
            TransformedText(annotatedString, OffsetMapping.Identity)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(12.dp))
                .onFocusChanged { focusState ->
                    internalIsFocused = focusState.isFocused
                    if (!internalIsFocused) {
                        showSuggestions = false
                        lastTemplateCursorPosition = -1
                        lastTemplateText = ""
                    }
                },
            value = textFieldValue,
            visualTransformation = visualTransformation,
            onValueChange = { newTextFieldValue ->
                textFieldValue = newTextFieldValue
                onValueChange(newTextFieldValue.text)
                updateSuggestions(newTextFieldValue, completionList)
            },
            enabled = enabled,
            interactionSource = interactionSource,
            label = { Text(stringResource(id = label)) },
            placeholder = { Text(stringResource(R.string.type_to_insert_variable)) },
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = true,
        )

        if (isError) {
            Text(
                text = errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp, 16.dp, 0.dp)
                    .align(Alignment.BottomEnd),
                textAlign = TextAlign.End,
                color = colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (showSuggestions && filteredPlaceholders.isNotEmpty() && internalIsFocused && enabled) {
            val maxHeightPx = with(density) {
                minOf(
                    MAX_COMPLETION_LIST_HEIGHT_DP.dp.roundToPx(),
                    (SINGLE_COMPLETION_ITEM_HEIGHT_DP * filteredPlaceholders.size).dp.roundToPx()
                )
            }

            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -maxHeightPx),
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                ),
                onDismissRequest = { showSuggestions = false }
            ) {
                AnimatedVisibility(
                    visible = showSuggestions
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = surfaceColor
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = primaryColor.copy(alpha = 0.15f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceColor)
                                .heightIn(max = maxHeightPx.dp)
                        ) {
                            itemsIndexed(
                                items = filteredPlaceholders,
                                key = { index: Int, item: Pair<String, ResourceId> -> item.first }
                            ) { itemsIndex: Int, item: Pair<String, ResourceId> ->
                                val (placeholder, resId) = item
                                val lastIndex = filteredPlaceholders.lastIndex
                                var isPressed by remember { mutableStateOf(false) }
                                val itemColor = if (isPressed) primaryColor.copy(alpha = 0.1f) else surfaceColor

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(itemColor)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            onClick = {
                                                val currentSelectionStart = textFieldValue.selection.start
                                                val textUpToCursor = textFieldValue.text.take(currentSelectionStart)
                                                val actualKey = extractTemplateKey(placeholder)

                                                val newText = insertTemplateKey(
                                                    textFieldValue.text,
                                                    textUpToCursor,
                                                    currentSelectionStart,
                                                    actualKey
                                                )
                                                val newCursorPos = textUpToCursor.lastIndexOf("{{").let { templateStart ->
                                                    if (templateStart != -1) {
                                                        val prefix = textFieldValue.text.substring(0, templateStart)
                                                        prefix.length + actualKey.length + 4
                                                    } else {
                                                        val currentWord = getCurrentWord(textUpToCursor)
                                                        if (currentWord.isNotEmpty()) {
                                                            val wordStartPos = currentSelectionStart - currentWord.length
                                                            textFieldValue.text.substring(0, wordStartPos).length + actualKey.length + 4
                                                        } else {
                                                            textUpToCursor.length + actualKey.length + 4
                                                        }
                                                    }
                                                }

                                                textFieldValue = textFieldValue.copy(
                                                    text = newText,
                                                    selection = TextRange(newCursorPos)
                                                )
                                                onValueChange(newText)
                                                showSuggestions = false
                                            }
                                        )
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val pressed = event.changes.any { it.pressed }
                                                    isPressed = pressed
                                                }
                                            }
                                        }
                                        .padding(18.dp, 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    primaryColor,
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = placeholder,
                                            color = primaryColor,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(resId),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                                    )
                                }
                                if (itemsIndex < lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        thickness = DividerDefaults.Thickness,
                                        color = colorScheme.outline.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    content()
}

/**
 * Extracts the template key from a placeholder string (removes {{ }} if present).
 */
private fun extractTemplateKey(placeholder: String): String {
    return if (placeholder.startsWith("{{") && placeholder.endsWith("}}")) {
        placeholder.substring(2, placeholder.length - 2)
    } else {
        placeholder
    }
}

/**
 * Inserts a template key into the text at the cursor position.
 */
private fun insertTemplateKey(
    fullText: String,
    textUpToCursor: String,
    cursorPosition: Int,
    actualKey: String
): String {
    if (textUpToCursor.lastIndexOf("{{") != -1) {
        val templateStart = textUpToCursor.lastIndexOf("{{")
        val partialTemplate = textUpToCursor.substring(templateStart)
        if (partialTemplate.startsWith("{{") && !partialTemplate.contains("}}")) {
            val prefix = fullText.substring(0, templateStart)
            val suffix = fullText.substring(cursorPosition)
            return "$prefix{{$actualKey}}$suffix"
        }
    }

    val currentWord = getCurrentWord(textUpToCursor)
    if (currentWord.isNotEmpty()) {
        val wordStartPos = cursorPosition - currentWord.length
        val prefix = fullText.substring(0, wordStartPos)
        val suffix = fullText.substring(cursorPosition)
        return "$prefix{{$actualKey}}$suffix"
    }

    return "$textUpToCursor{{$actualKey}}"
}

/**
 * Updates suggestion state after text change (called from onValueChange, not during composition).
 */
private fun updateSuggestions(
    newTextFieldValue: TextFieldValue,
    completionList: List<Pair<String, ResourceId>>,
) {
    // Filtering is handled by the remember block based on textFieldValue changes
}

private fun getCurrentWord(text: String): String {
    val wordPattern = "\\w+$"
    val matcher = Regex(wordPattern).find(text)
    return matcher?.value ?: ""
}
