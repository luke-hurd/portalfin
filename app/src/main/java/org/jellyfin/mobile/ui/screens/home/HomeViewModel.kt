package org.jellyfin.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Backs the native Portal home grid (roadmap: replace jellyfin-web's React home
 * with native Compose tiles). Builds the same rails jellyfin-web's home shows,
 * fetched directly from the Jellyfin REST API via the SDK:
 *   - Continue Watching   (itemsApi.getResumeItems)
 *   - Next Up             (tvShowsApi.getNextUp)
 *   - Latest <Library>    (userLibraryApi.getLatestMedia, one per library view)
 *
 * The injected [ApiClient] is already configured with the server base URL and
 * the signed-in user's access token by ApiClientController, so no auth wiring
 * is needed here — same as ImageProvider / DownloadsList.
 */
class HomeViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = HomeState.Loading
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) { loadContent() }
                _state.value = if (content.rows.isEmpty() && content.libraries.isEmpty()) {
                    HomeState.Empty
                } else {
                    content
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load native home")
                _state.value = HomeState.Error(e.message ?: "Couldn't load home")
            }
        }
    }

    private suspend fun loadContent(): HomeState.Content {
        // userId is omitted on these calls: the SDK fills it from the configured user.
        val rows = mutableListOf<HomeRow>()

        // Continue Watching
        val resume by apiClient.itemsApi.getResumeItems(limit = ROW_LIMIT)
        resume.items.orEmpty().takeIf { it.isNotEmpty() }?.let { items ->
            rows.add(HomeRow("Continue Watching", items))
        }

        // Next Up
        val nextUp by apiClient.tvShowsApi.getNextUp(limit = ROW_LIMIT)
        nextUp.items.orEmpty().takeIf { it.isNotEmpty() }?.let { items ->
            rows.add(HomeRow("Next Up", items))
        }

        // The user's library views — drive both the top category bar and the
        // "Latest <Library>" rails below.
        val views by apiClient.userViewsApi.getUserViews()
        val libraries = views.items.orEmpty().filter { it.collectionType in LATEST_COLLECTION_TYPES }

        for (view in libraries) {
            val latest by apiClient.userLibraryApi.getLatestMedia(
                parentId = view.id,
                limit = ROW_LIMIT,
            )
            latest.takeIf { it.isNotEmpty() }?.let { items ->
                rows.add(HomeRow("New Releases (${view.name.orEmpty()})", items))
            }
        }

        return HomeState.Content(libraries = libraries, rows = rows)
    }

    companion object {
        private const val ROW_LIMIT = 20

        // Library types worth a "Latest" rail on a media-player home. Music and
        // books don't belong on the Portal's video-first home.
        private val LATEST_COLLECTION_TYPES = setOf(
            CollectionType.MOVIES,
            CollectionType.TVSHOWS,
            CollectionType.HOMEVIDEOS,
            CollectionType.BOXSETS,
        )
    }
}

/** A single horizontal rail of media on the home screen. */
data class HomeRow(
    val title: String,
    val items: List<BaseItemDto>,
)

sealed interface HomeState {
    data object Loading : HomeState
    data object Empty : HomeState
    data class Content(
        val libraries: List<BaseItemDto>,
        val rows: List<HomeRow>,
    ) : HomeState
    data class Error(val message: String) : HomeState
}
