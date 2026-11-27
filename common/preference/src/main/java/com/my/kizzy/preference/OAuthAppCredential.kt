package com.my.kizzy.preference

/**
 * A configured Discord OAuth2 application that can back an RPC socket.
 *
 * [index] is the slot number used in the UI/settings (1, 2 or 3).
 */
data class OAuthAppCredential(
    val index: Int,
    val token: String,
    val appId: String,
)
