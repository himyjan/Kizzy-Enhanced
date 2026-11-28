/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * KizzyTileService.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_rpc_base.services

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.mutableStateOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.my.kizzy.feature_rpc_base.AppUtils
import com.my.kizzy.preference.Prefs
import com.my.kizzy.resources.R

class KizzyTileService : TileService() {

    private fun startRpcService(serviceClass: Class<*>) {
        val intent = Intent(this, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onClick() {
        super.onClick()
        val ctx = this
        when (qsTile.state) {
            Tile.STATE_ACTIVE -> {
                Prefs[Prefs.ACTIVE_RPC_TYPE] = ""
                Prefs[Prefs.MEDIA_RPC_ENABLED] = false
                ctx.stopService(Intent(ctx, AppDetectionService::class.java))
                ctx.stopService(Intent(ctx, MediaRpcService::class.java))
                ctx.stopService(Intent(ctx, ManualRpc::class.java))
                Toast.makeText(ctx, getString(R.string.stop_rpc_toast), Toast.LENGTH_SHORT).show()
            }
            Tile.STATE_INACTIVE -> {
                if (!isLocked) {
                    showDialog(createRpcChoosingDialog(ctx))
                } else {
                    val intent = Intent(ctx, DialogActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivityAndCollapse(intent)
                }
            }
            else -> {}
        }
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        tileAdded.value = true
    }

    private fun createRpcChoosingDialog(ctx: Context): Dialog {
        val rpc = arrayOf(
            getString(R.string.main_appDetection),
            getString(R.string.main_mediaRpc),
            getString(R.string.main_ManualRpc)
        )
        return MaterialAlertDialogBuilder(
            ContextThemeWrapper(ctx, com.my.kizzy.feature_rpc_base.R.style.MyTileDialogTheme)
        )
            .setTitle(getString(R.string.choose_rpc))
            .setSingleChoiceItems(rpc, -1) { dialog, which ->
                when (which) {
                    0 -> {
                        startRpcService(AppDetectionService::class.java)
                        Toast.makeText(ctx, getString(R.string.start_appDetection_toast), Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        startRpcService(MediaRpcService::class.java)
                        Toast.makeText(ctx, getString(R.string.start_media_rpc_toast), Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        startRpcService(ManualRpc::class.java)
                        Toast.makeText(ctx, getString(R.string.start_manual_rpc_toast), Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .create()
    }

    private fun updateTile() {
        if (Prefs[Prefs.TOKEN, ""].isEmpty()) {
            qsTile.state = Tile.STATE_UNAVAILABLE
            qsTile.updateTile()
            return
        }
        when (AppUtils.appDetectionRunning() || AppUtils.mediaRpcRunning() || AppUtils.ManualRpcRunning()) {
            true -> {
                qsTile.state = Tile.STATE_ACTIVE
                qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tile_stop)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    qsTile.subtitle = getSubtitle()
                }
            }
            false -> {
                qsTile.state = Tile.STATE_INACTIVE
                qsTile.icon = Icon.createWithResource(this, R.drawable.ic_tile_play)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    qsTile.subtitle = getString(R.string.off)
                }
            }
        }
        qsTile.updateTile()
    }

    private fun getSubtitle(): String {
        return when {
            AppUtils.appDetectionRunning() -> getString(R.string.main_appDetection)
            AppUtils.mediaRpcRunning() -> getString(R.string.main_mediaRpc)
            else -> getString(R.string.main_ManualRpc)
        }
    }

    companion object {
        val tileAdded = mutableStateOf(false)
    }
}