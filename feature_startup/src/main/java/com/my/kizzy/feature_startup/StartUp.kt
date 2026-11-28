/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * StartUp.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_startup

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.getLanguageDesc
import com.my.kizzy.preference.languages
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartUp(
    usageAccessStatus: MutableState<Boolean>,
    mediaControlStatus: MutableState<Boolean>,
    batteryOptimizationStatus: MutableState<Boolean>,
    navigateToLanguages: () -> Unit = {},
    navigateToLogin: () -> Unit = {},
    navigateToHome: () -> Unit = {},
) {
    val prefsState by Prefs.prefsState.collectAsState()
    val userJson: String = prefsState[Prefs.USER_DATA] as? String ?: ""
    val user = when {
        userJson.isNotEmpty() -> Json.decodeFromString<User>(userJson)
        else -> null
    }
    val context = LocalContext.current
    var storagePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val storageLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                storagePermissionGranted = isGranted
            },
        )
    var notificationPostingPerm by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context, POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
            )
        } else {
            mutableStateOf(true)
        }
    }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                notificationPostingPerm = isGranted
            },
        )
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.W600),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 15.dp, top = 0.dp, end = 15.dp, bottom = 70.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                item {
                    Text(
                        text = stringResource(id = R.string.setup),
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)),
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_warning),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(id = R.string.user_data_policy),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(id = R.string.setup_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    DiscordSetupCard()
                }
                item {
                    Subtitle(
                        text = stringResource(R.string.mandatory),
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.usage_access),
                        description = stringResource(id = R.string.usage_access_desc),
                        status = usageAccessStatus.value,
                    ) {
                        val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        try {
                            context.startActivity(appInfoIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    }
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.allow_usage_access),
                        description = stringResource(id = R.string.allow_usage_access_desc),
                        status = usageAccessStatus.value,
                    ) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.notification_access),
                        description = stringResource(id = R.string.request_for_notification_access),
                        status = mediaControlStatus.value,
                    ) {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.battery_optimization),
                        description = stringResource(id = R.string.battery_optimization_desc),
                        status = batteryOptimizationStatus.value,
                    ) {
                        val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        try {
                            context.startActivity(directIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        SetupCard(
                            title = stringResource(id = R.string.post_notification),
                            description = stringResource(id = R.string.request_for_post_notification),
                            status = notificationPostingPerm,
                        ) {
                            launcher.launch(POST_NOTIFICATIONS)
                        }
                    }
                } else {
                    item {
                        SetupCard(
                            title = stringResource(id = R.string.storage_access),
                            description = stringResource(id = R.string.request_for_storage_access),
                            status = storagePermissionGranted,
                        ) {
                            if (!storagePermissionGranted) {
                                storageLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                    }
                }

                item {
                    Subtitle(
                        text = stringResource(R.string.optional),
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.account),
                        description = when {
                            (prefsState[Prefs.TOKEN] as? String)?.isNotEmpty() == true ||
                                (prefsState[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String)?.isNotEmpty() == true ->
                                (prefsState[Prefs.USER_ID] as? String)
                                    ?: extractUserIdFromToken(
                                        prefsState[Prefs.TOKEN] as? String
                                            ?: prefsState[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String
                                            ?: "",
                                    )
                                    ?: stringResource(id = R.string.logged_in)
                            else -> stringResource(id = R.string.login_with_discord)
                        },
                    ) {
                        navigateToLogin()
                    }
                }
                item {
                    SetupCard(
                        title = stringResource(id = R.string.language),
                        description = buildString {
                            languages.keys.forEach { key ->
                                this.append(getLanguageDesc(key) + ", ")
                            }
                        },
                    ) {
                        navigateToLanguages()
                    }
                }
            }

            val hasDiscordLogin =
                (prefsState[Prefs.TOKEN] as? String)?.isNotBlank() == true ||
                    (prefsState[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String)?.isNotBlank() == true
            val allMandatoryComplete = hasDiscordLogin &&
                batteryOptimizationStatus.value &&
                notificationPostingPerm &&
                usageAccessStatus.value &&
                mediaControlStatus.value

            val anyToken = (prefsState[Prefs.TOKEN] as? String)
                ?: (prefsState[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] as? String)
            LaunchedEffect(anyToken) {
                val userId = prefsState[Prefs.USER_ID] as? String
                if (anyToken?.isNotEmpty() == true && (userId == null || userId.isEmpty())) {
                    fetchDiscordUserId(anyToken)?.let { fetchedUserId ->
                        Prefs[Prefs.USER_ID] = fetchedUserId
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(end = 15.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = allMandatoryComplete,
                ) {
                    TextButton(
                        onClick = { navigateToHome() },
                    ) {
                        Text(
                            text = stringResource(R.string.start_app_now),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun extractUserIdFromToken(token: String?): String? {
    val parts = token?.split(".") ?: return null
    return parts.firstOrNull()?.takeIf { it.isNotBlank() && it.all { it.isDigit() } }
}

private suspend fun fetchDiscordUserId(token: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://discord.com/api/v10/users/@me")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val body = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.getOrNull(1)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun DiscordSetupCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        with(MaterialTheme) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(id = R.string.discord_setup_title),
                    style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(id = R.string.discord_setup_desc),
                style = typography.bodyMedium,
                color = colorScheme.onSecondaryContainer,
            )
            DiscordSetupStep(
                number = "1",
                title = stringResource(id = R.string.discord_step1_title),
                content = stringResource(id = R.string.discord_step1_content),
            )
            DiscordSetupStep(
                number = "2",
                title = stringResource(id = R.string.discord_step2_title),
                content = stringResource(id = R.string.discord_step2_content),
            )
            DiscordSetupStep(
                number = "3",
                title = stringResource(id = R.string.discord_step3_title),
                content = stringResource(id = R.string.discord_step3_content),
            )
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lightbulb),
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Top),
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.discord_tip_title),
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(id = R.string.discord_tip_content),
                        style = typography.bodySmall,
                        color = colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 4,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordSetupStep(
    number: String,
    title: String,
    content: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .align(Alignment.Top),
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f),
                maxLines = 4,
            )
        }
    }
}