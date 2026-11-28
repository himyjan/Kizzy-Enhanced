/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ImagePicker.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 */

package xyz.dead8309.feature_manual_rpc.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BrowseFilesButton

@Composable
fun ImagePicker(
    visible: Boolean,
    onDismiss: () -> Unit,
    showProgress: Boolean,
    onImageSelected: (uri: Uri) -> Unit,
) {
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri.value = uri
        }
    }

    LaunchedEffect(visible) {
        if (!visible) {
            imageUri.value = null
        }
    }

    if (visible) {
        UploadDialog(
            image = imageUri,
            onBrowse = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPicked = { uri ->
                onImageSelected(uri)
            },
            onDismiss = {
                if (!showProgress) {
                    onDismiss()
                }
            },
            showProgress = showProgress,
        )
    }
}

@Composable
fun UploadDialog(
    image: MutableState<Uri?>,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
    showProgress: Boolean,
    onPicked: (uri: Uri) -> Unit
) {
    val selectedUri = image.value
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(20.dp),
        confirmButton = {
            if (selectedUri != null) {
                TextButton(
                    enabled = !showProgress,
                    shape = RoundedCornerShape(8.dp),
                    onClick = { onPicked(selectedUri) },
                ) {
                    Text(text = stringResource(R.string.confirm))
                    if (showProgress) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !showProgress,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.upload_image)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrowseFilesButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp)
                ) {
                    onBrowse()
                }
                if (selectedUri != null) {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = null,
                        modifier = Modifier.size(200.dp),
                        error = rememberVectorPainter(vectorResource(R.drawable.ic_broken_image))
                    )
                }
            }
        }
    )
}