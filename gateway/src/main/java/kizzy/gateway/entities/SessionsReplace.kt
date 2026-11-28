package kizzy.gateway.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One client session inside a `SESSIONS_REPLACE` dispatch.
 *
 * Discord sends the full session list whenever any client changes its
 * presence. The list also contains a synthetic `session_id = "all"` entry
 * that aggregates the activities of every session.
 */
@Serializable
data class SessionReplaceEntry(
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("client_info")
    val clientInfo: SessionClientInfo? = null,
    @SerialName("activities")
    val activities: List<SessionActivity> = emptyList(),
)

@Serializable
data class SessionClientInfo(
    @SerialName("os")
    val os: String? = null,
    @SerialName("client")
    val client: String? = null,
)

/**
 * The subset of an activity that is enough to tell whether the presence we
 * sent is still applied. `type = 4` is a Custom Status, not a Rich Presence.
 */
@Serializable
data class SessionActivity(
    @SerialName("name")
    val name: String? = null,
    @SerialName("type")
    val type: Int? = null,
    @SerialName("application_id")
    val applicationId: String? = null,
)