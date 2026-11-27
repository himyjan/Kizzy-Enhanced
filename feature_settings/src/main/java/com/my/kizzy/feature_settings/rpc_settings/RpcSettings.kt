package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.my.kizzy.data.rpc.Constants
import com.my.kizzy.domain.model.rpc.RpcButtons
import com.my.kizzy.feature_rpc_base.services.lastfm.LastFM
import com.my.kizzy.feature_rpc_base.services.lastfm.LibreFM
import com.my.kizzy.preference.OAuthApp
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
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

/**
 * RPC settings screen managing Discord connection modes (OAuth2 / Gateway),
 * multi-instance OAuth applications, custom buttons/activity presets, cover art,
 * image hosting, and Last.fm / Libre.fm / ListenBrainz scrobbling options[cite: 14].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpcSettings(
    onBackPressed: () -> Unit,
    authTabLauncher: ActivityResultLauncher<Intent>? = null,
    onBindAuthTabCallback: (((Int, Intent?) -> Unit) -> Unit)? = null,
    navigateToCustomRpcBoardScreen: (() -> Unit)? = null,
    navigateToLeaderboardScreen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // General and storage preference state
    var isLowResIconsEnabled by remember { mutableStateOf(Prefs[Prefs.RPC_USE_LOW_RES_ICON, false]) }
    var useImgur by remember { mutableStateOf(Prefs[Prefs.USE_IMGUR, false]) }
    var configsDir by remember { mutableStateOf(Prefs[Prefs.CONFIGS_DIRECTORY, ""]) }
    var showDirConfigDialog by remember { mutableStateOf(false) }

    // Discord connection & OAuth2 state
    var showConnectionMethodDialog by remember { mutableStateOf(false) }
    var oauth2Error by remember { mutableStateOf("") }
    var discordOAuth2ClientId by remember {
        mutableStateOf(Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""])
    }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var editingClientId22 by remember { mutableStateOf("") }
    var showClientIdDialog2 by remember { mutableStateOf(false) }
    var editingClientId2 by remember { mutableStateOf("") }
    var showClientIdDialog3 by remember { mutableStateOf(false) }
    var editingClientId3 by remember { mutableStateOf("") }

    // Multi-app OAuth configuration state for parallel RPC instances
    var showOAuthAppDialog by remember { mutableStateOf(false) }
    var editingOAuthApp by remember { mutableStateOf<OAuthApp?>(null) }
    var appLabelInput by remember { mutableStateOf("") }
    var appClientIdInput by remember { mutableStateOf("") }
    var appTokenInput by remember { mutableStateOf("") }
    var showUserLogin by remember { mutableStateOf(false) }
    var gatewayTokenMode by remember { mutableStateOf(false) }

    // Observe DataStore state so UI reflects persisted values after async priming
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap())
    var connectionMethod by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.RPC_CONNECTION_METHOD] as? String ?: Prefs.RPC_CONNECTION_METHOD_OAUTH2)
    }
    val savedOAuthApps = remember(prefsMap) { Prefs.getOAuthApps() }

    // OAuth2 / AuthTab helper bound to the current screen lifecycle
    val discordAuth = remember(context, scope, authTabLauncher) {
        DiscordOAuth2Auth(
            context = context,
            scope = scope,
            authTabLauncher = authTabLauncher,
        )
    }

    // Register and clear the active OAuth callback for this screen
    DisposableEffect(discordAuth, onBindAuthTabCallback) {
        onBindAuthTabCallback?.invoke { resultCode, callbackIntent ->
            discordAuth.onActivityResult(resultCode, callbackIntent)
        }
        onDispose {
            onBindAuthTabCallback?.invoke { _, _ -> }
        }
    }

    // Buttons, presence appearance, and application ID settings
    var useButtonConfigs by remember { mutableStateOf(Prefs[Prefs.USE_RPC_BUTTONS, false]) }
    var useYoutubeButton by remember { mutableStateOf(Prefs[Prefs.MEDIA_RPC_MEDIA_BUTTON, false]) }
    var linkToArtistChannel by remember { mutableStateOf(Prefs[Prefs.MEDIA_RPC_ARTIST_LINK, false]) }
    var showButtonsConfigDialog by remember { mutableStateOf(false) }
    var rpcButtons by remember {
        mutableStateOf<RpcButtons>(Json.decodeFromString(Prefs[Prefs.RPC_BUTTONS_DATA, "{}"]))
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

    // Last.fm scrobbling settings
    var showLastFmDialog by remember { mutableStateOf(false) }
    var lastfmApiKey by remember {
        mutableStateOf(Prefs[Prefs.LASTFM_API_KEY, com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_API_KEY])
    }
    var lastfmSecret by remember {
        mutableStateOf(Prefs[Prefs.LASTFM_SECRET, com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_SECRET])
    }
    var lastfmUsername by remember { mutableStateOf(Prefs[Prefs.LASTFM_USERNAME, ""]) }
    var lastfmPassword by remember { mutableStateOf(Prefs[Prefs.LASTFM_PASSWORD, ""]) }
    var lastfmPendingToken by remember { mutableStateOf<String?>(null) }
    var lastfmPasswordVisible by remember { mutableStateOf(false) }
    var lastfmShowTimestamps by remember { mutableStateOf(Prefs[Prefs.LASTFM_ENABLE_TIMESTAMPS, true]) }
    var lastfmShowCoverArt by remember { mutableStateOf(Prefs[Prefs.LASTFM_SHOW_COVER_ART, true]) }
    var lastfmShowPartyCurrent by remember { mutableStateOf(Prefs[Prefs.LASTFM_SHOW_PARTY_CURRENT, true]) }
    var lastfmShowPartyMax by remember { mutableStateOf(Prefs[Prefs.LASTFM_SHOW_PARTY_MAX, false]) }
    var isLastFmEnabled by remember { mutableStateOf(Prefs[Prefs.LASTFM_ENABLED, false]) }

    // Libre.fm scrobbling settings
    var showLibreFmDialog by remember { mutableStateOf(false) }
    var librefmApiKey by remember { mutableStateOf(Prefs[Prefs.LIBREFM_API_KEY, ""]) }
    var librefmSecret by remember { mutableStateOf(Prefs[Prefs.LIBREFM_SECRET, ""]) }
    var librefmUsername by remember { mutableStateOf(Prefs[Prefs.LIBREFM_USERNAME, ""]) }
    var librefmPassword by remember { mutableStateOf(Prefs[Prefs.LIBREFM_PASSWORD, ""]) }
    var librefmPasswordVisible by remember { mutableStateOf(false) }
    var isLibreFmEnabled by remember { mutableStateOf(Prefs[Prefs.LIBREFM_ENABLED, false]) }

    // ListenBrainz scrobbling settings
    var showListenBrainzDialog by remember { mutableStateOf(false) }
    var listenbrainzUserToken by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]) }
    var listenbrainzEnabled by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_ENABLED, false]) }
    var listenbrainzShowTimestamps by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS, true]) }
    var listenbrainzShowCoverArt by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_COVER_ART, true]) }
    var listenbrainzShowPartyCurrent by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT, true]) }
    var listenbrainzShowPartyMax by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_MAX, false]) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackPressed() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
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
                val currentMethod = if (connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH2) "OAuth2" else "Gateway"
                SettingItem(
                    title = stringResource(id = R.string.rpc_connection_method),
                    description = stringResource(id = R.string.rpc_connection_method_desc) + "  •  Currently using: $currentMethod",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                ) {
                    showConnectionMethodDialog = true
                }
            }
            item(key = "oauth2_client_id") {
                SettingItem(
                    title = stringResource(id = R.string.discord_oauth2_client_id),
                    description = stringResource(id = R.string.discord_oauth2_client_id_desc) + (if (discordOAuth2ClientId.isNotEmpty()) "  •  $discordOAuth2ClientId" else "  •  (optional)"),
                    icon = Icons.Outlined.Person,
                ) {
                    discordOAuth2ClientId = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""]
                    editingClientId22 = discordOAuth2ClientId
                    showClientIdDialog = true
                }
            }
            item(key = "oauth2_authorize") {
                val oauthStatus = when {
                    discordAuth.hasValidToken() -> "Authorized (OAuth2 + account token)"
                    Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN, ""].isNotEmpty() -> "Account token only"
                    else -> "Not authorized"
                }
                AnimatedVisibility(visible = connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH2) {
                    SettingItem(
                        title = stringResource(id = R.string.discord_oauth2_authorize),
                        description = stringResource(id = R.string.discord_oauth2_authorize_desc)
                                + if (oauth2Error.isNotEmpty()) "  •  Error: $oauth2Error" else "  •  $oauthStatus",
                        icon = Icons.AutoMirrored.Filled.Login,
                    ) {
                        oauth2Error = ""
                        scope.launch {
                            try {
                                // Run OAuth2 authorization first, then prompt for account token capture
                                val result = discordAuth.authorize(activity)
                                Log.d(
                                    "RpcSettings",
                                    "OAuth2 authorized: bearer_token len=${result.accessToken.length}, refresh_token len=${result.refreshToken.length}, expires_in=${result.expiresInSec}s"
                                )
                                gatewayTokenMode = true
                                showUserLogin = true
                                oauth2Error = ""
                                Toast.makeText(
                                    context,
                                    "Discord OAuth2 authorized. Now log in to capture your account token.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } catch (e: DiscordOAuth2Auth.DiscordOAuth2Exception) {
                                oauth2Error = e.message ?: "Authorization failed"
                                Log.e("RpcSettings", "OAuth2 authorization failed", e)
                            } catch (e: Exception) {
                                oauth2Error = "Unexpected error: ${e.message}"
                                Log.e("RpcSettings", "OAuth2 authorization unexpected failure", e)
                            }
                        }
                    }
                }
            }
            item(key = "multi_rpc_header") {
                Subtitle(text = "RPC Applications (${savedOAuthApps.size})")
            }
            items(items = savedOAuthApps, key = { it.id }) { app ->
                SettingItem(
                    title = app.label,
                    description = "App ID ${app.clientId}" +
                            if (app.token.isBlank()) "  •  not authorized" else "  •  authorized",
                    icon = Icons.Outlined.Person,
                ) {
                    editingOAuthApp = app
                    appLabelInput = app.label
                    appClientIdInput = app.clientId
                    appTokenInput = app.token
                    showOAuthAppDialog = true
                }
            }
            item(key = "oauth2_apps_add") {
                SettingItem(
                    title = "Add RPC Application",
                    description = "Add another Discord application with its own token",
                    icon = Icons.Default.Add,
                ) {
                    editingOAuthApp = null
                    appLabelInput = ""
                    appClientIdInput = ""
                    appTokenInput = ""
                    showOAuthAppDialog = true
                }
            }

            // Custom RPC Board section
            item(key = "custom_rpc_board_section") {
                Subtitle(text = stringResource(id = R.string.custom_rpc_board))
            }
            item(key = "custom_rpc_board_open") {
                SettingItem(
                    title = stringResource(id = R.string.custom_rpc_board_open),
                    description = stringResource(id = R.string.custom_rpc_board_open_desc),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_custom_rpc),
                ) {
                    navigateToCustomRpcBoardScreen?.invoke()
                }
            }

            // Discord Leaderboard section
            item(key = "discord_leaderboard_section") {
                Subtitle(text = stringResource(id = R.string.discord_leaderboard))
            }
            item(key = "discord_leaderboard_open") {
                SettingItem(
                    title = stringResource(id = R.string.discord_leaderboard),
                    description = stringResource(id = R.string.discord_leaderboard_desc),
                    icon = Icons.Outlined.List,
                ) {
                    navigateToLeaderboardScreen?.invoke()
                }
            }

            item(key = "rpc_hide_cover_art") {
                var hideCoverArt by remember(prefsMap) {
                    mutableStateOf(prefsMap[Prefs.RPC_HIDE_COVER_ART] as? Boolean ?: false)
                }
                PreferenceSwitch(
                    title = stringResource(id = R.string.rpc_hide_cover_art),
                    description = stringResource(id = R.string.rpc_hide_cover_art_desc),
                    icon = Icons.Outlined.RemoveRedEye,
                    isChecked = hideCoverArt,
                ) {
                    hideCoverArt = !hideCoverArt
                    Prefs[Prefs.RPC_HIDE_COVER_ART] = hideCoverArt
                }
            }
            item(key = "rpc_default_cover_art_large") {
                var defaultLargeCoverArt by remember { mutableStateOf(Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE, ""]) }

                val pickLargeCoverArt = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    uri?.let {
                        defaultLargeCoverArt = it.toString()
                        Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE] = defaultLargeCoverArt
                    }
                }

                RpcField(
                    value = defaultLargeCoverArt,
                    label = R.string.rpc_default_cover_art_large,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                pickLargeCoverArt.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*"))
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Pick image"
                            )
                        }
                    }
                ) {
                    defaultLargeCoverArt = it
                    Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE] = defaultLargeCoverArt
                }
            }
            item(key = "rpc_default_cover_art_small") {
                var defaultSmallCoverArt by remember { mutableStateOf(Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL, ""]) }

                val pickSmallCoverArt = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    uri?.let {
                        defaultSmallCoverArt = it.toString()
                        Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL] = defaultSmallCoverArt
                    }
                }

                RpcField(
                    value = defaultSmallCoverArt,
                    label = R.string.rpc_default_cover_art_small,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                pickSmallCoverArt.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*"))
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Pick image"
                            )
                        }
                    }
                ) {
                    defaultSmallCoverArt = it
                    Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL] = defaultSmallCoverArt
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
                    Prefs.removeAsync(Prefs.SAVED_IMAGES)
                    Prefs.removeAsync(Prefs.SAVED_ARTWORK)
                    Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                }
            }
            item(key = "lastfm_settings") {
                val sessionKey = Prefs[Prefs.LASTFM_SESSION_KEY, ""]
                val lastFmAuthStatus = if (sessionKey.isNotBlank()) "Authenticated" else "Not authenticated"
                val lastFmStatus = when {
                    isLastFmEnabled -> "Enabled  •  $lastFmAuthStatus"
                    else -> "Disabled"
                }
                SettingItem(
                    title = stringResource(id = R.string.lastfm_settings_title),
                    description = stringResource(id = R.string.lastfm_enabled) + "  •  $lastFmStatus",
                ) {
                    lastfmUsername = Prefs[Prefs.LASTFM_USERNAME, ""]
                    lastfmPassword = Prefs[Prefs.LASTFM_PASSWORD, ""]
                    lastfmShowTimestamps = Prefs[Prefs.LASTFM_ENABLE_TIMESTAMPS, true]
                    lastfmShowCoverArt = Prefs[Prefs.LASTFM_SHOW_COVER_ART, true]
                    lastfmShowPartyCurrent = Prefs[Prefs.LASTFM_SHOW_PARTY_CURRENT, true]
                    lastfmShowPartyMax = Prefs[Prefs.LASTFM_SHOW_PARTY_MAX, false]
                    isLastFmEnabled = Prefs[Prefs.LASTFM_ENABLED, false]
                    showLastFmDialog = true
                }
            }
            item(key = "librefm_settings") {
                val sessionKey = Prefs[Prefs.LIBREFM_SESSION_KEY, ""]
                val libreFmAuthStatus = if (sessionKey.isNotBlank()) "Authenticated" else "Not authenticated"
                val libreFmStatus = if (isLibreFmEnabled) "Enabled  •  $libreFmAuthStatus" else "Disabled"
                SettingItem(
                    title = stringResource(id = R.string.librefm_settings_title),
                    description = stringResource(id = R.string.librefm_enabled) + "  •  $libreFmStatus",
                ) {
                    librefmApiKey = Prefs[Prefs.LIBREFM_API_KEY, ""]
                    librefmSecret = Prefs[Prefs.LIBREFM_SECRET, ""]
                    librefmUsername = Prefs[Prefs.LIBREFM_USERNAME, ""]
                    librefmPassword = Prefs[Prefs.LIBREFM_PASSWORD, ""]
                    isLibreFmEnabled = Prefs[Prefs.LIBREFM_ENABLED, false]
                    showLibreFmDialog = true
                }
            }
            item(key = "listenbrainz_settings") {
                val listenbrainzToken = Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]
                val listenbrainzAuthStatus = if (listenbrainzToken.isNotBlank()) "Authenticated" else "Not authenticated"
                val listenbrainzStatus = when {
                    listenbrainzEnabled -> "Enabled  •  $listenbrainzAuthStatus"
                    else -> "Disabled"
                }
                SettingItem(
                    title = stringResource(id = R.string.listenbrainz_settings_title),
                    description = stringResource(id = R.string.listenbrainz_enabled) + "  •  $listenbrainzStatus",
                ) {
                    listenbrainzUserToken = Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]
                    listenbrainzEnabled = Prefs[Prefs.LISTENBRAINZ_ENABLED, false]
                    listenbrainzShowTimestamps = Prefs[Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS, true]
                    listenbrainzShowCoverArt = Prefs[Prefs.LISTENBRAINZ_SHOW_COVER_ART, true]
                    listenbrainzShowPartyCurrent = Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT, true]
                    listenbrainzShowPartyMax = Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_MAX, false]
                    showListenBrainzDialog = true
                }
            }
        }

        // Configs directory selector dialog
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
                }
            )
        }

        // Connection method (Gateway vs OAuth2) picker dialog
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
                            gatewayTokenMode = true
                            showUserLogin = true
                            discordAuth.launchGatewayLogin(activity)
                        }
                        SingleChoiceItem(
                            text = stringResource(id = R.string.rpc_connection_method_oauth2),
                            selected = connectionMethod == Prefs.RPC_CONNECTION_METHOD_OAUTH2
                        ) {
                            connectionMethod = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                            Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                            showConnectionMethodDialog = false
                        }
                    }
                }
            )
        }

        // Custom RPC button labels and URLs dialog
        if (showButtonsConfigDialog) {
            AlertDialog(
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(20.dp),
                onDismissRequest = { showButtonsConfigDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
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
                }
            )
        }

        // Activity type selector dialog
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
                            expanded = activityTypeisExpanded,
                            onDismissRequest = {
                                activityTypeisExpanded = !activityTypeisExpanded
                            },
                            modifier = Modifier.fillMaxWidth()
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
                    TextButton(
                        onClick = {
                            if (customActivityType.toIntOrNull() in Constants.KNOWN_ACTIVITY_TYPES) {
                                Prefs[Prefs.CUSTOM_ACTIVITY_TYPE] = customActivityType.toInt()
                                showActivityTypeDialog = false
                            }
                        }
                    ) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            )
        }

        // Discord presence status selector dialog
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

        // Custom Discord Application ID dialog
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

        // Custom Imgur Client ID dialog
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

        // Primary OAuth2 Client ID dialog
        if (showClientIdDialog) {
            AlertDialog(
                onDismissRequest = { showClientIdDialog = false },
                title = { Text(stringResource(R.string.discord_oauth2_client_id)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        RpcField(
                            value = discordOAuth2ClientId,
                            label = R.string.discord_oauth2_client_id,
                            onValueChange = { newText ->
                                discordOAuth2ClientId = newText
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (discordOAuth2ClientId.isBlank()) {
                                discordOAuth2ClientId = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""]
                            } else {
                                Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID] = discordOAuth2ClientId
                            }
                            showClientIdDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }

        // Secondary OAuth2 Client ID dialog (#2)
        if (showClientIdDialog2) {
            AlertDialog(
                onDismissRequest = { showClientIdDialog2 = false },
                title = { Text(stringResource(R.string.discord_oauth2_client_id)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        RpcField(
                            value = editingClientId2,
                            label = R.string.discord_oauth2_client_id,
                            onValueChange = { newText ->
                                editingClientId2 = newText
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (editingClientId2.isBlank()) {
                                editingClientId2 = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2, ""]
                            } else {
                                Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_2] = editingClientId2
                            }
                            showClientIdDialog2 = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }

        // Tertiary OAuth2 Client ID dialog (#3)
        if (showClientIdDialog3) {
            AlertDialog(
                onDismissRequest = { showClientIdDialog3 = false },
                title = { Text(stringResource(R.string.discord_oauth2_client_id)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        RpcField(
                            value = editingClientId3,
                            label = R.string.discord_oauth2_client_id,
                            onValueChange = { newText ->
                                editingClientId3 = newText
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (editingClientId3.isBlank()) {
                                editingClientId3 = Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3, ""]
                            } else {
                                Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID_3] = editingClientId3
                            }
                            showClientIdDialog3 = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        }

        // Account / gateway token login dialog
        if (showUserLogin) {
            DiscordUserLoginDialog(
                token = "",
                onDismiss = { showUserLogin = false; gatewayTokenMode = false },
                onConfirm = { token ->
                    if (gatewayTokenMode) {
                        Prefs[Prefs.TOKEN] = token
                        Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_GATEWAY
                        connectionMethod = Prefs.RPC_CONNECTION_METHOD_GATEWAY
                        Toast.makeText(context, "Discord gateway account token saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = token
                        Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                        connectionMethod = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                        Toast.makeText(context, "Discord account connected", Toast.LENGTH_SHORT).show()
                    }
                    showUserLogin = false
                    oauth2Error = ""
                    Log.d("RpcSettings", "Account token saved (len=${token.length}, parts=${token.count { it == '.' } + 1})")
                },
            )
        }

        // Last.fm configuration and authentication dialog
        if (showLastFmDialog) {
            AlertDialog(
                onDismissRequest = { showLastFmDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Prefs[Prefs.LASTFM_API_KEY] = lastfmApiKey
                            Prefs[Prefs.LASTFM_SECRET] = lastfmSecret
                            Prefs[Prefs.LASTFM_USERNAME] = lastfmUsername
                            Prefs[Prefs.LASTFM_PASSWORD] = lastfmPassword
                            Prefs[Prefs.LASTFM_ENABLE_TIMESTAMPS] = lastfmShowTimestamps
                            Prefs[Prefs.LASTFM_SHOW_COVER_ART] = lastfmShowCoverArt
                            Prefs[Prefs.LASTFM_SHOW_PARTY_CURRENT] = lastfmShowPartyCurrent
                            Prefs[Prefs.LASTFM_SHOW_PARTY_MAX] = lastfmShowPartyMax
                            Prefs[Prefs.LASTFM_ENABLED] = isLastFmEnabled
                            showLastFmDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                title = { Text(stringResource(R.string.lastfm_settings_title)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceSwitch(
                            title = stringResource(id = R.string.lastfm_enabled),
                            isChecked = isLastFmEnabled,
                        ) {
                            isLastFmEnabled = !isLastFmEnabled
                        }

                        RpcField(
                            value = lastfmApiKey,
                            label = R.string.lastfm_api_key,
                        ) {
                            lastfmApiKey = it
                        }

                        RpcField(
                            value = lastfmSecret,
                            label = R.string.lastfm_secret,
                        ) {
                            lastfmSecret = it
                        }

                        RpcField(
                            value = lastfmUsername,
                            label = R.string.lastfm_username,
                        ) {
                            lastfmUsername = it
                        }

                        RpcField(
                            value = lastfmPassword,
                            label = R.string.lastfm_password,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            password = !lastfmPasswordVisible,
                            trailingIcon = {
                                IconButton(onClick = { lastfmPasswordVisible = !lastfmPasswordVisible }) {
                                    Icon(
                                        imageVector = Icons.Outlined.RemoveRedEye,
                                        contentDescription = stringResource(
                                            if (lastfmPasswordVisible) R.string.lastfm_hide_password else R.string.lastfm_show_password
                                        )
                                    )
                                }
                            },
                        ) {
                            lastfmPassword = it
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val lastFmSessionKey = Prefs[Prefs.LASTFM_SESSION_KEY, ""]
                            val isAuthenticated = lastFmSessionKey.isNotBlank()
                            Text(
                                text = stringResource(
                                    if (isAuthenticated) R.string.lastfm_authenticated else R.string.lastfm_not_authenticated
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAuthenticated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        Prefs[Prefs.LASTFM_SESSION_KEY] = ""
                                        Prefs[Prefs.LASTFM_PASSWORD] = ""
                                        Prefs[Prefs.LASTFM_USERNAME] = ""
                                        Prefs[Prefs.LASTFM_API_KEY] = com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_API_KEY
                                        Prefs[Prefs.LASTFM_SECRET] = com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_SECRET
                                        lastfmPassword = ""
                                        lastfmUsername = ""
                                        lastfmApiKey = com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_API_KEY
                                        lastfmSecret = com.my.kizzy.feature_rpc_base.services.lastfm.DEFAULT_LASTFM_SECRET
                                    }
                                }
                            ) {
                                Text(text = stringResource(R.string.lastfm_logout))
                            }
                            if (!isAuthenticated) {
                                Column {
                                    // Step 1: Request token from Last.fm and open browser authorization
                                    TextButton(
                                        onClick = {
                                            if (lastfmApiKey.isBlank() || lastfmSecret.isBlank()) {
                                                Toast.makeText(context, "Enter API key and secret first", Toast.LENGTH_SHORT).show()
                                            } else {
                                                scope.launch {
                                                    LastFM.init(lastfmApiKey, lastfmSecret)
                                                    val token = try {
                                                        LastFM.getToken()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Last.fm get token failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (token?.token?.isNotEmpty() == true) {
                                                        lastfmPendingToken = token.token
                                                        val authUrl = "https://www.last.fm/api/auth/?api_key=$lastfmApiKey&token=${token.token}"
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                                        Toast.makeText(context, "Authorize in browser, then click Get Session", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(text = stringResource(R.string.lastfm_get_token))
                                    }

                                    // Step 2: Exchange authorized token for a session key
                                    if (lastfmPendingToken != null) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    LastFM.init(lastfmApiKey, lastfmSecret)
                                                    val session = try {
                                                        LastFM.getSession(lastfmPendingToken!!)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Last.fm get session failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (session != null && session.key.isNotEmpty()) {
                                                        LastFM.sessionKey = session.key
                                                        Prefs[Prefs.LASTFM_SESSION_KEY] = session.key
                                                        lastfmPendingToken = null
                                                        Toast.makeText(context, "Logged in to Last.fm", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Last.fm login failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(text = stringResource(R.string.lastfm_get_session))
                                        }
                                    }

                                    // Direct username/password mobile session sign-in
                                    TextButton(
                                        onClick = {
                                            if (lastfmUsername.isBlank() || lastfmPassword.isBlank()) {
                                                Toast.makeText(context, "Enter username and password first", Toast.LENGTH_SHORT).show()
                                            } else {
                                                scope.launch {
                                                    LastFM.init(lastfmApiKey, lastfmSecret)
                                                    val session = try {
                                                        LastFM.getMobileSession(lastfmUsername, lastfmPassword)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Last.fm login failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (session != null && session.key.isNotEmpty()) {
                                                        Prefs[Prefs.LASTFM_SESSION_KEY] = session.key
                                                        Toast.makeText(context, "Logged in to Last.fm as ${session.name}", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Last.fm login failed", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(text = "Login with Username/Password")
                                    }
                                }
                            }
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.lastfm_show_timestamps),
                            isChecked = lastfmShowTimestamps,
                        ) {
                            lastfmShowTimestamps = !lastfmShowTimestamps
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.lastfm_show_cover_art),
                            isChecked = lastfmShowCoverArt,
                        ) {
                            lastfmShowCoverArt = !lastfmShowCoverArt
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.lastfm_show_party_current),
                            isChecked = lastfmShowPartyCurrent,
                        ) {
                            lastfmShowPartyCurrent = !lastfmShowPartyCurrent
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.lastfm_show_party_max),
                            isChecked = lastfmShowPartyMax,
                        ) {
                            lastfmShowPartyMax = !lastfmShowPartyMax
                        }
                    }
                },
            )
        }

        // Libre.fm configuration and authentication dialog
        if (showLibreFmDialog) {
            AlertDialog(
                onDismissRequest = { showLibreFmDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val credentialsChanged =
                                Prefs[Prefs.LIBREFM_API_KEY, ""] != librefmApiKey ||
                                        Prefs[Prefs.LIBREFM_SECRET, ""] != librefmSecret ||
                                        Prefs[Prefs.LIBREFM_USERNAME, ""] != librefmUsername ||
                                        Prefs[Prefs.LIBREFM_PASSWORD, ""] != librefmPassword
                            Prefs[Prefs.LIBREFM_API_KEY] = librefmApiKey
                            Prefs[Prefs.LIBREFM_SECRET] = librefmSecret
                            Prefs[Prefs.LIBREFM_USERNAME] = librefmUsername
                            Prefs[Prefs.LIBREFM_PASSWORD] = librefmPassword
                            Prefs[Prefs.LIBREFM_ENABLED] = isLibreFmEnabled
                            if (credentialsChanged) Prefs[Prefs.LIBREFM_SESSION_KEY] = ""
                            showLibreFmDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                title = { Text(stringResource(R.string.librefm_settings_title)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceSwitch(
                            title = stringResource(id = R.string.librefm_enabled),
                            isChecked = isLibreFmEnabled,
                        ) {
                            isLibreFmEnabled = !isLibreFmEnabled
                        }

                        RpcField(
                            value = librefmApiKey,
                            label = R.string.librefm_api_key,
                        ) {
                            librefmApiKey = it
                        }

                        RpcField(
                            value = librefmSecret,
                            label = R.string.librefm_secret,
                        ) {
                            librefmSecret = it
                        }

                        RpcField(
                            value = librefmUsername,
                            label = R.string.librefm_username,
                        ) {
                            librefmUsername = it
                        }

                        RpcField(
                            value = librefmPassword,
                            label = R.string.librefm_password,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            password = !librefmPasswordVisible,
                            trailingIcon = {
                                IconButton(onClick = { librefmPasswordVisible = !librefmPasswordVisible }) {
                                    Icon(
                                        imageVector = Icons.Outlined.RemoveRedEye,
                                        contentDescription = stringResource(
                                            if (librefmPasswordVisible) R.string.lastfm_hide_password else R.string.lastfm_show_password
                                        )
                                    )
                                }
                            },
                        ) {
                            librefmPassword = it
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val libreFmSessionKey = Prefs[Prefs.LIBREFM_SESSION_KEY, ""]
                            val isAuthenticated = libreFmSessionKey.isNotBlank()
                            Text(
                                text = stringResource(
                                    if (isAuthenticated) R.string.lastfm_authenticated else R.string.lastfm_not_authenticated
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAuthenticated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        Prefs[Prefs.LIBREFM_SESSION_KEY] = ""
                                        Prefs[Prefs.LIBREFM_PASSWORD] = ""
                                        Prefs[Prefs.LIBREFM_USERNAME] = ""
                                        librefmPassword = ""
                                        librefmUsername = ""
                                    }
                                }
                            ) {
                                Text(text = stringResource(R.string.lastfm_logout))
                            }
                            if (!isAuthenticated) {
                                TextButton(
                                    onClick = {
                                        if (librefmUsername.isBlank() || librefmPassword.isBlank()) {
                                            Toast.makeText(context, "Enter username and password first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                LibreFM.init(librefmApiKey, librefmSecret)
                                                val session = try {
                                                    LibreFM.getMobileSession(librefmUsername, librefmPassword)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Libre.fm login failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                    null
                                                }
                                                if (session != null && session.key.isNotEmpty()) {
                                                    Prefs[Prefs.LIBREFM_SESSION_KEY] = session.key
                                                    Toast.makeText(context, "Logged in to Libre.fm", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Libre.fm login failed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(text = stringResource(R.string.lastfm_login))
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.librefm_api_key_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                },
            )
        }

        // ListenBrainz configuration dialog
        if (showListenBrainzDialog) {
            AlertDialog(
                onDismissRequest = { showListenBrainzDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            Prefs[Prefs.LISTENBRAINZ_USER_TOKEN] = listenbrainzUserToken
                            Prefs[Prefs.LISTENBRAINZ_ENABLED] = listenbrainzEnabled
                            Prefs[Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS] = listenbrainzShowTimestamps
                            Prefs[Prefs.LISTENBRAINZ_SHOW_COVER_ART] = listenbrainzShowCoverArt
                            Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT] = listenbrainzShowPartyCurrent
                            Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_MAX] = listenbrainzShowPartyMax
                            showListenBrainzDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                title = { Text(stringResource(R.string.listenbrainz_settings_title)) },
                text = {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        PreferenceSwitch(
                            title = stringResource(id = R.string.listenbrainz_enabled),
                            isChecked = listenbrainzEnabled,
                        ) {
                            listenbrainzEnabled = !listenbrainzEnabled
                        }

                        RpcField(
                            value = listenbrainzUserToken,
                            label = R.string.listenbrainz_user_token,
                        ) {
                            listenbrainzUserToken = it
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            val listenbrainzToken = Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]
                            val isAuthenticated = listenbrainzToken.isNotBlank()
                            Text(
                                text = stringResource(
                                    if (isAuthenticated) R.string.lastfm_authenticated else R.string.lastfm_not_authenticated
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAuthenticated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            )
                            if (isAuthenticated) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            Prefs[Prefs.LISTENBRAINZ_USER_TOKEN] = ""
                                            listenbrainzUserToken = ""
                                        }
                                    }
                                ) {
                                    Text(text = stringResource(R.string.lastfm_logout))
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        if (listenbrainzUserToken.isBlank()) {
                                            Toast.makeText(context, "Enter your ListenBrainz user token first", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                Prefs[Prefs.LISTENBRAINZ_USER_TOKEN] = listenbrainzUserToken
                                                Toast.makeText(context, "ListenBrainz token saved", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text(text = stringResource(R.string.lastfm_login))
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.listenbrainz_user_token_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        PreferenceSwitch(
                            title = stringResource(id = R.string.listenbrainz_show_timestamps),
                            isChecked = listenbrainzShowTimestamps,
                        ) {
                            listenbrainzShowTimestamps = !listenbrainzShowTimestamps
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.listenbrainz_show_cover_art),
                            isChecked = listenbrainzShowCoverArt,
                        ) {
                            listenbrainzShowCoverArt = !listenbrainzShowCoverArt
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.listenbrainz_show_party_current),
                            isChecked = listenbrainzShowPartyCurrent,
                        ) {
                            listenbrainzShowPartyCurrent = !listenbrainzShowPartyCurrent
                        }

                        PreferenceSwitch(
                            title = stringResource(id = R.string.listenbrainz_show_party_max),
                            isChecked = listenbrainzShowPartyMax,
                        ) {
                            listenbrainzShowPartyMax = !listenbrainzShowPartyMax
                        }
                    }
                },
            )
        }
    }

    // Add, edit, or delete an OAuth application using TextFieldState instead of deprecated TextField(String, ...)
    if (showOAuthAppDialog) {
        val labelState = rememberTextFieldState(initialText = appLabelInput)
        val clientIdState = rememberTextFieldState(initialText = appClientIdInput)
        val tokenState = rememberTextFieldState(initialText = appTokenInput)

        // Sync field states whenever the dialog opens or switches target app
        LaunchedEffect(editingOAuthApp?.id, showOAuthAppDialog) {
            if (showOAuthAppDialog) {
                labelState.setTextAndPlaceCursorAtEnd(appLabelInput)
                clientIdState.setTextAndPlaceCursorAtEnd(appClientIdInput)
                tokenState.setTextAndPlaceCursorAtEnd(appTokenInput)
            }
        }

        AlertDialog(
            onDismissRequest = { showOAuthAppDialog = false },
            title = {
                Text(
                    text = if (editingOAuthApp == null) "Add RPC Application" else "Edit RPC Application"
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        state = labelState,
                        label = { Text("Name") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        state = clientIdState,
                        label = { Text("Application Client ID") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        state = tokenState,
                        label = { Text("Gateway token (optional)") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            val clientId = clientIdState.text.toString().trim()
                            if (clientId.isBlank()) return@TextButton
                            oauth2Error = ""
                            scope.launch {
                                try {
                                    var target = editingOAuthApp
                                    if (target == null) {
                                        target = OAuthApp(
                                            id = OAuthApp.newId(),
                                            label = labelState.text.toString().ifBlank { "RPC App" },
                                            clientId = clientId,
                                        )
                                        Prefs.addOAuthApp(target)
                                        editingOAuthApp = target
                                    }
                                    val result = discordAuth.authorizeApp(activity, target.id, clientId)
                                    val authorized = target.copy(
                                        label = labelState.text.toString().ifBlank { target.label },
                                        clientId = clientId,
                                        token = result.accessToken,
                                        refreshToken = result.refreshToken,
                                        expiresAt = System.currentTimeMillis() + result.expiresInSec * 1000,
                                    )
                                    Prefs.updateOAuthApp(authorized)
                                    tokenState.setTextAndPlaceCursorAtEnd(result.accessToken)
                                    Toast.makeText(context, "Application authorized", Toast.LENGTH_SHORT).show()
                                } catch (e: DiscordOAuth2Auth.DiscordOAuth2Exception) {
                                    oauth2Error = e.message ?: "Authorization failed"
                                } catch (e: Exception) {
                                    oauth2Error = "Unexpected error: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("Authorize with Discord")
                    }
                    if (oauth2Error.isNotBlank()) {
                        Text(
                            text = oauth2Error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clientId = clientIdState.text.toString().trim()
                        if (clientId.isBlank()) return@TextButton
                        val base = editingOAuthApp
                        val label = labelState.text.toString().ifBlank { "RPC App" }
                        val token = tokenState.text.toString().trim()
                        if (base == null) {
                            Prefs.addOAuthApp(
                                OAuthApp(
                                    id = OAuthApp.newId(),
                                    label = label,
                                    clientId = clientId,
                                    token = token,
                                )
                            )
                        } else {
                            Prefs.updateOAuthApp(
                                base.copy(label = label, clientId = clientId, token = token)
                            )
                        }
                        showOAuthAppDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (editingOAuthApp != null) {
                        TextButton(
                            onClick = {
                                val app = editingOAuthApp
                                if (app != null) Prefs.deleteOAuthApp(app.id)
                                showOAuthAppDialog = false
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                    TextButton(onClick = { showOAuthAppDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}