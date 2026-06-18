package org.jellyfin.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.jellyfin.mobile.ui.screens.pressable
import org.jellyfin.mobile.ui.screens.shimmer
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

// Match the home screen's edge padding so the grids line up across screens.
private val EDGE_PADDING = 28.dp
private const val POSTER_WIDTH_PX = 360
private const val POSTER_HEIGHT_PX = 540 // 2:3 of width

// Shared grid geometry so the skeleton matches the real grid exactly.
// Fixed 6 columns (was ~7 via adaptive sizing — a touch crowded).
private const val GRID_COLUMNS = 6
private val GRID_H_SPACING = 14.dp
private val GRID_V_SPACING = 18.dp
private val POSTER_CORNER = 10.dp
private const val POSTER_ASPECT = 2f / 3f
private const val SKELETON_COUNT = 24
private const val GROUP_SKELETON_COUNT = 4

@Suppress("LongParameterList") // Compose screen entry point — params over a holder.
@Composable
fun LibraryScreen(
    title: String,
    libraryId: java.util.UUID,
    onItemClick: (BaseItemDto) -> Unit,
    viewModel: LibraryViewModel,
    groupViewModel: LibraryGroupViewModel,
    topContentPadding: Dp = 0.dp,
) {
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.ALL) }

    // Drive the right load when the filter changes.
    LaunchedEffect(libraryId, filter) {
        when (filter) {
            LibraryFilter.ALL, LibraryFilter.FAVORITES -> viewModel.load(libraryId, filter)
            LibraryFilter.GENRES -> groupViewModel.loadGenres(libraryId)
            LibraryFilter.COLLECTIONS -> groupViewModel.loadCollections(libraryId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filter) {
            LibraryFilter.ALL, LibraryFilter.FAVORITES ->
                FlatLibrary(title, filter, viewModel, onItemClick, topContentPadding) { filter = it }
            LibraryFilter.GENRES, LibraryFilter.COLLECTIONS ->
                GroupedLibrary(title, filter, groupViewModel, onItemClick, topContentPadding) { filter = it }
        }
    }
}

/** Flat paged grid for All / Favorites (one page of <=100 at a time). */
@Suppress("LongParameterList")
@Composable
private fun FlatLibrary(
    title: String,
    filter: LibraryFilter,
    viewModel: LibraryViewModel,
    onItemClick: (BaseItemDto) -> Unit,
    topContentPadding: Dp,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    val content = state as? LibraryState.Content

    // Jump back to the top whenever the page changes.
    LaunchedEffect(content?.page) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_H_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_V_SPACING),
    ) {
        libraryHeader(
            title = title,
            filter = filter,
            onFilterChange = onFilterChange,
            pager = content?.let {
                Pager(
                    page = it.page,
                    totalPages = it.totalPages,
                    hasPrev = it.hasPrev,
                    hasNext = it.hasNext,
                    onPrev = viewModel::prevPage,
                    onNext = viewModel::nextPage,
                )
            },
        )

        when (val current = state) {
            is LibraryState.Loading -> skeletonCells()
            is LibraryState.Empty -> emptyCell(
                if (filter == LibraryFilter.FAVORITES) "No favorites yet" else "This library is empty",
            )
            is LibraryState.Error -> emptyCell(current.message)
            is LibraryState.Content -> {
                items(current.items, key = { it.id.toString() }, contentType = { "poster" }) { item ->
                    PosterCard(item = item, onClick = { onItemClick(item) })
                }
                // Bottom pager (centered) + breathing room so it isn't smashed
                // against the screen edge.
                if (current.totalPages > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "bottom-pager") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 40.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            PageControls(
                                Pager(
                                    page = current.page,
                                    totalPages = current.totalPages,
                                    hasPrev = current.hasPrev,
                                    hasNext = current.hasNext,
                                    onPrev = viewModel::prevPage,
                                    onNext = viewModel::nextPage,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Page-control state for the library header. */
private data class Pager(
    val page: Int,
    val totalPages: Int,
    val hasPrev: Boolean,
    val hasNext: Boolean,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
)

/** Grouped grid for Genres / Collections: a titled full poster grid per group. */
@Suppress("LongParameterList")
@Composable
private fun GroupedLibrary(
    title: String,
    filter: LibraryFilter,
    viewModel: LibraryGroupViewModel,
    onItemClick: (BaseItemDto) -> Unit,
    topContentPadding: Dp,
    onFilterChange: (LibraryFilter) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_H_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_V_SPACING),
    ) {
        libraryHeader(title, filter, onFilterChange)

        when (val current = state) {
            is GroupState.Loading -> skeletonCells()
            is GroupState.Empty -> emptyCell("Nothing here")
            is GroupState.Error -> emptyCell(current.message)
            is GroupState.Content -> {
                current.groups.forEach { group ->
                    // Group title (full width).
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_${group.id}", contentType = "group-title") {
                        // Kick off this group's lazy load the first time it appears.
                        LaunchedEffect(group.id) { viewModel.ensureGroupLoaded(group.id) }
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = PortalColors.OnBackground,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                        )
                    }
                    val items = group.items
                    if (items == null) {
                        // Still loading this group — a couple of shimmer placeholders.
                        items(GROUP_SKELETON_COUNT, key = { "s_${group.id}_$it" }, contentType = { "poster-skeleton" }) {
                            Column {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(POSTER_ASPECT)
                                        .clip(RoundedCornerShape(POSTER_CORNER)).shimmer(),
                                )
                            }
                        }
                    } else {
                        items(items, key = { "${group.id}_${it.id}" }, contentType = { "poster" }) { item ->
                            PosterCard(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
    }
}

// --- Shared LazyGrid building blocks (used by FlatLibrary and GroupedLibrary) ---

/** Title, then a row of filter pills (left) + page controls (right). */
private fun LazyGridScope.libraryHeader(
    title: String,
    filter: LibraryFilter,
    onFilterChange: (LibraryFilter) -> Unit,
    pager: Pager? = null,
) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PortalColors.OnBackground,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "pills") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterPills(selected = filter, onSelect = onFilterChange)
            Spacer(Modifier.weight(1f))
            // Page controls live on the right of the capsule row (flat views only;
            // grouped Genres/Collections pass no pager).
            if (pager != null && pager.totalPages > 1) PageControls(pager)
        }
    }
}

/**
 * Prev / "Page X of Y" / Next. Uses OutlinedButtons with heightIn(min = 52.dp)
 * per the Portal style guide (the sample uses Buttons, never bare IconButtons).
 */
@Composable
private fun PageControls(pager: Pager) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = pager.onPrev,
            enabled = pager.hasPrev,
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
            Text("Prev", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = "Page ${pager.page + 1} of ${pager.totalPages}",
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
        )
        OutlinedButton(
            onClick = pager.onNext,
            enabled = pager.hasNext,
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Text("Next", style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** Shimmer poster placeholders (matches the real poster geometry). */
private fun LazyGridScope.skeletonCells() {
    items(SKELETON_COUNT, contentType = { "poster-skeleton" }) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(POSTER_ASPECT)
                    .clip(RoundedCornerShape(POSTER_CORNER))
                    .shimmer(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer(),
            )
        }
    }
}

private fun LazyGridScope.emptyCell(message: String) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            Text(text = message, color = PortalColors.OnSurface)
        }
    }
}

/** All / Favorites / Genres / Collections quick-filter pills. */
@Composable
private fun FilterPills(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LibraryFilter.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                // 52dp min touch target per the Portal style guide (matches the
                // sample's FilterChip).
                modifier = Modifier.heightIn(min = 52.dp),
            )
        }
    }
}

private val LibraryFilter.label: String
    get() = when (this) {
        LibraryFilter.ALL -> "All"
        LibraryFilter.FAVORITES -> "Favorites"
        LibraryFilter.GENRES -> "Genres"
        LibraryFilter.COLLECTIONS -> "Collections"
    }

@Composable
private fun PosterCard(
    item: BaseItemDto,
    onClick: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val context = LocalContext.current

    val imageRequest = remember(apiClient, item.id, context) {
        val url = apiClient.imageApi.getItemImageUrl(
            itemId = item.id,
            imageType = ImageType.PRIMARY,
            tag = item.imageTags?.get(ImageType.PRIMARY),
            maxWidth = POSTER_WIDTH_PX,
        )
        ImageRequest.Builder(context)
            .data(url)
            // Pin the decode to the poster's pixel size (2:3) so each card holds a
            // poster-sized bitmap, not a full-res one — same fix as the home cards;
            // this is what makes the grid scroll smoothly.
            .size(POSTER_WIDTH_PX, POSTER_HEIGHT_PX)
            .crossfade(true)
            .build()
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(POSTER_CORNER))
            .pressable(onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(POSTER_CORNER))
                .background(PortalColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
