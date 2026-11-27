/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RpcInstance.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  ******************************************************************
 *
 */

package com.my.kizzy.preference

import android.content.Context
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A single RPC instance shown on the home screen.
 *
 * The 3 built-in instances (Media RPC, Manual RPC, Custom RPC) are always
 * present and have fixed ids. User-created instances are stored in
 * [Prefs.HOME_RPC_INSTANCES] and can be renamed, deleted or reordered.
 *
 * Every instance owns its own Discord gateway connection. [id] is used as the
 * session key in `MultiRpcManager`, so toggling one instance never touches the
 * socket of another - even when several instances have the same [type].
 */
@Serializable
data class RpcInstance(
    val id: String,
    val type: String, // "media_rpc", "manual_rpc", "custom_rpc"
    val label: String, // user-facing name shown on the card
    val isEnabled: Boolean = false,
    /** Serialized RpcConfig for Custom/Manual RPC; blank means "use global prefs". */
    val config: String = "",
    /** Optional per-instance Discord application ID; blank means "use type default". */
    val appId: String = "",
    /**
     * Which configured OAuth2 application (1, 2 or 3) backs this instance.
     * -1 means "not assigned yet" and is resolved round-robin when created.
     */
    val oauthIndex: Int = -1,
) {
    companion object {
        /** Generates a globally unique instance id (never collides, unlike a timestamp). */
        fun newId(type: String): String = "${type}_instance_${UUID.randomUUID()}"
    }
}

/** The fixed id of the built-in Media RPC instance. */
const val RPC_INSTANCE_MEDIA = "media_rpc"
/** The fixed id of the built-in Manual RPC instance. */
const val RPC_INSTANCE_MANUAL = "manual_rpc"
/** The fixed id of the built-in Custom RPC instance. */
const val RPC_INSTANCE_CUSTOM = "custom_rpc"

/** The 3 built-in instances that always exist and cannot be deleted. */
val BUILT_IN_INSTANCES: List<RpcInstance> = listOf(
    // Keep the historical slot mapping: Media -> app 1, Custom -> app 2, Manual -> app 3.
    RpcInstance(id = RPC_INSTANCE_MEDIA, type = "media_rpc", label = "Media RPC", oauthIndex = 1),
    RpcInstance(id = RPC_INSTANCE_MANUAL, type = "manual_rpc", label = "Manual RPC", oauthIndex = 3),
    RpcInstance(id = RPC_INSTANCE_CUSTOM, type = "custom_rpc", label = "Custom RPC", oauthIndex = 2),
)

/** Resolves the display label for an RPC instance. Built-in instances use translated
 * string resources; user-created clones return their stored [label]. */
fun RpcInstance.displayLabel(context: Context): String {
    return when (id) {
        RPC_INSTANCE_MEDIA -> context.getString(com.my.kizzy.resources.R.string.main_mediaRpc)
        RPC_INSTANCE_MANUAL -> context.getString(com.my.kizzy.resources.R.string.main_ManualRpc)
        RPC_INSTANCE_CUSTOM -> context.getString(com.my.kizzy.resources.R.string.main_customRpc)
        else -> label
    }
}