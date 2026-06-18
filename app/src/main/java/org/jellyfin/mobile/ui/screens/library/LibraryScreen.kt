package org.jellyfin.mobile.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.CircularProgressIndicator
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
import org.jellyfin.mobile.ui.utils.PortalColors
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.compose.koinInject

private val EDGE_PADDING = 16.dp
private const val POSTER_WIDTH_PX = 360

@Composable
fun LibraryScreen(
    onItemClick: (BaseItemDto) -> Unit,
    viewModel: LibraryViewModel,
    topContentPadding: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is LibraryState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
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
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = EDGE_PADDING,
            end = EDGE_PADDING,
            top = topContentPadding + EDGE_PADDING,
            bottom = EDGE_PADDING,
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(items, key = { it.id.toString() }, contentType = { "poster" }) { item ->
            PosterCard(item = item, onClick = { onItemClick(item) })
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
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
