/*
 *
 *  ****************-------------------------------------------------
 *  *  * Copyright (C) 2022
 *  *  * Home.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 */

package com.my.kizzy.feature_home

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.toVersion
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.feature_home.feature.HomeFeature
import com.my.kizzy.feature_home.feature.homeFeaturesProvider
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.services.KizzyTileService
import com.my.kizzy.feature_settings.SettingsDrawer
import com.my.kizzy.navigation.AppRoute
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.RpcInstance
import com.my.kizzy.preference.displayLabel
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.KSwitch
import com.my.kizzy.ui.components.UpdateDialog
import kotlinx.coroutines.launch

/**
 * Main Home screen composable showing RPC feature/clone cards, drag-and-drop reordering,
 * instance renaming/deletion, update checks, and the navigation drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    state: HomeScreenState,
    checkForUpdates: () -> Unit,
    showBadge: Boolean,
    user: User?,
    navigateToProfile: () -> Unit,
    navigateToStyleAndAppearance: () -> Unit,
    navigateToLanguages: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRpcSettings: () -> Unit,
    navigateToLogsScreen: () -> Unit,
    navigateTo: (AppRoute) -> Unit = {},
    hasUsageAccess: MutableState<Boolean>,
    hasNotificationAccess: MutableState<Boolean>,
    onReloadHome: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Resolve string resources up front for dialogs, toasts, and Quick Settings tiles
    val updateCheckText = stringResource(R.string.update_check_for_update)
    val noUpdatesText = stringResource(R.string.update_no_updates_available)
    val qsTileLabel = stringResource(R.string.qs_tile_label)
    val mediaRpcLabel = stringResource(R.string.main_mediaRpc)
    val manualRpcLabel = stringResource(R.string.main_ManualRpc)
    val customRpcLabel = stringResource(R.string.main_customRpc)

    // Dialog and drawer UI states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAddRpcDialog by remember { mutableStateOf(false) }
    var selectedRpcType by remember { mutableStateOf<String?>(null) }
    var renamingTarget by remember { mutableStateOf<HomeFeature?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var drawerOpen by remember { mutableStateOf(false) }
    var isDrawerAnimating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
        canScroll = { false }
    )

    val rpcManager = RpcManager.getInstance(ctx)

    // Optimistic UI overrides for instant feedback before persisted preferences update
    var prefsRefreshCounter by remember { mutableIntStateOf(0) }
    val pendingAddedInstances = remember { mutableStateListOf<RpcInstance>() }
    val deletedInstanceIds = remember { mutableStateListOf<String>() }
    val labelOverrides = remember { mutableStateMapOf<String, String>() }

    // Observe preference map and read current RPC instances (built-ins + user clones)
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap<String, Any?>())
    val homeRpcInstances = remember(prefsMap, prefsRefreshCounter) {
        Prefs.getHomeRpcInstances()
    }

    // Build feature cards directly from current preferences and AppRoute navigation callback
    val features = homeFeaturesProvider(
        prefsMap = prefsMap,
        navigateTo = navigateTo,
        hasUsageAccess = hasUsageAccess,
        hasNotificationAccess = hasNotificationAccess,
        userVerified = user?.verified == true,
        onAddInstance = { instance ->
            Prefs.addHomeRpcInstance(instance)
            prefsRefreshCounter++
        },
        onDeleteInstance = { instanceId ->
            Prefs.deleteHomeRpcInstance(instanceId)
            prefsRefreshCounter++
        },
        onReorder = { instances ->
            Prefs.reorderHomeRpcInstances(instances)
            prefsRefreshCounter++
        },
    )

    // Clear optimistic state once persisted instances reflect the changes
    LaunchedEffect(homeRpcInstances) {
        val instanceMap = homeRpcInstances.associateBy { it.id }
        pendingAddedInstances.removeAll { it.id in instanceMap }
        deletedInstanceIds.removeAll { it !in instanceMap }
        labelOverrides.entries.removeAll { (id, label) ->
            instanceMap[id]?.displayLabel(ctx) == label
        }
    }

    // Merge persisted instances with newly created pending instances
    val effectiveInstances = remember(
        homeRpcInstances,
        pendingAddedInstances.toList(),
        prefsRefreshCounter,
    ) {
        val existingIds = homeRpcInstances.map { it.id }.toSet()
        homeRpcInstances + pendingAddedInstances.filter { it.id !in existingIds }
    }

    // Find a built-in template feature matching an RPC type for synthesizing clone cards
    fun templateFeatureForType(type: String): HomeFeature? {
        val lowerTitle = when (type) {
            "media_rpc" -> mediaRpcLabel.lowercase()
            "manual_rpc" -> manualRpcLabel.lowercase()
            "custom_rpc" -> customRpcLabel.lowercase()
            else -> type.lowercase()
        }
        return features.firstOrNull { f ->
            f.instanceId.isBlank() && f.title.lowercase().contains(lowerTitle)
        } ?: features.firstOrNull { it.instanceId.isBlank() }
    }

    // Combine base features with synthesized clones, apply overrides, and keep built-ins pinned first
    val visibleFeatures = remember(
        features,
        effectiveInstances,
        prefsRefreshCounter,
        deletedInstanceIds.toList(),
        labelOverrides.toMap(),
    ) {
        val instanceById = effectiveInstances.associateBy { it.id }
        val orderMap = effectiveInstances.mapIndexed { index, inst -> inst.id to index }.toMap()

        val mappedExisting = features
            .filter { feature ->
                feature.instanceId.isBlank() ||
                        (feature.instanceId !in deletedInstanceIds && feature.instanceId in instanceById)
            }
            .map { feature ->
                val resolvedLabel = labelOverrides[feature.instanceId]
                    ?: instanceById[feature.instanceId]?.displayLabel(ctx)
                if (!resolvedLabel.isNullOrBlank() && resolvedLabel != feature.title) {
                    feature.copy(title = resolvedLabel)
                } else {
                    feature
                }
            }

        val existingInstanceIds = mappedExisting.mapNotNull { it.instanceId.takeIf(String::isNotBlank) }.toSet()
        val synthesizedNew = effectiveInstances
            .filter { inst -> inst.id !in deletedInstanceIds && inst.id !in existingInstanceIds }
            .mapNotNull { inst ->
                val tpl = templateFeatureForType(inst.type) ?: return@mapNotNull null
                val resolvedLabel = labelOverrides[inst.id] ?: inst.displayLabel(ctx).ifBlank { tpl.title }
                tpl.copy(
                    title = resolvedLabel,
                    instanceId = inst.id,
                    isDeletable = true,
                    isChecked = false,
                )
            }

        (mappedExisting + synthesizedNew).sortedBy { feature ->
            if (feature.instanceId.isBlank()) Int.MIN_VALUE
            else orderMap[feature.instanceId] ?: Int.MAX_VALUE
        }
    }

    // Backing list for live drag-and-drop card reordering in the LazyColumn
    val displayFeatures = remember { mutableStateListOf<HomeFeature>() }
    LaunchedEffect(visibleFeatures) {
        displayFeatures.clear()
        displayFeatures.addAll(visibleFeatures)
    }

    var draggedKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemPitchPx = with(LocalDensity.current) { (84.dp + 12.dp).toPx() }

    // Persist current display order for instance cards
    fun persistCurrentOrder() {
        val orderedIds = displayFeatures.mapNotNull { it.instanceId.takeIf(String::isNotBlank) }
        if (orderedIds.isEmpty()) return
        val byId = effectiveInstances.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] } +
                effectiveInstances.filter { it.id !in orderedIds }
        Prefs.reorderHomeRpcInstances(reordered)
        prefsRefreshCounter++
    }

    // Apply an immediate label override and persist the renamed instance
    fun renameInstance(target: HomeFeature, newLabel: String) {
        val trimmed = newLabel.trim()
        if (trimmed.isEmpty() || target.instanceId.isBlank()) return

        labelOverrides[target.instanceId] = trimmed
        val idx = displayFeatures.indexOfFirst { it.instanceId == target.instanceId }
        if (idx >= 0) {
            displayFeatures[idx] = displayFeatures[idx].copy(title = trimmed)
        }

        val updated = effectiveInstances.map { inst ->
            if (inst.id == target.instanceId) inst.copy(label = trimmed) else inst
        }
        Prefs.reorderHomeRpcInstances(updated)
        prefsRefreshCounter++
    }

    // Remove a clone instance immediately and persist deletion
    fun deleteInstance(target: HomeFeature) {
        if (target.instanceId.isNotBlank() && target.instanceId !in deletedInstanceIds) {
            deletedInstanceIds.add(target.instanceId)
        }
        pendingAddedInstances.removeAll { it.id == target.instanceId }
        displayFeatures.removeAll { it.instanceId == target.instanceId }
        Prefs.deleteHomeRpcInstance(target.instanceId)
        prefsRefreshCounter++
        onReloadHome()
    }

    // Refresh running RPC service state whenever Home resumes
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            rpcManager.refreshFromServices()
            prefsRefreshCounter++
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
                            val sbm = ctx.getSystemService(StatusBarManager::class.java)
                            sbm.requestAddTileService(
                                cn,
                                qsTileLabel,
                                Icon.createWithResource(ctx, R.drawable.ic_tile_play),
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
                        val onUpdateClick = {
                            Toast.makeText(
                                ctx,
                                updateCheckText,
                                Toast.LENGTH_SHORT
                            ).show()
                            checkForUpdates()
                            showUpdateDialog = true
                        }

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
                                    modifier = Modifier.clickable(onClick = onUpdateClick)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Update,
                                contentDescription = "Update",
                                modifier = Modifier.clickable(onClick = onUpdateClick)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                            Text(text = stringResource(R.string.home_add_rpc))
                        }
                    }
                }

                items(
                    items = displayFeatures,
                    key = { feature -> feature.instanceId.ifBlank { "${feature.title}_${feature.route}" } },
                ) { item ->
                    val itemKey = item.instanceId.ifBlank { "${item.title}_${item.route}" }
                    val isDragging = draggedKey == itemKey
                    val currentIndex = displayFeatures.indexOfFirst { feature ->
                        (feature.instanceId.ifBlank { "${feature.title}_${feature.route}" }) == itemKey
                    }

                    // Swap adjacent deletable items when dragged past half a row pitch
                    val handleDragDelta: (Float) -> Unit = { deltaY ->
                        if (item.isDeletable && currentIndex >= 0) {
                            dragOffsetY += deltaY
                            val threshold = itemPitchPx * 0.5f

                            if (dragOffsetY > threshold && currentIndex < displayFeatures.lastIndex) {
                                val targetIndex = currentIndex + 1
                                if (displayFeatures[targetIndex].isDeletable) {
                                    val moved = displayFeatures.removeAt(currentIndex)
                                    displayFeatures.add(targetIndex, moved)
                                    dragOffsetY -= itemPitchPx
                                    persistCurrentOrder()
                                }
                            } else if (dragOffsetY < -threshold && currentIndex > 0) {
                                val targetIndex = currentIndex - 1
                                if (displayFeatures[targetIndex].isDeletable) {
                                    val moved = displayFeatures.removeAt(currentIndex)
                                    displayFeatures.add(targetIndex, moved)
                                    dragOffsetY += itemPitchPx
                                    persistCurrentOrder()
                                }
                            }
                        }
                    }

                    val finishDrag: () -> Unit = {
                        if (draggedKey == itemKey) {
                            draggedKey = null
                            dragOffsetY = 0f
                            persistCurrentOrder()
                        }
                    }

                    val cardContent: @Composable () -> Unit = {
                        FeatureCard(
                            feature = item,
                            isDragging = isDragging,
                            dragOffsetY = if (isDragging) dragOffsetY else 0f,
                            onDragStart = {
                                draggedKey = itemKey
                                dragOffsetY = 0f
                            },
                            onDragDelta = handleDragDelta,
                            onDragEnd = finishDrag,
                            onToggle = {
                                item.onCheckedChange(!item.isChecked)
                            },
                            onRename = {
                                renamingTarget = item
                            },
                            onDelete = {
                                deleteInstance(item)
                            },
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
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
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

            // Dialog to rename a user-added RPC instance
            renamingTarget?.let { target ->
                val nameState = rememberTextFieldState(initialText = target.title)
                LaunchedEffect(target.instanceId, target.title) {
                    nameState.setTextAndPlaceCursorAtEnd(target.title)
                }

                AlertDialog(
                    onDismissRequest = { renamingTarget = null },
                    title = { Text(text = "Rename RPC") },
                    text = {
                        TextField(
                            state = nameState,
                            label = { Text(text = "Name") },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val updatedName = nameState.text.toString()
                            val currentTarget = target
                            renamingTarget = null
                            renameInstance(currentTarget, updatedName)
                        }) {
                            Text(text = stringResource(R.string.save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { renamingTarget = null }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // Dialog to pick an RPC type and create a new clone instance
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
                            val baseLabel = when (type) {
                                "media_rpc" -> mediaRpcLabel
                                "manual_rpc" -> manualRpcLabel
                                "custom_rpc" -> customRpcLabel
                                else -> type
                            }
                            val existingCount = effectiveInstances.count { it.type == type }
                            val label = if (existingCount > 0) "$baseLabel #${existingCount + 1}" else baseLabel

                            // Assign OAuth slots in round-robin order across authenticated applications
                            val newInstance = RpcInstance(
                                id = RpcInstance.newId(type),
                                type = type,
                                label = label,
                                oauthIndex = Prefs.getRoundRobinOAuthIndex(existingCount),
                            )
                            if (pendingAddedInstances.none { it.id == newInstance.id }) {
                                pendingAddedInstances.add(newInstance)
                            }
                            Prefs.addHomeRpcInstance(newInstance)
                            prefsRefreshCounter++
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

            // Show release update dialog when an update check finishes with a newer version
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

/**
 * Single selectable RPC type row inside the "Add RPC" dialog.
 */
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

/**
 * Individual Home feature/clone row supporting click navigation, toggle switch,
 * rename, long-press drag reordering, and deletion.
 */
@Composable
private fun FeatureCard(
    feature: HomeFeature,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
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

    val contentColor = if (feature.isChecked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
            }
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(brush = brush)
            .clickable {
                feature.route?.let { feature.onClick(it) }
            }
            .padding(start = 22.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Icon(
            painter = painterResource(id = feature.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W500),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (feature.showSwitch) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(48.dp),
            ) {
                KSwitch(
                    checked = feature.isChecked,
                    onClick = onToggle,
                )
            }
        }
        // Rename, long-press drag handle, and delete controls for added RPC instances
        if (feature.isDeletable && !feature.isAddButton) {
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "✎",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = contentColor,
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(feature.instanceId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                },
                            )
                        },
                ) {
                    Text(
                        text = "☰",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = contentColor,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "\u2716",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Registers a temporary [LifecycleEventObserver] for the current [LocalLifecycleOwner].
 */
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
        user = fakeUser,
        navigateToProfile = {},
        navigateToStyleAndAppearance = {},
        navigateToLanguages = {},
        navigateToAbout = {},
        navigateToRpcSettings = {},
        navigateToLogsScreen = {},
        navigateTo = {},
        hasUsageAccess = remember { mutableStateOf(false) },
        hasNotificationAccess = remember { mutableStateOf(false) },
    )
}

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