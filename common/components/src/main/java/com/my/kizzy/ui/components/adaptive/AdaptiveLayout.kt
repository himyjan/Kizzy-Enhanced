@file:OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)

package com.my.kizzy.ui.components.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import kotlin.math.max

/**
 * Breakpoint constants for responsive layouts.
 * Based on Jetpack WindowManager 1.5.0 breakpoints:
 * - Compact: < 600dp (phones)
 * - Medium: 600dp - 839dp (large phones, small tablets)
 * - Expanded: ≥ 840dp (tablets, foldables, desktop windows)
 *
 * Note: WindowWidthSizeClass from material3-window-size-class only has COMPACT, MEDIUM, EXPANDED.
 * Jetpack WindowManager 1.5.0's BREAKPOINTS_V2 adds Large (1200dp) and Extra-large (≥1600dp)
 * via androidx.window:window, which is a separate dependency.
 *
 * Reference: https://android-developers.googleblog.com/2026/03/android-devices-extend-seamlessly-to.html
 */
object Breakpoints {
    const val COMPACT_WIDTH_DP = 600
    const val MEDIUM_WIDTH_DP = 840
    const val LARGE_WIDTH_DP = 1200
    const val EXTRA_LARGE_WIDTH_DP = 1600
    const val COMPACT_HEIGHT_DP = 480
    const val EXPANDED_HEIGHT_DP = 600
}

/**
 * Returns true if width is compact (phone).
 */
fun isCompactWidth(widthSizeClass: WindowWidthSizeClass): Boolean {
    return widthSizeClass == WindowWidthSizeClass.Compact
}

/**
 * Returns true if we should show two-pane layout (medium+ width).
 */
fun isTwoPane(widthSizeClass: WindowWidthSizeClass): Boolean {
    return widthSizeClass != WindowWidthSizeClass.Compact
}

/**
 * Helper to get responsive padding based on screen width class.
 */
fun responsivePadding(widthSizeClass: WindowWidthSizeClass): androidx.compose.ui.unit.Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Expanded -> 32.dp
        else -> 16.dp
    }
}

/**
 * Adaptive row layout that switches between row and column based on width class.
 * Compact (phone): Column stacked vertically
 * Medium+ (tablet, desktop): Row side by side
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass,
    content: @Composable () -> Unit
) {
    if (isCompactWidth(widthSizeClass)) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    } else {
        androidx.compose.foundation.layout.Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/**
 * Two-pane layout for foldables/tablets.
 * Shows left pane (navigation) and right pane (content) on medium+ screens.
 */
@Composable
fun TwoPaneLayout(
    widthSizeClass: WindowWidthSizeClass,
    leftPane: @Composable () -> Unit,
    rightPane: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isTwoPane(widthSizeClass)) {
        androidx.compose.foundation.layout.Row(
            modifier = modifier.fillMaxSize()
        ) {
            leftPane()
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f)
            ) {
                rightPane()
            }
        }
    } else {
        androidx.compose.foundation.layout.Column(
            modifier = modifier.fillMaxSize()
        ) {
            leftPane()
            rightPane()
        }
    }
}

/**
 * Adaptive scaffold content that adjusts based on window width class.
 */
@Composable
fun AdaptiveScaffoldContent(
    widthSizeClass: WindowWidthSizeClass,
    content: @Composable () -> Unit
) {
    val padding = responsivePadding(widthSizeClass)

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        content()
    }
}

/**
 * Responsive card item for grid layouts.
 */
@Composable
fun ResponsiveGridItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

/**
 * Returns the optimal column count for a grid based on window width class.
 * Compact: 1 column, Medium: 2 columns, Expanded: 4 columns
 */
fun responsiveColumnCount(widthSizeClass: WindowWidthSizeClass): Int {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 1
        WindowWidthSizeClass.Medium -> 2
        WindowWidthSizeClass.Expanded -> 4
        else -> 1
    }
}
