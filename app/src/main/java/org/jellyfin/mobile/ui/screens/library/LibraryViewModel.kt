package org.jellyfin.mobile.ui.screens.library

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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the native library grid — the destination when a home category chip is
 * tapped. Loads the items in one library view (parentId) sorted by name, via the
 * Jellyfin REST API. The injected [ApiClient] is already authenticated.
 */
class LibraryViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    fun load(libraryId: UUID) {
        _state.value = LibraryState.Loading
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val result by apiClient.itemsApi.getItems(
                        parentId = libraryId,
                        recursive = true,
                        // Only real, browsable content — recursive otherwise returns
                        // folders, collections, trailers and sample clips too.
                        includeItemTypes = BROWSABLE_TYPES,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        limit = PAGE_LIMIT,
                    )
                    result.items.orEmpty()
                }
                _state.value = if (items.isEmpty()) LibraryState.Empty else LibraryState.Content(items)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load library")
                _state.value = LibraryState.Error(e.message ?: "Couldn't load library")
            }
        }
    }

    companion object {
        // First-page cap. Paging can come later; this keeps the initial grid snappy.
        private const val PAGE_LIMIT = 200

        // Top-level browsable content for a category grid. A series shows as one
        // tile (not its episodes); folders/trailers/samples are excluded.
        private val BROWSABLE_TYPES = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.BOX_SET,
            BaseItemKind.VIDEO,
            BaseItemKind.MUSIC_VIDEO,
        )
    }
}

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Content(val items: List<BaseItemDto>) : LibraryState
    data class Error(val message: String) : LibraryState
}
