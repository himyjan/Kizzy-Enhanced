package com.my.kizzy.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.my.kizzy.resources.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

private val TEMPLATE_REGEX = Regex("\\{\\{[^{}]*\\}\\}")
private val WORD_REGEX = Regex("\\w+$")
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
    val textFieldState = rememberTextFieldState(initialText = value)

    // Keep the callback current for the snapshotFlow collector below: it is keyed on
    // textFieldState only, so a captured lambda would go stale and overwrite other
    // fields with values from an older composition.
    val onValueChangeState = rememberUpdatedState(onValueChange)

    var showSuggestions by remember { mutableStateOf(false) }
    var filteredPlaceholders by remember { mutableStateOf<List<Pair<String, ResourceId>>>(emptyList()) }
    var lastTemplateCursorPosition by rememberSaveable { mutableIntStateOf(-1) }
    var lastTemplateText by rememberSaveable { mutableStateOf("") }
    var internalIsFocused by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textFieldInteractionSource = remember { MutableInteractionSource() }

    fun refreshSuggestions() {
        if (!internalIsFocused || !enabled || completionList.isEmpty()) {
            filteredPlaceholders = emptyList()
            showSuggestions = false
            return
        }
        val query = extractActiveQuery(textFieldState.text.toString(), textFieldState.selection.start)
        val matches = if (query.isEmpty()) {
            completionList
        } else {
            completionList.filter { (placeholder, _) ->
                val key = extractTemplateKey(placeholder)
                placeholder.contains(query, ignoreCase = true) ||
                        key.contains(query, ignoreCase = true)
            }
        }
        filteredPlaceholders = matches
        showSuggestions = matches.isNotEmpty()
    }

    // Keep the internal state in sync with the externally-provided value.
    LaunchedEffect(value) {
        if (textFieldState.text != value) {
            textFieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }

    // Emit user/state edits to onValueChange and refresh suggestions.
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { updated ->
                onValueChangeState.value(updated)
                refreshSuggestions()
            }
    }

    val primaryColor = colorScheme.primary
    val containerColor = colorScheme.primaryContainer.copy(alpha = 0.3f)
    val surfaceColor = colorScheme.surface.copy(alpha = 0.98f)
    val itemColor = surfaceColor

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceColor)
                .border(BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f)), RoundedCornerShape(16.dp)),
        ) {
            TextField(
                state = textFieldState,
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
                        } else {
                            refreshSuggestions()
                        }
                    },
                enabled = enabled,
                interactionSource = textFieldInteractionSource,
                label = { Text(stringResource(id = label)) },
                placeholder = { Text(stringResource(R.string.type_to_insert_variable)) },
                keyboardOptions = keyboardOptions,
                trailingIcon = trailingIcon,
                isError = isError,
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            if (isError && errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .align(Alignment.BottomEnd),
                    textAlign = TextAlign.End,
                    color = colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (showSuggestions && filteredPlaceholders.isNotEmpty() && internalIsFocused && enabled) {
                val maxHeightPx = with(density) {
                    minOf(
                        MAX_COMPLETION_LIST_HEIGHT_DP.dp.roundToPx(),
                        (SINGLE_COMPLETION_ITEM_HEIGHT_DP * filteredPlaceholders.size).dp.roundToPx(),
                    )
                }

                Popup(
                    alignment = Alignment.TopCenter,
                    offset = IntOffset(0, -maxHeightPx),
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
                    onDismissRequest = { showSuggestions = false },
                ) {
                    Column {
                        AnimatedVisibility(visible = showSuggestions) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surfaceColor)
                                    .heightIn(max = with(density) { maxHeightPx.toDp() }),
                            ) {
                                itemsIndexed(
                                    items = filteredPlaceholders,
                                    key = { index: Int, item: Pair<String, ResourceId> -> "${item.first}#$index" },
                                ) { itemsIndex: Int, item: Pair<String, ResourceId> ->
                                    val (placeholder, resId) = item
                                    val lastIndex = filteredPlaceholders.lastIndex
                                    val itemInteractionSource = remember { MutableInteractionSource() }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(itemColor)
                                            .clickable(
                                                interactionSource = itemInteractionSource,
                                                indication = null,
                                                onClick = {
                                                    val currentText = textFieldState.text.toString()
                                                    val currentSelectionStart = textFieldState.selection.start.coerceIn(0, currentText.length)
                                                    val textUpToCursor = currentText.take(currentSelectionStart)
                                                    val actualKey = extractTemplateKey(placeholder)

                                                    val newText = insertTemplateKey(
                                                        fullText = currentText,
                                                        textUpToCursor = textUpToCursor,
                                                        cursorPosition = currentSelectionStart,
                                                        actualKey = actualKey,
                                                    )
                                                    val newCursorPos = textUpToCursor.lastIndexOf("{{").let { templateStart ->
                                                        if (templateStart != -1 && !textUpToCursor.substring(templateStart).contains("}}")) {
                                                            val prefix = currentText.substring(0, templateStart)
                                                            prefix.length + actualKey.length + 4
                                                        } else {
                                                            val currentWord = getCurrentWord(textUpToCursor)
                                                            if (currentWord.isNotEmpty()) {
                                                                val wordStartPos = currentSelectionStart - currentWord.length
                                                                currentText.substring(0, wordStartPos).length + actualKey.length + 4
                                                            } else {
                                                                currentSelectionStart + actualKey.length + 4
                                                            }
                                                        }
                                                    }.coerceIn(0, newText.length)

                                                    textFieldState.edit {
                                                        replace(0, currentText.length, newText)
                                                        selection = TextRange(newCursorPos, newCursorPos)
                                                    }
                                                    lastTemplateCursorPosition = newCursorPos
                                                    lastTemplateText = newText
                                                    onValueChange(newText)
                                                    showSuggestions = false
                                                },
                                            )
                                            .padding(horizontal = 18.dp, vertical = 12.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        primaryColor,
                                                        shape = CircleShape,
                                                    ),
                                            )
                                            Text(
                                                text = placeholder,
                                                color = primaryColor,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                ),
                                                modifier = Modifier.padding(start = 12.dp),
                                            )
                                        }
                                        Text(
                                            text = stringResource(resId),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                            ),
                                            color = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                                        )
                                    }
                                    if (itemsIndex < lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            color = colorScheme.outline.copy(alpha = 0.08f),
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
}

private fun extractActiveQuery(fullText: String, cursorPosition: Int): String {
    val safePos = cursorPosition.coerceIn(0, fullText.length)
    val textUpToCursor = fullText.take(safePos)
    val lastOpen = textUpToCursor.lastIndexOf("{{")
    if (lastOpen != -1) {
        val partial = textUpToCursor.substring(lastOpen)
        if (!partial.contains("}}")) {
            return partial.removePrefix("{{").trim()
        }
    }
    return getCurrentWord(textUpToCursor)
}

private fun extractTemplateKey(placeholder: String): String {
    return if (placeholder.startsWith("{{") && placeholder.endsWith("}}") && placeholder.length >= 4) {
        placeholder.substring(2, placeholder.length - 2)
    } else {
        placeholder
    }
}

private fun insertTemplateKey(
    fullText: String,
    textUpToCursor: String,
    cursorPosition: Int,
    actualKey: String,
): String {
    val templateStart = textUpToCursor.lastIndexOf("{{")
    if (templateStart != -1 && !textUpToCursor.substring(templateStart).contains("}}")) {
        val prefix = fullText.substring(0, templateStart)
        val suffix = fullText.substring(cursorPosition)
        return "$prefix{{$actualKey}}$suffix"
    }

    val currentWord = getCurrentWord(textUpToCursor)
    if (currentWord.isNotEmpty()) {
        val wordStartPos = (cursorPosition - currentWord.length).coerceAtLeast(0)
        val prefix = fullText.substring(0, wordStartPos)
        val suffix = fullText.substring(cursorPosition)
        return "$prefix{{$actualKey}}$suffix"
    }

    val prefix = fullText.substring(0, cursorPosition)
    val suffix = fullText.substring(cursorPosition)
    return "$prefix{{$actualKey}}$suffix"
}

private fun getCurrentWord(text: String): String {
    return WORD_REGEX.find(text)?.value.orEmpty()
}
