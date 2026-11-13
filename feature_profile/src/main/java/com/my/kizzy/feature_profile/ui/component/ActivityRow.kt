package com.my.kizzy.feature_profile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import com.my.kizzy.resources.R
import com.my.kizzy.ui.theme.DISCORD_BLURPLE
import com.my.kizzy.ui.theme.DISCORD_CARD
import com.my.kizzy.ui.theme.DISCORD_SUBTEXT
import com.my.kizzy.ui.theme.DISCORD_TEXT
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ActivityRow(
    rpcConfig: RpcConfig?,
    showTs: Boolean,
    special: String?,
) {
    val isPlaying =
        rpcConfig?.type.isNullOrEmpty() || rpcConfig?.type?.toIntOrNull()?.equals(0) == true
    val isPlayingMedia =
        rpcConfig?.type?.toIntOrNull()?.equals(2) == true || rpcConfig?.type?.toIntOrNull()
            ?.equals(3) == true

    // Media timeline - calculate real elapsed time from timestamps.
    //
    // The previous version keyed remember/LaunchedEffect on
    // timestampsStart/timestampsStop, which change every second (now - position).
    // This reset state to "00:00" and restarted the ticker with a 1-second delay
    // on every tick, making the timeline jump back to 00:00 repeatedly.
    //
    // Now we keep a mutable ref to the latest rpcConfig (updated via
    // LaunchedEffect(rpcConfig)) and run a stable ticker (LaunchedEffect(Unit))
    // that reads fresh values without ever resetting state or restarting.
    var latestRpcConfig by remember { mutableStateOf(rpcConfig) }
    LaunchedEffect(rpcConfig) {
        latestRpcConfig = rpcConfig
    }

    var elapsedText by remember { mutableStateOf("00:00") }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            val cfg = latestRpcConfig
            val type = cfg?.type?.toIntOrNull()
            val isPlayingMedia = type?.equals(2) == true || type?.equals(3) == true
            val start = cfg?.timestampsStart?.toLongOrNull()
            val stop = cfg?.timestampsStop?.toLongOrNull()

            if (isPlayingMedia && start != null && stop != null) {
                val difference = stop - start
                val paused = cfg?.paused == true
                if (paused) {
                    // Freeze the timeline where the player stopped. `start` is
                    // computed as "read time - position", so it cannot be used with
                    // the wall clock while paused - it would keep counting.
                    val position = (cfg?.pausedPositionMs ?: 0L)
                        .coerceIn(0L, difference.coerceAtLeast(0L))
                    sliderPosition = if (difference > 0L) {
                        position.toFloat() / difference.toFloat()
                    } else {
                        0f
                    }
                    elapsedText = getFormatFromMs(position)
                } else {
                    val elapsed1 = now - start
                    sliderPosition = if (difference > 0L) {
                        elapsed1.toFloat() / difference.toFloat()
                    } else {
                        0f
                    }
                    elapsedText = if (elapsed1 >= 0L && elapsed1 < difference) {
                        getFormatFromMs(elapsed1)
                    } else {
                        getFormatFromMs(difference.coerceAtLeast(0))
                    }
                }
            } else {
                sliderPosition = 0f
                elapsedText = "00:00"
            }
        }
    }

    val activityPrefix = when (rpcConfig?.type?.toIntOrNull()) {
        2 -> stringResource(R.string.activity_listening_title, rpcConfig?.name.orEmpty().ifEmpty { "music" })
        3 -> stringResource(R.string.activity_watching_title, rpcConfig?.name.orEmpty().ifEmpty { "video" })
        5 -> stringResource(R.string.activity_competiting_title, rpcConfig?.name.orEmpty())
        else -> rpcConfig?.name?.takeIf { it.isNotEmpty() }
            ?: stringResource(R.string.activity_playing_title)
    }

    val renderedDetails = if (isPlaying) {
        rpcConfig?.details?.takeIf { it.isNotEmpty() }
            ?: stringResource(id = R.string.user_profile)
    } else {
        rpcConfig?.details
    }
    val renderedState = rpcConfig?.state?.takeIf { it.isNotEmpty() }

    Surface(
        color = DISCORD_CARD,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = activityPrefix,
                color = DISCORD_TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(108.dp)) {
                    val largeImg = rpcConfig?.largeImg?.removePrefix("mp:")
                    AsyncImage(
                        model = if (isAsset(largeImg)) "https://media.discordapp.net/$largeImg" else
                            rpcConfig?.largeImg,
                        error = painterResource(id = R.drawable.editing_rpc_pencil),
                        placeholder = painterResource(R.drawable.editing_rpc_pencil),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = if (!rpcConfig?.largeImg.isNullOrEmpty())
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .align(Alignment.TopStart)
                        else
                            Modifier
                                .size(48.dp)
                                .background(DISCORD_BLURPLE, RoundedCornerShape(3.dp))
                        ,
                    )
                    if (!rpcConfig?.smallImg.isNullOrEmpty()) {
                        val smallImg = rpcConfig?.smallImg?.removePrefix("mp:")
                        Box(
                            modifier =
                                Modifier
                                    .border(
                                        2.dp,
                                        DISCORD_CARD,
                                        CircleShape,
                                    ).padding(2.dp)
                                    .align(Alignment.BottomEnd),
                        ) {
                            AsyncImage(
                                model =
                                if (isAsset(smallImg))
                                    "https://media.discordapp.net/$smallImg"
                                else
                                    rpcConfig?.smallImg,
                                error = painterResource(id = R.drawable.ic_rpc_placeholder),
                                placeholder = painterResource(R.drawable.ic_rpc_placeholder),
                                contentDescription = null,
                                contentScale = ContentScale.FillHeight,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = renderedDetails.orEmpty(),
                        color = DISCORD_TEXT,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = renderedState.orEmpty(),
                        color = DISCORD_SUBTEXT,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (
                        showTs &&
                        (isPlaying ||
                                (isPlayingMedia &&
                                        (rpcConfig?.timestampsStart?.toLongOrNull() == null || rpcConfig?.timestampsStop?.toLongOrNull() == null)))
                    ) {
                        Text(
                            text = stringResource(
                                id = rpcConfig?.timestampsStop?.toLongOrNull()?.let {
                                    if (it > 0) R.string.time_left else R.string.time_elapsed
                                } ?: R.string.time_elapsed,
                                formatTime(
                                    rpcConfig?.timestampsStart?.toLongOrNull(),
                                    rpcConfig?.timestampsStop?.toLongOrNull()
                                )
                                    .takeIf { it.isNotEmpty() } ?: elapsedText
                            ),
                            color = DISCORD_SUBTEXT,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showTs && isPlayingMedia && rpcConfig?.timestampsStart?.toLongOrNull() != null && rpcConfig?.timestampsStop?.toLongOrNull() != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 0.dp, 0.dp, 0.dp)
                        .height(6.dp)
                        .background(Color.LightGray.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .fillMaxWidth(sliderPosition)
                            .height(6.dp)
                            .background(DISCORD_BLURPLE)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp, 8.dp, 0.dp, 0.dp)
                ) {
                    Text(
                        text = elapsedText,
                        color = DISCORD_SUBTEXT,
                        fontSize = 12.sp,
                        modifier = Modifier
                    )
                    Text(
                        text = getFormatFromMs(rpcConfig.timestampsStop.toLong() - rpcConfig.timestampsStart.toLong()),
                        color = DISCORD_SUBTEXT,
                        fontSize = 12.sp,
                        modifier = Modifier
                    )
                }
            }

            if (rpcConfig != null) {
                ProfileButton(label = rpcConfig.button1, link = rpcConfig.button1link)
                ProfileButton(label = rpcConfig.button2, link = rpcConfig.button2link)
            }
        }
    }
}

private fun isAsset(url: String?): Boolean {
    return url?.startsWith("attachments") == true || url?.startsWith("external") == true || url?.startsWith("mp:") == true
}

private fun getFormatFromMs(ms: Long): String {
    var remainingMs = ms

    val daysDifference = TimeUnit.MILLISECONDS.toDays(remainingMs)
    remainingMs -= TimeUnit.DAYS.toMillis(daysDifference)
    val hoursDifference = TimeUnit.MILLISECONDS.toHours(remainingMs)
    remainingMs -= TimeUnit.HOURS.toMillis(hoursDifference)
    val minutesDifference = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    remainingMs -= TimeUnit.MINUTES.toMillis(minutesDifference)
    val secondsDifference = TimeUnit.MILLISECONDS.toSeconds(remainingMs)

    return (if (hoursDifference >= 1) String.format(Locale.US, "%02d:", hoursDifference) else "") +
            String.format(Locale.US, "%02d:%02d", minutesDifference, secondsDifference)
}

private fun formatTime(start: Long?, end: Long? = null): String {
    val startTime = end ?: start ?: return ""
    val endTime = System.currentTimeMillis()

    val difference = if (end != null) (startTime - endTime) else (endTime - startTime)
    if (difference < 0) return "00:00"

    return getFormatFromMs(difference)
}
