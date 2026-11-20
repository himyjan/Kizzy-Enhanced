/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * LastPresence.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.preference.Prefs
import kotlinx.serialization.json.Json

/**
 * Remembers the presence that was sent last.
 *
 * The RPC preview screen shows what Discord currently displays. Without a
 * memory of the last presence the preview was empty whenever the service was
 * not running - and the media state repository only knows the title and the
 * artist, not the name, the details and the buttons of the presence.
 *
 * The two features (Media RPC and Manual RPC) keep their own memory, so
 * the preview shows the presence of the feature that is actually running.
 */
object LastPresence {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Remembers the presence of the Media RPC.
     */
    fun rememberMedia(rpcConfig: RpcConfig) {
        // Best-effort persist; ignore failures.
        if (runCatching { Prefs[Prefs.LAST_RUN_MEDIA_RPC] = json.encodeToString(RpcConfig.serializer(), rpcConfig) }.isFailure) {
        }
    }

    /**
     * Remembers the presence of the Manual RPC.
     */
    fun rememberExperimental(rpcConfig: RpcConfig) {
        // Best-effort persist; ignore failures.
        if (runCatching { Prefs[Prefs.LAST_RUN_MANUAL_RPC] = json.encodeToString(RpcConfig.serializer(), rpcConfig) }.isFailure) {
        }
    }

    /**
     * The presence of the Media RPC that was sent last, or null.
     */
    fun media(): RpcConfig? = read(Prefs.LAST_RUN_MEDIA_RPC)

    /**
     * The presence of the Manual RPC that was sent last, or null.
     */
    fun experimental(): RpcConfig? = read(Prefs.LAST_RUN_MANUAL_RPC)

    private fun read(key: String): RpcConfig? {
        val raw = Prefs[key, ""]
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString(RpcConfig.serializer(), raw) }.getOrNull()
    }
}
