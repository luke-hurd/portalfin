@file:Suppress("NOTHING_TO_INLINE")

package org.jellyfin.mobile.utils

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsCollector
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.ui.content.ImageProvider
import org.jellyfin.mobile.utils.extensions.width
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import androidx.media3.common.AudioAttributes as Media3AudioAttributes

inline fun MediaSession.applyDefaultLocalAudioAttributes(contentType: Int) {
    val audioAttributes = AudioAttributes.Builder().apply {
        setUsage(AudioAttributes.USAGE_MEDIA)
        setContentType(contentType)
        if (AndroidVersion.isAtLeastQ) {
            setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        }
    }.build()
    setPlaybackToLocal(audioAttributes)
}

/**
 * Pick the best PORTRAIT poster for the now-playing artwork (e.g. the Portal home
 * "Now playing" card). Movies have their own Primary poster; an episode's Primary
 * is a 16:9 still, so prefer the parent series' portrait poster. Falls back to the
 * item's own Primary. Returns the (itemId, tag) to address via [ImageProvider]/the
 * image API; tag may be null (the server still resolves the current Primary image).
 */
fun BaseItemDto.posterImageSource(): Pair<java.util.UUID, String?> {
    if (type == BaseItemKind.EPISODE) {
        val sid = seriesId
        if (sid != null) {
            // seriesPrimaryImageTag is the series poster; use it when present, else
            // still address the series item (server resolves its Primary).
            return sid to seriesPrimaryImageTag
        }
    }
    return id to imageTags?.get(ImageType.PRIMARY)
}

/** URL for the portrait poster used in now-playing artwork. */
fun BaseItemDto.posterImageUrl(apiClient: ApiClient, fillHeight: Int): String {
    val (posterId, tag) = posterImageSource()
    return apiClient.imageApi.getItemImageUrl(
        itemId = posterId,
        imageType = ImageType.PRIMARY,
        fillHeight = fillHeight,
        quality = IMAGE_QUALITY,
        tag = tag,
    )
}

private const val IMAGE_QUALITY = 90

/**
 * Build the [MediaMetadata] surfaced to the system media session (lock screen,
 * notification, and the Portal home "Now playing" card).
 *
 * [artwork], when provided, is embedded directly as the album-art bitmap — the
 * most reliable path across launchers/widgets that don't resolve content URIs.
 * The content-provider URI is also set as a fallback for consumers that do.
 */
fun JellyfinMediaSource.toMediaMetadata(artwork: Bitmap? = null): MediaMetadata = MediaMetadata.Builder().apply {
    putString(MediaMetadata.METADATA_KEY_MEDIA_ID, itemId.toString())
    putString(MediaMetadata.METADATA_KEY_TITLE, item?.name ?: sourceInfo.name.orEmpty())
    item?.artists?.joinToString()?.let { artists ->
        putString(MediaMetadata.METADATA_KEY_ARTIST, artists)
    }
    putLong(MediaMetadata.METADATA_KEY_DURATION, runTime.inWholeMilliseconds)

    // Address a PORTRAIT poster (series poster for episodes) rather than the
    // episode's 16:9 still. The URI is a fallback for URI-aware consumers.
    val (posterId, posterTag) = item?.posterImageSource() ?: (itemId to item?.imageTags?.get(ImageType.PRIMARY))
    val imageUri = ImageProvider.buildItemUri(posterId, ImageType.PRIMARY, posterTag)
    putString(MediaMetadata.METADATA_KEY_ART_URI, imageUri.toString())

    if (artwork != null) {
        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
        putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
    }
}.build()

fun MediaSession.setPlaybackState(playbackState: Int, position: Long, playbackActions: Long) {
    val state = PlaybackState.Builder().apply {
        setState(playbackState, position, 1.0f)
        setActions(playbackActions)
    }.build()
    setPlaybackState(state)
}

fun MediaSession.setPlaybackState(isPlaying: Boolean, position: Long, playbackActions: Long) {
    setPlaybackState(
        if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        position,
        playbackActions,
    )
}

fun MediaSession.setPlaybackState(player: Player, playbackActions: Long) {
    val playbackState = when (val playerState = player.playbackState) {
        Player.STATE_IDLE, Player.STATE_ENDED -> PlaybackState.STATE_NONE
        Player.STATE_READY -> if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
        else -> error("Invalid player playbackState $playerState")
    }
    setPlaybackState(playbackState, player.currentPosition, playbackActions)
}

fun AudioManager.getVolumeRange(streamType: Int): IntRange {
    val minVolume = (if (AndroidVersion.isAtLeastP) getStreamMinVolume(streamType) else 0)
    val maxVolume = getStreamMaxVolume(streamType)
    return minVolume..maxVolume
}

fun AudioManager.getVolumeLevelPercent(): Int {
    val stream = AudioManager.STREAM_MUSIC
    val volumeRange = getVolumeRange(stream)
    val currentVolume = getStreamVolume(stream)
    return (currentVolume - volumeRange.first) * Constants.PERCENT_MAX / volumeRange.width
}

/**
 * Configure [Media3AudioAttributes] to handle audio focus
 */
inline fun Player.applyDefaultAudioAttributes(@C.AudioContentType contentType: Int) {
    val audioAttributes = Media3AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(contentType)
        .build()
    setAudioAttributes(audioAttributes, true)
}

fun Player.seekToOffset(offsetMs: Long) {
    var positionMs = currentPosition + offsetMs
    val durationMs = duration
    if (durationMs != C.TIME_UNSET) {
        positionMs = positionMs.coerceAtMost(durationMs)
    }
    positionMs = positionMs.coerceAtLeast(0)
    seekTo(positionMs)
}

fun Player.logTracks(analyticsCollector: AnalyticsCollector) {
    analyticsCollector.onTracksChanged(currentTracks)
}
