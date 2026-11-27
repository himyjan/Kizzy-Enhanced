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
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.toVersion
import com.my.kizzy.feature_about.about.About
import com.my.kizzy.feature_about.about.Credits
import com.my.kizzy.feature_about.about.CreditsScreenViewModel
import com.my.kizzy.feature_apps_rpc.AppsRPC
import com.my.kizzy.feature_apps_rpc.AppsScreenViewModel
import com.my.kizzy.feature_custom_rpc.CustomRPC
import com.my.kizzy.feature_custom_rpc.CustomScreenViewModel
import com.my.kizzy.feature_home.Home
import com.my.kizzy.feature_home.HomeScreenViewModel
import com.my.kizzy.feature_logs.LogScreen
import com.my.kizzy.feature_logs.LogsViewModel
import com.my.kizzy.feature_media_rpc.MediaRPC
import com.my.kizzy.feature_media_rpc.MediaScreenViewModel
import com.my.kizzy.feature_profile.ui.component.RpcPreviewScreen
import com.my.kizzy.feature_profile.ui.component.RpcPreviewViewModel
import com.my.kizzy.feature_profile.ui.user.UserScreen
import com.my.kizzy.feature_profile.ui.user.UserState
import com.my.kizzy.feature_profile.ui.user.UserViewModel
import com.my.kizzy.feature_settings.language.Language
import com.my.kizzy.feature_settings.rpc_settings.CustomRpcBoardScreen
import com.my.kizzy.feature_settings.rpc_settings.LeaderboardSettings
import com.my.kizzy.feature_settings.rpc_settings.RpcSettings
import com.my.kizzy.feature_settings.style.Appearance
import com.my.kizzy.feature_settings.style.DarkThemePreferences
import com.my.kizzy.feature_startup.StartUp
import com.my.kizzy.navigation.AppRoute
import com.my.kizzy.preference.Prefs
import kotlinx.serialization.json.Json
import xyz.dead8309.feature_manual_rpc.ManualRpcScreen
import xyz.dead8309.feature_manual_rpc.ManualRpcViewmodel
import xyz.dead8309.feature_manual_rpc.apps.ManualRpcAppsScreen

/**
 * Root Navigation 3 host composable for Kizzy.
 * Owns the `NavBackStack` of `@Serializable` `AppRoute` keys and maps each key to its screen.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun Kizzy(
    usageAccessStatus: MutableState<Boolean>,
    notificationListenerAccess: MutableState<Boolean>,
    batteryOptimizationStatus: MutableState<Boolean>,
    authTabLauncher: ActivityResultLauncher<Intent>? = null,
    onBindAuthTabCallback: (((Int, Intent?) -> Unit) -> Unit)? = null,
) {
    MaterialTheme {
        // Choose initial destination on first launch vs returning user
        val startRoute = remember {
            if (Prefs[Prefs.IS_FIRST_LAUNCHED, true]) AppRoute.Setup else AppRoute.Home
        }

        // Developer-owned back stack persisted across config changes & process death
        val backStack = rememberNavBackStack(startRoute)

        // Guard against popping past the root destination on rapid back presses
        fun goBack() {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        }

        // Push destination unless it is already at the top of the stack
        fun navigateTo(route: AppRoute) {
            if (backStack.lastOrNull() != route) {
                backStack.add(route)
            }
        }

        // Replace the entire back stack with a single destination (e.g., after onboarding)
        fun navigateAndClearTo(route: AppRoute) {
            backStack.clear()
            backStack.add(route)
        }

        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { goBack() },
            // Scope SaveableState and ViewModels to each individual NavEntry
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                // First-time setup & permission onboarding flow
                entry<AppRoute.Setup> {
                    StartUp(
                        usageAccessStatus = usageAccessStatus,
                        mediaControlStatus = notificationListenerAccess,
                        batteryOptimizationStatus = batteryOptimizationStatus,
                        navigateToLanguages = { navigateTo(AppRoute.Languages) },
                        navigateToHome = {
                            Prefs[Prefs.IS_FIRST_LAUNCHED] = false
                            navigateAndClearTo(AppRoute.Home)
                        },
                        navigateToLogin = { navigateTo(AppRoute.Profile) },
                    )
                }

                // Home screen with RPC toggles, clones, and update check
                entry<AppRoute.Home> {
                    val release = remember(Prefs.getSavedLatestRelease()) {
                        Prefs.getSavedLatestRelease()
                    }
                    val user = Prefs.getUser()
                    val viewModel = hiltViewModel<HomeScreenViewModel>()
                    val state by viewModel.aboutScreenState.collectAsState()
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
                        user = user,
                        navigateToProfile = { navigateTo(AppRoute.Profile) },
                        navigateToStyleAndAppearance = { navigateTo(AppRoute.StyleAndAppearance) },
                        navigateToLanguages = { navigateTo(AppRoute.Languages) },
                        navigateToAbout = { navigateTo(AppRoute.About) },
                        navigateToRpcSettings = { navigateTo(AppRoute.RpcSettings) },
                        navigateToLogsScreen = { navigateTo(AppRoute.LogsScreen) },
                        navigateTo = { route -> navigateTo(route) },
                        hasUsageAccess = usageAccessStatus,
                        hasNotificationAccess = notificationListenerAccess,
                    )
                }

                // App Detection screen
                entry<AppRoute.AppsDetection> {
                    val viewModel = hiltViewModel<AppsScreenViewModel>()
                    val state by viewModel.state.collectAsState()
                    AppsRPC(
                        onBackPressed = { goBack() },
                        hasUsageAccess = usageAccessStatus.value,
                        state = state,
                        updateAppEnabled = viewModel::updateAppEnabled,
                    )
                }

                // Built-in Custom RPC screen
                entry<AppRoute.CustomRpc> {
                    val viewModel = hiltViewModel<CustomScreenViewModel, CustomScreenViewModel.Factory>(
                        creationCallback = { factory -> factory.create(null) }
                    )
                    val state by viewModel.uiState.collectAsState()
                    CustomRPC(
                        onBackPressed = { goBack() },
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                }

                // Cloned Custom RPC instance screen keyed by `key.instanceId`
                entry<AppRoute.CustomRpcInstance> { key ->
                    val viewModel = hiltViewModel<CustomScreenViewModel, CustomScreenViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.instanceId) }
                    )
                    val state by viewModel.uiState.collectAsState()
                    CustomRPC(
                        onBackPressed = { goBack() },
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                }

                // Built-in Media RPC screen
                entry<AppRoute.MediaRpc> {
                    val viewModel = hiltViewModel<MediaScreenViewModel>()
                    val state by viewModel.state.collectAsState()
                    MediaRPC(
                        onBackPressed = { goBack() },
                        state = state,
                        hasNotificationAccess = notificationListenerAccess.value,
                        updateMediaAppEnabled = viewModel::updateMediaAppEnabled,
                        selectAllMediaApps = viewModel::selectAllMediaApps,
                        deselectAllMediaApps = viewModel::deselectAllMediaApps,
                        updateAppActivityType = viewModel::updateAppActivityType,
                    )
                }

                // Cloned Media RPC instance screen keyed by `key.instanceId`
                entry<AppRoute.MediaRpcInstance> { key ->
                    val viewModel = hiltViewModel<MediaScreenViewModel>()
                    val state by viewModel.state.collectAsState()
                    MediaRPC(
                        onBackPressed = { goBack() },
                        state = state,
                        hasNotificationAccess = notificationListenerAccess.value,
                        updateMediaAppEnabled = viewModel::updateMediaAppEnabled,
                        selectAllMediaApps = viewModel::selectAllMediaApps,
                        deselectAllMediaApps = viewModel::deselectAllMediaApps,
                        updateAppActivityType = viewModel::updateAppActivityType,
                        instanceId = key.instanceId,
                    )
                }

                // Discord user profile & OAuth login screen
                entry<AppRoute.Profile> {
                    val viewModel = hiltViewModel<UserViewModel>()
                    val logoutScope = rememberCoroutineScope()
                    val state by viewModel.state.collectAsState()
                    UserScreen(
                        state = state,
                        onBackPressed = { goBack() },
                        onLogout = { ctx -> viewModel.logout(ctx, logoutScope) },
                        authTabLauncher = authTabLauncher,
                        onBindAuthTabCallback = onBindAuthTabCallback,
                        onNavigateToRpcPreview = { instanceId ->
                            val currentState = state
                            val user = when (currentState) {
                                is UserState.LoadingCompleted -> currentState.user
                                is UserState.Error -> currentState.user
                                UserState.Loading -> null
                            }
                            val userId = user?.id ?: ""
                            val avatarUrl = user?.getAvatarImage() ?: ""
                            navigateTo(
                                AppRoute.RpcPreview(
                                    userId = userId,
                                    avatarUrl = avatarUrl,
                                    instanceId = instanceId,
                                )
                            )
                        },
                    )
                }

                // Language selector screen
                entry<AppRoute.Languages> {
                    Language(
                        onBackPressed = { goBack() },
                        updateLocaleLanguage = MainActivity::setLanguage,
                    )
                }

                // Appearance & theme settings
                entry<AppRoute.StyleAndAppearance> {
                    Appearance(
                        onBackPressed = { goBack() },
                        navigateToDarkTheme = { navigateTo(AppRoute.DarkTheme) },
                    )
                }

                // Dark theme preference screen
                entry<AppRoute.DarkTheme> {
                    DarkThemePreferences { goBack() }
                }

                // RPC settings hub
                entry<AppRoute.RpcSettings> {
                    RpcSettings(
                        onBackPressed = { goBack() },
                        authTabLauncher = authTabLauncher,
                        onBindAuthTabCallback = onBindAuthTabCallback,
                        navigateToCustomRpcBoardScreen = { navigateTo(AppRoute.CustomRpcBoard) },
                        navigateToLeaderboardScreen = { navigateTo(AppRoute.Leaderboard) },
                    )
                }

                // Leaderboard settings
                entry<AppRoute.Leaderboard> {
                    LeaderboardSettings(onBackPressed = { goBack() })
                }

                // Custom RPC board screen
                entry<AppRoute.CustomRpcBoard> {
                    CustomRpcBoardScreen(onBackPressed = { goBack() })
                }

                // Logs viewer screen
                entry<AppRoute.LogsScreen> {
                    val viewModel = hiltViewModel<LogsViewModel>()
                    LogScreen(viewModel, onBackPressed = { goBack() })
                }

                // About screen
                entry<AppRoute.About> {
                    About(
                        onBackPressed = { goBack() },
                        navigateToCredits = { navigateTo(AppRoute.Credits) },
                    )
                }

                // Credits screen
                entry<AppRoute.Credits> {
                    val viewModel = hiltViewModel<CreditsScreenViewModel>()
                    val state by viewModel.creditScreenState.collectAsState()
                    Credits(
                        state = state,
                        onBackPressed = { goBack() },
                    )
                }

                // RPC presence preview screen with assisted `instanceId`
                entry<AppRoute.RpcPreview> { key ->
                    val viewModel = hiltViewModel<RpcPreviewViewModel, RpcPreviewViewModel.Factory>(
                        creationCallback = { factory -> factory.create(key.instanceId) }
                    )
                    val rpcConfig by viewModel.rpcConfig.collectAsState()
                    RpcPreviewScreen(
                        userId = key.userId,
                        avatarUrl = key.avatarUrl,
                        rpcConfig = rpcConfig,
                        onBackPressed = { goBack() },
                    )
                }

                // Built-in Manual RPC screen
                entry<AppRoute.ManualRpc> {
                    val viewModel = hiltViewModel<ManualRpcViewmodel, ManualRpcViewmodel.Factory>(
                        creationCallback = { factory -> factory.create(null) }
                    )
                    val state by viewModel.uiState.collectAsState()
                    ManualRpcScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onBackPressed = { goBack() },
                        hasUsageAccess = usageAccessStatus.value,
                        hasNotificationAccess = notificationListenerAccess.value,
                        navigateToAppSelection = { navigateTo(AppRoute.ManualRpcApps) },
                    )
                }

                // Cloned Manual RPC instance screen keyed by `key.instanceId`
                entry<AppRoute.ManualRpcInstance> { key ->
                    val viewModel = hiltViewModel<ManualRpcViewmodel, ManualRpcViewmodel.Factory>(
                        creationCallback = { factory -> factory.create(key.instanceId) }
                    )
                    val state by viewModel.uiState.collectAsState()
                    ManualRpcScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onBackPressed = { goBack() },
                        hasUsageAccess = usageAccessStatus.value,
                        hasNotificationAccess = notificationListenerAccess.value,
                        navigateToAppSelection = { navigateTo(AppRoute.ManualRpcApps) },
                    )
                }

                // App picker for Manual RPC
                entry<AppRoute.ManualRpcApps> {
                    val viewModel = hiltViewModel<ManualRpcViewmodel, ManualRpcViewmodel.Factory>(
                        creationCallback = { factory -> factory.create(null) }
                    )
                    val state by viewModel.uiState.collectAsState()
                    ManualRpcAppsScreen(
                        onBackPressed = { goBack() },
                        state = state,
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        )
    }
}