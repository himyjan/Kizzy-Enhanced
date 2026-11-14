package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.RpcField
import com.my.kizzy.ui.components.SettingItem
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.dialog.SingleChoiceItem
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcSettings(onBackPressed: () -> Boolean) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    var isLowResIconsEnabled by remember { mutableStateOf(Prefs[Prefs.RPC_USE_LOW_RES_ICON, false]) }
    var useImgur by remember { mutableStateOf(Prefs[Prefs.USE_IMGUR, false]) }
    var configsDir by remember { mutableStateOf(Prefs[Prefs.CONFIGS_DIRECTORY, ""]) }
    var showDirConfigDialog by remember { mutableStateOf(false) }
    var connectionMethod by remember {
        mutableStateOf(Prefs[Prefs.RPC_CONNECTION_METHOD, Prefs.RPC_CONNECTION_METHOD_GATEWAY])
    }
    var showConnectionMethodDialog by remember { mutableStateOf(false) }
    var oauthError by remember { mutableStateOf("") }

    val discordAuth = remember { DiscordAuth(context, scope) }

    var useButtonConfigs by remember { mutableStateOf(Prefs[Prefs.USE_RPC_BUTTONS, false]) }
    var useYoutubeButton by remember { mutableStateOf(Prefs[Prefs.MEDIA_RPC_MEDIA_BUTTON, false]) }
    var linkToArtistChannel by remember { mutableStateOf(Prefs[Prefs.MEDIA_RPC_ARTIST_LINK, false]) }
    var showButtonsConfigDialog by remember { mutableStateOf(false) }
    var rpcButtons by remember {
        mutableStateOf(Json.decodeFromString<RpcButtons>(Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]))
    }
    var customActivityStatus by remember {
        mutableStateOf(Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"])
    }
    var customActivityType by remember {
        mutableStateOf(Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, 0].toString())
    }
    var showActivityTypeDialog by remember { mutableStateOf(false) }
    var showActivityStatusDialog by remember { mutableStateOf(false) }
    var showApplicationIdDialog by remember { mutableStateOf(false) }
    var showImgurClientIdDialog by remember { mutableStateOf(false) }
    var setLastRunRpcConfigOption by remember {
        mutableStateOf(Prefs[Prefs.APPLY_FIELDS_FROM_LAST_RUN_RPC, false])
    }
    var customApplicationId by remember { mutableStateOf(Prefs[Prefs.CUSTOM_ACTIVITY_APPLICATION_ID, ""]) }
    var imgurClientId by remember { mutableStateOf(Prefs[Prefs.IMGUR_CLIENT_ID, Constants.IMGUR_CLIENT_ID]) }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        LargeTopAppBar(title = {
            Text(
                text = stringResource(id = R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
            )
        }, navigationIcon = { BackButton { onBackPressed() } })
    }) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item(key = "general_settings") {
                Subtitle(text = stringResource(R.string.general_settings))
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                item(key = "configs_directory") {
                    SettingItem(
                        title = stringResource(id = R.string.configs_directory),
                        description = configsDir.ifEmpty { stringResource(id = R.string.custom_rpc_directory) },
                        icon = Icons.Default.Storage,
                    ) {
                        showDirConfigDialog = true
                    }
                }
            }
            item(key = "connection_method") {
                val currentMethod = if (connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH) "OAuth2" else "Gateway"
                SettingItem(
                    title = stringResource(id = R.string.rpc_connection_method),
                    description = stringResource(id = R.string.rpc_connection_method_desc) + "  •  Currently using: $currentMethod",
                    icon = Icons.Default.OpenInNew,
                ) {
                    showConnectionMethodDialog = true
                }
            }
            item(key = "oauth_authorize") {
                val oauthStatus = if (discordAuth.hasValidToken()) "Authorized" else "Not authorized"
                AnimatedVisibility(visible = connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH) {
                    SettingItem(
                        title = stringResource(id = R.string.discord_oauth_authorize),
                        description = stringResource(id = R.string.discord_oauth_authorize_desc)
                            + if (oauthError.isNotEmpty()) "  •  Error: $oauthError" else "  •  $oauthStatus",
                        icon = Icons.Default.Login,
                    ) {
                        oauthError = ""
                        scope.launch {
                            try {
                                val result = discordAuth.authorize(activity)
                                Log.d("RpcSettings", "OAuth authorization successful: ${result.accessToken}")
                                oauthError = ""
                                Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH
                                connectionMethod = Prefs.RPC_CONNECTION_METHOD_OAUTH
                            } catch (e: Exception) {
                                oauthError = e.message ?: e.javaClass.simpleName
                                Log.e("RpcSettings", "OAuth authorization failed", e)
                            }
                        }
                    }
                }
            }
            item(key = "use_custom_buttons") {
                PreferenceSwitch(
                    title = stringResource(id = R.string.use_custom_buttons),
                    description = stringResource(id = R.string.use_custom_buttons_desc),
                    icon = Icons.Default.Tune,
                    isChecked = useButtonConfigs,
                ) {
                    useButtonConfigs = !useButtonConfigs
                    Prefs[Prefs.USE_RPC_BUTTONS] = useButtonConfigs
                }
            }
            item(key = "use_youtube_button") {
                PreferenceSwitch(
                    title = stringResource(id = R.string.use_youtube_button),
                    description = stringResource(id = R.string.use_youtube_button_desc),
                    icon = Icons.AutoMirrored.Default.OpenInNew,
                    isChecked = useYoutubeButton,
                ) {
                    useYoutubeButton = !useYoutubeButton
                    Prefs[Prefs.MEDIA_RPC_MEDIA_BUTTON] = useYoutubeButton
                }
            }
            item(key = "link_to_artist_channel") {
                PreferenceSwitch(
                    title = stringResource(id = R.string.link_to_artist_channel),
                    description = stringResource(id = R.string.link_to_artist_channel_desc),
                    icon = Icons.Outlined.Person,
                    isChecked = linkToArtistChannel,
                ) {
                    linkToArtistChannel = !linkToArtistChannel
                    Prefs[Prefs.MEDIA_RPC_ARTIST_LINK] = linkToArtistChannel
                }
            }
            item(key = "rpc_buttons") {
                AnimatedVisibility(visible = useButtonConfigs) {
                    SettingItem(
                        title = stringResource(R.string.rpc_buttons),
                        description = stringResource(id = R.string.rpc_settings_button_configs),
                        icon = Icons.Outlined.SmartButton
                    ) {
                        showButtonsConfigDialog = true
                    }
                }
            }
            item(key = "custom_activity_type") {
                SettingItem(
                    title = stringResource(id = R.string.custom_activity_type),
                    description = stringResource(id = R.string.custom_activity_type_desc),
                    icon = Icons.Outlined.Code
                ) {
                    showActivityTypeDialog = true
                }

            }
            item(key = "custom_activity_status") {
                SettingItem(
                    title = stringResource(id = R.string.custom_activity_status),
                    description = stringResource(id = R.string.custom_activity_status_desc),
                    icon = Icons.Outlined.DoNotDisturbOn
                ) {
                    showActivityStatusDialog = true
                }
            }
            item(key = "set_previous_run_config") {
                PreferenceSwitch(
                    title = stringResource(R.string.set_previous_run_config_in_custom_rpc),
                    description = stringResource(R.string.set_previous_run_config_in_custom_rpc_desc),
                    icon = Icons.Default.Cached,
                    isChecked = setLastRunRpcConfigOption
                ) {
                    setLastRunRpcConfigOption = !setLastRunRpcConfigOption
                    Prefs[Prefs.APPLY_FIELDS_FROM_LAST_RUN_RPC] = setLastRunRpcConfigOption
                }
            }
            item(key = "custom_application_id") {
                SettingItem(
                    title = stringResource(id = R.string.custom_application_id),
                    description = stringResource(id = R.string.custom_application_id_desc),
                    icon = Icons.Outlined.Pin
                ) {
                    showApplicationIdDialog = true
                }
            }
            item(key = "advance_settings_subtitle") {
                Subtitle(text = stringResource(id = R.string.advance_settings))
            }
            item(key = "use_imgur") {
                PreferenceSwitch(
                    title = stringResource(id = R.string.use_imgur),
                    description = stringResource(id = R.string.use_imgur_desc),
                    icon = Icons.Outlined.Image,
                    isChecked = useImgur
                ) {
                    useImgur = !useImgur
                    Prefs[Prefs.USE_IMGUR] = useImgur
                }
            }
            item(key = "imgur_client_id_setting") {
                AnimatedVisibility(visible = useImgur) {
                    SettingItem(
                        title = stringResource(id = R.string.set_imgur_client_id),
                        description = stringResource(id = R.string.set_imgur_client_id_desc),
                        icon = Icons.Outlined.Code
                    ) {
                        showImgurClientIdDialog = true
                    }
                }
            }
            item(key = "use_low_res_icon") {
                PreferenceSwitch(
                    title = stringResource(id = R.string.use_low_res_icon),
                    description = stringResource(id = R.string.use_low_res_icon_desc),
                    icon = Icons.Outlined.HighQuality,
                    isChecked = isLowResIconsEnabled,
                ) {
                    isLowResIconsEnabled = !isLowResIconsEnabled
                    Prefs[Prefs.RPC_USE_LOW_RES_ICON] = isLowResIconsEnabled
                }
            }
            item(key = "delete_saved_icon_urls") {
                SettingItem(
                    title = stringResource(id = R.string.delete_saved_icon_urls),
                    description = stringResource(id = R.string.delete_saved_icon_urls_desc),
                    icon = Icons.Outlined.DeleteForever
                ) {
                    Prefs.remove(Prefs.SAVED_IMAGES)
                    Prefs.remove(Prefs.SAVED_ARTWORK)
                    Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (showDirConfigDialog) {
            AlertDialog(
                onDismissRequest = { showDirConfigDialog = false },
                confirmButton = {},
                icon = {
                    Icon(
                        imageVector = Icons.Default.Storage, contentDescription = null
                    )
                },
                title = { Text(stringResource(R.string.select_directory)) },
                text = {
                    Column {
                        SingleChoiceItem(
                            text = Constants.APP_DIRECTORY,
                            selected = configsDir == Constants.APP_DIRECTORY
                        ) {
                            configsDir = Constants.APP_DIRECTORY
                            Prefs[Prefs.CONFIGS_DIRECTORY] = configsDir
                            showDirConfigDialog = false
                        }
                        SingleChoiceItem(
                            text = Constants.DOWNLOADS_DIRECTORY,
                            selected = configsDir == Constants.DOWNLOADS_DIRECTORY
                        ) {
                            configsDir = Constants.DOWNLOADS_DIRECTORY
                            Prefs[Prefs.CONFIGS_DIRECTORY] = configsDir
                            showDirConfigDialog = false
                        }
                    }
                })
        }
        if (showConnectionMethodDialog) {
            AlertDialog(
                onDismissRequest = { showConnectionMethodDialog = false },
                confirmButton = {},
                title = { Text(stringResource(R.string.rpc_connection_method)) },
                text = {
                    Column {
                        SingleChoiceItem(
                            text = stringResource(R.string.rpc_connection_method_gateway),
                            selected = connectionMethod == Prefs.RPC_CONNECTION_METHOD_GATEWAY
                        ) {
                            connectionMethod = Prefs.RPC_CONNECTION_METHOD_GATEWAY
                            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_GATEWAY
                            showConnectionMethodDialog = false
                        }
                        SingleChoiceItem(
                            text = stringResource(R.string.rpc_connection_method_oauth),
                            selected = connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH
                        ) {
                            connectionMethod = Prefs.RPC_CONNECTION_METHOD_OAUTH
                            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH
                            showConnectionMethodDialog = false
                        }
                    }
                }
            )
        }
        if (showButtonsConfigDialog) {
            AlertDialog(
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(20.dp),
                onDismissRequest = { showButtonsConfigDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        Prefs[Prefs.RPC_BUTTONS_DATA] = Json.encodeToString(rpcButtons)
                        showButtonsConfigDialog = false
                    }
                    ) {
                        Text("Save")
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Storage, contentDescription = null
                    )
                },
                title = { Text(stringResource(R.string.enter_details)) },
                text = {
                    Column {
                        RpcField(
                            value = rpcButtons.button1,
                            label = R.string.activity_button1_text,
                            isError = rpcButtons.button1.length >= Constants.MAX_ALLOWED_CHARACTER_LENGTH,
                            errorMessage = stringResource(R.string.activity_button_max_character)
                        ) {
                            rpcButtons = rpcButtons.copy(button1 = it)
                        }
                        AnimatedVisibility(visible = rpcButtons.button1.isNotEmpty()) {
                            RpcField(
                                value = rpcButtons.button1Url,
                                label = R.string.activity_button1_url
                            ) {
                                rpcButtons = rpcButtons.copy(button1Url = it)
                            }
                        }
                        RpcField(
                            value = rpcButtons.button2,
                            label = R.string.activity_button2_text,
                            isError = rpcButtons.button2.length >= Constants.MAX_ALLOWED_CHARACTER_LENGTH,
                            errorMessage = stringResource(R.string.activity_button_max_character)
                        ) {
                            rpcButtons = rpcButtons.copy(button2 = it)
                        }
                        AnimatedVisibility(visible = rpcButtons.button2.isNotEmpty()) {
                            RpcField(
                                value = rpcButtons.button2Url,
                                label = R.string.activity_button2_url
                            ) {
                                rpcButtons = rpcButtons.copy(button2Url = it)
                            }
                        }
                    }
                })
        }

        if (showActivityTypeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showActivityTypeDialog = false
                },
                text = {
                    var activityTypeisExpanded by remember {
                        mutableStateOf(false)
                    }
                    val icon =
                        if (activityTypeisExpanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        }
                    Column {
                        RpcField(
                            value = customActivityType,
                            label = R.string.activity_type,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.clickable {
                                        activityTypeisExpanded = !activityTypeisExpanded
                                    })
                            }) {
                            customActivityType = it
                        }
                        DropdownMenu(
                            expanded = activityTypeisExpanded, onDismissRequest = {
                                activityTypeisExpanded = !activityTypeisExpanded
                            }, modifier = Modifier.fillMaxWidth()
                        ) {
                            Constants.ACTIVITY_TYPE.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = label)
                                    },
                                    onClick = {
                                        customActivityType = value.toString()
                                        activityTypeisExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (customActivityType.toIntOrNull() in 0..5) {
                            Prefs[Prefs.CUSTOM_ACTIVITY_TYPE] = customActivityType.toInt()
                            showActivityTypeDialog = false
                        }
                    }) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            )
        }

        if (showActivityStatusDialog) {
            AlertDialog(
                onDismissRequest = {
                    showActivityStatusDialog = false
                },
                confirmButton = {},
                text = {
                    Column {
                        Constants.ACTIVITY_STATUS.forEach { (resId, value) ->
                            SingleChoiceItem(
                                text = stringResource(resId),
                                selected = value == customActivityStatus
                            ) {
                                customActivityStatus = value
                                Prefs[Prefs.CUSTOM_ACTIVITY_STATUS] = value
                                showActivityStatusDialog = false
                            }
                        }
                    }
                }
            )
        }

        if (showApplicationIdDialog) {
            AlertDialog(
                onDismissRequest = {
                    showApplicationIdDialog = false
                },
                title = { Text("Application ID") },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        RpcField(
                            value = customApplicationId,
                            label = R.string.application_id,
                            isError = customApplicationId.length !in Constants.MAX_APPLICATION_ID_LENGTH_RANGE || !customApplicationId.all { it.isDigit() },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { newText ->
                                if (newText.length <= Constants.MAX_APPLICATION_ID_LENGTH_RANGE.last && newText.all { it.isDigit() }) {
                                    customApplicationId = newText
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (customApplicationId.length !in Constants.MAX_APPLICATION_ID_LENGTH_RANGE || !customApplicationId.all { it.isDigit() }) {
                                Toast.makeText(
                                    context,
                                    "Please enter a valid Application ID",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Prefs[Prefs.CUSTOM_ACTIVITY_APPLICATION_ID] = customApplicationId
                                showApplicationIdDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }

        if (showImgurClientIdDialog) {
            AlertDialog(
                onDismissRequest = {
                    imgurClientId = Prefs[Prefs.IMGUR_CLIENT_ID, Constants.IMGUR_CLIENT_ID]
                    showImgurClientIdDialog = false
                },
                title = { Text(stringResource(R.string.set_imgur_client_id)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        RpcField(
                            value = imgurClientId,
                            label = R.string.imgur_client_id,
                            onValueChange = { newText ->
                                imgurClientId = newText
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (imgurClientId.isBlank()) {
                                imgurClientId = Constants.IMGUR_CLIENT_ID
                            }
                            Prefs[Prefs.IMGUR_CLIENT_ID] = imgurClientId
                            showImgurClientIdDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }
    }
}