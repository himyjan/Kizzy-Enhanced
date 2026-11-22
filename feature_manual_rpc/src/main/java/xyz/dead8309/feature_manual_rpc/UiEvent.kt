/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * UiEvent.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package xyz.dead8309.feature_manual_rpc

sealed interface UiEvent {
    data class ToggleAppsRpcPart(val enabled: Boolean) : UiEvent
    data class ToggleMediaRpcPart(val enabled: Boolean) : UiEvent
    /** The automatic detection of the activity type, shared with the Media RPC. */
    data class ToggleAutoActivityType(val enabled: Boolean) : UiEvent
    data class SetTemplateName(val value: String) : UiEvent
    data class SetTemplateDetails(val value: String) : UiEvent
    data class SetTemplateState(val value: String) : UiEvent
    data class ToggleAppEnabled(val packageName: String) : UiEvent
    data class SelectAllApps(val packageNames: List<String>) : UiEvent
    data class DeselectAllApps(val packageNames: List<String>) : UiEvent
    /** A null activity type removes the choice, so the detection takes over again. */
    data class SetAppActivityType(val packageName: String, val activityType: Int?) : UiEvent
    data class ToggleShowCoverArt(val enabled: Boolean) : UiEvent
    data class ToggleShowAppIcon(val enabled: Boolean) : UiEvent
    data class ToggleShowArtistName(val enabled: Boolean) : UiEvent
    data class ToggleShowAlbumName(val enabled: Boolean) : UiEvent
    data class ToggleShowPlaybackState(val enabled: Boolean) : UiEvent
    data class ToggleEnableTimestamps(val enabled: Boolean) : UiEvent
    data class ToggleHideOnPause(val enabled: Boolean) : UiEvent
    data class SetPlatform(val value: String) : UiEvent
    object TriggerPlatformDropDownMenu : UiEvent
    data class ToggleButtons(val enabled: Boolean) : UiEvent
    data class SetButton1Text(val value: String) : UiEvent
    data class SetButton1Url(val value: String) : UiEvent
    data class SetButton2Text(val value: String) : UiEvent
    data class SetButton2Url(val value: String) : UiEvent
    data class ToggleUseCustomAppId(val enabled: Boolean) : UiEvent
    data class SetApplicationId(val value: String) : UiEvent
    data class SetTimestampMode(val value: String) : UiEvent
    object TriggerTimestampModeDropDownMenu : UiEvent
    data class SetCustomTimestampStart(val value: String) : UiEvent
    data class SetCustomTimestampEnd(val value: String) : UiEvent
    data class ToggleShowSongAsTitle(val enabled: Boolean) : UiEvent
    data class ToggleShowAppNameInDetails(val enabled: Boolean) : UiEvent
    data class ToggleShowChannelIcon(val enabled: Boolean) : UiEvent
    data class ToggleUseAppsRpc(val enabled: Boolean) : UiEvent
    data class SetTitleSource(val value: String) : UiEvent
    object TriggerTitleSourceDropDown : UiEvent

    // Advanced settings (matching Custom RPC / RpcConfig fields)
    data class SetActivityType(val value: String) : UiEvent
    object TriggerActivityTypeDropDownMenu : UiEvent
    data class SetStatus(val value: String) : UiEvent
    object TriggerStatusDropDownMenu : UiEvent
    data class SetLargeImage(val value: String) : UiEvent
    data class SetSmallImage(val value: String) : UiEvent
    data class SetLargeText(val value: String) : UiEvent
    data class SetSmallText(val value: String) : UiEvent
    data class SetStreamUrl(val value: String) : UiEvent
    data class SetPartyCurrentSize(val value: String) : UiEvent
    data class SetPartyMaxSize(val value: String) : UiEvent
    data class UploadImage(val file: java.io.File, val callback: (String) -> Unit) : UiEvent

    // RPC link fields (details_url, state_url, large_url, small_url)
    data class SetDetailsUrl(val value: String) : UiEvent
    data class SetStateUrl(val value: String) : UiEvent
    data class SetLargeUrl(val value: String) : UiEvent
    data class SetSmallUrl(val value: String) : UiEvent
}
