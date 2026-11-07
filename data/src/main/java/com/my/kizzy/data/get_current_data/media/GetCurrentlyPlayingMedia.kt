package com.my.kizzy.data.get_current_data.media

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Constants.APPLICATION_ID
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetCurrentPlayingMedia @Inject constructor(
    private val metadataResolver: MetadataResolver,
    private val componentName: ComponentName,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) {
    object Assets {
        // These assets belong to the original Kizzy Discord application — do not change this ID
        private const val MEDIA_ASSETS_APP_ID = "962990036020756480"
        val PLAY = "app-assets/$MEDIA_ASSETS_APP_ID/1300361266212241430.png"
        val PAUSE = "app-assets/$MEDIA_ASSETS_APP_ID/1300361619490209802.png"
        val STOP = "app-assets/$MEDIA_ASSETS_APP_ID/1300361702621188160.png"
    }

    private fun getPlaybackStateIcon(playbackState: Int): RpcImage {
        return when (playbackState) {
            PlaybackState.STATE_PLAYING -> RpcImage.DiscordImage(Assets.PLAY)
            PlaybackState.STATE_PAUSED -> RpcImage.DiscordImage(Assets.PAUSE)
            PlaybackState.STATE_STOPPED -> RpcImage.DiscordImage(Assets.STOP)
            else -> RpcImage.DiscordImage(Assets.PAUSE)
        }
    }

    private fun isPlaybackActive(state: Int?): Boolean {
        return state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING
    }

    operator fun invoke(): CommonRpc {
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions: List<android.media.session.MediaController> = try {
            mediaSessionManager.getActiveSessions(componentName)
        } catch (_: SecurityException) {
            // MEDIA_CONTENT_CONTROL permission is signatureOrSystem on API 34+;
            // regular third-party apps cannot hold it. Fail gracefully.
            emptyList()
        }

        // Sort sessions so that playing/buffering sessions come first.
        // This prevents a paused Spotify session from shadowing a playing YouTube session.
        val sorted = sessions.sortedByDescending { isPlaybackActive(it.playbackState?.state) }

        val skipped = mutableListOf<String>()

        for (mediaController in sorted) {
            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            // Skip sessions with no title (paused/backgrounded players that linger in
            // getActiveSessions for hours). A null title would otherwise produce a blank presence.
            if (title == null) {
                skipped += "${mediaController.packageName} (session has no title metadata)"
                continue
            }
            val appName = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(mediaController.packageName, 0)).toString()
            val author = metadata?.let { metadataResolver.formatArtists(it) }
            val album = metadata?.let { metadataResolver.getAlbum(it) }

            // Build state with multi-artist and multi-channel info.
            val stateParts = buildList {
                author?.takeIf { it.isNotBlank() }?.let { add(it) }
                metadata?.let { metadataResolver.getAudioChannels(it) }?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val state = stateParts.joinToString(" · ").takeIf { it.isNotEmpty() } ?: null

            val playbackState = mediaController.playbackState?.state

            var timestamps: Timestamps? = null
            // Discord progress bar timestamps. Players like YouTube freeze position at lastPositionUpdateTime,
            // so extrapolate to "now" using elapsedRealtime delta and playback speed. Duration <= 0 (Shorts,
            // live streams) still gets a start timestamp so Discord shows elapsed time instead of nothing.
            if (playbackState == PlaybackState.STATE_PLAYING) {
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                var position = mediaController.playbackState?.position?.coerceAtLeast(0L) ?: 0L
                val lastUpdate = mediaController.playbackState?.lastPositionUpdateTime ?: 0L
                if (lastUpdate > 0L) {
                    val sinceUpdate = SystemClock.elapsedRealtime() - lastUpdate
                    val speed = mediaController.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f
                    position = (position + (sinceUpdate * speed).toLong()).coerceAtLeast(0L)
                }
                if (duration > 0L) position = position.coerceAtMost(duration)
                val now = System.currentTimeMillis()
                val start = now - position
                timestamps = if (duration > 0L) Timestamps(start = start, end = start + duration)
                else Timestamps(start = start)
            }

            val appIcon = RpcImage.ApplicationIcon(
                mediaController.packageName, context
            )

            var coverArt: RpcImage? = null
            val bitmap = metadata?.let { metadataResolver.getCoverArt(it) }
            if (bitmap != null) {
                coverArt = RpcImage.BitmapImage(
                    context = context,
                    bitmap = bitmap,
                    packageName = mediaController.packageName,
                    title = "${metadata.let { metadataResolver.getAlbumArtists(it) }}|${metadata.let { metadataResolver.getAlbum(it) }?: "unknown"}|${title ?: ""}"
                )
            } else {
                val artworkUri = metadata?.let { metadataResolver.getArtworkUri(it) }
                if (artworkUri != null) {
                    val parsedUri = Uri.parse(artworkUri)
                    if (parsedUri.scheme in listOf("content", "file", "android.resource")) {
                        val bmp = runCatching {
                            context.contentResolver.openInputStream(parsedUri)?.use { BitmapFactory.decodeStream(it) }
                        }.getOrNull()
                        if (bmp != null) {
                            coverArt = RpcImage.BitmapImage(
                                context = context,
                                bitmap = bmp,
                                packageName = mediaController.packageName,
                                title = "${metadata.let { metadataResolver.getAlbumArtists(it) }}|${metadata.let { metadataResolver.getAlbum(it) }?: "unknown"}|${title ?: ""}"
                            )
                        }
                    } else {
                        coverArt = artworkUri.toRpcImage()
                    }
                }
            }

            val playbackStateIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

            if (mediaController.packageName.contains("youtube", ignoreCase = true)) {
                logger.i("MediaImage", "YT '$title': coverBitmap=${bitmap != null}, chosenCover=${coverArt?.javaClass?.simpleName ?: "null"}")
            }

            return CommonRpc(
                name = if (Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, false]) title ?: appName else appName,
                details = title,
                state = state,
                // 大圖顯示專輯封面，小圖顯示應用圖標（若用戶開啟）
                largeImage = coverArt,
                smallImage = if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) appIcon else null,
                largeText = album,
                smallText = if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) appName else null,
                packageName = "$title::${mediaController.packageName}",
                time = timestamps.takeIf { it != null },
                mediaUrl = metadataResolver.getMediaUrl(metadata, mediaController.packageName)
            )
        }
        logger.i("MediaRPC", "No media presence built. Sessions seen: ${skipped.joinToString(", ")}")
        return CommonRpc()
    }
}
