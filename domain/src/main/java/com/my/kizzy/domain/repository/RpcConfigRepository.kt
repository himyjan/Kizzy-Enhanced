package com.my.kizzy.domain.repository

import com.my.kizzy.domain.model.rpc.RpcConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for persisting and reading RPC configuration.
 *
 * All RPC config persistence goes through this repository instead of directly
 * accessing [com.my.kizzy.preference.Prefs], which centralizes the storage
 * logic and makes it easier to migrate away from DataStore in the future.
 */
interface RpcConfigRepository {

    /** The last run custom RPC config JSON string, or null if none. */
    suspend fun getLastRunCustomRpc(): String?

    /** Persists the last run custom RPC config JSON string. */
    suspend fun setLastRunCustomRpc(configJson: String?)

    /** The last run console RPC config JSON string, or null if none. */
    suspend fun getLastRunConsoleRpc(): String?

    /** Persists the last run console RPC config JSON string. */
    suspend fun setLastRunConsoleRpc(configJson: String?)

    /** The RPC buttons configuration, or null if none. */
    suspend fun getRpcButtons(): String?

    /** Persists the RPC buttons configuration JSON string. */
    suspend fun setRpcButtons(buttonsJson: String?)

    /** Whether fields from the last run RPC config should be applied. */
    suspend fun getApplyFieldsFromLastRunRpc(): Boolean

    /** Persists whether fields from the last run RPC config should be applied. */
    suspend fun setApplyFieldsFromLastRunRpc(apply: Boolean)

    /** Whether Media RPC is enabled. */
    suspend fun getMediaRpcEnabled(): Boolean

    /** Persists whether Media RPC is enabled. */
    suspend fun setMediaRpcEnabled(enabled: Boolean)

    /** The custom activity type. */
    suspend fun getCustomActivityType(): Int

    /** Persists the custom activity type. */
    suspend fun setCustomActivityType(type: Int)

    /** The custom activity status. */
    suspend fun getCustomActivityStatus(): String

    /** Persists the custom activity status. */
    suspend fun setCustomActivityStatus(status: String)
}
