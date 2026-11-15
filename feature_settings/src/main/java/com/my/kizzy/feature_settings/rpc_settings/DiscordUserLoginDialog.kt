package com.my.kizzy.feature_settings.rpc_settings

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Discord OAuth2 tokens returned by the authorization-code flow are **bearer** tokens
 * (`Authorization: Bearer ...`). The Discord gateway only accepts bot tokens or real
 * user tokens (`user_id.timestamp.hmac`), so a bearer token always fails IDENTIFY with
 * close code 4004.
 *
 * This dialog therefore logs the user in through Discord's web login and reads the
 * account token from the page storage, which is the same token the gateway accepts.
 */
private const val TOKEN_EXTRACT_JS = """
(function() {
  if (window.__kizzyTokenWatch) return;
  window.__kizzyTokenWatch = true;
  function clean(raw) {
    if (!raw) return null;
    var t = String(raw).trim();
    if (t.length > 1 && t.charAt(0) === '"' && t.charAt(t.length - 1) === '"') {
      t = t.substring(1, t.length - 1);
    }
    // Real Discord account tokens have three dot-separated parts.
    return t.split('.').length >= 3 ? t : null;
  }
  function scan() {
    try {
      var direct = clean(window.localStorage.getItem('token'));
      if (direct) { KizzyBridge.onToken(direct); return; }
    } catch (e) {}
    try {
      var frame = document.createElement('iframe');
      frame.style.display = 'none';
      document.body.appendChild(frame);
      var inner = clean(frame.contentWindow.localStorage.getItem('token'));
      document.body.removeChild(frame);
      if (inner) { KizzyBridge.onToken(inner); }
    } catch (e) {}
  }
  scan();
  setInterval(scan, 1500);
})();
"""

private const val LOGIN_URL = "https://discord.com/login"
private const val MOTOROLA = "motorola"
private const val SAMSUNG_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordUserLoginDialog(
    onDismiss: () -> Unit,
    onToken: (String) -> Unit,
) {
    var manualToken by remember { mutableStateOf("") }
    var delivered by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun deliver(raw: String) {
        val token = raw.trim()
        if (token.isEmpty() || delivered) return
        delivered = true
        mainHandler.post { onToken(token) }
    }

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
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Login with Discord",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                Text(
                    text = "Log in to your own Discord account. Kizzy reads your account token from the page and uses it for RPC. The OAuth2 consent screen cannot be used here because Discord rejects OAuth bearer tokens on the gateway.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            if (Build.MANUFACTURER.equals(MOTOROLA, ignoreCase = true)) {
                                settings.userAgentString = SAMSUNG_USER_AGENT
                            }
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onToken(token: String) = deliver(token)
                                },
                                "KizzyBridge",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript(TOKEN_EXTRACT_JS, null)
                                }
                            }
                            loadUrl(LOGIN_URL)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Or paste your account token manually") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { deliver(manualToken) },
                    enabled = manualToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use this token")
                }
            }
        }
    }
}
