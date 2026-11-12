/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ExperimentalRpcAppsScreen.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package xyz.dead8309.feature_experimental_rpc.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.ActivityTypeChip
import com.my.kizzy.ui.components.SearchBar
import com.my.kizzy.ui.components.rememberAppIconBitmap
import xyz.dead8309.feature_experimental_rpc.UiEvent
import xyz.dead8309.feature_experimental_rpc.UiState
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsMma
import androidx.compose.material.icons.outlined.Visibility

typealias AppItem = com.my.kizzy.data.utils.AppsInfo

/**
 * The icon is read from the package manager, which is a blocking call. It is
 * loaded in the background and cached, so scrolling the list does not stall the
 * UI thread.
 */
@Composable
private fun loadAppIconCached(packageName: String): ImageBitmap? =
    rememberAppIconBitmap(packageName, sizePx = 144)

/**
 * Individual app list item with stable key. Extracting to a separate composable with
 * [key] ensures Compose only recomposes items that actually change.
 */
@Composable
private fun AppListItem(
    app: AppItem,
    isChecked: Boolean,
    /** The type the user picked, null when it is detected automatically. */
    selectedTypeId: Int?,
    /** The type the automatic detection came up with. */
    detectedTypeId: Int,
    onToggleEnabled: (String) -> Unit,
    onSetActivityType: (String, Int?) -> Unit,
) {
    val iconBitmap = loadAppIconCached(app.pkg)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        painter = BitmapPainter(iconBitmap),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_apps),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = app.pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            KSwitch(
                checked = isChecked,
                enable = true,
                modifier = Modifier.padding(start = 20.dp, end = 6.dp),
                onClick = { onToggleEnabled(app.pkg) }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ActivityTypeChip(
                    selectedTypeId = selectedTypeId,
                    detectedTypeId = detectedTypeId,
                    onSelect = { type -> onSetActivityType(app.pkg, type) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalRpcAppsScreen(
    onBackPressed: () -> Unit,
    state: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var isSearchBarVisible by remember { mutableStateOf(false) }

    // Pre-compute filtered and sorted list outside of LazyColumn composition
    // to avoid running filter/sort on every recomposition
    val filteredApps = remember(state.installedApps, state.enabledApps, searchText) {
        state.installedApps
            .filter {
                searchText.isEmpty() ||
                        it.name.contains(searchText, ignoreCase = true) ||
                        it.pkg.contains(searchText, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<AppItem> {
                    state.enabledApps[it.pkg] ?: false
                }.thenBy { it.name }
            )
    }

    Scaffold(
        topBar = {
            // The search field lives in its own row below the app bar. Putting a
            // full width field inside the `actions` slot squeezes the title down to
            // zero width, which makes the title wrap and inflates the whole app bar.
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_selection)) },
                    navigationIcon = { BackButton(onClick = onBackPressed) },
                    actions = {
                        IconButton(onClick = { isSearchBarVisible = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search)
                            )
                        }
                    }
                )
                AnimatedVisibility(visible = isSearchBarVisible) {
                    SearchBar(
                        text = searchText,
                        onTextChanged = { searchText = it },
                        onClose = {
                            isSearchBarVisible = false
                            searchText = ""
                        },
                        placeholder = stringResource(R.string.search_placeholder),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (state.isAppsLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.pkg }
                    ) { app ->
                        val isChecked = state.enabledApps[app.pkg] ?: false
                        val selectedTypeId = state.appActivityTypes[app.pkg]
                        val detectedTypeId = ActivityTypeResolver.knownType(app.pkg)
                            ?: ActivityTypeResolver.DEFAULT_TYPE

                        AppListItem(
                            app = app,
                            isChecked = isChecked,
                            selectedTypeId = selectedTypeId,
                            detectedTypeId = detectedTypeId,
                            onToggleEnabled = { pkg ->
                                onEvent(UiEvent.ToggleAppEnabled(pkg))
                            },
                            onSetActivityType = { pkg, type ->
                                onEvent(UiEvent.SetAppActivityType(pkg, type))
                            }
                        )
                    }
                }
            }
        }
    }
}
