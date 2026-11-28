/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetInstalledApps.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.data.utils

import android.content.Context
import android.content.pm.PackageManager

/** UI model representing an installed launchable application and its selection state. */
data class AppsInfo(
    val name: String,
    val pkg: String,
    val isChecked: Boolean = false,
)

/**
 * Queries installed launchable applications on the device, sorted alphabetically
 * by display label.
 */
fun getInstalledApps(
    context: Context,
    isEnabled: (String) -> Boolean,
): List<AppsInfo> {
    val pm = context.packageManager
    val installedApps = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
    }.getOrDefault(emptyList())

    return installedApps
        .asSequence()
        .mapNotNull { app ->
            val launchIntent = runCatching { pm.getLaunchIntentForPackage(app.packageName) }.getOrNull()
            if (launchIntent == null) return@mapNotNull null
            val label = runCatching { app.loadLabel(pm).toString() }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: app.packageName
            AppsInfo(
                name = label,
                pkg = app.packageName,
                isChecked = runCatching { isEnabled(app.packageName) }.getOrDefault(false),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .toList()
}