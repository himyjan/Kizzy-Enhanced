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

import kotlinx.serialization.Serializable

/**
 * A single RPC instance shown on the home screen.
 *
 * The 3 built-in instances (Media RPC, Manual RPC, Custom RPC) are always
 * present and have fixed ids. User-created clones are stored in
 * [Prefs.HOME_RPC_INSTANCES] and can be renamed, deleted or reordered.
 *
 * Multiple instances of the same [type] run in parallel via [RpcManager] -
 * toggling one instance starts/stops the corresponding service, and the
 * service is shared between instances of the same type.
 */
@Serializable
data class RpcInstance(
    val id: String,
    val type: String, // "media_rpc", "manual_rpc", "custom_rpc"
    val label: String, // user-facing name shown on the card
    val isEnabled: Boolean = false,
)

/** The fixed id of the built-in Media RPC instance. */
const val RPC_INSTANCE_MEDIA = "media_rpc"
/** The fixed id of the built-in Manual RPC instance. */
const val RPC_INSTANCE_MANUAL = "manual_rpc"
/** The fixed id of the built-in Custom RPC instance. */
const val RPC_INSTANCE_CUSTOM = "custom_rpc"

/** The 3 built-in instances that always exist and cannot be deleted. */
val BUILT_IN_INSTANCES: List<RpcInstance> = listOf(
    RpcInstance(id = RPC_INSTANCE_MEDIA, type = "media_rpc", label = "Media RPC"),
    RpcInstance(id = RPC_INSTANCE_MANUAL, type = "manual_rpc", label = "Manual RPC"),
    RpcInstance(id = RPC_INSTANCE_CUSTOM, type = "custom_rpc", label = "Custom RPC"),
)
