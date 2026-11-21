/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * CustomRpcBoardItem.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.rpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A custom Discord RPC board that the user can define and activate.
 *
 * Boards are stored in DataStore as a JSON list. One board can be active at a
 * time; when active, its fields override the normal Custom RPC presence for
 * slot "2".
 */
@Serializable
data class CustomRpcBoardItem(
    @SerialName("id")
    val id: String,
    @SerialName("label")
    val label: String,
    @SerialName("applicationId")
    val applicationId: String = "",
    @SerialName("activityType")
    val activityType: Int = 0,
    @SerialName("name")
    val name: String = "",
    @SerialName("details")
    val details: String = "",
    @SerialName("state")
    val state: String = "",
    @SerialName("largeImage")
    val largeImage: String = "",
    @SerialName("smallImage")
    val smallImage: String = "",
    @SerialName("largeText")
    val largeText: String = "",
    @SerialName("smallText")
    val smallText: String = "",
    @SerialName("button1Label")
    val button1Label: String = "",
    @SerialName("button1Url")
    val button1Url: String = "",
    @SerialName("button2Label")
    val button2Label: String = "",
    @SerialName("button2Url")
    val button2Url: String = "",
    @SerialName("timestampMode")
    val timestampMode: String = "NONE",
    @SerialName("customStartMs")
    val customStartMs: Long? = null,
    @SerialName("isPinned")
    val isPinned: Boolean = false,
    @SerialName("sortOrder")
    val sortOrder: Int = 0,
    @SerialName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
