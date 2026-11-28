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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.resources.R
import com.my.kizzy.ui.theme.DISCORD_GREEN
import com.my.kizzy.ui.theme.DISCORD_RED
import com.my.kizzy.ui.theme.DISCORD_YELLOW

const val NITRO_ICON = "https://cdn.discordapp.com/badge-icons/2ba85e8026a8614b640c2837bcdfe21b.png"

private val ONLINE = DISCORD_GREEN
private val IDLE = DISCORD_YELLOW
private val DND = DISCORD_RED
private val OFFLINE = Color(0xFF80848E)

private fun presenceColor(status: String?): Color? = when (status?.lowercase()?.trim()) {
    "online" -> ONLINE
    "idle" -> IDLE
    "dnd" -> DND
    "offline", "invisible" -> OFFLINE
    else -> null
}

@Composable
private fun DiscordPresenceBadge(
    status: String?,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val cleanStatus = status?.lowercase()?.trim() ?: return
    val dotColor = presenceColor(cleanStatus) ?: return

    Box(
        modifier = modifier
            .size(26.dp)
            .border(4.dp, borderColor, CircleShape)
            .clip(CircleShape)
            .background(dotColor),
        contentAlignment = Alignment.Center,
    ) {
        when (cleanStatus) {
            "idle" -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(borderColor),
                )
            }
            "dnd" -> {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White),
                )
            }
            "offline", "invisible" -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(borderColor),
                )
            }
        }
    }
}

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

@Composable
fun ProfileCard(
    user: User?,
    borderColors: List<Color> = listOf(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.onSurfaceVariant),
    backgroundColors: List<Color> = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant),
    padding: Dp = 0.dp,
    type: String = stringResource(id = R.string.user_profile_rpc_name),
    rpcConfigs: List<RpcConfig> = emptyList(),
    showTs: Boolean = true,
    onAvatarClick: () -> Unit = {}
) {
    if (user == null) return

    val accent = user.accentComposeColor()
    val ringColor = accent ?: borderColors.firstOrNull() ?: MaterialTheme.colorScheme.onBackground
    val bannerFallback = parseHexColor(user.bannerColor)
        ?: accent
        ?: MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
    ) {
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
                        error = painterResource(R.drawable.broken_image),
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
                            imageVector = vectorResource(R.drawable.ic_person),
                            contentDescription = "User Avatar",
                            modifier = Modifier.size(86.dp).clip(CircleShape),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val avatarDecoration = user.avatarDecoration
                    if (avatarDecoration != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 4.dp, end = 4.dp),
                        ) {
                            AsyncImage(
                                model = user.getAvatarDecorationImage(),
                                placeholder = painterResource(id = R.drawable.broken_image),
                                error = painterResource(id = R.drawable.error_avatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                val statusStr = rpcConfigs.firstOrNull()?.status?.takeIf { it.isNotBlank() } ?: "online"
                DiscordPresenceBadge(
                    status = statusStr,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    borderColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            val customStatus = user.special?.takeIf { it.isNotBlank() }
            if (customStatus != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 124.dp, bottom = 12.dp, end = 20.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = customStatus,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

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
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // --- Badges + Nitro -------------------------------------------
            val allBadges = user.getAllBadges()
            val showNitroIcon = user.nitro && allBadges.none { it.icon == NITRO_ICON }
            if (allBadges.isNotEmpty() || showNitroIcon) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    allBadges.forEach { badge ->
                        AsyncImage(
                            model = badge.icon,
                            placeholder = painterResource(R.drawable.broken_image),
                            error = painterResource(R.drawable.broken_image),
                            modifier = Modifier
                                .size(30.dp)
                                .padding(3.dp),
                            contentDescription = badge.name,
                        )
                    }
                    if (showNitroIcon) {
                        AsyncImage(
                            model = NITRO_ICON,
                            placeholder = painterResource(R.drawable.broken_image),
                            error = painterResource(R.drawable.broken_image),
                            modifier = Modifier
                                .size(30.dp)
                                .padding(3.dp),
                            contentDescription = "Nitro",
                        )
                    }
                }
            }

            // --- Bio (actual bio, never replaced by custom status) -------
            val bio = user.bio?.takeIf { it.isNotBlank() }
            Spacer(Modifier.height(16.dp))
            SectionHeader(text = stringResource(id = R.string.user_profile_bio))
            if (bio == null) {
                Text(
                    text = stringResource(id = R.string.user_profile_no_bio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                var bioExpanded by remember { mutableStateOf(false) }
                var bioOverflow by remember { mutableStateOf(false) }
                val linkColor = MaterialTheme.colorScheme.primary
                val textColor = MaterialTheme.colorScheme.onBackground
                val annotatedBio = remember(bio, linkColor, textColor) {
                    buildAnnotatedBio(bio, linkColor, textColor)
                }

                Text(
                    text = annotatedBio,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { res: TextLayoutResult -> bioOverflow = res.hasVisualOverflow },
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
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { bioExpanded = !bioExpanded },
                    )
                }
            }

            if (rpcConfigs.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader(text = stringResource(R.string.user_profile_rpc_name))
                Spacer(Modifier.height(8.dp))
                rpcConfigs.forEachIndexed { index, cfg ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    ActivityRow(
                        rpcConfig = cfg,
                        showTs = showTs,
                        special = user.special,
                    )
                }
            }
        }
    }
}

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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
fun ProfileButton(label: String?, link: String?) {
    val uriHandler = LocalUriHandler.current
    if (!label.isNullOrEmpty()) {
        ElevatedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val trimmed = link?.trim()
                if (!trimmed.isNullOrEmpty() &&
                    (trimmed.startsWith("http://", ignoreCase = true) ||
                            trimmed.startsWith("https://", ignoreCase = true))
                ) {
                    runCatching { uriHandler.openUri(trimmed) }
                }
            },
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(label)
        }
    }
}

private fun buildAnnotatedBio(bio: String, linkColor: Color, textColor: Color): AnnotatedString = buildAnnotatedString {
    val urlRegex = Regex("https?://[^\\s]+")
    var lastIndex = 0
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
    )
    urlRegex.findAll(bio).forEach { matchResult ->
        val start = matchResult.range.first
        val end = matchResult.range.last + 1
        val url = bio.substring(start, end)
        if (start > lastIndex) {
            withStyle(SpanStyle(color = textColor)) {
                append(bio.substring(lastIndex, start))
            }
        }
        withLink(LinkAnnotation.Url(url = url, styles = linkStyles)) {
            append(url)
        }
        lastIndex = end
    }
    if (lastIndex < bio.length) {
        withStyle(SpanStyle(color = textColor)) {
            append(bio.substring(lastIndex))
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
        special = "Listening to music",
        verified = false,
        nitro = true,
        bio = "Hello 👋 https://example.com"
    )
    ProfileCard(user = user)
}