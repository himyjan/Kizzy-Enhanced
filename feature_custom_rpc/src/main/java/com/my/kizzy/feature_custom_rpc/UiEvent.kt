/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * UiEvents.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_custom_rpc

import com.my.kizzy.domain.model.rpc.RpcConfig
import java.io.File

sealed interface UiEvent {
    data object TriggerBottomSheet : UiEvent
    data object TriggerActivityTypeDropDownMenu : UiEvent
    data object TriggerStatusDropDownMenu : UiEvent
    data object TriggerPlatformDropDownMenu : UiEvent
    data class SetFieldsFromConfig(val config: RpcConfig) : UiEvent
    data object TriggerStartTimeStampsDialog : UiEvent
    data object TriggerStopTimeStampsDialog : UiEvent
    data class UploadImage(val file: File, val callback: (result: String) -> Unit) : UiEvent

    sealed interface SheetEvent : UiEvent {
        data object TriggerLoadDialog : SheetEvent
        data object TriggerShareDialog : SheetEvent
        data object TriggerSaveDialog : SheetEvent
        data object TriggerDeleteDialog : SheetEvent
        data object TriggerPreviewDialog : SheetEvent
        data object TriggerStoragePermissionRequest : SheetEvent
        data object ClearAllFields : SheetEvent
    }
}