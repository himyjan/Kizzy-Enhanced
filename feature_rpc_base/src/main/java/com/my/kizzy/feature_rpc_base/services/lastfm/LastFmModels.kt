package com.my.kizzy.feature_rpc_base.services.lastfm

import kotlinx.serialization.Serializable

@Serializable
data class LastFmSession(
    val name: String,
    val key: String,
    val subscriber: Int
)

@Serializable
data class LastFmSessionResponse(
    val session: LastFmSession
)

@Serializable
data class LastFmTokenResponse(
    val token: String
)

@Serializable
data class LastFmError(
    val error: Int,
    val message: String
)

@Serializable
data class LastFmScrobbleResponse(
    val scrobbles: Scrobbles
) {
    @Serializable
    data class Scrobbles(
        val accepted: Int,
        val ignored: Int,
        val rejected: Int
    )
}
