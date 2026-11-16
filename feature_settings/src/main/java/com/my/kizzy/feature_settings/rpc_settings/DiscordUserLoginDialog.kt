package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val LOGIN_URL = "https://discord.com/login"

@Composable
fun DiscordUserLoginDialog(
    onDismiss: () -> Unit,
    onToken: (String) -> Unit,
) {
    var manualToken by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Login with Discord",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            val intent = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setEphemeralBrowsingEnabled(true)
                                .build()
                            intent.launchUrl(activity, Uri.parse(LOGIN_URL))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Ephemeral Login Browser")
                }

                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Paste your account token manually") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = { 
                        if (manualToken.isNotBlank()) {
                            onToken(manualToken.trim())
                        }
                    },
                    enabled = manualToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use this token")
                }
            }
        }
    }
}