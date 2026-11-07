package com.my.kizzy.data.get_current_data.media

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import com.my.kizzy.data.rpc.Constants.APPLICATION_ID
import com.my.kizzy.data.get_current_data.media.RichMediaMetadata
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.utils.toRpcImage
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

    operator fun invoke(): RichMediaMetadata {
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

        for (mediaController in sorted) {
            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            val appName = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(mediaController.packageName, 0)).toString()
            val author = metadata?.let { metadataResolver.formatArtists(it) }
            val album = metadata?.let { metadataResolver.getAlbum(it) }

            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val position = mediaController.playbackState?.position
            val playbackState = mediaController.playbackState?.state

            var timestamps: Timestamps? = null
            duration?.let {
                if (it != 0L && playbackState == PlaybackState.STATE_PLAYING) timestamps = Timestamps(
                    end = System.currentTimeMillis() + duration - (position ?: 0L),
                    start = System.currentTimeMillis() - (position ?: 0L)
                )
            }

            // Even if title is null/empty, still show presence with just the app name.
            // This prevents the presence from disappearing when media apps send
            // empty title metadata (common during ad breaks or between tracks).
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
            }

            val playbackStateIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

            return RichMediaMetadata(
                appName = appName,
                packageName = mediaController.packageName,
                title = title,
                artist = author,
                album = album,
                coverArt = coverArt,
                appIcon = appIcon,
                playbackStateIcon = playbackStateIcon,
                playbackState = playbackState,
                duration = duration,
                position = position,
                timestamps = timestamps,
                audioChannels = metadata?.let { metadataResolver.getAudioChannels(it) },
                allArtists = metadata?.let { metadataResolver.getAllArtists(it) } ?: emptyList(),
            )
        }
        return RichMediaMetadata()
    }
}
