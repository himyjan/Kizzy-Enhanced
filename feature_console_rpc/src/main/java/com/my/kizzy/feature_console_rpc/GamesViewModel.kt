/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GamesViewModel.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_console_rpc

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.kizzy.data.get_current_data.app.GetForegroundPackage
import com.my.kizzy.data.rpc.ActiveGameTracker
import com.my.kizzy.data.rpc.ConsoleAppRegistry
import com.my.kizzy.data.rpc.ConsoleGameCatalog
import com.my.kizzy.domain.model.Game
import com.my.kizzy.domain.model.Resource
import com.my.kizzy.domain.use_case.get_games.GetGamesUseCase
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A DroidDeck/Eden instance that is currently open.
 */
data class DetectedConsole(
    val platformName: String,
    val packageName: String,
    val gameTitle: String?,
    val largeImage: String?,
    val smallImage: String?,
)

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val getGamesUseCase: GetGamesUseCase,
    private val foregroundPackage: GetForegroundPackage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state: MutableState<GamesState> = mutableStateOf(GamesState.Loading)
    val state: State<GamesState> = _state
    private val games = mutableListOf<Game>()
    val isSearchBarVisible = mutableStateOf(false)

    /** DroidDeck/Eden currently in the foreground, if any. */
    private val _detected = mutableStateOf<DetectedConsole?>(null)
    val detected: State<DetectedConsole?> = _detected

    val autoDetect = mutableStateOf(Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT, true])
    val includeSpoofed = mutableStateOf(Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED, false])

    /** Whether Console RPC is running (updated from AppUtils on init). */
    private val _consoleRpcRunning = mutableStateOf(AppUtils.consoleRpcRunning())
    val consoleRpcRunning: State<Boolean> = _consoleRpcRunning

    /** Whether another RPC service is running (updated from AppUtils on init). */
    private val _otherRpcServicesRunning = mutableStateOf(
        AppUtils.mediaRpcRunning() || AppUtils.experimentalRpcRunning() || AppUtils.appDetectionRunning()
    )
    val otherRpcServicesRunning: State<Boolean> = _otherRpcServicesRunning

    private var searchJob: Job? = null
    private var detectionJob: Job? = null

    init {
        getGames()
        observeConsoleForeground()
    }

    private fun getGames() {
        getGamesUseCase().onEach { result ->
            when (result) {
                is Resource.Success -> {
                    val freshGames = result.data ?: emptyList()
                    games.clear()
                    games.addAll(freshGames)
                    _state.value = GamesState.Success(games = freshGames)
                    // The service reuses this catalog to attach cover art to an
                    // automatically detected game without fetching it again.
                    ConsoleGameCatalog.update(freshGames)
                }

                is Resource.Error -> {
                    _state.value = GamesState.Error(
                        error = result.message ?: context.getString(R.string.unknown_error)
                    )
                }

                is Resource.Loading -> {
                    _state.value = GamesState.Loading
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Watches the foreground app while the screen is open.
     *
     * The heavy lookup runs on IO; the Compose state is written on the main
     * thread so it cannot race with composition.
     */
    private fun observeConsoleForeground() {
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            while (isActive) {
                val detected = withContext(Dispatchers.IO) { readDetectedConsole() }
                _detected.value = detected
                delay(DETECTION_INTERVAL_MS)
            }
        }
    }

    private fun readDetectedConsole(): DetectedConsole? {
        val includeSpoofed = Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED, false]
        val foreground = foregroundPackage()
        // While the Console RPC screen itself is open, Kizzy is the foreground
        // app and the launcher cannot be seen. Fall back to the launcher that
        // most recently posted a game notification so the card still shows
        // what will be used when the user starts Console RPC.
        val packageName = when {
            ConsoleAppRegistry.isKnown(foreground, includeSpoofed) -> foreground
            foreground == null || foreground in SELF_PACKAGES ->
                ActiveGameTracker.currentPackage()
            else -> null
        }
        val console = ConsoleAppRegistry.resolve(packageName, includeSpoofed) ?: return null
        val title = ActiveGameTracker.titleFor(console.packageName)
        val game = ConsoleGameCatalog.match(title)
        return DetectedConsole(
            platformName = console.platform.displayName,
            packageName = console.packageName,
            gameTitle = title,
            largeImage = game?.large_image,
            smallImage = game?.small_image,
        )
    }

    fun onUiEvent(uiEvent: UiEvent) {
        when (uiEvent) {
            UiEvent.CloseSearchBar -> isSearchBarVisible.value = false
            UiEvent.OpenSearchBar -> isSearchBarVisible.value = true
            is UiEvent.Search -> onSearch(uiEvent.query)
            UiEvent.TryAgain -> getGames()
        }
    }

    fun toggleAutoDetect(enabled: Boolean) {
        autoDetect.value = enabled
        Prefs[Prefs.CONSOLE_RPC_AUTO_DETECT] = enabled
    }

    fun toggleIncludeSpoofed(enabled: Boolean) {
        includeSpoofed.value = enabled
        Prefs[Prefs.CONSOLE_RPC_INCLUDE_SPOOFED] = enabled
        // Refresh immediately so the currently open launcher appears/disappears
        // without waiting for the next poll.
        viewModelScope.launch {
            _detected.value = withContext(Dispatchers.IO) { readDetectedConsole() }
        }
    }

    private fun onSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(500)
            searchForGame(query)
        }
    }

    private fun searchForGame(query: String) = if (query == "")
        _state.value = GamesState.Success(games = games)
    else {
        val newList = games.filter {
            it.game_title.contains(query, ignoreCase = true)
        }
        _state.value = GamesState.Success(games = newList)
    }

    private companion object {
        // 5 seconds instead of 2: the foreground app rarely changes while the
        // screen is open, and polling every 2 seconds keeps the CPU awake for
        // no reason. 5 seconds is still responsive enough for the auto-detect
        // card while saving meaningful battery.
        const val DETECTION_INTERVAL_MS = 5000L
        val SELF_PACKAGES = setOf("com.my.kizzy", "com.my.kizzy.enhanced", "com.discord")
    }
}
