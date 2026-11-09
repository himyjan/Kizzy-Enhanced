/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * DynamicSvg.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_settings.style.svg

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.TonalPalettes
import com.my.kizzy.ui.theme.LocalDarkTheme
import java.nio.charset.StandardCharsets

@Composable
fun DynamicSVGImage(
    modifier: Modifier = Modifier,
    contentDescription: String,
    svgString: String,
    tonalPalettes: TonalPalettes = LocalTonalPalettes.current,
    isDarkTheme: Boolean = LocalDarkTheme.current.isDarkTheme()
) {
    val dynamicSvg = svgString.parseDynamicColor(
        tonalPalettes = tonalPalettes,
        isDarkTheme = isDarkTheme
    )
    val svgBytes = dynamicSvg.toByteArray(StandardCharsets.UTF_8)

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(svgBytes)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
    )
}
