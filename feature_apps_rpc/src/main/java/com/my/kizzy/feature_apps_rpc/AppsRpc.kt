/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * AppsRpc.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

@file:Suppress("DEPRECATION")

package com.my.kizzy.feature_apps_rpc

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppsOutage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.feature_rpc_base.services.AppDetectionService
import com.my.kizzy.feature_rpc_base.services.CustomRpcService
import com.my.kizzy.data.utils.AppsInfo
import com.my.kizzy.feature_rpc_base.services.ExperimentalRpc
import com.my.kizzy.feature_rpc_base.services.MediaRpcService
import com.my.kizzy.feature_rpc_base.utils.stopMediaRpcService
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.AppsItem
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.components.SwitchBar
import com.my.kizzy.ui.components.preference.PreferencesHint
import androidx.compose.material.icons.outlined.AppsOutage

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsRPC(
    state: AppsState,
    updateAppEnabled: (String) -> Unit,
    onBackPressed: () -> Unit,
    hasUsageAccess: Boolean,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true })
    val ctx = LocalContext.current
    var serviceEnabled by remember { mutableStateOf(AppUtils.appDetectionRunning()) }
    var searchText by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // The search field lives in its own row below the app bar. Putting a
            // full width field inside the `actions` slot squeezes the title down to
            // zero width, which makes the title wrap and inflates the whole app bar.
            Column {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.main_appDetection),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    },
                    navigationIcon = { BackButton { onBackPressed() } },
                    actions = {
                        IconButton(onClick = { isSearchBarVisible = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(id = R.string.search)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                AnimatedVisibility(visible = isSearchBarVisible) {
                    SearchBar(
                        text = searchText,
                        onTextChanged = { searchText = it },
                        onClose = {
                            isSearchBarVisible = false
                            searchText = ""
                        },
                        placeholder = stringResource(id = R.string.search_placeholder),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    item {
                        AnimatedVisibility(
                            visible = !hasUsageAccess
                        ) {
                            PreferencesHint(
                                title = stringResource(id = R.string.usage_access),
                                description = stringResource(id = R.string.usage_access_desc),
                                icon = Icons.Outlined.AppsOutage,
                            ) {
                                when (hasUsageAccess) {
                                    false -> ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    else -> Unit
                                }
                            }
                        }
                    }
                    item {
                        SwitchBar(
                            title = stringResource(id = R.string.enable_appsRpc),
                            isChecked = serviceEnabled,
                            enabled = hasUsageAccess
                        ) {
                            serviceEnabled = !serviceEnabled
                            when (serviceEnabled) {
                                true -> {
                                    ctx.stopMediaRpcService()
                                    ctx.stopService(Intent(ctx, CustomRpcService::class.java))
                                    ctx.stopService(Intent(ctx, ExperimentalRpc::class.java))
                                    ctx.startService(Intent(ctx, AppDetectionService::class.java))
                                }

                                false -> ctx.stopService(
                                    Intent(
                                        ctx,
                                        AppDetectionService::class.java
                                    )
                                )
                            }
                        }
                    }
                    // Pre-filter apps list before composition to avoid running
                    // contains() checks for every item on each recomposition.
                    // Only matching apps are composed, eliminating search lag.
                    val filteredApps = if (searchText.isBlank()) {
                        state.apps
                    } else {
                        state.apps.filter { app ->
                            app.name.contains(searchText, ignoreCase = true)
                                || app.pkg.contains(searchText, ignoreCase = true)
                        }
                    }

                    items(
                        count = filteredApps.size,
                        key = { index -> filteredApps[index].pkg }
                    ) { index ->
                        val app = filteredApps[index]
                        AppsItem(
                            name = app.name,
                            pkg = app.pkg,
                            isChecked = state.enabledApps[app.pkg] ?: false,
                            onClick = { updateAppEnabled(app.pkg) }
                        )
                    }
                }
            }
        }
    }
}