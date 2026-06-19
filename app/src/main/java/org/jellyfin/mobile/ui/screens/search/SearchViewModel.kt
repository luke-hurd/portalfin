package org.jellyfin.mobile.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/**
 * Backs the native search screen. Hits the Jellyfin REST API directly
 * ([ItemsApi.getItems] with `searchTerm`, recursive across all libraries) — the
 * same server-side fuzzy match that powers jellyfin-web's search, but native and
 * returning full [BaseItemDto]s so taps open the native detail screen.
 *
 * Queries are debounced ([DEBOUNCE_MS]) so typing doesn't fire a request per
 * keystroke. The injected [ApiClient] is authenticated.
 */
class SearchViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var query: String = ""
    private var searchJob: Job? = null

    /** Called on every text change; debounces then searches. */
    fun onQueryChange(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        val trimmed = newQuery.trim()
        if (trimmed.isEmpty()) {
            _state.value = SearchState.Idle
            return
        }
        _state.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(trimmed)
        }
    }

    private suspend fun search(term: String) {
        try {
            val items = withContext(Dispatchers.IO) {
                val response by apiClient.itemsApi.getItems(
                    searchTerm = term,
                    recursive = true,
                    includeItemTypes = SEARCHABLE_TYPES,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    limit = RESULT_LIMIT,
                )
                response.items.orEmpty()
            }
            // Guard against a stale result landing after the query moved on.
            if (term != query.trim()) return
            _state.value = if (items.isEmpty()) SearchState.Empty(term) else SearchState.Results(items)
        } catch (e: Exception) {
            Timber.e(e, "Search failed")
            if (term == query.trim()) _state.value = SearchState.Error(e.message ?: "Search failed")
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val RESULT_LIMIT = 60

        // Match the library grid's browsable types: a series shows as one tile.
        private val SEARCHABLE_TYPES = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.BOX_SET,
            BaseItemKind.VIDEO,
            BaseItemKind.MUSIC_VIDEO,
        )
    }
}

sealed interface SearchState {
    /** No query yet — show a prompt. */
    data object Idle : SearchState
    data object Loading : SearchState
    data class Empty(val term: String) : SearchState
    data class Results(val items: List<BaseItemDto>) : SearchState
    data class Error(val message: String) : SearchState
}
