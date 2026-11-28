package com.my.kizzy.feature_settings.style

/**
 * source: https://github.com/JunkFood02/Seal
 */

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.google.android.material.color.DynamicColors
import com.kyant.monet.Hct
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.PaletteStyle
import com.kyant.monet.TonalPalettes
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.a1
import com.kyant.monet.a2
import com.kyant.monet.a3
import com.kyant.monet.n2
import com.kyant.monet.toColor
import com.my.kizzy.feature_settings.style.svg.DynamicSVGImage
import com.my.kizzy.feature_settings.style.svg.PALETTE
import com.my.kizzy.preference.DEFAULT_SEED_COLOR
import com.my.kizzy.preference.Prefs
import com.my.kizzy.preference.Prefs.CUSTOM_THEME_COLOR
import com.my.kizzy.preference.ThemeStyle
import com.my.kizzy.preference.modifyThemeSeedColor
import com.my.kizzy.preference.modifyThemeStyle
import com.my.kizzy.preference.palettesMap
import com.my.kizzy.preference.switchDynamicColor
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.BackButton
import com.my.kizzy.ui.components.SettingItem
import com.my.kizzy.ui.components.Subtitle
import com.my.kizzy.ui.components.dialog.TextFieldDialog
import com.my.kizzy.ui.components.preference.PreferenceSingleChoiceItem
import com.my.kizzy.ui.components.preference.PreferenceSwitch
import com.my.kizzy.ui.theme.LocalDarkTheme
import com.my.kizzy.ui.theme.LocalDynamicColorSwitch
import com.my.kizzy.ui.theme.LocalPaletteStyleIndex
import com.my.kizzy.ui.theme.LocalSeedColor
import com.my.kizzy.ui.theme.LocalThemeStyle
import com.my.kizzy.ui.theme.autoDark

val colorList = listOf(
    Color(DEFAULT_SEED_COLOR),
    Color(0xFFAF92F1),
    Color.Blue,
    Hct(60.0, 100.0, 70.0).toSrgb().toColor(),
    Hct(125.0, 50.0, 60.0).toSrgb().toColor(),
    Color.Cyan,
    Color.Yellow,
    Color.Magenta
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Appearance(
    onBackPressed: () -> Unit,
    navigateToDarkTheme: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.display),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    BackButton(onClick = onBackPressed)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .aspectRatio(1.38f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.inverseOnSurface)
                        .clickable { },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DynamicSVGImage(
                        modifier = Modifier
                            .padding(60.dp)
                            .size(200.dp),
                        contentDescription = "palette",
                        svgString = PALETTE
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                val initialPage = colorList.indexOf(Color(LocalSeedColor.current)).let {
                    if (it == -1) 1 else it
                }
                val pagerState = rememberPagerState(
                    pageCount = { colorList.size },
                    initialPage = initialPage
                )
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics { },
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { page ->
                    Row {
                        ColorButtons(colorList[page], last = page == colorList.lastIndex)
                    }
                }
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics { }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pagerState.pageCount) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(if (isSelected) 8.dp else 6.dp)
                        )
                    }
                }
            }
            Subtitle(text = stringResource(id = R.string.theme_style))
            PreferenceSingleChoiceItem(
                text = stringResource(R.string.theme_style_kizzy),
                selected = LocalThemeStyle.current == ThemeStyle.KIZZY,
            ) { modifyThemeStyle(ThemeStyle.KIZZY) }
            PreferenceSingleChoiceItem(
                text = stringResource(R.string.theme_style_discord_dark),
                selected = LocalThemeStyle.current == ThemeStyle.DISCORD_DARK,
            ) { modifyThemeStyle(ThemeStyle.DISCORD_DARK) }
            PreferenceSingleChoiceItem(
                text = stringResource(R.string.theme_style_discord_light),
                selected = LocalThemeStyle.current == ThemeStyle.DISCORD_LIGHT,
            ) { modifyThemeStyle(ThemeStyle.DISCORD_LIGHT) }
            if (DynamicColors.isDynamicColorAvailable()) {
                PreferenceSwitch(
                    title = stringResource(id = R.string.dynamic_color),
                    description = stringResource(id = R.string.dynamic_color_desc),
                    icon = vectorResource(R.drawable.ic_palette),
                    isChecked = LocalDynamicColorSwitch.current,
                    onClick = { switchDynamicColor() }
                )
            }

            SettingItem(
                title = stringResource(id = R.string.dark_theme),
                description = LocalDarkTheme.current.getDarkThemeDesc(),
                icon = vectorResource(R.drawable.ic_dark_mode),
                onClick = navigateToDarkTheme
            )
        }
    }
}

@Composable
fun RowScope.ColorButtons(color: Color, last: Boolean = false) {
    palettesMap.forEach { (key, style) ->
        ColorButton(color = color, index = key, tonalStyle = style, custom = key == 3 && last)
    }
}

@Composable
fun RowScope.ColorButton(
    modifier: Modifier = Modifier,
    color: Color = Color.Green,
    index: Int = 0,
    tonalStyle: PaletteStyle = PaletteStyle.TonalSpot,
    custom: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    var customColorValue by remember { mutableStateOf(Prefs[CUSTOM_THEME_COLOR, ""]) }
    val tonalPalettes = if (custom) {
        customColorValue.color.toTonalPalettes(tonalStyle)
    } else {
        color.toTonalPalettes(tonalStyle)
    }
    val isSelect = !LocalDynamicColorSwitch.current &&
        LocalSeedColor.current == color.toArgb() &&
        LocalPaletteStyleIndex.current == index

    TextFieldDialog(
        visible = showDialog,
        title = stringResource(R.string.primary_color),
        icon = vectorResource(R.drawable.ic_palette),
        value = customColorValue,
        placeholder = stringResource(R.string.primary_color_hint),
        onValueChange = { customColorValue = it },
        onDismissRequest = { showDialog = false },
        onConfirm = { hexString ->
            customColorValue = hexString
            switchDynamicColor(enabled = false)
            Prefs[CUSTOM_THEME_COLOR] = hexString
            modifyThemeSeedColor(customColorValue.color.toArgb(), index)
            showDialog = false
        }
    )
    ColorButtonImpl(
        modifier = modifier,
        tonalPalettes = tonalPalettes,
        cardColor = 95.autoDark(LocalDarkTheme.current.isDarkTheme()).n2,
        isSelected = { isSelect }
    ) {
        if (custom) {
            showDialog = true
        } else {
            switchDynamicColor(enabled = false)
            modifyThemeSeedColor(color.toArgb(), index)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.ColorButtonImpl(
    modifier: Modifier = Modifier,
    isSelected: () -> Boolean = { false },
    tonalPalettes: TonalPalettes,
    cardColor: Color,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit = {}
) {
    val containerSize by animateDpAsState(
        targetValue = if (isSelected()) 28.dp else 0.dp,
        label = "containerSizeAnimation"
    )
    val iconSize by animateDpAsState(
        targetValue = if (isSelected()) 16.dp else 0.dp,
        label = "iconSizeAnimation"
    )

    Surface(
        modifier = modifier
            .padding(4.dp)
            .sizeIn(maxHeight = 80.dp, maxWidth = 80.dp, minHeight = 64.dp, minWidth = 64.dp)
            .weight(1f, false)
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        onClick = onClick
    ) {
        CompositionLocalProvider(LocalTonalPalettes provides tonalPalettes) {
            val color1 = 80.a1
            val color2 = 90.a2
            val color3 = 60.a3
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .drawBehind { drawCircle(color1) }
                        .align(Alignment.Center)
                ) {
                    Surface(
                        color = color2,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(24.dp)
                    ) {}
                    Surface(
                        color = color3,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                    ) {}
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .size(containerSize)
                            .drawBehind { drawCircle(containerColor) },
                    ) {
                        Icon(
                            imageVector = vectorResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier
                                .size(iconSize)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

val String.color: Color
    get() = runCatching { Color(trim().toColorInt()) }.getOrDefault(Color.Transparent)