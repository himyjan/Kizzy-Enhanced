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
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.preference.Prefs
import com.my.kizzy.domain.interfaces.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetCurrentPlayingMediaAll @Inject constructor(
    private val metadataResolver: MetadataResolver,
    private val componentName: ComponentName,
    @ApplicationContext private val context: Context,
    private val logger: Logger,
) {
    object Assets {
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

    operator fun invoke(): RichMediaMetadata {
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions: List<android.media.session.MediaController> = try {
            mediaSessionManager.getActiveSessions(componentName)
        } catch (_: SecurityException) {
            emptyList()
        }

        val sorted = sessions.sortedByDescending { isPlaybackActive(it.playbackState?.state) }

        val skipped = mutableListOf<String>()

        for (mediaController in sorted) {
            // Respect the app selections. The Experimental RPC list is the explicit
            // user selection; the Media RPC list allows every app until the user edits it.
            // An app is broadcast when it is enabled in EITHER list.
            val isEnabled = Prefs.isExperimentalAppEnabled(mediaController.packageName) ||
                Prefs.isMediaAppEnabled(mediaController.packageName)
            if (!isEnabled) {
                logger.d("MediaRPC", "Skipping session ${mediaController.packageName}: app not enabled for media RPC")
                skipped += "${mediaController.packageName} (app not enabled for media RPC)"
                continue
            }

            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }

            if (title == null) {
                logger.d("MediaRPC", "Skipping session ${mediaController.packageName}: session has no title metadata")
                skipped += "${mediaController.packageName} (session has no title metadata)"
                continue
            }
            val appName = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(mediaController.packageName, 0)).toString()
            val author = metadata?.let { metadataResolver.formatArtists(it) }
            val album = metadata?.let { metadataResolver.getAlbum(it) }

            val stateParts = buildList {
                author?.takeIf { it.isNotBlank() }?.let { add(it) }
                metadata?.let { metadataResolver.getAudioChannels(it) }?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val state = stateParts.joinToString(" · ").takeIf { it.isNotEmpty() } ?: null

            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = mediaController.playbackState?.position?.coerceAtLeast(0L) ?: 0L
            val playbackState = mediaController.playbackState?.state

            var timestamps: Timestamps? = null
            if (playbackState == PlaybackState.STATE_PLAYING) {
                val durationForTs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                var positionForTs = position
                val lastUpdate = mediaController.playbackState?.lastPositionUpdateTime ?: 0L
                if (lastUpdate > 0L) {
                    val sinceUpdate = SystemClock.elapsedRealtime() - lastUpdate
                    val speed = mediaController.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f
                    positionForTs = (positionForTs + (sinceUpdate * speed).toLong()).coerceAtLeast(0L)
                }
                if (durationForTs > 0L) positionForTs = positionForTs.coerceAtMost(durationForTs)
                val now = System.currentTimeMillis()
                val start = now - positionForTs
                timestamps = if (durationForTs > 0L) Timestamps(start = start, end = start + durationForTs)
                else Timestamps(start = start)
            }

            val appIcon = RpcImage.ApplicationIcon(mediaController.packageName, context)

            var coverArt: RpcImage? = null
            val bitmap = metadata?.let { metadataResolver.getCoverArt(it) }
            if (bitmap != null) {
                coverArt = RpcImage.BitmapImage(
                    context = context,
                    bitmap = bitmap,
                    packageName = mediaController.packageName,
                    title = "${metadata.let { metadataResolver.getAlbumArtists(it) }}|${metadata.let { metadataResolver.getAlbum(it) }?: "unknown"}|${title ?: ""}"
                )
                logger.i("MediaImage", "YT '$title': coverBitmap=bitmap, chosenCover=BitmapImage(from embedded bitmap)")
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
                            logger.d("MediaImage", "YT '$title': coverBitmap=bitmap, chosenCover=BitmapImage(decoded from artworkUri=$artworkUri)")
                        } else {
                            coverArt = artworkUri.toRpcImage()
                            logger.d("MediaImage", "YT '$title': coverBitmap=null, artworkUri=$artworkUri, chosenCover=ImageUrl")
                        }
                    } else {
                        coverArt = artworkUri.toRpcImage()
                        logger.d("MediaImage", "YT '$title': coverBitmap=null, artworkUri=$artworkUri, chosenCover=ImageUrl")
                    }
                } else {
                    logger.d("MediaImage", "YT '$title': coverBitmap=null, chosenCover=null")
                }
            }

            logger.d("MediaRPC", "Session package=${mediaController.packageName} title='$title' artist=$author album=$album playbackState=$playbackState coverBitmap=${bitmap != null}")

            val playbackStateIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

            return RichMediaMetadata(
                appName = appName,
                packageName = mediaController.packageName,
                title = title,
                artist = author,
                state = state,
                album = album,
                coverArt = coverArt,
                appIcon = appIcon,
                playbackStateIcon = playbackStateIcon,
                playbackState = playbackState,
                duration = duration,
                position = position,
                isMusicMetadata = album != null || !author.isNullOrBlank(),
                timestamps = timestamps,
                audioChannels = metadata?.let { metadataResolver.getAudioChannels(it) },
                allArtists = metadata?.let { metadataResolver.getAllArtists(it) } ?: emptyList(),
            )
        }
        logger.i("MediaRPC", "No media presence built. Sessions seen: ${skipped.joinToString(", ")}")
        return RichMediaMetadata()
    }
}