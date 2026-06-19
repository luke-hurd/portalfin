package org.jellyfin.mobile.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
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
 * Backs the grouped library views (Genres, Collections). First loads the list of
 * groups (genre names / collection BoxSets); each group's items are then fetched
 * LAZILY when its section first scrolls into view ([ensureGroupLoaded]) so a
 * library with many genres/collections stays fast.
 */
class LibraryGroupViewModel : ViewModel(), KoinComponent {
    private val apiClient: ApiClient by inject()

    private val _state = MutableStateFlow<GroupState>(GroupState.Loading)
    val state: StateFlow<GroupState> = _state.asStateFlow()

    private var libraryId: UUID? = null
    private var mode: LibraryFilter = LibraryFilter.GENRES
    private val loadingGroups = mutableSetOf<String>()

    fun loadGenres(libraryId: UUID) = loadGroups(libraryId, LibraryFilter.GENRES)

    fun loadCollections(libraryId: UUID) = loadGroups(libraryId, LibraryFilter.COLLECTIONS)

    private fun loadGroups(libraryId: UUID, mode: LibraryFilter) {
        // Avoid reloading the same view when the screen recomposes.
        if (this.libraryId == libraryId && this.mode == mode && _state.value is GroupState.Content) return
        this.libraryId = libraryId
        this.mode = mode
        _state.value = GroupState.Loading
        viewModelScope.launch {
            try {
                val groups = withContext(Dispatchers.IO) {
                    if (mode == LibraryFilter.GENRES) fetchGenreGroups(libraryId) else fetchCollectionGroups(libraryId)
                }
                _state.value = if (groups.isEmpty()) GroupState.Empty else GroupState.Content(groups)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load groups")
                _state.value = GroupState.Error(e.message ?: "Couldn't load")
            }
        }
    }

    /** Fetch a group's items the first time its section appears. */
    fun ensureGroupLoaded(groupId: String) {
        val parent = libraryId ?: return
        val group = (_state.value as? GroupState.Content)?.groups?.firstOrNull { it.id == groupId } ?: return
        if (group.items != null || groupId in loadingGroups) return
        loadingGroups += groupId
        viewModelScope.launch {
            val items = try {
                withContext(Dispatchers.IO) { fetchGroupItems(parent, group) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load group %s", groupId)
                emptyList()
            } finally {
                loadingGroups -= groupId
            }
            setGroupItems(groupId, items)
        }
    }

    private suspend fun fetchGroupItems(parent: UUID, group: LibraryGroup): List<BaseItemDto> {
        if (mode == LibraryFilter.COLLECTIONS) {
            // Collection (BoxSet) children = its movies.
            val res by apiClient.itemsApi.getItems(
                parentId = UUID.fromString(group.id),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
            )
            return res.items.orEmpty()
        }
        // Genre: items in this library tagged with the genre name.
        val res by apiClient.itemsApi.getItems(
            parentId = parent,
            recursive = true,
            includeItemTypes = GROUP_ITEM_TYPES,
            genres = listOf(group.name),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        )
        return res.items.orEmpty()
    }

    private fun setGroupItems(groupId: String, items: List<BaseItemDto>) {
        _state.update { s ->
            (s as? GroupState.Content)?.let { c ->
                c.copy(groups = c.groups.map { if (it.id == groupId) it.copy(items = items) else it })
            } ?: s
        }
    }

    private suspend fun fetchGenreGroups(libraryId: UUID): List<LibraryGroup> {
        val res by apiClient.genresApi.getGenres(parentId = libraryId, sortBy = listOf(ItemSortBy.SORT_NAME))
        // Genre groups are keyed by name (the items query filters by genre name).
        return res.items.orEmpty().mapNotNull { g -> g.name?.let { LibraryGroup(id = it, name = it) } }
    }

    private suspend fun fetchCollectionGroups(libraryId: UUID): List<LibraryGroup> {
        val res by apiClient.itemsApi.getItems(
            parentId = libraryId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.BOX_SET),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING),
        )
        return res.items.orEmpty().map { LibraryGroup(id = it.id.toString(), name = it.name.orEmpty()) }
    }

    companion object {
        private val GROUP_ITEM_TYPES = listOf(
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.VIDEO,
            BaseItemKind.MUSIC_VIDEO,
        )
    }
}

/** A genre or collection section. [items] is null until lazily loaded. */
data class LibraryGroup(
    val id: String,
    val name: String,
    val items: List<BaseItemDto>? = null,
)

sealed interface GroupState {
    data object Loading : GroupState
    data object Empty : GroupState
    data class Content(val groups: List<LibraryGroup>) : GroupState
    data class Error(val message: String) : GroupState
}
