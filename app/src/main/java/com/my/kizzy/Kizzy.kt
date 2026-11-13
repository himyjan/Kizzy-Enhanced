/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * Kizzy.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy

import android.annotation.SuppressLint
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.navArgument
import com.my.kizzy.domain.model.toVersion
import com.my.kizzy.feature_about.about.About
import com.my.kizzy.feature_about.about.Credits
import com.my.kizzy.feature_about.about.CreditsScreenViewModel
import com.my.kizzy.feature_apps_rpc.AppsRPC
import com.my.kizzy.feature_apps_rpc.AppsScreenViewModel
import com.my.kizzy.feature_console_rpc.GamesScreen
import com.my.kizzy.feature_console_rpc.GamesViewModel
import com.my.kizzy.feature_custom_rpc.CustomRPC
import com.my.kizzy.feature_custom_rpc.CustomScreenViewModel
import com.my.kizzy.feature_home.Home
import com.my.kizzy.feature_home.HomeScreenViewModel
import com.my.kizzy.feature_home.feature.homeFeaturesProvider
import com.my.kizzy.feature_logs.LogScreen
import com.my.kizzy.feature_logs.LogsViewModel
import com.my.kizzy.feature_media_rpc.MediaRPC
import com.my.kizzy.feature_media_rpc.MediaScreenViewModel
import com.my.kizzy.feature_profile.ui.login.LoginScreen
import com.my.kizzy.feature_profile.ui.component.RpcPreviewScreen
import com.my.kizzy.feature_profile.ui.component.RpcPreviewViewModel
import com.my.kizzy.feature_profile.ui.user.UserScreen
import com.my.kizzy.feature_profile.ui.user.UserState
import com.my.kizzy.feature_profile.ui.user.UserViewModel
import com.my.kizzy.feature_settings.language.Language
import com.my.kizzy.feature_settings.rpc_settings.RpcSettings
import com.my.kizzy.feature_settings.style.Appearance
import com.my.kizzy.feature_settings.style.DarkThemePreferences
import com.my.kizzy.feature_startup.StartUp
import com.my.kizzy.navigation.Routes
import com.my.kizzy.preference.Prefs
import xyz.dead8309.feature_experimental_rpc.ExperimentalRpcScreen
import xyz.dead8309.feature_experimental_rpc.ExperimentalRpcViewmodel
import xyz.dead8309.feature_experimental_rpc.apps.ExperimentalRpcAppsScreen

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ComponentActivity.Kizzy(
    usageAccessStatus: MutableState<Boolean>,
    notificationListenerAccess: MutableState<Boolean>,
) {
    Scaffold()
    {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = if (Prefs[Prefs.IS_FIRST_LAUNCHED, true]) Routes.SETUP else Routes.HOME
        ) {
            composable(Routes.SETUP) {
                StartUp(
                    usageAccessStatus = usageAccessStatus,
                    mediaControlStatus = notificationListenerAccess,
                    navigateToLanguages = {
                        navController.navigate(Routes.LANGUAGES) {
                            launchSingleTop = true
                        }
                    },
                    navigateToHome = {
                        Prefs[Prefs.IS_FIRST_LAUNCHED] = false
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    },
                    navigateToLogin = {
                        navController.navigate(Routes.PROFILE)
                    })
            }
            composable(Routes.HOME) { navBackStackEntry ->
                val release = remember(Prefs.getSavedLatestRelease()) {
                    Prefs.getSavedLatestRelease()
                }
                val user = Prefs.getUser()
                val viewModel = hiltViewModel<HomeScreenViewModel>(navBackStackEntry)
                val state = viewModel.aboutScreenState.collectAsState().value
                val showBadge = release
                    ?.toVersion()
                    ?.whetherNeedUpdate(BuildConfig.VERSION_NAME.toVersion())
                    ?: false
                Home(
                    state = state,
                    checkForUpdates = {
                        if (release != null && release.toVersion() > BuildConfig.VERSION_NAME.toVersion()) {
                            viewModel.setReleaseFromPrefs(release)
                        } else {
                            viewModel.getLatestUpdate()
                        }
                    },
                    showBadge = showBadge,
                    features = homeFeaturesProvider(
                        navigateTo = { navController.navigate(it) },
                        hasUsageAccess = usageAccessStatus,
                        hasNotificationAccess = notificationListenerAccess,
                        userVerified = user?.verified == true
                    ),
                    user = user,
                    navigateToProfile = {
                        navController.navigate(Routes.PROFILE)
                    },
                    navigateToStyleAndAppearance = {
                        navController.navigate(Routes.STYLE_AND_APPEARANCE)
                    },
                    navigateToLanguages = {
                        navController.navigate(Routes.LANGUAGES)
                    },
                    navigateToAbout = {
                        navController.navigate(Routes.ABOUT)
                    },
                    navigateToRpcSettings = {
                        navController.navigate(Routes.RPC_SETTINGS)
                    },
                    navigateToLogsScreen = {
                        navController.navigate(Routes.LOGS_SCREEN)
                    }
                )
            }
            composable(Routes.APPS_DETECTION) { navBackStackEntry ->
                val viewModel = hiltViewModel<AppsScreenViewModel>(navBackStackEntry)
                AppsRPC(
                    onBackPressed = { navController.popBackStack() },
                    hasUsageAccess = usageAccessStatus.value,
                    state = viewModel.state.collectAsState().value,
                    updateAppEnabled = viewModel::updateAppEnabled,
                )
            }
            composable(Routes.CUSTOM_RPC) { navBackStackEntry ->
                val viewModel = hiltViewModel<CustomScreenViewModel>(navBackStackEntry)
                CustomRPC(
                    onBackPressed = { navController.popBackStack() },
                    state = viewModel.uiState.collectAsState().value,
                    onEvent = viewModel::onEvent
                )
            }
            composable(Routes.MEDIA_RPC) { navBackStackEntry ->
                val viewModel = hiltViewModel<MediaScreenViewModel>(navBackStackEntry)
                MediaRPC(
                    onBackPressed = { navController.popBackStack() },
                    state = viewModel.state.collectAsState().value,
                    hasNotificationAccess = notificationListenerAccess.value,
                    updateMediaAppEnabled = viewModel::updateMediaAppEnabled
                )
            }
            composable(Routes.PROFILE) { navBackStackEntry ->
                var loggedIn by remember {
                    mutableStateOf(Prefs[Prefs.TOKEN, ""].isNotEmpty())
                }
                if (loggedIn) {
                    val viewModel = hiltViewModel<UserViewModel>(navBackStackEntry)
                    val state by viewModel.state.collectAsState()
                    UserScreen(
                        state = state,
                        onBackPressed = navController::popBackStack,
                        onLogout = {
                            Prefs.remove(Prefs.TOKEN)
                            Prefs.remove(Prefs.USER_ID)
                            Prefs.remove(Prefs.USER_DATA)
                            Prefs.remove(Prefs.USER_BIO)
                            Prefs.remove(Prefs.USER_NITRO)
                            loggedIn = false
                        },
                        onNavigateToRpcPreview = {
                            val currentState = state
                            val user = when (currentState) {
                                is UserState.LoadingCompleted -> currentState.user
                                is UserState.Error -> currentState.user
                                UserState.Loading -> null
                                else -> null
                            }
                            val userId = user?.id ?: ""
                            val avatarUrl = user?.getAvatarImage() ?: ""
                            // Uri.encode (not URLEncoder): URLEncoder turns a space into
                            // "+", which the navigation route does not decode back, so
                            // "Penthouse Rules" was shown as "Penthouse+Rules".
                            val encodedAvatarUrl = android.net.Uri.encode(avatarUrl)
                            val rpcConfig = when (currentState) {
                                is UserState.LoadingCompleted -> currentState.rpcConfig
                                is UserState.Error -> currentState.rpcConfig
                                else -> null
                            }
                            // The config is serialized with the real serializer and
                            // then encoded once. Building the json by hand broke on
                            // every name that contained a quote or a backslash, and
                            // the hand written json did not match the model, so
                            // the preview showed nothing.
                            val encodedRpcConfig = if (rpcConfig != null) {
                                android.net.Uri.encode(
                                    kotlinx.serialization.json.Json {
                                        ignoreUnknownKeys = true
                                        encodeDefaults = true
                                    }.encodeToString(
                                        com.my.kizzy.domain.model.rpc.RpcConfig.serializer(),
                                        rpcConfig
                                    )
                                )
                            } else ""
                            navController.navigate("${Routes.RPC_PREVIEW}/$userId/$encodedAvatarUrl/$encodedRpcConfig")
                        }
                    )
                } else {
                    LoginScreen(
                        onBackPressed = navController::popBackStack,
                        onCompleted = {
                            loggedIn = true
                        },
                    )
                }
            }
            composable(Routes.CONSOLE_RPC) { navBackStackEntry ->
                val viewModel = hiltViewModel<GamesViewModel>(navBackStackEntry)
                GamesScreen(
                    onBackPressed = { navController.popBackStack() },
                    onEvent = { viewModel.onUiEvent(it) },
                    isSearchBarVisible = viewModel.isSearchBarVisible.value,
                    state = viewModel.state.value,
                    detectedConsole = viewModel.detected.value,
                    autoDetect = viewModel.autoDetect.value,
                    onToggleAutoDetect = viewModel::toggleAutoDetect,
                    includeSpoofed = viewModel.includeSpoofed.value,
                    onToggleIncludeSpoofed = viewModel::toggleIncludeSpoofed,
                )
            }
            composable(Routes.LANGUAGES) {
                Language(
                    onBackPressed = { navController.popBackStack() },
                    updateLocaleLanguage = MainActivity::setLanguage
                )
            }
            composable(Routes.STYLE_AND_APPEARANCE) {
                Appearance(onBackPressed = {
                    navController.popBackStack()
                }) {
                    navController.navigate(Routes.DARK_THEME)
                }
            }
            composable(Routes.DARK_THEME) {
                DarkThemePreferences {
                    navController.popBackStack()
                }
            }
            composable(Routes.RPC_SETTINGS) {
                RpcSettings { navController.popBackStack() }
            }
            composable(Routes.LOGS_SCREEN) { navBackStackEntry ->
                val viewModel = hiltViewModel<LogsViewModel>(navBackStackEntry)
                LogScreen(viewModel, onBackPressed = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                About(
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    navigateToCredits = {
                        navController.navigate(Routes.CREDITS)
                    }
                )
            }
            composable(Routes.CREDITS) { navBackStackEntry ->
                val viewModel = hiltViewModel<CreditsScreenViewModel>(navBackStackEntry)
                Credits(
                    state = viewModel.creditScreenState.collectAsState().value,
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = "${Routes.RPC_PREVIEW}/{userId}/{avatarUrl}/{rpcConfig}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("avatarUrl") { type = NavType.StringType },
                    navArgument("rpcConfig") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                val avatarUrl = backStackEntry.arguments?.getString("avatarUrl") ?: ""
                // The route argument is only a snapshot of the presence at the
                // moment the page was opened. The ViewModel decodes it and, while
                // Media RPC runs, replaces the media fields with the live ones so
                // the next song, seek and pause state show up here too.
                val previewViewModel = hiltViewModel<RpcPreviewViewModel>(backStackEntry)
                val rpcConfig by previewViewModel.rpcConfig.collectAsState()
                RpcPreviewScreen(
                    userId = userId,
                    avatarUrl = avatarUrl,
                    rpcConfig = rpcConfig,
                    onBackPressed = { navController.popBackStack() }
                )
            }
            composable(Routes.EXPERIMENTAL_RPC) { navBackStackEntry ->
                val experimentalRpcViewModel = hiltViewModel<ExperimentalRpcViewmodel>(navBackStackEntry)
                ExperimentalRpcScreen(
                    state = experimentalRpcViewModel.uiState.collectAsState().value,
                    onEvent = experimentalRpcViewModel::onEvent,
                    onBackPressed = { navController.popBackStack() },
                    hasUsageAccess = usageAccessStatus.value,
                    hasNotificationAccess = notificationListenerAccess.value,
                    navigateToAppSelection = {
                        navController.navigate(Routes.EXPERIMENTAL_RPC_APPS)
                    },
                )
            }
            composable(Routes.EXPERIMENTAL_RPC_APPS) { navBackStackEntry ->
                val experimentalRpcViewModel = hiltViewModel<ExperimentalRpcViewmodel>(navBackStackEntry)
                ExperimentalRpcAppsScreen(
                    onBackPressed = { navController.popBackStack() },
                    state = experimentalRpcViewModel.uiState.collectAsState().value,
                    onEvent = experimentalRpcViewModel::onEvent,
                )
            }
        }
    }
}
