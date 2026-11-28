/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CustomRpcBoardManager.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.my.kizzy.data.rpc.CustomRpcBoardItem
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.customRpcBoardsDataStore: DataStore<Preferences> by
preferencesDataStore(name = "custom_rpc_boards")

private val boardListSerializer = ListSerializer(CustomRpcBoardItem.serializer())

/**
 * Manages custom Discord RPC boards backed by Jetpack DataStore and an
 * in-memory snapshot guarded against concurrent mutations.
 */
@Singleton
class CustomRpcBoardManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val dataStore: DataStore<Preferences> = appContext.customRpcBoardsDataStore

    private val boardsKey = stringPreferencesKey("custom_rpc_boards")
    private val activeBoardIdKey = stringPreferencesKey("custom_rpc_active_board_id")

    private val json = Json { ignoreUnknownKeys = true }
    private val stateMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _boardsFlow = MutableStateFlow<List<CustomRpcBoardItem>>(emptyList())
    val boardsFlow: StateFlow<List<CustomRpcBoardItem>> = _boardsFlow.asStateFlow()

    @Volatile
    private var _boards: List<CustomRpcBoardItem> = emptyList()

    init {
        scope.launch {
            try {
                loadBoardsFromStore()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep empty list if initial store read fails.
            }
        }
    }

    /** Returns the current in-memory list of boards. */
    fun loadBoards(): List<CustomRpcBoardItem> = _boards

    /** Persists the given boards to DataStore and updates the in-memory copy. */
    suspend fun saveBoards(boards: List<CustomRpcBoardItem>) {
        stateMutex.withLock {
            _boards = boards
            _boardsFlow.value = boards
            persistBoards(boards)
        }
    }

    private suspend fun loadBoardsFromStore() {
        val prefs = dataStore.data.first()
        val stored: String? = prefs[boardsKey]
        if (!stored.isNullOrBlank()) {
            try {
                val decoded = json.decodeFromString(boardListSerializer, stored)
                stateMutex.withLock {
                    _boards = decoded
                    _boardsFlow.value = decoded
                }
            } catch (_: Exception) {
                stateMutex.withLock {
                    _boards = emptyList()
                    _boardsFlow.value = emptyList()
                }
            }
        }
    }

    /** Adds a new board and persists it. */
    suspend fun addBoard(board: CustomRpcBoardItem) {
        stateMutex.withLock {
            val updated = _boards + board
            _boards = updated
            _boardsFlow.value = updated
            persistBoards(updated)
        }
    }

    /** Updates an existing board (matched by id) and persists it. */
    suspend fun updateBoard(board: CustomRpcBoardItem) {
        stateMutex.withLock {
            val updated = _boards.map { if (it.id == board.id) board else it }
            _boards = updated
            _boardsFlow.value = updated
            persistBoards(updated)
        }
    }

    /**
     * Deletes the board with the given id and persists the change.
     * If the deleted board was active, clears the active board id.
     */
    suspend fun deleteBoard(boardId: String) {
        stateMutex.withLock {
            val updated = _boards.filter { it.id != boardId }
            _boards = updated
            _boardsFlow.value = updated
            if (getActiveBoardId() == boardId) {
                setActiveBoardId(null)
            }
            persistBoards(updated)
        }
    }

    /**
     * Moves the board with [boardId] by [delta] positions (-1 = up, +1 = down)
     * within the board list and persists the new order.
     */
    suspend fun moveBoard(boardId: String, delta: Int) {
        stateMutex.withLock {
            val index = _boards.indexOfFirst { it.id == boardId }
            if (index < 0 || delta == 0) return
            val newIndex = (index + delta).coerceIn(0, _boards.lastIndex)
            if (newIndex == index) return
            val mutable = _boards.toMutableList()
            val board = mutable.removeAt(index)
            mutable.add(newIndex, board)
            val updated = mutable.toList()
            _boards = updated
            _boardsFlow.value = updated
            persistBoards(updated)
        }
    }

    /** Returns the board with the given id, or null if not found. */
    fun getBoard(boardId: String): CustomRpcBoardItem? = _boards.firstOrNull { it.id == boardId }

    /** Returns the currently active board id, or null if none. */
    suspend fun getActiveBoardId(): String? {
        val prefs = dataStore.data.first()
        return prefs[activeBoardIdKey]
    }

    /** Sets the active board id and persists it. */
    suspend fun setActiveBoardId(boardId: String?) {
        dataStore.edit { prefs: MutablePreferences ->
            if (boardId == null) {
                prefs.remove(activeBoardIdKey)
            } else {
                prefs[activeBoardIdKey] = boardId
            }
        }
    }

    /** Returns the currently active board, or null if none / not found. */
    suspend fun getActiveBoard(): CustomRpcBoardItem? {
        val id = getActiveBoardId()
        return if (id != null) getBoard(id) else null
    }

    /** Activates the given board: persists it (if not already), sets it as active. */
    suspend fun activateBoard(board: CustomRpcBoardItem) {
        stateMutex.withLock {
            val updated = if (_boards.any { it.id == board.id }) _boards else _boards + board
            _boards = updated
            _boardsFlow.value = updated
            setActiveBoardId(board.id)
            persistBoards(updated)
        }
    }

    /** Deactivates the currently active board (clears the active board id). */
    suspend fun deactivateBoard() {
        setActiveBoardId(null)
    }

    /** Returns true if the board with the given id is currently active. */
    suspend fun isBoardActive(boardId: String): Boolean = getActiveBoardId() == boardId

    private suspend fun persistBoards(boards: List<CustomRpcBoardItem>) {
        dataStore.edit { prefs: MutablePreferences ->
            prefs[boardsKey] = json.encodeToString(boardListSerializer, boards)
        }
    }

    companion object {
        @Volatile
        private var instance: CustomRpcBoardManager? = null

        fun getInstance(context: Context): CustomRpcBoardManager {
            return instance ?: synchronized(this) {
                instance ?: CustomRpcBoardManager(context.applicationContext).also { instance = it }
            }
        }
    }
}