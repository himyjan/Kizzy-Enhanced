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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.my.kizzy.domain.model.rpc.RpcConfig
import androidx.compose.material3.MaterialTheme
import com.my.kizzy.resources.R
import com.my.kizzy.ui.theme.DISCORD_CARD
import com.my.kizzy.ui.theme.DISCORD_GREEN
import com.my.kizzy.ui.theme.DISCORD_SUBTEXT
import com.my.kizzy.ui.theme.DISCORD_TRACK_COLOR
import com.my.kizzy.ui.theme.DISCORD_TEXT
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

// Discord's green "elapsed" marker shown next to the note glyph when a media
// activity has no seekable timeline.
private val MEDIA_ELAPSED_GREEN = DISCORD_GREEN

// Guard for the "now - start" fallback: any start timestamp further away than
// this is not a position (e.g. an epoch value or an absolute end time).
private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Pure computation of the media timeline: returns (progress 0f..1f,
 * elapsedText).
 *
 * Kept as a top-level function so the very first frame can be computed
 * synchronously during composition instead of waiting for the ticker.
 */
private fun computeTimeline(cfg: RpcConfig?, nowMs: Long): Pair<Float, String> {
    val type = cfg?.type?.toIntOrNull()
    val isMedia = type == 2 || type == 3
    val start = cfg?.timestampsStart?.toLongOrNull()
    val stop = cfg?.timestampsStop?.toLongOrNull()

    if (cfg != null && isMedia && start != null && stop != null) {
        val difference = stop - start
        if (cfg.paused) {
            // Freeze the timeline where the player stopped. `start` is
            // computed as "read time - position", so it cannot be used with
            // the wall clock while paused - it would keep counting.
            val position = (cfg.pausedPositionMs ?: 0L)
                .coerceIn(0L, difference.coerceAtLeast(0L))
            val progress = if (difference > 0L) {
                position.toFloat() / difference.toFloat()
            } else {
                0f
            }
            return progress to getFormatFromMs(position)
        }
        val elapsed = nowMs - start
        val progress = if (difference > 0L) {
            (elapsed.toFloat() / difference.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val text = if (elapsed >= 0L && elapsed < difference) {
            getFormatFromMs(elapsed)
        } else {
            getFormatFromMs(difference.coerceAtLeast(0))
        }
        return progress to text
    }

    // Media without a full start/stop range (or non-media): Discord shows
    // no seekable bar - just an elapsed marker. Prefer the paused position,
    // then the start timestamp converted to a position, so "stopped" media
    // does not read as 00:00 or as a multi-year timestamp.
    val text = when {
        cfg?.paused == true && cfg.pausedPositionMs != null ->
            getFormatFromMs((cfg.pausedPositionMs ?: 0L).coerceAtLeast(0L))
        isMedia && start != null && nowMs - start in 0..SEVEN_DAYS_MS ->
            getFormatFromMs(nowMs - start)
        else -> "00:00"
    }
    return 0f to text
}

/**
 * Discord's "Current activity" card.
 *
 * Layout mirrors Discord mobile: header row with the activity verb plus a
 * "..." affordance, then the large/small artwork next to the title and
 * subtitle, a single-row media timeline (elapsed — track — total) and the
 * optional action buttons at the bottom.
 */
@Composable
fun ActivityRow(
    rpcConfig: RpcConfig?,
    showTs: Boolean,
    special: String?,
) {
    val uriHandler = LocalUriHandler.current
    // Only open real http(s) links so a bad/blank url never crashes the preview.
    fun openLink(url: String?) {
        val trimmed = url?.trim()
        if (trimmed != null &&
            (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true))
        ) {
            runCatching { uriHandler.openUri(trimmed) }
        }
    }

    val isPlaying =
        rpcConfig?.type?.isNullOrEmpty() == true || rpcConfig?.type?.toIntOrNull()?.equals(0) == true
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

    // Computed synchronously for the first frame: LaunchedEffect only runs
    // after composition, so seeding "00:00" showed a stale value on entry
    // even when the track was already seconds in.
    val initialTimeline = remember(rpcConfig) {
        computeTimeline(rpcConfig, System.currentTimeMillis())
    }
    var elapsedText by remember(rpcConfig) { mutableStateOf(initialTimeline.second) }
    var sliderPosition by remember(rpcConfig) { mutableFloatStateOf(initialTimeline.first) }

    LaunchedEffect(Unit) {
        while (true) {
            // 2-second tick instead of 1 second: reduces UI recompositions
            // by 50% while the profile screen is visible and media is playing.
            delay(2000)
            val next = computeTimeline(latestRpcConfig, System.currentTimeMillis())
            sliderPosition = next.first
            elapsedText = next.second
        }
    }

    val activityPrefix = when (rpcConfig?.type?.toIntOrNull()) {
        2 -> stringResource(
            R.string.activity_listening_title,
            rpcConfig?.name.orEmpty().ifEmpty { "music" })
        3 -> stringResource(
            R.string.activity_watching_title,
            rpcConfig?.name.orEmpty().ifEmpty { "video" })
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
    // Local vals so the timeline block can smart-cast below.
    val cfg = rpcConfig
    val startMs = cfg?.timestampsStart?.toLongOrNull()
    val stopMs = cfg?.timestampsStop?.toLongOrNull()
    val hasTimeline = cfg != null && showTs && isPlayingMedia &&
            startMs != null && stopMs != null

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            // Header: "Listening to X" with the "..." menu affordance on the
            // right, exactly like Discord's activity card. The icon is visual
            // only - Kizzy has no activity menu.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = activityPrefix,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clickable { openLink(rpcConfig?.largeUrl) }
                ) {
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
                                .size(80.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .align(Alignment.TopStart)
                        else
                            Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .align(Alignment.TopStart),
                    )
                    if (!rpcConfig?.smallImg.isNullOrEmpty()) {
                        val smallImg = rpcConfig?.smallImg?.removePrefix("mp:")
                        Box(
                            modifier =
                                Modifier
                                    .border(
                                        3.dp,
                                        MaterialTheme.colorScheme.surfaceVariant,
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
                                placeholder = painterResource(id = R.drawable.ic_rpc_placeholder),
                                contentDescription = null,
                                contentScale = ContentScale.FillHeight,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                ) {
                    Text(
                        text = renderedDetails.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { openLink(rpcConfig?.detailsUrl) },
                    )

                    if (!renderedState.isNullOrEmpty()) {
                        Text(
                            text = renderedState,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { openLink(rpcConfig?.stateUrl) },
                        )
                    }

                    when {
                        // Media without a seekable timeline: Discord renders a
                        // green "♪ 0:21" elapsed marker instead of a bar.
                        showTs && isPlayingMedia && !hasTimeline -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MEDIA_ELAPSED_GREEN,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = elapsedText,
                                color = MEDIA_ELAPSED_GREEN,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        // Playing game / non-media: "Time elapsed: 12m 30s".
                        showTs && isPlaying -> Text(
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            if (hasTimeline) {
                Spacer(Modifier.height(12.dp))
                // Discord draws the media timeline as one row:
                // elapsed ———— track ———— total.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = elapsedText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderPosition)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = getFormatFromMs(stopMs - startMs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            if (rpcConfig != null &&
                (rpcConfig.button1.isNotEmpty() || rpcConfig.button2.isNotEmpty())
            ) {
                Spacer(Modifier.height(12.dp))
                ProfileButton(label = rpcConfig.button1, link = rpcConfig.button1link)
                if (rpcConfig.button1.isNotEmpty() && rpcConfig.button2.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                }
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
