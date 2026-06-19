package org.jellyfin.mobile.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the native detail screen. getItem returns the full item (overview,
 * genres, ratings, chapters, mediaStreams, people…). Similar items are fetched
 * separately for the "More Like This" row. The injected [ApiClient] is authed.
 */
class DetailViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(itemId: UUID) {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    val result by apiClient.userLibraryApi.getItem(itemId = itemId)
                    result
                }
                _state.value = DetailState.Content(item)
                // Related items load after the main content so the page paints fast.
                loadSimilar(itemId)
                // For a series, also load seasons + the next-up episode.
                if (item.type == BaseItemKind.SERIES) loadSeriesExtras(itemId)
                // For an episode, load its sibling episodes (same season).
                if (item.type == BaseItemKind.EPISODE) loadSiblingEpisodes(item)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load detail")
                _state.value = DetailState.Error(e.message ?: "Couldn't load this title")
            }
        }
    }

    private fun loadSimilar(itemId: UUID) {
        viewModelScope.launch {
            val similar = try {
                withContext(Dispatchers.IO) {
                    val result by apiClient.libraryApi.getSimilarItems(itemId = itemId, limit = SIMILAR_LIMIT)
                    result.items.orEmpty()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load similar items")
                emptyList()
            }
            val content = _state.value as? DetailState.Content ?: return@launch
            _state.value = content.copy(similar = similar)
        }
    }

    /** Seasons + the next-up episode (where you left off) for a series. */
    private fun loadSeriesExtras(seriesId: UUID) {
        viewModelScope.launch {
            val (seasons, nextUp) = try {
                withContext(Dispatchers.IO) {
                    val seasonsRes by apiClient.tvShowsApi.getSeasons(seriesId = seriesId)
                    val nextUpRes by apiClient.tvShowsApi.getNextUp(seriesId = seriesId, limit = 1)
                    seasonsRes.items.orEmpty() to nextUpRes.items.orEmpty().firstOrNull()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load series extras")
                emptyList<BaseItemDto>() to null
            }
            val content = _state.value as? DetailState.Content ?: return@launch
            _state.value = content.copy(seasons = seasons, nextUp = nextUp)
        }
    }

    /** Other episodes in the same season, for the episode-detail sibling row. */
    private fun loadSiblingEpisodes(episode: BaseItemDto) {
        val seriesId = episode.seriesId ?: return
        val seasonId = episode.seasonId ?: return
        viewModelScope.launch {
            val siblings = try {
                withContext(Dispatchers.IO) {
                    val res by apiClient.tvShowsApi.getEpisodes(seriesId = seriesId, seasonId = seasonId)
                    res.items.orEmpty()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load sibling episodes")
                emptyList()
            }
            val content = _state.value as? DetailState.Content ?: return@launch
            _state.value = content.copy(siblingEpisodes = siblings)
        }
    }

    companion object {
        private const val SIMILAR_LIMIT = 20
    }
}

sealed interface DetailState {
    data object Loading : DetailState
    data class Content(
        val item: BaseItemDto,
        val similar: List<BaseItemDto> = emptyList(),
        val seasons: List<BaseItemDto> = emptyList(),
        val nextUp: BaseItemDto? = null,
        val siblingEpisodes: List<BaseItemDto> = emptyList(),
    ) : DetailState
    data class Error(val message: String) : DetailState
}
