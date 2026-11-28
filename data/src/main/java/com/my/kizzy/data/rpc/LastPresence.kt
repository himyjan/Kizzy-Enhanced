package com.my.kizzy.data.rpc

import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.preference.Prefs
import kotlinx.serialization.json.Json

/**
 * Remembers the most recently sent presence payload for Media RPC and Manual RPC
 * so UI previews can render the last presence even when services are stopped.
 */
object LastPresence {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun rememberMedia(rpcConfig: RpcConfig) {
        runCatching {
            Prefs[Prefs.LAST_RUN_MEDIA_RPC] = json.encodeToString(RpcConfig.serializer(), rpcConfig)
        }
    }

    fun rememberExperimental(rpcConfig: RpcConfig) {
        runCatching {
            Prefs[Prefs.LAST_RUN_MANUAL_RPC] = json.encodeToString(RpcConfig.serializer(), rpcConfig)
        }
    }

    fun media(): RpcConfig? = read(Prefs.LAST_RUN_MEDIA_RPC)

    fun experimental(): RpcConfig? = read(Prefs.LAST_RUN_MANUAL_RPC)

    private fun read(key: String): RpcConfig? {
        val raw = Prefs[key, ""]
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(RpcConfig.serializer(), raw) }.getOrNull()
    }
}