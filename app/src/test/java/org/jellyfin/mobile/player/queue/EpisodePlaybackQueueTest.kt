package org.jellyfin.mobile.player.queue

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class EpisodePlaybackQueueTest {
    private val seriesId = UUID.randomUUID()

    private fun episode(season: Int, number: Int) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.EPISODE,
        seriesId = seriesId,
        parentIndexNumber = season,
        indexNumber = number,
    )

    @Test
    fun `starting in the middle retains previous and next episodes across seasons`() = runBlocking {
        val episodes = listOf(episode(1, 25), episode(1, 26), episode(2, 1), episode(2, 2))
        val selected = episodes[2]
        val result = episodePlaybackQueue(selected) {
            assertEquals(seriesId, it)
            episodes
        }
        assertEquals(episodes.map { it.id }, result.ids)
        assertEquals(2, result.startIndex)
        assertEquals(episodes[1].id, result.ids[result.startIndex - 1])
        assertEquals(episodes[3].id, result.ids[result.startIndex + 1])
    }

    @Test
    fun `deduplicating episodes preserves server order and selected index`() = runBlocking {
        val episodes = listOf(episode(1, 1), episode(1, 2), episode(1, 3))
        val result = episodePlaybackQueue(episodes[1]) { listOf(episodes[0]) + episodes }
        assertEquals(episodes.map { it.id }, result.ids)
        assertEquals(1, result.startIndex)
    }

    @Test
    fun `empty or filtered series results still play the requested episode`() = runBlocking {
        val selected = episode(2, 1)
        for (response in listOf(emptyList(), listOf(episode(1, 1)))) {
            assertEquals(EpisodePlaybackQueue(listOf(selected.id), 0), episodePlaybackQueue(selected) { response })
        }
    }

    @Test
    fun `unavailable server falls back to selected episode`() = runBlocking {
        val selected = episode(1, 1)
        val result = episodePlaybackQueue(selected) { throw IllegalStateException("Offline") }
        assertEquals(EpisodePlaybackQueue(listOf(selected.id), 0), result)
    }

    @Test
    fun `movies and episodes without series metadata skip the lookup`() = runBlocking {
        val items = listOf(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MOVIE),
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.EPISODE),
        )
        for (item in items) {
            var lookedUp = false
            val result = episodePlaybackQueue(item) {
                lookedUp = true
                emptyList()
            }
            assertEquals(false, lookedUp)
            assertEquals(EpisodePlaybackQueue(listOf(item.id), 0), result)
        }
    }

    @Test
    fun `first and last episodes keep their queue boundaries`() = runBlocking {
        val episodes = listOf(episode(1, 1), episode(1, 2), episode(1, 3))
        assertEquals(0, episodePlaybackQueue(episodes.first()) { episodes }.startIndex)
        val last = episodePlaybackQueue(episodes.last()) { episodes }
        assertEquals(last.ids.lastIndex, last.startIndex)
    }

    @Test
    fun `leaving the detail page cancels queue loading`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { episodePlaybackQueue(episode(1, 1)) { throw CancellationException() } }
        }
    }
}
