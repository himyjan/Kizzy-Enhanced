/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DialogActivity.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.my.kizzy.resources.R

class DialogActivity : Activity() {

    private var finishingFromSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        showDialog()
    }

    private fun startRpcService(serviceClass: Class<*>) {
        val intent = Intent(this, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showDialog() {
        val rpc = arrayOf(
            getString(R.string.main_appDetection),
            getString(R.string.main_mediaRpc),
            getString(R.string.main_ManualRpc)
        )
        val dialog = MaterialAlertDialogBuilder(
            ContextThemeWrapper(this, com.my.kizzy.feature_rpc_base.R.style.MyTileDialogTheme)
        )
            .setTitle(getString(R.string.choose_rpc))
            .setSingleChoiceItems(rpc, -1) { dialogInterface, which ->
                finishingFromSelection = true
                when (which) {
                    0 -> startRpcService(AppDetectionService::class.java)
                    1 -> startRpcService(MediaRpcService::class.java)
                    2 -> startRpcService(ManualRpc::class.java)
                }
                dialogInterface.dismiss()
                finish()
            }
            .create()
        dialog.setOnDismissListener {
            if (!finishingFromSelection && !isFinishing) {
                finish()
            }
        }
        dialog.show()
    }
}