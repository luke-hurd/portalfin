package org.jellyfin.mobile.ui.screens.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the season detail page — a full detail screen for one season: the season
 * item (image, overview), its episodes (getEpisodes), and the series' other
 * seasons (getSeasons) for the season selector. The injected [ApiClient] is authed.
 */
class SeasonViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<SeasonState>(SeasonState.Loading)
    val state: StateFlow<SeasonState> = _state.asStateFlow()

    fun load(seriesId: UUID, seasonId: UUID) {
        _state.value = SeasonState.Loading
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val seasonItem by apiClient.userLibraryApi.getItem(itemId = seasonId)
                    val episodesRes by apiClient.tvShowsApi.getEpisodes(seriesId = seriesId, seasonId = seasonId)
                    val seasonsRes by apiClient.tvShowsApi.getSeasons(seriesId = seriesId)
                    SeasonState.Content(
                        season = seasonItem,
                        episodes = episodesRes.items.orEmpty(),
                        seasons = seasonsRes.items.orEmpty(),
                    )
                }
                _state.value = content
            } catch (e: Exception) {
                Timber.e(e, "Failed to load season")
                _state.value = SeasonState.Error(e.message ?: "Couldn't load this season")
            }
        }
    }
}

sealed interface SeasonState {
    data object Loading : SeasonState
    data class Content(
        val season: BaseItemDto,
        val episodes: List<BaseItemDto>,
        val seasons: List<BaseItemDto>,
    ) : SeasonState
    data class Error(val message: String) : SeasonState
}
