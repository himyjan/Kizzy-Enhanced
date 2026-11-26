/*
 *
 *  ****************-------------------------------------------------
 *  *  * Copyright (C) 2022
 *  *  * Home.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_home

import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.toVersion
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_home.feature.HomeFeature
import com.my.kizzy.feature_home.feature.ToolTipContent
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType
import com.my.kizzy.feature_rpc_base.services.KizzyTileService
import com.my.kizzy.feature_settings.SettingsDrawer
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RpcInstance
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.UpdateDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    state: HomeScreenState,
    checkForUpdates: () -> Unit,
    showBadge: Boolean,
    features: List<HomeFeature>,
    user: User?,
    navigateToProfile: () -> Unit,
    navigateToStyleAndAppearance: () -> Unit,
    navigateToLanguages: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRpcSettings: () -> Unit,
    navigateToLogsScreen: () -> Unit,
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Configuration-aware string resources
    val updateCheckText = stringResource(R.string.update_check_for_update)
    val noUpdatesText = stringResource(R.string.update_no_updates_available)
    val qsTileLabel = stringResource(R.string.qs_tile_label)
    val mediaRpcLabel = stringResource(R.string.main_mediaRpc)
    val manualRpcLabel = stringResource(R.string.main_ManualRpc)
    val customRpcLabel = stringResource(R.string.main_customRpc)

    var showUpdateDialog by remember {
        mutableStateOf(false)
    }

    // State for the "Add RPC" type picker dialog opened from the Features header button
    var showAddRpcDialog by remember { mutableStateOf(false) }
    var selectedRpcType by remember { mutableStateOf<String?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerOpen by remember { mutableStateOf(false) }
    var isDrawerAnimating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { false }
        )

    // `features` is now derived directly from RpcManager.activeRpc (see
    // homeFeaturesProvider), so the switch state can be read from it without
    // keeping a second, easily-stale copy in local state.
    val rpcManager = RpcManager.getInstance(ctx)

    // Read user-created RPC instances from DataStore (built-ins + clones).
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap<String, Any?>())
    val homeRpcInstances = Prefs.getHomeRpcInstances()

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            rpcManager.refreshFromServices()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                SettingsDrawer(
                    user = user,
                    showKizzyQuickieRequestItem = !LocalInspectionMode.current && !KizzyTileService.tileAdded.value,
                    onRequestAddTile = {
                        val cn = ComponentName(ctx, KizzyTileService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                            sbm.requestAddTileService(
                                cn,
                                qsTileLabel,
                                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_tile_play),
                                {}
                            ) {}
                        }
                    },
                    navigateToProfile = navigateToProfile,
                    navigateToStyleAndAppearance = navigateToStyleAndAppearance,
                    navigateToLanguages = navigateToLanguages,
                    navigateToAbout = navigateToAbout,
                    navigateToRpcSettings = navigateToRpcSettings,
                    navigateToLogsScreen = navigateToLogsScreen
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.welcome) + ", ${user?.globalName ?: user?.username ?: ""}",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!isDrawerAnimating) {
                                    isDrawerAnimating = true
                                    val targetOpen = !drawerOpen
                                    scope.launch {
                                        if (targetOpen) {
                                            drawerState.open()
                                        } else {
                                            drawerState.close()
                                        }
                                        drawerOpen = targetOpen
                                        isDrawerAnimating = false
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Menu,
                                Icons.Outlined.Menu.name,
                            )
                        }
                    },
                    actions = {
                        if (showBadge) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        modifier = Modifier
                                            .offset(8.dp, (-14).dp)
                                            .size(8.dp)
                                            .clip(CircleShape),
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Update,
                                    contentDescription = "Update",
                                    modifier = Modifier.clickable {
                                        Toast.makeText(
                                            ctx,
                                            updateCheckText,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        checkForUpdates()
                                        showUpdateDialog = true
                                    }
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = "Update",
                                modifier = Modifier.clickable {
                                    Toast.makeText(
                                        ctx,
                                        updateCheckText,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    checkForUpdates()
                                    showUpdateDialog = true
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { navigateToProfile() }) {
                            if (user != null && user.getAvatarImage() != null) {
                                AsyncImage(
                                    model = user.getAvatarImage(),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            CircleShape,
                                        )
                                        .clip(CircleShape),
                                    placeholder = painterResource(R.drawable.error_avatar),
                                    contentDescription = user.username
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = Icons.Default.Person.name,
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        ) { paddingValues ->
            // Single top-level LazyColumn bounded by Scaffold's content constraints.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Features section header on the left with "Add RPC" aligned to the right.
                item(key = "features_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(id = R.string.features),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        FilledTonalButton(
                            onClick = { showAddRpcDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_home_add_rpc),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Add RPC")
                        }
                    }
                }

                // No local list copy: RpcManager is the single source of truth and
                // `features` recomposes from it, so the switch always shows the real
                // running state.
                items(
                    items = features,
                    key = { it.instanceId.ifBlank { "${it.title}_${it.route}" } },
                ) { item ->
                    val idx = features.indexOf(item)
                    val isBuiltIn = !item.isDeletable
                    val canMoveUp = !isBuiltIn && idx > 0 && features[idx - 1].isDeletable
                    val canMoveDown = !isBuiltIn && idx < features.lastIndex - 1 && features[idx + 1].isDeletable

                    val moveUp: () -> Unit = {
                        val updated = homeRpcInstances.toMutableList()
                        val pos = updated.indexOfFirst { it.id == item.instanceId }
                        if (pos > 0) {
                            val moved = updated.removeAt(pos)
                            updated.add(pos - 1, moved)
                            Prefs.reorderHomeRpcInstances(updated)
                        }
                    }

                    val moveDown: () -> Unit = {
                        val updated = homeRpcInstances.toMutableList()
                        val pos = updated.indexOfFirst { it.id == item.instanceId }
                        if (pos in 0 until updated.lastIndex) {
                            val moved = updated.removeAt(pos)
                            updated.add(pos + 1, moved)
                            Prefs.reorderHomeRpcInstances(updated)
                        }
                    }

                    val cardContent: @Composable () -> Unit = {
                        FeatureCard(
                            feature = item,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onToggle = {
                                item.onCheckedChange(!item.isChecked)
                            },
                            onDelete = {
                                Prefs.deleteHomeRpcInstance(item.instanceId)
                            },
                            onMoveUp = moveUp,
                            onMoveDown = moveDown,
                        )
                    }

                    if (item.tooltipText.isNotBlank()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Above,
                            ),
                            state = rememberTooltipState(),
                            tooltip = {
                                RichTooltip(
                                    title = {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    },
                                    text = { Text(item.tooltipText) },
                                    action = {
                                        if (item.featureDocsLink.isNotBlank()) {
                                            TextButton(onClick = { uriHandler.openUri(item.featureDocsLink) }) {
                                                Text(text = stringResource(R.string.learn_more))
                                            }
                                        }
                                    },
                                )
                            },
                            content = cardContent,
                        )
                    } else {
                        cardContent()
                    }
                }
            }

            // Add RPC type picker dialog
            if (showAddRpcDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAddRpcDialog = false
                        selectedRpcType = null
                    },
                    title = { Text(text = stringResource(R.string.add_rpc_title)) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.add_rpc_select_type),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RpcTypeRow(
                                type = "media_rpc",
                                label = mediaRpcLabel,
                                selectedRpcType = selectedRpcType,
                                onSelect = { selectedRpcType = it },
                            )
                            RpcTypeRow(
                                type = "manual_rpc",
                                label = manualRpcLabel,
                                selectedRpcType = selectedRpcType,
                                onSelect = { selectedRpcType = it },
                            )
                            RpcTypeRow(
                                type = "custom_rpc",
                                label = customRpcLabel,
                                selectedRpcType = selectedRpcType,
                                onSelect = { selectedRpcType = it },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val type = selectedRpcType ?: return@TextButton
                            showAddRpcDialog = false
                            selectedRpcType = null
                            val label = when (type) {
                                "media_rpc" -> mediaRpcLabel
                                "manual_rpc" -> manualRpcLabel
                                "custom_rpc" -> customRpcLabel
                                else -> type
                            }
                            val newInstance = RpcInstance(
                                id = "${type}_clone_${System.currentTimeMillis()}",
                                type = type,
                                label = label,
                            )
                            // Save clone to prefs and start its service
                            Prefs.addHomeRpcInstance(newInstance)
                            val rpcType = when (newInstance.type) {
                                "media_rpc" -> RpcType.MediaRpc
                                "manual_rpc" -> RpcType.ManualRpc
                                "custom_rpc" -> RpcType.CustomRpc
                                else -> null
                            }
                            if (rpcType != null) {
                                rpcManager.switchTo(setOf(rpcType), add = true)
                            }
                        }) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddRpcDialog = false
                            selectedRpcType = null
                        }) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    },
                )
            }

            when (state) {
                is HomeScreenState.LoadingCompleted -> {
                    if (showUpdateDialog) {
                        if (state.release.toVersion()
                                .whetherNeedUpdate(BuildConfig.VERSION_NAME.toVersion())
                        ) {
                            with(state.release) {
                                UpdateDialog(
                                    newVersionPublishDate = publishedAt ?: "",
                                    newVersionSize = assets?.getOrNull(0)?.size ?: 0,
                                    newVersionLog = body ?: "",
                                    apkUrl = assets?.getOrNull(0)?.browserDownloadUrl,
                                    onDismissRequest = {
                                        showUpdateDialog = false
                                    },
                                )
                            }
                        } else {
                            Toast.makeText(
                                ctx,
                                noUpdatesText,
                                Toast.LENGTH_SHORT
                            ).show()
                            showUpdateDialog = false
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun RpcTypeRow(
    type: String,
    label: String,
    selectedRpcType: String?,
    onSelect: (String) -> Unit,
) {
    val isSelected = selectedRpcType == type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(type) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isSelected) {
            Text(text = "✓", color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun FeatureCard(
    feature: HomeFeature,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val brush = if (feature.isChecked) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(brush = brush)
            .clickable {
                feature.route?.let { feature.onClick(it) }
            }
            .padding(start = 22.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Icon(
            painter = painterResource(id = feature.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W500),
            color = if (feature.isChecked) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        if (feature.showSwitch) {
            Spacer(modifier = Modifier.width(12.dp))
            KSwitch(
                checked = feature.isChecked,
                modifier = Modifier.padding(start = 12.dp),
                onClick = onToggle,
            )
        }
        // Reorder and delete controls for user-added cloned RPC instances
        if (feature.isDeletable && !feature.isAddButton) {
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canMoveUp) {
                    IconButton(
                        onClick = onMoveUp,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(text = "\u2191")
                    }
                }
                if (canMoveDown) {
                    IconButton(
                        onClick = onMoveDown,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Text(text = "\u2193")
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Text(text = "\u2716", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    Home(
        state = HomeScreenState.Loading,
        checkForUpdates = {},
        showBadge = true,
        features = fakeFeatures,
        user = fakeUser,
        navigateToProfile = { },
        navigateToStyleAndAppearance = { },
        navigateToLanguages = { },
        navigateToAbout = { },
        navigateToRpcSettings = { }) {

    }
}

val fakeFeatures = listOf(
    HomeFeature(
        title = "Media RPC",
        icon = R.drawable.ic_media_rpc,
        route = "media_rpc_route",
        shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),
        tooltipText = ToolTipContent.MEDIA_RPC_DOCS
    ),
    HomeFeature(
        title = "App Detection",
        icon = R.drawable.ic_manual_rpc,
        route = "app_detection_route",
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        tooltipText = ToolTipContent.APP_DETECTION_DOCS
    ),
    HomeFeature(
        title = "Manual RPC",
        icon = R.drawable.ic_manual_rpc,
        route = "manual_rpc_route",
        shape = RoundedCornerShape(20.dp, 44.dp, 20.dp, 44.dp),
        tooltipText = ToolTipContent.MANUAL_RPC_DOCS
    ),
    HomeFeature(
        title = "Custom RPC",
        icon = R.drawable.ic_custom_rpc,
        route = "custom_rpc_route",
        shape = RoundedCornerShape(44.dp, 20.dp, 44.dp, 20.dp),
        tooltipText = ToolTipContent.CUSTOM_RPC_DOCS
    )
)

val fakeUser = User(
    accentColor = null,
    avatar = null,
    avatarDecoration = null,
    badges = null,
    banner = null,
    bannerColor = null,
    discriminator = "3050",
    id = null,
    publicFlags = null,
    username = "yzziK",
    special = null,
    verified = false,
    nitro = true,
    bio = "Hello 👋"
)