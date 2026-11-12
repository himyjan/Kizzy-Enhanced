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
            logger.w("MediaRPC", "MEDIA_CONTENT_CONTROL permission is signatureOrSystem on API 34+; regular third-party apps cannot hold it. Fail gracefully.")
            emptyList()
        }

        // Sort sessions so that playing/buffering sessions come first.
        // This prevents a paused Spotify session from shadowing a playing YouTube session.
        val sorted = sessions.sortedByDescending { isPlaybackActive(it.playbackState?.state) }

        val skipped = mutableListOf<String>()

        for (mediaController in sorted) {
            // Only report media from apps the user enabled in the Media RPC app list.
            // Without this check the app selection had no effect at all - every app that
            // published a media session was broadcast to Discord.
            if (!Prefs.isMediaAppEnabled(mediaController.packageName)) {
                logger.d("MediaRPC", "Skipping session ${mediaController.packageName}: app not enabled in Media RPC")
                skipped += "${mediaController.packageName} (app not enabled in Media RPC)"
                continue
            }

            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }

            if (title == null) {
                logger.d("MediaRPC", "Skipping session ${mediaController.packageName}: no title metadata (session may be paused/backgrounded)")
                skipped += "${mediaController.packageName} (session has no title metadata)"
                continue
            }

            val appName = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(mediaController.packageName, 0)).toString()
            val author = metadata?.let { metadataResolver.formatArtists(it) }
            val album = metadata?.let { metadataResolver.getAlbum(it) }

            // The artist / album / song-as-title switches were written by the
            // Media RPC screen but never read, so they had no effect at all.
            val artistEnabled = Prefs[Prefs.MEDIA_RPC_ARTIST_NAME, true]
            val albumEnabled = Prefs[Prefs.MEDIA_RPC_ALBUM_NAME, true]
            val showSongAsTitle = Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true]

            // Build state with multi-artist and multi-channel info.
            val stateParts = buildList {
                if (artistEnabled) author?.takeIf { it.isNotBlank() }?.let { add(it) }
                metadata?.let { metadataResolver.getAudioChannels(it) }?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val state = stateParts.joinToString(" · ").takeIf { it.isNotEmpty() } ?: null

            // "name" and "details" used to both carry the track title, so Discord
            // showed the title twice and the app name was lost. The app name now
            // fills whichever line the title does not use.
            val rpcName = if (showSongAsTitle) title else appName
            val rpcDetails = if (showSongAsTitle) appName else title

            val playbackState = mediaController.playbackState?.state

            var timestamps: Timestamps? = null
            // Discord progress bar timestamps — compute whenever we have a usable position
            // (not just STATE_PLAYING), so the bar keeps showing during buffering/connecting.
            // YouTube freezes position at lastPositionUpdateTime, so extrapolate by speed.
            val position = mediaController.playbackState?.position?.coerceAtLeast(0L) ?: 0L
            if (position > 0L) {
                var extrapolated = position
                val lastUpdate = mediaController.playbackState?.lastPositionUpdateTime ?: 0L
                if (lastUpdate > 0L) {
                    val sinceUpdate = SystemClock.elapsedRealtime() - lastUpdate
                    val speed = mediaController.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f
                    extrapolated = (extrapolated + (sinceUpdate * speed).toLong()).coerceAtLeast(0L)
                }
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                if (duration > 0L) extrapolated = extrapolated.coerceAtMost(duration)
                val now = System.currentTimeMillis()
                val start = now - extrapolated
                timestamps = if (duration > 0L) Timestamps(start = start, end = start + duration) else Timestamps(start = start)
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
                logger.d("MediaImage", "YT '$title': coverBitmap=bitmap, chosenCover=BitmapImage(from ALBUM_ART/ART/DISPLAY_ICON)")
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
                            logger.d("MediaImage", "YT '$title': coverBitmap=decodedBitmap(from artworkUri=$artworkUri), chosenCover=BitmapImage(decoded)")
                        } else {
                            coverArt = artworkUri.toRpcImage()
                            logger.d("MediaImage", "YT '$title': coverBitmap=artworkUri(artworkUri=$artworkUri), chosenCover=ImageUrl")
                        }
                    } else {
                        coverArt = artworkUri.toRpcImage()
                        logger.d("MediaImage", "YT '$title': coverBitmap=null, artworkUri=$artworkUri, chosenCover=ImageUrl")
                    }
                } else {
                    logger.d("MediaImage", "YT '$title': coverBitmap=null, chosenCover=null")
                }
            }

            logger.d("MediaRPC", "Session '${mediaController.packageName}' title='$title' artist='$author' album=$album package=${mediaController.packageName} playbackState=${playbackState} coverBitmap=${bitmap != null}")

            val playbackStateIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

            return CommonRpc(
                name = rpcName,
                details = rpcDetails,
                state = state,
                largeImage = coverArt,
                smallImage = if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) appIcon else null,
                largeText = album.takeIf { albumEnabled },
                smallText = if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) appName else null,
                packageName = "$title::${mediaController.packageName}",
                time = timestamps.takeIf { it != null },
                mediaUrl = metadataResolver.getMediaUrl(metadata, mediaController.packageName),
                playbackState = playbackState,
                appPackage = mediaController.packageName,
                appName = appName,
                isMusicMetadata = album != null || !author.isNullOrBlank(),
            )
        }
        logger.i("MediaRPC", "No media presence built. Sessions seen: ${skipped.joinToString(", ")}")
        return CommonRpc()
    }
}
