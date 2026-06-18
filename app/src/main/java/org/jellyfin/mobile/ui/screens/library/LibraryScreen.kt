package org.jellyfin.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

private val EDGE_PADDING = 16.dp
private const val POSTER_WIDTH_PX = 360

// Shared grid geometry so the skeleton matches the real grid exactly.
private val GRID_MIN_CELL = 150.dp
private val GRID_H_SPACING = 14.dp
private val GRID_V_SPACING = 18.dp
private val POSTER_CORNER = 10.dp
private const val POSTER_ASPECT = 2f / 3f
private const val SKELETON_COUNT = 24

@Composable
fun LibraryScreen(
    onItemClick: (BaseItemDto) -> Unit,
    viewModel: LibraryViewModel,
    topContentPadding: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is LibraryState.Loading -> PosterGridSkeleton(topContentPadding)
            is LibraryState.Empty -> Text(
                text = "This library is empty",
                color = PortalColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
            is LibraryState.Error -> Text(
                text = current.message,
                color = PortalColors.Error,
                modifier = Modifier.align(Alignment.Center),
            )
            is LibraryState.Content -> PosterGrid(current.items, onItemClick, topContentPadding)
        }
    }
}

@Composable
private fun PosterGrid(
    items: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit,
    topContentPadding: Dp,
) {
    // Fixed poster width → as many columns as fit the Portal's 1280px width.
    // Top padding clears the shared header (posters scroll behind it).
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_H_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_V_SPACING),
    ) {
        items(items, key = { it.id.toString() }, contentType = { "poster" }) { item ->
            PosterCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

/**
 * Loading skeleton: the SAME grid geometry as [PosterGrid] (cell size, spacing,
 * poster aspect/corner) filled with gently-pulsing placeholders, so the wait
 * reads as the page laying out rather than a bare spinner. No scroll while loading.
 */
@Composable
private fun PosterGridSkeleton(topContentPadding: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(GRID_H_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_V_SPACING),
        userScrollEnabled = false,
    ) {
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
                // Placeholder label line.
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
            style = MaterialTheme.typography.body2,
            color = PortalColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
