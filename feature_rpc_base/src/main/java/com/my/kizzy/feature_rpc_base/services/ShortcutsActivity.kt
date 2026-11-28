/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ShortcutsActivity.kt is part of Kizzy
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
import android.widget.Toast
import com.my.kizzy.resources.R

class ShortcutsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        handleShortcut()
    }

    private fun startRpcService(serviceClass: Class<*>) {
        val intent = Intent(this, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopAllRpcServices() {
        stopService(Intent(this, AppDetectionService::class.java))
        stopService(Intent(this, MediaRpcService::class.java))
        stopService(Intent(this, ManualRpc::class.java))
    }

    private fun handleShortcut() {
        when (intent?.action) {
            Intents.START_APP_DETECTION_SHORTCUT -> {
                stopAllRpcServices()
                startRpcService(AppDetectionService::class.java)
                Toast.makeText(
                    this, getString(R.string.start_appDetection_toast), Toast.LENGTH_SHORT
                ).show()
            }

            Intents.START_MEDIA_RPC_SHORTCUT -> {
                stopAllRpcServices()
                startRpcService(MediaRpcService::class.java)
                Toast.makeText(
                    this, getString(R.string.start_media_rpc_toast), Toast.LENGTH_SHORT
                ).show()
            }

            Intents.START_MANUAL_RPC_SHORTCUT -> {
                stopAllRpcServices()
                startRpcService(ManualRpc::class.java)
                Toast.makeText(
                    this, getString(R.string.start_manual_rpc_toast), Toast.LENGTH_SHORT
                ).show()
            }

            Intents.STOP_RPC -> {
                stopAllRpcServices()
                Toast.makeText(this, getString(R.string.stop_rpc_toast), Toast.LENGTH_SHORT)
                    .show()
            }

            else -> Unit
        }

        finish()
    }

    companion object {
        object Intents {
            const val START_APP_DETECTION_SHORTCUT = "com.my.kizzy.intent.action.START_APP_DETECTION"
            const val START_MEDIA_RPC_SHORTCUT = "com.my.kizzy.intent.action.START_MEDIA_RPC"
            const val START_MANUAL_RPC_SHORTCUT = "com.my.kizzy.intent.action.START_MANUAL_RPC"
            const val STOP_RPC = "com.my.kizzy.intent.action.STOP_RPC"
        }
    }
}