/*

 *

 *  ******************************************************************

 *  *  * Copyright (C) 2022

 *  *  * CustomRpc.kt is part of Kizzy

 *  *  *  and can not be copied and/or distributed without the express

 *  *  * permission of yzziK(Vaibhav)

 *  *  *****************************************************************

 *

 *

 */
package com.my.kizzy.feature_custom_rpc

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import coil3.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.data.rpc.Constants.MAX_ALLOWED_CHARACTER_LENGTH
import com.my.kizzy.data.utils.uriToFile
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.feature_custom_rpc.components.BottomSheet
import com.my.kizzy.feature_custom_rpc.components.DateTimePickerDialog
import com.my.kizzy.feature_custom_rpc.components.ImagePicker
import com.my.kizzy.feature_custom_rpc.components.sheet.DeleteConfigDialog
import com.my.kizzy.feature_custom_rpc.components.sheet.LoadConfig
import com.my.kizzy.feature_rpc_base.RpcManager
import com.my.kizzy.feature_rpc_base.RpcManager.RpcType
import com.my.kizzy.feature_custom_rpc.components.sheet.PreviewDialog
import com.my.kizzy.feature_custom_rpc.components.sheet.RequestStoragePermissionDialog
import com.my.kizzy.feature_custom_rpc.components.sheet.SaveConfigDialog
import com.my.kizzy.feature_custom_rpc.components.sheet.ShareConfig
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.RpcField
import com.my.kizzy.ui.components.SwitchBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.BrokenImage

@Composable

fun CustomRPC(

    state: UiState = UiState(),

    onBackPressed: () -> Unit,

    onEvent: (UiEvent) -> Unit = {},

) {

    val snackBarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    CustomRpcScreen(

        onBackPressed = onBackPressed,

        onEvent = onEvent,

        snackBarHostState = snackBarHostState,

        state = state

    )

    if (state.showBottomSheet) {

        BottomSheet(onEvent = onEvent, onDismiss = {

            onEvent(UiEvent.TriggerBottomSheet)

        })

    }

    if (state.showLoadDialog) {

        LoadConfig(

            onDismiss = {

                onEvent(UiEvent.SheetEvent.TriggerLoadDialog)

            },

            onConfigSelected = {

                onEvent(UiEvent.SetFieldsFromConfig(it))

            }

        )

    }

    if (state.showShareDialog) {

        ShareConfig(

            onDismiss = {

                onEvent(UiEvent.SheetEvent.TriggerShareDialog)

            }

        )

    }

    if (state.showStoragePermissionRequestDialog) {

        RequestStoragePermissionDialog(

            onDismiss = { onEvent(UiEvent.SheetEvent.TriggerStoragePermissionRequest) },

            context = LocalContext.current

        )

    }

    if (state.showSaveDialog) {

        SaveConfigDialog(

            rpc = state.rpcConfig,

            onDismiss = { onEvent(UiEvent.SheetEvent.TriggerSaveDialog) },

            onSaved = {

                scope.launch {

                    snackBarHostState.showSnackbar(it)

                }

            }

        )

    }

    if (state.showDeleteDialog) {

        DeleteConfigDialog(

            onDismiss = {

                onEvent(UiEvent.SheetEvent.TriggerDeleteDialog)

            },

            onFilesDeleted = {

                scope.launch {

                    snackBarHostState.showSnackbar(it)

                }

            }

        )

    }

    if (state.showPreviewDialog) {

        val json = Prefs[Prefs.USER_DATA, "{}"]

        val user = Json.decodeFromString<User>(json)

        PreviewDialog(

            user = user,

            rpc = state.rpcConfig,

            onDismiss = {

                onEvent(UiEvent.SheetEvent.TriggerPreviewDialog)

            },

        )

    }

}



@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun CustomRpcScreen(

    snackBarHostState: SnackbarHostState,

    state: UiState,

    onBackPressed: () -> Unit,

    onEvent: (UiEvent) -> Unit,



    ) {

    val scrollBehavior =

        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(

            rememberTopAppBarState(),

            canScroll = { true })



    Scaffold(

        snackbarHost = { SnackbarHost(snackBarHostState) },

        modifier = Modifier

            .fillMaxSize()

            .nestedScroll(scrollBehavior.nestedScrollConnection),

        topBar = {

            LargeTopAppBar(

                title = {

                    Text(

                        text = stringResource(id = R.string.main_customRpc),

                        style = MaterialTheme.typography.headlineMedium,

                    )

                },

                navigationIcon = { BackButton { onBackPressed() } },

                scrollBehavior = scrollBehavior,

                actions = {

                    IconButton(onClick = { onEvent(UiEvent.TriggerBottomSheet) }) {

                        Icon(

                            imageVector = Icons.Outlined.MoreVert,

                            contentDescription = "menu"

                        )

                    }

                    IconButton(onClick = { onEvent(UiEvent.SheetEvent.ClearAllFields) }) {

                        Icon(

                            imageVector = Icons.Outlined.ClearAll,

                            contentDescription = stringResource(id = R.string.clear_all_fields)

                        )

                    }

                })

        }) { padding ->

        // Two tabs: the regular Custom RPC settings and the quick select for the
        // Console RPC presets. Picking a preset jumps back to the settings tab so
        // the user can fine tune every value there.
        var selectedTab by remember { mutableStateOf(0) }

        Column(modifier = Modifier.padding(padding)) {

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Custom RPC",
                            modifier = Modifier.size(24.dp),
                            tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    text = { Text(text = stringResource(id = R.string.rpc_tab_custom_settings)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.DesktopWindows,
                            contentDescription = "Console RPC",
                            modifier = Modifier.size(24.dp),
                            tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    text = { Text(text = stringResource(id = R.string.rpc_tab_console_presets)) }
                )
            }

            RpcTextFieldsColumn(

                snackBarHostState = snackBarHostState,

                uiState = state,

                onEvent = onEvent,

                selectedTab = selectedTab,

                onPresetSelected = { selectedTab = 0 }

            )

        }

    }

}



@Composable

private fun RpcTextFieldsColumn(

    onEvent: (UiEvent) -> Unit,

    uiState: UiState,

    snackBarHostState: SnackbarHostState,

    selectedTab: Int = 0,

    onPresetSelected: () -> Unit = {},

) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rpcManager = RpcManager.getInstance(context)
    // Cover art preview toggles
    var showLargeCoverArtPreview by remember { mutableStateOf(uiState.showLargeCoverArt) }
    var showSmallCoverArtPreview by remember { mutableStateOf(uiState.showSmallCoverArt) }
    // Re-sync from services every time this screen enters so UI matches reality
    LaunchedEffect(Unit) {
        rpcManager.refreshFromServices()
    }
    var isCustomRpcEnabled by remember {
        mutableStateOf(RpcType.CustomRpc in rpcManager.activeRpc.value)
    }
    var isConsoleRpcEnabled by remember {
        mutableStateOf(RpcType.ConsoleRpc in rpcManager.activeRpc.value)
    }
    LaunchedEffect(rpcManager.activeRpc) {
        isCustomRpcEnabled = RpcType.CustomRpc in rpcManager.activeRpc.value
        isConsoleRpcEnabled = RpcType.ConsoleRpc in rpcManager.activeRpc.value
    }
    var consolePlatform by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_PLATFORM, "xbox"])
    }
    var consoleActivityType by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_ACTIVITY_TYPE, "0"])
    }
    var consoleStatus by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_STATUS, "online"])
    }
    var consoleEnableTimestamps by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_ENABLE_TIMESTAMPS, false])
    }
    var consoleShowCoverArt by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART, true])
    }
    var consoleButton1Text by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_BUTTON1_TEXT, ""])
    }
    var consoleButton1Url by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_BUTTON1_URL, ""])
    }
    var consoleButton2Text by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_BUTTON2_TEXT, ""])
    }
    var consoleButton2Url by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_BUTTON2_URL, ""])
    }
    var showConsolePlatformDialog by remember { mutableStateOf(false) }
    var showConsoleActivityTypeDialog by remember { mutableStateOf(false) }
    var showConsoleStatusDialog by remember { mutableStateOf(false) }
    var showConsoleButtonsDialog by remember { mutableStateOf(false) }

    // Console RPC presets. The user presets are kept in a state so a preset
    // created or edited in the dialog shows up in the list immediately.
    var consoleCustomPresets by remember { mutableStateOf(ConsolePresetStore.custom()) }
    var consoleSelectedPresetId by remember {
        // Restore the last selected preset. When there is none (first run after
        // the update), highlight the preset that matches the saved platform so
        // the list reflects the current Console RPC configuration.
        mutableStateOf(
            ConsolePresetStore.selectedId().ifBlank {
                val platform = Prefs[Prefs.CONSOLE_RPC_PLATFORM, "xbox"]
                ConsolePresetStore.builtIns().firstOrNull { it.platform == platform }?.id ?: ""
            }
        )
    }
    var consolePresetEditorOpen by remember { mutableStateOf(false) }
    var consolePresetBeingEdited by remember { mutableStateOf<ConsolePreset?>(null) }
    var consoleAutoDetect by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true])
    }
    var consoleIncludeSpoofed by remember {
        mutableStateOf(Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED, false])
    }

    // Console RPC game list state
    var consoleSearchText by remember { mutableStateOf("") }
    val consoleSearchTextState = rememberTextFieldState(initialText = "")

    LaunchedEffect(consoleSearchTextState) {
        snapshotFlow { consoleSearchTextState.text.toString() }
            .drop(1)
            .distinctUntilChanged()
            .collect { consoleSearchText = it }
    }
    var consoleSelectedGameTitle by remember { mutableStateOf("") }

    // Image pickers for preset cover art URLs
    val pickLargeCoverArt = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val edited = consolePresetBeingEdited
            if (edited != null) {
                consolePresetBeingEdited = edited.copy(
                    largeCoverArtUrl = it.toString()
                )
            }
        }
    }
    val pickSmallCoverArt = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val edited = consolePresetBeingEdited
            if (edited != null) {
                consolePresetBeingEdited = edited.copy(
                    smallCoverArtUrl = it.toString()
                )
            }
        }
    }

    /** Writes the console values to the preferences so they survive a restart. */
    fun persistConsoleSettings() {
        Prefs[Prefs.CONSOLE_RPC_PLATFORM] = consolePlatform
        Prefs[Prefs.CONSOLE_RPC_ACTIVITY_TYPE] = consoleActivityType
        Prefs[Prefs.CONSOLE_RPC_STATUS] = consoleStatus
        Prefs[Prefs.CONSOLE_RPC_ENABLE_TIMESTAMPS] = consoleEnableTimestamps
        Prefs[Prefs.CONSOLE_RPC_SHOW_COVER_ART] = consoleShowCoverArt
    }

    /** The presence that Console RPC sends with the current values. */
    fun buildConsoleConfig(): RpcConfig {
        val startTs = if (consoleEnableTimestamps) System.currentTimeMillis().toString() else ""
        val endTs = if (consoleEnableTimestamps) (System.currentTimeMillis() + 3600000L).toString() else ""
        return RpcConfig(
            name = "Console RPC",
            details = "Console RPC running",
            type = consoleActivityType,
            status = consoleStatus,
            timestampsStart = startTs,
            timestampsStop = endTs,
            platform = consolePlatform,
            // Use the cover art URLs that were set on uiState.rpcConfig (by
            // applyConsolePreset / applyConsoleGame / SetFieldsFromConfig) when
            // cover art is enabled, otherwise clear them.
            largeImg = if (consoleShowCoverArt) uiState.rpcConfig.largeImg else "",
            smallImg = if (consoleShowCoverArt) uiState.rpcConfig.smallImg else "",
            button1 = consoleButton1Text,
            button1link = consoleButton1Url,
            button2 = consoleButton2Text,
            button2link = consoleButton2Url,
        )
    }

    /** Re-sends the presence when Console RPC is already running. */
    fun applyConsoleSettingsIfRunning() {
        if (RpcType.ConsoleRpc !in rpcManager.activeRpc.value) return
        scope.launch {
            rpcManager.switchTo(
                RpcType.ConsoleRpc,
                rpcConfigString = Json.encodeToString(buildConsoleConfig()),
                isConsoleRpc = true
            )
        }
    }

    /** Applies a preset to the console values and, when running, to Discord.
     *  Also updates the Custom RPC tab text fields so the user can see and edit them.
     *  If Console RPC is not running, starts it so the preset takes effect immediately. */
    fun applyConsolePreset(preset: ConsolePreset, presetName: String) {
        consolePlatform = preset.platform
        consoleActivityType = preset.activityType
        consoleStatus = preset.status
        consoleEnableTimestamps = preset.timestamps
        consoleShowCoverArt = preset.coverArt

        // Also update Custom RPC tab fields so the user sees and can edit them
        val startTs = if (preset.timestamps) System.currentTimeMillis().toString() else ""
        val endTs = if (preset.timestamps) (System.currentTimeMillis() + 3600000L).toString() else ""
        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(
            name = presetName,
            details = presetName,
            type = preset.activityType,
            status = preset.status,
            timestampsStart = startTs,
            timestampsStop = endTs,
            platform = preset.platform,
            // Use the preset's cover art URLs when cover art is enabled,
            // otherwise clear the images.
            largeImg = if (preset.coverArt) preset.largeCoverArtUrl else "",
            smallImg = if (preset.coverArt) preset.smallCoverArtUrl else "",
            button1 = "",
            button1link = "",
            button2 = "",
            button2link = "",
        )))

        persistConsoleSettings()

        // If Console RPC is not running, start it so the preset takes effect immediately
        if (RpcType.ConsoleRpc !in rpcManager.activeRpc.value) {
            scope.launch {
                rpcManager.switchTo(
                    RpcType.ConsoleRpc,
                    rpcConfigString = Json.encodeToString(buildConsoleConfig()),
                    isConsoleRpc = true
                )
            }
        } else {
            applyConsoleSettingsIfRunning()
        }
    }

    /** Applies a game to the console values and, when running, to Discord. */
    fun applyConsoleGame(game: com.my.kizzy.domain.model.Game, startTs: String, endTs: String) {
        consolePlatform = game.platform
        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(
            name = game.platform,
            details = game.game_title,
            timestampsStart = startTs,
            timestampsStop = endTs,
            platform = game.platform,
            largeImg = if (consoleShowCoverArt) game.large_image.orEmpty() else "",
            smallImg = if (consoleShowCoverArt) game.small_image.orEmpty() else "",
        )))
        persistConsoleSettings()

        // If Console RPC is not running, start it so the game takes effect immediately
        if (RpcType.ConsoleRpc !in rpcManager.activeRpc.value) {
            scope.launch {
                rpcManager.switchTo(
                    RpcType.ConsoleRpc,
                    rpcConfigString = Json.encodeToString(buildConsoleConfig()),
                    isConsoleRpc = true
                )
            }
        } else {
            applyConsoleSettingsIfRunning()
        }
    }

    LazyColumn(

        modifier = Modifier.fillMaxSize()

    ) {

        // Enable Custom RPC switch - only on the Custom RPC tab. Console RPC is
        // started by selecting a preset, no separate enable switch is needed.
        if (selectedTab == 0) {
            item {

                SwitchBar(

                    title = stringResource(R.string.enable_customRpc),

                    isChecked = isCustomRpcEnabled,

                ) {
                    val newValue = !isCustomRpcEnabled
                    isCustomRpcEnabled = newValue
                    when (newValue) {
                        true -> {
                            // Send the config the user typed on the Custom RPC tab.
                            // This used to send buildConsoleConfig(), so the service
                            // always received "Console RPC / Console RPC running".
                            val customConfig = uiState.rpcConfig
                            scope.launch {
                                // Add Custom RPC without stopping Console RPC or other RPCs.
                                rpcManager.switchTo(
                                    setOf(RpcType.CustomRpc),
                                    add = true,
                                    rpcConfigString = Json.encodeToString(customConfig),
                                    isConsoleRpc = false
                                )
                            }
                        }
                        false -> scope.launch {
                            // Stop only Custom RPC, leave Console RPC and others running.
                            rpcManager.stopRpcTypes(setOf(RpcType.CustomRpc))
                        }
                    }
                }
            }

        }

        if (selectedTab == 0) {

        with(uiState.rpcConfig) {

            item {

                RpcField(

                    value = name, label = R.string.activity_name

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(name = it)))

                }

            }



            item {

                RpcField(

                    value = details, label = R.string.activity_details

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(details = it)))

                }

            }



            item {

                RpcField(

                    value = state, label = R.string.activity_state

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(state = it)))

                }

            }



            item {

                Row {

                    RpcField(

                        value = partyCurrentSize,

                        label = R.string.party_current,

                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),

                        isError =

                            partyCurrentSize.isNotEmpty() && (

                                    partyCurrentSize.toIntOrNull() == null ||

                                            0 >= partyCurrentSize.toInt() ||

                                            (partyMaxSize.toIntOrNull() != null && partyCurrentSize.toInt() > partyMaxSize.toInt())

                                    ),

                        errorMessage =

                            if (partyCurrentSize.isNotEmpty()) {

                                if (partyCurrentSize.toIntOrNull() == null) {

                                    stringResource(R.string.party_invalid_number)

                                } else if (0 >= partyCurrentSize.toInt()) {

                                    stringResource(R.string.party_less_than_zero)

                                } else if (partyMaxSize.toIntOrNull() != null && partyCurrentSize.toInt() > partyMaxSize.toInt()) {

                                    stringResource(R.string.party_greater_than_max)

                                } else {

                                    ""

                                }

                            } else {

                                ""

                            }



                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(partyCurrentSize = it)))

                    }

                }

            }



            item {

                AnimatedVisibility(visible = partyCurrentSize.isNotBlank()) {

                    RpcField(

                        value = partyMaxSize,

                        label = R.string.party_max,

                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),

                        isError =

                            partyMaxSize.isNotEmpty() && (

                                    partyMaxSize.toIntOrNull() == null ||

                                            0 >= partyMaxSize.toInt() ||

                                            (partyCurrentSize.toIntOrNull() != null && partyCurrentSize.toInt() > partyMaxSize.toInt())

                                    ),

                        errorMessage =

                            if (partyMaxSize.isNotEmpty()) {

                                if (partyMaxSize.toIntOrNull() == null) {

                                    stringResource(R.string.party_invalid_number)

                                } else if (0 >= partyMaxSize.toInt()) {

                                    stringResource(R.string.party_less_than_zero)

                                } else if (partyCurrentSize.toIntOrNull() != null && partyCurrentSize.toInt() > partyMaxSize.toInt()) {

                                    stringResource(R.string.party_greater_than_max)

                                } else {

                                    ""

                                }

                            } else if (partyCurrentSize.isNotEmpty() && partyMaxSize.isBlank()) {

                                stringResource(R.string.party_max_cannot_be_empty)

                            } else {

                                ""

                            }

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(partyMaxSize = it)))

                    }

                }

            }



            item {

                RpcField(

                    value = timestampsStart,

                    label = R.string.activity_start_timestamps,

                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                    trailingIcon = {

                        Icon(

                            imageVector = Icons.Outlined.EditCalendar,

                            contentDescription = null,

                            modifier = Modifier.clickable {

                                onEvent(UiEvent.TriggerStartTimeStampsDialog)

                            })

                    },

                    content = {

                        if (uiState.showStartTimeStampsPickerDialog) {

                            DateTimePickerDialog(

                                selectedDate = timestampsStart.toLongOrNull(),

                                onDismiss = { onEvent(UiEvent.TriggerStartTimeStampsDialog) }

                            ) {

                                onEvent(

                                    UiEvent.SetFieldsFromConfig(

                                        uiState.rpcConfig.copy(

                                            timestampsStart = it.toString()

                                        )

                                    )

                                )

                            }

                        }

                    }) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(timestampsStart = it)))

                }

            }



            item {

                RpcField(

                    value = timestampsStop,

                    label = R.string.activity_stop_timestamps,

                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                    trailingIcon = {

                        Icon(

                            imageVector = Icons.Outlined.EditCalendar,

                            contentDescription = null,

                            modifier = Modifier.clickable {

                                onEvent(UiEvent.TriggerStopTimeStampsDialog)

                            })

                    },

                    content = {

                        if (uiState.showStopTimeStampsPickerDialog) {

                            DateTimePickerDialog(

                                selectedDate = timestampsStop.toLongOrNull(),

                                onDismiss = { onEvent(UiEvent.TriggerStopTimeStampsDialog) }

                            ) {

                                onEvent(

                                    UiEvent.SetFieldsFromConfig(

                                        uiState.rpcConfig.copy(

                                            timestampsStop = it.toString()

                                        )

                                    )

                                )

                            }

                        }

                    }) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(timestampsStop = it)))

                }

            }



            val iconStatus = if (uiState.activityTypeIsExpanded)

                Icons.Outlined.KeyboardArrowUp

            else

                Icons.Outlined.KeyboardArrowDown



            item {

                RpcField(

                    value = platform,

                    label = R.string.activity_platform,

                    trailingIcon = {

                        Icon(

                            imageVector = iconStatus,

                            contentDescription = null,

                            modifier = Modifier.clickable {

                                onEvent(UiEvent.TriggerPlatformDropDownMenu)

                            })

                    },

                    content = {

                        DropdownMenu(

                            expanded = uiState.platformIsExpanded, onDismissRequest = {

                                onEvent(UiEvent.TriggerPlatformDropDownMenu)

                            },

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Constants.ACTIVITY_PLATFORMS.forEach { (label, value) ->

                                DropdownMenuItem(

                                    text = {

                                        Text(text = label)

                                    },

                                    onClick = {

                                        onEvent(

                                            UiEvent.SetFieldsFromConfig(

                                                uiState.rpcConfig.copy(

                                                    platform = value

                                                )

                                            )

                                        )

                                        onEvent(UiEvent.TriggerPlatformDropDownMenu)

                                    },

                                )

                            }

                        }

                    }

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(platform = it)))

                }

            }



            item {

                RpcField(

                    value = status,

                    label = R.string.activity_status_online_idle_dnd,

                    trailingIcon = {

                        Icon(

                            imageVector = iconStatus,

                            contentDescription = null,

                            modifier = Modifier.clickable {

                                onEvent(UiEvent.TriggerStatusDropDownMenu)

                            })

                    },

                    content = {

                        DropdownMenu(

                            expanded = uiState.statusIsExpanded, onDismissRequest = {

                                onEvent(UiEvent.TriggerStatusDropDownMenu)

                            },

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Constants.ACTIVITY_STATUS.forEach { (resId, value) ->

                                DropdownMenuItem(

                                    text = {

                                        Text(text = stringResource(resId))

                                    },

                                    onClick = {

                                        onEvent(

                                            UiEvent.SetFieldsFromConfig(

                                                uiState.rpcConfig.copy(

                                                    status = value

                                                )

                                            )

                                        )

                                        onEvent(UiEvent.TriggerStatusDropDownMenu)

                                    },

                                )

                            }

                        }

                    }

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(status = it)))

                }

            }



            item {

                RpcField(

                    value = button1, label = R.string.activity_button1_text,

                    isError = button1.length >= MAX_ALLOWED_CHARACTER_LENGTH,

                    errorMessage = stringResource(R.string.activity_button_max_character)

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(button1 = it)))

                }

            }

            item {

                AnimatedVisibility(visible = button1.isNotBlank()) {

                    RpcField(

                        value = button1link,

                        label = R.string.activity_button1_url

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(button1link = it)))

                    }

                }

            }



            item {

                RpcField(

                    value = button2, label = R.string.activity_button2_text,

                    isError = button2.length >= MAX_ALLOWED_CHARACTER_LENGTH,

                    errorMessage = stringResource(R.string.activity_button_max_character)

                ) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(button2 = it)))

                }

            }



            item {

                AnimatedVisibility(visible = button2.isNotBlank()) {

                    RpcField(

                        value = button2link,

                        label = R.string.activity_button2_url

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(button2link = it)))

                    }

                }

            }

            item {

                var openPickerDialog by remember {

                    mutableStateOf(false)

                }

                var showProgress by remember {

                    mutableStateOf(false)

                }

                RpcField(

                    value = largeImg,

                    label = R.string.activity_large_image,

                    trailingIcon = {

                        Icon(

                            imageVector = Icons.Outlined.Image,

                            contentDescription = "openGallery",

                            modifier = Modifier.clickable {

                                openPickerDialog = true

                            })

                    },

                    content = {

                        ImagePicker(

                            visible = openPickerDialog,

                            onDismiss = { openPickerDialog = false },

                            showProgress = showProgress

                        ) { uri ->

                            showProgress = true

                            onEvent(UiEvent.UploadImage(context.uriToFile(uri)) { result ->

                                showProgress = false

                                openPickerDialog = false

                                onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(largeImg = result)))

                            })

                        }

                    }) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(largeImg = it)))

                }

            }

            item {

                AnimatedVisibility(visible = showLargeCoverArtPreview && largeImg.isNotBlank()) {

                    Column(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(16.dp, 8.dp)

                    ) {

                        Text(

                            text = stringResource(id = R.string.activity_large_image_preview),

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                        AsyncImage(

                            model = coverArtPreviewUrl(largeImg),

                            contentDescription = null,

                            modifier = Modifier

                                .size(200.dp)

                                .clip(RoundedCornerShape(8.dp)),

                            placeholder = rememberVectorPainter(Icons.Outlined.Image),

                            error = rememberVectorPainter(Icons.Outlined.BrokenImage)

                        )

                    }

                }

            }

            item {

                AnimatedVisibility(visible = largeImg.isNotBlank()) {

                    RpcField(

                        value = largeText,

                        label = R.string.activity_large_text

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(largeText = it)))

                    }

                }

            }

            item {

                var openPickerDialog by remember {

                    mutableStateOf(false)

                }

                var showProgress by remember {

                    mutableStateOf(false)

                }

                RpcField(

                    value = smallImg,

                    label = R.string.activity_small_image,

                    trailingIcon = {

                        Icon(

                            imageVector = Icons.Outlined.Image,

                            contentDescription = "openGallery",

                            modifier = Modifier.clickable {

                                openPickerDialog = true

                            })

                    },

                    content = {

                        ImagePicker(

                            visible = openPickerDialog,

                            onDismiss = { openPickerDialog = false },

                            showProgress = showProgress

                        ) { uri ->

                            showProgress = true

                            onEvent(UiEvent.UploadImage(context.uriToFile(uri)) { result ->

                                showProgress = false

                                openPickerDialog = false

                                onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(smallImg = result)))

                            })

                        }

                    }) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(smallImg = it)))

                }

            }

            item {

                AnimatedVisibility(visible = showSmallCoverArtPreview && smallImg.isNotBlank()) {

                    Column(

                        modifier = Modifier

                            .fillMaxWidth()

                            .padding(16.dp, 8.dp)

                    ) {

                        Text(

                            text = stringResource(id = R.string.activity_small_image_preview),

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                        AsyncImage(

                            model = coverArtPreviewUrl(smallImg),

                            contentDescription = null,

                            modifier = Modifier

                                .size(120.dp)

                                .clip(RoundedCornerShape(8.dp)),

                            placeholder = rememberVectorPainter(Icons.Outlined.Image),

                            error = rememberVectorPainter(Icons.Outlined.BrokenImage)

                        )

                    }

                }

            }

            item {

                AnimatedVisibility(visible = smallImg.isNotBlank()) {

                    RpcField(

                        value = smallText,

                        label = R.string.activity_small_text

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(smallText = it)))

                    }

                }

            }

            val icon = if (uiState.activityTypeIsExpanded)

                Icons.Outlined.KeyboardArrowUp

            else

                Icons.Outlined.KeyboardArrowDown



            item {

                RpcField(

                    value = type,

                    label = R.string.activity_type,

                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

                    trailingIcon = {

                        Icon(

                            imageVector = icon,

                            contentDescription = null,

                            modifier = Modifier.clickable {

                                onEvent(UiEvent.TriggerActivityTypeDropDownMenu)

                            })

                    }) {

                    onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(type = it)))

                }



                DropdownMenu(

                    expanded = uiState.activityTypeIsExpanded, onDismissRequest = {

                        onEvent(UiEvent.TriggerActivityTypeDropDownMenu)

                    }, modifier = Modifier.fillMaxWidth()

                ) {

                    Constants.ACTIVITY_TYPE.forEach { (label, value) ->

                        DropdownMenuItem(

                            text = {

                                Text(text = label)

                            },

                            onClick = {

                                onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(type = value.toString())))

                                onEvent(UiEvent.TriggerActivityTypeDropDownMenu)

                            },

                        )

                    }

                }

            }

            // ===== Console RPC Settings =====

            item {
                var consolePlatformExpanded by remember { mutableStateOf(false) }
                val consolePlatformIcon = if (consolePlatformExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown

                RpcField(
                    value = consolePlatform,
                    label = R.string.console_platform,
                    trailingIcon = {
                        Icon(
                            imageVector = consolePlatformIcon,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                consolePlatformExpanded = !consolePlatformExpanded
                            }
                        )
                    },
                    content = {
                        DropdownMenu(
                            expanded = consolePlatformExpanded,
                            onDismissRequest = { consolePlatformExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Constants.ACTIVITY_PLATFORMS.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(text = label) },
                                    onClick = {
                                        consolePlatform = value
                                        consolePlatformExpanded = false
                                    }
                                )
                            }
                        }
                    }
                ) { }
            }

            item {
                var consoleActivityTypeExpanded by remember { mutableStateOf(false) }
                val consoleActivityTypeIcon = if (consoleActivityTypeExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown

                RpcField(
                    value = consoleActivityType,
                    label = R.string.console_activity_type,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Icon(
                            imageVector = consoleActivityTypeIcon,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                consoleActivityTypeExpanded = !consoleActivityTypeExpanded
                            }
                        )
                    },
                    content = {
                        DropdownMenu(
                            expanded = consoleActivityTypeExpanded,
                            onDismissRequest = { consoleActivityTypeExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Constants.ACTIVITY_TYPE.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(text = label) },
                                    onClick = {
                                        consoleActivityType = value.toString()
                                        consoleActivityTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                ) { }
            }

            item {
                var consoleStatusExpanded by remember { mutableStateOf(false) }
                val consoleStatusIcon = if (consoleStatusExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown

                RpcField(
                    value = consoleStatus,
                    label = R.string.console_status,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Icon(
                            imageVector = consoleStatusIcon,
                            contentDescription = null,
                            modifier = Modifier.clickable {
                                consoleStatusExpanded = !consoleStatusExpanded
                            }
                        )
                    },
                    content = {
                        DropdownMenu(
                            expanded = consoleStatusExpanded,
                            onDismissRequest = { consoleStatusExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Constants.ACTIVITY_STATUS.forEach { (resId, value) ->
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(resId)) },
                                    onClick = {
                                        consoleStatus = value
                                        consoleStatusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                ) { }
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_enable_timestamps),
                    isChecked = consoleEnableTimestamps,
                ) {
                    consoleEnableTimestamps = !consoleEnableTimestamps
                }
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_show_cover_art),
                    isChecked = consoleShowCoverArt,
                ) {
                    consoleShowCoverArt = !consoleShowCoverArt
                }
            }

            item {
                RpcField(
                    value = consoleButton1Text,
                    label = R.string.activity_button1_text,
                ) {
                    consoleButton1Text = it
                }
            }

            item {
                AnimatedVisibility(visible = consoleButton1Text.isNotBlank()) {
                    RpcField(
                        value = consoleButton1Url,
                        label = R.string.activity_button1_url,
                    ) {
                        consoleButton1Url = it
                    }
                }
            }

            item {
                RpcField(
                    value = consoleButton2Text,
                    label = R.string.activity_button2_text,
                ) {
                    consoleButton2Text = it
                }
            }

            item {
                AnimatedVisibility(visible = consoleButton2Text.isNotBlank()) {
                    RpcField(
                        value = consoleButton2Url,
                        label = R.string.activity_button2_url,
                    ) {
                        consoleButton2Url = it
                    }
                }
            }

            item {

                AnimatedVisibility(visible = type == "1") {

                    val streamUrlInfoText = stringResource(id = R.string.stream_url_info)

                    RpcField(

                        value = url,

                        label = R.string.stream_url,

                        trailingIcon = {

                            Icon(

                                imageVector = Icons.Outlined.Info,

                                contentDescription = null,

                                modifier = Modifier.clickable {

                                    scope.launch {

                                        snackBarHostState.showSnackbar(

                                            message = streamUrlInfoText

                                        )

                                    }

                                })

                        }

                    ) {

                        onEvent(UiEvent.SetFieldsFromConfig(uiState.rpcConfig.copy(url = it)))

                    }

                }

                Spacer(modifier = Modifier.size(100.dp))

            }

        }

        // ===== Console RPC Presets =====

        } else {

            item {
                Text(
                    text = stringResource(id = R.string.rpc_tab_console_presets_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Console RPC enable switch - independent from Custom RPC.
            // Toggling it starts/stops only Console RPC, leaving Custom RPC running.
            item {
                SwitchBar(
                    title = stringResource(id = R.string.enable_console_rpc),
                    isChecked = isConsoleRpcEnabled,
                ) {
                    val newValue = !isConsoleRpcEnabled
                    isConsoleRpcEnabled = newValue
                    when (newValue) {
                        true -> {
                            persistConsoleSettings()
                            val consoleConfig = buildConsoleConfig()
                            scope.launch {
                                rpcManager.switchTo(
                                    setOf(RpcType.ConsoleRpc),
                                    add = true,
                                    rpcConfigString = Json.encodeToString(consoleConfig),
                                    isConsoleRpc = true
                                )
                            }
                        }
                        false -> scope.launch {
                            rpcManager.stopRpcTypes(setOf(RpcType.ConsoleRpc))
                        }
                    }
                }
            }

            // ===== Console RPC Presets =====
            item {
                Text(
                    text = stringResource(id = R.string.console_presets_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                // Built-in presets first, then the ones the user created. The
                // list is rebuilt from the state so a new preset appears here
                // without leaving the tab.
                val presets = ConsolePresetStore.builtIns() + consoleCustomPresets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val presetName = if (preset.nameRes != 0) stringResource(preset.nameRes) else preset.name
                        val presetAppliedMessage = stringResource(R.string.console_preset_applied)
                        ConsolePresetChip(
                            preset = preset,
                            selected = consoleSelectedPresetId == preset.id,
                            onClick = {
                                consoleSelectedPresetId = preset.id
                                ConsolePresetStore.select(preset.id)
                                applyConsolePreset(preset, presetName)
                                scope.launch {
                                    snackBarHostState.showSnackbar(presetAppliedMessage)
                                }
                            },
                            onEdit = if (preset.builtIn) null else ({
                                consolePresetBeingEdited = preset
                                consolePresetEditorOpen = true
                            })
                        )
                    }
                }
            }

            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        consolePresetBeingEdited = ConsolePresetStore.newPreset()
                        consolePresetEditorOpen = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = stringResource(id = R.string.console_preset_new))
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.console_options_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_enable_timestamps),
                    isChecked = consoleEnableTimestamps,
                ) {
                    consoleEnableTimestamps = !consoleEnableTimestamps
                    persistConsoleSettings()
                    applyConsoleSettingsIfRunning()
                }
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_show_cover_art),
                    isChecked = consoleShowCoverArt,
                ) {
                    consoleShowCoverArt = !consoleShowCoverArt
                    persistConsoleSettings()
                    applyConsoleSettingsIfRunning()
                }
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_auto_detect),
                    isChecked = consoleAutoDetect,
                ) {
                    consoleAutoDetect = !consoleAutoDetect
                    Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT] = consoleAutoDetect
                }
            }

            item {
                SwitchBar(
                    title = stringResource(id = R.string.console_include_spoofed),
                    isChecked = consoleIncludeSpoofed,
                ) {
                    consoleIncludeSpoofed = !consoleIncludeSpoofed
                    Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED] = consoleIncludeSpoofed
                }
            }

            item {
                Spacer(modifier = Modifier.size(24.dp))
            }

            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = onPresetSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(text = stringResource(id = R.string.rpc_tab_back_to_settings))
                }
            }

            item {
                Spacer(modifier = Modifier.size(100.dp))
            }

            // ===== Console RPC Game List with Search (Bottom Priority) =====
            item {
                Text(
                    text = stringResource(id = R.string.console_games_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            item {
                androidx.compose.material3.OutlinedTextField(
                    state = consoleSearchTextState,
                    label = { Text(text = stringResource(id = R.string.search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(id = R.string.search)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                val games = if (consoleSearchText.isBlank()) {
                    uiState.consoleGames
                } else {
                    uiState.consoleGames.filter { it.game_title.contains(consoleSearchText, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.consoleGames.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No games found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(games, key = { it.game_title }) { game ->
                            val gameSelectedText = stringResource(R.string.console_game_selected, game.game_title)
                            ConsoleGameItem(
                                game = game,
                                selected = consoleSelectedGameTitle == game.game_title,
                                onClick = {
                                    consoleSelectedGameTitle = game.game_title
                                    val startTs = if (consoleEnableTimestamps) System.currentTimeMillis().toString() else ""
                                    val endTs = if (consoleEnableTimestamps) (System.currentTimeMillis() + 3600000L).toString() else ""
                                    scope.launch {
                                        snackBarHostState.showSnackbar(gameSelectedText)
                                    }
                                    applyConsoleGame(
                                        game = game,
                                        startTs = startTs,
                                        endTs = endTs
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.size(24.dp))
            }

        }

    }

    // Editor dialog for the presets. It lives outside the LazyColumn so it stays
    // open while the list scrolls underneath it.
    val presetToEdit = consolePresetBeingEdited
    if (consolePresetEditorOpen && presetToEdit != null) {
        // A preset can only be deleted once it has been saved. A brand new one
        // is simply dismissed with Cancel.
        val isSavedCustomPreset =
            !presetToEdit.builtIn && consoleCustomPresets.any { it.id == presetToEdit.id }
        ConsolePresetEditorDialog(
            initial = presetToEdit,
            onDismiss = { consolePresetEditorOpen = false },
            onSave = { saved ->
                ConsolePresetStore.upsert(saved)
                consoleCustomPresets = ConsolePresetStore.custom()
                consolePresetEditorOpen = false
                // Always apply when saving — whether creating a new preset or
                // editing an existing one, the user just confirmed this preset
                // is what they want on Discord.
                applyConsolePreset(saved, saved.name)
                consoleSelectedPresetId = saved.id
            },
            onDelete = if (!isSavedCustomPreset) null else ({
                ConsolePresetStore.delete(presetToEdit.id)
                consoleCustomPresets = ConsolePresetStore.custom()
                consolePresetEditorOpen = false
                // Clear the selection if we deleted the currently selected
                // preset, so the UI doesn't point at a missing preset.
                if (consoleSelectedPresetId == presetToEdit.id) {
                    consoleSelectedPresetId = ""
                }
            }),
            pickLargeImage = { pickLargeCoverArt.launch(it) },
            pickSmallImage = { pickSmallCoverArt.launch(it) },
        )
    }

}



@Preview

@Composable

fun CustomRpcPreview() {

    CustomRPC(onBackPressed = {})

}

/**
 * A row in the Console RPC game list.
 *
 * Shows the game title and platform, marks the selected game, and displays cover art.
 */
@Composable

fun ConsoleGameItem(
    game: com.my.kizzy.domain.model.Game,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface

    androidx.compose.material3.ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            ) {
                coil3.compose.AsyncImage(
                    model = game.large_image,
                    contentDescription = game.game_title,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colorScheme.primary)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(id = R.string.selected),
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp),
                        tint = colorScheme.onPrimary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.material3.Text(
                    text = game.game_title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.compose.material3.Text(
                    text = game.platform,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A row in the Console RPC preset list.
 *
 * Shows the platform name and the Discord platform it reports, marks the
 * selected preset, and offers an edit action for the presets the user created.
 */
@Composable

fun ConsolePresetChip(
    preset: ConsolePreset,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {

    val colorScheme = MaterialTheme.colorScheme
    val contentColor = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
    val name = if (preset.nameRes != 0) stringResource(preset.nameRes) else preset.name

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(
                if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = consolePresetIcon(preset),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = preset.platform,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    colorScheme.onSurfaceVariant
                }
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(id = R.string.console_preset_edit),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Converts a cover art path/URL to a previewable HTTP(S) URL for AsyncImage.
 *
 * Attachment paths (e.g. "attachments/123/456.png") and external paths
 * (e.g. "external/123/456.png") are not valid HTTP URLs, so they are
 * converted to "https://media.discordapp.net/...". The "mp:" prefix is
 * stripped first. Plain http(s) URLs are returned as-is.
 */
private fun coverArtPreviewUrl(url: String?): String? {
    val trimmed = url?.trim() ?: return null
    val withoutMp = trimmed.removePrefix("mp:")
    return when {
        withoutMp.startsWith("attachments") || withoutMp.startsWith("external") ->
            "https://media.discordapp.net/$withoutMp"
        else -> withoutMp
    }
}
