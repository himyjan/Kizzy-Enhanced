/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Credits.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_about.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.Contributor
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.CreditItem
import com.my.kizzy.ui.components.Subtitle
import kotlin.math.ceil
import kotlin.math.max

data class Credit(val title: String = "", val license: String = "", val url: String = "")

const val GPL_V3 = "GNU General Public License v3.0"
const val APACHE_V2 = "Apache License, Version 2.0"
const val MIT = "MIT License"

const val originalKizzy = "https://github.com/dead8309/Kizzy"
const val readYou = "https://github.com/Ashinch/ReadYou"
const val seal = "https://github.com/JunkFood02/Seal"
const val metrolist = "https://github.com/MetrolistGroup/Metrolist"
const val materialColor = "https://github.com/material-foundation/material-color-utilities"
const val nintendoRepo = "https://github.com/ninstar/Rich-Presence-U"
const val XboxRpc = "https://github.com/MrCoolAndroid/Xbox-Rich-Presence-Discord"
const val monet = "https://github.com/Kyant0/Monet"

val creditsList = listOf(
    Credit("Kizzy (original app)", GPL_V3, originalKizzy),
    Credit("Read You", GPL_V3, readYou),
    Credit("Seal", GPL_V3, seal),
    Credit("Metrolist", GPL_V3, metrolist),
    Credit("material-color-utilities", APACHE_V2, materialColor),
    Credit("Rich-Presence-U", GPL_V3, nintendoRepo),
    Credit("Xbox-Rich-Presence-Discord", MIT, XboxRpc),
    Credit("Monet", APACHE_V2, monet)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Credits(state: CreditScreenState, onBackPressed: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { true }
    )

    val uriHandler = LocalUriHandler.current
    fun openUrl(url: String) {
        if (url.isNotBlank()) {
            uriHandler.openUri(url)
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
                        text = stringResource(id = R.string.credits),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = { BackButton { onBackPressed() } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "design_credits_header") {
                Subtitle(text = stringResource(id = R.string.design_credits))
            }
            items(
                items = creditsList,
                key = { item -> "${item.title}_${item.url}" }
            ) { item: Credit ->
                CreditItem(
                    title = item.title,
                    description = item.license
                ) {
                    openUrl(item.url)
                }
            }
            item(key = "contributors_header") {
                Subtitle(text = stringResource(id = R.string.contributors))
            }
            item(key = "contributors_content") {
                when (state) {
                    is CreditScreenState.Error -> {
                        Text(
                            text = state.error ?: stringResource(id = R.string.unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }

                    CreditScreenState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                        )
                    }

                    is CreditScreenState.LoadingCompleted -> {
                        if (state.contributors.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.no_internet_connection),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                            )
                        } else {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            ) {
                                val minCellWidth = 90.dp
                                val columns = max(1, (maxWidth / minCellWidth).toInt())
                                val rows = max(1, ceil(state.contributors.size.toDouble() / columns).toInt())
                                val cellHeight = maxWidth / columns
                                val totalGridHeight = cellHeight * rows

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minCellWidth),
                                    userScrollEnabled = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(totalGridHeight)
                                ) {
                                    items(
                                        items = state.contributors,
                                        key = { contributor -> "${contributor.name}_${contributor.url}" }
                                    ) { contributor ->
                                        ContributorItem(contributor = contributor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContributorItem(contributor: Contributor) {
    val uriHandler = LocalUriHandler.current
    val absoluteElevation = LocalAbsoluteTonalElevation.current + 2.dp
    Box(
        modifier = Modifier
            .padding(5.dp)
            .aspectRatio(ratio = 1f)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteElevation))
            .clickable {
                if (contributor.url.isNotBlank()) {
                    uriHandler.openUri(contributor.url)
                }
            }
    ) {
        AsyncImage(
            model = contributor.avatar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .size(60.dp)
                .clip(RoundedCornerShape(26.dp)),
            contentDescription = contributor.name
        )
        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            text = contributor.name,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview
@Composable
fun CreditsPreview() {
    Credits(
        state = CreditScreenState.Loading,
        onBackPressed = {}
    )
}

@Preview
@Composable
fun CreditsPreview2() {
    Credits(
        state = CreditScreenState.Error(error = "No internet connection"),
        onBackPressed = {}
    )
}

@Preview
@Composable
fun CreditsPreview3() {
    Credits(
        state = CreditScreenState.LoadingCompleted(
            listOf(
                Contributor(
                    avatar = "",
                    name = "dead8309",
                    url = "https://github.com/dead8309"
                )
            )
        ),
        onBackPressed = {}
    )
}