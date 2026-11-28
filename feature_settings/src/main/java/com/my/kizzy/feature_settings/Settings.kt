package com.my.kizzy.feature_settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.user.User
import com.my.kizzy.resources.R
import com.my.kizzy.ui.components.Subtitle

@Composable
fun SettingsDrawer(
    user: User?,
    showKizzyQuickieRequestItem: Boolean,
    onRequestAddTile: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToStyleAndAppearance: () -> Unit,
    navigateToLanguages: () -> Unit,
    navigateToAbout: () -> Unit,
    navigateToRpcSettings: () -> Unit,
    navigateToLogsScreen: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Enhanced",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(8f)
            ) {
                item(key = "display") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.display),
                        icon = vectorResource(R.drawable.ic_palette),
                        onClick = navigateToStyleAndAppearance
                    )
                }
                item(key = "language") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.language),
                        icon = vectorResource(R.drawable.ic_language),
                        onClick = navigateToLanguages
                    )
                }
                item(key = "logs") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.logs),
                        icon = vectorResource(R.drawable.ic_bug_report),
                        onClick = navigateToLogsScreen
                    )
                }
                item(key = "settings") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.settings),
                        icon = vectorResource(R.drawable.ic_settings),
                        onClick = navigateToRpcSettings
                    )
                }
                item(key = "divider") {
                    HorizontalDivider()
                }
                item(key = "drawer_subtitle_help") {
                    Subtitle(
                        text = stringResource(id = R.string.drawer_subtitle_help),
                        modifier = Modifier
                    )
                }
                item(key = "faq") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.drawer_faq),
                        icon = Icons.AutoMirrored.Rounded.HelpOutline
                    ) {
                        uriHandler.openUri("https://kizzy.vercel.app/#FAQ")
                    }
                }
                item(key = "about") {
                    SettingsItemCard(
                        title = stringResource(id = R.string.about),
                        icon = vectorResource(R.drawable.ic_info),
                        onClick = navigateToAbout
                    )
                }
                item(key = "qs_tile") {
                    RequestQsTile(
                        visible = showKizzyQuickieRequestItem,
                        onRequestAddTile = onRequestAddTile
                    )
                }
            }
            if (user != null) {
                ProfileCardSmall(
                    user = user,
                    navigateToProfile = navigateToProfile
                )
            }
        }
    }
}

@Composable
fun RequestQsTile(
    visible: Boolean = true,
    onRequestAddTile: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(800))
    ) {
        SettingsItemCard(
            title = stringResource(R.string.qs_tile_label),
            icon = vectorResource(R.drawable.ic_star),
            selected = true,
            onClick = onRequestAddTile
        )
    }
}

@Composable
fun SettingsItemCard(
    title: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    NavigationDrawerItem(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                    .copy(fontWeight = FontWeight.SemiBold),
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(28.dp)
            )
        },
        selected = selected,
        onClick = onClick
    )
}

@Composable
fun ProfileCardSmall(
    user: User?,
    navigateToProfile: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        onClick = navigateToProfile
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                AsyncImage(
                    model = user?.getAvatarImage(),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(50.dp)
                        .clip(CircleShape),
                    contentDescription = user?.username,
                    error = painterResource(R.drawable.error_avatar),
                    placeholder = painterResource(R.drawable.error_avatar),
                )
                Text(
                    buildAnnotatedString {
                        withStyle(
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Left
                            ).toSpanStyle()
                        ) {
                            append((user?.globalName ?: user?.username).orEmpty() + "\n")
                        }
                        withStyle(
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Left
                            ).toSpanStyle()
                        ) {
                            append(user?.username.orEmpty())
                            if (user?.discriminator != null && user.discriminator != "0") {
                                append("#${user.discriminator}")
                            }
                        }
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview
@Composable
fun SettingsDrawerPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
    ) {
        SettingsDrawer(
            showKizzyQuickieRequestItem = false,
            onRequestAddTile = {},
            user = null,
            navigateToProfile = {},
            navigateToStyleAndAppearance = {},
            navigateToLanguages = {},
            navigateToAbout = {},
            navigateToRpcSettings = {},
            navigateToLogsScreen = {}
        )
    }
}