/*

 *  ******************************************************************
 *  *  * Copyright (C) 2022
 *  *  * GetCurrentlyPlayingMedia.kt is part of Kizzy
 *  *  *  and can not be copied and/or distributed without the express
 *  *  * permission of yzziK(Vaibhav)
 *  *  *****************************************************************

*/

package com.my.kizzy.data.get_current_data.media

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.blankj.utilcode.util.AppUtils
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.Constants.APPLICATION_ID
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class GetCurrentPlayingMedia @Inject constructor(
    private val metadataResolver: MetadataResolver,
    private val componentName: ComponentName,
    @ApplicationContext private val context: Context
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

    operator fun invoke(): CommonRpc {
        var largeIcon: RpcImage? = null
        var smallIcon: RpcImage? = null
        var smallText: String? = null
        var timestamps: Timestamps? = null
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = mediaSessionManager.getActiveSessions(componentName)

        // Collect all valid sessions to support multi-channel/multi-artist aggregation
        val validSessions = mutableListOf<MediaControllerWithMetadata>()
        for (mediaController in sessions) {
            // If the app is not enabled for media rpc, skip it
            if (!Prefs.isMediaAppEnabled(mediaController.packageName)) {
                continue
            }

            if (
                Prefs[Prefs.MEDIA_RPC_HIDE_ON_PAUSE, false] &&
                (
                    mediaController.playbackState?.state == PlaybackState.STATE_PAUSED ||
                    mediaController.playbackState?.state == PlaybackState.STATE_STOPPED
                )
            ) {
                continue
            }

            val metadata = mediaController.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (title == null || title.isBlank()) continue

            validSessions.add(
                MediaControllerWithMetadata(mediaController, metadata, title)
            )
        }

        if (validSessions.isEmpty()) return CommonRpc()

        // Use the primary (first) session for the main RPC data
        val primary = validSessions.first()
        val metadata = primary.metadata
        val title = primary.title
        val appName = AppUtils.getAppName(primary.mediaController.packageName)

        // Multi-artist support: show all collaborating artists
        val allArtists = metadataResolver.getAllArtists(metadata)
        val author = if (Prefs[Prefs.MEDIA_RPC_ARTIST_NAME, false]) {
            metadataResolver.formatArtists(metadata)
        } else null

        val album = if (Prefs[Prefs.MEDIA_RPC_ALBUM_NAME, false]) {
            metadataResolver.getAlbum(metadata)
        } else null
        val bitmap = metadataResolver.getCoverArt(metadata)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val playbackState = primary.mediaController.playbackState?.state
        val position = primary.mediaController.playbackState?.position

        duration?.let {
            if (it != 0L && playbackState == PlaybackState.STATE_PLAYING) timestamps = Timestamps(
                end = System.currentTimeMillis() + duration - (position ?: 0L),
                start = System.currentTimeMillis() - (position ?: 0L)
            )
        }

        // Multi-channel audio info (e.g., "5.1 Surround", "Dolby Atmos")
        val audioChannels = metadataResolver.getAudioChannels(metadata)

        if (title != null) {
            largeIcon =
                if (Prefs[Prefs.MEDIA_RPC_APP_ICON, false]) RpcImage.ApplicationIcon(
                    primary.mediaController.packageName, context
                ) else null
            if (bitmap != null) {
                smallIcon = largeIcon
                smallText = appName
                largeIcon = RpcImage.BitmapImage(
                    context = context,
                    bitmap = bitmap,
                    packageName = primary.mediaController.packageName,
                    // <Main artist>|<Album>|<Title>
                    title = "${metadataResolver.getAlbumArtists(metadata)}|${metadataResolver.getAlbum(metadata) ?: "unknown"}|${title}"
                )
            }

            if (Prefs[Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE, false]) {
                smallIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)
                smallText = null
            }

            // Build state with multi-artist and multi-channel info
            var stateBuilder = StringBuilder()
            if (!author.isNullOrEmpty()) {
                stateBuilder.append(author)
            }
            if (!audioChannels.isNullOrEmpty()) {
                if (stateBuilder.isNotEmpty()) stateBuilder.append(" • ")
                stateBuilder.append(audioChannels)
            }
            val finalState = stateBuilder.toString().takeIf { it.isNotEmpty() } ?: album

            return if (Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, false]) {
                CommonRpc(
                    name = title,
                    details = author,
                    state = finalState,
                    largeImage = largeIcon,
                    smallImage = smallIcon,
                    largeText = album,
                    smallText = smallText,
                    packageName = "$title::${primary.mediaController.packageName}",
                    time = timestamps.takeIf { it != null },
                    mediaUrl = metadataResolver.getMediaUrl(metadata, primary.mediaController.packageName)
                )
            } else {
                CommonRpc(
                    name = appName,
                    details = title,
                    state = finalState,
                    largeImage = largeIcon,
                    smallImage = smallIcon,
                    largeText = album,
                    smallText = smallText,
                    packageName = "$title::${primary.mediaController.packageName}",
                    time = timestamps.takeIf { it != null },
                    mediaUrl = metadataResolver.getMediaUrl(metadata, primary.mediaController.packageName)
                )
            }
        }
        return CommonRpc()
    }
}

/**
 * Holds a media controller along with its resolved metadata and title.
 */
private class MediaControllerWithMetadata(
    val mediaController: android.media.session.MediaController,
    val metadata: android.media.MediaMetadata,
    val title: String
)
