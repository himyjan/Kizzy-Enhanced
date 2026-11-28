package com.my.kizzy.feature_settings.rpc_settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.resources.R

private class TrimmedSingleLineTokenTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val raw = asCharSequence().toString()
        val cleaned = raw.replace("\n", "").replace("\r", "")
        if (cleaned != raw) {
            replace(0, length, cleaned)
        }
    }
}

@Composable
fun DiscordUserLoginDialog(
    token: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val tokenState = rememberTextFieldState(initialText = token)

    LaunchedEffect(token) {
        if (token != tokenState.text.toString()) {
            tokenState.setTextAndPlaceCursorAtEnd(token)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.login_with_discord)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    state = tokenState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Paste your account token manually") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = TrimmedSingleLineTokenTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tokenState.text.toString().trim()) }) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}