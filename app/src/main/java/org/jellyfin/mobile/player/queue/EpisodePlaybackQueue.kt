package org.jellyfin.mobile.player.queue

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID

internal data class EpisodePlaybackQueue(val ids: List<UUID>, val startIndex: Int)

/** Keep preceding episodes in the queue so Previous works immediately as well as Next. */
internal suspend fun episodePlaybackQueue(
    item: BaseItemDto,
    loadEpisodes: suspend (UUID) -> List<BaseItemDto>,
): EpisodePlaybackQueue {
    val singleItem = EpisodePlaybackQueue(listOf(item.id), 0)
    val seriesId = item.seriesId ?: return singleItem
    if (item.type != BaseItemKind.EPISODE) return singleItem

    val episodes = try {
        loadEpisodes(seriesId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A failed series lookup must not prevent playing the selected episode.
        Timber.w(e, "Could not load episode playback queue")
        return singleItem
    }
    // The server supplies its configured episode order, including specials.
    val ids = episodes.map { it.id }.distinct()
    val startIndex = ids.indexOf(item.id)
    return if (startIndex >= 0) EpisodePlaybackQueue(ids, startIndex) else singleItem
}
