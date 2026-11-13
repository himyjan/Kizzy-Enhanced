package kizzy.gateway.entities.presence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActivityButton(
    @SerialName("label")
    val label: String,
    @SerialName("url")
    val url: String
)

@Serializable
data class Activity(
    @SerialName("name")
    val name: String?,
    @SerialName("state")
    val state: String? = null,
    @SerialName("details")
    val details: String? = null,
    @SerialName("party")
    val party: Party? = null,
    @SerialName("type")
    val type: Int? = 0,
    @SerialName("platform")
    val platform: String? = null,
    @SerialName("timestamps")
    val timestamps: Timestamps? = null,
    @SerialName("assets")
    val assets: Assets? = null,
    @SerialName("buttons")
    // Discord's Gateway PRESENCE_UPDATE (client-presence / self-bot) format uses
    // an array of LABEL STRINGS here; the URLs live in metadata.button_urls.
    // The RPC/IPC format [{label, url}] is a different protocol and is rejected
    // by newer Discord clients, which drops the whole activity.
    val buttons: List<String>? = null,
    @SerialName("metadata")
    val metadata: Metadata? = null,
    @SerialName("application_id")
    val applicationId: String? = null,
    @SerialName("url")
    val url: String? = null
)
