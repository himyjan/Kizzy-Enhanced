package com.my.kizzy.preference

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A saved Discord application used as an independent RPC connection.
 *
 * The whole list is persisted as one JSON string under [Prefs.OAUTH2_APPS],
 * which is encrypted with Tink before being written to disk.
 */
@Serializable
data class OAuthApp(
    val id: String,
    val label: String,
    /** Discord application client ID. */
    val clientId: String,
    /** Gateway token for this application (sensitive). */
    val token: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    /** Stable 1-based slot used for round-robin assignment. */
    val index: Int = 1,
) {
    companion object {
        fun newId(): String = "oauth_app_${UUID.randomUUID()}"
    }
}
