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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.my.kizzy.data.rpc.CustomRpcBoardItem
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * Provides a DataStore for custom RPC boards, scoped to a Context.
 * Follows the same pattern as Prefs.appPreferencesDataStore.
 */
private val Context.customRpcBoardsDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by
    preferencesDataStore(name = "custom_rpc_boards")

private val boardListSerializer = ListSerializer(CustomRpcBoardItem.serializer())

/**
 * Manages custom Discord RPC boards.
 *
 * Boards are persisted to DataStore as a JSON list under the key
 * "custom_rpc_boards". The active board id is persisted under
 * "custom_rpc_active_board_id".
 */
@Singleton
class CustomRpcBoardManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val dataStore = appContext.customRpcBoardsDataStore

    private val boardsKey = stringPreferencesKey("custom_rpc_boards")
    private val activeBoardIdKey = stringPreferencesKey("custom_rpc_active_board_id")

    private val json = Json { ignoreUnknownKeys = true }

    private var _boards: List<CustomRpcBoardItem> = emptyList()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch(Dispatchers.IO) {
            loadBoardsFromStore()
        }
    }

    /**
     * Returns the current in-memory list of boards.
     */
    fun loadBoards(): List<CustomRpcBoardItem> = _boards

    /**
     * Persists the given boards to DataStore and updates the in-memory copy.
     */
    suspend fun saveBoards(boards: List<CustomRpcBoardItem>) {
        _boards = boards
        persistBoards(boards)
    }

    /**
     * Loads boards from DataStore into memory.
     */
    private suspend fun loadBoardsFromStore() {
        val prefs = appContext.customRpcBoardsDataStore.data.first()
        val stored: String? = prefs[boardsKey]
        if (stored != null && stored.isNotBlank()) {
            try {
                _boards = Json.decodeFromString(boardListSerializer, stored)
            } catch (_: Exception) {
                _boards = emptyList()
            }
        }
    }

    /**
     * Adds a new board and persists it.
     */
    suspend fun addBoard(board: CustomRpcBoardItem) {
        _boards = _boards + board
        persistBoards(_boards)
    }

    /**
     * Updates an existing board (matched by id) and persists it.
     */
    suspend fun updateBoard(board: CustomRpcBoardItem) {
        _boards = _boards.map { if (it.id == board.id) board else it }
        persistBoards(_boards)
    }

    /**
     * Deletes the board with the given id and persists the change.
     * If the deleted board was active, clears the active board id.
     */
    suspend fun deleteBoard(boardId: String) {
        _boards = _boards.filter { it.id != boardId }
        val activeId = getActiveBoardId()
        if (activeId == boardId) {
            setActiveBoardId(null)
        }
        persistBoards(_boards)
    }

    /**
     * Moves the board with [boardId] by [delta] positions (-1 = up, +1 = down)
     * within the board list and persists the new order.
     */
    suspend fun moveBoard(boardId: String, delta: Int) {
        val index = _boards.indexOfFirst { it.id == boardId }
        if (index < 0 || delta == 0) return
        val newIndex = (index + delta).coerceIn(0, _boards.lastIndex)
        if (newIndex == index) return
        val mutable = _boards.toMutableList()
        val board = mutable.removeAt(index)
        mutable.add(newIndex, board)
        _boards = mutable.toList()
        persistBoards(_boards)
    }

    /**
     * Returns the board with the given id, or null if not found.
     */
    fun getBoard(boardId: String): CustomRpcBoardItem? = _boards.firstOrNull { it.id == boardId }

    /**
     * Returns the currently active board id, or null if none.
     */
    suspend fun getActiveBoardId(): String? {
        val prefs = appContext.customRpcBoardsDataStore.data.first()
        return prefs[activeBoardIdKey]
    }

    /**
     * Sets the active board id and persists it.
     */
    suspend fun setActiveBoardId(boardId: String?) {
        appContext.customRpcBoardsDataStore.edit(transform = { prefs: MutablePreferences ->
            if (boardId == null) {
                prefs.remove(activeBoardIdKey)
            } else {
                prefs[activeBoardIdKey] = boardId
            }
        })
    }

    /**
     * Returns the currently active board, or null if none / not found.
     */
    suspend fun getActiveBoard(): CustomRpcBoardItem? {
        val id = getActiveBoardId()
        return if (id != null) getBoard(id) else null
    }

    /**
     * Activates the given board: persists it (if not already), sets it as active.
     */
    suspend fun activateBoard(board: CustomRpcBoardItem) {
        if (!_boards.any { it.id == board.id }) {
            _boards = _boards + board
        }
        setActiveBoardId(board.id)
        persistBoards(_boards)
    }

    /**
     * Deactivates the currently active board (clears the active board id).
     */
    suspend fun deactivateBoard() {
        setActiveBoardId(null)
    }

    /**
     * Returns true if the board with the given id is currently active.
     */
    suspend fun isBoardActive(boardId: String): Boolean {
        return getActiveBoardId() == boardId
    }

    private suspend fun persistBoards(boards: List<CustomRpcBoardItem>) {
        appContext.customRpcBoardsDataStore.edit(transform = { prefs: MutablePreferences ->
            prefs[boardsKey] = Json.encodeToString(boardListSerializer, boards)
        })
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
