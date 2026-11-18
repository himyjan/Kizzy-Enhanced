package com.my.kizzy.feature_settings.rpc_settings

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.runtime.collectAsState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew

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
    var showConnectionMethodDialog by remember { mutableStateOf(false) }
    var oauth2Error by remember { mutableStateOf("") }
    var discordOAuth2ClientId by remember {
        mutableStateOf(Prefs[Prefs.DISCORD_OAUTH2_APPLICATION_CLIENT_ID, ""])
    }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var editingClientId22 by remember { mutableStateOf("") }
    var showUserLogin by remember { mutableStateOf(false) }

    // Collect from DataStore state flow so OAuth2 selection persists across cold boots.
    // DataStore priming is async (Dispatchers.IO), so Prefs[...] may return the default
    // on first read before priming completes. Using collectAsState makes the UI react
    // when _prefsState is primed from disk.
    val prefsMap by Prefs.prefsState.collectAsState(initial = emptyMap())
    var connectionMethod by remember(prefsMap) {
        mutableStateOf(prefsMap[Prefs.RPC_CONNECTION_METHOD] as? String ?: Prefs.RPC_CONNECTION_METHOD_GATEWAY)
    }

    val discordAuth = remember<DiscordOAuth2Auth> { DiscordOAuth2Auth(context, scope) }

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

    // Last.fm scrobbling settings
    var showLastFmDialog by remember { mutableStateOf(false) }
    var lastfmApiKey by remember { mutableStateOf(Prefs[Prefs.LASTFM_API_KEY, ""]) }
    var lastfmSecret by remember { mutableStateOf(Prefs[Prefs.LASTFM_SECRET, ""]) }
    var lastfmUsername by remember { mutableStateOf(Prefs[Prefs.LASTFM_USERNAME, ""]) }
    var lastfmPassword by remember { mutableStateOf(Prefs[Prefs.LASTFM_PASSWORD, ""]) }
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
    var isLibreFmEnabled by remember { mutableStateOf(Prefs[Prefs.LIBREFM_ENABLED, false]) }

    // ListenBrainz scrobbling settings
    var showListenBrainzDialog by remember { mutableStateOf(false) }
    var listenbrainzUserToken by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_USER_TOKEN, ""]) }
    var listenbrainzEnabled by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_ENABLED, false]) }
    var listenbrainzShowTimestamps by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS, true]) }
    var listenbrainzShowCoverArt by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_COVER_ART, true]) }
    var listenbrainzShowPartyCurrent by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT, true]) }
    var listenbrainzShowPartyMax by remember { mutableStateOf(Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_MAX, false]) }

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
                                // OAuth 2.1 Authorization Code flow with PKCE S256.
                                // discordAuth.authorize() opens the system browser for
                                // user consent, exchanges the authorization code for
                                // bearer + refresh tokens, and stores them internally.
                                val result = discordAuth.authorize(activity)
                                Log.d("RpcSettings", "OAuth2 authorized: bearer_token len=${result.accessToken.length}, refresh_token len=${result.refreshToken.length}, expires_in=${result.expiresInSec}s")

                                // Discord's OAuth2 returns a bearer token, which the
                                // gateway rejects with 4004. We still need the real
                                // account token (user_id.timestamp.hmac) for the gateway,
                                // so open the WebView login to capture it from page storage.
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

                // Image pickers for default cover art
                val pickLargeCoverArt = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    uri?.let { defaultLargeCoverArt = it.toString(); Prefs[Prefs.RPC_DEFAULT_COVER_ART_LARGE] = defaultLargeCoverArt }
                }

                RpcField(
                    value = defaultLargeCoverArt,
                    label = R.string.rpc_default_cover_art_large,
                    trailingIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = { pickLargeCoverArt.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*"))) }
                        ) {
                            androidx.compose.material3.Icon(
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
                    uri?.let { defaultSmallCoverArt = it.toString(); Prefs[Prefs.RPC_DEFAULT_COVER_ART_SMALL] = defaultSmallCoverArt }
                }

                RpcField(
                    value = defaultSmallCoverArt,
                    label = R.string.rpc_default_cover_art_small,
                    trailingIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = { pickSmallCoverArt.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/*"))) }
                        ) {
                            androidx.compose.material3.Icon(
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
                val lastFmStatus = when {
                    isLastFmEnabled -> "Enabled"
                    else -> "Disabled"
                }
                SettingItem(
                    title = stringResource(id = R.string.lastfm_settings_title),
                    description = stringResource(id = R.string.lastfm_enabled) + "  •  $lastFmStatus",
                ) {
                    lastfmApiKey = Prefs[Prefs.LASTFM_API_KEY, ""]
                    lastfmSecret = Prefs[Prefs.LASTFM_SECRET, ""]
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
                val libreFmStatus = if (isLibreFmEnabled) "Enabled" else "Disabled"
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
                val listenbrainzStatus = when {
                    listenbrainzEnabled -> "Enabled"
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
                                // Keep existing value if empty
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

        if (showUserLogin) {
            DiscordUserLoginDialog(
                onDismiss = { showUserLogin = false },
                onToken = { token ->
                    Prefs[Prefs.DISCORD_OAUTH2_ACCESS_TOKEN] = token
                    Prefs[Prefs.RPC_CONNECTION_METHOD] = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                    connectionMethod = Prefs.RPC_CONNECTION_METHOD_OAUTH2
                    showUserLogin = false
                    oauth2Error = ""
                    Toast.makeText(context, "Discord account connected", Toast.LENGTH_SHORT).show()
                    Log.d("RpcSettings", "Account token saved (len=${token.length}, parts=${token.count { it == '.' } + 1})")
                    // Do NOT start DiscordOAuth2RpcService here. It builds an empty
                    // presence (name/state/details = null) that overwrites the real
                    // Media/Custom presence. The running RPC service already calls
                    // KizzyRPC.refreshFromPrefs() and picks up the new token.
                },
            )
        }

        if (showLastFmDialog) {
            AlertDialog(
                onDismissRequest = { showLastFmDialog = false },
                confirmButton = {
                    TextButton(onClick = {
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
                    }) {
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
                        ) {
                            lastfmPassword = it
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

        if (showLibreFmDialog) {
            AlertDialog(
                onDismissRequest = { showLibreFmDialog = false },
                confirmButton = {
                    TextButton(onClick = {
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
                    }) {
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
                        ) {
                            librefmPassword = it
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

        if (showListenBrainzDialog) {
            AlertDialog(
                onDismissRequest = { showListenBrainzDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        Prefs[Prefs.LISTENBRAINZ_USER_TOKEN] = listenbrainzUserToken
                        Prefs[Prefs.LISTENBRAINZ_ENABLED] = listenbrainzEnabled
                        Prefs[Prefs.LISTENBRAINZ_ENABLE_TIMESTAMPS] = listenbrainzShowTimestamps
                        Prefs[Prefs.LISTENBRAINZ_SHOW_COVER_ART] = listenbrainzShowCoverArt
                        Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_CURRENT] = listenbrainzShowPartyCurrent
                        Prefs[Prefs.LISTENBRAINZ_SHOW_PARTY_MAX] = listenbrainzShowPartyMax
                        showListenBrainzDialog = false
                    }) {
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
}