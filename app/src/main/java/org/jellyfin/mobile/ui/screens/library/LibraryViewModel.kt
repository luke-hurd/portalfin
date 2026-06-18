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
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.UUID

/**
 * Backs the native library/category grid. TRUE pagination: shows ONE page of up
 * to [PAGE_SIZE] items at a time (not infinite append) so the device only ever
 * holds/decodes one page's worth — Prev/Next controls move between pages. Images
 * still lazy-load per cell via Coil. The injected [ApiClient] is authenticated.
 */
class LibraryViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var libraryId: UUID? = null
    private var filter: LibraryFilter = LibraryFilter.ALL
    private var page = 0
    private var totalCount = 0

    /** (Re)load library [libraryId] with [filter], starting at page 0. */
    fun load(libraryId: UUID, filter: LibraryFilter = LibraryFilter.ALL) {
        val changed = libraryId != this.libraryId || filter != this.filter
        this.libraryId = libraryId
        this.filter = filter
        if (changed) page = 0
        _state.value = LibraryState.Loading
        viewModelScope.launch { fetchPage() }
    }

    fun nextPage() {
        if ((page + 1) * PAGE_SIZE >= totalCount) return
        page++
        _state.value = LibraryState.Loading
        viewModelScope.launch { fetchPage() }
    }

    fun prevPage() {
        if (page == 0) return
        page--
        _state.value = LibraryState.Loading
        viewModelScope.launch { fetchPage() }
    }

    private suspend fun fetchPage() {
        val parent = libraryId ?: return
        try {
            val result = withContext(Dispatchers.IO) {
                val response by apiClient.itemsApi.getItems(
                    parentId = parent,
                    recursive = true,
                    includeItemTypes = BROWSABLE_TYPES,
                    filters = if (filter == LibraryFilter.FAVORITES) listOf(ItemFilter.IS_FAVORITE) else null,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    startIndex = page * PAGE_SIZE,
                    limit = PAGE_SIZE,
                )
                response
            }
            totalCount = result.totalRecordCount
            val items = result.items.orEmpty()
            _state.value = if (items.isEmpty()) {
                LibraryState.Empty
            } else {
                val totalPages = ((totalCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
                LibraryState.Content(
                    items = items,
                    page = page,
                    totalPages = totalPages,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load library page")
            _state.value = LibraryState.Error(e.message ?: "Couldn't load library")
        }
    }

    companion object {
        private const val PAGE_SIZE = 100

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

/** Quick-filter pill selection. */
enum class LibraryFilter { ALL, FAVORITES, GENRES, COLLECTIONS }

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Content(
        val items: List<BaseItemDto>,
        val page: Int,       // 0-based
        val totalPages: Int,
    ) : LibraryState {
        val hasPrev: Boolean get() = page > 0
        val hasNext: Boolean get() = page < totalPages - 1
    }
    data class Error(val message: String) : LibraryState
}
