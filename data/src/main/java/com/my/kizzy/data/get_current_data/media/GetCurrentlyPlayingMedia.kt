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
import com.my.kizzy.data.rpc.ActivityTypeResolver
import com.my.kizzy.data.rpc.CommonRpc
import com.my.kizzy.data.rpc.MediaServiceResolver
import com.my.kizzy.data.rpc.RpcImage
import com.my.kizzy.data.rpc.Timestamps
import com.my.kizzy.data.rpc.YouTubeResolver
import com.my.kizzy.data.utils.toRpcImage
import com.my.kizzy.domain.interfaces.Logger
import com.my.kizzy.preference.Prefs
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

/**
 * Inspects active Android [MediaSessionManager] sessions and builds a [CommonRpc]
 * payload for the highest-priority enabled media app.
 */
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

    /**
     * Resolves the currently playing media session into a [CommonRpc] payload.
     *
     * Suspending because resolving direct YouTube video/channel links may require
     * a network lookup.
     */
    suspend operator fun invoke(instanceId: String? = null): CommonRpc {
        val mediaSessionManager =
            context.getSystemService(Service.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions: List<android.media.session.MediaController> = try {
            mediaSessionManager.getActiveSessions(componentName)
        } catch (_: SecurityException) {
            logger.w(
                "MediaRPC",
                "MEDIA_CONTENT_CONTROL permission is signatureOrSystem on API 34+; regular third-party apps cannot hold it. Fail gracefully."
            )
            emptyList()
        }

        // Prioritize active/playing sessions so a paused session never shadows an active one.
        val sorted = sessions.sortedByDescending { isPlaybackActive(it.playbackState?.state) }

        val skipped = mutableListOf<String>()

        for (mediaController in sorted) {
            val pkg = mediaController.packageName

            if (!Prefs.isMediaAppEnabled(pkg)) {
                logger.d("MediaRPC", "Skipping session $pkg: app not enabled in Media RPC")
                skipped += "$pkg (app not enabled in Media RPC)"
                continue
            }

            // Bind non-null metadata up front so Kotlin smart-casts `metadata` to `MediaMetadata`.
            val metadata = mediaController.metadata
            if (metadata == null) {
                logger.d("MediaRPC", "Skipping session $pkg: no metadata object")
                skipped += "$pkg (session has no metadata)"
                continue
            }

            val title = runCatching {
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()

            if (title == null) {
                logger.d("MediaRPC", "Skipping session $pkg: no title metadata (session may be paused/backgrounded)")
                skipped += "$pkg (session has no title metadata)"
                continue
            }

            val appName = runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: ActivityTypeResolver.knownAppName(pkg)
                ?: pkg

            val author = metadataResolver.formatArtists(metadata)
            val album = metadataResolver.getAlbum(metadata)

            val artistEnabled = Prefs[Prefs.MEDIA_RPC_ARTIST_NAME, true]
            val albumEnabled = Prefs[Prefs.MEDIA_RPC_ALBUM_NAME, true]
            val showSongAsTitle = Prefs[Prefs.MEDIA_RPC_SHOW_SONG_AS_TITLE, true]
            val showCoverArt = Prefs[Prefs.MEDIA_RPC_SHOW_COVER_ART, true]

            val stateParts = buildList {
                if (artistEnabled) author.takeIf { it.isNotBlank() }?.let { add(it) }
                metadataResolver.getAudioChannels(metadata)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            val state = stateParts.joinToString(" · ").takeIf { it.isNotEmpty() }

            val rpcName = if (showSongAsTitle) title else appName
            val rpcDetails = title

            val playbackState = mediaController.playbackState?.state
            val position = mediaController.playbackState?.position?.coerceAtLeast(0L) ?: 0L
            val duration = runCatching {
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            }.getOrDefault(0L)

            var timestamps: Timestamps? = null
            if (duration > 0L) {
                var extrapolated = position
                val lastUpdate = mediaController.playbackState?.lastPositionUpdateTime ?: 0L
                if (isPlaybackActive(playbackState) && lastUpdate > 0L) {
                    val sinceUpdate = SystemClock.elapsedRealtime() - lastUpdate
                    val speed = mediaController.playbackState?.playbackSpeed?.takeIf { it > 0f } ?: 1f
                    extrapolated = (extrapolated + (sinceUpdate * speed).toLong()).coerceAtLeast(0L)
                }
                extrapolated = extrapolated.coerceAtMost(duration)
                val now = System.currentTimeMillis()
                val start = now - extrapolated
                timestamps = Timestamps(start = start, end = start + duration)
            } else if (position > 0L) {
                val now = System.currentTimeMillis()
                timestamps = Timestamps(start = now - position)
            }

            val appIcon = RpcImage.ApplicationIcon(pkg, context)

            var coverArt: RpcImage? = null
            if (showCoverArt) {
                val artworkUri = metadataResolver.getArtworkUri(metadata)
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
                                packageName = pkg,
                                title = "${metadataResolver.getAlbumArtists(metadata)}|${album ?: "unknown"}|$title"
                            )
                            logger.d("MediaImage", "'$title': coverBitmap=decodedBitmap(from artworkUri=$artworkUri), chosenCover=BitmapImage(decoded)")
                        } else {
                            coverArt = artworkUri.toRpcImage()
                            logger.d("MediaImage", "'$title': coverBitmap=null(artworkUri=$artworkUri), chosenCover=ImageUrl")
                        }
                    } else {
                        coverArt = artworkUri.toRpcImage()
                        logger.d("MediaImage", "'$title': artworkUri=$artworkUri, chosenCover=ImageUrl")
                    }
                } else {
                    val bitmap = metadataResolver.getCoverArt(metadata)
                    if (bitmap != null) {
                        coverArt = RpcImage.BitmapImage(
                            context = context,
                            bitmap = bitmap,
                            packageName = pkg,
                            title = "${metadataResolver.getAlbumArtists(metadata)}|${album ?: "unknown"}|$title"
                        )
                        logger.d("MediaImage", "'$title': coverBitmap=bitmap, chosenCover=BitmapImage(from ALBUM_ART/ART)")
                    } else {
                        logger.d("MediaImage", "'$title': coverBitmap=null, chosenCover=null")
                    }
                }
            } else {
                logger.d("MediaImage", "'$title': cover art disabled by preference, skipping artwork")
            }

            logger.d(
                "MediaRPC",
                "Session '$pkg' title='$title' artist='$author' album=$album playbackState=$playbackState coverArt=${coverArt != null}"
            )

            val playbackStateIcon = getPlaybackStateIcon(playbackState ?: PlaybackState.STATE_PAUSED)

            val channelIconKey = Prefs.scopedKey(Prefs.MEDIA_RPC_SHOW_CHANNEL_ICON, instanceId)
            val appIconKey = Prefs.scopedKey(Prefs.MEDIA_RPC_APP_ICON, instanceId)
            val playbackIconKey = Prefs.scopedKey(Prefs.MEDIA_RPC_SHOW_PLAYBACK_STATE, instanceId)
            val showChannelIcon = Prefs[channelIconKey, false]
            val showAppIconSmall = Prefs[appIconKey, false]
            val showPlaybackIconSmall = Prefs[playbackIconKey, false]

            val channelAvatarUrl = if (showChannelIcon && MediaServiceResolver.isYouTube(pkg)) {
                metadataResolver.getChannelAvatarUrl(metadata, pkg)
            } else null

            val smallImageRpc: RpcImage? = when {
                channelAvatarUrl != null -> RpcImage.ExternalImage(channelAvatarUrl)
                showAppIconSmall -> appIcon
                showPlaybackIconSmall -> playbackStateIcon
                else -> null
            }
            val smallTextRpc: String? = when {
                channelAvatarUrl != null -> author.takeIf { it.isNotBlank() }
                showAppIconSmall -> appName
                else -> null
            }

            // Always prefer direct YouTube video/channel/playlist resolution before generic fallback.
            val mediaUrl = metadataResolver.getDirectMediaUrl(metadata, pkg)
                ?: metadataResolver.getMediaUrl(metadata, pkg)

            val detailsUrl = mediaUrl
                ?: MediaServiceResolver.mediaSearchUrl(pkg, title)
                ?: MediaServiceResolver.browserSearchUrl(title)

            val artistUrl = metadataResolver.getArtistUrl(metadata, pkg)
                ?: MediaServiceResolver.artistUrl(pkg, author)

            val smallImageChannelUrl = if (channelAvatarUrl != null) {
                artistUrl.takeIf { YouTubeResolver.isYoutubeFamilyUrl(it) }
            } else null

            val albumUrl = metadataResolver.getAlbumUrl(metadata, pkg)
            val playlistUrl = metadataResolver.getPlaylistUrl(metadata, pkg)
            val channelUrl = artistUrl

            val albumText = album?.takeIf { it.isNotBlank() }
            val playlistName = metadataResolver.getPlaylistName(metadata)
            val largeTextIsPlaylist = albumText == null && !playlistName.isNullOrBlank()
            val largeTextValue = when {
                albumText != null -> albumText.takeIf { albumEnabled }
                else -> playlistName
            }

            return CommonRpc(
                name = rpcName,
                details = rpcDetails,
                state = state,
                detailsUrl = detailsUrl,
                stateUrl = artistUrl,
                largeImage = coverArt,
                smallImage = smallImageRpc,
                smallText = smallTextRpc,
                smallImageChannelUrl = smallImageChannelUrl,
                largeUrl = mediaUrl,
                largeText = largeTextValue,
                largeTextIsPlaylist = largeTextIsPlaylist,
                packageName = "$title::$pkg",
                time = timestamps,
                mediaUrl = mediaUrl,
                albumUrl = albumUrl,
                playlistUrl = playlistUrl,
                channelUrl = channelUrl,
                playbackState = playbackState,
                appPackage = pkg,
                appName = appName,
                isMusicMetadata = album != null || author.isNotBlank(),
                statusDisplayType = null,
            )
        }
        logger.i("MediaRPC", "No media presence built. Sessions seen: ${skipped.joinToString(", ")}")
        return CommonRpc()
    }
}