/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * RequestStoragePermission.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_custom_rpc.components.sheet

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.my.kizzy.resources.R

@Composable
internal fun RequestStoragePermissionDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    val activity = context as Activity
    val shouldShowRationale = remember {
        mutableStateOf<Boolean>(
            activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        )
    }
    var storagePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val storageLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                storagePermissionGranted = isGranted
                shouldShowRationale.value = activity.shouldShowRequestPermissionRationale(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            })
    if (!storagePermissionGranted) {
        if (shouldShowRationale.value) {
            // Educational dialog — first denial, explain why permission is needed
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.permission_required))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(id = R.string.storage_access))
                    }
                },
                text = {
                    Text(text = stringResource(id = R.string.request_for_storage_access))
                },
                confirmButton = {
                    Button(onClick = { storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }) {
                        Text(text = stringResource(id = R.string.grant_permission))
                    }
                },
                dismissButton = {
                    Button(onClick = { onDismiss() }) {
                        Text(text = stringResource(id = android.R.string.cancel))
                    }
                },
            )
        } else {
            // Permanently denied — route to App Settings
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(id = R.string.permission_required))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = stringResource(id = R.string.storage_access))
                    }
                },
                text = {
                    Text(text = stringResource(id = R.string.request_for_storage_access))
                },
                confirmButton = {
                    Button(onClick = { context.openAppSettings() }) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                },
                dismissButton = {
                    Button(onClick = { onDismiss() }) {
                        Text(text = stringResource(id = android.R.string.cancel))
                    }
                },
            )
        }
    }
}
