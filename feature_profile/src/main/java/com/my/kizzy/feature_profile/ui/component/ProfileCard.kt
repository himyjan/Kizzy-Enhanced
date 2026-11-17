/*
 *
 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * ProfileCard.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************
 *
 *
 */

package com.my.kizzy.feature_profile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.resources.R
import com.my.kizzy.ui.theme.DISCORD_BG
import com.my.kizzy.ui.theme.DISCORD_CARD
import com.my.kizzy.ui.theme.DISCORD_GREEN
import com.my.kizzy.ui.theme.DISCORD_LIGHT_DARK
import com.my.kizzy.ui.theme.DISCORD_RED
import com.my.kizzy.ui.theme.DISCORD_RED
import com.my.kizzy.ui.theme.DISCORD_SUBTEXT
import com.my.kizzy.ui.theme.DISCORD_TEXT
import com.my.kizzy.ui.theme.DISCORD_YELLOW

const val NITRO_ICON = "https://cdn.discordapp.com/badge-icons/2ba85e8026a8614b640c2837bcdfe21b.png"

// Discord's presence dots - brand status palette.
private val ONLINE = DISCORD_GREEN
private val IDLE = DISCORD_YELLOW
private val DND = DISCORD_RED
private val OFFLINE = Color(0xFF80848E)

private fun presenceColor(status: String?): Color? = when (status) {
    "online" -> ONLINE
    "idle" -> IDLE
    "dnd" -> DND
    "offline", "invisible" -> OFFLINE
    else -> null
}

// bannerColor arrives as "#RRGGBB" / "RRGGBB" (sometimes with alpha);
// accentColor as a 0xRRGGBB int without alpha.
private fun parseHexColor(value: String?): Color? {
    val hex = value?.removePrefix("#")?.trim().orEmpty()
    if (hex.isEmpty()) return null
    return try {
        Color((hex.toLong(16) or 0xFF000000L).toInt())
    } catch (_: NumberFormatException) {
        null
    }
}

private fun User.accentComposeColor(): Color? =
    accentColor?.let { Color(it or 0xFF000000.toInt()) }

/**
 * Discord's mobile profile: full-bleed banner, oversized avatar with a
 * presence dot overlapping the banner, display name, username with the
 * custom-status chip, badge strip, collapsible bio and the activity card.
 */
@Composable
fun ProfileCard(
    user: User?,
    borderColors: List<Color> = listOf(DISCORD_TEXT, DISCORD_SUBTEXT),
    backgroundColors: List<Color> = listOf(DISCORD_CARD, DISCORD_CARD),
    padding: Dp = 0.dp,
    type: String = stringResource(id = R.string.user_profile_rpc_name),
    rpcConfig: RpcConfig? = null,
    showTs: Boolean = true,
    onAvatarClick: () -> Unit = {}
) {
    if (user == null) return

    val accent = user.accentComposeColor()
    val ringColor = accent ?: borderColors.firstOrNull() ?: DISCORD_TEXT
    val bannerFallback = parseHexColor(user.bannerColor)
        ?: accent
        ?: DISCORD_LIGHT_DARK

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
    ) {
        // --- Banner + overlapping avatar + presence dot -----------------
        // The box is taller than the banner so the avatar can hang half
        // below it, exactly like Discord.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(188.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                val bannerImage = user.getBannerImage()
                if (bannerImage != null) {
                    AsyncImage(
                        model = bannerImage,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.broken_image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(bannerFallback),
                    )
                }
            }

            val avatarImage = user.getAvatarImage()
            // The dot must live OUTSIDE the circle clip, otherwise it gets cut
            // away at the bottom-right corner and is never visible.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .size(96.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .border(width = 5.dp, color = ringColor, shape = CircleShape)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarImage != null) {
                        AsyncImage(
                            model = avatarImage,
                            placeholder = painterResource(id = R.drawable.error_avatar),
                            error = painterResource(id = R.drawable.error_avatar),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(86.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "User Avatar",
                            modifier = Modifier.size(86.dp).clip(CircleShape),
                            tint = DISCORD_SUBTEXT,
                        )
                    }
                }

                // Presence dot - driven by the RPC activity status.
                val dotColor = presenceColor(rpcConfig?.status?.takeIf { it.isNotBlank() })
                if (dotColor != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .border(5.dp, DISCORD_CARD, CircleShape)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }

        // --- Identity ---------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            val displayName = user.globalName?.takeIf { it.isNotBlank() } ?: user.username
            if (!displayName.isNullOrEmpty()) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DISCORD_TEXT,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Username row, with the custom status as a chip - "name 🌧 rain".
            val customStatus = user.special?.takeIf { it.isNotBlank() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                val usernameText = (user.username.orEmpty()) +
                        if (!user.discriminator.isNullOrBlank() && user.discriminator != "0")
                            "#" + user.discriminator else ""
                Text(
                    text = usernameText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DISCORD_SUBTEXT,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (customStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DISCORD_LIGHT_DARK)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = customStatus,
                            fontSize = 13.sp,
                            color = DISCORD_TEXT,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // --- Badges ---------------------------------------------------
            val allBadges = user.getAllBadges()
            if (allBadges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DISCORD_LIGHT_DARK)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allBadges.forEach { badge ->
                        AsyncImage(
                            model = badge.icon,
                            placeholder = painterResource(R.drawable.broken_image),
                            modifier = Modifier
                                .size(30.dp)
                                .padding(3.dp),
                            contentDescription = badge.name,
                        )
                    }
                }
            }

            // --- Bio (collapsible, "View Full Bio") -----------------------
            val bio = user.bio?.takeIf { it.isNotBlank() }
            if (bio != null) {
                Spacer(Modifier.height(16.dp))
                SectionHeader(text = stringResource(id = R.string.user_profile_bio))
                var bioExpanded by remember { mutableStateOf(false) }
                var bioOverflow by remember { mutableStateOf(false) }
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DISCORD_TEXT,
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> bioOverflow = result.hasVisualOverflow },
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (bioOverflow) {
                    Text(
                        text = stringResource(
                            if (bioExpanded) R.string.profile_show_less
                            else R.string.profile_view_full_bio
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = DISCORD_TEXT,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { bioExpanded = !bioExpanded },
                    )
                }
            }

            // --- Current activity ----------------------------------------
            if (rpcConfig != null) {
                Spacer(Modifier.height(18.dp))
                ActivityRow(
                    rpcConfig = rpcConfig,
                    showTs = showTs,
                    special = user.special,
                )
            }
        }
    }
}

/** Discord's small caps section heading ("ABOUT ME", "Current activity"). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = DISCORD_SUBTEXT,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun ProfileButton(label: String?, link: String?) {
    val uriHandler = LocalUriHandler.current
    if (!label.isNullOrEmpty()) {
        ElevatedButton(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = {
                if (!link.isNullOrEmpty()) {
                    uriHandler.openUri(link)
                }
            },
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = DISCORD_LIGHT_DARK,
                contentColor = Color.White.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(label)
        }
    }
}

@Preview
@Composable
fun PreviewProfileCard() {
    val user = User(
        accentColor = 0x5865F2,
        avatar = null,
        avatarDecoration = null,
        badges = null,
        banner = null,
        bannerColor = "#5865F2",
        discriminator = null,
        id = null,
        publicFlags = null,
        username = "yizzK",
        special = null,
        verified = false,
        bio = "Hello 👋"
    )
    ProfileCard(user = user)
}
