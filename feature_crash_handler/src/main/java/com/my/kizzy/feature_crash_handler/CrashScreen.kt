/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CrashScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_crash_handler

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.data.utils.shareAsFile
import com.my.kizzy.feature_crash_handler.R as CrashR
import com.my.kizzy.resources.R as ResR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashScreen(trace: String?) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val hasTrace = !trace.isNullOrBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = ResR.string.app_crashed),
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                    )
                },
                actions = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            painter = painterResource(id = CrashR.drawable.ic_close),
                            contentDescription = "Close"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (hasTrace) {
                ExtendedFloatingActionButton(
                    onClick = { ctx.shareAsFile(trace, "Kizzy_Log.txt") }
                ) {
                    Icon(
                        painter = painterResource(id = CrashR.drawable.ic_share),
                        contentDescription = "Share Logs",
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(text = stringResource(id = ResR.string.share_crash_logs))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(id = ResR.string.share_crash_logs_desc),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            ElevatedCard(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            ) {
                Text(
                    text = trace?.takeIf { it.isNotBlank() }
                        ?: stringResource(id = ResR.string.unknown_error),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewCrashScreen() = CrashScreen(
    trace = """
        Kizzy crash report
        Manufacturer: ******
        Device: *****
        Android version: **
        App version: *** (**)
        Stacktrace:
        java.lang.SecurityException: Missing permission to control media.
        	at android.os.Parcel.createExceptionOrNull(Parcel.java:3011)
        	at android.os.Parcel.createException(Parcel.java:2995)
        	at android.media.session.ISessionManager${'$'}Stub${'$'}Proxy.getSessions(ISessionManager.java:672)
    """.trimIndent()
)