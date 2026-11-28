/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ActivityType.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  *  permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.my.kizzy.resources.R

/**
 * The Discord activity types.
 *
 * The ids are mirrored from `com.my.kizzy.data.rpc.Constants.ACTIVITY_TYPE`. The
 * components module deliberately does not depend on the data module, so the ids
 * are repeated here instead of imported.
 */
object ActivityType {
    const val PLAYING = 0
    const val STREAMING = 1
    const val LISTENING = 2
    const val WATCHING = 3

    /** Valid SDK id, but a profile status rather than a Rich Presence activity. */
    const val CUSTOM_STATUS = 4
    const val COMPETING = 5

    /** Valid SDK id, only rendered while the user has an active voice/hang state. */
    const val HANG_STATUS = 6

    /** Sentinel meaning "let the automatic detection decide". Mirrors Prefs.ACTIVITY_TYPE_AUTO. */
    const val AUTO = -1

    /**
     * Every type the user may pick for a media/app presence. [CUSTOM_STATUS] and
     * [HANG_STATUS] are deliberately excluded: Discord does not render them as a
     * Rich Presence card, so picking one would make the activity disappear.
     */
    val ALL = listOf(PLAYING, STREAMING, LISTENING, WATCHING, COMPETING)

    /** Every id defined by `discordpp::ActivityTypes`, including non-presence ones. */
    val KNOWN: Set<Int> = setOf(
        PLAYING, STREAMING, LISTENING, WATCHING, CUSTOM_STATUS, COMPETING, HANG_STATUS,
    )

    fun isKnown(type: Int): Boolean = type in KNOWN

    fun label(type: Int): String = when (type) {
        PLAYING -> "Playing"
        STREAMING -> "Streaming"
        LISTENING -> "Listening"
        WATCHING -> "Watching"
        CUSTOM_STATUS -> "Custom Status"
        COMPETING -> "Competing"
        HANG_STATUS -> "Hang Status"
        else -> "Auto"
    }

    @DrawableRes
    fun icon(type: Int): Int = when (type) {
        PLAYING -> R.drawable.ic_sports_esports
        STREAMING -> R.drawable.ic_live_tv
        LISTENING -> R.drawable.ic_headphones
        WATCHING -> R.drawable.ic_remove_red_eye
        CUSTOM_STATUS -> R.drawable.ic_edit
        COMPETING -> R.drawable.ic_sports_esports
        HANG_STATUS -> R.drawable.ic_headphones
        else -> R.drawable.ic_edit
    }

    @Composable
    fun color(type: Int): Color = when (type) {
        PLAYING -> Color(0xFF1976D2)
        STREAMING -> Color(0xFF9C27B0)
        LISTENING -> Color(0xFF43A047)
        WATCHING -> Color(0xFFFF9800)
        CUSTOM_STATUS -> Color(0xFF607D8B)
        COMPETING -> Color(0xFFF44336)
        HANG_STATUS -> Color(0xFF00897B)
        else -> MaterialTheme.colorScheme.primary
    }
}

/**
 * A chip that shows which activity type an app is reported as and lets the user
 * pick a different one.
 *
 * @param selectedTypeId the type the user picked, null when the automatic
 *        detection is in charge
 * @param detectedTypeId the type the automatic detection came up with
 * @param onSelect called with the new type, or with null to go back to the
 *        automatic detection
 */
@Composable
fun ActivityTypeChip(
    selectedTypeId: Int?,
    detectedTypeId: Int,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // AUTO (-1) behaves like "no explicit choice": the detected type is displayed
    // and the "Auto" entry in the menu is checked.
    val isAuto = selectedTypeId == null || selectedTypeId == ActivityType.AUTO
    val effectiveType = if (isAuto) detectedTypeId else selectedTypeId
    val typeColor = ActivityType.color(effectiveType)

    Box(modifier) {
        SuggestionChip(
            onClick = { expanded = true },
            label = { Text(ActivityType.label(effectiveType)) },
            icon = {
                Icon(
                    painter = painterResource(ActivityType.icon(effectiveType)),
                    contentDescription = null,
                    tint = typeColor,
                )
            },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = typeColor.copy(alpha = 0.2f),
                labelColor = typeColor,
            ),
            border = null,
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(250.dp),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.auto_detect, ActivityType.label(detectedTypeId)),
                        color = typeColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(ActivityType.icon(detectedTypeId)),
                        contentDescription = null,
                        tint = typeColor,
                    )
                },
                trailingIcon = {
                    if (isAuto) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            HorizontalDivider()
            ActivityType.ALL.forEach { type ->
                val color = ActivityType.color(type)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = ActivityType.label(type),
                            color = color,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(ActivityType.icon(type)),
                            contentDescription = null,
                            tint = color,
                        )
                    },
                    trailingIcon = {
                        if (!isAuto && selectedTypeId == type) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * A preference row that lets the user pick the activity type of an app.
 *
 * @param selectedTypeId the type that is currently in use
 * @param onSelect called with the type the user picked
 */
@Composable
fun ActivityTypePreference(
    title: String,
    description: String? = null,
    selectedTypeId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            ActivityTypeChip(
                selectedTypeId = selectedTypeId,
                detectedTypeId = selectedTypeId,
                // "Auto" in the menu means "no explicit choice". It used to be
                // mapped back to the current type, so the Auto entry did nothing
                // and the automatic detection could never be re-enabled.
                onSelect = { onSelect(it ?: ActivityType.AUTO) },
            )
        }
    }
}