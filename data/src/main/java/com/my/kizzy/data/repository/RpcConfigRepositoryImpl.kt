package com.my.kizzy.data.repository

import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.repository.RpcConfigRepository
import com.my.kizzy.preference.Prefs
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RpcConfigRepositoryImpl @Inject constructor() : RpcConfigRepository {

    override suspend fun getLastRunCustomRpc(): String? =
        withContext(Dispatchers.IO) { Prefs[Prefs.LAST_RUN_CUSTOM_RPC, ""] }

    override suspend fun setLastRunCustomRpc(configJson: String?) =
        withContext(Dispatchers.IO) { Prefs[Prefs.LAST_RUN_CUSTOM_RPC] = configJson }

    override suspend fun getLastRunConsoleRpc(): String? =
        withContext(Dispatchers.IO) { Prefs[Prefs.LAST_RUN_CONSOLE_RPC, ""] }

    override suspend fun setLastRunConsoleRpc(configJson: String?) =
        withContext(Dispatchers.IO) { Prefs[Prefs.LAST_RUN_CONSOLE_RPC] = configJson }

    override suspend fun getRpcButtons(): String? =
        withContext(Dispatchers.IO) { Prefs[Prefs.RPC_BUTTONS_DATA, ""] }

    override suspend fun setRpcButtons(buttonsJson: String?) =
        withContext(Dispatchers.IO) { Prefs[Prefs.RPC_BUTTONS_DATA] = buttonsJson }

    override suspend fun getApplyFieldsFromLastRunRpc(): Boolean =
        withContext(Dispatchers.IO) { Prefs[Prefs.APPLY_FIELDS_FROM_LAST_RUN_RPC, false] }

    override suspend fun setApplyFieldsFromLastRunRpc(apply: Boolean) =
        withContext(Dispatchers.IO) { Prefs[Prefs.APPLY_FIELDS_FROM_LAST_RUN_RPC] = apply }

    override suspend fun getMediaRpcEnabled(): Boolean =
        withContext(Dispatchers.IO) { Prefs[Prefs.MEDIA_RPC_ENABLED, false] }

    override suspend fun setMediaRpcEnabled(enabled: Boolean) =
        withContext(Dispatchers.IO) { Prefs[Prefs.MEDIA_RPC_ENABLED] = enabled }

    override suspend fun getCustomActivityType(): Int =
        withContext(Dispatchers.IO) { Prefs[Prefs.CUSTOM_ACTIVITY_TYPE, 0] }

    override suspend fun setCustomActivityType(type: Int) =
        withContext(Dispatchers.IO) { Prefs[Prefs.CUSTOM_ACTIVITY_TYPE] = type }

    override suspend fun getCustomActivityStatus(): String =
        withContext(Dispatchers.IO) { Prefs[Prefs.CUSTOM_ACTIVITY_STATUS, "dnd"] }

    override suspend fun setCustomActivityStatus(status: String) =
        withContext(Dispatchers.IO) { Prefs[Prefs.CUSTOM_ACTIVITY_STATUS] = status }
}
