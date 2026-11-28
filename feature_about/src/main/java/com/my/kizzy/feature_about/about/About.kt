/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * About.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_about.about

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ContactSupport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.my.kizzy.feature_about.BuildConfig
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SettingItem
import com.my.kizzy.ui.components.preference.PreferencesHint

const val github_Repository = "https://github.com/himyjan/Kizzy-Enhanced"
const val github_Repository_original = "https://github.com/dead8309/Kizzy"
const val github_Release = "$github_Repository/releases/latest"
const val github_Issues = "$github_Repository/issues/new"
const val github_Discussions = "$github_Repository/discussions"
const val github_privacy_policy = "$github_Repository_original/blob/master/TERMS_OF_SERVICE.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun About(
    onBackPressed: () -> Unit,
    navigateToCredits: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )

    fun openUrl(url: String) {
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.about),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = { BackButton { onBackPressed() } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "privacy_policy") {
                PreferencesHint(
                    title = stringResource(id = R.string.privacy_policy),
                    description = stringResource(id = R.string.privacy_policy_desc),
                    icon = vectorResource(R.drawable.ic_privacy_tip)
                ) {
                    openUrl(github_privacy_policy)
                }
            }
            item(key = "github_readme") {
                SettingItem(
                    title = stringResource(id = R.string.github_readme),
                    description = stringResource(id = R.string.github_readme_desc),
                    icon = vectorResource(R.drawable.ic_description)
                ) {
                    openUrl(github_Repository)
                }
            }
            item(key = "github_latest_release") {
                SettingItem(
                    title = stringResource(id = R.string.github_latest_release),
                    description = stringResource(id = R.string.github_latest_release_desc),
                    icon = vectorResource(R.drawable.ic_new_releases)
                ) {
                    openUrl(github_Release)
                }
            }
            item(key = "github_discussions") {
                SettingItem(
                    title = "GitHub Discussions",
                    description = "Ask questions, report bugs, or share feedback",
                    icon = vectorResource(R.drawable.ic_forum)
                ) {
                    openUrl(github_Discussions)
                }
            }
            item(key = "github_issue") {
                SettingItem(
                    title = stringResource(id = R.string.github_issue),
                    description = stringResource(id = R.string.github_issue_desc),
                    icon = Icons.AutoMirrored.Outlined.ContactSupport
                ) {
                    openUrl(github_Issues)
                }
            }
            item(key = "credits") {
                SettingItem(
                    title = stringResource(id = R.string.credits),
                    description = stringResource(id = R.string.credits_desc),
                    icon = vectorResource(R.drawable.ic_auto_awesome)
                ) {
                    navigateToCredits()
                }
            }
            item(key = "version") {
                SettingItem(
                    title = stringResource(id = R.string.version),
                    description = BuildConfig.VERSION_NAME,
                    icon = vectorResource(R.drawable.ic_info)
                ) {
                }
            }
        }
    }
}

@Preview
@Composable
fun AboutScreen() {
    About(onBackPressed = {}, navigateToCredits = {})
}