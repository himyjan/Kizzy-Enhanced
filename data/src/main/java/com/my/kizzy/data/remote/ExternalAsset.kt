/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ExternalAsset.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalAsset(
    @SerialName("external_asset_path")
    val externalAssetPath: String = "",
    @SerialName("url")
    val url: String = "",
)

@Serializable
data class ExternalAssetsRequest(
    @SerialName("urls")
    val urls: List<String>,
)

@Serializable
data class ExternalAssetsResponse(
    @SerialName("data")
    val data: List<ExternalAsset> = emptyList(),
)